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
    compactIdentityColumns = false,
    includeSerialNoColumn = true,
  } = options

  const requiredKeys = requiredFieldKeys || []

  const filterMeta = getRecognitionFieldFilterMeta()
  const withSearch = (col) => {
    if (!searchFields || !col.dataIndex) return col
    const key = col.key || col.dataIndex
    if (compactIdentityColumns && ['PAGE_NUM', 'NO', 'EMPLOYEE_NO', 'serialNo'].includes(key)) {
      return col
    }
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

  const identity = compactIdentityColumns
    ? {
      serialNo: {
        width: 32,
        autoWidth: false,
        density: 'compact',
        align: 'center',
        className: 'col-micro-id',
        sorter: false,
      },
      PAGE_NUM: {
        width: 32,
        autoWidth: false,
        minWidth: 28,
        maxWidth: 36,
        density: 'compact',
        align: 'center',
        className: 'col-micro-id',
        sorter: false,
      },
      NO: {
        width: 32,
        autoWidth: false,
        minWidth: 28,
        maxWidth: 36,
        density: 'compact',
        align: 'center',
        className: 'col-micro-id',
        sorter: false,
      },
      EMPLOYEE_NO: {
        width: 56,
        autoWidth: false,
        minWidth: 48,
        maxWidth: 64,
        density: 'compact',
        className: 'col-micro-id',
        sorter: false,
      },
      Pays: { width: 76, autoWidth: false, minWidth: 68, maxWidth: 88, density: 'compact' },
    }
    : {
      serialNo: { width: 44, autoWidth: false },
      PAGE_NUM: {},
      NO: { maxWidth: 96, minWidth: 88, align: 'center' },
      EMPLOYEE_NO: {},
      Pays: { maxWidth: 84, minWidth: 60 },
    }

  const timeCompact = compactIdentityColumns
    ? {
      Date: { width: 100, autoWidth: false, minWidth: 92, maxWidth: 108, density: 'compact' },
      HORAIRES_DU_TRAVAIL: { width: 92, autoWidth: false, minWidth: 84, maxWidth: 100, density: 'compact' },
      ARRIVEE: { width: 96, autoWidth: false, minWidth: 88, maxWidth: 108, density: 'compact', align: 'center' },
      DEPAR: { width: 96, autoWidth: false, minWidth: 88, maxWidth: 108, density: 'compact', align: 'center' },
      PAUSE: { width: 48, autoWidth: false, minWidth: 44, maxWidth: 56, density: 'compact', align: 'center' },
      workHours: { width: 48, autoWidth: false, minWidth: 44, maxWidth: 56, density: 'compact', align: 'center' },
      Observations: { width: 52, autoWidth: false, minWidth: 44, maxWidth: 64, density: 'compact' },
      SIGNATURE: { width: 52, autoWidth: false, minWidth: 44, maxWidth: 60, density: 'compact' },
    }
    : {}

  const cols = []

  if (includeSerialNoColumn) {
    cols.push({
      title: t('common.serialNumber'),
      key: 'serialNo',
      align: 'center',
      sorter: false,
      customCell: bindCellStyle(cellStyle, 'serialNo'),
      ...identity.serialNo,
    })
  }

  cols.push(
    col({
      title: t('taskEdit.pageNumber'),
      dataIndex: 'PAGE_NUM',
      key: 'PAGE_NUM',
      customCell: bindCellStyle(cellStyle, 'PAGE_NUM'),
      ...identity.PAGE_NUM,
    }),
    col({
      title: titleFor('NO', 'taskEdit.workerNumber'),
      dataIndex: 'NO',
      key: 'NO',
      customCell: bindCellStyle(cellStyle, 'NO'),
      ...identity.NO,
    }),
    col({
      title: t('taskEdit.employeeNo'),
      dataIndex: 'EMPLOYEE_NO',
      key: 'EMPLOYEE_NO',
      customCell: bindCellStyle(cellStyle, 'EMPLOYEE_NO'),
      ...identity.EMPLOYEE_NO,
    }),
    requiredCol('Pays', {
      title: titleFor('Pays', 'taskEdit.countryField'),
      dataIndex: 'Pays',
      key: 'Pays',
      customCell: bindCellStyle(cellStyle, 'Pays'),
      ...identity.Pays,
    }),
    requiredCol('Entrepot', {
      title: titleFor('Entrepot', 'taskEdit.warehouse'),
      dataIndex: 'Entrepot',
      key: 'Entrepot',
      maxWidth: compactIdentityColumns ? 120 : undefined,
      customCell: bindCellStyle(cellStyle, 'Entrepot'),
    }),
    requiredCol('Date', {
      title: titleFor('Date', 'taskEdit.date'),
      dataIndex: 'Date',
      key: 'Date',
      formatHintTooltipKey: 'fieldFormat.dateTooltip',
      maxWidth: compactIdentityColumns ? 108 : undefined,
      customCell: bindCellStyle(cellStyle, 'Date'),
      ...timeCompact.Date,
    }),
    requiredCol('NOM_PRENOM', {
      title: titleFor('NOM_PRENOM', 'taskEdit.name'),
      dataIndex: 'NOM_PRENOM',
      key: 'NOM_PRENOM',
      ellipsis: false,
      maxWidth: compactIdentityColumns ? 160 : undefined,
      customCell: bindCellStyle(cellStyle, 'NOM_PRENOM'),
    }),
    requiredCol('AGENCE_INTERIMAIRE', {
      title: titleFor('AGENCE_INTERIMAIRE', 'taskEdit.agency'),
      dataIndex: 'AGENCE_INTERIMAIRE',
      key: 'AGENCE_INTERIMAIRE',
      maxWidth: compactIdentityColumns ? 120 : undefined,
      customCell: bindCellStyle(cellStyle, 'AGENCE_INTERIMAIRE'),
    }),
    requiredCol('HORAIRES_DU_TRAVAIL', {
      title: titleFor('HORAIRES_DU_TRAVAIL', 'taskEdit.shift'),
      dataIndex: 'HORAIRES_DU_TRAVAIL',
      key: 'HORAIRES_DU_TRAVAIL',
      formatHintTooltipKey: 'fieldFormat.shiftTooltip',
      maxWidth: compactIdentityColumns ? 100 : undefined,
      density: compactIdentityColumns ? 'compact' : undefined,
      customCell: bindCellStyle(cellStyle, 'HORAIRES_DU_TRAVAIL'),
      ...timeCompact.HORAIRES_DU_TRAVAIL,
    }),
    requiredCol('ARRIVEE', {
      title: titleFor('ARRIVEE', 'taskEdit.arrival'),
      dataIndex: 'ARRIVEE',
      key: 'ARRIVEE',
      formatHintTooltipKey: 'fieldFormat.arrivalTooltip',
      maxWidth: compactIdentityColumns ? 108 : undefined,
      density: compactIdentityColumns ? 'compact' : undefined,
      customCell: bindCellStyle(cellStyle, 'ARRIVEE'),
      ...timeCompact.ARRIVEE,
    }),
    requiredCol('DEPAR', {
      title: titleFor('DEPAR', 'taskEdit.departure'),
      dataIndex: 'DEPAR',
      key: 'DEPAR',
      formatHintTooltipKey: 'fieldFormat.departureTooltip',
      maxWidth: compactIdentityColumns ? 108 : undefined,
      density: compactIdentityColumns ? 'compact' : undefined,
      customCell: bindCellStyle(cellStyle, 'DEPAR'),
      ...timeCompact.DEPAR,
    }),
    requiredCol('PAUSE', {
      title: titleFor('PAUSE', 'taskEdit.breakTime'),
      dataIndex: 'PAUSE',
      key: 'PAUSE',
      ellipsis: false,
      maxWidth: compactIdentityColumns ? 56 : undefined,
      density: compactIdentityColumns ? 'compact' : undefined,
      customCell: bindCellStyle(cellStyle, 'PAUSE'),
      ...timeCompact.PAUSE,
    }),
  )

  if (includeWorkHours) {
    cols.push({
      title: t('taskEdit.workHours'),
      key: 'workHours',
      ellipsis: false,
      maxWidth: compactIdentityColumns ? 56 : undefined,
      density: compactIdentityColumns ? 'compact' : undefined,
      customCell: bindCellStyle(cellStyle, 'workHours'),
      ...timeCompact.workHours,
    })
  }

  cols.push(
    col({
      title: t('taskEdit.signature'),
      dataIndex: 'SIGNATURE',
      key: 'SIGNATURE',
      customCell: bindCellStyle(cellStyle, 'SIGNATURE'),
      ...(timeCompact.SIGNATURE || { maxWidth: compactIdentityColumns ? 60 : undefined }),
    }),
    col({
      title: t('taskEdit.observations'),
      dataIndex: 'Observations',
      key: 'Observations',
      customCell: bindCellStyle(cellStyle, 'Observations'),
      ...(timeCompact.Observations || { maxWidth: compactIdentityColumns ? 64 : undefined }),
    }),
  )

  if (includeAnomalyReasons) {
    const anomalyCol = {
      title: t('taskEdit.anomalyReasons'),
      key: 'anomalyReasons',
      ellipsis: false,
      customCell: bindCellStyle(cellStyle, 'anomalyReasons'),
    }
    if (options.fixedAnomalyReasons) {
      anomalyCol.autoWidth = false
      anomalyCol.width = options.anomalyReasonsColumnWidth || 220
      anomalyCol.minWidth = options.anomalyReasonsColumnWidth || 220
      anomalyCol.fixed = 'right'
      anomalyCol.align = 'center'
    } else {
      anomalyCol.minWidth = 160
    }
    cols.push(anomalyCol)
  }

  if (options.useExceptionTypeColumn) {
    cols.push({
      // 确认提交时异常类型必填，表头展示 * 提示用户
      title: appendRequiredMark(t('taskEdit.mark')),
      dataIndex: 'ExceptionType',
      key: 'ExceptionType',
      className: 'col-required-header',
      autoWidth: false,
      width: options.exceptionTypeColumnWidth || 96,
      fixed: 'right',
      align: 'center',
      ellipsis: false,
      customCell: bindCellStyle(cellStyle, 'ExceptionType'),
    })
  } else {
    cols.push({
      title: t('taskEdit.recognitionMark'),
      dataIndex: 'SmartMark',
      key: 'SmartMark',
      customCell: bindCellStyle(cellStyle, 'SmartMark'),
    })
  }

  if (includeAction) {
    const hasRightFixedPeer = cols.some((col) => col.fixed === 'right')
    cols.push({
      title: t('taskEdit.action'),
      key: 'action',
      autoWidth: false,
      width: options.actionColumnWidth || 40,
      fixed: fixedAction || hasRightFixedPeer ? 'right' : undefined,
      align: 'center',
      customCell: bindCellStyle(cellStyle, 'action'),
    })
  }

  const firstRightFixedIdx = cols.findIndex((col) => col.fixed === 'right')
  const normalizedCols = firstRightFixedIdx < 0
    ? cols
    : cols.map((col, idx) => (
      idx >= firstRightFixedIdx && col.fixed !== 'right'
        ? { ...col, fixed: 'right' }
        : col
    ))

  return withTableSorters(normalizedCols, {
    skipKeys: includeAction ? [] : ['action'],
  })
}
