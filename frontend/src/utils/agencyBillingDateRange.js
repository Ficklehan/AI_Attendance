import dayjs from 'dayjs'

/** 与后端 AgencyBillingService.MAX_RANGE_DAYS 一致（含首尾） */
export const MAX_BILLING_RANGE_DAYS = 62

export function countInclusiveDays(start, end) {
  return dayjs(end).startOf('day').diff(dayjs(start).startOf('day'), 'day') + 1
}

/**
 * 判断某日能否作为结束日（相对固定开始日，含首尾最多 MAX 天）
 */
export function canBeBillingEnd(current, start) {
  if (!current || !start) return false
  const cur = dayjs(current).startOf('day')
  const s = dayjs(start).startOf('day')
  return !cur.isBefore(s) && cur.diff(s, 'day') < MAX_BILLING_RANGE_DAYS
}

/**
 * 判断某日能否作为开始日（相对固定结束日，含首尾最多 MAX 天）
 */
export function canBeBillingStart(current, end) {
  if (!current || !end) return false
  const cur = dayjs(current).startOf('day')
  const e = dayjs(end).startOf('day')
  return !cur.isAfter(e) && e.diff(cur, 'day') < MAX_BILLING_RANGE_DAYS
}

/**
 * Ant Design Vue 4 RangePicker 的 disabledDate 只接收 current。
 * 根据当前开始/结束（含面板内未确认的 calendar 值）计算是否应禁用。
 */
export function isBillingDateDisabled(current, bounds) {
  if (!current) return false
  const start = bounds?.start || null
  const end = bounds?.end || null

  if (start && !end) {
    return !canBeBillingEnd(current, start)
  }
  if (!start && end) {
    return !canBeBillingStart(current, end)
  }
  if (start && end) {
    return !canBeBillingEnd(current, start) && !canBeBillingStart(current, end)
  }
  return false
}

export function createBillingDisabledDate(getBounds) {
  return (current) => isBillingDateDisabled(current, getBounds())
}

export function clampBillingDateRange(range) {
  if (!range?.[0] || !range?.[1]) return range
  let start = dayjs(range[0])
  let end = dayjs(range[1])
  if (end.isBefore(start)) {
    const swapped = start
    start = end
    end = swapped
  }
  if (countInclusiveDays(start, end) <= MAX_BILLING_RANGE_DAYS) {
    return [start.format('YYYY-MM-DD'), end.format('YYYY-MM-DD')]
  }
  const clampedEnd = start.add(MAX_BILLING_RANGE_DAYS - 1, 'day')
  return [start.format('YYYY-MM-DD'), clampedEnd.format('YYYY-MM-DD')]
}

export function isBillingDateRangeValid(range) {
  if (!range?.[0] || !range?.[1]) return false
  return countInclusiveDays(range[0], range[1]) <= MAX_BILLING_RANGE_DAYS
}
