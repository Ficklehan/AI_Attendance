const App = getApp()
const { t } = require('../../utils/i18n')
const { isApiSuccess, getApiData, getApiMessage } = require('../../utils/response')
const { parseRecords } = require('../../utils/task')
const { isAbsentRow } = require('../../utils/recordDisplay')
const {
  CALIBRATABLE_FIELDS,
  FIELD_LABEL_KEYS,
  normalizeCalibValue
} = require('../../utils/calibratableFields')

Page({
  data: {
    taskId: '',
    rowKey: '',
    draft: {},
    fields: [],
    saving: false,
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
    if (!this.loadFromResultPage()) {
      this.loadFromApi()
    }
  },

  refreshTexts: function () {
    this.setData({
      texts: {
        hint: t('recordEdit.hint'),
        save: t('recordEdit.save'),
        cancel: t('common.cancel')
      }
    })
  },

  loadFromResultPage: function () {
    const pages = getCurrentPages()
    const prev = pages.length > 1 ? pages[pages.length - 2] : null
    if (!prev || !prev.data || !Array.isArray(prev.data.records)) {
      return false
    }
    if (prev.data.taskId !== this.data.taskId || !prev.data.canSubmit) {
      return false
    }
    const record = prev.data.records.find((r) => r._rowKey === this.data.rowKey)
    if (!record) {
      return false
    }
    if (record.isDeleted || isAbsentRow(record)) {
      tt.showToast({ title: t('recordEdit.notEditable'), icon: 'none' })
      setTimeout(() => tt.navigateBack(), 600)
      return true
    }
    this.initDraft(record)
    return true
  },

  loadFromApi: function () {
    tt.showLoading({ title: t('common.loading') })
    tt.request({
      url: `${App.globalData.baseUrl}/tasks/${this.data.taskId}`,
      header: {
        Authorization: App.globalData.token ? `Bearer ${App.globalData.token}` : ''
      },
      success: (res) => {
        if (!isApiSuccess(res.data)) {
          tt.showToast({ title: getApiMessage(res.data, t('result.loadFail')), icon: 'none' })
          return
        }
        const task = getApiData(res.data) || {}
        if (task.status !== 'processed') {
          tt.showToast({ title: t('recordEdit.notProcessed'), icon: 'none' })
          return
        }
        const records = parseRecords(task.rawData || task.confirmedData)
        const record = records.find((r) => r._rowKey === this.data.rowKey)
        if (!record) {
          tt.showToast({ title: t('recordEdit.recordNotFound'), icon: 'none' })
          return
        }
        if (record.isDeleted || isAbsentRow(record)) {
          tt.showToast({ title: t('recordEdit.notEditable'), icon: 'none' })
          return
        }
        this.initDraft(record)
      },
      fail: () => {
        tt.showToast({ title: t('common.networkFail'), icon: 'none' })
      },
      complete: () => tt.hideLoading()
    })
  },

  initDraft: function (record) {
    const draft = {}
    CALIBRATABLE_FIELDS.forEach((key) => {
      const v = record[key]
      draft[key] = v === undefined || v === null ? '' : v
    })
    this.setData({ draft }, () => this.rebuildForm())
  },

  rebuildForm: function () {
    const fields = CALIBRATABLE_FIELDS.map((key) => ({
      key,
      label: t(FIELD_LABEL_KEYS[key] || key),
      value: this.data.draft[key] === undefined || this.data.draft[key] === null
        ? ''
        : String(this.data.draft[key])
    }))
    this.setData({ fields })
  },

  onFieldInput: function (e) {
    const key = e.currentTarget.dataset.key
    const value = e.detail.value
    const draft = { ...this.data.draft, [key]: value }
    this.setData({ draft }, () => this.rebuildForm())
  },

  buildDraftPayload: function () {
    const payload = {}
    CALIBRATABLE_FIELDS.forEach((key) => {
      let value = this.data.draft[key]
      if (key === 'PAUSE' && value !== '' && value !== null && value !== undefined) {
        const n = Number(String(value).replace(',', '.'))
        if (!Number.isNaN(n)) {
          value = Math.round(n)
        }
      }
      payload[key] = value
    })
    return payload
  },

  save: function () {
    if (this.data.saving) return
    const pages = getCurrentPages()
    const prev = pages.length > 1 ? pages[pages.length - 2] : null
    if (!prev || typeof prev.applyRecordDraft !== 'function') {
      tt.showToast({ title: t('recordEdit.saveFail'), icon: 'none' })
      return
    }
    this.setData({ saving: true })
    const draft = this.buildDraftPayload()
    const changed = CALIBRATABLE_FIELDS.some((key) => {
      const record = (prev.data.records || []).find((r) => r._rowKey === this.data.rowKey)
      if (!record) return true
      return normalizeCalibValue(record[key]) !== normalizeCalibValue(draft[key])
    })
    if (!changed) {
      tt.showToast({ title: t('recordEdit.noChanges'), icon: 'none' })
      this.setData({ saving: false })
      return
    }
    prev.applyRecordDraft(this.data.rowKey, draft)
    tt.showToast({ title: t('recordEdit.success'), icon: 'success' })
    setTimeout(() => {
      this.setData({ saving: false })
      tt.navigateBack()
    }, 400)
  },

  goBack: function () {
    tt.navigateBack()
  }
})
