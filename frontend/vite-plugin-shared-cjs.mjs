import fs from 'fs'
import path from 'path'
import esbuild from 'esbuild'

/**
 * 将 shared/js/*.cjs 在 Vite 中编译为浏览器可执行的 ESM（避免 module.exports 直出）
 */
export function vitePluginSharedCjs(sharedJsDir) {
  function isSharedCjs(id) {
    const clean = id.split('?')[0].replace(/\\/g, '/')
    return /\/shared\/js\/[^/]+\.cjs$/.test(clean)
  }

  return {
    name: 'vite-shared-cjs',
    enforce: 'pre',
    async load(id) {
      if (!isSharedCjs(id)) return null
      const entry = id.split('?')[0]
      if (!fs.existsSync(entry)) return null

      const result = await esbuild.build({
        entryPoints: [entry],
        bundle: true,
        write: false,
        format: 'esm',
        platform: 'browser',
        target: 'es2020',
      })

      return {
        code: result.outputFiles[0].text,
        map: result.outputFiles[0].map || null,
      }
    },
  }
}
