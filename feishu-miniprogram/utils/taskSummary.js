const { isApiSuccess, getApiData } = require('./response')
const { mapTaskListItem } = require('./task')

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
  const app = getApp()
  const base = app.globalData.baseUrl
  const token = app.globalData.token
  if (!base || !token) {
    return Promise.resolve({ ...EMPTY_SUMMARY })
  }
  return new Promise((resolve) => {
    tt.request({
      url: `${base}/tasks/summary`,
      header: { Authorization: `Bearer ${token}` },
      success: (res) => {
        if (!isApiSuccess(res.data)) {
          resolve({ ...EMPTY_SUMMARY })
          return
        }
        resolve(normalizeSummary(getApiData(res.data)))
      },
      fail: () => resolve({ ...EMPTY_SUMMARY })
    })
  })
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
  const app = getApp()
  const base = app.globalData.baseUrl
  const token = app.globalData.token
  if (!base || !token) {
    return Promise.resolve(null)
  }
  return new Promise((resolve) => {
    tt.request({
      url: `${base}/tasks`,
      data: { current: 1, size: 1, status: 'processed' },
      header: { Authorization: `Bearer ${token}` },
      success: (res) => {
        if (!isApiSuccess(res.data)) {
          resolve(null)
          return
        }
        const page = getApiData(res.data) || {}
        const records = page.records || []
        const first = records.length > 0 ? mapTaskListItem(records[0]) : null
        resolve(first)
      },
      fail: () => resolve(null)
    })
  })
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
