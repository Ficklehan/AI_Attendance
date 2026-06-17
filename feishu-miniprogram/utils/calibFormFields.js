const { t } = require('./i18n')
const {
  CALIBRATABLE_FIELDS,
  FIELD_LABEL_KEYS,
} = require('./calibratableFields')
const {
  isConfiguredRequiredField,
  appendRequiredMark,
  getMissingRequiredFieldKeys,
} = require('./requiredRecordFields')
const { getInvalidFormatFieldKeys, isArrivalDepartureSameTime } = require('./recordFieldFormatRules')
const { getFormatHintKeys } = require('./fieldFormatHints')
const { normalizePauseMinutes } = require('./recordDisplay')
const { formatCalibDisplayValue } = require('./calibrationHistory')

function buildCalibFormFields(draft, sourceRecord, options) {
  const opts = options || {}
  const fieldKeys = opts.fieldKeys || CALIBRATABLE_FIELDS
  const record = Object.assign({}, sourceRecord || {}, draft || {})
  const missingKeys = getMissingRequiredFieldKeys(record)
  const formatInvalidKeys = getInvalidFormatFieldKeys(record)

  return fieldKeys.map((key) => {
    const required = isConfiguredRequiredField(key)
    let label = t(FIELD_LABEL_KEYS[key] || key)
    if (required) {
      label = appendRequiredMark(label)
    }
    let value = draft[key]
    if (key === 'PAUSE') {
      const minutes = normalizePauseMinutes(value)
      value = minutes === '' ? '' : String(minutes)
    } else {
      value = value === undefined || value === null ? '' : String(value)
    }
    const formatInvalid = formatInvalidKeys.indexOf(key) !== -1
    const hintKeys = getFormatHintKeys(key, { record, isSameArrivalDeparture: isArrivalDepartureSameTime })
    let formatPlaceholder = ''
    let formatHint = ''
    if (formatInvalid && hintKeys) {
      const short = t(hintKeys.short)
      formatPlaceholder = short !== hintKeys.short ? short : ''
      const tooltip = t(hintKeys.tooltip)
      formatHint = tooltip !== hintKeys.tooltip ? tooltip : formatPlaceholder
    }
    const field = {
      key,
      label,
      value,
      required,
      missing: missingKeys.indexOf(key) !== -1,
      formatInvalid,
      formatPlaceholder,
      formatHint,
      inputType: key === 'PAUSE' ? 'number' : 'text',
    }
    if (opts.includeOriginal && sourceRecord) {
      field.originalDisplay = formatCalibDisplayValue(sourceRecord[key])
    }
    return field
  })
}

module.exports = {
  buildCalibFormFields,
}
