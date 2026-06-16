const { t } = require('../../utils/i18n')
const { isApiSuccess, getApiData, getApiMessage } = require('../../utils/response')
const { apiCall } = require('../../utils/request')
const { parseRecords } = require('../../utils/task')
const {
  CALIBRATABLE_FIELDS,
  normalizeCalibValue
} = require('../../utils/calibratableFields')
const {
  buildCalibrationHistoryUi,
} = require('../../utils/calibrationHistory')
const { buildCalibFormFields } = require('../../utils/calibFormFields')
const { loadConfirmValidationConfig } = require('../../utils/confirmValidationConfig')

Page({
  data: {
    taskId: '',
    rowKey: '',
    reason: '',
    original: {},
    draft: {},
    fields: [],
    historyEntries: [],
    submitting: false,
    texts: {}
  },

  onLoad: function (options) {
    this.refreshTexts()
    const taskId = options.taskId || ''
    const rowKey = options.rowKey || ''
    if (!taskId || !rowKey) {
      tt.showToast({ title: t('result.missingTaskId'), icon: 'none' })
      return
    }
    this.setData({ taskId, rowKey })
    loadConfirmValidationConfig().then(() => this.loadRecord())
  },

  refreshTexts: function () {
    this.setData({
      texts: {
        hint: t('calibration.hint'),
        reason: t('calibration.reason'),
        reasonPlaceholder: t('calibration.reasonPlaceholder'),
        originalValue: t('calibration.originalValue'),
        historyTitle: t('calibration.historyTitle'),
        submit: t('calibration.submit'),
        cancel: t('common.cancel')
      }
    })
  },

  loadRecord: function () {
    const taskId = this.data.taskId
    tt.showLoading({ title: t('common.loading') })
    apiCall({ url: `/tasks/${taskId}` })
      .then((res) => {
        if (!isApiSuccess(res.data)) {
          tt.showToast({ title: getApiMessage(res.data, t('result.loadFail')), icon: 'none' })
          return
        }
        const task = getApiData(res.data) || {}
        if (task.status !== 'confirmed') {
          tt.showToast({ title: t('calibration.notConfirmed'), icon: 'none' })
          return
        }
        const payload = task.confirmedData || task.rawData
        const records = parseRecords(payload)
        const record = records.find((r) => r._rowKey === this.data.rowKey)
        if (!record) {
          tt.showToast({ title: t('calibration.recordNotFound'), icon: 'none' })
          return
        }
        const original = {}
        const draft = {}
        CALIBRATABLE_FIELDS.forEach((key) => {
          original[key] = record[key]
          draft[key] = record[key]
        })
        const historyEntries = buildCalibrationHistoryUi(record)
        this._sourceRecord = record
        this.setData({ original, draft, historyEntries }, () => this.rebuildForm())
      })
      .catch(() => {
        tt.showToast({ title: t('common.networkFail'), icon: 'none' })
      })
      .then(
        () => tt.hideLoading(),
        () => tt.hideLoading()
      )
  },

  rebuildForm: function () {
    const fields = buildCalibFormFields(this.data.draft, this._sourceRecord || {}, {
      includeOriginal: true,
    })
    this.setData({ fields })
  },

  onFieldInput: function (e) {
    const key = e.currentTarget.dataset.key
    const value = e.detail.value
    const draft = { ...this.data.draft, [key]: value }
    this.setData({ draft }, () => this.rebuildForm())
  },

  onReasonInput: function (e) {
    this.setData({ reason: e.detail.value })
  },

  buildUpdates: function () {
    const updates = {}
    CALIBRATABLE_FIELDS.forEach((key) => {
      const from = normalizeCalibValue(this.data.original[key])
      const to = normalizeCalibValue(this.data.draft[key])
      if (from !== to) {
        updates[key] = this.data.draft[key]
      }
    })
    return updates
  },

  submit: function () {
    if (this.data.submitting) return
    const reason = (this.data.reason || '').trim()
    if (!reason) {
      tt.showToast({ title: t('calibration.reasonRequired'), icon: 'none' })
      return
    }
    const updates = this.buildUpdates()
    if (Object.keys(updates).length === 0) {
      tt.showToast({ title: t('calibration.noChanges'), icon: 'none' })
      return
    }
    this.setData({ submitting: true })
    apiCall({
      url: `/tasks/${this.data.taskId}/calibrate-record`,
      method: 'POST',
      data: { rowKey: this.data.rowKey, updates, reason }
    })
      .then((res) => {
        if (isApiSuccess(res.data)) {
          tt.showToast({ title: t('calibration.success'), icon: 'success' })
          const pages = getCurrentPages()
          const prev = pages.length > 1 ? pages[pages.length - 2] : null
          if (prev && typeof prev.loadTaskResult === 'function') {
            prev.loadTaskResult(true)
          }
          setTimeout(() => tt.navigateBack(), 500)
        } else {
          tt.showToast({ title: getApiMessage(res.data, t('calibration.submitFail')), icon: 'none' })
        }
      })
      .catch(() => {
        tt.showToast({ title: t('common.networkFail'), icon: 'none' })
      })
      .then(
        () => this.setData({ submitting: false }),
        () => this.setData({ submitting: false })
      )
  },

  goBack: function () {
    tt.navigateBack()
  }
})
