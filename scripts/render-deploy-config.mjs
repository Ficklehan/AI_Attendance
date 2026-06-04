#!/usr/bin/env node
/**
 * Read deploy/environments/<env>.yaml and render:
 *   - deploy/rendered/<env>.env          (server / CI env fragment)
 *   - feishu-miniprogram/config.prod.js  (miniprogram public API; shared for prod/uat)
 *   - deploy/rendered/<env>.snapshot.json (audit)
 *
 * Usage:
 *   node scripts/render-deploy-config.mjs
 *   node scripts/render-deploy-config.mjs --env uat
 *   node scripts/render-deploy-config.mjs --env production --check
 */

import fs from 'fs'
import path from 'path'
import { fileURLToPath } from 'url'

const __dirname = path.dirname(fileURLToPath(import.meta.url))
const ROOT = path.resolve(__dirname, '..')

function parseArgs(argv) {
  const args = { env: 'production', check: false }
  for (let i = 2; i < argv.length; i++) {
    if (argv[i] === '--env' && argv[i + 1]) {
      args.env = argv[++i]
    } else if (argv[i] === '--check') {
      args.check = true
    }
  }
  return args
}

/** Minimal YAML parser for flat two-level deploy manifests. */
function parseSimpleYaml(text) {
  const root = {}
  let section = root
  let sectionKey = null

  for (const rawLine of text.split('\n')) {
    const line = rawLine.replace(/\r$/, '')
    const trimmed = line.trim()
    if (!trimmed || trimmed.startsWith('#')) continue

    if (!line.startsWith(' ') && !line.startsWith('\t')) {
      const idx = trimmed.indexOf(':')
      if (idx === -1) continue
      const key = trimmed.slice(0, idx).trim()
      const value = trimmed.slice(idx + 1).trim()
      if (value) {
        root[key] = stripQuotes(value)
        section = root
        sectionKey = null
      } else {
        root[key] = {}
        section = root[key]
        sectionKey = key
      }
      continue
    }

    const idx = trimmed.indexOf(':')
    if (idx === -1) continue
    const key = trimmed.slice(0, idx).trim()
    const value = stripQuotes(trimmed.slice(idx + 1).trim())
    if (sectionKey) {
      section[key] = value
    } else {
      root[key] = value
    }
  }
  return root
}

function stripQuotes(value) {
  if (
    (value.startsWith('"') && value.endsWith('"')) ||
    (value.startsWith("'") && value.endsWith("'"))
  ) {
    return value.slice(1, -1)
  }
  return value
}

function joinUrl(origin, ...segments) {
  let url = origin.replace(/\/+$/, '')
  for (const segment of segments) {
    const part = String(segment || '').replace(/^\/+|\/+$/g, '')
    if (part) url += '/' + part
  }
  return url
}

/** Join URL path segments (no origin), e.g. /attendance + feishu/callback */
function joinPath(...segments) {
  let path = ''
  for (const segment of segments) {
    const part = String(segment || '').replace(/^\/+|\/+$/g, '')
    if (part) path += '/' + part
  }
  return path || '/'
}

function resolveFrontendPath(manifest, segment) {
  const raw = String(segment || '').trim()
  if (raw.startsWith('/')) {
    return raw
  }
  const webBase = (manifest.paths && manifest.paths.frontend_web_base) || '/attendance'
  return joinPath(webBase, raw)
}

function deriveUrls(manifest) {
  const host = manifest.public.host
  const scheme = manifest.public.scheme || 'https'
  const origin = `${scheme}://${host}`
  const apiContext = manifest.paths.api_context || '/attendance/api'
  const apiBaseUrl = joinUrl(origin, apiContext)
  const feishuOAuthSuffix = manifest.paths.feishu_oauth_callback || '/feishu-auth/callback'
  const frontendFeishuCallbackPath = resolveFrontendPath(
    manifest,
    manifest.paths.frontend_feishu_callback || 'feishu/callback'
  )
  const frontendLoginPath = resolveFrontendPath(
    manifest,
    manifest.paths.frontend_login != null && manifest.paths.frontend_login !== ''
      ? manifest.paths.frontend_login
      : (manifest.paths && manifest.paths.frontend_web_base) || 'attendance'
  )

  return {
    PUBLIC_HOST: host,
    PUBLIC_SCHEME: scheme,
    PUBLIC_ORIGIN: origin,
    PUBLIC_BASE_URL: apiBaseUrl,
    API_CONTEXT_PATH: apiContext.startsWith('/') ? apiContext : `/${apiContext}`,
    FEISHU_REDIRECT_URI: joinUrl(apiBaseUrl, feishuOAuthSuffix),
    FEISHU_FRONTEND_CALLBACK_URL: joinUrl(origin, frontendFeishuCallbackPath),
    FEISHU_FRONTEND_LOGIN_URL: joinUrl(origin, frontendLoginPath),
    SPRING_PROFILES_ACTIVE: (manifest.spring && manifest.spring.profile) || 'prod',
    CORS_ALLOWED_ORIGIN: origin
  }
}

function renderEnvFile(urls, manifest) {
  const lines = [
    '# AUTO-GENERATED — do not edit. Source: deploy/environments/' + manifest._sourceFile,
    '# Regenerate: node scripts/render-deploy-config.mjs --env ' + manifest._envName,
    '',
    `DEPLOY_MANIFEST=${manifest._sourceFile}`,
    `DEPLOY_ENV=${manifest._envName}`,
    `PUBLIC_HOST=${urls.PUBLIC_HOST}`,
    `PUBLIC_SCHEME=${urls.PUBLIC_SCHEME}`,
    `PUBLIC_ORIGIN=${urls.PUBLIC_ORIGIN}`,
    `PUBLIC_BASE_URL=${urls.PUBLIC_BASE_URL}`,
    `API_CONTEXT_PATH=${urls.API_CONTEXT_PATH}`,
    '',
    `SPRING_PROFILES_ACTIVE=${urls.SPRING_PROFILES_ACTIVE}`,
    '',
    `FEISHU_REDIRECT_URI=${urls.FEISHU_REDIRECT_URI}`,
    `FEISHU_FRONTEND_CALLBACK_URL=${urls.FEISHU_FRONTEND_CALLBACK_URL}`,
    `FEISHU_FRONTEND_LOGIN_URL=${urls.FEISHU_FRONTEND_LOGIN_URL}`,
    `CORS_ALLOWED_ORIGIN=${urls.CORS_ALLOWED_ORIGIN}`,
    ''
  ]
  return lines.join('\n')
}

function renderMiniprogramConfig(urls, manifest) {
  return `/**
 * AUTO-GENERATED — do not edit.
 * Source: deploy/environments/${manifest._sourceFile}
 * Regenerate: node scripts/render-deploy-config.mjs --env ${manifest._envName}
 */
module.exports = {
  PUBLIC_HOST: '${urls.PUBLIC_HOST}',
  PUBLIC_ORIGIN: '${urls.PUBLIC_ORIGIN}',
  PUBLIC_BASE_URL: '${urls.PUBLIC_BASE_URL}',
  DEPLOY_ENV: '${manifest._envName}',
  SPRING_PROFILE: '${urls.SPRING_PROFILES_ACTIVE}'
}
`
}

function deepMerge(base, override) {
  const result = { ...base }
  for (const [key, value] of Object.entries(override)) {
    if (key.startsWith('_')) continue
    if (
      value &&
      typeof value === 'object' &&
      !Array.isArray(value) &&
      base[key] &&
      typeof base[key] === 'object'
    ) {
      result[key] = deepMerge(base[key], value)
    } else if (value !== undefined) {
      result[key] = value
    }
  }
  return result
}

function readManifest(envName) {
  const file = path.join(ROOT, 'deploy', 'environments', `${envName}.yaml`)
  if (!fs.existsSync(file)) {
    throw new Error(`Manifest not found: ${file}`)
  }
  const text = fs.readFileSync(file, 'utf8')
  let parsed = parseSimpleYaml(text)

  if (parsed.inherit) {
    const baseName = parsed.inherit
    const base = readManifest(baseName)
    parsed = deepMerge(base, parsed)
  }

  parsed._envName = envName
  parsed._sourceFile = `${envName}.yaml`
  return parsed
}

function writeFileEnsuringDir(filePath, content) {
  fs.mkdirSync(path.dirname(filePath), { recursive: true })
  fs.writeFileSync(filePath, content, 'utf8')
}

function main() {
  const { env, check } = parseArgs(process.argv)
  const manifest = readManifest(env)
  const urls = deriveUrls(manifest)

  const renderedDir = path.join(ROOT, 'deploy', 'rendered')
  const envFile = path.join(renderedDir, `${env}.env`)
  const snapshotFile = path.join(renderedDir, `${env}.snapshot.json`)
  const miniprogramFile = path.join(ROOT, 'feishu-miniprogram', 'config.prod.js')

  const envContent = renderEnvFile(urls, manifest)
  const miniprogramContent = renderMiniprogramConfig(urls, manifest)
  const snapshot = {
    generatedAt: new Date().toISOString(),
    manifest: manifest._sourceFile,
    env,
    urls
  }

  if (check) {
    const existingEnv = fs.existsSync(envFile) ? fs.readFileSync(envFile, 'utf8') : ''
    const existingMp = fs.existsSync(miniprogramFile) ? fs.readFileSync(miniprogramFile, 'utf8') : ''
    if (existingEnv !== envContent || existingMp !== miniprogramContent) {
      console.error('Rendered config is out of date. Run: node scripts/render-deploy-config.mjs --env', env)
      process.exit(1)
    }
    console.log('Deploy config up to date for env:', env)
    return
  }

  writeFileEnsuringDir(envFile, envContent)
  writeFileEnsuringDir(snapshotFile, JSON.stringify(snapshot, null, 2) + '\n')

  console.log('Rendered deploy config for env:', env)
  console.log('  env file:      ', path.relative(ROOT, envFile))
  console.log('  snapshot:      ', path.relative(ROOT, snapshotFile))

  // 小程序公网配置以 production 清单为准（uat 与 prod 同域时避免被覆盖）
  if (env === 'production') {
    fs.writeFileSync(miniprogramFile, miniprogramContent, 'utf8')
    console.log('  miniprogram:   ', path.relative(ROOT, miniprogramFile))
  }
  console.log('')
  console.log('Public API base:', urls.PUBLIC_BASE_URL)
  console.log('Spring profile: ', urls.SPRING_PROFILES_ACTIVE)
  console.log('')
  console.log('Server restart (after domain change):')
  console.log('  ./start.sh restart-prod')
  console.log('  # or: npm run restart:prod')
}

main()
