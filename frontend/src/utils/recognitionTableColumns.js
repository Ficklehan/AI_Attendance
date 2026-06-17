/** 识别结果表格列定义（Home / TaskEdit 共用，随语言切换） */

import { appendRequiredMark, DEFAULT_CONFIRM_VALIDATION } from './requiredRecordFields'
import { withTableSorters } from './tableSort'
import { getRecognitionFieldFilterMeta } from './fieldFilterValue'

function requiredTitle(t, key) {
  return appendRequiredMark(t(key))
}

function bindCellStyle(cellStyle, columnKey) {
  if (!cellStyle) return undefined
  return (record, rowIndex) => cellStyle(record, rowIndex, columnKey)
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
      customCell: bindCellStyle(cellStyle, 'serialNo'),
    },
    col({ title: t('taskEdit.pageNumber'), dataIndex: 'PAGE_NUM', key: 'PAGE_NUM', customCell: bindCellStyle(cellStyle, 'PAGE_NUM') }),
    col({ title: titleFor('NO', 'taskEdit.workerNumber'), dataIndex: 'NO', key: 'NO', customCell: bindCellStyle(cellStyle, 'NO') }),
    requiredCol('Pays', { title: titleFor('Pays', 'taskEdit.countryField'), dataIndex: 'Pays', key: 'Pays', customCell: bindCellStyle(cellStyle, 'Pays') }),
    requiredCol('Entrepot', { title: titleFor('Entrepot', 'taskEdit.warehouse'), dataIndex: 'Entrepot', key: 'Entrepot', customCell: bindCellStyle(cellStyle, 'Entrepot') }),
    requiredCol('Date', {
      title: titleFor('Date', 'taskEdit.date'),
      dataIndex: 'Date',
      key: 'Date',
      formatHintTooltipKey: 'fieldFormat.dateTooltip',
      customCell: bindCellStyle(cellStyle, 'Date'),
    }),
    requiredCol('NOM_PRENOM', {
      title: titleFor('NOM_PRENOM', 'taskEdit.name'),
      dataIndex: 'NOM_PRENOM',
      key: 'NOM_PRENOM',
      ellipsis: false,
      customCell: bindCellStyle(cellStyle, 'NOM_PRENOM'),
    }),
    requiredCol('AGENCE_INTERIMAIRE', {
      title: titleFor('AGENCE_INTERIMAIRE', 'taskEdit.agency'),
      dataIndex: 'AGENCE_INTERIMAIRE',
      key: 'AGENCE_INTERIMAIRE',
      customCell: bindCellStyle(cellStyle, 'AGENCE_INTERIMAIRE'),
    }),
    requiredCol('HORAIRES_DU_TRAVAIL', {
      title: titleFor('HORAIRES_DU_TRAVAIL', 'taskEdit.shift'),
      dataIndex: 'HORAIRES_DU_TRAVAIL',
      key: 'HORAIRES_DU_TRAVAIL',
      formatHintTooltipKey: 'fieldFormat.shiftTooltip',
      customCell: bindCellStyle(cellStyle, 'HORAIRES_DU_TRAVAIL'),
    }),
    requiredCol('ARRIVEE', {
      title: titleFor('ARRIVEE', 'taskEdit.arrival'),
      dataIndex: 'ARRIVEE',
      key: 'ARRIVEE',
      formatHintTooltipKey: 'fieldFormat.arrivalTooltip',
      customCell: bindCellStyle(cellStyle, 'ARRIVEE'),
    }),
    requiredCol('DEPAR', {
      title: titleFor('DEPAR', 'taskEdit.departure'),
      dataIndex: 'DEPAR',
      key: 'DEPAR',
      formatHintTooltipKey: 'fieldFormat.departureTooltip',
      customCell: bindCellStyle(cellStyle, 'DEPAR'),
    }),
    requiredCol('PAUSE', { title: titleFor('PAUSE', 'taskEdit.breakTime'), dataIndex: 'PAUSE', key: 'PAUSE', ellipsis: false, customCell: bindCellStyle(cellStyle, 'PAUSE') }),
  ]

  if (includeWorkHours) {
    cols.push({ title: t('taskEdit.workHours'), key: 'workHours', ellipsis: false, customCell: bindCellStyle(cellStyle, 'workHours') })
  }

  cols.push(
    col({ title: t('taskEdit.signature'), dataIndex: 'SIGNATURE', key: 'SIGNATURE', customCell: bindCellStyle(cellStyle, 'SIGNATURE') }),
    col({ title: t('taskEdit.observations'), dataIndex: 'Observations', key: 'Observations', customCell: bindCellStyle(cellStyle, 'Observations') }),
  )

  if (includeAnomalyReasons) {
    cols.push({ title: t('taskEdit.anomalyReasons'), key: 'anomalyReasons', ellipsis: false, customCell: bindCellStyle(cellStyle, 'anomalyReasons') })
  }

  cols.push({ title: t('taskEdit.mark'), dataIndex: 'SmartMark', key: 'SmartMark', customCell: bindCellStyle(cellStyle, 'SmartMark') })

  if (includeAction) {
    cols.push({
      title: t('taskEdit.action'),
      key: 'action',
      autoWidth: false,
      width: options.actionColumnWidth || 56,
      fixed: fixedAction ? 'right' : undefined,
      align: fixedAction ? 'center' : undefined,
      customCell: bindCellStyle(cellStyle, 'action'),
    })
  }

  return withTableSorters(cols, {
    skipKeys: includeAction ? [] : ['action'],
  })
}
