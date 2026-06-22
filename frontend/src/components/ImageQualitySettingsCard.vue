<template>
  <a-card class="surface-card image-quality-card" :bordered="false">
    <template v-if="showTitle" #title>
      <div>
        <h3 class="card-title">{{ t('settings.system.imageQualityTitle') }}</h3>
        <p class="card-desc">{{ t('settings.system.imageQualityDesc') }}</p>
      </div>
    </template>

    <a-form layout="vertical">
      <p v-if="!showTitle" class="hint">{{ t('settings.system.imageQualityDesc') }}</p>

      <a-form-item>
        <a-checkbox v-model:checked="form.enabled">
          {{ t('settings.system.imageQualityEnabled') }}
        </a-checkbox>
      </a-form-item>

      <a-divider orientation="left">{{ t('settings.system.imageQualityUploadSection') }}</a-divider>
      <p class="section-hint">{{ t('settings.system.imageQualityUploadHint') }}</p>
      <a-form-item>
        <a-checkbox v-model:checked="form.preUploadSharpnessEnabled" :disabled="!form.enabled">
          {{ t('settings.system.imageQualityPreUpload') }}
        </a-checkbox>
      </a-form-item>
      <a-form-item :label="t('settings.system.imageQualityUploadStrictness')">
        <a-select
          v-model:value="uploadStrictness"
          :disabled="!form.enabled || !form.preUploadSharpnessEnabled"
          style="width: 100%"
          @change="onUploadStrictnessChange"
        >
          <a-select-option v-for="item in strictnessOptions" :key="item.value" :value="item.value">
            {{ item.label }}
          </a-select-option>
        </a-select>
        <p class="field-hint">{{ t('settings.system.imageQualityUploadStrictnessHint') }}</p>
      </a-form-item>

      <a-divider orientation="left">{{ t('settings.system.imageQualityAfterSection') }}</a-divider>
      <p class="section-hint">{{ t('settings.system.imageQualityAfterHint') }}</p>
      <a-form-item>
        <a-checkbox v-model:checked="form.postRecognitionQualityEnabled" :disabled="!form.enabled">
          {{ t('settings.system.imageQualityPostRecognition') }}
        </a-checkbox>
      </a-form-item>

      <a-form-item :label="t('settings.system.imageQualityStatsRange')">
        <a-select
          v-model:value="statsRange"
          :disabled="!form.enabled || !form.postRecognitionQualityEnabled"
          style="width: 100%"
          @change="onStatsRangeChange"
        >
          <a-select-option v-for="item in statsRangeOptions" :key="item.value" :value="item.value">
            {{ item.label }}
          </a-select-option>
        </a-select>
        <p class="field-hint">{{ statsRangeHint }}</p>
      </a-form-item>

      <a-form-item :label="t('settings.system.imageQualityRecognitionStrictness')">
        <a-select
          v-model:value="recognitionStrictness"
          :disabled="!form.enabled || !form.postRecognitionQualityEnabled"
          style="width: 100%"
          @change="onRecognitionStrictnessChange"
        >
          <a-select-option v-for="item in recognitionStrictnessOptions" :key="item.value" :value="item.value">
            {{ item.label }}
          </a-select-option>
        </a-select>
        <p class="field-hint">{{ recognitionStrictnessHint }}</p>
      </a-form-item>

      <a-collapse
        v-model:activeKey="thresholdPanelActive"
        :bordered="false"
        class="threshold-collapse"
      >
        <a-collapse-panel
          key="thresholds"
          :header="t('settings.system.imageQualityAdvancedSection')"
          :collapsible="form.enabled && form.postRecognitionQualityEnabled ? undefined : 'disabled'"
        >
          <p class="section-hint">{{ t('settings.system.imageQualityAdvancedHint') }}</p>

          <p class="subsection-label">{{ t('settings.system.imageQualityBlockSection') }}</p>
          <p class="field-hint">{{ t('settings.system.imageQualityBlockHint') }}</p>
          <a-row :gutter="16">
            <a-col :span="12">
              <a-form-item :label="t('settings.system.imageQualityBlockBlur')">
                <a-input-number
                  v-model:value="form.blockBlurRowPercent"
                  :min="1"
                  :max="100"
                  addon-after="%"
                  style="width: 100%"
                  @change="markThresholdsCustom"
                />
              </a-form-item>
            </a-col>
            <a-col :span="12">
              <a-form-item :label="t('settings.system.imageQualityBlockUnknown')">
                <a-input-number
                  v-model:value="form.blockUnknownFieldPercent"
                  :min="1"
                  :max="100"
                  addon-after="%"
                  style="width: 100%"
                  @change="markThresholdsCustom"
                />
              </a-form-item>
            </a-col>
          </a-row>
            <a-row :gutter="16">
            <a-col :span="12">
              <a-form-item :label="t('settings.system.imageQualityBlockMalformed')">
                <a-input-number
                  v-model:value="form.blockMalformedRowPercent"
                  :min="0"
                  :max="100"
                  addon-after="%"
                  style="width: 100%"
                  @change="markThresholdsCustom"
                />
                <p class="field-hint">{{ t('settings.system.imageQualityBlockMalformedHint') }}</p>
              </a-form-item>
            </a-col>
          </a-row>
          <a-row :gutter="16">
            <a-col :span="12">
              <a-form-item :label="t('settings.system.imageQualityBlockFewRows')">
                <a-input-number
                  v-model:value="form.blockFewRowsMaxEffective"
                  :min="0"
                  :max="20"
                  style="width: 100%"
                  @change="markThresholdsCustom"
                />
              </a-form-item>
            </a-col>
            <a-col :span="12">
              <a-form-item :label="t('settings.system.imageQualityBlockFewRowsUnknown')">
                <a-input-number
                  v-model:value="form.blockFewRowsUnknownPercent"
                  :min="1"
                  :max="100"
                  addon-after="%"
                  style="width: 100%"
                  @change="markThresholdsCustom"
                />
              </a-form-item>
            </a-col>
          </a-row>
        </a-collapse-panel>
      </a-collapse>

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
import request from '@/api/index'

defineProps({
  showTitle: { type: Boolean, default: true },
})

const { t } = useI18n()
const saving = ref(false)
const uploadStrictness = ref('standard')
const statsRange = ref('attendance_only')
const recognitionStrictness = ref('standard')
const thresholdPanelActive = ref([])

const UPLOAD_STRICTNESS_MAP = {
  loose: 60,
  standard: 80,
  strict: 120,
}

const RECOGNITION_STRICTNESS_MAP = {
  loose: {
    blockBlurRowPercent: 60,
    blockUnknownFieldPercent: 75,
    blockMalformedRowPercent: 15,
    warnBlurRowPercent: 40,
    warnUnknownFieldPercent: 50,
    blockFewRowsMaxEffective: 3,
    blockFewRowsUnknownPercent: 85,
  },
  standard: {
    blockBlurRowPercent: 50,
    blockUnknownFieldPercent: 65,
    blockMalformedRowPercent: 10,
    warnBlurRowPercent: 30,
    warnUnknownFieldPercent: 40,
    blockFewRowsMaxEffective: 2,
    blockFewRowsUnknownPercent: 80,
  },
  strict: {
    blockBlurRowPercent: 40,
    blockUnknownFieldPercent: 55,
    blockMalformedRowPercent: 5,
    warnBlurRowPercent: 20,
    warnUnknownFieldPercent: 30,
    blockFewRowsMaxEffective: 2,
    blockFewRowsUnknownPercent: 70,
  },
}

const STATS_RANGE_MAP = {
  attendance_only: {
    blurRateDenominator: 'EFFECTIVE_ROWS',
    unknownRateScope: 'EFFECTIVE_ROWS',
    unknownRateExcludeAbsent: true,
  },
  named_rows: {
    blurRateDenominator: 'EFFECTIVE_ROWS',
    unknownRateScope: 'EFFECTIVE_ROWS',
    unknownRateExcludeAbsent: false,
  },
  all_rows: {
    blurRateDenominator: 'ALL_ROWS',
    unknownRateScope: 'ALL_ROWS',
    unknownRateExcludeAbsent: false,
  },
}

const DEFAULT_IMAGE_QUALITY = {
  enabled: true,
  preUploadSharpnessEnabled: true,
  postRecognitionQualityEnabled: true,
  minLaplacianVariance: 80,
  blurRateDenominator: 'EFFECTIVE_ROWS',
  unknownRateScope: 'EFFECTIVE_ROWS',
  unknownRateExcludeAbsent: true,
  ...RECOGNITION_STRICTNESS_MAP.standard,
}

const form = reactive({ ...DEFAULT_IMAGE_QUALITY })

const strictnessOptions = computed(() => [
  { value: 'loose', label: t('settings.system.imageQualityStrictnessLoose') },
  { value: 'standard', label: t('settings.system.imageQualityStrictnessStandard') },
  { value: 'strict', label: t('settings.system.imageQualityStrictnessStrict') },
])

const statsRangeOptions = computed(() => [
  { value: 'attendance_only', label: t('settings.system.imageQualityStatsRangeAttendance') },
  { value: 'named_rows', label: t('settings.system.imageQualityStatsRangeNamed') },
  { value: 'all_rows', label: t('settings.system.imageQualityStatsRangeAll') },
])

const recognitionStrictnessOptions = computed(() => [
  { value: 'loose', label: t('settings.system.imageQualityRecognitionLoose') },
  { value: 'standard', label: t('settings.system.imageQualityRecognitionStandard') },
  { value: 'strict', label: t('settings.system.imageQualityRecognitionStrict') },
  { value: 'custom', label: t('settings.system.imageQualityRecognitionCustom') },
])

const statsRangeHint = computed(() => {
  const key = `settings.system.imageQualityStatsRangeHint_${statsRange.value}`
  return t(key)
})

const recognitionStrictnessHint = computed(() => {
  if (recognitionStrictness.value === 'custom') {
    return t('settings.system.imageQualityRecognitionHint_custom')
  }
  const key = `settings.system.imageQualityRecognitionHint_${recognitionStrictness.value}`
  return t(key)
})

function resolveStrictnessFromVariance(variance) {
  const value = Number(variance) || 80
  if (value <= 65) return 'loose'
  if (value >= 100) return 'strict'
  return 'standard'
}

function resolveRecognitionStrictness(raw) {
  const candidates = ['loose', 'standard', 'strict']
  for (const key of candidates) {
    const preset = RECOGNITION_STRICTNESS_MAP[key]
    const match = Object.keys(preset).every((field) => Number(raw[field]) === preset[field])
    if (match) return key
  }
  return 'custom'
}

function syncThresholdPanel(strictness) {
  thresholdPanelActive.value = strictness === 'custom' ? ['thresholds'] : []
}

function markThresholdsCustom() {
  recognitionStrictness.value = 'custom'
}

function resolveStatsRange(raw) {
  const scope = raw.blurRateDenominator || raw.unknownRateScope || 'EFFECTIVE_ROWS'
  if (scope === 'ALL_ROWS') return 'all_rows'
  if (raw.unknownRateExcludeAbsent === false) return 'named_rows'
  return 'attendance_only'
}

function onUploadStrictnessChange(key) {
  form.minLaplacianVariance = UPLOAD_STRICTNESS_MAP[key] || 80
}

function onStatsRangeChange(key) {
  const preset = STATS_RANGE_MAP[key]
  if (!preset) return
  Object.assign(form, preset)
}

function onRecognitionStrictnessChange(key) {
  if (key === 'custom') {
    thresholdPanelActive.value = ['thresholds']
    return
  }
  const preset = RECOGNITION_STRICTNESS_MAP[key]
  if (!preset) return
  Object.assign(form, preset)
}

const applyImageQuality = (raw) => {
  if (!raw) return
  Object.assign(form, {
    ...DEFAULT_IMAGE_QUALITY,
    ...raw,
    unknownRateExcludeAbsent: raw.unknownRateExcludeAbsent !== false,
  })
  uploadStrictness.value = resolveStrictnessFromVariance(form.minLaplacianVariance)
  statsRange.value = resolveStatsRange(form)
  recognitionStrictness.value = resolveRecognitionStrictness(form)
  syncThresholdPanel(recognitionStrictness.value)
}

const loadConfig = async () => {
  try {
    const res = await request({
      url: '/config/system',
      method: 'get',
      silentError: true,
    })
    applyImageQuality(res.data?.imageQuality)
  } catch (e) {
    console.error(e)
  }
}

const handleSave = async () => {
  onUploadStrictnessChange(uploadStrictness.value)
  onStatsRangeChange(statsRange.value)
  if (recognitionStrictness.value !== 'custom') {
    onRecognitionStrictnessChange(recognitionStrictness.value)
  }
  saving.value = true
  try {
    await request({
      url: '/config/system',
      method: 'post',
      data: {
        imageQuality: { ...form },
      },
    })
    message.success(t('config.saveSuccess'))
    await loadConfig()
  } catch (e) {
    console.error(e)
  } finally {
    saving.value = false
  }
}

const handleReset = () => {
  Object.assign(form, DEFAULT_IMAGE_QUALITY)
  uploadStrictness.value = 'standard'
  statsRange.value = 'attendance_only'
  recognitionStrictness.value = 'standard'
  thresholdPanelActive.value = []
}

onMounted(loadConfig)
</script>

<style scoped lang="scss">
.image-quality-card :deep(.ant-card-head-title) {
  padding: 0;
}

.card-title {
  margin: 0 0 4px;
  font-size: 16px;
  font-weight: 600;
}

.card-desc,
.hint,
.section-hint,
.field-hint {
  margin: 0 0 8px;
  font-size: 13px;
  color: #8c8c8c;
  font-weight: normal;
  line-height: 1.5;
}

.field-hint {
  margin-top: 6px;
  margin-bottom: 0;
}

.threshold-collapse {
  margin-bottom: 16px;
  background: transparent;

  :deep(.ant-collapse-item) {
    border: 1px dashed #e8e8e8;
    border-radius: 8px;
    overflow: hidden;
  }

  :deep(.ant-collapse-header) {
    font-weight: 500;
  }
}

.subsection-label {
  margin: 12px 0 4px;
  font-size: 13px;
  font-weight: 600;
  color: #595959;
}
</style>
