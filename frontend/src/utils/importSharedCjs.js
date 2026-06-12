/**
 * Vite 开发态加载 shared/*.cjs：无 default 导出时用 namespace 或 .default
 */
export function importSharedCjs(ns) {
  if (!ns) return ns
  const d = ns.default
  if (d && typeof d === 'object' && !Array.isArray(d)) {
    return { ...ns, ...d }
  }
  return ns
}
