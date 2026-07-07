import i18n from '@/locales'

/** 当前 PC 界面语言，用于异步导出表头多语言 */
export function currentExportLocale() {
  return i18n.global.locale.value || 'zh-CN'
}

/** 为导出 API 请求体附加 locale */
export function withExportLocale(query = {}) {
  return {
    ...query,
    locale: currentExportLocale(),
  }
}
