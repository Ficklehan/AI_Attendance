/** 识别结果表格列定义（Home / TaskEdit 共用，随语言切换） */

import { appendRequiredMark, DEFAULT_CONFIRM_VALIDATION } from './requiredRecordFields'
import { withTableSorters } from './tableSort'
import { getRecognitionFieldFilterMeta } from './fieldFilterValue'

function requiredTitle(t, key) {
  return appendRequiredMark(t(key))
}

export function buildRecognitionTableColumns(t, options = {}) {
  const {
    cellStyle,
    includeWorkHours = false,
    includeAnomalyReasons = true,
    includeAction = true,
    searchFields = false,
    fixedAction = false,
    requiredFieldKeys = DEFAULT_CONFIRM_VALIDATION.requiredFields,
  } = options

  const requiredKeys = requiredFieldKeys || []

  const filterMeta = getRecognitionFieldFilterMeta()
  const withSearch = (col) => {
    if (!searchFields || !col.dataIndex) return col
    const meta = filterMeta[col.dataIndex] || { filterType: 'text' }
    return { ...col, searchField: col.dataIndex, filterType: meta.filterType, filterOptionsKey: meta.filterOptionsKey }
  }

  const titleFor = (key, i18nKey) => (
    requiredKeys.includes(key) ? requiredTitle(t, i18nKey) : t(i18nKey)
  )

  const col = (def) => withSearch({ ellipsis: false, align: 'left', ...def })

  const requiredCol = (key, def) => col({
    ...def,
    className: requiredKeys.includes(key) ? 'col-required-header' : def.className,
  })

  const cols = [
    {
      title: t('common.serialNumber'),
      key: 'serialNo',
      width: 56,
      autoWidth: false,
      align: 'center',
      sorter: false,
      customCell: cellStyle,
    },
    col({ title: t('taskEdit.pageNumber'), dataIndex: 'PAGE_NUM', key: 'PAGE_NUM', customCell: cellStyle }),
    col({ title: titleFor('NO', 'taskEdit.workerNumber'), dataIndex: 'NO', key: 'NO', customCell: cellStyle }),
    requiredCol('Pays', { title: titleFor('Pays', 'taskEdit.countryField'), dataIndex: 'Pays', key: 'Pays', customCell: cellStyle }),
    requiredCol('Entrepot', { title: titleFor('Entrepot', 'taskEdit.warehouse'), dataIndex: 'Entrepot', key: 'Entrepot', customCell: cellStyle }),
    requiredCol('Date', { title: titleFor('Date', 'taskEdit.date'), dataIndex: 'Date', key: 'Date', customCell: cellStyle }),
    requiredCol('NOM_PRENOM', {
      title: titleFor('NOM_PRENOM', 'taskEdit.name'),
      dataIndex: 'NOM_PRENOM',
      key: 'NOM_PRENOM',
      ellipsis: false,
      customCell: cellStyle,
    }),
    requiredCol('AGENCE_INTERIMAIRE', {
      title: titleFor('AGENCE_INTERIMAIRE', 'taskEdit.agency'),
      dataIndex: 'AGENCE_INTERIMAIRE',
      key: 'AGENCE_INTERIMAIRE',
      customCell: cellStyle,
    }),
    requiredCol('HORAIRES_DU_TRAVAIL', {
      title: titleFor('HORAIRES_DU_TRAVAIL', 'taskEdit.shift'),
      dataIndex: 'HORAIRES_DU_TRAVAIL',
      key: 'HORAIRES_DU_TRAVAIL',
      customCell: cellStyle,
    }),
    requiredCol('ARRIVEE', { title: titleFor('ARRIVEE', 'taskEdit.arrival'), dataIndex: 'ARRIVEE', key: 'ARRIVEE', customCell: cellStyle }),
    requiredCol('DEPAR', { title: titleFor('DEPAR', 'taskEdit.departure'), dataIndex: 'DEPAR', key: 'DEPAR', customCell: cellStyle }),
    requiredCol('PAUSE', { title: titleFor('PAUSE', 'taskEdit.breakTime'), dataIndex: 'PAUSE', key: 'PAUSE', ellipsis: false, customCell: cellStyle }),
  ]

  if (includeWorkHours) {
    cols.push({ title: t('taskEdit.workHours'), key: 'workHours', ellipsis: false, customCell: cellStyle })
  }

  cols.push(
    col({ title: t('taskEdit.signature'), dataIndex: 'SIGNATURE', key: 'SIGNATURE', customCell: cellStyle }),
    col({ title: t('taskEdit.observations'), dataIndex: 'Observations', key: 'Observations', customCell: cellStyle }),
  )

  if (includeAnomalyReasons) {
    cols.push({ title: t('taskEdit.anomalyReasons'), key: 'anomalyReasons', ellipsis: false, customCell: cellStyle })
  }

  cols.push({ title: t('taskEdit.mark'), dataIndex: 'SmartMark', key: 'SmartMark', customCell: cellStyle })

  if (includeAction) {
    cols.push({
      title: t('taskEdit.action'),
      key: 'action',
      autoWidth: false,
      width: options.actionColumnWidth || 56,
      fixed: fixedAction ? 'right' : undefined,
      align: fixedAction ? 'center' : undefined,
      customCell: cellStyle,
    })
  }

  return withTableSorters(cols, {
    skipKeys: includeAction ? [] : ['action'],
  })
}
