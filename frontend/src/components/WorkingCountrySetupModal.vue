<template>
  <a-modal
    :open="open"
    :title="$t('country.setupTitle')"
    :width="480"
    :closable="false"
    :mask-closable="false"
    :keyboard="false"
    destroy-on-close
    :footer="null"
  >
    <p class="working-country-setup__desc">{{ $t('country.setupDesc') }}</p>
    <a-form layout="vertical" class="working-country-setup__form">
      <a-form-item :label="$t('country.setupLanguageLabel')">
        <a-select
          :value="selectedLocale"
          :options="languageSelectOptions"
          :placeholder="$t('language.select')"
          @change="handleLocaleChange"
        />
        <p class="working-country-setup__hint">{{ $t('country.setupLanguageHint') }}</p>
      </a-form-item>
      <a-form-item :label="$t('country.title')" required>
        <a-select
          v-model:value="selectedCountry"
          :options="countryOptions"
          :loading="countryStore.loading"
          show-search
          option-filter-prop="label"
          :placeholder="$t('country.setupPlaceholder')"
        />
      </a-form-item>
    </a-form>
    <a-button
      type="primary"
      block
      size="large"
      :loading="saving"
      :disabled="!selectedCountry"
      @click="handleSave"
    >
      {{ $t('country.setupConfirm') }}
    </a-button>
  </a-modal>
</template>

<script setup>
import { computed, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { message } from 'ant-design-vue'
import { useAuthStore } from '@/stores/auth'
import { useCountryStore } from '@/stores/country'
import { buildCountrySelectOption } from '@/utils/countryLabels'
import { markUserWorkingCountryConfigured, syncPersonalWorkingCountryOnUser } from '@/utils/workingCountrySetup'
import { setStoredUserInfo } from '@/utils/auth'
import { buildLanguageSelectOptions, SUPPORTED_LOCALES } from '@/constants/languageOptions'

const props = defineProps({
  open: { type: Boolean, default: false },
})

const emit = defineEmits(['update:open', 'saved'])

const { t, locale } = useI18n()
const authStore = useAuthStore()
const countryStore = useCountryStore()

const selectedCountry = ref(undefined)
const selectedLocale = ref(locale.value)
const saving = ref(false)

const languageSelectOptions = buildLanguageSelectOptions()

const countryOptions = computed(() => {
  void locale.value
  return (countryStore.options || [])
    .filter((item) => item.code && item.code !== 'default')
    .map((item) => buildCountrySelectOption(item))
})

function resolveStoredLocale() {
  const stored = localStorage.getItem('locale')
  if (stored && SUPPORTED_LOCALES.includes(stored)) return stored
  return locale.value
}

const handleLocaleChange = (value) => {
  if (!value || !SUPPORTED_LOCALES.includes(value)) return
  selectedLocale.value = value
  locale.value = value
  localStorage.setItem('locale', value)
}

watch(
  () => props.open,
  async (visible) => {
    if (!visible) return
    selectedCountry.value = undefined
    selectedLocale.value = resolveStoredLocale()
    locale.value = selectedLocale.value
    try {
      await countryStore.hydrate(true)
    } catch (error) {
      console.error('加载国家选项失败:', error)
    }
  },
)

const handleSave = async () => {
  if (!selectedCountry.value || saving.value) return
  saving.value = true
  try {
    const code = await countryStore.setWorkingCountry(selectedCountry.value)
    markUserWorkingCountryConfigured(code)
    if (authStore.userInfo) {
      authStore.userInfo = syncPersonalWorkingCountryOnUser(authStore.userInfo, code)
      setStoredUserInfo(authStore.userInfo)
    }
    message.success(t('country.saved'))
    emit('update:open', false)
    emit('saved', code)
  } catch (error) {
    console.error('设置工作国家失败:', error)
    message.error(error?.message || t('country.saveFailed'))
  } finally {
    saving.value = false
  }
}
</script>

<style lang="scss" scoped>
.working-country-setup__desc {
  margin: 0 0 16px;
  color: $text-secondary;
  line-height: 1.6;
}

.working-country-setup__form {
  :deep(.ant-form-item) {
    margin-bottom: 16px;
  }
}

.working-country-setup__hint {
  margin: 6px 0 0;
  font-size: $font-size-sm;
  color: $text-tertiary;
  line-height: 1.5;
}
</style>
