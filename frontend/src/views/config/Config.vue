<template>
  <div class="config-page page-inner">
    <PageShell :title="pageTitle" :subtitle="pageSubtitle">
      <template #extra>
        <a-button v-if="!wizardCompleted" type="primary" @click="showWizard = true">
          {{ $t('config.wizard.title') }}
        </a-button>
        <a-button @click="reloadConfigs">
          <template #icon><ReloadOutlined /></template>
          {{ $t('config.reloadFromFile') }}
        </a-button>
      </template>
    </PageShell>

    <a-card class="country-bar surface-card" :bordered="false">
      <div class="country-selector">
        <div class="selector-info">
          <span class="label">{{ $t('config.countryLabel') }}：</span>
          <a-select
            v-model:value="selectedCountry"
            style="width: 200px"
            @change="loadCountryConfigs"
            :options="countryOptions"
          />
          <a-tag v-if="countryBundle.promptFromGlobalFallback" color="orange">
            {{ $t('config.aiFallbackGlobal') }}
          </a-tag>
          <a-tag v-else color="blue">{{ $t('config.aiCountrySpecific') }}</a-tag>
          <a-tag v-if="countryBundle.feishuFromGlobalFallback" color="orange">
            {{ $t('config.feishuFallbackGlobal') }}
          </a-tag>
          <a-tag v-else color="green">{{ $t('config.feishuCountrySpecific') }}</a-tag>
        </div>
        <a-space wrap>
          <a-button type="primary" @click="setAsCurrentCountry">
            <template #icon><CheckCircleOutlined /></template>
            {{ $t('config.setWorkingCountry') }}
          </a-button>
          <a-button @click="loadCountryConfigs">
            <template #icon><SyncOutlined /></template>
            {{ $t('config.refreshView') }}
          </a-button>
        </a-space>
      </div>
    </a-card>

    <a-collapse v-model:activeKey="effectiveCollapseKeys" class="effective-collapse">
      <a-collapse-panel key="effective" :header="$t('config.effective.panelTitle')">
        <a-spin :spinning="bundleLoading">
          <div class="effective-config-panel">
            <div class="panel-header">
              <a-space size="small" wrap>
                <a-tag color="purple">
                  {{ $t('config.effective.workingCountry') }}：{{ formatCountryCode(currentWorkingCountry) }}
                </a-tag>
                <a-tag>
                  {{ $t('config.effective.editCountry') }}：{{ formatCountryCode(selectedCountry) }}
                </a-tag>
                <a-button type="link" size="small" @click="openPromptPreview">
                  {{ $t('config.effective.previewPrompt') }}
                </a-button>
              </a-space>
            </div>
          <a-row :gutter="16">
            <a-col :xs="24" :md="12">
              <div class="effective-block">
                <h4 class="block-title">
                  <RobotOutlined />
                  {{ $t('config.effective.aiSection') }}
                </h4>
                <a-descriptions :column="1" size="small" bordered>
                  <a-descriptions-item :label="$t('config.effective.requestCountry')">
                    {{ formatCountryCode(countryBundle.requestCountry) }}
                  </a-descriptions-item>
                  <a-descriptions-item :label="$t('config.effective.effective')">
                    <a-tag :color="countryBundle.promptFromGlobalFallback ? 'orange' : 'blue'">
                      {{ formatCountryCode(countryBundle.effectivePromptCountry) }}
                    </a-tag>
                  </a-descriptions-item>
                  <a-descriptions-item :label="$t('config.effective.mdSection')">
                    {{ countryBundle.promptSection || '—' }}
                  </a-descriptions-item>
                  <a-descriptions-item :label="$t('config.effective.promptLength')">
                    {{ $t('config.effective.chars', { n: (countryBundle.aiPrompt || '').length }) }}
                  </a-descriptions-item>
                  <a-descriptions-item :label="$t('config.effective.continueLength')">
                    {{ $t('config.effective.chars', { n: (countryBundle.continuePrompt || '').length }) }}
                  </a-descriptions-item>
                </a-descriptions>
                <p v-if="countryBundle.promptFromGlobalFallback" class="block-hint">
                  {{ $t('config.effective.promptFallbackHint') }}
                </p>
              </div>
            </a-col>
            <a-col :xs="24" :md="12">
              <div class="effective-block">
                <h4 class="block-title">
                  <LinkOutlined />
                  {{ $t('config.effective.feishuSection') }}
                </h4>
                <a-descriptions :column="1" size="small" bordered>
                  <a-descriptions-item :label="$t('config.effective.requestCountry')">
                    {{ formatCountryCode(countryBundle.requestCountry) }}
                  </a-descriptions-item>
                  <a-descriptions-item :label="$t('config.effective.effective')">
                    <a-tag :color="countryBundle.feishuFromGlobalFallback ? 'orange' : 'green'">
                      {{ formatCountryCode(countryBundle.effectiveFeishuCountry) }}
                    </a-tag>
                  </a-descriptions-item>
                  <a-descriptions-item :label="$t('config.effective.appToken')">
                    <a-tag :color="feishuTokenConfigured ? 'success' : 'default'">
                      {{ feishuTokenConfigured ? $t('config.effective.configured') : $t('config.effective.notConfigured') }}
                    </a-tag>
                    <span v-if="feishuTokenConfigured" class="token-mask">
                      {{ maskToken(countryBundle.appToken) }}
                    </span>
                  </a-descriptions-item>
                  <a-descriptions-item :label="$t('config.effective.tableId')">
                    <a-tag :color="feishuTableConfigured ? 'success' : 'default'">
                      {{ feishuTableConfigured ? $t('config.effective.configured') : $t('config.effective.notConfigured') }}
                    </a-tag>
                    <span v-if="feishuTableConfigured" class="token-mask">
                      {{ countryBundle.tableId }}
                    </span>
                  </a-descriptions-item>
                  <a-descriptions-item :label="$t('config.effective.fieldMapping')">
                    {{ $t('config.effective.mappingCount', { n: fieldMappingCount }) }}
                  </a-descriptions-item>
                  <a-descriptions-item :label="$t('config.effective.feishuSync')">
                    <a-tag :color="countryBundle.syncEnabled ? 'success' : 'default'">
                      {{ countryBundle.syncEnabled ? $t('config.effective.syncOn') : $t('config.effective.syncOff') }}
                    </a-tag>
                  </a-descriptions-item>
                </a-descriptions>
                <p v-if="countryBundle.feishuFromGlobalFallback" class="block-hint">
                  {{ $t('config.effective.feishuFallbackHint') }}
                </p>
                <p v-if="!feishuReady" class="block-hint warn">
                  {{ $t('config.effective.feishuIncomplete') }}
                </p>
              </div>
            </a-col>
          </a-row>
          </div>
        </a-spin>
      </a-collapse-panel>
    </a-collapse>

    <a-modal
      v-model:open="showPromptPreview"
      title="MiMo 实际请求提示词预览"
      width="720px"
      :footer="null"
    >
      <a-descriptions v-if="promptPreview" :column="1" size="small" bordered class="preview-meta">
        <a-descriptions-item label="请求国家">{{ promptPreview.requestCountry }}</a-descriptions-item>
        <a-descriptions-item label="生效 AI 国家">{{ promptPreview.effectivePromptCountry || promptPreview.effectiveCountry }}</a-descriptions-item>
        <a-descriptions-item label="生效飞书国家">{{ promptPreview.effectiveFeishuCountry }}</a-descriptions-item>
        <a-descriptions-item label="章节">{{ promptPreview.promptSection }}</a-descriptions-item>
        <a-descriptions-item label="含示例块">{{ promptPreview.includesExampleBlock ? '是' : '否' }}</a-descriptions-item>
        <a-descriptions-item label="API 长度">{{ promptPreview.apiPromptLength }} 字符</a-descriptions-item>
      </a-descriptions>
      <a-typography-paragraph v-if="promptPreview?.apiPromptPreview" class="preview-text">
        <pre>{{ promptPreview.apiPromptPreview }}</pre>
      </a-typography-paragraph>
    </a-modal>

    <a-tabs
      v-model:activeKey="activeModule"
      type="card"
      class="config-tabs"
      :tab-bar-style="showModuleTabs ? undefined : { display: 'none' }"
    >
      <a-tab-pane v-if="visibleTabKeys.includes('ai')" key="ai" :tab="$t('config.tabs.ai')">
      <div class="module-panel">
        <a-card class="config-card">
          <template #title>
            <div class="card-header-flex">
              <div>
                <h3 class="card-title">{{ t('config.aiConfig.title') }}</h3>
                <p class="card-desc">{{ t('config.aiConfig.subtitle') }}</p>
              </div>
            </div>
          </template>

          <a-form layout="vertical">
            <a-form-item :label="t('config.aiConfig.aiPrompt')">
              <template #label>
                <span>
                  {{ t('config.aiConfig.aiPrompt') }}
                  <a-tooltip :title="t('config.aiConfig.aiPromptDesc')">
                    <QuestionCircleOutlined style="margin-left: 4px; color: #73707F" />
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
                    <QuestionCircleOutlined style="margin-left: 4px; color: #73707F" />
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

        <NightShiftSettingsCard style="margin-top: 20px" />
      </div>
      </a-tab-pane>

      <a-tab-pane v-if="visibleTabKeys.includes('feishu')" key="feishu" :tab="$t('config.tabs.feishu')">
      <div class="module-panel">
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
                    <QuestionCircleOutlined style="margin-left: 4px; color: #73707F" />
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
                    <QuestionCircleOutlined style="margin-left: 4px; color: #73707F" />
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
                    <QuestionCircleOutlined style="margin-left: 4px; color: #73707F" />
                  </a-tooltip>
                </span>
              </template>
              <a-input
                v-model:value="configs.tableId"
                :placeholder="t('config.feishuConfig.tableIdPlaceholder')"
              />
            </a-form-item>

            <a-form-item :label="t('config.feishuConfig.syncEnabled')">
              <a-switch v-model:checked="configs.syncEnabled" />
              <span class="sync-enabled-hint">{{ t('config.feishuConfig.syncEnabledHint') }}</span>
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
      </a-tab-pane>

      <a-tab-pane v-if="visibleTabKeys.includes('mapping')" key="mapping" :tab="$t('config.tabs.mapping')">
      <div class="module-panel">
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
                <TableColumnSettings
                  :columns="mappingConfigurableColumns"
                  :hidden-keys="mappingHiddenKeys"
                  :frozen-keys="mappingFrozenKeys"
                  @update:hidden-keys="setMappingHiddenKeys"
                  @update:frozen-keys="setMappingFrozenKeys"
                  @show-all="showAllMappingColumns"
                  @clear-freeze="clearMappingFrozenKeys"
                />
              </div>
            </div>
          </template>

          <a-table
            :columns="mappingColumns"
            :scroll="{ x: mappingScrollX }"
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
                <div class="table-action-cell table-action-cell--icons table-action-cell--icons-1">
                  <span class="table-action-cell__slot">
                    <a-button type="text" danger size="small" @click="removeMapping(record)">
                      <DeleteOutlined />
                    </a-button>
                  </span>
                </div>
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
      </a-tab-pane>

      <a-tab-pane v-if="visibleTabKeys.includes('system')" key="system" :tab="$t('config.tabs.system')">
      <div class="module-panel">
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
                    <QuestionCircleOutlined style="margin-left: 4px; color: #73707F" />
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
      </a-tab-pane>
    </a-tabs>

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
            <p style="color: #73707F; margin-top: 16px">此向导将帮助您完成所有必要配置，只需5分钟即可开始使用！</p>
          </div>
        </div>

        <div v-if="wizardStep === 2" class="wizard-step-content">
          <h3>{{ t('config.aiConfig.countrySelect') }}</h3>
          <p style="color: #73707F; margin-bottom: 24px">{{ t('config.aiConfig.countrySelectDesc') }}</p>
          <div class="country-selector-grid">
            <div
              v-for="country in countries"
              :key="country.code"
              class="country-card"
              :class="{ selected: wizardCountry === country.code }"
              @click="wizardCountry = country.code"
            >
              <span class="country-flag">{{ country.flag }}</span>
              <span class="country-name">{{ translateCountryName(country.code, country.name) }}</span>
            </div>
          </div>
        </div>

        <div v-if="wizardStep === 3" class="wizard-step-content">
          <h3>{{ t('config.feishuConfig.title') }}</h3>
          <p style="color: #73707F; margin-bottom: 24px">{{ t('config.feishuConfig.subtitle') }}</p>
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
          <p style="color: #73707F; margin-bottom: 24px">{{ t('config.mappingConfig.subtitle') }}</p>
          <a-alert type="info" :description="`将为 ${wizardCountryName} 自动加载推荐的字段映射模板`" show-icon />
        </div>

        <div v-if="wizardStep === 5" class="wizard-step-content">
          <div class="welcome-content">
            <div class="welcome-icon">✅</div>
            <h2>{{ t('config.wizard.step5') }}</h2>
            <p style="color: #34C77B; font-size: 16px; margin-top: 16px">
              {{ t('config.wizard.step5Desc') }}
            </p>
            <p style="color: #73707F; margin-top: 16px">点击"完成配置"保存所有设置并开始使用！</p>
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
import { ref, reactive, computed, onMounted, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { useRoute } from 'vue-router'
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
import PageShell from '@/components/PageShell.vue'
import NightShiftSettingsCard from '@/components/NightShiftSettingsCard.vue'
import { useCountryStore } from '@/stores/country'
import { useAuthStore } from '@/stores/auth'
import { setCachedWorkingCountry } from '@/utils/countryHeader'
import { formatCountryLabel, translateCountryName, buildCountrySelectOption } from '@/utils/countryLabels'
import { withTableSorters } from '@/utils/tableSort'
import TableColumnSettings from '@/components/TableColumnSettings.vue'
import { useColumnFreeze } from '@/composables/useColumnFreeze'
import { sumTableScrollX } from '@/utils/tableAutoColumns'

const { t, locale } = useI18n()
const route = useRoute()
const countryStore = useCountryStore()
const authStore = useAuthStore()

const routeModule = computed(() => route.meta.configModule || '')
const visibleTabKeys = computed(() => {
  const m = routeModule.value
  if (m === 'ai') return ['ai']
  if (m === 'feishu') return ['feishu', 'mapping', 'system']
  return ['ai', 'feishu', 'mapping', 'system']
})
const showModuleTabs = computed(() => visibleTabKeys.value.length > 1)
const pageTitle = computed(() => {
  if (routeModule.value === 'ai') return t('settings.menu.ai')
  if (routeModule.value === 'feishu') return t('settings.menu.feishu')
  return t('config.title')
})
const pageSubtitle = computed(() => {
  if (routeModule.value === 'ai') return t('config.aiConfig.subtitle')
  if (routeModule.value === 'feishu') return t('config.feishuConfig.subtitle')
  return t('config.subtitle')
})

const effectiveCollapseKeys = ref([])

const activeModule = ref('ai')

watch(
  routeModule,
  (m) => {
    if (m === 'ai') activeModule.value = 'ai'
    else if (m === 'feishu' && !visibleTabKeys.value.includes(activeModule.value)) {
      activeModule.value = 'feishu'
    }
  },
  { immediate: true }
)

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
const bundleLoading = ref(false)
const showPromptPreview = ref(false)
const promptPreview = ref(null)

const countryBundle = ref({
  requestCountry: 'default',
  effectivePromptCountry: 'default',
  effectiveFeishuCountry: 'default',
  promptSection: '',
  aiPrompt: '',
  continuePrompt: '',
  appToken: '',
  tableId: '',
  fieldMapping: [],
  promptFromGlobalFallback: false,
  feishuFromGlobalFallback: false,
  syncEnabled: true
})

const configs = reactive({
  aiPrompt: '',
  continuePrompt: '',
  appToken: '',
  tableId: '',
  syncEnabled: true,
  autoConfirm: false,
  notificationEnabled: true,
  batchSize: 100
})

const fieldMappings = ref([])

const modules = [
  { key: 'ai', titleKey: 'config.modules.ai', descKey: 'config.modules.aiDesc', icon: RobotOutlined, color: '#5B6CF7' },
  { key: 'feishu', titleKey: 'config.modules.feishu', descKey: 'config.modules.feishuDesc', icon: LinkOutlined, color: '#61DDAA' },
  { key: 'mapping', titleKey: 'config.modules.mapping', descKey: 'config.modules.mappingDesc', icon: SwapOutlined, color: '#F7BA1E' },
  { key: 'system', titleKey: 'config.modules.system', descKey: 'config.modules.systemDesc', icon: SettingOutlined, color: '#F55F74' }
]

const DEFAULT_COUNTRIES = [
  { code: 'default', flag: '🇺🇳', name: '全局默认' },
  { code: 'CN', flag: '🇨🇳', name: '中国' },
  { code: 'FR', flag: '🇫🇷', name: '法国' },
  { code: 'DE', flag: '🇩🇪', name: '德国' },
  { code: 'US', flag: '🇺🇸', name: '美国' },
  { code: 'PL', flag: '🇵🇱', name: '波兰' },
  { code: 'NL', flag: '🇳🇱', name: '荷兰' },
  { code: 'IT', flag: '🇮🇹', name: '意大利' },
  { code: 'ES', flag: '🇪🇸', name: '西班牙' },
  { code: 'CZ', flag: '🇨🇿', name: '捷克' }
]

const countries = ref([...DEFAULT_COUNTRIES])

const countryOptions = computed(() => {
  void locale.value
  return countries.value.map((c) => buildCountrySelectOption(c))
})

const wizardCountryName = computed(() => {
  void locale.value
  const c = countries.value.find((c) => c.code === wizardCountry.value)
  return c ? translateCountryName(c.code, c.name) : wizardCountry.value
})

const fieldMappingCount = computed(() => {
  const m = countryBundle.value.fieldMapping
  return Array.isArray(m) ? m.length : 0
})

const feishuTokenConfigured = computed(() => {
  const t = countryBundle.value.appToken
  return t != null && String(t).trim().length > 0
})

const feishuTableConfigured = computed(() => {
  const t = countryBundle.value.tableId
  return t != null && String(t).trim().length > 0
})

const feishuReady = computed(() => feishuTokenConfigured.value && feishuTableConfigured.value)

const formatCountryCode = (code) => {
  void locale.value
  if (!code || code === 'default') {
    return `${formatCountryLabel('default', '🇺🇳', '全局默认')} (default)`
  }
  const c = countries.value.find((item) => item.code === code)
  return c ? `${formatCountryLabel(c.code, c.flag, c.name)} (${code})` : code
}

const maskToken = (token) => {
  if (!token) return ''
  const s = String(token)
  if (s.length <= 12) return '***'
  return `${s.slice(0, 6)}…${s.slice(-4)}`
}

const loadCountryBundle = async (countryCode) => {
  const code = countryCode || selectedCountry.value
  bundleLoading.value = true
  try {
    const res = await request({ url: '/config/country-bundle', params: { country: code } })
    countryBundle.value = { ...countryBundle.value, ...(res.data || {}) }
  } catch (error) {
    console.error('加载国家配置摘要失败:', error)
  } finally {
    bundleLoading.value = false
  }
}

const openPromptPreview = async () => {
  try {
    const res = await request({
      url: '/config/recognition-prompt-preview',
      params: { country: selectedCountry.value }
    })
    promptPreview.value = res.data
    showPromptPreview.value = true
  } catch (error) {
    console.error('加载提示词预览失败:', error)
  }
}

const baseMappingColumns = computed(() => withTableSorters([
  { title: t('config.mappingConfig.aiField'), dataIndex: 'aiField', key: 'aiField', width: 150 },
  { title: t('config.mappingConfig.feishuField'), dataIndex: 'feishuField', key: 'feishuField', width: 150 },
  { title: t('config.mappingConfig.fieldType'), dataIndex: 'type', key: 'type', width: 120 },
  { title: t('config.mappingConfig.required'), dataIndex: 'required', key: 'required', width: 100 },
  { title: t('config.mappingConfig.description'), dataIndex: 'description', key: 'description' },
  { title: t('config.mappingConfig.action'), key: 'action', width: 80, align: 'center', fixed: 'right' },
]))
const {
  frozenColumns: mappingColumns,
  hiddenKeys: mappingHiddenKeys,
  frozenKeys: mappingFrozenKeys,
  configurableColumns: mappingConfigurableColumns,
  setHiddenKeys: setMappingHiddenKeys,
  setFrozenKeys: setMappingFrozenKeys,
  showAllColumns: showAllMappingColumns,
  clearFrozenKeys: clearMappingFrozenKeys,
} = useColumnFreeze('config-mapping', baseMappingColumns, { defaultFrozen: ['aiField'] })
const mappingScrollX = computed(() => sumTableScrollX(mappingColumns.value))

const wizardSteps = [
  { titleKey: 'config.wizard.step1', descKey: 'config.wizard.step1Desc' },
  { titleKey: 'config.wizard.step2', descKey: 'config.wizard.step2Desc' },
  { titleKey: 'config.wizard.step3', descKey: 'config.wizard.step3Desc' },
  { titleKey: 'config.wizard.step4', descKey: 'config.wizard.step4Desc' },
  { titleKey: 'config.wizard.step5', descKey: 'config.wizard.step5Desc' }
]

const defaultContinuePrompt = '接续上文继续输出，格式与字段不变，不重复已输出行。'

/** 配置页以库内容为准；库空则空编辑框，不再注入本地硬编码模板 */
const applyPromptFromApi = (aiRes) => {
  configs.aiPrompt = aiRes?.data?.ai_prompt || ''
  configs.continuePrompt = aiRes?.data?.continue_prompt || defaultContinuePrompt
}

const defaultFieldMapping = [
  { aiField: 'NO', feishuField: 'NO', type: 'string', required: true, description: '工号' },
  { aiField: 'Pays', feishuField: 'Pays', type: 'string', required: false, description: '国家' },
  { aiField: 'Entrepot', feishuField: 'Entrepôt', type: 'string', required: false, description: '仓库' },
  { aiField: 'NOM_PRENOM', feishuField: 'NOM PRENOM', type: 'string', required: false, description: '姓名' },
  { aiField: 'AGENCE_INTERIMAIRE', feishuField: "AGENCE D'INTERIMAIR", type: 'string', required: false, description: '中介机构' },
  { aiField: 'HORAIRES_DU_TRAVAIL', feishuField: 'HORAIRES DU TRAVAI', type: 'string', required: false, description: '工作时间' },
  { aiField: 'Date', feishuField: 'Date', type: 'date', required: true, description: '日期' },
  { aiField: 'ARRIVEE_DATETIME', feishuField: 'ARRIVE', type: 'datetime', required: true, description: '到达时间' },
  { aiField: 'DEPAR_DATETIME', feishuField: 'DEPAR', type: 'datetime', required: true, description: '离开时间' },
  { aiField: 'PAUSE', feishuField: 'PAUS', type: 'number', required: true, description: '休息(分钟)' },
  { aiField: 'SIGNATURE', feishuField: 'SIGNATURE', type: 'string', required: false, description: '员工签名' },
  { aiField: 'Observations', feishuField: 'Observations', type: 'string', required: false, description: '备注' },
  { aiField: 'WorkHours', feishuField: 'WorkHours', type: 'number', required: false, description: '出勤工时' },
  { aiField: 'SmartMark', feishuField: 'Mark', type: 'string', required: false, description: '标记' },
  { aiField: 'TASK_ID', feishuField: '任务id', type: 'string', required: false, description: '任务id' },
  { aiField: 'UPLOADED_BY', feishuField: '上传人员', type: 'user', required: false, description: '上传人员' },
]

const reloadConfigs = async (silent = false) => {
  try {
    await request({ url: '/config/reload', method: 'POST' })
    if (!silent) {
      message.success('配置已重新加载（飞书/国家配置）')
    }
    await loadConfigs()
    await loadCountryBundle(selectedCountry.value)
  } catch (error) {
    console.error('刷新配置失败:', error);
    if (!silent) {
      message.error(t('config.refreshFailed'));
    }
  }
};

const loadConfigs = async () => {
  loading.value = true
  try {
    const [countryRes, optionsRes] = await Promise.all([
      request({ url: '/config/current-country' }),
      request({ url: '/config/country-options' }),
    ])

    if (optionsRes.data?.length) {
      countries.value = optionsRes.data
    }

    currentWorkingCountry.value = countryRes.data.country || 'default'
    selectedCountry.value = currentWorkingCountry.value
    setCachedWorkingCountry(currentWorkingCountry.value)
    countryStore.workingCountry = currentWorkingCountry.value
    if (optionsRes.data?.length) {
      countryStore.options = optionsRes.data
    }
    countryStore.hydrated = true

    // 国家确定后再拉提示词，避免先按旧国家取数再被工作国家覆盖
    const promptRes = await request({
      url: '/config/ai-prompt',
      params: { country: selectedCountry.value },
    })
    applyPromptFromApi(promptRes)

    const feishuForCountry = await request({
      url: '/config/feishu',
      params: { country: selectedCountry.value },
    })
    configs.appToken = feishuForCountry.data.appToken || ''
    configs.tableId = feishuForCountry.data.tableId || ''
    configs.syncEnabled = feishuForCountry.data.syncEnabled !== false

    if (feishuForCountry.data.fieldMapping && Array.isArray(feishuForCountry.data.fieldMapping)) {
      fieldMappings.value = feishuForCountry.data.fieldMapping
    } else {
      fieldMappings.value = JSON.parse(JSON.stringify(defaultFieldMapping))
    }

    await loadCountryBundle(selectedCountry.value)
    await countryStore.loadBundle(selectedCountry.value)
  } catch (error) {
    console.error('加载配置失败:', error)
  } finally {
    loading.value = false
  }
}

const loadCountryConfigs = async () => {
  loading.value = true
  try {
    const [aiRes, feishuRes] = await Promise.all([
      request({ url: '/config/ai-prompt', params: { country: selectedCountry.value } }),
      request({ url: '/config/feishu', params: { country: selectedCountry.value } })
    ])
    applyPromptFromApi(aiRes)
    configs.appToken = feishuRes.data.appToken || ''
    configs.tableId = feishuRes.data.tableId || ''
    configs.syncEnabled = feishuRes.data.syncEnabled !== false
    if (feishuRes.data.fieldMapping && Array.isArray(feishuRes.data.fieldMapping)) {
      fieldMappings.value = feishuRes.data.fieldMapping
    }
    await loadCountryBundle(selectedCountry.value)
  } catch (error) {
    console.error('加载国家配置失败:', error)
  } finally {
    loading.value = false
  }
}

const setAsCurrentCountry = async () => {
  try {
    await countryStore.setWorkingCountry(selectedCountry.value)
    currentWorkingCountry.value = selectedCountry.value
    await loadCountryBundle(selectedCountry.value)
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
      method: 'post',
      data: {
        country: selectedCountry.value,
        ai_prompt: configs.aiPrompt,
        continue_prompt: configs.continuePrompt
      }
    })
    // 保存后立刻从库回读，确保展示与库一致
    const aiRes = await request({
      url: '/config/ai-prompt',
      params: { country: selectedCountry.value },
    })
    applyPromptFromApi(aiRes)
    message.success(t('config.saveSuccess'))
    await loadCountryBundle(selectedCountry.value)
  } catch (error) {
    console.error('保存失败:', error)
  } finally {
    saving.value = false
  }
}

const saveFeishuConfig = async () => {
  saving.value = true
  try {
    await request({
      url: '/config/feishu',
      method: 'post',
      data: {
        country: selectedCountry.value,
        bitable_app_token: configs.appToken,
        bitable_table_id: configs.tableId,
        field_mapping: fieldMappings.value,
        sync_enabled: configs.syncEnabled,
      }
    })
    message.success(t('config.saveSuccess'))
    await loadCountryBundle(selectedCountry.value)
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
      method: 'post',
      data: {
        country: selectedCountry.value,
        field_mapping: fieldMappings.value
      }
    })
    message.success(t('config.saveSuccess'))
    await loadCountryBundle(selectedCountry.value)
  } catch (error) {
    console.error('保存失败:', error)
  } finally {
    saving.value = false
  }
}

const saveSystemConfig = async () => {
  saving.value = true
  try {
    await request({
      url: '/config/system',
      method: 'post',
      data: { notificationEnabled: configs.notificationEnabled },
    })
    message.success(t('config.saveSuccess'))
  } catch (error) {
    console.error('保存系统设置失败:', error)
  } finally {
    saving.value = false
  }
}

const loadSystemConfig = async () => {
  try {
    const res = await request({ url: '/config/system', method: 'get' })
    if (res.data && typeof res.data.notificationEnabled === 'boolean') {
      configs.notificationEnabled = res.data.notificationEnabled
    }
  } catch (error) {
    console.error('加载系统设置失败:', error)
  }
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
    message.error(t('config.parseLinkFailed'))
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
    // 向导复用库中 default 提示词，不再注入前端硬编码模板
    const defaultPromptRes = await request({
      url: '/config/ai-prompt',
      params: { country: 'default' },
      silentError: true,
    }).catch(() => null)
    const prompt = defaultPromptRes?.data?.ai_prompt || ''
    const continuePrompt = defaultPromptRes?.data?.continue_prompt || defaultContinuePrompt
    await request({
      url: '/config/ai-prompt',
      method: 'post',
      data: {
        country: wizardCountry.value,
        ai_prompt: prompt,
        continue_prompt: continuePrompt
      }
    })
    
    if (wizardAppToken.value || wizardTableId.value) {
      const fieldMappingYaml = generateFieldMappingYaml(defaultFieldMapping)
      await request({
        url: '/config/feishu',
        method: 'post',
        data: {
          country: wizardCountry.value,
          bitable_app_token: wizardAppToken.value,
          bitable_table_id: wizardTableId.value,
          field_mapping: fieldMappingYaml
        }
      })
    }
    
    await countryStore.setWorkingCountry(wizardCountry.value)

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

watch(activeModule, (m) => {
  if (m === 'system') loadSystemConfig()
})

onMounted(async () => {
  await reloadConfigs(true)
  if (activeModule.value === 'system') {
    await loadSystemConfig()
  }
})
</script>

<style lang="scss" scoped>
.config-page {
  display: flex;
  flex-direction: column;
  gap: $spacing-lg;
}

.country-bar {
  :deep(.ant-card-body) {
    padding: $spacing-lg;
  }
}

.effective-collapse {
  background: transparent;
  border: none;

  :deep(.ant-collapse-item) {
    border: 1px solid $border-light;
    border-radius: $border-radius-lg;
    overflow: hidden;
    background: $bg-card;
  }
}

.config-tabs {
  :deep(.ant-tabs-nav) {
    margin-bottom: $spacing-md;
  }

  :deep(.ant-tabs-tab) {
    font-weight: $font-weight-medium;
  }
}

.country-selector {
  display: flex;
  justify-content: space-between;
  align-items: center;
  flex-wrap: wrap;
  gap: 12px;
  margin-bottom: 16px;
}

.effective-config-panel {
  padding-top: 4px;
  border-top: 1px solid $border;
}

.panel-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  flex-wrap: wrap;
  gap: 8px;
  margin-bottom: 12px;
}

.panel-title {
  font-size: $font-size-lg;
  font-weight: $font-weight-semibold;
  color: $text-strong;
}

.effective-block {
  margin-bottom: 8px;
}

.block-title {
  display: flex;
  align-items: center;
  gap: 8px;
  margin: 0 0 10px;
  font-size: $font-size-md;
  font-weight: $font-weight-semibold;
  color: $text-strong;
}

.block-hint {
  margin: 10px 0 0;
  font-size: $font-size-sm;
  color: $text-secondary;
  line-height: 1.5;

  &.warn {
    color: $warning-dark;
  }
}

.sync-enabled-hint {
  margin-left: 12px;
  font-size: $font-size-sm;
  color: $text-secondary;
}

.token-mask {
  margin-left: 8px;
  font-size: $font-size-sm;
  color: $text-secondary;
  font-family: ui-monospace, monospace;
}

.preview-meta {
  margin-bottom: 12px;
}

.preview-text pre {
  margin: 0;
  padding: 12px;
  max-height: 360px;
  overflow: auto;
  font-size: $font-size-sm;
  line-height: 1.5;
  background: $bg-muted;
  border-radius: $radius-sm;
  white-space: pre-wrap;
  word-break: break-word;
}

.url-parse-result {
  margin-top: 12px;

  :deep(.ant-alert) {
    border-radius: $radius-sm;
  }
}

.selector-info {
  display: flex;
  align-items: center;
  gap: 12px;
}

.label {
  font-weight: $font-weight-medium;
  font-size: $font-size-lg;
}

.module-nav {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: 16px;
  margin-bottom: 24px;
}

.module-card {
  background: white;
  border-radius: $radius-lg;
  padding: 20px;
  display: flex;
  align-items: center;
  gap: 16px;
  cursor: pointer;
  transition: all $duration-base $ease-smooth;
  border: 2px solid transparent;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.06);

  &:hover {
    box-shadow: $shadow-md;
    transform: translateY(-2px);
  }

  &.active {
    border-color: $primary;
    background: $primary-light;
  }
}

.module-icon {
  width: 56px;
  height: 56px;
  border-radius: $radius-lg;
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
  font-size: $font-size-lg;
  font-weight: $font-weight-semibold;
  color: $text-strong;
}

.module-desc {
  margin: 0;
  font-size: 13px;
  color: $text-secondary;
}

.module-panel {
  .config-card {
    border-radius: $radius-lg;
    border: none;
    box-shadow: $shadow-card;
  }
}

.card-header-flex {
  display: flex;
  justify-content: space-between;
  align-items: center;
  width: 100%;
}

.card-title {
  margin: 0 0 $space-1 0;
  font-size: $font-size-xl;
  font-weight: $font-weight-extrabold;
  color: $text-strong;
  letter-spacing: -0.02em;
}

.card-desc {
  margin: 0;
  font-size: 13px;
  color: $text-secondary;
}

.header-actions {
  display: flex;
  gap: 8px;
}

.help-box {
  display: flex;
  gap: 16px;
  padding: 16px;
  background: $bg-surface;
  border-radius: $radius-lg;
}

.help-icon {
  font-size: 24px;
}

.help-content {
  flex: 1;

  h4 {
    margin: 0 0 8px 0;
    font-size: $font-size-md;
    font-weight: $font-weight-semibold;
    color: $text-strong;
  }

  p {
    margin: 0;
    font-size: 13px;
    color: $text-secondary;
  }
}

.setting-item {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 16px 0;
  border-bottom: 1px solid $border;

  &:last-child {
    border-bottom: none;
  }
}

.setting-info {
  h4 {
    margin: 0 0 4px 0;
    font-size: $font-size-md;
    font-weight: $font-weight-semibold;
    color: $text-strong;
  }

  .setting-desc {
    margin: 0;
    font-size: 13px;
    color: $text-secondary;
  }
}

.wizard-modal {
  :deep(.ant-modal-content) {
    border-radius: $radius-lg;
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
    background: $primary;
    color: white;
  }

  &.completed .step-number {
    background: $success;
    color: white;
  }
}

.step-number {
  width: 36px;
  height: 36px;
  border-radius: 50%;
  background: $border;
  color: $text-secondary;
  display: flex;
  align-items: center;
  justify-content: center;
  font-weight: $font-weight-semibold;
  font-size: $font-size-md;
  transition: all $duration-base $ease-smooth;
}

.step-info {
  .step-title {
    font-size: 13px;
    font-weight: $font-weight-semibold;
    color: $text-strong;
  }

  .step-desc {
    font-size: $font-size-sm;
    color: $text-secondary;
  }
}

.step-divider {
  width: 40px;
  height: 2px;
  background: $border;
  margin: 0 8px;

  &.active {
    background: $primary;
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
    color: $text-strong;
  }
}

.welcome-content {
  padding: 40px 0;

  .welcome-icon {
    font-size: 64px;
    margin-bottom: 24px;
  }

  h2 {
    margin: 0 0 $space-4 0;
    font-size: $font-size-3xl;
    font-weight: $font-weight-extrabold;
    color: $text-strong;
    letter-spacing: -0.02em;
  }

  p {
    margin: 0;
    font-size: $font-size-lg;
    color: $text-secondary;
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
  border: 2px solid $border;
  border-radius: $radius-lg;
  cursor: pointer;
  transition: all $duration-base $ease-smooth;

  &:hover {
    border-color: $primary;
    background: rgba($primary, 0.05);
  }

  &.selected {
    border-color: $primary;
    background: rgba($primary, 0.1);
  }
}

.country-flag {
  font-size: 32px;
}

.country-name {
  font-size: $font-size-md;
  font-weight: $font-weight-semibold;
  color: $text-strong;
}

.wizard-footer {
  display: flex;
  gap: 12px;
  justify-content: flex-end;
  padding-top: 24px;
  border-top: 1px solid $border;
  margin-top: 24px;
}
</style>
