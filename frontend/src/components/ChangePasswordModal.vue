<template>
  <a-modal
    :open="open"
    :title="$t('auth.changePassword')"
    :width="440"
    :confirm-loading="submitting"
    :ok-text="$t('common.confirm')"
    :cancel-text="$t('common.cancel')"
    destroy-on-close
    @ok="handleSubmit"
    @cancel="handleCancel"
  >
    <p class="change-password-hint">{{ $t('auth.changePasswordHint') }}</p>
    <a-form layout="vertical" class="change-password-form">
      <a-form-item
        :label="$t('auth.oldPassword')"
        required
        :validate-status="errors.oldPassword ? 'error' : ''"
        :help="errors.oldPassword"
      >
        <a-input-password
          v-model:value="form.oldPassword"
          autocomplete="current-password"
          @pressEnter="handleSubmit"
        />
      </a-form-item>
      <a-form-item
        :label="$t('auth.newPassword')"
        required
        :validate-status="errors.newPassword ? 'error' : ''"
        :help="errors.newPassword || $t('auth.passwordMinHint')"
      >
        <a-input-password
          v-model:value="form.newPassword"
          autocomplete="new-password"
        />
      </a-form-item>
      <a-form-item
        :label="$t('auth.confirmPassword')"
        required
        :validate-status="errors.confirmPassword ? 'error' : ''"
        :help="errors.confirmPassword"
      >
        <a-input-password
          v-model:value="form.confirmPassword"
          autocomplete="new-password"
          @pressEnter="handleSubmit"
        />
      </a-form-item>
    </a-form>
  </a-modal>
</template>

<script setup>
import { reactive, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { useRouter } from 'vue-router'
import { message } from 'ant-design-vue'
import { changePassword } from '@/api/auth'
import { useAuthStore } from '@/stores/auth'
import { showErrorMessage } from '@/utils/translateError'

const props = defineProps({
  open: { type: Boolean, default: false },
})

const emit = defineEmits(['update:open'])

const { t } = useI18n()
const router = useRouter()
const authStore = useAuthStore()

const submitting = ref(false)
const form = reactive({
  oldPassword: '',
  newPassword: '',
  confirmPassword: '',
})
const errors = reactive({
  oldPassword: '',
  newPassword: '',
  confirmPassword: '',
})

const resetForm = () => {
  form.oldPassword = ''
  form.newPassword = ''
  form.confirmPassword = ''
  errors.oldPassword = ''
  errors.newPassword = ''
  errors.confirmPassword = ''
}

const validate = () => {
  errors.oldPassword = ''
  errors.newPassword = ''
  errors.confirmPassword = ''
  let ok = true

  if (!form.oldPassword.trim()) {
    errors.oldPassword = t('auth.oldPasswordRequired')
    ok = false
  }
  if (!form.newPassword.trim()) {
    errors.newPassword = t('auth.newPasswordRequired')
    ok = false
  } else if (form.newPassword.length < 6) {
    errors.newPassword = t('auth.passwordMinLength')
    ok = false
  }
  if (!form.confirmPassword.trim()) {
    errors.confirmPassword = t('auth.confirmPasswordRequired')
    ok = false
  } else if (form.newPassword !== form.confirmPassword) {
    errors.confirmPassword = t('auth.passwordMismatch')
    ok = false
  }
  if (ok && form.oldPassword === form.newPassword) {
    errors.newPassword = t('auth.passwordSameAsOld')
    ok = false
  }
  return ok
}

const handleCancel = () => {
  emit('update:open', false)
}

const handleSubmit = () => {
  if (!validate()) {
    return Promise.reject()
  }

  submitting.value = true
  return changePassword({
    oldPassword: form.oldPassword,
    newPassword: form.newPassword,
  })
    .then(() => {
      message.success(t('auth.changePasswordSuccess'))
      emit('update:open', false)
      authStore.logout()
      router.push('/login')
    })
    .catch((e) => {
      showErrorMessage(e)
      return Promise.reject(e)
    })
    .finally(() => {
      submitting.value = false
    })
}

watch(
  () => props.open,
  (visible) => {
    if (visible) resetForm()
  },
)
</script>

<style scoped lang="scss">
.change-password-hint {
  margin: 0 0 16px;
  font-size: 13px;
  color: $text-secondary;
  line-height: 1.5;
}

.change-password-form {
  :deep(.ant-form-item) {
    margin-bottom: 16px;
  }
}
</style>
