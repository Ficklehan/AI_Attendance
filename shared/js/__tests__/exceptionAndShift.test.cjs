/**
 * Node built-in test for shiftVarianceCore + exceptionTypeCore.
 * Run: node --test shared/js/__tests__/exceptionAndShift.test.cjs
 */
const { describe, it } = require('node:test')
const assert = require('node:assert/strict')
const {
  parseShiftSchedule,
  computeShiftVarianceMinutes,
  formatShiftVarianceZh,
} = require('../shiftVarianceCore.cjs')
const {
  EXCEPTION_TYPE,
  ensureExceptionType,
  canEditRequiredFields,
  shouldHighlightRequiredForCalibration,
  onExceptionTypeChange,
  onCalibratableFieldChange,
  isExceptionTypeMissingForSubmit,
  hasOcrWrongTimeFieldAdjusted,
  isOcrWrongMissingFieldAdjustment,
  OCR_WRONG_ADJUST_FIELDS,
} = require('../exceptionTypeCore.cjs')

describe('shiftVarianceCore', () => {
  it('parses standard shift', () => {
    const shift = parseShiftSchedule('09:00-14:00')
    assert.equal(shift.shiftStart, '09:00')
    assert.equal(shift.shiftEnd, '14:00')
  })

  it('detects early/late arrival and early leave/overtime', () => {
    const variance = computeShiftVarianceMinutes({
      HORAIRES_DU_TRAVAIL: '09:00-14:00',
      ARRIVEE: '08:55',
      DEPAR: '14:10',
    })
    assert.equal(variance.earlyArrivalMin, 5)
    assert.equal(variance.lateArrivalMin, 0)
    assert.equal(variance.earlyLeaveMin, 0)
    assert.equal(variance.overtimeMin, 10)
    const phrases = formatShiftVarianceZh(variance)
    assert.deepEqual(phrases, ['早到 5 min', '晚离开 10 min'])
  })

  it('detects late arrival and early leave', () => {
    const variance = computeShiftVarianceMinutes({
      HORAIRES_DU_TRAVAIL: '09:00-14:00',
      ARRIVEE: '09:20',
      DEPAR: '13:45',
    })
    assert.equal(variance.lateArrivalMin, 20)
    assert.equal(variance.earlyLeaveMin, 15)
  })

  it('formats duration over one hour as hours and minutes', () => {
    const { formatDurationZh, formatShiftVarianceZh, formatShiftVarianceSentenceZh } = require('../shiftVarianceCore.cjs')
    assert.equal(formatDurationZh(60), '1 h')
    assert.equal(formatDurationZh(75), '1 h 15 min')
    assert.equal(formatDurationZh(125), '2 h 5 min')
    const phrases = formatShiftVarianceZh({
      earlyArrivalMin: 0,
      lateArrivalMin: 70,
      earlyLeaveMin: 0,
      overtimeMin: 90,
    })
    assert.deepEqual(phrases, ['迟到 1 h 10 min', '晚离开 1 h 30 min'])
    assert.equal(
      formatShiftVarianceSentenceZh({
        earlyArrivalMin: 10,
        lateArrivalMin: 0,
        earlyLeaveMin: 15,
        overtimeMin: 0,
      }),
      '员工早到 10 min且早离开 15 min',
    )
  })
})

describe('exceptionTypeCore', () => {
  it('clears type when required missing or format invalid; never auto attendance_ok', () => {
    const missing = { ExceptionType: EXCEPTION_TYPE.ATTENDANCE_OK }
    ensureExceptionType(missing, { hasRequiredMissing: () => true })
    assert.equal(missing.ExceptionType, '')

    const badFormat = { ExceptionType: EXCEPTION_TYPE.ATTENDANCE_OK }
    ensureExceptionType(badFormat, {
      hasRequiredMissing: () => false,
      hasFormatInvalid: () => true,
    })
    assert.equal(badFormat.ExceptionType, '')

    const ok = {}
    ensureExceptionType(ok, { hasRequiredMissing: () => false, hasFormatInvalid: () => false })
    assert.equal(ok.ExceptionType, '')

    const keep = { ExceptionType: EXCEPTION_TYPE.PAPER_OK_OCR_WRONG }
    ensureExceptionType(keep, { hasRequiredMissing: () => false, hasFormatInvalid: () => false })
    assert.equal(keep.ExceptionType, EXCEPTION_TYPE.PAPER_OK_OCR_WRONG)
  })

  it('locks required fields only for attendance_ok', () => {
    assert.equal(canEditRequiredFields({ ExceptionType: EXCEPTION_TYPE.ATTENDANCE_OK }), false)
    assert.equal(canEditRequiredFields({ ExceptionType: EXCEPTION_TYPE.PAPER_OK_OCR_WRONG }), true)
    assert.equal(shouldHighlightRequiredForCalibration({
      ExceptionType: EXCEPTION_TYPE.PAPER_OK_OCR_WRONG,
    }), true)
    assert.equal(shouldHighlightRequiredForCalibration({
      ExceptionType: EXCEPTION_TYPE.PAPER_WRONG_TIME,
    }), true)
    assert.equal(shouldHighlightRequiredForCalibration({
      ExceptionType: EXCEPTION_TYPE.ATTENDANCE_OK,
    }), false)
    const { shouldHighlightFieldForCalibration } = require('../exceptionTypeCore.cjs')
    assert.equal(shouldHighlightFieldForCalibration({
      ExceptionType: EXCEPTION_TYPE.PAPER_WRONG_TIME,
    }, 'ARRIVEE'), true)
    assert.equal(shouldHighlightFieldForCalibration({
      ExceptionType: EXCEPTION_TYPE.PAPER_OK_OCR_WRONG,
    }, 'NOM_PRENOM'), false)
  })

  it('keeps AI baseline and last edit only', () => {
    const record = {
      ARRIVEE: '09:00',
      DEPAR: '14:00',
      NOM_PRENOM: 'A',
    }
    onExceptionTypeChange(record, EXCEPTION_TYPE.PAPER_OK_OCR_WRONG, ['ARRIVEE', 'DEPAR', 'NOM_PRENOM'])
    assert.equal(record._aiBaseline.ARRIVEE, '09:00')
    record.ARRIVEE = '09:05'
    onCalibratableFieldChange(record, ['ARRIVEE', 'DEPAR', 'NOM_PRENOM'])
    assert.equal(record._aiBaseline.ARRIVEE, '09:00')
    assert.equal(record._lastEditSnapshot.ARRIVEE, '09:05')
    record.ARRIVEE = '09:10'
    onCalibratableFieldChange(record, ['ARRIVEE', 'DEPAR', 'NOM_PRENOM'])
    assert.equal(record._aiBaseline.ARRIVEE, '09:00')
    assert.equal(record._lastEditSnapshot.ARRIVEE, '09:10')
  })

  it('captures baseline on focus before value changes', () => {
    const { onCalibratableFieldFocus } = require('../exceptionTypeCore.cjs')
    const record = {
      ARRIVEE: '09:00',
      DEPAR: '14:00',
      ExceptionType: EXCEPTION_TYPE.PAPER_OK_OCR_WRONG,
    }
    onCalibratableFieldFocus(record, ['ARRIVEE', 'DEPAR'])
    assert.equal(record._aiBaseline.ARRIVEE, '09:00')
    record.ARRIVEE = '09:20'
    onCalibratableFieldChange(record, ['ARRIVEE', 'DEPAR'])
    assert.equal(record._aiBaseline.ARRIVEE, '09:00')
    assert.equal(record._lastEditSnapshot.ARRIVEE, '09:20')
  })

  it('flags missing exception type for submit', () => {
    assert.equal(isExceptionTypeMissingForSubmit({ ExceptionType: '' }, {}), true)
    assert.equal(isExceptionTypeMissingForSubmit({
      ExceptionType: EXCEPTION_TYPE.ATTENDANCE_OK,
    }, {}), false)
    assert.equal(isExceptionTypeMissingForSubmit({ isDeleted: true }, {}), false)
  })

  it('clears historical auto attendance_ok when times do not match shift', () => {
    const autoOk = {
      ExceptionType: EXCEPTION_TYPE.ATTENDANCE_OK,
      HORAIRES_DU_TRAVAIL: '09:00-18:00',
      ARRIVEE: '08:30',
      DEPAR: '17:30',
    }
    ensureExceptionType(autoOk, { hasRequiredMissing: () => false, hasFormatInvalid: () => false })
    assert.equal(autoOk.ExceptionType, '')

    const manualOk = {
      ExceptionType: EXCEPTION_TYPE.ATTENDANCE_OK,
      _exceptionTypeManual: true,
      HORAIRES_DU_TRAVAIL: '09:00-18:00',
      ARRIVEE: '08:30',
      DEPAR: '17:30',
    }
    ensureExceptionType(manualOk, { hasRequiredMissing: () => false, hasFormatInvalid: () => false })
    assert.equal(manualOk.ExceptionType, EXCEPTION_TYPE.ATTENDANCE_OK)
  })

  it('marks manual flag when user selects exception type', () => {
    const record = { ARRIVEE: '09:00' }
    onExceptionTypeChange(record, EXCEPTION_TYPE.ATTENDANCE_OK, ['ARRIVEE'])
    assert.equal(record.ExceptionType, EXCEPTION_TYPE.ATTENDANCE_OK)
    assert.equal(record._exceptionTypeManual, true)
  })

  it('auto attendance_ok only when arrive/depart exactly match shift and no notes', () => {
    const matched = {
      ExceptionType: '',
      HORAIRES_DU_TRAVAIL: '09:00-18:00',
      ARRIVEE: '09:00',
      DEPAR: '18:00',
      SmartMark: '正常',
    }
    ensureExceptionType(matched, {
      hasRequiredMissing: () => false,
      hasFormatInvalid: () => false,
      hasRecognitionNotes: () => false,
    })
    assert.equal(matched.ExceptionType, EXCEPTION_TYPE.ATTENDANCE_OK)

    const early = {
      ExceptionType: '',
      HORAIRES_DU_TRAVAIL: '09:00-18:00',
      ARRIVEE: '08:30',
      DEPAR: '17:30',
    }
    ensureExceptionType(early, {
      hasRequiredMissing: () => false,
      hasFormatInvalid: () => false,
      hasRecognitionNotes: () => false,
    })
    assert.equal(early.ExceptionType, '')

    const nightOnly = {
      ExceptionType: '',
      HORAIRES_DU_TRAVAIL: '19:00-04:00',
      ARRIVEE: '19:00',
      DEPAR: '04:00',
      SmartMark: '正常;夜班',
    }
    ensureExceptionType(nightOnly, {
      hasRequiredMissing: () => false,
      hasFormatInvalid: () => false,
      hasRecognitionNotes: () => false,
    })
    assert.equal(nightOnly.ExceptionType, '')
  })

  it('clears exception type for deleted/absent rows', () => {
    const deleted = {
      ExceptionType: EXCEPTION_TYPE.ATTENDANCE_OK,
      _exceptionTypeManual: true,
      isDeleted: true,
    }
    ensureExceptionType(deleted, {
      hasRequiredMissing: () => false,
      isAbsentRow: () => false,
    })
    assert.equal(deleted.ExceptionType, '')
    assert.equal(deleted._exceptionTypeManual, undefined)

    const absent = {
      ExceptionType: EXCEPTION_TYPE.ATTENDANCE_OK,
      SmartMark: '未出勤',
    }
    ensureExceptionType(absent, {
      hasRequiredMissing: () => false,
      isAbsentRow: () => true,
    })
    assert.equal(absent.ExceptionType, '')

    const { isExceptionTypeExempt } = require('../exceptionTypeCore.cjs')
    assert.equal(isExceptionTypeExempt(absent, () => true), true)
    assert.equal(isExceptionTypeExempt(absent, { isAbsentRow: () => true }), true)
  })

  it('keeps empty when times do not match and no manual selection', () => {
    const blank = {
      ExceptionType: '',
      HORAIRES_DU_TRAVAIL: '09:00-14:00',
      ARRIVEE: '09:10',
      DEPAR: '14:00',
    }
    ensureExceptionType(blank, { hasRequiredMissing: () => false, hasFormatInvalid: () => false })
    assert.equal(blank.ExceptionType, '')
  })

  it('blocks OCR-wrong submit until shift/arrival/departure/break adjusted', () => {
    const untouched = {
      ExceptionType: EXCEPTION_TYPE.PAPER_OK_OCR_WRONG,
      HORAIRES_DU_TRAVAIL: '09:00-14:00',
      ARRIVEE: '09:00',
      DEPAR: '14:00',
      PAUSE: '0',
      _aiBaseline: {
        HORAIRES_DU_TRAVAIL: '09:00-14:00',
        ARRIVEE: '09:00',
        DEPAR: '14:00',
        PAUSE: '0',
      },
    }
    assert.equal(hasOcrWrongTimeFieldAdjusted(untouched), false)
    assert.equal(isOcrWrongMissingFieldAdjustment(untouched, {}), true)

    untouched.ARRIVEE = '09:10'
    assert.equal(hasOcrWrongTimeFieldAdjusted(untouched), true)
    assert.equal(isOcrWrongMissingFieldAdjustment(untouched, {}), false)

    assert.deepEqual(OCR_WRONG_ADJUST_FIELDS, [
      'HORAIRES_DU_TRAVAIL',
      'ARRIVEE',
      'DEPAR',
      'PAUSE',
    ])
  })

  it('skips OCR-wrong adjustment check for attendance_ok and deleted', () => {
    assert.equal(isOcrWrongMissingFieldAdjustment({
      ExceptionType: EXCEPTION_TYPE.ATTENDANCE_OK,
      ARRIVEE: '09:00',
      _aiBaseline: { ARRIVEE: '09:00' },
    }, {}), false)
    assert.equal(isOcrWrongMissingFieldAdjustment({
      isDeleted: true,
      ExceptionType: EXCEPTION_TYPE.PAPER_OK_OCR_WRONG,
    }, {}), false)
  })

  it('lists baseline field diffs', () => {
    const { getBaselineFieldDiffs } = require('../exceptionTypeCore.cjs')
    const record = {
      ARRIVEE: '09:05',
      DEPAR: '14:00',
      _aiBaseline: { ARRIVEE: '09:00', DEPAR: '14:00' },
    }
    const diffs = getBaselineFieldDiffs(record, ['ARRIVEE', 'DEPAR'])
    assert.equal(diffs.length, 1)
    assert.equal(diffs[0].field, 'ARRIVEE')
    assert.equal(diffs[0].from, '09:00')
    assert.equal(diffs[0].to, '09:05')
  })
})

describe('collectConfirmValidationIssues includes ExceptionType', () => {
  it('emits exceptionType issue when type empty', () => {
    const { createRequiredRecordFields } = require('../requiredRecordFields.cjs')
    const { isPlaceholderValue } = require('../fieldPlaceholder.cjs')
    const { markContains } = require('../recognitionMarkCore.cjs')
    const api = createRequiredRecordFields({ isPlaceholderValue, markContains })
    api.setConfirmValidationConfig({
      requiredFields: ['NOM_PRENOM', 'Date', 'ARRIVEE', 'DEPAR', 'PAUSE'],
    })
    const records = [{
      NOM_PRENOM: 'Alice',
      Date: '2024-01-01',
      ARRIVEE: '09:00',
      DEPAR: '14:00',
      PAUSE: 0,
      ExceptionType: '',
    }]
    const issues = api.collectConfirmValidationIssues(records)
    assert.equal(issues.some((i) => i.issueType === 'exceptionType'), true)
  })

  it('emits ocrCalibration when OCR-wrong without time field edits', () => {
    const { createRequiredRecordFields } = require('../requiredRecordFields.cjs')
    const { isPlaceholderValue } = require('../fieldPlaceholder.cjs')
    const { markContains } = require('../recognitionMarkCore.cjs')
    const api = createRequiredRecordFields({ isPlaceholderValue, markContains })
    api.setConfirmValidationConfig({
      requiredFields: ['NOM_PRENOM', 'Date', 'ARRIVEE', 'DEPAR', 'PAUSE'],
    })
    const records = [{
      NOM_PRENOM: 'Alice',
      Date: '2024-01-01',
      HORAIRES_DU_TRAVAIL: '09:00-14:00',
      ARRIVEE: '09:00',
      DEPAR: '14:00',
      PAUSE: 0,
      ExceptionType: EXCEPTION_TYPE.PAPER_OK_OCR_WRONG,
      _aiBaseline: {
        HORAIRES_DU_TRAVAIL: '09:00-14:00',
        ARRIVEE: '09:00',
        DEPAR: '14:00',
        PAUSE: '0',
      },
    }]
    const issues = api.collectConfirmValidationIssues(records)
    assert.equal(issues.some((i) => i.issueType === 'ocrCalibration'), true)

    records[0].DEPAR = '14:15'
    const okIssues = api.collectConfirmValidationIssues(records)
    assert.equal(okIssues.some((i) => i.issueType === 'ocrCalibration'), false)
  })
})
