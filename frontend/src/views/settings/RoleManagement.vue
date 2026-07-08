<template>
  <div class="role-mgmt">
    <PageShell :title="$t('settings.roles.title')" :subtitle="$t('settings.roles.subtitle')">
      <template #extra>
        <a-space>
          <a-button @click="openCreateRole">
            <template #icon><PlusOutlined /></template>
            {{ $t('settings.roles.addRole') }}
          </a-button>
          <a-button
            type="primary"
            :loading="saving"
            :disabled="!canSaveCurrentRole"
            @click="handleSave"
          >
            {{ $t('common.save') }}
          </a-button>
        </a-space>
      </template>
    </PageShell>

    <div class="role-layout">
      <a-card class="surface-card role-list-card" :bordered="false" :loading="loading">
        <div class="role-list-title">{{ $t('settings.roles.roleList') }}</div>
        <a-menu
          v-model:selected-keys="selectedRoleKeys"
          mode="inline"
          class="role-menu"
          @click="onRoleMenuClick"
        >
          <a-menu-item v-for="role in systemRoles" :key="role.roleKey">
            <div class="role-menu-item">
              <span>{{ role.roleName }}</span>
              <a-tag v-if="role.builtIn" size="small">{{ $t('settings.roles.builtIn') }}</a-tag>
            </div>
          </a-menu-item>
        </a-menu>
      </a-card>

      <a-card class="surface-card role-config-card" :bordered="false" :loading="loading">
        <template v-if="activeRole">
          <div class="role-header">
            <div>
              <h3>{{ activeRole.roleName }}</h3>
              <span class="role-key">{{ activeRole.roleKey }}</span>
            </div>
            <a-space>
              <a-button v-if="!activeRole.builtIn" size="small" @click="openRenameRole">
                {{ $t('settings.roles.renameRole') }}
              </a-button>
              <a-popconfirm
                v-if="!activeRole.builtIn"
                :title="$t('settings.roles.deleteRoleConfirm', { name: activeRole.roleName })"
                @confirm="handleDeleteRole"
              >
                <a-button size="small" danger>{{ $t('common.delete') }}</a-button>
              </a-popconfirm>
            </a-space>
          </div>

          <p class="flow-hint">{{ $t('settings.roles.flowHint') }}</p>

          <a-tabs v-model:activeKey="activeTab" class="role-tabs">
            <a-tab-pane key="functional" :tab="$t('settings.roles.stepFunctional')">
              <p class="tab-desc">{{ $t('settings.roles.functionalDesc') }}</p>

              <div v-if="isAdminRole" class="admin-readonly">
                <a-tag color="blue">{{ $t('settings.roles.adminFixed') }}</a-tag>
                <p class="tab-desc">{{ $t('settings.roles.adminFunctionalHint') }}</p>
              </div>

              <template v-else>
                <div class="perm-list">
                  <div v-for="def in PERM_DEFS" :key="def.key" class="perm-row">
                    <div class="perm-label-block">
                      <span class="perm-label">{{ $t(def.nameKey) }}</span>
                      <p v-if="def.hintKey" class="perm-hint">{{ $t(def.hintKey) }}</p>
                    </div>
                    <a-switch
                      :checked="getFunctionalPerm(def.key)"
                      :checked-children="$t('common.yes')"
                      :un-checked-children="$t('common.no')"
                      @update:checked="(val) => setFunctionalPerm(def.key, val)"
                    />
                  </div>
                </div>
              </template>
            </a-tab-pane>

            <a-tab-pane key="dataScope" :tab="$t('settings.roles.stepDataScope')">
              <template v-if="currentScope?.editable">
                <p class="tab-desc">{{ $t('settings.roles.dataScopeDesc') }}</p>
                <a-radio-group v-model:value="activeScopeType">
                  <a-radio value="restricted">{{ $t('settings.roles.scopeRestricted') }}</a-radio>
                  <a-radio value="all">{{ $t('settings.roles.scopeAll') }}</a-radio>
                </a-radio-group>

                <template v-if="activeScopeType === 'restricted'">
                  <div class="dimension-hint-row">
                    <p class="tab-desc">{{ $t('settings.roles.restrictedHint') }}</p>
                    <a-button size="small" :loading="optionsLoading" @click="refreshDimensionOptions">
                      {{ $t('settings.roles.refreshOptions') }}
                    </a-button>
                  </div>
                  <p class="tab-desc tab-desc--sub">{{ $t('settings.roles.optionsFromCatalog') }}</p>

                  <div class="dimension-block">
                    <label class="dimension-label">{{ $t('settings.roles.dimOwnerUser') }}</label>
                    <DimensionCheckboxPicker
                      v-model="activeOwnerUserRules"
                      :options="ownerUserOptions"
                      :max-height="ownerUserPickerHeight"
                    />
                  </div>

                  <div class="dimension-block">
                    <label class="dimension-label">{{ $t('settings.roles.dimWorkCountryRegion') }}</label>
                    <p class="tab-desc tab-desc--sub dimension-inline-hint">{{ $t('settings.roles.dimWorkCountryRegionHint') }}</p>
                    <DimensionCheckboxPicker
                      v-model="unifiedWorkCountrySelection"
                      :options="catalogWorkCountryOptions"
                      :max-height="220"
                    />
                  </div>

                  <div class="dimension-block">
                    <label class="dimension-label">{{ $t('settings.roles.dimWarehouse') }}</label>
                    <DimensionCheckboxPicker
                      v-model="activeWarehouseRules"
                      :options="warehouseOptions"
                      :max-height="220"
                    />
                  </div>

                  <div class="dimension-block">
                    <label class="dimension-label">{{ $t('settings.roles.dimAgency') }}</label>
                    <DimensionCheckboxPicker
                      v-model="activeAgencyRules"
                      :options="agencyOptions"
                      :max-height="220"
                    />
                  </div>
                </template>
                <p v-else class="tab-desc">{{ $t('settings.roles.allHint') }}</p>
              </template>

              <template v-else>
                <a-tag color="blue">{{ $t('settings.roles.scopeAll') }}</a-tag>
                <p class="tab-desc">{{ $t('settings.roles.adminDataHint') }}</p>
              </template>
            </a-tab-pane>

            <a-tab-pane key="members" :tab="$t('settings.roles.stepMembers')">
              <a-alert
                type="info"
                show-icon
                class="role-hint-alert"
                :message="$t('settings.roles.membersCrossLinkTitle')"
                :description="$t('settings.roles.membersCrossLinkDesc')"
              />
              <p class="tab-desc">{{ $t('settings.roles.membersDesc') }}</p>
              <div class="members-toolbar">
                <a-input-search
                  v-model:value="memberKeyword"
                  class="member-search"
                  :placeholder="$t('settings.users.searchPlaceholder')"
                  allow-clear
                  @search="loadMembers"
                />
                <a-button type="primary" @click="openAddMembers">
                  <template #icon><PlusOutlined /></template>
                  {{ $t('settings.roles.addMembers') }}
                </a-button>
              </div>
              <a-table
                :columns="memberColumns"
                :data-source="members"
                :loading="membersLoading"
                row-key="id"
                class="rich-table-header"
                :pagination="memberPagination"
                @change="handleMemberTableChange"
              >
                <template #bodyCell="{ column, record }">
                  <template v-if="column.key === 'action'">
                    <a-popconfirm
                      v-if="canRemoveMember"
                      :title="$t('settings.roles.removeMemberConfirm', { name: memberDisplayName(record) })"
                      :ok-text="$t('common.confirm')"
                      :cancel-text="$t('common.cancel')"
                      :disabled="!canRemoveMemberRecord(record)"
                      @confirm="handleRemoveMember(record)"
                    >
                      <a-button
                        type="link"
                        size="small"
                        danger
                        :disabled="!canRemoveMemberRecord(record)"
                      >
                        {{ $t('settings.roles.removeMember') }}
                      </a-button>
                    </a-popconfirm>
                    <span v-else class="member-action-muted">{{ $t('settings.roles.defaultRoleMemberHint') }}</span>
                  </template>
                </template>
              </a-table>
            </a-tab-pane>
          </a-tabs>
        </template>
      </a-card>
    </div>

    <a-modal
      v-model:open="createRoleOpen"
      :title="$t('settings.roles.addRole')"
      :confirm-loading="roleSaving"
      @ok="submitCreateRole"
    >
      <a-form layout="vertical">
        <a-form-item :label="$t('settings.roles.roleKey')" required>
          <a-input v-model:value="createRoleForm.roleKey" :placeholder="$t('settings.roles.roleKeyHint')" />
        </a-form-item>
        <a-form-item :label="$t('settings.roles.roleName')" required>
          <a-input v-model:value="createRoleForm.roleName" />
        </a-form-item>
      </a-form>
    </a-modal>

    <a-modal
      v-model:open="renameRoleOpen"
      :title="$t('settings.roles.renameRole')"
      :confirm-loading="roleSaving"
      @ok="submitRenameRole"
    >
      <a-form layout="vertical">
        <a-form-item :label="$t('settings.roles.roleName')" required>
          <a-input v-model:value="renameRoleForm.roleName" />
        </a-form-item>
      </a-form>
    </a-modal>

    <a-modal
      v-model:open="addMembersOpen"
      :title="$t('settings.roles.addMembers')"
      :confirm-loading="memberSaving"
      width="720px"
      @ok="submitAddMembers"
    >
      <a-input-search
        v-model:value="candidateKeyword"
        :placeholder="$t('settings.users.searchPlaceholder')"
        allow-clear
        class="member-search"
        @search="loadCandidates"
      />
      <a-table
        class="rich-table-header candidate-table"
        :row-selection="candidateRowSelection"
        :columns="candidateColumns"
        :data-source="candidates"
        :loading="candidatesLoading"
        row-key="id"
        :pagination="candidatePagination"
        @change="handleCandidateTableChange"
      >
        <template #bodyCell="{ column, record }">
          <template v-if="column.key === 'role'">
            <a-space wrap size="small">
              <a-tag
                v-for="roleKey in userRoleKeys(record)"
                :key="roleKey"
                :color="roleKey === 'admin' ? 'blue' : 'default'"
              >
                {{ roleNameMap[roleKey] || roleKey }}
              </a-tag>
            </a-space>
          </template>
        </template>
      </a-table>
    </a-modal>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { onBeforeRouteLeave } from 'vue-router'
import { useI18n } from 'vue-i18n'
import { message, Modal } from 'ant-design-vue'
import { PlusOutlined } from '@ant-design/icons-vue'
import PageShell from '@/components/PageShell.vue'
import DimensionCheckboxPicker from '@/components/DimensionCheckboxPicker.vue'
import { getRoleDataScopes, updateRoleDataScope, getDataScopeDimensionOptions } from '@/api/dataScope'
import { getRolePermissions, updateRolePermissions } from '@/api/permissions'
import { listRoles, createRole, updateRole, deleteRole, getRoleMembers, getRoleMemberCandidates, addRoleMembers, removeRoleMember } from '@/api/roles'
import { useCountryStore } from '@/stores/country'
import { useAuthStore } from '@/stores/auth'

const { t } = useI18n()
const countryStore = useCountryStore()
const authStore = useAuthStore()
const loading = ref(false)
const saving = ref(false)
const roleSaving = ref(false)
const optionsLoading = ref(false)

const PERM_DEFS = [
  { key: 'tasks', nameKey: 'settings.roles.capTasks' },
  { key: 'country', nameKey: 'settings.roles.capCountry' },
  { key: 'aiConfig', nameKey: 'settings.roles.capAiConfig' },
  { key: 'feishuConfig', nameKey: 'settings.roles.capFeishuConfig' },
  { key: 'users', nameKey: 'settings.roles.capUsers' },
  { key: 'audit', nameKey: 'settings.roles.capAudit' },
  { key: 'recordCalibrate', nameKey: 'settings.roles.capRecordCalibrate' },
  {
    key: 'taskDeleteConfirmed',
    nameKey: 'settings.roles.capTaskDeleteConfirmed',
    hintKey: 'settings.roles.capTaskDeleteConfirmedHint',
  },
  { key: 'reminderConfig', nameKey: 'settings.roles.capReminderConfig' },
  { key: 'employees', nameKey: 'settings.roles.capEmployees' },
]

const systemRoles = ref([])
const roleScopes = reactive({})
const rolePermissions = reactive({})
const selectedRoleKeys = ref(['user'])
const activeTab = ref('functional')

const members = ref([])
const membersLoading = ref(false)
const memberKeyword = ref('')
const memberPagination = reactive({ current: 1, pageSize: 10, total: 0, showSizeChanger: true })

const addMembersOpen = ref(false)
const memberSaving = ref(false)
const candidates = ref([])
const candidatesLoading = ref(false)
const candidateKeyword = ref('')
const selectedCandidateIds = ref([])
const candidatePagination = reactive({ current: 1, pageSize: 10, total: 0 })

const dimensionOptions = reactive({
  country: [],
  warehouse: [],
  agency: [],
  owner_user: [],
  work_region: [],
  work_country_region: [],
})

const createRoleOpen = ref(false)
const renameRoleOpen = ref(false)
const createRoleForm = reactive({ roleKey: '', roleName: '' })
const renameRoleForm = reactive({ roleName: '' })
const savedSnapshots = ref({})
let lastActiveTab = 'functional'

const activeRoleKey = computed(() => selectedRoleKeys.value[0] || 'user')
const activeRole = computed(() => systemRoles.value.find((r) => r.roleKey === activeRoleKey.value) || null)
const currentScope = computed(() => roleScopes[activeRoleKey.value] || null)

const snapshotRole = (roleKey) => JSON.stringify({
  perms: rolePermissions[roleKey] || {},
  scope: roleScopes[roleKey] || {},
})

const refreshSnapshots = () => {
  const next = {}
  systemRoles.value.forEach((role) => {
    next[role.roleKey] = snapshotRole(role.roleKey)
  })
  savedSnapshots.value = next
}

const isRoleDirty = (roleKey) => {
  const key = roleKey ?? activeRoleKey.value
  if (key === 'admin') return false
  const saved = savedSnapshots.value[key]
  if (!saved) return false
  return saved !== snapshotRole(key)
}

const isConfigTab = (tab) => tab === 'functional' || tab === 'dataScope'

const confirmDiscardChanges = (onOk) => {
  Modal.confirm({
    title: t('settings.roles.unsavedTitle'),
    content: t('settings.roles.unsavedContent'),
    okText: t('settings.roles.discardChanges'),
    cancelText: t('common.cancel'),
    onOk,
  })
}

const onTabChange = (nextTab) => {
  if (nextTab === 'members') loadMembers()
}

watch(activeTab, (newTab) => {
  const oldTab = lastActiveTab
  if (newTab !== oldTab && isConfigTab(oldTab) && isRoleDirty()) {
    activeTab.value = oldTab
    confirmDiscardChanges(() => {
      activeTab.value = newTab
      lastActiveTab = newTab
      onTabChange(newTab)
    })
    return
  }
  lastActiveTab = newTab
  if (newTab !== oldTab) onTabChange(newTab)
})

const activeScopeType = computed({
  get: () => currentScope.value?.scopeType ?? 'restricted',
  set: (value) => {
    if (currentScope.value) currentScope.value.scopeType = value
  },
})

const activeOwnerUserRules = computed({
  get: () => currentScope.value?.rules?.owner_user ?? [],
  set: (value) => {
    if (currentScope.value?.rules) currentScope.value.rules.owner_user = value
  },
})

const activeWarehouseRules = computed({
  get: () => currentScope.value?.rules?.warehouse ?? [],
  set: (value) => {
    if (currentScope.value?.rules) currentScope.value.rules.warehouse = value
  },
})

const activeAgencyRules = computed({
  get: () => currentScope.value?.rules?.agency ?? [],
  set: (value) => {
    if (currentScope.value?.rules) currentScope.value.rules.agency = value
  },
})

const isAdminRole = computed(() => activeRoleKey.value === 'admin')
const canSaveCurrentRole = computed(() => !isAdminRole.value && !!currentScope.value?.editable)
const canRemoveMember = computed(() => activeRoleKey.value !== 'user')

const memberColumns = computed(() => [
  { title: t('auth.username'), dataIndex: 'username', key: 'username' },
  { title: t('settings.users.realName'), dataIndex: 'realName', key: 'realName' },
  { title: t('settings.users.email'), dataIndex: 'email', key: 'email', ellipsis: true },
  { title: t('common.operation'), key: 'action', width: 140, align: 'center' },
])

const candidateColumns = computed(() => [
  { title: t('auth.username'), dataIndex: 'username', key: 'username' },
  { title: t('settings.users.realName'), dataIndex: 'realName', key: 'realName' },
  { title: t('settings.users.email'), dataIndex: 'email', key: 'email', ellipsis: true },
  { title: t('settings.users.role'), key: 'role', width: 180 },
])

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

const refreshAuthIfSelf = async (userIds) => {
  const selfId = authStore.userInfo?.id
  if (!selfId || !userIds.some((id) => id === selfId)) return
  await authStore.fetchUserInfo()
  await authStore.refreshPermissions()
}

const candidateRowSelection = computed(() => ({
  selectedRowKeys: selectedCandidateIds.value,
  onChange: (keys) => {
    selectedCandidateIds.value = keys
  },
}))

const formatOptionLabel = (value, label) => {
  const text = (label || value || '').trim()
  if (!text || text === value) return value
  if (text.includes(value)) return text
  return `${text} (${value})`
}

const normalizeApiOption = (opt) => {
  if (opt == null) return null
  if (typeof opt === 'string') return { value: opt, label: opt }
  const value = opt.value ?? opt.id
  if (!value) return null
  return { value, label: formatOptionLabel(value, opt.label || value) }
}

const mergePickerOptions = (apiOptions, selectedValues = [], extraOptions = []) => {
  const map = new Map()
  const push = (opt) => {
    const normalized = normalizeApiOption(opt)
    if (normalized) map.set(normalized.value, normalized)
  }
  extraOptions.forEach(push)
  ;(apiOptions || []).forEach(push)
  ;(selectedValues || []).forEach((value) => {
    if (value && !map.has(value)) {
      map.set(value, { value, label: value })
    }
  })
  return Array.from(map.values()).sort((a, b) => {
    if (a.value === '__self__') return -1
    if (b.value === '__self__') return 1
    return a.label.localeCompare(b.label, undefined, { sensitivity: 'base' })
  })
}

const ownerUserOptions = computed(() =>
  mergePickerOptions(
    dimensionOptions.owner_user,
    currentScope.value?.rules?.owner_user,
    [{ value: '__self__', label: t('settings.roles.selfOnly') }]
  )
)

const catalogWorkCountryOptions = computed(() => {
  const fromStore = (countryStore.selectOptions || [])
    .filter((item) => {
      const code = item.value || item.code
      return code && code !== 'default'
    })
    .map((item) => ({
      value: item.value || item.code,
      label: item.label || item.value || item.code,
    }))
  return mergePickerOptions(
    fromStore.length ? fromStore : dimensionOptions.work_country_region,
    unifiedWorkCountrySelection.value
  )
})

const unifiedWorkCountrySelection = computed({
  get() {
    const rules = currentScope.value?.rules
    if (!rules) return []
    const merged = new Set(
      [...(rules.country || []), ...(rules.work_region || [])]
        .map((value) => String(value || '').trim().toUpperCase())
        .filter(Boolean)
    )
    return Array.from(merged)
  },
  set(values) {
    const rules = currentScope.value?.rules
    if (!rules) return
    const normalized = [...new Set(
      (values || [])
        .map((value) => String(value || '').trim().toUpperCase())
        .filter(Boolean)
    )]
    rules.country = [...normalized]
    rules.work_region = [...normalized]
  },
})

const warehouseOptions = computed(() =>
  mergePickerOptions(dimensionOptions.warehouse, currentScope.value?.rules?.warehouse)
)

const agencyOptions = computed(() =>
  mergePickerOptions(dimensionOptions.agency, currentScope.value?.rules?.agency)
)

const ownerUserPickerHeight = computed(() => {
  const count = ownerUserOptions.value.length
  if (count <= 6) return 160
  if (count <= 12) return 200
  return 260
})

const emptyRules = () => ({
  owner_user: [],
  country: [],
  warehouse: [],
  agency: [],
  work_region: [],
})

const syncScopeState = (scopes) => {
  const next = {}
  Object.entries(scopes || {}).forEach(([roleKey, scope]) => {
    const rules = scope.rules || {}
    next[roleKey] = {
      editable: scope.editable !== false && roleKey !== 'admin',
      scopeType: scope.scopeType || 'restricted',
      rules: {
        ...emptyRules(),
        owner_user: [...(rules.owner_user || [])],
        country: [...(rules.country || [])],
        warehouse: [...(rules.warehouse || [])],
        agency: [...(rules.agency || [])],
        work_region: [...(rules.work_region || [])],
      },
    }
  })
  Object.keys(roleScopes).forEach((key) => delete roleScopes[key])
  Object.assign(roleScopes, next)
}

const syncPermissionState = (data) => {
  Object.keys(rolePermissions).forEach((key) => delete rolePermissions[key])
  const roles = data?.roles || data || {}
  systemRoles.value.forEach((role) => {
    const roleKey = role.roleKey
    const source = roles?.[roleKey] || {}
    rolePermissions[roleKey] = {}
    PERM_DEFS.forEach((def) => {
      rolePermissions[roleKey][def.key] = !!source[def.key]
    })
  })
}

const getFunctionalPerm = (key) => !!rolePermissions[activeRoleKey.value]?.[key]

const setFunctionalPerm = (key, checked) => {
  rolePermissions[activeRoleKey.value][key] = checked
}

const memberDisplayName = (record) => record.realName || record.username || record.email || record.id

const canRemoveMemberRecord = (record) => {
  if (!canRemoveMember.value) return false
  if (activeRoleKey.value === 'admin') {
    if (record.id === authStore.userInfo?.id) return false
  }
  return true
}

const applyDimensionOptions = (opts) => {
  dimensionOptions.country = opts.country || opts.work_country_region || []
  dimensionOptions.work_country_region = opts.work_country_region || opts.country || []
  dimensionOptions.warehouse = opts.warehouse || []
  dimensionOptions.agency = opts.agency || []
  dimensionOptions.owner_user = opts.owner_user || []
  dimensionOptions.work_region = opts.work_region || opts.work_country_region || []
}

const refreshDimensionOptions = async () => {
  optionsLoading.value = true
  try {
    const optionsRes = await getDataScopeDimensionOptions()
    applyDimensionOptions(optionsRes.data || {})
    message.success(t('settings.roles.optionsRefreshed'))
  } catch (e) {
    message.error(e.message || t('common.error'))
  } finally {
    optionsLoading.value = false
  }
}

const loadData = async () => {
  loading.value = true
  try {
    const [rolesRes, scopesRes, permsRes, optionsRes] = await Promise.all([
      listRoles(),
      getRoleDataScopes(),
      getRolePermissions(),
      getDataScopeDimensionOptions(),
      countryStore.hydrate(),
    ])

    systemRoles.value = rolesRes.data || []
    if (!systemRoles.value.some((r) => r.roleKey === selectedRoleKeys.value[0])) {
      selectedRoleKeys.value = [systemRoles.value[0]?.roleKey || 'user']
    }

    syncScopeState(scopesRes.data)
    syncPermissionState(permsRes.data || {})
    applyDimensionOptions(optionsRes.data || {})
    refreshSnapshots()
  } catch (e) {
    message.error(e.message || t('common.error'))
  } finally {
    loading.value = false
  }
}

const onRoleMenuClick = ({ key }) => {
  if (key === activeRoleKey.value) return
  const switchRole = () => {
    selectedRoleKeys.value = [key]
    memberPagination.current = 1
    if (activeTab.value === 'members') {
      loadMembers()
    }
  }
  if (isRoleDirty()) {
    confirmDiscardChanges(switchRole)
    return
  }
  switchRole()
}

const loadMembers = async () => {
  if (!activeRoleKey.value) return
  membersLoading.value = true
  try {
    const res = await getRoleMembers(activeRoleKey.value, {
      page: memberPagination.current,
      size: memberPagination.pageSize,
      keyword: memberKeyword.value || undefined,
    })
    const page = res.data || {}
    members.value = page.records || []
    memberPagination.total = page.total || 0
  } catch (e) {
    message.error(e.message || t('common.error'))
  } finally {
    membersLoading.value = false
  }
}

const handleMemberTableChange = (pag) => {
  memberPagination.current = pag.current
  memberPagination.pageSize = pag.pageSize
  loadMembers()
}

const openAddMembers = async () => {
  selectedCandidateIds.value = []
  candidateKeyword.value = ''
  candidatePagination.current = 1
  addMembersOpen.value = true
  await loadCandidates()
}

const loadCandidates = async () => {
  candidatesLoading.value = true
  try {
    const res = await getRoleMemberCandidates(activeRoleKey.value, {
      page: candidatePagination.current,
      size: candidatePagination.pageSize,
      keyword: candidateKeyword.value || undefined,
    })
    const page = res.data || {}
    candidates.value = page.records || []
    candidatePagination.total = page.total || 0
  } catch (e) {
    message.error(e.message || t('common.error'))
  } finally {
    candidatesLoading.value = false
  }
}

const handleCandidateTableChange = (pag) => {
  candidatePagination.current = pag.current
  candidatePagination.pageSize = pag.pageSize
  loadCandidates()
}

const submitAddMembers = async () => {
  if (!selectedCandidateIds.value.length) {
    message.warning(t('settings.roles.selectUsersToAdd'))
    return
  }
  memberSaving.value = true
  try {
    await addRoleMembers(activeRoleKey.value, selectedCandidateIds.value)
    addMembersOpen.value = false
    message.success(t('settings.roles.memberAdded'))
    await refreshAuthIfSelf(selectedCandidateIds.value)
    await loadMembers()
  } catch (e) {
    message.error(e.message || t('config.saveFailed'))
  } finally {
    memberSaving.value = false
  }
}

const handleRemoveMember = async (record) => {
  try {
    await removeRoleMember(activeRoleKey.value, record.id)
    message.success(t('settings.roles.memberRemoved'))
    await refreshAuthIfSelf([record.id])
    await loadMembers()
  } catch (e) {
    message.error(e.message || t('config.saveFailed'))
  }
}

const hasRestrictedRules = (rules = {}) => {
  const dims = ['owner_user', 'country', 'warehouse', 'agency', 'work_region']
  return dims.some((dim) => Array.isArray(rules[dim]) && rules[dim].length > 0)
}

const handleSave = async () => {
  const roleKey = activeRoleKey.value
  const scope = roleScopes[roleKey]
  const perms = rolePermissions[roleKey]
  if (!scope?.editable || !perms) return

  if (scope.scopeType === 'restricted' && !hasRestrictedRules(scope.rules)) {
    message.warning(t('settings.roles.restrictedEmptyWarning'))
    return
  }

  saving.value = true
  try {
    await Promise.all([
      updateRolePermissions({
        roles: { [roleKey]: { ...perms } },
      }),
      updateRoleDataScope(roleKey, {
        scopeType: scope.scopeType,
        rules: scope.scopeType === 'restricted' ? { ...scope.rules } : emptyRules(),
      }),
    ])
    message.success(t('settings.roles.saved'))
    await loadData()
    refreshSnapshots()
  } catch (e) {
    message.error(e.message || t('config.saveFailed'))
  } finally {
    saving.value = false
  }
}

const openCreateRole = () => {
  createRoleForm.roleKey = ''
  createRoleForm.roleName = ''
  createRoleOpen.value = true
}

const submitCreateRole = async () => {
  if (!createRoleForm.roleKey.trim() || !createRoleForm.roleName.trim()) {
    message.warning(t('settings.roles.roleRequired'))
    return
  }
  roleSaving.value = true
  try {
    const created = await createRole({
      roleKey: createRoleForm.roleKey.trim().toLowerCase(),
      roleName: createRoleForm.roleName.trim(),
    })
    createRoleOpen.value = false
    message.success(t('settings.roles.roleCreated'))
    await loadData()
    selectedRoleKeys.value = [created.data?.roleKey || createRoleForm.roleKey.trim().toLowerCase()]
    activeTab.value = 'functional'
  } catch (e) {
    message.error(e.message || t('config.saveFailed'))
  } finally {
    roleSaving.value = false
  }
}

const openRenameRole = () => {
  renameRoleForm.roleName = activeRole.value?.roleName || ''
  renameRoleOpen.value = true
}

const submitRenameRole = async () => {
  if (!renameRoleForm.roleName.trim() || !activeRole.value) return
  roleSaving.value = true
  try {
    await updateRole(activeRole.value.roleKey, { roleName: renameRoleForm.roleName.trim() })
    renameRoleOpen.value = false
    message.success(t('settings.roles.roleRenamed'))
    await loadData()
  } catch (e) {
    message.error(e.message || t('config.saveFailed'))
  } finally {
    roleSaving.value = false
  }
}

const handleDeleteRole = async () => {
  if (!activeRole.value || activeRole.value.builtIn) return
  roleSaving.value = true
  try {
    await deleteRole(activeRole.value.roleKey)
    message.success(t('settings.roles.roleDeleted'))
    selectedRoleKeys.value = ['user']
    await loadData()
  } catch (e) {
    message.error(e.message || t('config.saveFailed'))
  } finally {
    roleSaving.value = false
  }
}

onMounted(loadData)

onBeforeRouteLeave((_to, _from, next) => {
  if (!isRoleDirty()) {
    next()
    return
  }
  Modal.confirm({
    title: t('settings.roles.unsavedTitle'),
    content: t('settings.roles.unsavedContent'),
    okText: t('settings.roles.discardChanges'),
    cancelText: t('common.cancel'),
    onOk: () => next(),
    onCancel: () => next(false),
  })
})
</script>

<style scoped lang="scss">
.role-hint-alert {
  margin-bottom: 12px;
}

.role-mgmt {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.role-layout {
  display: flex;
  gap: 16px;
  align-items: flex-start;
}

.role-list-card {
  flex: 0 0 220px;
}

.role-config-card {
  flex: 1;
  min-width: 0;
}

.role-list-title {
  font-size: 13px;
  font-weight: 600;
  margin-bottom: 8px;
  padding: 0 8px;
}

.role-menu {
  border-inline-end: none !important;
}

.role-menu-item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
}

.role-header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 8px;

  h3 {
    margin: 0 0 4px;
    font-size: 16px;
    font-weight: 600;
  }

  .role-key {
    font-size: 12px;
    color: var(--text-secondary, #5c5c5c);
  }
}

.flow-hint {
  margin: 0 0 12px;
  color: var(--text-secondary, #5c5c5c);
  font-size: 13px;
}

.role-tabs {
  margin-top: 4px;
}

.tab-desc {
  margin: 0 0 12px;
  color: var(--text-secondary, #5c5c5c);
  font-size: 13px;
  line-height: 1.5;

  &--sub {
    margin-top: 0;
    font-size: 12px;
  }
}

.functional-country-row {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 12px;
}

.members-toolbar {
  display: flex;
  flex-wrap: wrap;
  gap: 12px;
  margin-bottom: 16px;
  align-items: center;
}

.member-search {
  width: 280px;
}

.candidate-table {
  margin-top: 12px;
}

.member-action-muted {
  color: var(--text-secondary, #999);
  font-size: 12px;
}

.perm-list {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.perm-row {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
  padding: 10px 12px;
  border-radius: 8px;
  background: rgba(0, 0, 0, 0.02);
}

.perm-label-block {
  flex: 1;
  min-width: 0;
}

.perm-label {
  display: block;
  font-size: 13px;
}

.perm-hint {
  margin: 4px 0 0;
  font-size: 12px;
  line-height: 1.5;
  color: $text-secondary;
}

.dimension-hint-row {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
  margin-top: 12px;
}

.dimension-block {
  margin-bottom: 16px;

  .dimension-label {
    display: block;
    margin-bottom: 6px;
    font-size: 13px;
    font-weight: 500;
  }
}

.dimension-inline-hint {
  margin: 0 0 8px;
}

.footer-hint {
  margin: 20px 0 0;
  color: var(--text-secondary, #73707f);
  font-size: 13px;
}
</style>
