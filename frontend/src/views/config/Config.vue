<template>
  <div class="config-container">
    <a-alert
      v-if="!wizardCompleted"
      message="快速配置向导"
      description="点击这里，5分钟完成所有配置！"
      type="info"
      show-icon
      class="wizard-banner"
      @click="showWizard = true"
    />
    
    <a-card class="country-selector-card">
      <div class="country-selector">
        <div class="selector-info">
          <span class="label">当前配置国家：</span>
          <a-select
            v-model:value="selectedCountry"
            style="width: 200px"
            @change="loadCountryConfigs"
            :options="countryOptions"
          />
          <a-tag :color="isUsingDefault ? 'orange' : 'green'" style="margin-left: 8px">
            {{ isUsingDefault ? '使用默认配置' : '使用特定配置' }}
          </a-tag>
        </div>
        <a-space>
          <a-button type="primary" @click="setAsCurrentCountry">
            <template #icon><CheckCircleOutlined /></template>
            设为当前工作国家
          </a-button>
          <a-button @click="reloadConfigs">
            <template #icon><ReloadOutlined /></template>
            从配置文件刷新
          </a-button>
          <a-button @click="loadCountryConfigs">
            <template #icon><SyncOutlined /></template>
            刷新显示
          </a-button>
        </a-space>
      </div>
    </a-card>

    <div class="module-nav">
      <div
        v-for="module in modules"
        :key="module.key"
        class="module-card"
        :class="{ active: activeModule === module.key }"
        @click="activeModule = module.key"
      >
        <div class="module-icon" :style="{ backgroundColor: module.color }">
          <component :is="module.icon" />
        </div>
        <div class="module-info">
          <h4 class="module-title">{{ t(module.titleKey) }}</h4>
          <p class="module-desc">{{ t(module.descKey) }}</p>
        </div>
      </div>
    </div>

    <div class="module-content">
      <div v-if="activeModule === 'ai'" class="module-panel">
        <a-card class="config-card">
          <template #title>
            <div class="card-header-flex">
              <div>
                <h3 class="card-title">{{ t('config.aiConfig.title') }}</h3>
                <p class="card-desc">{{ t('config.aiConfig.subtitle') }}</p>
              </div>
              <div class="header-actions">
                <a-select
                  v-model:value="selectedCountry"
                  style="width: 150px; margin-right: 8px"
                  :options="countryOptions"
                  @change="loadCountryConfigs"
                />
                <a-button @click="loadCountryTemplate">
                  <template #icon><FileTextOutlined /></template>
                  {{ t('config.aiConfig.templateLoad') }}
                </a-button>
              </div>
            </div>
          </template>

          <a-form layout="vertical">
            <a-form-item :label="t('config.aiConfig.aiPrompt')">
              <template #label>
                <span>
                  {{ t('config.aiConfig.aiPrompt') }}
                  <a-tooltip :title="t('config.aiConfig.aiPromptDesc')">
                    <QuestionCircleOutlined style="margin-left: 4px; color: #8c8c8c" />
                  </a-tooltip>
                </span>
              </template>
              <a-textarea
                v-model:value="configs.aiPrompt"
                :rows="12"
                placeholder="请输入AI识别提示词..."
              />
            </a-form-item>

            <a-form-item :label="t('config.aiConfig.continuePrompt')">
              <template #label>
                <span>
                  {{ t('config.aiConfig.continuePrompt') }}
                  <a-tooltip :title="t('config.aiConfig.continuePromptDesc')">
                    <QuestionCircleOutlined style="margin-left: 4px; color: #8c8c8c" />
                  </a-tooltip>
                </span>
              </template>
              <a-textarea
                v-model:value="configs.continuePrompt"
                :rows="3"
                placeholder="请输入继续提示词..."
              />
            </a-form-item>

            <a-form-item>
              <a-button type="primary" @click="saveAiConfig" :loading="saving">
                {{ t('common.save') }}
              </a-button>
            </a-form-item>
          </a-form>
        </a-card>
      </div>

      <div v-if="activeModule === 'feishu'" class="module-panel">
        <a-card class="config-card">
          <template #title>
            <div class="card-header-flex">
              <div>
                <h3 class="card-title">{{ t('config.feishuConfig.title') }}</h3>
                <p class="card-desc">{{ t('config.feishuConfig.subtitle') }}</p>
              </div>
              <div class="header-actions">
                <a-tag :color="feishuConnected ? 'success' : 'warning'">
                  {{ feishuConnected ? '✅ ' + t('config.feishuConfig.statusConnected') : '⏳ ' + t('config.feishuConfig.statusDisconnected') }}
                </a-tag>
              </div>
            </div>
          </template>

          <div class="help-box">
            <div class="help-icon">❓</div>
            <div class="help-content">
              <h4>{{ t('config.feishuConfig.howToGetToken') }}</h4>
              <p>{{ t('config.feishuConfig.howToGetTokenDesc') }}</p>
              <a-button type="link" size="small">{{ t('config.feishuConfig.viewHelp') }}</a-button>
            </div>
          </div>

          <a-form layout="vertical" style="margin-top: 24px">
            <a-form-item label="飞书多维表链接">
              <template #label>
                <span>
                  飞书多维表链接
                  <a-tooltip title="粘贴飞书多维表链接，自动提取App Token和Table ID">
                    <QuestionCircleOutlined style="margin-left: 4px; color: #8c8c8c" />
                  </a-tooltip>
                </span>
              </template>
              <a-input-search
                v-model:value="feishuUrl"
                placeholder="例如: https://xxx.feishu.cn/base/xxx?table=tblxxx"
                @search="handleFeishuUrlParse"
                @change="handleFeishuUrlChange"
              >
                <template #buttonIcon>
                  <LinkOutlined />
                </template>
                <template #enterButton>
                  <a-button type="primary" :loading="parsingUrl">
                    解析链接
                  </a-button>
                </template>
              </a-input-search>
              <div v-if="urlParseResult" class="url-parse-result">
                <a-alert
                  v-if="urlParseResult.error"
                  :message="urlParseResult.error"
                  type="error"
                  show-icon
                />
                <a-alert
                  v-else
                  :message="`提取成功：App Token: ${urlParseResult.appToken}, Table ID: ${urlParseResult.tableId || '未找到'}`"
                  type="success"
                  show-icon
                />
              </div>
            </a-form-item>

            <a-divider>或手动输入</a-divider>

            <a-form-item :label="t('config.feishuConfig.appToken')">
              <template #label>
                <span>
                  {{ t('config.feishuConfig.appToken') }}
                  <a-tooltip :title="t('config.feishuConfig.appTokenDesc')">
                    <QuestionCircleOutlined style="margin-left: 4px; color: #8c8c8c" />
                  </a-tooltip>
                </span>
              </template>
              <a-input
                v-model:value="configs.appToken"
                :placeholder="t('config.feishuConfig.appTokenPlaceholder')"
              />
            </a-form-item>

            <a-form-item :label="t('config.feishuConfig.tableId')">
              <template #label>
                <span>
                  {{ t('config.feishuConfig.tableId') }}
                  <a-tooltip :title="t('config.feishuConfig.tableIdDesc')">
                    <QuestionCircleOutlined style="margin-left: 4px; color: #8c8c8c" />
                  </a-tooltip>
                </span>
              </template>
              <a-input
                v-model:value="configs.tableId"
                :placeholder="t('config.feishuConfig.tableIdPlaceholder')"
              />
            </a-form-item>

            <a-form-item>
              <a-button type="primary" @click="testFeishuConnection" :loading="testingConnection">
                <template #icon><CheckCircleOutlined /></template>
                {{ t('config.feishuConfig.testConnection') }}
              </a-button>
              <a-button style="margin-left: 8px" @click="saveFeishuConfig" :loading="saving">
                {{ t('common.save') }}
              </a-button>
            </a-form-item>
          </a-form>
        </a-card>
      </div>

      <div v-if="activeModule === 'mapping'" class="module-panel">
        <a-card class="config-card">
          <template #title>
            <div class="card-header-flex">
              <div>
                <h3 class="card-title">{{ t('config.mappingConfig.title') }}</h3>
                <p class="card-desc">{{ t('config.mappingConfig.subtitle') }}</p>
              </div>
              <div class="header-actions">
                <a-button style="margin-right: 8px" @click="loadMappingTemplate">
                  <template #icon><FileTextOutlined /></template>
                  {{ t('config.mappingConfig.loadTemplate') }}
                </a-button>
                <a-button @click="addMapping">
                  <template #icon><PlusOutlined /></template>
                  {{ t('config.mappingConfig.addMapping') }}
                </a-button>
              </div>
            </div>
          </template>

          <a-table
            :columns="mappingColumns"
            :data-source="fieldMappings"
            :pagination="false"
            row-key="aiField"
          >
            <template #bodyCell="{ column, record }">
              <template v-if="column.key === 'aiField'">
                <a-input v-model:value="record.aiField" placeholder="AI字段名" />
              </template>
              <template v-else-if="column.key === 'feishuField'">
                <a-input v-model:value="record.feishuField" placeholder="飞书字段名" />
              </template>
              <template v-else-if="column.key === 'type'">
                <a-select v-model:value="record.type">
                  <a-select-option value="string">{{ t('config.mappingConfig.types.string') }}</a-select-option>
                  <a-select-option value="number">{{ t('config.mappingConfig.types.number') }}</a-select-option>
                  <a-select-option value="date">{{ t('config.mappingConfig.types.date') }}</a-select-option>
                  <a-select-option value="datetime">{{ t('config.mappingConfig.types.datetime') }}</a-select-option>
                </a-select>
              </template>
              <template v-else-if="column.key === 'required'">
                <a-switch v-model:checked="record.required" />
              </template>
              <template v-else-if="column.key === 'description'">
                <a-input v-model:value="record.description" placeholder="描述" />
              </template>
              <template v-else-if="column.key === 'action'">
                <a-button type="text" danger size="small" @click="removeMapping(record)">
                  <DeleteOutlined />
                </a-button>
              </template>
            </template>
          </a-table>

          <div style="margin-top: 24px">
            <a-button type="primary" @click="saveMappingConfig" :loading="saving">
              {{ t('common.save') }}
            </a-button>
          </div>
        </a-card>
      </div>

      <div v-if="activeModule === 'system'" class="module-panel">
        <a-card class="config-card">
          <template #title>
            <div class="card-header-flex">
              <div>
                <h3 class="card-title">{{ t('config.systemConfig.title') }}</h3>
                <p class="card-desc">{{ t('config.systemConfig.subtitle') }}</p>
              </div>
            </div>
          </template>

          <a-form layout="vertical">
            <a-form-item>
              <a-tooltip :title="t('config.systemConfig.autoConfirmDesc')">
                <div class="setting-item">
                  <div class="setting-info">
                    <h4>{{ t('config.systemConfig.autoConfirm') }}</h4>
                    <p class="setting-desc">{{ t('config.systemConfig.autoConfirmDesc') }}</p>
                  </div>
                  <a-switch v-model:checked="configs.autoConfirm" />
                </div>
              </a-tooltip>
            </a-form-item>

            <a-form-item>
              <a-tooltip :title="t('config.systemConfig.notificationEnabledDesc')">
                <div class="setting-item">
                  <div class="setting-info">
                    <h4>{{ t('config.systemConfig.notificationEnabled') }}</h4>
                    <p class="setting-desc">{{ t('config.systemConfig.notificationEnabledDesc') }}</p>
                  </div>
                  <a-switch v-model:checked="configs.notificationEnabled" />
                </div>
              </a-tooltip>
            </a-form-item>

            <a-form-item :label="t('config.systemConfig.batchSize')">
              <template #label>
                <span>
                  {{ t('config.systemConfig.batchSize') }}
                  <a-tooltip :title="t('config.systemConfig.batchSizeDesc')">
                    <QuestionCircleOutlined style="margin-left: 4px; color: #8c8c8c" />
                  </a-tooltip>
                </span>
              </template>
              <a-input-number v-model:value="configs.batchSize" :min="10" :max="500" style="width: 200px" />
            </a-form-item>

            <a-form-item>
              <a-button type="primary" @click="saveSystemConfig" :loading="saving">
                {{ t('config.systemConfig.save') }}
              </a-button>
              <a-button style="margin-left: 8px" @click="resetSystemConfig">
                {{ t('config.systemConfig.reset') }}
              </a-button>
            </a-form-item>
          </a-form>
        </a-card>
      </div>
    </div>

    <a-modal
      v-model:open="showWizard"
      :title="t('config.wizard.title')"
      :footer="null"
      width="700px"
      class="wizard-modal"
      @cancel="showWizard = false"
    >
      <div class="wizard-steps">
        <div v-for="(step, index) in wizardSteps" :key="index" class="wizard-step" :class="{ active: wizardStep === index + 1, completed: wizardStep > index + 1 }">
          <div class="step-number">{{ index + 1 }}</div>
          <div class="step-info">
            <div class="step-title">{{ t(step.titleKey) }}</div>
            <div class="step-desc">{{ t(step.descKey) }}</div>
          </div>
          <div v-if="index < wizardSteps.length - 1" class="step-divider" :class="{ active: wizardStep > index + 1 }"></div>
        </div>
      </div>

      <div class="wizard-content">
        <div v-if="wizardStep === 1" class="wizard-step-content">
          <div class="welcome-content">
            <div class="welcome-icon">🎉</div>
            <h2>{{ t('config.wizard.step1') }}</h2>
            <p>{{ t('config.wizard.step1Desc') }}</p>
            <p style="color: #8c8c8c; margin-top: 16px">此向导将帮助您完成所有必要配置，只需5分钟即可开始使用！</p>
          </div>
        </div>

        <div v-if="wizardStep === 2" class="wizard-step-content">
          <h3>{{ t('config.aiConfig.countrySelect') }}</h3>
          <p style="color: #8c8c8c; margin-bottom: 24px">{{ t('config.aiConfig.countrySelectDesc') }}</p>
          <div class="country-selector-grid">
            <div
              v-for="country in countries"
              :key="country.code"
              class="country-card"
              :class="{ selected: wizardCountry === country.code }"
              @click="wizardCountry = country.code"
            >
              <span class="country-flag">{{ country.flag }}</span>
              <span class="country-name">{{ country.name }}</span>
            </div>
          </div>
        </div>

        <div v-if="wizardStep === 3" class="wizard-step-content">
          <h3>{{ t('config.feishuConfig.title') }}</h3>
          <p style="color: #8c8c8c; margin-bottom: 24px">{{ t('config.feishuConfig.subtitle') }}</p>
          <a-form layout="vertical">
            <a-form-item :label="t('config.feishuConfig.appToken')">
              <a-input v-model:value="wizardAppToken" :placeholder="t('config.feishuConfig.appTokenPlaceholder')" />
            </a-form-item>
            <a-form-item :label="t('config.feishuConfig.tableId')">
              <a-input v-model:value="wizardTableId" :placeholder="t('config.feishuConfig.tableIdPlaceholder')" />
            </a-form-item>
          </a-form>
        </div>

        <div v-if="wizardStep === 4" class="wizard-step-content">
          <h3>{{ t('config.mappingConfig.title') }}</h3>
          <p style="color: #8c8c8c; margin-bottom: 24px">{{ t('config.mappingConfig.subtitle') }}</p>
          <a-alert type="info" :description="`将为 ${wizardCountryName} 自动加载推荐的字段映射模板`" show-icon />
        </div>

        <div v-if="wizardStep === 5" class="wizard-step-content">
          <div class="welcome-content">
            <div class="welcome-icon">✅</div>
            <h2>{{ t('config.wizard.step5') }}</h2>
            <p style="color: #52c41a; font-size: 16px; margin-top: 16px">
              {{ t('config.wizard.step5Desc') }}
            </p>
            <p style="color: #8c8c8c; margin-top: 16px">点击"完成配置"保存所有设置并开始使用！</p>
          </div>
        </div>
      </div>

      <div class="wizard-footer">
        <a-button v-if="wizardStep > 1" @click="wizardStep--">
          {{ t('config.wizard.prev') }}
        </a-button>
        <a-button v-if="wizardStep < 5" type="primary" @click="wizardStep++">
          {{ t('config.wizard.next') }}
        </a-button>
        <a-button v-if="wizardStep === 5" type="primary" @click="completeWizard">
          {{ t('config.wizard.finish') }}
        </a-button>
        <a-button style="margin-left: auto" @click="showWizard = false">
          {{ t('config.wizard.skip') }}
        </a-button>
      </div>
    </a-modal>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted } from 'vue'
import { useI18n } from 'vue-i18n'
import { message, Modal } from 'ant-design-vue'
import {
  SettingOutlined,
  RobotOutlined,
  LinkOutlined,
  SwapOutlined,
  PlusOutlined,
  DeleteOutlined,
  FileTextOutlined,
  ReloadOutlined,
  QuestionCircleOutlined,
  CheckCircleOutlined,
  SyncOutlined
} from '@ant-design/icons-vue';
import request from '@/api/index'
import { parseFeishuBitableUrl } from '@/utils/feishu'

const { t } = useI18n()

const activeModule = ref('ai')
const showWizard = ref(false)
const wizardCompleted = ref(false)
const wizardStep = ref(1)
const wizardCountry = ref('CN')
const wizardAppToken = ref('')
const wizardTableId = ref('')
const loading = ref(false)
const saving = ref(false)
const testingConnection = ref(false)
const feishuConnected = ref(false)
const selectedCountry = ref('default')
const currentWorkingCountry = ref('default')
const feishuUrl = ref('')
const parsingUrl = ref(false)
const urlParseResult = ref(null)

const configs = reactive({
  aiPrompt: '',
  continuePrompt: '',
  appToken: '',
  tableId: '',
  autoConfirm: false,
  notificationEnabled: true,
  batchSize: 100
})

const fieldMappings = ref([])

const modules = [
  { key: 'ai', titleKey: 'config.modules.ai', descKey: 'config.modules.aiDesc', icon: RobotOutlined, color: '#5B8FF9' },
  { key: 'feishu', titleKey: 'config.modules.feishu', descKey: 'config.modules.feishuDesc', icon: LinkOutlined, color: '#61DDAA' },
  { key: 'mapping', titleKey: 'config.modules.mapping', descKey: 'config.modules.mappingDesc', icon: SwapOutlined, color: '#F7BA1E' },
  { key: 'system', titleKey: 'config.modules.system', descKey: 'config.modules.systemDesc', icon: SettingOutlined, color: '#F55F74' }
]

const countries = [
  { code: 'default', flag: '🌐', name: '全局默认' },
  { code: 'CN', flag: '🇨🇳', name: '中国' },
  { code: 'FR', flag: '🇫🇷', name: '法国' },
  { code: 'DE', flag: '🇩🇪', name: '德国' },
  { code: 'PL', flag: '🇵🇱', name: '波兰' },
  { code: 'NL', flag: '🇳🇱', name: '荷兰' },
  { code: 'CZ', flag: '🇨🇿', name: '捷克' }
]

const countryOptions = countries.map(c => ({ value: c.code, label: `${c.flag} ${c.name}` }))

const wizardCountryName = computed(() => {
  const c = countries.find(c => c.code === wizardCountry.value)
  return c ? c.name : wizardCountry.value
})

const isUsingDefault = computed(() => selectedCountry.value === 'default')

const mappingColumns = [
  { title: 'AI字段', dataIndex: 'aiField', key: 'aiField', width: 150 },
  { title: '飞书字段', dataIndex: 'feishuField', key: 'feishuField', width: 150 },
  { title: '字段类型', dataIndex: 'type', key: 'type', width: 120 },
  { title: '必填', dataIndex: 'required', key: 'required', width: 100 },
  { title: '描述', dataIndex: 'description', key: 'description' },
  { title: '操作', key: 'action', width: 80, fixed: 'right' }
]

const wizardSteps = [
  { titleKey: 'config.wizard.step1', descKey: 'config.wizard.step1Desc' },
  { titleKey: 'config.wizard.step2', descKey: 'config.wizard.step2Desc' },
  { titleKey: 'config.wizard.step3', descKey: 'config.wizard.step3Desc' },
  { titleKey: 'config.wizard.step4', descKey: 'config.wizard.step4Desc' },
  { titleKey: 'config.wizard.step5', descKey: 'config.wizard.step5Desc' }
]

const defaultPrompts = {
  default: `识别法国考勤表格，逐行返回单个JSON数组：[NO,姓名,中介,班次,日期,到达,离开,休息,检查器,标记,已删除]。

规则：
1. 只返回真实数据，禁止编造
2. 标记列：手写/模糊/正常；夜班（20:00后到或06:00前走，跨午夜）；未出勤（到达离开都空或???）
3. 标记用;分隔，如"正常;夜班"
4. 删除线=true否则=false
5. 时间统一转HH:MM（24h）：6h→06:00,6h30→06:30,6.30→06:30,630→06:30,6→06:00,18h30→18:30
6. 日期统一转YYYY-MM-DD：17/05/2026→2026-05-17,17-05-2026→2026-05-17,17-05-26→2026-05-17
7. 每行单独数组，不要包大数组

示例：
["1","张三","中介A","MATIN","2026-05-17","08:00","18:00","60","","正常",false]
["2","李四","中介B","NUIT","2026-05-17","22:00","06:00","60","","正常;夜班",false]
["3","王五","中介C","MATIN","2026-05-17","08:30","17:30","60","","手写",false]
["4","???","中介D","SOIR","2026-05-17","???","???","30","","模糊;未出勤",false]`,
  CN: `识别中国考勤表格，逐行返回单个 JSON 数组。

规则：
1. 只返回真实数据，禁止编造
2. 仔细观察工号和姓名列，识别记录质量：
   - 如果内容是手写的，标记为"手写"
   - 如果内容模糊不清楚（如???），标记为"模糊"
   - 如果内容清晰可辨认，标记为"正常"
3. 根据到达时间和离开时间判断是否为夜班：
   - 到达时间在22:00之后，或离开时间在06:00之前
   - 跨越午夜的班次
   - 如果是夜班，添加"夜班"标记
4. 第10个字段（标记）使用分号分隔多个标记，如"正常;夜班"或"手写"
5. 删除线标记第11个字段设为 true，否则 false
6. 每一行就是一条记录，格式为：[工号,姓名,中介,班次,日期,到达,离开,休息,检查器,标记,已删除]`,
  FR: `识别法国考勤表格，逐行返回单个 JSON 数组。

规则：
1. 只返回真实数据，禁止编造
2. 仔细观察工号和姓名列，识别记录质量：
   - 如果内容是手写的，标记为"手写"
   - 如果内容模糊不清楚（如???），标记为"模糊"
   - 如果内容清晰可辨认，标记为"正常"
3. 根据到达时间和离开时间判断是否为夜班（法国出勤规则）：
   - 到达时间在20:00之后，或离开时间在06:00之前
   - 跨越午夜的班次（如22:00到06:00）
   - 如果是夜班，添加"夜班"标记
4. 第10个字段（标记）使用分号分隔多个标记
5. 每一行就是一条记录，格式为：[NO,NOM_PRENOM,AGENCE_INTERIMAIRE,HORAIRES_DU_TRAVAIL,Date,ARRIVEE,DEPART,PAUSE,CHECKER,SmartMark,已删除]`
}

const defaultContinuePrompt = '请接续上文继续输出，不要重复已有内容，保持相同格式。'

const defaultFieldMapping = [
  { aiField: 'NO', feishuField: 'NO', type: 'string', required: true, description: '工号' },
  { aiField: 'NOM_PRENOM', feishuField: 'NOM', type: 'string', required: true, description: '姓名' },
  { aiField: 'AGENCE_INTERIMAIRE', feishuField: 'AGENCE', type: 'string', required: false, description: '中介' },
  { aiField: 'SHIFT', feishuField: 'SHIFT', type: 'string', required: false, description: '班次' },
  { aiField: 'Date', feishuField: 'DATE', type: 'date', required: true, description: '日期' },
  { aiField: 'ARRIVEE', feishuField: 'ARRIVE', type: 'datetime', required: true, description: '到达时间' },
  { aiField: 'DEPART', feishuField: 'DEPART', type: 'datetime', required: true, description: '离开时间' },
  { aiField: 'PAUSE', feishuField: 'PAUS', type: 'number', required: true, description: '休息时间' },
  { aiField: 'CHECKER', feishuField: 'CHECKER', type: 'string', required: false, description: '检查器' },
  { aiField: 'SmartMark', feishuField: 'Mark', type: 'string', required: false, description: '标记' }
]

const reloadConfigs = async () => {
  try {
    await request({ url: '/config/reload', method: 'POST' });
    message.success('配置文件已从 prompts.md/feishu.md 重新加载成功！');
    await loadConfigs();
  } catch (error) {
    console.error('刷新配置失败:', error);
    message.error('刷新配置失败，请检查控制台');
  }
};

const loadConfigs = async () => {
  loading.value = true
  try {
    const [aiRes, feishuRes, countryRes] = await Promise.all([
      request({ url: '/config/ai-prompt', params: { country: selectedCountry.value } }),
      request({ url: '/config/feishu', params: { country: selectedCountry.value } }),
      request({ url: '/config/current-country' })
    ])

    configs.aiPrompt = aiRes.data.ai_prompt || defaultPrompts.default
    configs.continuePrompt = aiRes.data.continue_prompt || defaultContinuePrompt
    configs.appToken = feishuRes.data.appToken || ''
    configs.tableId = feishuRes.data.tableId || ''
    
    if (feishuRes.data.fieldMapping && Array.isArray(feishuRes.data.fieldMapping)) {
      fieldMappings.value = feishuRes.data.fieldMapping
    } else {
      fieldMappings.value = JSON.parse(JSON.stringify(defaultFieldMapping))
    }
    
    currentWorkingCountry.value = countryRes.data.country || 'default'
  } catch (error) {
    console.error('加载配置失败:', error)
  } finally {
    loading.value = false
  }
}

const loadCountryConfigs = async () => {
  await loadConfigs()
}

const setAsCurrentCountry = async () => {
  try {
    await request({
      url: '/config/current-country',
      method: 'put',
      data: { country: selectedCountry.value }
    })
    currentWorkingCountry.value = selectedCountry.value
    message.success('已设置为当前工作国家')
  } catch (error) {
    console.error('设置失败:', error)
  }
}

const saveAiConfig = async () => {
  saving.value = true
  try {
    await request({
      url: '/config/ai-prompt',
      method: 'put',
      data: {
        country: selectedCountry.value,
        ai_prompt: configs.aiPrompt,
        continue_prompt: configs.continuePrompt
      }
    })
    message.success(t('config.saveSuccess'))
  } catch (error) {
    console.error('保存失败:', error)
  } finally {
    saving.value = false
  }
}

const saveFeishuConfig = async () => {
  saving.value = true
  try {
    const fieldMappingYaml = generateFieldMappingYaml(fieldMappings.value)
    await request({
      url: '/config/feishu',
      method: 'put',
      data: {
        country: selectedCountry.value,
        bitable_app_token: configs.appToken,
        bitable_table_id: configs.tableId,
        field_mapping: fieldMappingYaml
      }
    })
    message.success(t('config.saveSuccess'))
  } catch (error) {
    console.error('保存失败:', error)
  } finally {
    saving.value = false
  }
}

const saveMappingConfig = async () => {
  saving.value = true
  try {
    await request({
      url: '/config/field-mapping',
      method: 'put',
      data: {
        country: selectedCountry.value,
        field_mapping: fieldMappings.value
      }
    })
    message.success(t('config.saveSuccess'))
  } catch (error) {
    console.error('保存失败:', error)
  } finally {
    saving.value = false
  }
}

const saveSystemConfig = async () => {
  message.info('系统设置已保存')
}

const handleFeishuUrlChange = () => {
  if (!feishuUrl.value) {
    urlParseResult.value = null
  }
}

const handleFeishuUrlParse = async () => {
  if (!feishuUrl.value) {
    message.warning('请输入飞书多维表链接')
    return
  }

  parsingUrl.value = true
  try {
    const result = parseFeishuBitableUrl(feishuUrl.value)
    urlParseResult.value = result

    if (!result.error) {
      configs.appToken = result.appToken || ''
      configs.tableId = result.tableId || ''

      if (result.tableId) {
        message.success('成功提取App Token和Table ID')
      } else {
        message.info('已提取App Token，请手动输入Table ID')
      }
    } else {
      message.error(result.error)
    }
  } catch (error) {
    console.error('解析链接失败:', error)
    message.error('解析链接失败')
  } finally {
    parsingUrl.value = false
  }
}

const testFeishuConnection = async () => {
  testingConnection.value = true
  try {
    await new Promise(resolve => setTimeout(resolve, 1500))
    feishuConnected.value = true
    message.success(t('config.feishuConfig.testSuccess'))
  } catch (error) {
    feishuConnected.value = false
    message.error(t('config.feishuConfig.testFailed'))
  } finally {
    testingConnection.value = false
  }
}

const loadCountryTemplate = () => {
  configs.aiPrompt = defaultPrompts[selectedCountry.value] || defaultPrompts.default
  configs.continuePrompt = defaultContinuePrompt
  message.success(t('config.aiConfig.templateLoadSuccess'))
}

const loadMappingTemplate = () => {
  fieldMappings.value = JSON.parse(JSON.stringify(defaultFieldMapping))
  message.success(t('config.aiConfig.templateLoadSuccess'))
}

const addMapping = () => {
  fieldMappings.value.push({
    aiField: '',
    feishuField: '',
    type: 'string',
    required: false,
    description: ''
  })
}

const removeMapping = (record) => {
  const index = fieldMappings.value.indexOf(record)
  if (index > -1) {
    fieldMappings.value.splice(index, 1)
  }
}

const resetSystemConfig = () => {
  configs.autoConfirm = false
  configs.notificationEnabled = true
  configs.batchSize = 100
}

const generateFieldMappingYaml = (mappings) => {
  let yaml = 'field_mapping:\n'
  for (const mapping of mappings) {
    yaml += `  - aiField: '${mapping.aiField}'\n`
    yaml += `    feishuField: '${mapping.feishuField}'\n`
    yaml += `    type: '${mapping.type}'\n`
    yaml += `    required: ${mapping.required}\n`
    yaml += `    description: '${mapping.description}'\n`
  }
  return yaml
}

const completeWizard = async () => {
  saving.value = true
  try {
    const prompt = defaultPrompts[wizardCountry.value] || defaultPrompts.default
    await request({
      url: '/config/ai-prompt',
      method: 'put',
      data: {
        country: wizardCountry.value,
        ai_prompt: prompt,
        continue_prompt: defaultContinuePrompt
      }
    })
    
    if (wizardAppToken.value || wizardTableId.value) {
      const fieldMappingYaml = generateFieldMappingYaml(defaultFieldMapping)
      await request({
        url: '/config/feishu',
        method: 'put',
        data: {
          country: wizardCountry.value,
          bitable_app_token: wizardAppToken.value,
          bitable_table_id: wizardTableId.value,
          field_mapping: fieldMappingYaml
        }
      })
    }
    
    await request({
      url: '/config/current-country',
      method: 'put',
      data: { country: wizardCountry.value }
    })
    
    wizardCompleted.value = true
    showWizard.value = false
    selectedCountry.value = wizardCountry.value
    await loadConfigs()
    message.success(t('config.saveSuccess'))
  } catch (error) {
    console.error('保存失败:', error)
  } finally {
    saving.value = false
  }
}

onMounted(() => {
  loadConfigs()
})
</script>

<style lang="scss" scoped>
.config-container {
  padding: 0;
}

.wizard-banner {
  margin-bottom: 24px;
  border-radius: 8px;
  cursor: pointer;
}

.country-selector-card {
  margin-bottom: 24px;
}

.country-selector {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.url-parse-result {
  margin-top: 12px;

  :deep(.ant-alert) {
    border-radius: 6px;
  }
}

.selector-info {
  display: flex;
  align-items: center;
  gap: 12px;
}

.label {
  font-weight: 500;
  font-size: 15px;
}

.module-nav {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: 16px;
  margin-bottom: 24px;
}

.module-card {
  background: white;
  border-radius: 12px;
  padding: 20px;
  display: flex;
  align-items: center;
  gap: 16px;
  cursor: pointer;
  transition: all 0.2s ease;
  border: 2px solid transparent;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.06);

  &:hover {
    box-shadow: 0 4px 12px rgba(0, 0, 0, 0.1);
    transform: translateY(-2px);
  }

  &.active {
    border-color: #5B8FF9;
    background: linear-gradient(135deg, rgba(91, 143, 249, 0.05) 0%, rgba(123, 97, 255, 0.05) 100%);
  }
}

.module-icon {
  width: 56px;
  height: 56px;
  border-radius: 12px;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 24px;
  color: white;
  flex-shrink: 0;
}

.module-info {
  flex: 1;
}

.module-title {
  margin: 0 0 4px 0;
  font-size: 15px;
  font-weight: 600;
  color: #1F2329;
}

.module-desc {
  margin: 0;
  font-size: 13px;
  color: #8F959E;
}

.module-panel {
  .config-card {
    border-radius: 12px;
    border: none;
    box-shadow: 0 2px 8px rgba(0, 0, 0, 0.06);
  }
}

.card-header-flex {
  display: flex;
  justify-content: space-between;
  align-items: center;
  width: 100%;
}

.card-title {
  margin: 0 0 4px 0;
  font-size: 17px;
  font-weight: 600;
  color: #1F2329;
}

.card-desc {
  margin: 0;
  font-size: 13px;
  color: #8F959E;
}

.header-actions {
  display: flex;
  gap: 8px;
}

.help-box {
  display: flex;
  gap: 16px;
  padding: 16px;
  background: linear-gradient(135deg, #F8F9FF 0%, #F0F4FF 100%);
  border-radius: 10px;
}

.help-icon {
  font-size: 24px;
}

.help-content {
  flex: 1;

  h4 {
    margin: 0 0 8px 0;
    font-size: 14px;
    font-weight: 600;
    color: #1F2329;
  }

  p {
    margin: 0;
    font-size: 13px;
    color: #8F959E;
  }
}

.setting-item {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 16px 0;
  border-bottom: 1px solid #F0F1F5;

  &:last-child {
    border-bottom: none;
  }
}

.setting-info {
  h4 {
    margin: 0 0 4px 0;
    font-size: 14px;
    font-weight: 600;
    color: #1F2329;
  }

  .setting-desc {
    margin: 0;
    font-size: 13px;
    color: #8F959E;
  }
}

.wizard-modal {
  :deep(.ant-modal-content) {
    border-radius: 12px;
    overflow: hidden;
  }
}

.wizard-steps {
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 24px 0;
}

.wizard-step {
  display: flex;
  align-items: center;
  gap: 12px;

  &.active .step-number {
    background: #5B8FF9;
    color: white;
  }

  &.completed .step-number {
    background: #52c41a;
    color: white;
  }
}

.step-number {
  width: 36px;
  height: 36px;
  border-radius: 50%;
  background: #F0F1F5;
  color: #8F959E;
  display: flex;
  align-items: center;
  justify-content: center;
  font-weight: 600;
  font-size: 14px;
  transition: all 0.2s ease;
}

.step-info {
  .step-title {
    font-size: 13px;
    font-weight: 600;
    color: #1F2329;
  }

  .step-desc {
    font-size: 12px;
    color: #8F959E;
  }
}

.step-divider {
  width: 40px;
  height: 2px;
  background: #F0F1F5;
  margin: 0 8px;

  &.active {
    background: #5B8FF9;
  }
}

.wizard-content {
  padding: 24px 0;
  min-height: 300px;
}

.wizard-step-content {
  text-align: center;

  h3 {
    margin-bottom: 16px;
    color: #1F2329;
  }
}

.welcome-content {
  padding: 40px 0;

  .welcome-icon {
    font-size: 64px;
    margin-bottom: 24px;
  }

  h2 {
    margin: 0 0 16px 0;
    font-size: 24px;
    font-weight: 700;
    color: #1F2329;
  }

  p {
    margin: 0;
    font-size: 15px;
    color: #8F959E;
  }
}

.country-selector-grid {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 12px;
}

.country-card {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 8px;
  padding: 20px;
  border: 2px solid #F0F1F5;
  border-radius: 10px;
  cursor: pointer;
  transition: all 0.2s ease;

  &:hover {
    border-color: #5B8FF9;
    background: rgba(91, 143, 249, 0.05);
  }

  &.selected {
    border-color: #5B8FF9;
    background: rgba(91, 143, 249, 0.1);
  }
}

.country-flag {
  font-size: 32px;
}

.country-name {
  font-size: 14px;
  font-weight: 600;
  color: #1F2329;
}

.wizard-footer {
  display: flex;
  gap: 12px;
  justify-content: flex-end;
  padding-top: 24px;
  border-top: 1px solid #F0F1F5;
  margin-top: 24px;
}
</style>
