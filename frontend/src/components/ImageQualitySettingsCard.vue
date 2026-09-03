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

      <p class="section-hint">{{ t('settings.system.imageQualityPostRecognitionDisabledHint') }}</p>

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

const UPLOAD_STRICTNESS_MAP = {
  loose: 60,
  standard: 80,
  strict: 120,
}

/** 识别后阈值仍随配置持久化，但产品侧已关闭拦截，仅保留上传锐度。 */
const DEFAULT_IMAGE_QUALITY = {
  enabled: true,
  preUploadSharpnessEnabled: true,
  postRecognitionQualityEnabled: false,
  minLaplacianVariance: 80,
  blurRateDenominator: 'EFFECTIVE_ROWS',
  unknownRateScope: 'EFFECTIVE_ROWS',
  unknownRateExcludeAbsent: true,
  blockBlurRowPercent: 50,
  blockUnknownFieldPercent: 65,
  blockMalformedRowPercent: 10,
  warnBlurRowPercent: 30,
  warnUnknownFieldPercent: 40,
  blockFewRowsMaxEffective: 2,
  blockFewRowsUnknownPercent: 80,
}

const form = reactive({ ...DEFAULT_IMAGE_QUALITY })

const strictnessOptions = computed(() => [
  { value: 'loose', label: t('settings.system.imageQualityStrictnessLoose') },
  { value: 'standard', label: t('settings.system.imageQualityStrictnessStandard') },
  { value: 'strict', label: t('settings.system.imageQualityStrictnessStrict') },
])

function resolveStrictnessFromVariance(variance) {
  const value = Number(variance) || 80
  if (value <= 65) return 'loose'
  if (value >= 100) return 'strict'
  return 'standard'
}

function onUploadStrictnessChange(key) {
  form.minLaplacianVariance = UPLOAD_STRICTNESS_MAP[key] || 80
}

const applyImageQuality = (raw) => {
  if (!raw) return
  Object.assign(form, {
    ...DEFAULT_IMAGE_QUALITY,
    ...raw,
    postRecognitionQualityEnabled: false,
    unknownRateExcludeAbsent: raw.unknownRateExcludeAbsent !== false,
  })
  uploadStrictness.value = resolveStrictnessFromVariance(form.minLaplacianVariance)
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
  form.postRecognitionQualityEnabled = false
  saving.value = true
  try {
    await request({
      url: '/config/system',
      method: 'post',
      data: {
        imageQuality: { ...form, postRecognitionQualityEnabled: false },
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
</style>
