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
      <a-table
        :columns="columns"
        :data-source="users"
        :loading="loading"
        row-key="id"
        :pagination="pagination"
        @change="handleTableChange"
      >
        <template #bodyCell="{ column, record }">
          <template v-if="column.key === 'role'">
            <a-tag :color="record.role === 'admin' ? 'blue' : 'default'">
              {{ record.role === 'admin' ? $t('settings.users.roleAdmin') : $t('settings.users.roleUser') }}
            </a-tag>
          </template>
          <template v-else-if="column.key === 'status'">
            <a-tag :color="record.status === 'active' ? 'green' : 'red'">
              {{ record.status === 'active' ? $t('settings.users.statusActive') : $t('settings.users.statusDisabled') }}
            </a-tag>
          </template>
          <template v-else-if="column.key === 'action'">
            <a-button type="link" size="small" @click="openEdit(record)">{{ $t('common.edit') }}</a-button>
          </template>
        </template>
      </a-table>
    </a-card>

    <a-modal
      v-model:open="modalOpen"
      :title="editingId ? $t('settings.users.editTitle') : $t('settings.users.createTitle')"
      :confirm-loading="saving"
      @ok="submitForm"
    >
      <a-form layout="vertical" :model="form">
        <a-form-item v-if="!editingId" :label="$t('auth.username')" required>
          <a-input v-model:value="form.username" />
        </a-form-item>
        <a-form-item :label="$t('settings.users.email')" required>
          <a-input v-model:value="form.email" />
        </a-form-item>
        <a-form-item :label="$t('auth.password')" :required="!editingId">
          <a-input-password v-model:value="form.password" :placeholder="editingId ? $t('settings.users.passwordKeep') : ''" />
        </a-form-item>
        <a-form-item :label="$t('settings.users.realName')">
          <a-input v-model:value="form.realName" />
        </a-form-item>
        <a-form-item :label="$t('settings.users.employeeId')">
          <a-input v-model:value="form.employeeId" />
        </a-form-item>
        <a-form-item :label="$t('settings.users.feishuUserId')" :help="$t('settings.users.feishuUserIdHint')">
          <a-input v-model:value="form.feishuUserId" :placeholder="$t('settings.users.feishuUserIdPlaceholder')" allow-clear />
        </a-form-item>
        <a-form-item :label="$t('settings.users.role')">
          <a-select v-model:value="form.role" :options="roleOptions" />
        </a-form-item>
        <a-form-item v-if="editingId" :label="$t('common.status')">
          <a-select v-model:value="form.status" :options="statusOptions" />
        </a-form-item>
      </a-form>
    </a-modal>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { message } from 'ant-design-vue'
import { PlusOutlined } from '@ant-design/icons-vue'
import PageShell from '@/components/PageShell.vue'
import { listUsers, createUser, updateUser } from '@/api/users'
import { withTableSorters, keyFieldSorter } from '@/utils/tableSort'

const { t } = useI18n()

const users = ref([])
const loading = ref(false)
const saving = ref(false)
const modalOpen = ref(false)
const editingId = ref('')
const page = ref(1)
const pageSize = ref(20)
const total = ref(0)

const form = reactive({
  username: '',
  email: '',
  password: '',
  realName: '',
  employeeId: '',
  feishuUserId: '',
  role: 'user',
  status: 'active',
})

const roleOptions = computed(() => [
  { value: 'user', label: t('settings.users.roleUser') },
  { value: 'admin', label: t('settings.users.roleAdmin') },
])

const statusOptions = computed(() => [
  { value: 'active', label: t('settings.users.statusActive') },
  { value: 'disabled', label: t('settings.users.statusDisabled') },
])

const columns = computed(() => withTableSorters([
  { title: t('auth.username'), dataIndex: 'username', key: 'username' },
  { title: t('settings.users.email'), dataIndex: 'email', key: 'email' },
  { title: t('settings.users.realName'), dataIndex: 'realName', key: 'realName' },
  { title: t('settings.users.feishuUserId'), dataIndex: 'feishuUserId', key: 'feishuUserId', ellipsis: true, width: 140 },
  { title: t('settings.users.role'), key: 'role', width: 100, sorter: keyFieldSorter('role') },
  { title: t('common.status'), key: 'status', width: 100, sorter: keyFieldSorter('status') },
  { title: t('common.operation'), key: 'action', width: 90 },
]))

const pagination = computed(() => ({
  current: page.value,
  pageSize: pageSize.value,
  total: total.value,
  showSizeChanger: true,
}))

const resetForm = () => {
  form.username = ''
  form.email = ''
  form.password = ''
  form.realName = ''
  form.employeeId = ''
  form.feishuUserId = ''
  form.role = 'user'
  form.status = 'active'
}

const fetchUsers = async () => {
  loading.value = true
  try {
    const res = await listUsers({ current: page.value, size: pageSize.value })
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
}

const openEdit = (record) => {
  editingId.value = record.id
  form.username = record.username
  form.email = record.email || ''
  form.password = ''
  form.realName = record.realName || ''
  form.employeeId = record.employeeId || ''
  form.feishuUserId = record.feishuUserId || ''
  form.role = record.role || 'user'
  form.status = record.status || 'active'
  modalOpen.value = true
}

const submitForm = async () => {
  saving.value = true
  try {
    if (editingId.value) {
      const payload = {
        email: form.email,
        realName: form.realName,
        employeeId: form.employeeId,
        feishuUserId: form.feishuUserId,
        role: form.role,
        status: form.status,
      }
      if (form.password) payload.password = form.password
      await updateUser(editingId.value, payload)
      message.success(t('settings.users.updated'))
    } else {
      await createUser({
        username: form.username,
        email: form.email,
        password: form.password,
        realName: form.realName,
        employeeId: form.employeeId,
        role: form.role,
      })
      message.success(t('settings.users.created'))
    }
    modalOpen.value = false
    fetchUsers()
  } finally {
    saving.value = false
  }
}

onMounted(fetchUsers)
</script>
