const CALIBRATABLE_FIELDS = [
  'NO', 'Pays', 'Entrepot', 'NOM_PRENOM', 'AGENCE_INTERIMAIRE', 'HORAIRES_DU_TRAVAIL',
  'Date', 'ARRIVEE', 'DEPAR', 'PAUSE', 'SIGNATURE', 'Observations'
]

const FIELD_LABEL_KEYS = {
  NO: 'result.fieldWorkerNo',
  Pays: 'result.fieldCountry',
  Entrepot: 'result.fieldWarehouse',
  NOM_PRENOM: 'result.fieldName',
  AGENCE_INTERIMAIRE: 'result.fieldAgency',
  HORAIRES_DU_TRAVAIL: 'result.fieldShift',
  Date: 'result.fieldDate',
  ARRIVEE: 'result.fieldArrival',
  DEPAR: 'result.fieldDeparture',
  PAUSE: 'result.fieldBreak',
  SIGNATURE: 'result.fieldSignature',
  Observations: 'result.fieldObservations'
}

function normalizeCalibValue(value) {
  if (value === undefined || value === null) return ''
  return String(value).trim()
}

module.exports = {
  CALIBRATABLE_FIELDS,
  FIELD_LABEL_KEYS,
  normalizeCalibValue
}
