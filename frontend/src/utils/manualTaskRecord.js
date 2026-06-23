import { defaultPaysLabel } from './countryDefaults'

function pickMostCommon(values) {
  const counts = new Map()
  for (const value of values) {
    const text = String(value ?? '').trim()
    if (!text) continue
    counts.set(text, (counts.get(text) || 0) + 1)
  }
  let best = ''
  let bestCount = 0
  for (const [text, count] of counts) {
    if (count > bestCount) {
      best = text
      bestCount = count
    }
  }
  return best
}

/** 任务详情手动补行：识别遗漏时可插入空白可编辑行 */
export function createManualTaskRecord({ taskId, taskCountry, existingRecords = [] }) {
  const manualCount = existingRecords.filter((row) => row?._manuallyAdded).length
  const rowKey = `${taskId}-manual-${Date.now()}-${manualCount + 1}`

  const pays = defaultPaysLabel(taskCountry)
    || pickMostCommon(existingRecords.map((row) => row?.Pays))
  const entrepot = pickMostCommon(existingRecords.map((row) => row?.Entrepot))
  const date = pickMostCommon(existingRecords.map((row) => row?.Date))
  const agency = pickMostCommon(existingRecords.map((row) => row?.AGENCE_INTERIMAIRE))

  return {
    NO: '',
    NOM_PRENOM: '',
    Pays: pays,
    Entrepot: entrepot,
    Date: date,
    AGENCE_INTERIMAIRE: agency,
    HORAIRES_DU_TRAVAIL: '',
    ARRIVEE: '',
    DEPAR: '',
    PAUSE: null,
    SIGNATURE: '未签字',
    CHECKER: '未签字',
    Observations: '',
    PAGE_NUM: '',
    SmartMark: '正常',
    Mark: '正常',
    isDeleted: false,
    anomalies: [],
    _rowKey: rowKey,
    _manuallyAdded: true,
    _baseName: '',
    _nameAutoNumbered: false,
    _duplicateConfirmedUnique: false,
  }
}
