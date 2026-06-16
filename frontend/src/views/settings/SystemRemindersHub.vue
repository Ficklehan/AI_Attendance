<template>
  <div class="system-reminders-hub">
    <PageShell :title="$t('settings.hub.title')" :subtitle="$t('settings.hub.subtitle')" />

    <a-card class="surface-card hub-tabs-card" :bordered="false">
      <a-tabs v-model:activeKey="activeTab">
        <a-tab-pane key="reminders" :tab="$t('settings.hub.tabReminders')">
          <ReminderRulesPanel />
        </a-tab-pane>
        <a-tab-pane key="nightShift" :tab="$t('settings.hub.tabNightShift')">
          <NightShiftSettingsCard />
        </a-tab-pane>
        <a-tab-pane key="imageQuality" :tab="$t('settings.hub.tabImageQuality')">
          <ImageQualitySettingsCard />
        </a-tab-pane>
        <a-tab-pane key="system" :tab="$t('settings.hub.tabSystem')">
          <SystemSettingsPanel />
        </a-tab-pane>
      </a-tabs>
    </a-card>
  </div>
</template>

<script setup>
import { ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import PageShell from '@/components/PageShell.vue'
import NightShiftSettingsCard from '@/components/NightShiftSettingsCard.vue'
import ImageQualitySettingsCard from '@/components/ImageQualitySettingsCard.vue'
import ReminderRulesPanel from '@/views/settings/ReminderRulesPanel.vue'
import SystemSettingsPanel from '@/views/settings/SystemSettingsPanel.vue'

const route = useRoute()
const activeTab = ref('reminders')

watch(
  () => route.query.tab,
  (tab) => {
    if (tab === 'nightShift' || tab === 'imageQuality' || tab === 'system' || tab === 'reminders') {
      activeTab.value = tab
    }
  },
  { immediate: true },
)
</script>

<style scoped lang="scss">
.hub-tabs-card :deep(.ant-card-body) {
  padding-top: 8px;
}
</style>
