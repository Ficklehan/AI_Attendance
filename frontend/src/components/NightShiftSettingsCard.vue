<template>
  <a-card class="surface-card night-shift-card" :bordered="false">
    <template v-if="showTitle" #title>
      <div>
        <h3 class="card-title">{{ t('settings.system.nightShiftTitle') }}</h3>
        <p class="card-desc">{{ t('settings.system.nightShiftDesc') }}</p>
      </div>
    </template>

    <a-form layout="vertical">
      <p v-if="!showTitle" class="hint">{{ t('settings.system.nightShiftDesc') }}</p>

      <a-form-item :label="t('settings.system.nightShiftCountry')">
        <a-select
          v-model:value="selectedCountry"
          :options="countryOptions"
          style="width: 100%"
          @change="onCountryChange"
        />
        <p v-if="selectedCountry !== 'default' && !hasCountryOverride" class="country-hint">
          {{ t('settings.system.nightShiftUseGlobalHint') }}
        </p>
        <p v-else-if="selectedCountry !== 'default' && hasCountryOverride" class="country-hint country-hint--active">
          {{ t('settings.system.nightShiftCountryOverrideActive') }}
        </p>
        <p v-if="configuredCountryTags.length" class="country-hint">
          {{ t('settings.system.nightShiftConfiguredCountries') }}：
          <a-tag v-for="code in configuredCountryTags" :key="code" style="margin-top: 4px">{{ code }}</a-tag>
        </p>
      </a-form-item>

      <a-form-item v-if="selectedCountry !== 'default'">
        <a-checkbox v-model:checked="hasCountryOverride" @change="onOverrideToggle">
          {{ t('settings.system.nightShiftCountryOverride') }}
        </a-checkbox>
      </a-form-item>

      <template v-if="selectedCountry === 'default' || hasCountryOverride">
        <a-row :gutter="16">
          <a-col :span="12">
            <a-form-item :label="t('settings.system.nightShiftStart')">
              <a-time-picker
                v-model:value="form.startTime"
                format="HH:mm"
                value-format="HH:mm"
                :minute-step="5"
                style="width: 100%"
              />
            </a-form-item>
          </a-col>
          <a-col :span="12">
            <a-form-item :label="t('settings.system.nightShiftEnd')">
              <a-time-picker
                v-model:value="form.endTime"
                format="HH:mm"
                value-format="HH:mm"
                :minute-step="5"
                style="width: 100%"
              />
            </a-form-item>
          </a-col>
        </a-row>

        <a-form-item>
          <a-checkbox v-model:checked="form.crossMidnight">
            {{ t('settings.system.nightShiftCrossMidnight') }}
          </a-checkbox>
        </a-form-item>
        <a-form-item>
          <a-checkbox v-model:checked="form.useScheduleColumn">
            {{ t('settings.system.nightShiftUseSchedule') }}
          </a-checkbox>
        </a-form-item>
      </template>

      <a-form-item>
        <a-button type="primary" :loading="saving" @click="handleSave">
          {{ t('common.save') }}
        </a-button>
        <a-button style="margin-left: 8px" @click="handleReset">
          {{ t('settings.system.resetDefaults') }}
        </a-button>
      </a-form-item>
    </a-form>
  </a-card>
</template>

<script setup>
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { message } from 'ant-design-vue'
import { useI18n } from 'vue-i18n'
import request from '@/api/index'
import { useCountryStore } from '@/stores/country'
import { buildCountrySelectOption } from '@/utils/countryLabels'
import { setNightShiftAdminConfig } from '@/utils/nightShiftRules'

defineProps({
  showTitle: { type: Boolean, default: true },
})

const { t } = useI18n()
const countryStore = useCountryStore()
const saving = ref(false)
const selectedCountry = ref('default')
const hasCountryOverride = ref(false)

const DEFAULT_NIGHT_SHIFT = {
  startTime: '20:00',
  endTime: '06:00',
  crossMidnight: true,
  useScheduleColumn: true,
}

const globalForm = reactive({ ...DEFAULT_NIGHT_SHIFT })
const form = reactive({ ...DEFAULT_NIGHT_SHIFT })
const byCountry = reactive({})

const countryOptions = computed(() =>
  (countryStore.options.length ? countryStore.options : [{ code: 'default', flag: '🇺🇳', name: '全局默认' }])
    .map((item) => buildCountrySelectOption(item)),
)

const configuredCountryTags = computed(() => Object.keys(byCountry).sort())

const CLOCK_RE = /^([0-1]?\d|2[0-3]):([0-5]\d)$/

const normalizeClock = (value, fallback) => {
  if (!value || !String(value).trim()) return fallback
  const match = String(value).trim().match(CLOCK_RE)
  if (!match) return fallback
  return `${String(match[1]).padStart(2, '0')}:${match[2]}`
}

const copyRules = (target, source) => {
  target.startTime = normalizeClock(source?.startTime, DEFAULT_NIGHT_SHIFT.startTime)
  target.endTime = normalizeClock(source?.endTime, DEFAULT_NIGHT_SHIFT.endTime)
  target.crossMidnight = source?.crossMidnight !== false
  target.useScheduleColumn = source?.useScheduleColumn !== false
}

const applyCountryForm = () => {
  if (selectedCountry.value === 'default') {
    copyRules(form, globalForm)
    hasCountryOverride.value = false
    return
  }
  const override = byCountry[selectedCountry.value]
  hasCountryOverride.value = Boolean(override)
  copyRules(form, override || globalForm)
}

const onCountryChange = () => {
  applyCountryForm()
}

const onOverrideToggle = () => {
  if (selectedCountry.value === 'default') return
  if (hasCountryOverride.value) {
    const override = byCountry[selectedCountry.value]
    copyRules(form, override || globalForm)
    return
  }
  copyRules(form, globalForm)
}

const applyAdminConfig = (raw) => {
  if (!raw) return
  copyRules(globalForm, raw)
  Object.keys(byCountry).forEach((key) => delete byCountry[key])
  const overrides = raw.byCountry && typeof raw.byCountry === 'object' ? raw.byCountry : {}
  Object.keys(overrides).forEach((code) => {
    const rule = overrides[code]
    if (rule && typeof rule === 'object') {
      byCountry[code] = { ...DEFAULT_NIGHT_SHIFT, ...rule }
    }
  })
  setNightShiftAdminConfig({ ...raw, byCountry: { ...overrides } })
  applyCountryForm()
}

const syncSelectedCountry = () => {
  const working = countryStore.workingCountry
  if (working && working !== 'default') {
    selectedCountry.value = working
  }
  applyCountryForm()
}

const loadConfig = async () => {
  try {
    await countryStore.hydrate()
    const res = await request({
      url: '/config/system',
      method: 'get',
      silentError: true,
    })
    applyAdminConfig(res.data?.nightShift)
    syncSelectedCountry()
  } catch (e) {
    console.error(e)
  }
}

const buildPayload = () => {
  const payload = {
    startTime: globalForm.startTime,
    endTime: globalForm.endTime,
    crossMidnight: globalForm.crossMidnight,
    useScheduleColumn: globalForm.useScheduleColumn,
    byCountry: {},
  }
  Object.keys(byCountry).forEach((code) => {
    payload.byCountry[code] = { ...byCountry[code] }
  })

  if (selectedCountry.value === 'default') {
    copyRules(globalForm, form)
    payload.startTime = globalForm.startTime
    payload.endTime = globalForm.endTime
    payload.crossMidnight = globalForm.crossMidnight
    payload.useScheduleColumn = globalForm.useScheduleColumn
  } else if (hasCountryOverride.value) {
    byCountry[selectedCountry.value] = {
      startTime: form.startTime,
      endTime: form.endTime,
      crossMidnight: form.crossMidnight,
      useScheduleColumn: form.useScheduleColumn,
    }
    payload.byCountry = { ...byCountry }
  } else {
    delete payload.byCountry[selectedCountry.value]
    payload.byCountry = { ...byCountry }
  }
  return payload
}

const handleSave = async () => {
  if (selectedCountry.value !== 'default' && hasCountryOverride.value) {
    byCountry[selectedCountry.value] = {
      startTime: form.startTime,
      endTime: form.endTime,
      crossMidnight: form.crossMidnight,
      useScheduleColumn: form.useScheduleColumn,
    }
  }
  saving.value = true
  try {
    const nightShift = buildPayload()
    const res = await request({
      url: '/config/system',
      method: 'put',
      data: { nightShift },
    })
    applyAdminConfig(res.data?.nightShift || nightShift)
    message.success(t('config.saveSuccess'))
  } catch (e) {
    console.error(e)
  } finally {
    saving.value = false
  }
}

const handleReset = () => {
  if (selectedCountry.value === 'default') {
    Object.assign(globalForm, DEFAULT_NIGHT_SHIFT)
    copyRules(form, DEFAULT_NIGHT_SHIFT)
    return
  }
  hasCountryOverride.value = false
  delete byCountry[selectedCountry.value]
  copyRules(form, globalForm)
}

onMounted(loadConfig)

watch(
  () => countryStore.workingCountry,
  (code) => {
    if (!code || code === 'default') return
    selectedCountry.value = code
    applyCountryForm()
  },
)
</script>

<style scoped lang="scss">
.night-shift-card :deep(.ant-card-head-title) {
  padding: 0;
}

.card-title {
  margin: 0 0 4px;
  font-size: 16px;
  font-weight: 600;
}

.card-desc,
.hint,
.country-hint {
  margin: 0;
  font-size: 13px;
  color: #8c8c8c;
  font-weight: normal;
}

.country-hint {
  margin-top: 8px;
}
</style>
