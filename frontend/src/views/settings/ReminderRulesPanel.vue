<template>
  <div class="reminder-rules-panel">
    <div class="panel-toolbar">
      <a-button type="primary" @click="openCreate">
        <template #icon><PlusOutlined /></template>
        {{ $t('settings.reminders.add') }}
      </a-button>
    </div>

    <a-card class="surface-card" :bordered="false">
      <a-table
        :columns="columns"
        :data-source="rules"
        :loading="loading"
        row-key="id"
        :pagination="false"
      >
        <template #bodyCell="{ column, record }">
          <template v-if="column.key === 'interval'">
            {{ formatIntervalLabel(record.intervalValue, record.intervalUnit) }}
          </template>
          <template v-else-if="column.key === 'scope'">
            <div class="scope-cell">
              <div>{{ formatScopeCountries(record.scopeCountries) }}</div>
              <div class="muted">{{ formatScopeRoles(record.scopeRoles) }}</div>
            </div>
          </template>
          <template v-else-if="column.key === 'enabled'">
            <a-switch
              :checked="record.enabled"
              :loading="togglingId === record.id"
              @change="(v) => toggleEnabled(record, v)"
            />
          </template>
          <template v-else-if="column.key === 'lastRun'">
            <div v-if="record.lastRunAt">
              <div>{{ formatTime(record.lastRunAt) }}</div>
              <div class="muted">
                {{ $t('settings.reminders.lastRunStats', { hit: record.lastHitCount, sent: record.lastSentCount }) }}
              </div>
            </div>
            <span v-else class="muted">—</span>
          </template>
          <template v-else-if="column.key === 'actions'">
            <div class="table-action-cell table-action-cell--links table-action-cell--links-2">
              <span class="table-action-cell__slot">
                <a-button type="link" size="small" @click="openEdit(record)">{{ $t('common.edit') }}</a-button>
              </span>
              <span class="table-action-cell__slot">
                <a-popconfirm :title="$t('settings.reminders.deleteConfirm')" @confirm="removeRule(record.id)">
                  <a-button type="link" size="small" danger>{{ $t('common.delete') }}</a-button>
                </a-popconfirm>
              </span>
            </div>
          </template>
        </template>
      </a-table>
    </a-card>

    <a-modal
      v-model:open="modalOpen"
      :title="editingId ? $t('settings.reminders.editTitle') : $t('settings.reminders.createTitle')"
      width="720px"
      :confirm-loading="saving"
      @ok="handleSave"
    >
      <a-steps :current="step" size="small" class="reminder-steps">
        <a-step :title="$t('settings.reminders.stepWhen')" />
        <a-step :title="$t('settings.reminders.stepWho')" />
        <a-step :title="$t('settings.reminders.stepWhat')" />
      </a-steps>

      <div v-show="step === 0" class="step-body">
        <a-form layout="vertical">
          <a-form-item :label="$t('settings.reminders.ruleName')" required>
            <a-input v-model:value="form.name" />
          </a-form-item>
          <a-form-item :label="$t('settings.reminders.taskStatuses')" required>
            <a-select v-model:value="form.taskStatuses" mode="multiple" :options="statusOptions" />
          </a-form-item>
          <a-form-item :label="$t('settings.reminders.interval')" required>
            <a-space>
              <a-input-number
                v-model:value="form.intervalValue"
                :min="0.1"
                :step="0.1"
                :precision="1"
                style="width: 100px"
              />
              <a-select v-model:value="form.intervalUnit" :options="unitOptions" style="width: 120px" />
            </a-space>
            <div class="hint">{{ $t('settings.reminders.intervalDecimalHint') }}</div>
            <div class="hint">{{ $t('settings.reminders.lagHint') }}</div>
          </a-form-item>
          <a-form-item :label="$t('settings.reminders.scopeCountries')">
            <a-select
              v-model:value="form.scopeCountries"
              mode="multiple"
              allow-clear
              :placeholder="$t('settings.reminders.scopeAll')"
              :options="countryOptions"
              :loading="countriesLoading"
            />
            <div class="hint">{{ $t('settings.reminders.scopeCountriesHint') }}</div>
          </a-form-item>
          <a-form-item :label="$t('settings.reminders.scopeRoles')">
            <a-select
              v-model:value="form.scopeRoles"
              mode="multiple"
              allow-clear
              :placeholder="$t('settings.reminders.scopeAll')"
              :options="roleOptions"
              :loading="rolesLoading"
            />
            <div class="hint">{{ $t('settings.reminders.scopeRolesHint') }}</div>
          </a-form-item>
        </a-form>
      </div>

      <div v-show="step === 1" class="step-body">
        <a-form layout="vertical">
          <a-form-item :label="$t('settings.reminders.recipients')" required>
            <a-select
              v-model:value="form.recipientUserIds"
              mode="multiple"
              show-search
              option-filter-prop="label"
              :options="userOptions"
              :loading="usersLoading"
            />
          </a-form-item>
          <a-form-item>
            <a-switch v-model:checked="form.includeTaskCreator" />
            <span class="switch-label">{{ $t('settings.reminders.includeCreator') }}</span>
            <div class="hint">{{ $t('settings.reminders.includeCreatorHint') }}</div>
          </a-form-item>
        </a-form>
      </div>

      <div v-show="step === 2" class="step-body">
        <a-form layout="vertical">
          <a-form-item :label="$t('settings.reminders.messageTemplateOperator')" required>
            <p class="hint hint--inline">{{ $t('settings.reminders.messageTemplateOperatorHint') }}</p>
            <a-textarea v-model:value="form.messageTemplate" :rows="6" />
            <a-space wrap style="margin-top: 8px">
              <a-button size="small" @click="insertVar('pendingCount', 'operator')">{pendingCount}</a-button>
              <a-button size="small" @click="insertVar('threshold', 'operator')">{threshold}</a-button>
              <a-button size="small" @click="insertVar('latestTaskId', 'operator')">{latestTaskId}</a-button>
              <a-button size="small" @click="resetOperatorTemplate">{{ $t('settings.reminders.resetTemplate') }}</a-button>
            </a-space>
          </a-form-item>
          <a-form-item :label="$t('settings.reminders.preview')">
            <pre class="preview-box">{{ previewOperatorText }}</pre>
          </a-form-item>

          <a-divider />

          <a-form-item :label="$t('settings.reminders.messageTemplateSupervisor')">
            <p class="hint hint--inline">{{ $t('settings.reminders.messageTemplateSupervisorHint') }}</p>
            <a-textarea v-model:value="form.messageTemplateSupervisor" :rows="6" />
            <a-space wrap style="margin-top: 8px">
              <a-button size="small" @click="insertVar('pendingCount', 'supervisor')">{pendingCount}</a-button>
              <a-button size="small" @click="insertVar('taskCreatorNames', 'supervisor')">{taskCreatorNames}</a-button>
              <a-button size="small" @click="insertVar('recipientName', 'supervisor')">{recipientName}</a-button>
              <a-button size="small" @click="resetSupervisorTemplate">{{ $t('settings.reminders.resetSupervisorTemplate') }}</a-button>
            </a-space>
          </a-form-item>
          <a-form-item :label="$t('settings.reminders.previewSupervisor')">
            <pre class="preview-box">{{ previewSupervisorText }}</pre>
          </a-form-item>

          <a-form-item>
            <a-switch v-model:checked="form.enabled" />
            <span class="switch-label">{{ $t('settings.reminders.enabled') }}</span>
          </a-form-item>
        </a-form>
      </div>

      <template #footer>
        <a-button v-if="step > 0" @click="step -= 1">{{ $t('common.back') }}</a-button>
        <a-button v-if="step < 2" type="primary" @click="nextStep">{{ $t('settings.reminders.next') }}</a-button>
        <a-button v-if="step === 2" type="primary" :loading="saving" @click="handleSave">{{ $t('common.save') }}</a-button>
      </template>
    </a-modal>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { storeToRefs } from 'pinia'
import { message } from 'ant-design-vue'
import { useI18n } from 'vue-i18n'
import { PlusOutlined } from '@ant-design/icons-vue'
import { listUsers } from '@/api/users'
import { listRoles } from '@/api/roles'
import { useCountryStore } from '@/stores/country'
import {
  listReminderRules,
  createReminderRule,
  updateReminderRule,
  deleteReminderRule,
  setReminderRuleEnabled,
  getDefaultReminderTemplate,
} from '@/api/reminder'
import { formatIntervalValue, isValidIntervalValue, normalizeIntervalValue } from '@/utils/reminderInterval'

const { t } = useI18n()
const countryStore = useCountryStore()
const { selectOptions: countrySelectOptions } = storeToRefs(countryStore)

const loading = ref(false)
const saving = ref(false)
const countriesLoading = ref(false)
const rolesLoading = ref(false)
const rules = ref([])
const modalOpen = ref(false)
const editingId = ref(null)
const step = ref(0)
const togglingId = ref(null)
const usersLoading = ref(false)
const userOptions = ref([])
const roleOptions = ref([])
const defaultTemplate = ref('')
const defaultSupervisorTemplate = ref('')

const countryOptions = computed(() => countrySelectOptions.value || [])

const form = reactive({
  name: '',
  taskStatuses: ['processed'],
  scopeCountries: [],
  scopeRoles: [],
  intervalValue: 1,
  intervalUnit: 'day',
  recipientUserIds: [],
  includeTaskCreator: true,
  messageTemplate: '',
  messageTemplateSupervisor: '',
  enabled: true,
})

const statusOptions = [
  { value: 'processed', label: '待核对 (processed)' },
  { value: 'processing', label: '识别中 (processing)' },
  { value: 'failed', label: '失败 (failed)' },
]

const unitOptions = computed(() => [
  { value: 'minute', label: t('settings.reminders.unitMinute') },
  { value: 'hour', label: t('settings.reminders.unitHour') },
  { value: 'day', label: t('settings.reminders.unitDay') },
  { value: 'week', label: t('settings.reminders.unitWeek') },
])

const columns = computed(() => [
  { title: t('settings.reminders.ruleName'), dataIndex: 'name', key: 'name' },
  { title: t('settings.reminders.scope'), key: 'scope', width: 160 },
  { title: t('settings.reminders.interval'), key: 'interval', width: 120 },
  { title: t('settings.reminders.recipientCount'), dataIndex: 'recipientCount', key: 'recipientCount', width: 100 },
  { title: t('settings.reminders.enabled'), key: 'enabled', width: 90 },
  { title: t('settings.reminders.lastRun'), key: 'lastRun', width: 180 },
  { title: t('settings.reminders.actions'), key: 'actions', width: 140, align: 'center' },
])

const renderTemplatePreview = (template, options = {}) => {
  const {
    recipientName = '张三',
    taskCreatorName = '李四',
    taskCreatorNames = '李四、王五',
  } = options
  return (template || '')
    .replace(/\{pendingCount\}/g, '3')
    .replace(/\{threshold\}/g, formatIntervalLabel(form.intervalValue, form.intervalUnit))
    .replace(/\{latestTaskId\}/g, 'T20260101001')
    .replace(/\{latestTaskTime\}/g, '2026-01-01 10:00')
    .replace(/\{recipientName\}/g, recipientName)
    .replace(/\{taskCreatorName\}/g, taskCreatorName)
    .replace(/\{taskCreatorNames\}/g, taskCreatorNames)
    .replace(/\{taskStatus\}/g, '待核对')
}

const previewOperatorText = computed(() => renderTemplatePreview(form.messageTemplate))

const previewSupervisorText = computed(() => renderTemplatePreview(
  form.messageTemplateSupervisor || defaultSupervisorTemplate.value,
  { recipientName: '王主管', taskCreatorNames: '李四、王五' },
))

const unitLabel = (unit) => {
  const map = {
    minute: t('settings.reminders.unitMinute'),
    hour: t('settings.reminders.unitHour'),
    day: t('settings.reminders.unitDay'),
    week: t('settings.reminders.unitWeek'),
  }
  return map[unit] || unit
}

const formatIntervalLabel = (value, unit) => `${formatIntervalValue(value)} ${unitLabel(unit)}`

const formatTime = (v) => String(v || '').replace('T', ' ').slice(0, 16)

const countryLabelMap = computed(() => {
  const map = new Map()
  for (const opt of countryOptions.value) {
    map.set(opt.value, opt.label)
  }
  return map
})

const roleLabelMap = computed(() => {
  const map = new Map()
  for (const opt of roleOptions.value) {
    map.set(opt.value, opt.label)
  }
  return map
})

const formatScopeCountries = (codes) => {
  if (!codes?.length) return `${t('settings.reminders.scopeCountriesShort')}: ${t('settings.reminders.scopeAll')}`
  const labels = codes.map((c) => countryLabelMap.value.get(c) || c)
  return `${t('settings.reminders.scopeCountriesShort')}: ${labels.join('、')}`
}

const formatScopeRoles = (roles) => {
  if (!roles?.length) return `${t('settings.reminders.scopeRolesShort')}: ${t('settings.reminders.scopeAll')}`
  const labels = roles.map((r) => roleLabelMap.value.get(r) || r)
  return `${t('settings.reminders.scopeRolesShort')}: ${labels.join('、')}`
}

const loadCountries = async () => {
  countriesLoading.value = true
  try {
    await countryStore.hydrate()
  } finally {
    countriesLoading.value = false
  }
}

const loadRoles = async () => {
  rolesLoading.value = true
  try {
    const res = await listRoles()
    const roles = res.data || []
    roleOptions.value = roles.map((r) => ({
      value: r.roleKey,
      label: r.roleName || r.roleKey,
    }))
  } finally {
    rolesLoading.value = false
  }
}

const fetchRules = async () => {
  loading.value = true
  try {
    const res = await listReminderRules()
    rules.value = res.data || []
  } finally {
    loading.value = false
  }
}

const loadUsers = async () => {
  usersLoading.value = true
  try {
    const res = await listUsers({ current: 1, size: 500 })
    const records = res.data?.records || []
    userOptions.value = records
      .filter((u) => u.status === 'active')
      .map((u) => ({
        value: u.id,
        label: `${u.realName || u.username}${u.feishuUserId ? ' · 飞书+站内' : ' · 仅站内'}`,
      }))
  } finally {
    usersLoading.value = false
  }
}

const loadDefaultTemplate = async () => {
  try {
    const res = await getDefaultReminderTemplate()
    defaultTemplate.value = res.data?.template || ''
    defaultSupervisorTemplate.value = res.data?.supervisorTemplate || ''
  } catch (e) {
    console.error(e)
  }
}

const resetForm = () => {
  form.name = t('settings.reminders.defaultName')
  form.taskStatuses = ['processed']
  form.scopeCountries = []
  form.scopeRoles = []
  form.intervalValue = 1
  form.intervalUnit = 'day'
  form.recipientUserIds = []
  form.includeTaskCreator = true
  form.messageTemplate = defaultTemplate.value
  form.messageTemplateSupervisor = defaultSupervisorTemplate.value
  form.enabled = true
  step.value = 0
}

const openCreate = () => {
  editingId.value = null
  resetForm()
  modalOpen.value = true
}

const openEdit = (record) => {
  editingId.value = record.id
  form.name = record.name
  form.taskStatuses = [...(record.taskStatuses || ['processed'])]
  form.scopeCountries = [...(record.scopeCountries || [])]
  form.scopeRoles = [...(record.scopeRoles || [])]
  form.intervalValue = record.intervalValue
  form.intervalUnit = record.intervalUnit
  form.recipientUserIds = [...(record.recipientUserIds || [])]
  form.includeTaskCreator = record.includeTaskCreator !== false
  form.messageTemplate = record.messageTemplate
  form.messageTemplateSupervisor = record.messageTemplateSupervisor || defaultSupervisorTemplate.value
  form.enabled = record.enabled !== false
  step.value = 0
  modalOpen.value = true
}

const nextStep = () => {
  if (step.value === 0) {
    if (!form.name?.trim() || !form.taskStatuses?.length || !isValidIntervalValue(form.intervalValue)) {
      message.warning(t('settings.reminders.validationRequired'))
      return
    }
  }
  if (step.value === 1 && (!form.recipientUserIds || form.recipientUserIds.length === 0)) {
    message.warning(t('settings.reminders.recipientsRequired'))
    return
  }
  step.value += 1
}

const handleSave = async () => {
  if (!form.messageTemplate?.trim()) {
    message.warning(t('settings.reminders.validationRequired'))
    return
  }
  saving.value = true
  try {
    const payload = {
      name: form.name.trim(),
      taskStatuses: form.taskStatuses,
      scopeCountries: form.scopeCountries?.length ? form.scopeCountries : [],
      scopeRoles: form.scopeRoles?.length ? form.scopeRoles : [],
      intervalValue: normalizeIntervalValue(form.intervalValue),
      intervalUnit: form.intervalUnit,
      recipientUserIds: form.recipientUserIds,
      includeTaskCreator: form.includeTaskCreator,
      messageTemplate: form.messageTemplate,
      messageTemplateSupervisor: form.messageTemplateSupervisor?.trim() || null,
      enabled: form.enabled,
    }
    if (editingId.value) {
      await updateReminderRule(editingId.value, payload)
      message.success(t('settings.reminders.updated'))
    } else {
      await createReminderRule(payload)
      message.success(t('settings.reminders.created'))
    }
    modalOpen.value = false
    await fetchRules()
  } catch (e) {
    console.error(e)
  } finally {
    saving.value = false
  }
}

const toggleEnabled = async (record, enabled) => {
  togglingId.value = record.id
  try {
    await setReminderRuleEnabled(record.id, enabled)
    record.enabled = enabled
  } finally {
    togglingId.value = null
  }
}

const removeRule = async (id) => {
  await deleteReminderRule(id)
  message.success(t('settings.reminders.deleted'))
  await fetchRules()
}

const insertVar = (name, target = 'operator') => {
  const key = target === 'supervisor' ? 'messageTemplateSupervisor' : 'messageTemplate'
  form[key] = (form[key] || '') + `{${name}}`
}

const resetOperatorTemplate = () => {
  form.messageTemplate = defaultTemplate.value
}

const resetSupervisorTemplate = () => {
  form.messageTemplateSupervisor = defaultSupervisorTemplate.value
}

onMounted(async () => {
  await Promise.all([fetchRules(), loadUsers(), loadDefaultTemplate(), loadCountries(), loadRoles()])
})
</script>

<style scoped lang="scss">
.panel-toolbar {
  display: flex;
  justify-content: flex-end;
  margin-bottom: 12px;
}

.reminder-steps {
  margin-bottom: 20px;
}

.step-body {
  min-height: 280px;
}

.hint {
  margin-top: 6px;
  font-size: 12px;
  color: #8c8c8c;
}

.switch-label {
  margin-left: 8px;
}

.hint--inline {
  margin-bottom: 8px;
}

.preview-box {
  background: #fafafa;
  border: 1px solid #f0f0f0;
  border-radius: 8px;
  padding: 12px;
  white-space: pre-wrap;
  font-size: 13px;
  margin: 0;
}

.muted {
  color: #8c8c8c;
  font-size: 12px;
}

.scope-cell {
  font-size: 12px;
  line-height: 1.5;
}
</style>
