/** 员工记录可校准字段（与后端 CALIBRATABLE_FIELDS 一致） */
export const CALIBRATABLE_FIELDS = [
  'NO',
  'Pays',
  'Entrepot',
  'NOM_PRENOM',
  'AGENCE_INTERIMAIRE',
  'HORAIRES_DU_TRAVAIL',
  'Date',
  'ARRIVEE',
  'DEPAR',
  'PAUSE',
  'SIGNATURE',
  'Observations',
]

export const FIELD_LABEL_KEYS = {
  NO: 'taskEdit.workerNumber',
  Pays: 'taskEdit.countryField',
  Entrepot: 'taskEdit.warehouse',
  NOM_PRENOM: 'taskEdit.name',
  AGENCE_INTERIMAIRE: 'taskEdit.agency',
  HORAIRES_DU_TRAVAIL: 'taskEdit.shift',
  Date: 'taskEdit.date',
  ARRIVEE: 'taskEdit.arrival',
  DEPAR: 'taskEdit.departure',
  PAUSE: 'taskEdit.breakTime',
  SIGNATURE: 'taskEdit.signature',
  Observations: 'taskEdit.observations',
}

export function normalizeCalibValue(value) {
  if (value === null || value === undefined) return ''
  return String(value).trim()
}
