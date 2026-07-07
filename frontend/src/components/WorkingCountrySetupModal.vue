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
    <a-form layout="vertical">
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

const props = defineProps({
  open: { type: Boolean, default: false },
})

const emit = defineEmits(['update:open', 'saved'])

const { t, locale } = useI18n()
const authStore = useAuthStore()
const countryStore = useCountryStore()

const selectedCountry = ref(undefined)
const saving = ref(false)

const countryOptions = computed(() => {
  void locale.value
  return (countryStore.options || [])
    .filter((item) => item.code && item.code !== 'default')
    .map((item) => buildCountrySelectOption(item))
})

watch(
  () => props.open,
  async (visible) => {
    if (!visible) return
    selectedCountry.value = undefined
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
      localStorage.setItem('userInfo', JSON.stringify(authStore.userInfo))
    }
    message.success(t('country.saved'))
    emit('update:open', false)
    emit('saved', code)
  } catch (error) {
    console.error('设置工作国家失败:', error)
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
</style>
