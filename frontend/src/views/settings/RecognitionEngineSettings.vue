<template>
  <div class="recognition-engine-page page-inner">
    <PageShell
      :title="t('settings.recognitionEngine.title')"
      :subtitle="t('settings.recognitionEngine.subtitle')"
    />

    <a-card class="surface-card" :bordered="false">
      <template #title>
        <div class="card-header-flex">
          <div>
            <h3 class="card-title">{{ t('settings.recognitionEngine.activeModel') }}</h3>
            <p class="card-desc">{{ t('settings.recognitionEngine.activeModelDesc') }}</p>
          </div>
          <a-tag color="purple">{{ t('settings.recognitionEngine.globalTag') }}</a-tag>
        </div>
      </template>

      <a-form layout="vertical">
        <a-form-item :label="t('settings.recognitionEngine.modelLabel')">
          <a-radio-group v-model:value="engine.engine" button-style="solid">
            <a-radio-button value="mimo" :disabled="!engine.mimoConfigured">
              MiMo
            </a-radio-button>
            <a-radio-button value="deepseek" :disabled="!engine.deepseekConfigured">
              DeepSeek
            </a-radio-button>
          </a-radio-group>
        </a-form-item>

        <div class="engine-meta">
          <a-tag :color="engine.mimoConfigured ? 'success' : 'default'">
            MiMo · {{ engine.mimoModel || 'mimo-v2.5' }}
            <span v-if="!engine.mimoConfigured">（{{ t('settings.recognitionEngine.notConfigured') }}）</span>
          </a-tag>
          <a-tag :color="engine.deepseekConfigured ? 'success' : 'default'">
            DeepSeek · {{ engine.deepseekModel || 'deepseek-chat' }}
            <span v-if="!engine.deepseekConfigured">（{{ t('settings.recognitionEngine.notConfigured') }}）</span>
          </a-tag>
        </div>

        <a-alert
          v-if="!engine.deepseekConfigured"
          type="info"
          show-icon
          class="env-alert"
          :message="t('settings.recognitionEngine.deepseekSetupTitle')"
          :description="t('settings.recognitionEngine.deepseekSetupDesc')"
        />

        <a-form-item>
          <a-button type="primary" :loading="saving" @click="handleSave">
            {{ t('common.save') }}
          </a-button>
        </a-form-item>
      </a-form>
    </a-card>

    <a-card class="surface-card env-card" :bordered="false" :title="t('settings.recognitionEngine.envTitle')">
      <p class="env-intro">{{ t('settings.recognitionEngine.envIntro') }}</p>
      <a-table
        :columns="envColumns"
        :data-source="envRows"
        :pagination="false"
        size="small"
        row-key="key"
      />
      <p class="env-footnote">{{ t('settings.recognitionEngine.envFootnote') }}</p>
    </a-card>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { message } from 'ant-design-vue'
import { useI18n } from 'vue-i18n'
import PageShell from '@/components/PageShell.vue'
import request from '@/api/index'

const { t } = useI18n()
const saving = ref(false)

const engine = reactive({
  engine: 'mimo',
  mimoConfigured: false,
  deepseekConfigured: false,
  mimoModel: '',
  deepseekModel: '',
})

const envColumns = computed(() => [
  { title: t('settings.recognitionEngine.envColVar'), dataIndex: 'key', key: 'key', width: 220 },
  { title: t('settings.recognitionEngine.envColDesc'), dataIndex: 'desc', key: 'desc' },
  { title: t('settings.recognitionEngine.envColExample'), dataIndex: 'example', key: 'example', width: 280 },
])

const envRows = computed(() => [
  { key: 'MIMO_API_KEY', desc: t('settings.recognitionEngine.envMimoKey'), example: 'sk-...' },
  { key: 'MIMO_API_KEY_1 / MIMO_API_KEYS', desc: t('settings.recognitionEngine.envMimoKeys'), example: 'sk-key1,sk-key2' },
  { key: 'MIMO_API_URL', desc: t('settings.recognitionEngine.envMimoUrl'), example: 'https://api.xiaomimimo.com/v1' },
  { key: 'MIMO_MODEL', desc: t('settings.recognitionEngine.envMimoModel'), example: 'mimo-v2.5' },
  { key: 'DEEPSEEK_API_KEY', desc: t('settings.recognitionEngine.envDeepseekKey'), example: 'sk-...' },
  { key: 'DEEPSEEK_API_KEY_1 / DEEPSEEK_API_KEYS', desc: t('settings.recognitionEngine.envDeepseekKeys'), example: 'sk-key1,sk-key2' },
  { key: 'DEEPSEEK_API_URL', desc: t('settings.recognitionEngine.envDeepseekUrl'), example: 'https://api.deepseek.com/v1' },
  { key: 'DEEPSEEK_MODEL', desc: t('settings.recognitionEngine.envDeepseekModel'), example: 'deepseek-chat' },
])

const loadConfig = async () => {
  const res = await request({ url: '/config/recognition-engine', method: 'get' })
  if (!res.data) return
  engine.engine = res.data.engine || 'mimo'
  engine.mimoConfigured = !!res.data.mimoConfigured
  engine.deepseekConfigured = !!res.data.deepseekConfigured
  engine.mimoModel = res.data.mimoModel || ''
  engine.deepseekModel = res.data.deepseekModel || ''
}

const handleSave = async () => {
  saving.value = true
  try {
    const res = await request({
      url: '/config/recognition-engine',
      method: 'post',
      data: { engine: engine.engine },
    })
    if (res.data) {
      engine.engine = res.data.engine || engine.engine
      engine.mimoConfigured = !!res.data.mimoConfigured
      engine.deepseekConfigured = !!res.data.deepseekConfigured
      engine.mimoModel = res.data.mimoModel || engine.mimoModel
      engine.deepseekModel = res.data.deepseekModel || engine.deepseekModel
    }
    message.success(t('settings.recognitionEngine.saveSuccess'))
  } catch (error) {
    console.error('保存识别引擎失败:', error)
  } finally {
    saving.value = false
  }
}

onMounted(() => {
  loadConfig().catch((error) => console.error('加载识别引擎配置失败:', error))
})
</script>

<style scoped lang="scss">
.recognition-engine-page {
  display: flex;
  flex-direction: column;
  gap: $spacing-lg;
}

.card-header-flex {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: $spacing-md;
  width: 100%;
}

.card-title {
  margin: 0 0 4px;
  font-size: 16px;
  font-weight: 600;
}

.card-desc {
  margin: 0;
  font-size: $font-size-sm;
  color: $text-secondary;
}

.engine-meta {
  display: flex;
  flex-wrap: wrap;
  gap: $spacing-sm;
  margin-bottom: $spacing-md;
}

.env-alert {
  margin-bottom: $spacing-md;
}

.env-intro,
.env-footnote {
  margin: 0 0 $spacing-md;
  color: $text-secondary;
  font-size: $font-size-sm;
  line-height: 1.6;
}

.env-footnote {
  margin: $spacing-md 0 0;
}
</style>
