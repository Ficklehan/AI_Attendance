const {
  normalizeWorkerNo,
  normalizePersonName,
  normalizeLabelText,
  normalizeRecordTextFields,
} = require('../shared-js/recognizedTextNormalizer')
const { normalizeDate } = require('../shared-js/recognizedDateNormalizer')
const { normalizeClockTime, normalizeShiftSchedule } = require('../shared-js/recognizedTimeNormalizer')

function applyFieldNormalization(target, field) {
  if (!target || !field) return target
  if (field === 'NO') target.NO = normalizeWorkerNo(target.NO)
  if (field === 'NOM_PRENOM') target.NOM_PRENOM = normalizePersonName(target.NOM_PRENOM)
  if (field === 'Entrepot') target.Entrepot = normalizeLabelText(target.Entrepot)
  if (field === 'AGENCE_INTERIMAIRE') target.AGENCE_INTERIMAIRE = normalizeLabelText(target.AGENCE_INTERIMAIRE)
  if (field === 'Date') target.Date = normalizeDate(target.Date)
  if (field === 'ARRIVEE' || field === 'DEPAR') {
    const next = normalizeClockTime(target[field])
    if (next !== undefined && next !== null) target[field] = next
  }
  if (field === 'HORAIRES_DU_TRAVAIL') {
    const next = normalizeShiftSchedule(target[field])
    if (next !== undefined && next !== null) target[field] = next
  }
  return target
}

module.exports = {
  normalizeWorkerNo,
  normalizePersonName,
  normalizeLabelText,
  normalizeDate,
  normalizeRecordTextFields,
  applyFieldNormalization,
}
