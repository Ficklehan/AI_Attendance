const { FORMAT_FIELD_KEYS } = require('./recordFieldFormatRules.cjs')

/** i18n keys under shared common locale `fieldFormat` */
const SAME_TIME_HINT_KEYS = {
  short: 'fieldFormat.sameTimeShort',
  tooltip: 'fieldFormat.sameTimeTooltip',
}

const FIELD_FORMAT_HINT_KEYS = {
  Date: {
    short: 'fieldFormat.dateShort',
    tooltip: 'fieldFormat.dateTooltip',
  },
  HORAIRES_DU_TRAVAIL: {
    short: 'fieldFormat.shiftShort',
    tooltip: 'fieldFormat.shiftTooltip',
  },
  ARRIVEE: {
    short: 'fieldFormat.arrivalShort',
    tooltip: 'fieldFormat.arrivalTooltip',
  },
  DEPAR: {
    short: 'fieldFormat.departureShort',
    tooltip: 'fieldFormat.departureTooltip',
  },
}

function isFormatHintField(fieldKey) {
  return FORMAT_FIELD_KEYS.indexOf(fieldKey) !== -1
}

function getFormatHintKeys(fieldKey, options) {
  const record = options && options.record
  const isSameArrivalDeparture = options && options.isSameArrivalDeparture
  if (
    record
    && typeof isSameArrivalDeparture === 'function'
    && isSameArrivalDeparture(record)
    && (fieldKey === 'ARRIVEE' || fieldKey === 'DEPAR')
  ) {
    return SAME_TIME_HINT_KEYS
  }
  return FIELD_FORMAT_HINT_KEYS[fieldKey] || null
}

module.exports = {
  FIELD_FORMAT_HINT_KEYS,
  SAME_TIME_HINT_KEYS,
  isFormatHintField,
  getFormatHintKeys,
}
