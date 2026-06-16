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
const { normalizePauseMinutes } = require('./recordDisplay')
const { formatCalibDisplayValue } = require('./calibrationHistory')

function buildCalibFormFields(draft, sourceRecord, options) {
  const opts = options || {}
  const fieldKeys = opts.fieldKeys || CALIBRATABLE_FIELDS
  const record = Object.assign({}, sourceRecord || {}, draft || {})
  const missingKeys = getMissingRequiredFieldKeys(record)

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
    const field = {
      key,
      label,
      value,
      required,
      missing: missingKeys.indexOf(key) !== -1,
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
