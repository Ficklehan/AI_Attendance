/**
 * PC / 小程序语言包拆分与合并
 */
import fs from 'fs'
import path from 'path'

export const PC_LOCALES = ['zh-CN', 'en-US', 'fr-FR', 'nl-NL', 'cs-CZ', 'pl-PL', 'de-DE', 'es-ES']

const manifest = JSON.parse(
  fs.readFileSync(new URL('../shared/locales/shared-manifest.json', import.meta.url), 'utf8'),
)

export function deepMerge(base, overlay) {
  if (!overlay) return structuredClone(base ?? {})
  const out = structuredClone(base ?? {})
  for (const [key, value] of Object.entries(overlay)) {
    if (
      value
      && typeof value === 'object'
      && !Array.isArray(value)
      && out[key]
      && typeof out[key] === 'object'
      && !Array.isArray(out[key])
    ) {
      out[key] = deepMerge(out[key], value)
    } else {
      out[key] = value
    }
  }
  return out
}

function omitEmpty(obj) {
  if (!obj || typeof obj !== 'object') return obj
  const out = {}
  for (const [k, v] of Object.entries(obj)) {
    if (v && typeof v === 'object' && !Array.isArray(v)) {
      const nested = omitEmpty(v)
      if (Object.keys(nested).length) out[k] = nested
    } else if (v !== undefined) {
      out[k] = v
    }
  }
  return out
}

/**
 * 将完整 PC / MP 语言包拆为 common + 各端 overlay
 */
export function splitLocaleSources(pcFull, mpFull) {
  const common = {}
  const pc = structuredClone(pcFull)
  const mp = structuredClone(mpFull ?? {})

  for (const ns of manifest.fullNamespaces) {
    if (pc[ns]) common[ns] = structuredClone(pc[ns])
    delete pc[ns]
    delete mp[ns]
  }

  if (pc.country || mp.country) {
    common.country = {}
    for (const k of manifest.countryCodeKeys) {
      const pv = pc.country?.[k]
      const mv = mp.country?.[k]
      if (pv !== undefined) common.country[k] = pv
      else if (mv !== undefined) common.country[k] = mv
      delete pc.country?.[k]
      delete mp.country?.[k]
    }
    if (pc.country && !Object.keys(pc.country).length) delete pc.country
    if (mp.country && !Object.keys(mp.country).length) delete mp.country
  }

  const pcErr = pc.errors || {}
  const mpErr = mp.errors || {}
  common.errors = {}
  const errKeys = new Set([...Object.keys(pcErr), ...Object.keys(mpErr)])
  for (const k of errKeys) {
    const inPc = k in pcErr
    const inMp = k in mpErr
    if (inPc && inMp && pcErr[k] === mpErr[k]) {
      common.errors[k] = pcErr[k]
      delete pc.errors[k]
      delete mp.errors[k]
    } else if (inPc && inMp && pcErr[k] !== mpErr[k]) {
      common.errors[k] = manifest.errorsCanonical === 'pc' ? pcErr[k] : mpErr[k]
      delete pc.errors[k]
      delete mp.errors[k]
    }
  }
  if (pc.errors && !Object.keys(pc.errors).length) delete pc.errors
  if (mp.errors && !Object.keys(mp.errors).length) delete mp.errors

  const pcCommon = pc.common || {}
  const mpCommon = mp.common || {}
  common.common = {}
  for (const k of manifest.commonKeys) {
    if (pcCommon[k] !== undefined && pcCommon[k] === mpCommon[k]) {
      common.common[k] = pcCommon[k]
      delete pc.common[k]
      delete mp.common[k]
    }
  }
  if (pc.common && !Object.keys(pc.common).length) delete pc.common
  if (mp.common && !Object.keys(mp.common).length) delete mp.common

  return {
    common: omitEmpty(common),
    pc: omitEmpty(pc),
    mp: omitEmpty(mp),
  }
}

export function mergePlatformLocale(common, overlay) {
  return deepMerge(common, overlay)
}

export function readJsonIfExists(file) {
  if (!fs.existsSync(file)) return null
  return JSON.parse(fs.readFileSync(file, 'utf8'))
}

export function writeJson(file, data) {
  fs.mkdirSync(path.dirname(file), { recursive: true })
  fs.writeFileSync(file, `${JSON.stringify(data, null, 2)}\n`)
}

export function loadSplitLocale(root, locale) {
  const common = readJsonIfExists(path.join(root, 'shared/locales/common', `${locale}.json`)) || {}
  const pc = readJsonIfExists(path.join(root, 'shared/locales/pc', `${locale}.json`)) || {}
  const mp = readJsonIfExists(path.join(root, 'shared/locales/miniprogram', `${locale}.json`)) || {}
  return {
    common,
    pc,
    mp,
    pcMerged: mergePlatformLocale(common, pc),
    mpMerged: mergePlatformLocale(common, mp),
  }
}
