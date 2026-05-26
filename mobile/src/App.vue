<template>
  <div class="app-container">
    <router-view />
    <TabBar v-if="showTabBar" />
  </div>
</template>

<script setup>
import { computed } from 'vue'
import { useRoute } from 'vue-router'
import TabBar from './components/TabBar.vue'

const route = useRoute()

const showTabBar = computed(() => {
  const noTabPages = ['camera', 'result', 'login']
  return !noTabPages.includes(route.name)
})
</script>

<style scoped>
.app-container {
  min-height: 100vh;
  padding-bottom: 80px;
  background: #f5f5f5;
}
</style>
