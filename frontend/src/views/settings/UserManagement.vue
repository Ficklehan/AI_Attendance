<template>
  <div class="user-mgmt">
    <PageShell :title="$t('settings.users.title')" :subtitle="$t('settings.users.subtitle')">
      <template #extra>
        <a-button type="primary" @click="openCreate">
          <template #icon><PlusOutlined /></template>
          {{ $t('settings.users.add') }}
        </a-button>
      </template>
    </PageShell>

    <a-card class="surface-card" :bordered="false">
      <a-alert
        type="info"
        show-icon
        class="user-role-hint"
        :message="$t('settings.users.rolesHintTitle')"
        :description="$t('settings.users.rolesHintDesc')"
      />
      <div class="table-toolbar">
        <a-input-search
          v-model:value="searchKeyword"
          class="user-search"
          :placeholder="$t('settings.users.searchPlaceholder')"
          allow-clear
          @search="handleSearch"
        />
        <TableColumnSettings
          :columns="configurableColumns"
          :hidden-keys="hiddenKeys"
          :frozen-keys="frozenKeys"
          @update:hidden-keys="setHiddenKeys"
          @update:frozen-keys="setFrozenKeys"
          @show-all="showAllColumns"
          @clear-freeze="clearFrozenKeys"
        />
      </div>
      <a-table
        :columns="columns"
        :scroll="{ x: scrollX }"
        :data-source="users"
        :loading="loading"
        row-key="id"
        :pagination="pagination"
        class="rich-table-header"
        @change="handleTableChange"
      >
        <template #bodyCell="{ column, record, text }">
          <template v-if="column.key === 'role'">
            <CopyableCell :text="displayUserRoles(record)">
              <a-space wrap size="small">
                <a-tag
                  v-for="roleKey in userRoleKeys(record)"
                  :key="roleKey"
                  :color="roleKey === 'admin' ? 'blue' : 'default'"
                >
                  {{ roleNameMap[roleKey] || roleKey }}
                </a-tag>
              </a-space>
            </CopyableCell>
          </template>
          <template v-else-if="column.key === 'workingCountry'">
            <CopyableCell :text="formatUserCountry(record)">
              <a-tag color="geekblue">{{ formatUserCountry(record) }}</a-tag>
            </CopyableCell>
          </template>
          <template v-else-if="column.key === 'status'">
            <CopyableCell :text="record.status === 'active' ? $t('settings.users.statusActive') : $t('settings.users.statusDisabled')">
              <a-tag :color="record.status === 'active' ? 'green' : 'red'">
                {{ record.status === 'active' ? $t('settings.users.statusActive') : $t('settings.users.statusDisabled') }}
              </a-tag>
            </CopyableCell>
          </template>
          <template v-else-if="column.key === 'action'">
            <div class="table-action-cell table-action-cell--links table-action-cell--links-3">
              <span class="table-action-cell__slot">
                <a-button type="link" size="small" @click="openEdit(record)">{{ $t('common.edit') }}</a-button>
              </span>
              <span class="table-action-cell__slot">
                <a-popconfirm
                  v-if="record.status === 'active'"
                  :title="$t('settings.users.disableConfirm', { name: displayName(record) })"
                  :ok-text="$t('settings.users.disable')"
                  :cancel-text="$t('common.cancel')"
                  :disabled="isCurrentUser(record)"
                  @confirm="toggleUserStatus(record, 'disabled')"
                >
                  <a-button
                    type="link"
                    size="small"
                    danger
                    :disabled="isCurrentUser(record)"
                  >
                    {{ $t('settings.users.disable') }}
                  </a-button>
                </a-popconfirm>
                <a-popconfirm
                  v-else
                  :title="$t('settings.users.enableConfirm', { name: displayName(record) })"
                  :ok-text="$t('settings.users.enable')"
                  :cancel-text="$t('common.cancel')"
                  @confirm="toggleUserStatus(record, 'active')"
                >
                  <a-button type="link" size="small">{{ $t('settings.users.enable') }}</a-button>
                </a-popconfirm>
              </span>
              <span class="table-action-cell__slot">
                <a-popconfirm
                  :title="$t('settings.users.deleteConfirm', { name: displayName(record) })"
                  :ok-text="$t('common.delete')"
                  :cancel-text="$t('common.cancel')"
                  :disabled="isCurrentUser(record)"
                  @confirm="handleDeleteUser(record)"
                >
                  <a-button
                    type="link"
                    size="small"
                    danger
                    :disabled="isCurrentUser(record)"
                  >
                    {{ $t('common.delete') }}
                  </a-button>
                </a-popconfirm>
              </span>
            </div>
          </template>
          <template v-else-if="isCopyableTableColumn(column)">
            <CopyableCell :text="resolveTableCellCopyText(column, record, text)" />
          </template>
        </template>
      </a-table>
    </a-card>

    <a-modal
      v-model:open="modalOpen"
      :title="editingId ? $t('settings.users.editTitle') : $t('settings.users.createTitle')"
      :confirm-loading="saving"
      width="640px"
      destroy-on-close
      @ok="submitForm"
      @after-close="resetFormValidation"
    >
      <a-form
        ref="formRef"
        layout="vertical"
        :model="form"
        :rules="formRules"
        class="user-form"
      >
        <div class="form-section-title">{{ $t('settings.users.formSectionProfile') }}</div>
        <a-row :gutter="16">
          <a-col :span="12">
            <a-form-item :label="$t('settings.users.realName')" name="realName">
              <a-input v-model:value="form.realName" :placeholder="$t('settings.users.realNamePlaceholder')" />
            </a-form-item>
          </a-col>
          <a-col :span="12">
            <a-form-item :label="$t('settings.users.employeeId')" name="employeeId">
              <a-input v-model:value="form.employeeId" :placeholder="$t('settings.users.employeeIdPlaceholder')" />
            </a-form-item>
          </a-col>
        </a-row>

        <div class="form-section-title">{{ $t('settings.users.formSectionAccount') }}</div>
        <a-form-item v-if="!editingId" :label="$t('auth.username')" name="username">
          <a-input v-model:value="form.username" :placeholder="$t('settings.users.usernamePlaceholder')" />
        </a-form-item>
        <a-form-item :label="$t('settings.users.email')" name="email">
          <a-input v-model:value="form.email" type="email" :placeholder="$t('settings.users.emailPlaceholder')" />
        </a-form-item>
        <a-form-item :label="$t('auth.password')" name="password" :required="!editingId">
          <a-input-password
            v-model:value="form.password"
            :placeholder="editingId ? $t('settings.users.passwordKeep') : $t('settings.users.passwordPlaceholder')"
          />
        </a-form-item>

        <div class="form-section-title">{{ $t('settings.users.formSectionBinding') }}</div>
        <a-form-item :label="$t('settings.users.feishuUserId')" name="feishuUserId" :help="$t('settings.users.feishuUserIdHint')">
          <a-input v-model:value="form.feishuUserId" :placeholder="$t('settings.users.feishuUserIdPlaceholder')" allow-clear />
        </a-form-item>

        <div class="form-section-title">{{ $t('settings.users.formSectionAccess') }}</div>
        <a-row :gutter="16">
          <a-col :span="12">
            <a-form-item :label="$t('settings.users.workingCountry')" name="workingCountry" :help="$t('settings.users.workingCountryHint')">
              <a-select
                v-model:value="form.workingCountry"
                :options="countryOptions"
                show-search
                :filter-option="filterCountryOption"
                allow-clear
                :placeholder="$t('settings.users.workingCountryPlaceholder')"
              />
            </a-form-item>
          </a-col>
          <a-col :span="12">
            <a-form-item :label="$t('settings.users.role')" name="roles">
              <a-select
                v-model:value="form.roles"
                mode="multiple"
                :options="roleOptions"
                :placeholder="$t('settings.users.rolesPlaceholder')"
              />
            </a-form-item>
          </a-col>
        </a-row>
        <a-form-item v-if="editingId" :label="$t('common.status')" name="status">
          <a-select v-model:value="form.status" :options="statusOptions" />
        </a-form-item>
      </a-form>
    </a-modal>
  </div>
</template>

<script setup>
import { computed, nextTick, onMounted, reactive, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { message } from 'ant-design-vue'
import { PlusOutlined } from '@ant-design/icons-vue'
import PageShell from '@/components/PageShell.vue'
import CopyableCell from '@/components/CopyableCell.vue'
import TableColumnSettings from '@/components/TableColumnSettings.vue'
import { useAuthStore } from '@/stores/auth'
import { useCountryStore } from '@/stores/country'
import { listUsers, createUser, updateUser, updateUserStatus, deleteUser } from '@/api/users'
import { listRoles } from '@/api/roles'
import { withTableSorters, keyFieldSorter } from '@/utils/tableSort'
import { useColumnFreeze } from '@/composables/useColumnFreeze'
import { sumTableScrollX } from '@/utils/tableAutoColumns'
import { isCopyableTableColumn, resolveTableCellCopyText } from '@/utils/tableCopy'
import { formatCountryLabel } from '@/utils/countryLabels'
import { setCachedWorkingCountry } from '@/utils/countryHeader'

const { t } = useI18n()
const authStore = useAuthStore()
const countryStore = useCountryStore()

const users = ref([])
const loading = ref(false)
const saving = ref(false)
const modalOpen = ref(false)
const editingId = ref('')
const formRef = ref(null)
const page = ref(1)
const pageSize = ref(20)
const total = ref(0)
const searchKeyword = ref('')

const form = reactive({
  username: '',
  email: '',
  password: '',
  realName: '',
  employeeId: '',
  feishuUserId: '',
  workingCountry: 'default',
  roles: ['user'],
  status: 'active',
})

const countryOptions = computed(() => countryStore.selectOptions)

const filterCountryOption = (input, option) => {
  const label = (option?.label || '').toLowerCase()
  const value = (option?.value || '').toLowerCase()
  const q = (input || '').toLowerCase()
  return label.includes(q) || value.includes(q)
}

const formatUserCountry = (record) => {
  const code = record.workingCountry || 'default'
  const meta = countryStore.options.find((item) => item.code === code)
  return formatCountryLabel(code, meta?.flag, meta?.name)
}

const systemRoles = ref([])

const roleOptions = computed(() =>
  systemRoles.value.map((role) => ({
    value: role.roleKey,
    label: role.roleName,
  }))
)

const roleNameMap = computed(() => {
  const map = {}
  systemRoles.value.forEach((role) => {
    map[role.roleKey] = role.roleName
  })
  return map
})

const userRoleKeys = (record) => {
  if (Array.isArray(record?.roles) && record.roles.length) return record.roles
  return record?.role ? [record.role] : ['user']
}

const displayUserRoles = (record) => userRoleKeys(record)
  .map((key) => roleNameMap.value[key] || key)
  .join(', ')

const fetchRoles = async () => {
  try {
    const res = await listRoles()
    systemRoles.value = res.data || []
  } catch (e) {
    console.error(e)
  }
}

const statusOptions = computed(() => [
  { value: 'active', label: t('settings.users.statusActive') },
  { value: 'disabled', label: t('settings.users.statusDisabled') },
])

const requiredRule = (labelKey) => ({
  required: true,
  whitespace: true,
  message: t('validation.required', { field: t(labelKey) }),
  trigger: 'blur',
})

const formRules = computed(() => {
  const rules = {
    realName: [requiredRule('settings.users.realName')],
    email: [
      {
        validator: (_rule, value) => {
          const v = (value || '').trim()
          if (!v) return Promise.resolve()
          const ok = /^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(v)
          if (!ok) {
            return Promise.reject(
              new Error(t('validation.invalidFormat', { field: t('settings.users.email') })),
            )
          }
          return Promise.resolve()
        },
        trigger: 'blur',
      },
    ],
  }
  if (!editingId.value) {
    rules.username = [
      requiredRule('auth.username'),
      {
        min: 2,
        max: 64,
        message: t('validation.minLength', { field: t('auth.username'), min: 2 }),
        trigger: 'blur',
      },
    ]
    rules.password = [
      requiredRule('auth.password'),
      {
        min: 6,
        max: 64,
        message: t('validation.minLength', { field: t('auth.password'), min: 6 }),
        trigger: 'blur',
      },
    ]
  } else {
    rules.password = [
      {
        validator: (_rule, value) => {
          if (!value || value.length >= 6) return Promise.resolve()
          return Promise.reject(
            new Error(t('validation.minLength', { field: t('auth.password'), min: 6 })),
          )
        },
        trigger: 'blur',
      },
    ]
  }
  return rules
})

const resetFormValidation = () => {
  formRef.value?.clearValidate()
}

const baseColumns = computed(() => withTableSorters([
  { title: t('auth.username'), dataIndex: 'username', key: 'username' },
  { title: t('settings.users.email'), dataIndex: 'email', key: 'email' },
  { title: t('settings.users.realName'), dataIndex: 'realName', key: 'realName' },
  { title: t('settings.users.workingCountry'), key: 'workingCountry', width: 140, sorter: keyFieldSorter('workingCountry') },
  { title: t('settings.users.feishuUserId'), dataIndex: 'feishuUserId', key: 'feishuUserId', ellipsis: true, width: 140 },
  { title: t('settings.users.role'), key: 'role', width: 180, sorter: keyFieldSorter('role') },
  { title: t('common.status'), key: 'status', width: 100, sorter: keyFieldSorter('status') },
  { title: t('common.operation'), key: 'action', width: 220, align: 'center', fixed: 'right' },
]))
const {
  frozenColumns: columns,
  hiddenKeys,
  frozenKeys,
  configurableColumns,
  setHiddenKeys,
  setFrozenKeys,
  showAllColumns,
  clearFrozenKeys,
} = useColumnFreeze('user-management', baseColumns, { defaultFrozen: ['username'] })
const scrollX = computed(() => sumTableScrollX(columns.value))

const pagination = computed(() => ({
  current: page.value,
  pageSize: pageSize.value,
  total: total.value,
  showSizeChanger: true,
}))

const displayName = (record) => record.realName || record.username || record.email || record.id

const isCurrentUser = (record) => record.id === authStore.userInfo?.id

const handleDeleteUser = async (record) => {
  if (isCurrentUser(record)) {
    message.warning(t('settings.users.cannotDeleteSelf'))
    return
  }
  try {
    await deleteUser(record.id)
    message.success(t('settings.users.deleted'))
    fetchUsers()
  } catch (e) {
    message.error(e.message || t('common.error'))
  }
}

const toggleUserStatus = async (record, status) => {
  if (status === 'disabled' && isCurrentUser(record)) {
    message.warning(t('settings.users.cannotDisableSelf'))
    return
  }
  try {
    await updateUserStatus(record.id, status)
    message.success(t('settings.users.statusUpdated'))
    fetchUsers()
  } catch (e) {
    console.error(e)
  }
}

const resetForm = () => {
  form.username = ''
  form.email = ''
  form.password = ''
  form.realName = ''
  form.employeeId = ''
  form.feishuUserId = ''
  form.workingCountry = 'default'
  form.roles = ['user']
  form.status = 'active'
}

const handleSearch = () => {
  page.value = 1
  fetchUsers()
}

const fetchUsers = async () => {
  loading.value = true
  try {
    const params = { current: page.value, size: pageSize.value }
    const kw = searchKeyword.value?.trim()
    if (kw) params.keyword = kw
    const res = await listUsers(params)
    users.value = res.data?.records || []
    total.value = res.data?.total || 0
  } finally {
    loading.value = false
  }
}

const handleTableChange = (pag) => {
  page.value = pag.current
  pageSize.value = pag.pageSize
  fetchUsers()
}

const openCreate = () => {
  editingId.value = ''
  resetForm()
  modalOpen.value = true
  nextTick(resetFormValidation)
}

const openEdit = (record) => {
  editingId.value = record.id
  form.username = record.username
  form.email = record.email || ''
  form.password = ''
  form.realName = record.realName || ''
  form.employeeId = record.employeeId || ''
  form.feishuUserId = record.feishuUserId || ''
  form.workingCountry = record.workingCountry || 'default'
  form.roles = userRoleKeys(record)
  form.status = record.status || 'active'
  modalOpen.value = true
  nextTick(resetFormValidation)
}

const submitForm = async () => {
  const valid = await formRef.value?.validate().catch(() => false)
  if (!valid) return

  saving.value = true
  try {
    const realName = form.realName.trim()
    if (editingId.value) {
      const payload = {
        email: form.email?.trim() || '',
        realName,
        employeeId: form.employeeId?.trim() || '',
        feishuUserId: form.feishuUserId,
        workingCountry: form.workingCountry === 'default' ? '' : form.workingCountry,
        roles: form.roles?.length ? form.roles : ['user'],
        status: form.status,
      }
      if (form.password) payload.password = form.password
      await updateUser(editingId.value, payload)
      if (editingId.value === authStore.userInfo?.id) {
        const effective = form.workingCountry || 'default'
        setCachedWorkingCountry(effective)
        countryStore.workingCountry = effective
        await countryStore.loadBundle(effective)
        await authStore.fetchUserInfo()
        await authStore.refreshPermissions(effective !== 'default' ? effective : undefined)
      }
      message.success(t('settings.users.updated'))
    } else {
      await createUser({
        username: form.username.trim(),
        email: form.email?.trim() || '',
        password: form.password,
        realName,
        employeeId: form.employeeId?.trim() || '',
        workingCountry: form.workingCountry === 'default' ? '' : form.workingCountry,
        roles: form.roles?.length ? form.roles : ['user'],
      })
      message.success(t('settings.users.created'))
    }
    modalOpen.value = false
    fetchUsers()
  } finally {
    saving.value = false
  }
}

onMounted(async () => {
  await countryStore.hydrate()
  fetchRoles()
  fetchUsers()
})
</script>

<style scoped>
.table-toolbar {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 12px;
  margin-bottom: 12px;
}

.user-search {
  width: min(360px, 100%);
}

.user-role-hint {
  margin-bottom: 16px;
}

.user-form :deep(.ant-form-item) {
  margin-bottom: 16px;
}

.user-form :deep(.ant-form-item:last-child) {
  margin-bottom: 0;
}

.form-section-title {
  margin: 4px 0 12px;
  font-size: 13px;
  font-weight: 600;
  color: rgba(0, 0, 0, 0.65);
}

.form-section-title:not(:first-child) {
  margin-top: 8px;
  padding-top: 12px;
  border-top: 1px solid rgba(0, 0, 0, 0.06);
}
</style>
