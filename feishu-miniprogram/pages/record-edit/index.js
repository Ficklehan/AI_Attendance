const { t } = require('../../utils/i18n')
const { isApiSuccess, getApiData, getApiMessage } = require('../../utils/response')
const { apiCall } = require('../../utils/request')
const { parseRecords } = require('../../utils/task')
const { isAbsentRow, normalizePauseMinutes } = require('../../utils/recordDisplay')
const {
  CALIBRATABLE_FIELDS,
  normalizeCalibValue
} = require('../../utils/calibratableFields')
const { buildCalibFormFields } = require('../../utils/calibFormFields')
const { loadConfirmValidationConfig } = require('../../utils/confirmValidationConfig')

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
    loadConfirmValidationConfig().then(() => {
      if (!this.loadFromResultPage()) {
        this.loadFromApi()
      }
    })
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
    apiCall({ url: `/tasks/${this.data.taskId}` })
      .then((res) => {
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
      })
      .catch(() => {
        tt.showToast({ title: t('common.networkFail'), icon: 'none' })
      })
      .then(
        () => tt.hideLoading(),
        () => tt.hideLoading()
      )
  },

  initDraft: function (record) {
    this._sourceRecord = record
    const draft = {}
    CALIBRATABLE_FIELDS.forEach((key) => {
      const v = record[key]
      if (key === 'PAUSE') {
        const minutes = normalizePauseMinutes(v)
        draft[key] = minutes === '' ? '' : String(minutes)
        return
      }
      draft[key] = v === undefined || v === null ? '' : v
    })
    this.setData({ draft }, () => this.rebuildForm())
  },

  rebuildForm: function () {
    const fields = buildCalibFormFields(this.data.draft, this._sourceRecord || {})
    this.setData({ fields })
  },

  onFieldInput: function (e) {
    const key = e.currentTarget.dataset.key
    let value = e.detail.value
    if (key === 'PAUSE') {
      value = String(value || '').replace(/[^\d]/g, '')
    }
    const draft = { ...this.data.draft, [key]: value }
    const { applyFieldNormalization } = require('../../utils/recognizedFieldNormalize')
    applyFieldNormalization(draft, key)
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
