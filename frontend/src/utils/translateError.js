import { h } from 'vue'
import { Button, message, notification } from 'ant-design-vue'
import i18n from '@/locales'

const CODE_FALLBACK_KEYS = {
  401: 'errors.loginRequired',
  404: 'errors.apiNotFound',
  405: 'errors.methodNotAllowed',
  500: 'errors.systemError',
  1001: 'errors.userNotFound',
  1002: 'errors.userAlreadyExists',
  1003: 'errors.passwordWrong',
  1004: 'errors.loginRequired',
  1006: 'errors.accessDenied',
  2001: 'errors.taskNotFound',
  /** 2002 = TASK_STATUS_ERROR；导出未就绪等场景后端会带 messageKey，优先走 messageKey */
  2002: 'errors.taskStatusCannotConfirm',
  3001: 'errors.imageInvalid',
  4001: 'errors.aiNoParseableRecords',
}

const ERROR_WITH_PREVIEW = new Set([
  'errors.aiNoParseableRecords',
  'errors.aiInvalidJson',
])

/** 兼容尚未重启的后端仍返回中文 message */
const LEGACY_EXACT = {
  '请先登录': 'errors.loginRequired',
  '无权限访问': 'errors.accessDenied',
  '无权限访问该任务': 'errors.taskAccessDenied',
  '无权限访问该文件': 'errors.fileAccessDenied',
  '用户不存在': 'errors.userNotFound',
  '用户已被禁用': 'errors.userDisabled',
  '不能禁用当前登录账号': 'errors.cannotDisableSelf',
  '不能删除当前登录账号': 'errors.cannotDeleteSelf',
  '不能删除最后一个管理员账号': 'errors.cannotDeleteLastAdmin',
  '用户名已存在': 'errors.userAlreadyExists',
  '邮箱已被使用': 'errors.emailAlreadyExists',
  '飞书用户已存在': 'errors.feishuUserAlreadyExists',
  '密码错误': 'errors.passwordWrong',
  '旧密码错误': 'errors.oldPasswordWrong',
  '任务不存在': 'errors.taskNotFound',
  '任务状态不允许确认': 'errors.taskStatusCannotConfirm',
  '仅已确认任务可重试飞书同步': 'errors.feishuRetryConfirmedOnly',
  '飞书已同步成功，无需重试': 'errors.feishuAlreadySynced',
  '当前国家已关闭飞书多维表同步': 'errors.feishuSyncDisabled',
  '没有可同步的确认数据': 'errors.noConfirmedDataToSync',
  '确认数据为空，无法同步': 'errors.confirmedDataEmpty',
  '已确认任务不可删除': 'errors.confirmedTaskCannotDelete',
  '任务已作废': 'errors.taskAlreadyCancelled',
  '已确认任务不可取消': 'errors.confirmedTaskCannotCancel',
  '没有可导出的数据': 'errors.noExportData',
  '文件不存在': 'errors.fileNotFound',
  '图片文件无效': 'errors.imageInvalid',
  '非法文件路径': 'errors.invalidFilePath',
  '只支持图片文件': 'errors.imagesOnly',
  '只支持图片或 PDF 文件': 'errors.imagesOnly',
  '无法识别图片格式，请使用 JPG/PNG 拍照后重试': 'errors.unrecognizedImageFormat',
  '无法识别文件格式，请使用 JPG/PNG 或 PDF': 'errors.unrecognizedImageFormat',
  '系统异常，请稍后重试': 'errors.systemError',
  '文件大小超出限制，最大支持10MB': 'errors.fileSizeExceeded',
  '接口不存在': 'errors.apiNotFound',
  '请求方法不支持': 'errors.methodNotAllowed',
  '请求失败': 'errors.requestFailed',
  '网络错误': 'errors.networkError',
  '识别任务过多，请稍后再试': 'errors.recognitionConcurrentLimit',
  '数据库字段缺失，请执行 migration': 'errors.dbMigrationRequired',
  '数据库字段缺失，请执行 backend/config/migration 下的迁移脚本': 'errors.dbMigrationRequired',
}

function normalizePayload(payload) {
  if (!payload) return {}
  if (typeof payload === 'string') return { message: payload }
  return payload
}

export function formatErrorPreview(preview) {
  if (!preview) return ''
  return String(preview)
    .replace(/\]\s*\[/g, ']\n[')
    .replace(/\s{2,}/g, ' ')
    .trim()
}

const IMAGE_QUALITY_BLOCK_REASON_KEYS = {
  BLUR_ROWS: 'errors.imageQualityBlockReasonBlurRows',
  UNKNOWN_FIELDS: 'errors.imageQualityBlockReasonUnknownFields',
  FEW_ROWS_UNKNOWN: 'errors.imageQualityBlockReasonFewRowsUnknown',
}

function enrichImageQualityArgs(args) {
  const enriched = { ...(args || {}) }
  const t = i18n.global.t
  const te = i18n.global.te
  if (enriched.blockReason) {
    const reasonKey = IMAGE_QUALITY_BLOCK_REASON_KEYS[enriched.blockReason]
    if (reasonKey && te(reasonKey)) {
      enriched.blockReasonLabel = t(reasonKey)
    } else {
      enriched.blockReasonLabel = String(enriched.blockReason)
    }
  } else if (!enriched.blockReasonLabel) {
    enriched.blockReasonLabel = '—'
  }
  const denom = enriched.blurRateDenominator || enriched.unknownRateScope
  const excludeAbsent = enriched.unknownRateExcludeAbsent !== false
  let base
  if (denom === 'ALL_ROWS') {
    base = te('errors.imageQualityStatsRangeAllRows')
      ? t('errors.imageQualityStatsRangeAllRows')
      : (te('errors.imageQualityDenominatorAllRows') ? t('errors.imageQualityDenominatorAllRows') : denom)
  } else if (excludeAbsent) {
    base = te('errors.imageQualityStatsRangeAttendanceOnly')
      ? t('errors.imageQualityStatsRangeAttendanceOnly')
      : (te('errors.imageQualityDenominatorEffectiveRows') ? t('errors.imageQualityDenominatorEffectiveRows') : 'EFFECTIVE_ROWS')
  } else {
    base = te('errors.imageQualityStatsRangeNamedRows')
      ? t('errors.imageQualityStatsRangeNamedRows')
      : (te('errors.imageQualityUnknownIncludeAbsent') ? t('errors.imageQualityUnknownIncludeAbsent') : '')
  }
  enriched.denominatorLabel = base
  if (enriched.blockBlurThreshold == null) {
    enriched.blockBlurThreshold = '—'
  }
  if (enriched.blockUnknownThreshold == null) {
    enriched.blockUnknownThreshold = '—'
  }
  return enriched
}

function resolveAiImageTooBlurryTitle(titleArgs, preview) {
  const t = i18n.global.t
  const te = i18n.global.te
  const byReason = {
    BLUR_ROWS: 'errors.aiImageTooBlurryBlurRows',
    UNKNOWN_FIELDS: 'errors.aiImageTooBlurryUnknownFields',
    FEW_ROWS_UNKNOWN: 'errors.aiImageTooBlurryFewRows',
  }
  const reasonKey = byReason[titleArgs.blockReason]
  if (reasonKey && te(reasonKey)) {
    return { title: t(reasonKey, titleArgs), preview, key: reasonKey }
  }
  return { title: t('errors.aiImageTooBlurry', titleArgs), preview, key: 'errors.aiImageTooBlurry' }
}

function resolveErrorParts(payload) {
  const { messageKey, messageArgs, message, code } = normalizePayload(payload)
  const t = i18n.global.t
  const te = i18n.global.te

  let key = messageKey
  let args = enrichImageQualityArgs(messageArgs || {})

  if (!key && message && message.startsWith('errors.') && te(message)) {
    key = message
  }
  if (!key && code != null && CODE_FALLBACK_KEYS[code]) {
    key = CODE_FALLBACK_KEYS[code]
  }
  if (!key && message && /^\d+$/.test(String(message).trim())) {
    key = 'errors.requestFailed'
  }
  if (!key && message && LEGACY_EXACT[message]) {
    key = LEGACY_EXACT[message]
  }

  let preview = args.preview ? String(args.preview) : ''
  if (!preview && message) {
    if (message.includes('摘要:')) {
      preview = message.split('摘要:').pop().trim()
    } else if (message.includes('Summary:')) {
      preview = message.split('Summary:').pop().trim()
    }
  }

  if (!key && message) {
    if (message.includes('文件不存在:')) {
      const fileKey = message.split(':').slice(1).join(':').trim()
      return { title: t('errors.fileNotFoundWithKey', { fileKey }), preview: '', key: null }
    }
    if (message.includes('上传的图片无效或过小')) {
      const match = message.match(/（(\d+)\s*bytes）/)
      return {
        title: t('errors.uploadImageTooSmall', { size: match ? match[1] : 0 }),
        preview: '',
        key: null,
      }
    }
    if (message.includes('模型把表头文字当成了数据')) {
      return { title: t('errors.aiHeaderEcho'), preview: '', key: null }
    }
    if (message.includes('未加引号的非法 JSON')) {
      key = 'errors.aiInvalidJson'
      if (!preview) preview = message.slice(-200)
    } else if (message.includes('模型未返回可解析的考勤 JSON')) {
      key = 'errors.aiNoParseableRecords'
    } else if (message.includes('识别结果疑似模型编造')) {
      return { title: t('errors.aiFabricated'), preview: '', key: null }
    } else if (message.includes('疑似模型臆测而非读图')) {
      return { title: t('errors.aiUnreadableTimes'), preview: '', key: null }
    } else if (message.includes('图片不够清晰') || message.startsWith('errors.aiImageTooBlurry')) {
      key = 'errors.aiImageTooBlurry'
      if (!args.blurPercent && !args.unknownPercent) {
        const blurMatch = message.match(/约\s*(\d+)%\s*行模糊/)
        const unknownMatch = message.match(/或\s*(\d+)%\s*关键字段/)
        if (blurMatch) args.blurPercent = Number(blurMatch[1])
        if (unknownMatch) args.unknownPercent = Number(unknownMatch[1])
      }
    } else if (message.startsWith('errors.uploadImageTooBlurry')) {
      key = 'errors.uploadImageTooBlurry'
    } else if (message.includes('未配置 MIMO API Key')) {
      return { title: t('errors.mimoNotConfigured'), preview: '', key: null }
    } else if (
      message.includes('xiaomimimo.com')
      || message.includes('API请求失败')
      || message.includes('502 Bad Gateway')
      || message.includes('Bad Gateway')
    ) {
      return { title: t('errors.mimoUnavailable'), preview: '', key: 'errors.mimoUnavailable' }
    } else if (message.startsWith('errors.mimoUnavailable')) {
      key = 'errors.mimoUnavailable'
    }
  }

  if (key && te(key)) {
    const titleArgs = enrichImageQualityArgs({ ...args })
    if (key === 'errors.aiImageTooBlurry'
        && titleArgs.blurPercent == null
        && titleArgs.unknownPercent == null) {
      return { title: t('errors.uploadImageTooBlurry', titleArgs), preview, key: 'errors.uploadImageTooBlurry' }
    }
    if (key === 'errors.aiImageTooBlurry') {
      return resolveAiImageTooBlurryTitle(titleArgs, preview)
    }
    if (key === 'errors.uploadImageTooBlurry'
        && titleArgs.variance == null
        && titleArgs.threshold == null) {
      return { title: t('errors.uploadImageTooBlurry', { variance: '—', threshold: '—', ...titleArgs }), preview, key }
    }
    return { title: t(key, titleArgs), preview, key }
  }

  return {
    title: message || t('errors.requestFailed'),
    preview,
    key: null,
  }
}

export function translateApiError(payload) {
  const { title, preview, key } = resolveErrorParts(payload)
  if (key && ERROR_WITH_PREVIEW.has(key) && preview) {
    return title
  }
  if (preview && !key) {
    return title
  }
  return title
}

/** 展示 API 错误：长摘要用通知卡片 + 可滚动预览，避免 message 挤成一行 */
export function showApiError(payload) {
  const { title, preview, key } = resolveErrorParts(payload)
  const formatted = formatErrorPreview(preview)

  if (key && ERROR_WITH_PREVIEW.has(key) && formatted.length > 40) {
    const t = i18n.global.t
    notification.error({
      message: title,
      description: () =>
        h('div', { class: 'api-error-detail' }, [
          h('div', { class: 'api-error-detail__label' }, t('errors.aiParsePreviewLabel')),
          h('pre', { class: 'api-error-preview' }, formatted),
        ]),
      duration: 12,
      class: 'api-error-notification',
      style: { width: 'min(520px, 92vw)' },
    })
    return title
  }

  message.error(title)
  return title
}

export function translateErrorMessage(error) {
  if (!error) return translateApiError(null)
  if (error.response?.data) return translateApiError(error.response.data)
  return translateApiError({
    message: error.message,
    code: error.code,
    messageKey: error.messageKey,
    messageArgs: error.messageArgs,
  })
}

const HOME_UPLOAD_DIRECT_KEYS = new Set([
  'errors.uploadImageTooSmall',
  'errors.unrecognizedImageFormat',
  'errors.imagesOnly',
  'errors.aiImageTooBlurry',
])

function showHomeCenterNotice(type, title, description) {
  const t = i18n.global.t
  const key = `home-upload-notice-${Date.now()}`
  notification[type]({
    message: title,
    description,
    duration: 0,
    key,
    class: 'home-upload-center-notice',
    btn: () => h(
      Button,
      {
        type: 'primary',
        size: 'small',
        onClick: () => notification.close(key),
      },
      () => (i18n.global.te('common.gotIt') ? t('common.gotIt') : t('common.confirm')),
    ),
  })
}

/** 首页上传/识别失败：单条提示，避免与 axios 拦截器重复弹窗 */
export function showHomeUploadError(error) {
  const key = error?.messageKey
  const t = i18n.global.t
  const te = i18n.global.te
  const reason = translateErrorMessage(error)

  if (key === 'errors.uploadImageTooBlurry') {
    showHomeCenterNotice(
      'warning',
      te('home.uploadBlurryTitle') ? t('home.uploadBlurryTitle') : reason,
      te('home.uploadBlurryHint') ? t('home.uploadBlurryHint') : undefined,
    )
    return
  }

  if (key && HOME_UPLOAD_DIRECT_KEYS.has(key)) {
    showHomeCenterNotice('warning', reason)
    return
  }

  showHomeCenterNotice('error', t('home.uploadFailedGeneric', { reason }))
}

function isBackendUnreachable(error) {
  if (!error) return false
  if (!error.response) {
    const msg = String(error.message || '').toLowerCase()
    return (
      error.code === 'ECONNREFUSED' ||
      msg.includes('network error') ||
      msg.includes('econnrefused') ||
      msg.includes('socket hang up')
    )
  }
  const status = error.response.status
  if (status !== 500 && status !== 502 && status !== 503) return false
  const data = error.response.data
  if (data && typeof data === 'object' && data.code != null) return false
  return true
}

export function showErrorMessage(error) {
  if (!error) {
    showApiError(null)
    return
  }
  if (isBackendUnreachable(error)) {
    showApiError({ messageKey: 'errors.backendUnavailable' })
    return
  }
  if (error.response?.data) {
    showApiError(error.response.data)
    return
  }
  showApiError({ message: error.message, code: error.code })
}
