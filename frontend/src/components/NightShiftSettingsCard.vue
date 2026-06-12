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

      <a-row :gutter="16">
        <a-col :span="12">
          <a-form-item :label="t('settings.system.nightShiftStart')">
            <a-time-picker
              v-model:value="nightStart"
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
              v-model:value="nightEnd"
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
import { computed, onMounted, reactive, ref } from 'vue'
import { message } from 'ant-design-vue'
import { useI18n } from 'vue-i18n'
import dayjs from 'dayjs'
import request from '@/api/index'
import { setNightShiftRulesLocal } from '@/utils/nightShiftRules'

defineProps({
  showTitle: { type: Boolean, default: true },
})

const { t } = useI18n()
const saving = ref(false)

const DEFAULT_NIGHT_SHIFT = {
  startTime: '20:00',
  endTime: '06:00',
  crossMidnight: true,
  useScheduleColumn: true,
}

const form = reactive({ ...DEFAULT_NIGHT_SHIFT })

const nightStart = computed({
  get: () => dayjs(form.startTime, 'HH:mm'),
  set: (val) => {
    form.startTime = val ? dayjs(val).format('HH:mm') : DEFAULT_NIGHT_SHIFT.startTime
  },
})

const nightEnd = computed({
  get: () => dayjs(form.endTime, 'HH:mm'),
  set: (val) => {
    form.endTime = val ? dayjs(val).format('HH:mm') : DEFAULT_NIGHT_SHIFT.endTime
  },
})

const applyNightShift = (raw) => {
  if (!raw) return
  form.startTime = raw.startTime || DEFAULT_NIGHT_SHIFT.startTime
  form.endTime = raw.endTime || DEFAULT_NIGHT_SHIFT.endTime
  form.crossMidnight = raw.crossMidnight !== false
  form.useScheduleColumn = raw.useScheduleColumn !== false
  setNightShiftRulesLocal(form)
}

const loadConfig = async () => {
  try {
    const res = await request({
      url: '/config/system',
      method: 'get',
      silentError: true,
    })
    applyNightShift(res.data?.nightShift)
  } catch (e) {
    console.error(e)
  }
}

const handleSave = async () => {
  saving.value = true
  try {
    await request({
      url: '/config/system',
      method: 'put',
      data: {
        nightShift: {
          startTime: form.startTime,
          endTime: form.endTime,
          crossMidnight: form.crossMidnight,
          useScheduleColumn: form.useScheduleColumn,
        },
      },
    })
    setNightShiftRulesLocal(form)
    message.success(t('config.saveSuccess'))
  } catch (e) {
    console.error(e)
  } finally {
    saving.value = false
  }
}

const handleReset = () => {
  Object.assign(form, DEFAULT_NIGHT_SHIFT)
}

onMounted(loadConfig)
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
.hint {
  margin: 0;
  font-size: 13px;
  color: #8c8c8c;
  font-weight: normal;
}
</style>
