const { t } = require('./i18n')

/**
 * 从 ai_raw_output / recognitionEngine 标签解析国家码。
 * 支持 `mimo:FR`、`deepseek:FR`；无前缀时回退 fallback。
 */
function parsePromptCountryFromEngine(engine, fallback) {
  const raw = String(engine || '').trim()
  if (raw.indexOf('mimo:') === 0) {
    return raw.slice(5) || fallback || ''
  }
  if (raw.indexOf('deepseek:') === 0) {
    return raw.slice(9) || fallback || ''
  }
  return fallback || ''
}

/** 识别进度里 aiRawOutput 可能是引擎标签或 AI 原文；优先用标签，否则回退上传时的 recognitionEngine */
function isEngineTag(value) {
  const raw = String(value || '').trim()
  if (!raw) return false
  if (raw === 'mimo' || raw === 'deepseek' || raw === 'simulated') return true
  if (raw.indexOf('mimo:') === 0 || raw.indexOf('deepseek:') === 0) return true
  if (raw.indexOf('simulated') === 0) return true
  return false
}

function resolveProgressEngine(aiRawOutput, preferredEngine) {
  const raw = String(aiRawOutput || '').trim()
  const preferred = String(preferredEngine || '').trim()
  if (isEngineTag(raw)) return raw
  if (preferred) return preferred
  return ''
}

function withCountryLabel(baseKey, countryKey, defaultKey, promptCountry) {
  const cc = String(promptCountry || '').trim()
  if (cc && cc !== 'default') {
    const text = t(countryKey, { country: cc })
    return text !== countryKey ? text : `${t(baseKey)}（${cc}）`
  }
  const text = t(defaultKey)
  return text !== defaultKey ? text : t(baseKey)
}

/**
 * 展示识别引擎标签。引擎未知时返回空串（不默认写成 MiMo），
 * 由上传/轮询返回的 recognitionEngine / aiRawOutput 驱动，与后台当前模型一致。
 */
function formatRecognitionEngine(engine, promptCountry) {
  const raw = String(engine || '').trim()
  if (!raw) return ''

  if (raw === 'simulated' || raw.indexOf('simulated') === 0) {
    const text = t('result.engineSimulated')
    return text !== 'result.engineSimulated'
      ? text
      : '模拟数据（非真实 AI，请关闭 ALLOW_SIMULATED_RECOGNITION）'
  }

  const country = parsePromptCountryFromEngine(raw, promptCountry)

  if (raw === 'deepseek' || raw.indexOf('deepseek:') === 0) {
    return withCountryLabel(
      'result.engineDeepSeek',
      'result.engineDeepSeekWithCountry',
      'result.engineDeepSeekDefault',
      country,
    )
  }

  if (raw === 'mimo' || raw.indexOf('mimo:') === 0) {
    return withCountryLabel(
      'result.engineMimo',
      'result.engineMimoWithCountry',
      'result.engineMimoDefault',
      country,
    )
  }

  if (raw.length > 80 || raw.indexOf('[') >= 0 || raw.indexOf('张三') >= 0) {
    const text = t('result.engineGeneric')
    return text !== 'result.engineGeneric' ? text : 'AI'
  }

  return raw
}

module.exports = {
  formatRecognitionEngine,
  parsePromptCountryFromEngine,
  resolveProgressEngine,
  isEngineTag,
}
