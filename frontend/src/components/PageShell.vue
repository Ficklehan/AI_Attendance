<template>
  <header class="page-shell">
    <div class="page-shell__main">
      <div v-if="breadcrumb?.length" class="page-shell__breadcrumb">
        <span
          v-for="(item, index) in breadcrumb"
          :key="index"
          class="crumb"
          :class="{ active: index === breadcrumb.length - 1 }"
        >
          {{ item }}
        </span>
      </div>
      <h1 class="page-shell__title">{{ title }}</h1>
      <p v-if="subtitle" class="page-shell__subtitle">{{ subtitle }}</p>
    </div>
    <div v-if="$slots.extra" class="page-shell__extra">
      <slot name="extra" />
    </div>
  </header>
</template>

<script setup>
defineProps({
  title: { type: String, required: true },
  subtitle: { type: String, default: '' },
  breadcrumb: { type: Array, default: () => [] }
})
</script>

<style lang="scss" scoped>
.page-shell {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: $spacing-lg;
  margin-bottom: $spacing-xl;
  flex-wrap: wrap;
}

.page-shell__breadcrumb {
  display: flex;
  align-items: center;
  gap: $spacing-sm;
  margin-bottom: $spacing-sm;
  font-size: $font-size-xs;
  color: $text-secondary;

  .crumb:not(:last-child)::after {
    content: '/';
    margin-left: $spacing-sm;
    color: $border-color;
  }

  .crumb.active {
    color: $primary-color;
    font-weight: 500;
  }
}

.page-shell__title {
  margin: 0;
  font-size: $font-size-2xl;
  font-weight: 700;
  color: $text-primary;
  letter-spacing: -0.02em;
  line-height: 1.25;
}

.page-shell__subtitle {
  margin: $spacing-sm 0 0;
  font-size: $font-size-base;
  color: $text-secondary;
  line-height: 1.5;
  max-width: 640px;
}

.page-shell__extra {
  display: flex;
  align-items: center;
  gap: $spacing-sm;
  flex-shrink: 0;
}
</style>
