<template>
  <div class="system-reminders-hub">
    <PageShell :title="$t('settings.hub.title')" :subtitle="$t('settings.hub.subtitle')" />

    <a-card class="surface-card hub-tabs-card" :bordered="false">
      <a-tabs v-model:activeKey="activeTab">
        <a-tab-pane key="reminders" :tab="$t('settings.hub.tabReminders')">
          <ReminderRulesPanel />
        </a-tab-pane>
        <a-tab-pane key="system" :tab="$t('settings.hub.tabSystem')">
          <SystemSettingsPanel />
        </a-tab-pane>
      </a-tabs>
    </a-card>
  </div>
</template>

<script setup>
import { ref } from 'vue'
import PageShell from '@/components/PageShell.vue'
import ReminderRulesPanel from '@/views/settings/ReminderRulesPanel.vue'
import SystemSettingsPanel from '@/views/settings/SystemSettingsPanel.vue'

const activeTab = ref('reminders')
</script>

<style scoped lang="scss">
.hub-tabs-card :deep(.ant-card-body) {
  padding-top: 8px;
}
</style>
