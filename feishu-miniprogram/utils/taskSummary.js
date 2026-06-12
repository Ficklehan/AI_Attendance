const { isApiSuccess, getApiData } = require('./response')
const { mapTaskListItem } = require('./task')
const { apiCall } = require('./request')

const EMPTY_SUMMARY = {
  processing: 0,
  review: 0,
  confirmed: 0,
  failed: 0,
  cancelled: 0,
  total: 0,
  allUsersScope: false
}

function normalizeSummary(raw) {
  if (!raw) return { ...EMPTY_SUMMARY }
  return {
    processing: Number(raw.processing) || 0,
    review: Number(raw.review) || 0,
    confirmed: Number(raw.confirmed) || 0,
    failed: Number(raw.failed) || 0,
    cancelled: Number(raw.cancelled) || 0,
    total: Number(raw.total) || 0,
    allUsersScope: !!raw.allUsersScope
  }
}

function toStatusSummary(summary) {
  const s = normalizeSummary(summary)
  return {
    processing: s.processing,
    review: s.review,
    completed: s.confirmed,
    failed: s.failed,
    cancelled: s.cancelled,
    total: s.total
  }
}

function fetchTaskSummary() {
  const { getAuthToken } = require('./request')
  if (!getAuthToken()) {
    return Promise.resolve({ ...EMPTY_SUMMARY })
  }
  return apiCall({ url: '/tasks/summary' })
    .then((res) => {
      if (!isApiSuccess(res.data)) {
        return { ...EMPTY_SUMMARY }
      }
      return normalizeSummary(getApiData(res.data))
    })
    .catch(() => ({ ...EMPTY_SUMMARY }))
}

function markTaskDataDirty() {
  try {
    const app = getApp()
    app.globalData.taskDataVersion = (app.globalData.taskDataVersion || 0) + 1
  } catch (e) {
    // ignore
  }
}

function shouldReloadTaskData(pageInstance) {
  try {
    const app = getApp()
    const version = app.globalData.taskDataVersion || 0
    if (pageInstance._taskDataVersion !== version) {
      pageInstance._taskDataVersion = version
      return true
    }
  } catch (e) {
    // ignore
  }
  return false
}

function fetchFirstReviewTask() {
  const { getAuthToken } = require('./request')
  if (!getAuthToken()) {
    return Promise.resolve(null)
  }
  return apiCall({
    url: '/tasks',
    data: { current: 1, size: 1, status: 'processed' }
  })
    .then((res) => {
      if (!isApiSuccess(res.data)) {
        return null
      }
      const page = getApiData(res.data) || {}
      const records = page.records || []
      return records.length > 0 ? mapTaskListItem(records[0]) : null
    })
    .catch(() => null)
}

function tabCountFromSummary(tab, summary) {
  const s = normalizeSummary(summary)
  const normalized = tab === 'tosubmit' ? 'review' : tab
  if (normalized === 'pending') return s.processing
  if (normalized === 'review') return s.review
  if (normalized === 'completed') return s.confirmed
  if (normalized === 'failed') return s.failed
  return s.total
}

module.exports = {
  EMPTY_SUMMARY,
  normalizeSummary,
  toStatusSummary,
  fetchTaskSummary,
  fetchFirstReviewTask,
  tabCountFromSummary,
  markTaskDataDirty,
  shouldReloadTaskData
}
