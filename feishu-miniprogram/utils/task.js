const { t } = require('./i18n')
const { buildTaskImageList } = require('./imageUrl')
const { appendSyncToStatusText, syncStatusMeta } = require('./syncStatus')

function getAppSafe() {
  try {
    return getApp()
  } catch (e) {
    return null
  }
}

function statusMeta(status) {
  const map = {
    processing: { textKey: 'task.statusProcessing', tag: 'badge-warning' },
    processed: { textKey: 'task.statusProcessed', tag: 'badge-warning' },
    confirmed: { textKey: 'task.statusConfirmed', tag: 'badge-success' },
    failed: { textKey: 'task.statusFailed', tag: 'badge-error' },
    cancelled: { textKey: 'task.statusCancelled', tag: 'badge-neutral' }
  }
  const item = map[status]
  if (item) {
    return { text: t(item.textKey), tag: item.tag }
  }
  return { text: status || t('task.statusUnknown'), tag: 'badge-neutral' }
}

function normalizeTasksTab(tab) {
  if (!tab || tab === 'all') return 'all'
  if (tab === 'tosubmit') return 'review'
  return tab
}

function formatTime(time) {
  if (!time) return ''
  const date = new Date(time)
  if (Number.isNaN(date.getTime())) return ''
  const month = date.getMonth() + 1
  const day = date.getDate()
  const hour = `${date.getHours()}`.padStart(2, '0')
  const minute = `${date.getMinutes()}`.padStart(2, '0')
  return `${month}月${day}日 ${hour}:${minute}`
}

function shortenName(fileKey, taskId) {
  const raw = fileKey || taskId || '考勤任务'
  if (raw.length <= 22) return raw
  return `${raw.slice(0, 22)}…`
}

function mapTaskListItem(dto) {
  if (!dto) return null
  const status = dto.status || ''
  const meta = statusMeta(status)
  const syncStatus = dto.syncStatus || 'none'
  const statusText = meta.text
  let syncLabel = ''
  if (status === 'confirmed' && syncStatus && syncStatus !== 'none') {
    const shortKey = {
      pending: 'sync.statusPendingShort',
      synced: 'sync.statusSyncedShort',
      sync_failed: 'sync.statusFailedShort'
    }[syncStatus]
    if (shortKey) {
      const short = t(shortKey)
      syncLabel = short && short !== shortKey ? short : ''
    }
  }
  let listVariant = ''
  if (status === 'processed') listVariant = 'attention'
  else if (status === 'processing') listVariant = 'processing'

  return {
    id: dto.taskId,
    taskId: dto.taskId,
    name: shortenName(dto.fileKey, dto.taskId),
    createTime: dto.createdAt,
    status,
    syncStatus,
    syncDotClass: syncDotClass(syncStatus, status),
    statusText,
    statusTextFull: appendSyncToStatusText(meta.text, status, syncStatus),
    syncLabel,
    statusTag: meta.tag,
    timeText: formatTime(dto.createdAt),
    userName: dto.userName || '',
    listVariant
  }
}

function mapTaskList(records) {
  return (records || []).map(mapTaskListItem).filter(Boolean)
}

function tabStatusParam(tab) {
  const normalized = normalizeTasksTab(tab)
  if (normalized === 'pending') return 'processing'
  if (normalized === 'review') return 'processed'
  if (normalized === 'completed') return 'confirmed'
  return ''
}

function syncDotClass(syncStatus, status) {
  if (status !== 'confirmed') return ''
  if (syncStatus === 'pending') return 'sync-dot-pending'
  if (syncStatus === 'synced') return 'sync-dot-ok'
  if (syncStatus === 'sync_failed') return 'sync-dot-err'
  return ''
}

function filterTasksByTab(tasks, tab) {
  const normalized = normalizeTasksTab(tab)
  if (normalized === 'all') return tasks
  if (normalized === 'pending') {
    return tasks.filter((item) => item.status === 'processing')
  }
  if (normalized === 'review') {
    return tasks.filter((item) => item.status === 'processed')
  }
  if (normalized === 'completed') {
    return tasks.filter((item) => item.status === 'confirmed')
  }
  return tasks
}

function parseRecords(rawData) {
  if (!rawData) return []
  try {
    const parsed = typeof rawData === 'string' ? JSON.parse(rawData) : rawData
    const list = Array.isArray(parsed) ? parsed : []
    const { getCountry } = require('./preferences')
    const { applyMissingPays } = require('./countries')
    const workingCountry = getCountry()
    return list.map((record) => applyMissingPays(record, workingCountry))
  } catch (e) {
    console.error('解析任务记录失败', e)
    return []
  }
}

const {
  buildDisplayRecords: buildDisplayRecordsRich,
  isAbsentRow,
  getMarkTag
} = require('./recordDisplay')

function buildDisplayRecords(records, maxCount) {
  return buildDisplayRecordsRich(records, maxCount)
}

function mapTaskDetail(task, images) {
  const status = task.status || ''
  const syncStatus = task.syncStatus || 'none'
  const meta = statusMeta(status)
  const statusText = appendSyncToStatusText(meta.text, status, syncStatus)
  const imageItems = images || []
  const imageUrl = imageItems.length > 0 ? imageItems[0].url : ''
  return {
    name: shortenName(task.fileKey, task.taskId),
    status,
    syncStatus,
    syncError: task.syncError || '',
    statusText,
    statusTag: meta.tag,
    createTime: task.createdAt,
    timeText: formatTime(task.createdAt),
    originalImage: imageUrl,
    imageCount: imageItems.length
  }
}

module.exports = {
  formatTime,
  mapTaskListItem,
  mapTaskList,
  tabStatusParam,
  normalizeTasksTab,
  filterTasksByTab,
  parseRecords,
  buildDisplayRecords,
  mapTaskDetail,
  statusMeta,
  isAbsentRow,
  getMarkTag
}
