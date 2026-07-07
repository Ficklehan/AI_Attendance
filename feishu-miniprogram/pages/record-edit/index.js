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
const { createManualTaskRecord } = require('../../utils/manualTaskRecord')
const { prepareRecordPlaceholders } = require('../../shared-js/fieldPlaceholder')
const { computeSignatureMark } = require('../../utils/recognitionLabels')
const { syncRecordPaysToTaskRegion } = require('../../utils/paysCountryPicker')
const { getCountry, syncCountryFromServer } = require('../../utils/preferences')
const {
  resolveTaskWorkRegionBindingCode,
  resolveManualRecordCountryCode,
} = require('../../utils/taskWorkRegion')

Page({
  data: {
    taskId: '',
    rowKey: '',
    mode: '',
    taskWorkRegionCode: '',
    paysExpanded: false,
    draft: {},
    fields: [],
    saving: false,
    texts: {}
  },

  onLoad: function (options) {
    this.refreshTexts()
    const taskId = options.taskId || ''
    const mode = options.mode || ''
    const rowKey = options.rowKey || ''

    if (mode === 'createManual') {
      if (!taskId) {
        tt.showToast({ title: t('result.missingTaskId'), icon: 'none' })
        return
      }
      this.setData({ taskId, mode })
      loadConfirmValidationConfig().then(() => this.initCreateManual())
      return
    }

    if (!taskId || !rowKey) {
      tt.showToast({ title: t('result.missingTaskId'), icon: 'none' })
      return
    }
    this.setData({ taskId, rowKey, mode })
    loadConfirmValidationConfig().then(() => {
      if (!this.loadFromResultPage()) {
        this.loadFromApi()
      }
    })
  },

  onShow: function () {
    this.refreshTexts()
    if (this.data.mode === 'createManual') {
      const prev = this.getResultPage()
      const taskForRegion = prev && prev._taskForRegion
      const regionCode = taskForRegion
        ? resolveTaskWorkRegionBindingCode(
          taskForRegion,
          getCountry(),
          (prev.data && prev.data.records) || [],
          false,
        )
        : resolveManualRecordCountryCode(taskForRegion, getCountry()) || getCountry() || ''
      if (regionCode && regionCode !== this.data.taskWorkRegionCode && this._sourceRecord) {
        this.setData({ taskWorkRegionCode: regionCode }, () => {
          const synced = syncRecordPaysToTaskRegion(this._sourceRecord, regionCode, false, 'processed')
          this.initDraft(synced)
        })
        return
      }
    }
    if (this._sourceRecord) {
      this.rebuildForm()
    }
  },

  refreshTexts: function () {
    const isAdd = this.data.mode === 'createManual'
    try {
      tt.setNavigationBarTitle({ title: t(isAdd ? 'recordEdit.addTitle' : 'result.editRecord') })
    } catch (e) {
      console.warn('setNavigationBarTitle failed', e)
    }
    this.setData({
      texts: {
        hint: t(isAdd ? 'recordEdit.addHint' : 'recordEdit.hint'),
        save: t('recordEdit.save'),
        cancel: t('common.cancel'),
        paysLockedHint: t('recordEdit.paysLockedHint'),
      }
    })
  },

  getResultPage: function () {
    const pages = getCurrentPages()
    const prev = pages.length > 1 ? pages[pages.length - 2] : null
    if (!prev || !prev.data || prev.data.taskId !== this.data.taskId) {
      return null
    }
    return prev
  },

  initCreateManual: function () {
    const prev = this.getResultPage()
    if (!prev || !prev.data.canSubmit) {
      tt.showToast({ title: t('recordEdit.notProcessed'), icon: 'none' })
      setTimeout(() => tt.navigateBack(), 600)
      return
    }
    syncCountryFromServer().finally(() => {
      const taskForRegion = prev._taskForRegion || {}
      const taskWorkRegionCode = resolveManualRecordCountryCode(taskForRegion, getCountry())
        || prev.data.taskWorkRegionCode
        || getCountry()
        || ''
      const draftRecord = createManualTaskRecord({
        taskId: this.data.taskId,
        taskCountry: taskWorkRegionCode,
        existingRecords: prev.data.records || [],
      })
      this.setData({
        rowKey: draftRecord._rowKey,
        taskWorkRegionCode,
        mode: 'createManual',
      }, () => {
        this.refreshTexts()
        this.initDraft(draftRecord)
      })
    })
  },

  loadFromResultPage: function () {
    const prev = this.getResultPage()
    if (!prev || !Array.isArray(prev.data.records) || !prev.data.canSubmit) {
      return false
    }
    this.setData({ taskWorkRegionCode: prev.data.taskWorkRegionCode || '' })
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
        const engine = task.aiRawOutput || ''
        const promptCountry = engine.indexOf('mimo:') === 0 ? engine.slice(5) : (task.promptCountry || '')
        const taskForRegion = { ...task, promptCountry: promptCountry || task.promptCountry }
        const records = parseRecords(task.rawData || task.confirmedData, {
          isConfirmed: task.status === 'confirmed',
        })
        const taskWorkRegionCode = resolveTaskWorkRegionBindingCode(
          taskForRegion,
          getCountry(),
          records,
          task.status === 'confirmed'
        )
        this.setData({ taskWorkRegionCode })
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
    const synced = syncRecordPaysToTaskRegion(record, this.data.taskWorkRegionCode, false, 'processed')
    this._sourceRecord = synced
    const draft = {}
    CALIBRATABLE_FIELDS.forEach((key) => {
      const v = synced[key]
      if (key === 'PAUSE') {
        const minutes = normalizePauseMinutes(v)
        draft[key] = minutes === '' ? '' : String(minutes)
        return
      }
      draft[key] = v === undefined || v === null ? '' : v
    })
    this.setData({ draft, paysExpanded: false }, () => {
      this.rebuildForm()
      this.refreshTexts()
    })
  },

  rebuildForm: function () {
    const fields = buildCalibFormFields(this.data.draft, this._sourceRecord || {}, {
      taskWorkRegionCode: this.data.taskWorkRegionCode,
      paysExpanded: this.data.paysExpanded,
      isConfirmed: false,
    })
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

  togglePaysCountry: function (e) {
    const locked = e.currentTarget.dataset.locked === true
      || e.currentTarget.dataset.locked === 'true'
    if (locked) {
      tt.showToast({ title: t('recordEdit.paysLockedHint'), icon: 'none' })
      return
    }
    this.setData({ paysExpanded: !this.data.paysExpanded }, () => this.rebuildForm())
  },

  onSelectPaysCountry: function () {
    tt.showToast({ title: t('recordEdit.paysLockedHint'), icon: 'none' })
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

  finalizeRecord: function (baseRecord, payload) {
    let record = prepareRecordPlaceholders({ ...baseRecord, ...payload })
    record = syncRecordPaysToTaskRegion(record, this.data.taskWorkRegionCode, false, 'processed')
    const signatureMark = computeSignatureMark(record)
    record.SIGNATURE = signatureMark
    record.CHECKER = signatureMark
    return record
  },

  save: function () {
    if (this.data.saving) return
    const prev = this.getResultPage()

    if (this.data.mode === 'createManual') {
      if (!prev || typeof prev.appendManualRecord !== 'function') {
        tt.showToast({ title: t('recordEdit.saveFail'), icon: 'none' })
        return
      }
      this.setData({ saving: true })
      const record = this.finalizeRecord(this._sourceRecord || {}, this.buildDraftPayload())
      prev.appendManualRecord(record)
      tt.showToast({ title: t('recordEdit.success'), icon: 'success' })
      setTimeout(() => {
        this.setData({ saving: false })
        tt.navigateBack()
      }, 400)
      return
    }

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
