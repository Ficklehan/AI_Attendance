const { t, tOr } = require('../../utils/i18n')
const { isApiSuccess, getApiData, getApiMessage } = require('../../utils/response')
const { apiCall } = require('../../utils/request')
const { parseRecords } = require('../../utils/task')
const { isAbsentRow, restoreAbsentRecord, normalizePauseMinutes } = require('../../utils/recordDisplay')
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
const {
  SNAPSHOT_FIELD_KEYS,
  EXCEPTION_TYPE,
  buildExceptionTypeDeps,
  buildExceptionTypeOptions,
  ensureExceptionType,
  onExceptionTypeChange,
  isExceptionTypeSelectDisabled,
  exceptionTypeDisabledHint,
  isCalibFieldEditable,
  onCalibratableFieldFocus,
  onCalibratableFieldChange,
  shouldHighlightFieldForCalibration,
} = require('../../utils/exceptionTypeUi')
const {
  enrichCalibFieldForPicker,
  composeShift,
  toPickerTime,
} = require('../../utils/recordEditPickers')

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
    convertedFromAbsent: false,
    exceptionType: '',
    exceptionTypeOptions: [],
    exceptionTypeDisabled: false,
    exceptionTypeDisabledHint: '',
    showFocusHint: false,
    focusFieldBadgeText: '重点关注',
    focusFieldsHintText: '请对照纸质表重点核对并修正：日期、班次、到达、离开、休息。',
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
    const focusFieldBadgeText = tOr('recordEdit.focusFieldBadge', null, '重点关注')
    const focusFieldsHintText = tOr(
      'recordEdit.focusFieldsHint',
      null,
      '请对照纸质表重点核对并修正：日期、班次、到达、离开、休息。',
    )
    this.setData({
      focusFieldBadgeText,
      focusFieldsHintText,
      texts: {
        hint: t(isAdd ? 'recordEdit.addHint' : 'recordEdit.hint'),
        save: t('recordEdit.save'),
        cancel: t('common.cancel'),
        paysLockedHint: t('recordEdit.paysLockedHint'),
        exceptionTypeLabel: tOr('result.exceptionTypeRequired', null, '请选择异常类型'),
        exceptionTypePickHint: tOr('result.exceptionTypePickHint', null, '点选其一确认 →'),
        exceptionTypeLockedHint: tOr(
          'recordEdit.exceptionTypeLockedHint',
          null,
          '已选「考勤正确」，班次/到达/离开等必填字段不可改；可改姓名、仓库、中介，或先改选异常类型。',
        ),
        pickDate: tOr('recordEdit.pickDate', null, '选择日期'),
        pickTime: tOr('recordEdit.pickTime', null, '选择时间'),
        absentRestoreHint: tOr(
          'recordEdit.absentRestoreHint',
          null,
          '该行原为未出勤，请填写到达、离开等出勤信息后保存，即可改为正常出勤。',
        ),
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
    if (record.isDeleted) {
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
        const { parsePromptCountryFromEngine } = require('../../utils/engineLabel')
        const promptCountry = parsePromptCountryFromEngine(engine, task.promptCountry || '')
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
        if (record.isDeleted) {
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
    const convertedFromAbsent = isAbsentRow(record)
    let synced = { ...syncRecordPaysToTaskRegion(record, this.data.taskWorkRegionCode, false, 'processed') }
    synced = restoreAbsentRecord(synced)
    ensureExceptionType(synced, buildExceptionTypeDeps())
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
    this._openBaseline = {}
    SNAPSHOT_FIELD_KEYS.forEach((key) => {
      this._openBaseline[key] = draft[key] === undefined || draft[key] === null ? '' : draft[key]
    })
    CALIBRATABLE_FIELDS.forEach((key) => {
      if (this._openBaseline[key] === undefined) {
        this._openBaseline[key] = draft[key] === undefined || draft[key] === null ? '' : draft[key]
      }
    })
    this.setData({ draft, paysExpanded: false, convertedFromAbsent }, () => {
      this.rebuildForm()
      this.refreshTexts()
    })
  },

  rebuildForm: function () {
    const source = this._sourceRecord || {}
    const openBaseline = this._openBaseline || source._aiBaseline || null
    const fields = buildCalibFormFields(this.data.draft, source, {
      taskWorkRegionCode: this.data.taskWorkRegionCode,
      paysExpanded: this.data.paysExpanded,
      isConfirmed: false,
    }).map((field) => {
      const merged = { ...source, ...this.data.draft, ExceptionType: source.ExceptionType }
      const locked = !isCalibFieldEditable(merged, field.key)
      const highlight = shouldHighlightFieldForCalibration(merged, field.key)
      return enrichCalibFieldForPicker({
        ...field,
        locked,
        highlight,
      }, merged, openBaseline)
    })
    const deps = buildExceptionTypeDeps()
    const merged = { ...source, ...this.data.draft }
    const allOptions = buildExceptionTypeOptions()
    const type = source.ExceptionType || ''
    this.setData({
      fields,
      exceptionType: type,
      showFocusHint: type === EXCEPTION_TYPE.PAPER_OK_OCR_WRONG
        || type === EXCEPTION_TYPE.PAPER_WRONG_TIME,
      exceptionTypeDisabled: isExceptionTypeSelectDisabled(merged, deps),
      exceptionTypeDisabledHint: exceptionTypeDisabledHint(merged, deps),
      exceptionTypeOptions: allOptions.map((opt) => ({
        ...opt,
        active: opt.value === type,
      })),
    })
  },

  applyDraftField: function (key, value) {
    if (!key) return
    if (this._sourceRecord) {
      onCalibratableFieldFocus(this._sourceRecord, SNAPSHOT_FIELD_KEYS)
    }
    const draft = { ...this.data.draft, [key]: value }
    const { applyFieldNormalization } = require('../../utils/recognizedFieldNormalize')
    applyFieldNormalization(draft, key)
    if (this._sourceRecord) {
      this._sourceRecord[key] = draft[key]
      onCalibratableFieldChange(this._sourceRecord, SNAPSHOT_FIELD_KEYS)
    }
    this.setData({ draft }, () => this.rebuildForm())
  },

  onSelectExceptionType: function (e) {
    const value = e.currentTarget.dataset.value
    if (!value || !this._sourceRecord) return
    const deps = buildExceptionTypeDeps()
    const merged = { ...this._sourceRecord, ...this.data.draft }
    if (isExceptionTypeSelectDisabled(merged, deps)) {
      const hint = exceptionTypeDisabledHint(merged, deps)
      if (hint) tt.showToast({ title: hint, icon: 'none' })
      return
    }
    onExceptionTypeChange(this._sourceRecord, value, SNAPSHOT_FIELD_KEYS)
    this.rebuildForm()
  },

  onFieldInput: function (e) {
    const key = e.currentTarget.dataset.key
    let value = e.detail.value
    if (key === 'PAUSE') {
      value = String(value || '').replace(/[^\d]/g, '')
    }
    this.applyDraftField(key, value)
  },

  onDatePickerChange: function (e) {
    const key = e.currentTarget.dataset.key
    const value = e.detail && e.detail.value
    this.applyDraftField(key, value)
  },

  onTimePickerChange: function (e) {
    const key = e.currentTarget.dataset.key
    const value = e.detail && e.detail.value
    this.applyDraftField(key, toPickerTime(value))
  },

  onTimeFieldInput: function (e) {
    const key = e.currentTarget.dataset.key
    if (!key) return
    const value = e.detail.value
    const draft = { ...this.data.draft, [key]: value }
    const fields = (this.data.fields || []).map((field) => (
      field.key === key ? { ...field, displayValue: value } : field
    ))
    this.setData({ draft, fields })
  },

  onTimeInputBlur: function (e) {
    const key = e.currentTarget.dataset.key
    if (!key) return
    const { normalizeClockTime } = require('../../shared-js/recognizedTimeNormalizer')
    const value = e.detail && e.detail.value != null ? e.detail.value : this.data.draft[key]
    this.applyDraftField(key, normalizeClockTime(value))
  },

  onShiftPickerChange: function (e) {
    const key = e.currentTarget.dataset.key
    const part = e.currentTarget.dataset.part
    const value = e.detail && e.detail.value
    if (!key || !part) return
    const current = String(this.data.draft[key] || '')
    const { parseShiftParts } = require('../../utils/recordEditPickers')
    const parts = parseShiftParts(current)
    if (part === 'start') parts.start = toPickerTime(value, parts.start)
    if (part === 'end') parts.end = toPickerTime(value, parts.end)
    this.applyDraftField(key, composeShift(parts.start, parts.end))
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
    const source = this._sourceRecord || {}
    payload.ExceptionType = source.ExceptionType || ''
    payload._exceptionTypeManual = source._exceptionTypeManual
    payload.SmartMark = source.SmartMark
    payload._restored = source._restored
    if (source._prevMark !== undefined) payload._prevMark = source._prevMark
    if (source.Mark !== undefined) payload.Mark = source.Mark
    if (source._aiBaseline) payload._aiBaseline = source._aiBaseline
    if (source._lastEditSnapshot) payload._lastEditSnapshot = source._lastEditSnapshot
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
    const record = (prev.data.records || []).find((r) => r._rowKey === this.data.rowKey)
    const changed = CALIBRATABLE_FIELDS.some((key) => {
      if (!record) return true
      return normalizeCalibValue(record[key]) !== normalizeCalibValue(draft[key])
    })
      || normalizeCalibValue(record && record.ExceptionType) !== normalizeCalibValue(draft.ExceptionType)
      || Boolean(draft._restored) !== Boolean(record && record._restored)
      || normalizeCalibValue(record && record.SmartMark) !== normalizeCalibValue(draft.SmartMark)
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
