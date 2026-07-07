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
                <div class="functional-country-row">
                  <label class="dimension-label">{{ $t('settings.roles.functionalCountryLabel') }}</label>
                  <a-select
                    v-model:value="functionalCountry"
                    :options="functionalCountryOptions"
                    style="min-width: 220px"
                  />
                </div>
                <p v-if="functionalCountry !== '__default__'" class="tab-desc tab-desc--sub">
                  {{ $t('settings.roles.functionalCountryHint') }}
                </p>

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
              <template v-if="currentScope && currentScope.editable">
                <p class="tab-desc">{{ $t('settings.roles.dataScopeDesc') }}</p>
                <a-radio-group v-model:value="currentScope.scopeType">
                  <a-radio value="restricted">{{ $t('settings.roles.scopeRestricted') }}</a-radio>
                  <a-radio value="all">{{ $t('settings.roles.scopeAll') }}</a-radio>
                </a-radio-group>

                <template v-if="currentScope.scopeType === 'restricted'">
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
                      v-model="currentScope.rules.owner_user"
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
                      v-model="currentScope.rules.warehouse"
                      :options="warehouseOptions"
                      :max-height="220"
                    />
                  </div>

                  <div class="dimension-block">
                    <label class="dimension-label">{{ $t('settings.roles.dimAgency') }}</label>
                    <DimensionCheckboxPicker
                      v-model="currentScope.rules.agency"
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
          </a-tabs>

          <p class="footer-hint">{{ $t('settings.roles.assignHint') }}</p>
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
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { message } from 'ant-design-vue'
import { PlusOutlined } from '@ant-design/icons-vue'
import PageShell from '@/components/PageShell.vue'
import DimensionCheckboxPicker from '@/components/DimensionCheckboxPicker.vue'
import { getRoleDataScopes, updateRoleDataScope, getDataScopeDimensionOptions } from '@/api/dataScope'
import { getRolePermissions, updateRolePermissions } from '@/api/permissions'
import { listRoles, createRole, updateRole, deleteRole } from '@/api/roles'
import { useCountryStore } from '@/stores/country'

const { t } = useI18n()
const countryStore = useCountryStore()
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
]

const systemRoles = ref([])
const roleScopes = reactive({})
const rolePermissions = reactive({})
const roleCountryPermissions = reactive({})
const selectedRoleKeys = ref(['user'])
const activeTab = ref('functional')
const functionalCountry = ref('__default__')

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

const activeRoleKey = computed(() => selectedRoleKeys.value[0] || 'user')
const activeRole = computed(() => systemRoles.value.find((r) => r.roleKey === activeRoleKey.value) || null)
const currentScope = computed(() => roleScopes[activeRoleKey.value] || null)
const isAdminRole = computed(() => activeRoleKey.value === 'admin')
const canSaveCurrentRole = computed(() => !isAdminRole.value && !!currentScope.value?.editable)

const functionalCountryOptions = computed(() => {
  const options = [{ value: '__default__', label: t('settings.roles.functionalCountryDefault') }]
  ;(countryStore.selectOptions || []).forEach((item) => {
    const code = item.value || item.code
    if (!code || code === 'default') return
    options.push({ value: code, label: item.label || code })
  })
  return options
})

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
  Object.keys(roleCountryPermissions).forEach((key) => delete roleCountryPermissions[key])
  const roles = data?.roles || data || {}
  const byCountry = data?.byCountry || {}
  systemRoles.value.forEach((role) => {
    const roleKey = role.roleKey
    const source = roles?.[roleKey] || {}
    rolePermissions[roleKey] = {}
    PERM_DEFS.forEach((def) => {
      rolePermissions[roleKey][def.key] = !!source[def.key]
    })
    const countryMap = byCountry?.[roleKey] || {}
    roleCountryPermissions[roleKey] = {}
    Object.entries(countryMap).forEach(([countryCode, perms]) => {
      if (!countryCode || countryCode === 'default') return
      roleCountryPermissions[roleKey][countryCode] = {}
      PERM_DEFS.forEach((def) => {
        roleCountryPermissions[roleKey][countryCode][def.key] = perms?.[def.key] !== undefined
          ? !!perms[def.key]
          : !!rolePermissions[roleKey][def.key]
      })
    })
  })
}

const ensureCountryPerms = (roleKey, countryCode) => {
  if (!roleCountryPermissions[roleKey]) {
    roleCountryPermissions[roleKey] = {}
  }
  if (!roleCountryPermissions[roleKey][countryCode]) {
    roleCountryPermissions[roleKey][countryCode] = {}
    PERM_DEFS.forEach((def) => {
      roleCountryPermissions[roleKey][countryCode][def.key] = !!rolePermissions[roleKey]?.[def.key]
    })
  }
}

const getFunctionalPerm = (key) => {
  const roleKey = activeRoleKey.value
  if (functionalCountry.value === '__default__') {
    return !!rolePermissions[roleKey]?.[key]
  }
  ensureCountryPerms(roleKey, functionalCountry.value)
  const countryVal = roleCountryPermissions[roleKey][functionalCountry.value][key]
  return countryVal !== undefined ? !!countryVal : !!rolePermissions[roleKey]?.[key]
}

const setFunctionalPerm = (key, checked) => {
  const roleKey = activeRoleKey.value
  if (functionalCountry.value === '__default__') {
    rolePermissions[roleKey][key] = checked
    return
  }
  ensureCountryPerms(roleKey, functionalCountry.value)
  roleCountryPermissions[roleKey][functionalCountry.value][key] = checked
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
  } catch (e) {
    message.error(e.message || t('common.error'))
  } finally {
    loading.value = false
  }
}

const onRoleMenuClick = ({ key }) => {
  selectedRoleKeys.value = [key]
}

const handleSave = async () => {
  const roleKey = activeRoleKey.value
  const scope = roleScopes[roleKey]
  const perms = rolePermissions[roleKey]
  if (!scope?.editable || !perms) return

  saving.value = true
  try {
    await Promise.all([
      updateRolePermissions({
        roles: { [roleKey]: { ...perms } },
        byCountry: roleCountryPermissions[roleKey]
          ? { [roleKey]: { ...roleCountryPermissions[roleKey] } }
          : {},
      }),
      updateRoleDataScope(roleKey, {
        scopeType: scope.scopeType,
        rules: scope.scopeType === 'restricted' ? { ...scope.rules } : emptyRules(),
      }),
    ])
    message.success(t('settings.roles.saved'))
    await loadData()
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
</script>

<style scoped lang="scss">
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
