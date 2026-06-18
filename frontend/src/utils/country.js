import request from '@/api/index'
import { getCachedWorkingCountry, setCachedWorkingCountry } from '@/utils/countryHeader'

export { getCachedWorkingCountry, setCachedWorkingCountry, buildAuthCountryHeaders } from '@/utils/countryHeader'

export async function getCurrentWorkingCountry() {
  const cached = getCachedWorkingCountry()
  if (cached && cached !== 'default') {
    return cached
  }
  try {
    const res = await request({ url: '/config/current-country', method: 'get' })
    const country = res?.data?.country || 'default'
    setCachedWorkingCountry(country)
    return country
  } catch {
    return cached || 'default'
  }
}

export async function syncWorkingCountryFromServer() {
  try {
    const res = await request({ url: '/config/current-country', method: 'get' })
    const country = res?.data?.country || 'default'
    setCachedWorkingCountry(country)
    return country
  } catch {
    return getCachedWorkingCountry()
  }
}

export async function updateWorkingCountryOnServer(country) {
  await request({
    url: '/config/current-country',
    method: 'post',
    data: { country: country || 'default' },
  })
  setCachedWorkingCountry(country)
  return country || 'default'
}
