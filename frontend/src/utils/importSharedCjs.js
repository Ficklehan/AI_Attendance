/**
 * Vite 经 esbuild 将 shared/*.cjs 转为 ESM 后，全部导出在 default 对象上。
 */
export function importSharedCjs(ns) {
  if (!ns) return ns
  const d = ns.default
  if (d && typeof d === 'object' && !Array.isArray(d)) {
    return d
  }
  return ns
}
