const { getCountry } = require('./preferences')
const core = require('../shared-js/taskWorkRegionCore')

function withWorkingCountry(userWorkingCountry) {
  return userWorkingCountry || getCountry()
}

module.exports = {
  ...core,
  resolveUserWorkingCountryCode(userWorkingCountry) {
    return core.resolveUserWorkingCountryCode(withWorkingCountry(userWorkingCountry))
  },
  resolveTaskWorkRegionBannerCode(task, userWorkingCountry, records, isConfirmed) {
    return core.resolveTaskWorkRegionBannerCode(
      task,
      withWorkingCountry(userWorkingCountry),
      records,
      isConfirmed,
    )
  },
  resolveTaskWorkRegionCodeForDisplay(task, userWorkingCountry, records, isConfirmed) {
    return core.resolveTaskWorkRegionCodeForDisplay(
      task,
      withWorkingCountry(userWorkingCountry),
      records,
      isConfirmed,
    )
  },
  resolveTaskWorkRegionDisplayCountryCode(task, userWorkingCountry, records, isConfirmed) {
    return core.resolveTaskWorkRegionDisplayCountryCode(
      task,
      withWorkingCountry(userWorkingCountry),
      records,
      isConfirmed,
    )
  },
  resolveTaskWorkRegionCode(task, userWorkingCountry) {
    return core.resolveTaskWorkRegionCode(task, withWorkingCountry(userWorkingCountry))
  },
  resolveTaskWorkRegionCountryCode(task, userWorkingCountry) {
    return core.resolveTaskWorkRegionCountryCode(task, withWorkingCountry(userWorkingCountry))
  },
  resolveTaskWorkRegionPendingCountryCode(task, userWorkingCountry) {
    return core.resolveTaskWorkRegionPendingCountryCode(task, withWorkingCountry(userWorkingCountry))
  },
  resolveTaskWorkRegionBindingCode(task, userWorkingCountry, records, isConfirmed) {
    return core.resolveTaskWorkRegionBindingCode(
      task,
      withWorkingCountry(userWorkingCountry),
      records,
      isConfirmed,
    )
  },
  resolveManualRecordCountryCode(task, userWorkingCountry) {
    return core.resolveManualRecordCountryCode(task, withWorkingCountry(userWorkingCountry))
  },
  resolveActiveTaskWorkRegionCode(task, userWorkingCountry, records, isConfirmed) {
    return core.resolveTaskWorkRegionBindingCode(
      task,
      withWorkingCountry(userWorkingCountry),
      records,
      isConfirmed,
    )
  },
  resolveTaskNightShiftCountryCode(task, userWorkingCountry, records, isConfirmed) {
    return core.resolveTaskNightShiftCountryCode(
      task,
      withWorkingCountry(userWorkingCountry),
      records,
      isConfirmed,
    ) || ''
  },
}
