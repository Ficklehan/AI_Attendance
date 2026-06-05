<template>
  <div class="permission-mgmt">
    <PageShell :title="$t('settings.permissions.title')" :subtitle="$t('settings.permissions.subtitle')">
      <template #extra>
        <a-button type="primary" :loading="saving" @click="handleSave">
          {{ $t('common.save') }}
        </a-button>
      </template>
    </PageShell>

    <a-card class="surface-card" :bordered="false" :loading="loading">
      <div class="table-toolbar">
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
        :data-source="rows"
        :pagination="false"
        row-key="key"
        size="middle"
      >
        <template #bodyCell="{ column, record }">
          <template v-if="column.key === 'admin'">
            <CheckOutlined v-if="record.admin" class="perm-yes" />
            <CloseOutlined v-else class="perm-no" />
          </template>
          <template v-else-if="column.key === 'user'">
            <template v-if="record.editable">
              <a-switch
                v-model:checked="rolePermissions.user[record.key]"
                :checked-children="$t('common.yes')"
                :un-checked-children="$t('common.no')"
              />
            </template>
            <template v-else>
              <CheckOutlined v-if="record.user" class="perm-yes" />
              <CloseOutlined v-else class="perm-no" />
            </template>
          </template>
        </template>
      </a-table>
      <p class="permission-hint">{{ $t('settings.permissions.hint') }}</p>
      <p class="permission-hint">{{ $t('settings.permissions.calibrateHint') }}</p>
    </a-card>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { message } from 'ant-design-vue'
import { CheckOutlined, CloseOutlined } from '@ant-design/icons-vue'
import PageShell from '@/components/PageShell.vue'
import TableColumnSettings from '@/components/TableColumnSettings.vue'
import { getRolePermissions, updateRolePermissions } from '@/api/permissions'
import { withTableSorters, keyFieldSorter } from '@/utils/tableSort'
import { useColumnFreeze } from '@/composables/useColumnFreeze'
import { sumTableScrollX } from '@/utils/tableAutoColumns'

const { t } = useI18n()
const loading = ref(false)
const saving = ref(false)

const PERM_DEFS = [
  { key: 'tasks', nameKey: 'settings.permissions.capTasks', editable: false },
  { key: 'country', nameKey: 'settings.permissions.capCountry', editable: false },
  { key: 'aiConfig', nameKey: 'settings.permissions.capAiConfig', editable: false },
  { key: 'feishuConfig', nameKey: 'settings.permissions.capFeishuConfig', editable: false },
  { key: 'users', nameKey: 'settings.permissions.capUsers', editable: false },
  { key: 'audit', nameKey: 'settings.permissions.capAudit', editable: false },
  { key: 'recordCalibrate', nameKey: 'settings.permissions.capRecordCalibrate', editable: true },
]

const rolePermissions = reactive({
  admin: {},
  user: {},
})

const rows = computed(() =>
  PERM_DEFS.map((def) => ({
    key: def.key,
    name: t(def.nameKey),
    editable: def.editable,
    admin: !!rolePermissions.admin[def.key],
    user: !!rolePermissions.user[def.key],
  }))
)

const baseColumns = computed(() => withTableSorters([
  { title: t('settings.permissions.feature'), dataIndex: 'name', key: 'name' },
  { title: t('settings.users.roleAdmin'), key: 'admin', width: 120, align: 'center', sorter: keyFieldSorter('admin') },
  { title: t('settings.users.roleUser'), key: 'user', width: 140, align: 'center', sorter: keyFieldSorter('user') },
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
} = useColumnFreeze('permission-management', baseColumns, { defaultFrozen: ['name'] })
const scrollX = computed(() => sumTableScrollX(columns.value))

const loadPermissions = async () => {
  loading.value = true
  try {
    const res = await getRolePermissions()
    const data = res.data || {}
    PERM_DEFS.forEach((def) => {
      rolePermissions.admin[def.key] = !!data.admin?.[def.key]
      rolePermissions.user[def.key] = !!data.user?.[def.key]
    })
  } catch (e) {
    console.error(e)
  } finally {
    loading.value = false
  }
}

const handleSave = async () => {
  saving.value = true
  try {
    await updateRolePermissions({
      user: { ...rolePermissions.user },
    })
    message.success(t('settings.permissions.saved'))
  } catch (e) {
    console.error(e)
  } finally {
    saving.value = false
  }
}

onMounted(loadPermissions)
</script>

<style scoped lang="scss">
.perm-yes {
  color: #52c41a;
}

.perm-no {
  color: #d9d9d9;
}

.permission-hint {
  margin: 16px 0 0;
  color: var(--text-secondary, #73707f);
  font-size: 13px;
}
</style>
