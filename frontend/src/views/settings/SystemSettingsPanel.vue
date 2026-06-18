<template>
  <div class="system-settings-panel">
    <a-card class="surface-card" :bordered="false">
      <a-form layout="vertical">
        <a-form-item>
          <div class="setting-item">
            <div class="setting-info">
              <h4>{{ t('config.systemConfig.notificationEnabled') }}</h4>
              <p class="setting-desc">{{ t('config.systemConfig.notificationEnabledDesc') }}</p>
            </div>
            <a-switch v-model:checked="form.notificationEnabled" />
          </div>
        </a-form-item>

        <a-divider>{{ t('settings.system.confirmValidationTitle') }}</a-divider>
        <p class="hint">{{ t('settings.system.confirmValidationDesc') }}</p>
        <p class="hint hint--muted">{{ t('settings.system.confirmValidationScopeHint') }}</p>

        <a-form-item :label="t('settings.system.confirmValidationFields')">
          <a-checkbox-group v-model:value="form.requiredFields" class="field-checkboxes">
            <a-row>
              <a-col v-for="field in fieldOptions" :key="field.key" :span="8">
                <a-checkbox :value="field.key">{{ t(field.labelKey) }}</a-checkbox>
              </a-col>
            </a-row>
          </a-checkbox-group>
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
  </div>
</template>

<script setup>
import { onMounted, reactive, ref } from 'vue'
import { message } from 'ant-design-vue'
import { useI18n } from 'vue-i18n'
import request from '@/api/index'
import { CONFIRM_FIELD_KEYS, DEFAULT_CONFIRM_VALIDATION } from '@/utils/requiredRecordFields'
import { FIELD_LABEL_KEYS } from '@/constants/calibratableFields'

const { t } = useI18n()
const saving = ref(false)

const form = reactive({
  notificationEnabled: true,
  requiredFields: [...DEFAULT_CONFIRM_VALIDATION.requiredFields],
})

const fieldOptions = CONFIRM_FIELD_KEYS.map((key) => ({
  key,
  labelKey: FIELD_LABEL_KEYS[key] || key,
}))

const loadConfig = async () => {
  try {
    const res = await request({ url: '/config/system', method: 'get' })
    if (typeof res.data?.notificationEnabled === 'boolean') {
      form.notificationEnabled = res.data.notificationEnabled
    }
    const cv = res.data?.confirmValidation
    if (cv?.requiredFields?.length) {
      form.requiredFields = [...cv.requiredFields]
    }
  } catch (e) {
    console.error(e)
  }
}

const handleSave = async () => {
  if (!form.requiredFields.length) {
    message.warning(t('settings.system.confirmValidationFieldsRequired'))
    return
  }
  saving.value = true
  try {
    await request({
      url: '/config/system',
      method: 'post',
      data: {
        notificationEnabled: form.notificationEnabled,
        confirmValidation: {
          scope: DEFAULT_CONFIRM_VALIDATION.scope,
          requiredFields: form.requiredFields,
        },
      },
    })
    message.success(t('config.saveSuccess'))
  } catch (e) {
    console.error(e)
  } finally {
    saving.value = false
  }
}

const handleReset = () => {
  form.notificationEnabled = true
  form.requiredFields = [...DEFAULT_CONFIRM_VALIDATION.requiredFields]
}

onMounted(loadConfig)
</script>

<style scoped lang="scss">
.setting-item {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
}

.setting-info h4 {
  margin: 0 0 4px;
  font-size: 15px;
}

.setting-desc,
.hint {
  margin: 0;
  font-size: 13px;
  color: #8c8c8c;
}

.hint--muted {
  margin-bottom: 12px;
}

.field-checkboxes {
  width: 100%;
}
</style>
