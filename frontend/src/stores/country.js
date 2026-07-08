import { defineStore } from 'pinia'
import request from '@/api/index'
import { useAuthStore } from '@/stores/auth'
import { getCachedWorkingCountry, setCachedWorkingCountry, markWorkingCountryConfigured } from '@/utils/countryHeader'
import { needsWorkingCountrySetup, resolveSelectedWorkingCountry } from '@/utils/workingCountrySetup'
import { loadNightShiftRules } from '@/utils/nightShiftRules'
import { COUNTRY_FLAG_FALLBACK, DEFAULT_COUNTRY_FLAG, resolveCountryFlag } from '@/utils/countryCatalog'
import { buildCountrySelectOption, formatCountryLabel, translateCountryName } from '@/utils/countryLabels'
import { setStoredUserInfo } from '@/utils/auth'

export const useCountryStore = defineStore('country', {
  state: () => ({
    workingCountry: getCachedWorkingCountry(),
    options: [],
    bundle: null,
    hydrated: false,
    loading: false,
    setupRequired: false,
  }),

  getters: {
    selectOptions(state) {
      return (state.options || []).map((item) => buildCountrySelectOption(item))
    },

    workingCountryMeta(state) {
      const code = state.workingCountry || 'default'
      const found = (state.options || []).find((item) => item.code === code)
      if (found) {
        return {
          ...found,
          flag: resolveCountryFlag(found.code, found.flag),
          name: translateCountryName(found.code, found.name),
        }
      }
      if (code === 'default') {
        return { code, flag: DEFAULT_COUNTRY_FLAG, name: translateCountryName('default', '全局默认') }
      }
      return {
        code,
        flag: COUNTRY_FLAG_FALLBACK[code] || '🏳️',
        name: translateCountryName(code, code),
      }
    },

    workingCountryLabel() {
      const meta = this.workingCountryMeta
      return formatCountryLabel(meta.code, meta.flag, meta.name)
    },

    promptFromGlobalFallback(state) {
      return Boolean(state.bundle?.promptFromGlobalFallback)
    },

    feishuFromGlobalFallback(state) {
      return Boolean(state.bundle?.feishuFromGlobalFallback)
    },
  },

  actions: {
    async hydrate(force = false) {
      if (this.hydrated && !force) {
        return this.workingCountry
      }
      this.loading = true
      try {
        const authStore = useAuthStore()
        const optionsRes = await request({ url: '/config/country-options', method: 'get' })
        if (optionsRes?.data?.length) {
          this.options = optionsRes.data
        }
        const cached = getCachedWorkingCountry()
        const userInfo = authStore.userInfo
        const needsSetup = needsWorkingCountrySetup(userInfo)
        this.setupRequired = needsSetup

        let country = 'default'
        if (needsSetup) {
          country = cached && cached !== 'default' ? cached : 'default'
        } else {
          country = resolveSelectedWorkingCountry(userInfo)
          markWorkingCountryConfigured()
        }

        this.workingCountry = country
        setCachedWorkingCountry(country)
        await this.loadBundle()
        this.hydrated = true
        return country
      } finally {
        this.loading = false
      }
    },

    async loadBundle(countryCode) {
      const code = countryCode || this.workingCountry || 'default'
      try {
        const res = await request({
          url: '/config/country-bundle',
          method: 'get',
          params: { country: code },
        })
        this.bundle = res.data || null
      } catch (error) {
        console.error('加载国家配置摘要失败:', error)
        this.bundle = null
      }
    },

    async setWorkingCountry(country) {
      const code = country || 'default'
      const authStore = useAuthStore()

      const res = await request({
        url: '/auth/working-country',
        method: 'post',
        data: { country: code },
      })
      const effective = res?.data?.country || code
      this.workingCountry = code
      setCachedWorkingCountry(code)
      if (code && code !== 'default') {
        markWorkingCountryConfigured()
        this.setupRequired = false
      } else {
        this.setupRequired = true
      }
      if (authStore.userInfo) {
        authStore.userInfo = {
          ...authStore.userInfo,
          personalWorkingCountry: code === 'default' ? null : code,
          workingCountry: effective,
        }
        setStoredUserInfo(authStore.userInfo)
      }
      await this.loadBundle(code)
      loadNightShiftRules(true, code).catch(() => {})
      this.hydrated = true
      if (authStore.isAuthenticated) {
        await authStore.refreshPermissions(code === 'default' ? undefined : code)
      }
      return code
    },
  },
})
