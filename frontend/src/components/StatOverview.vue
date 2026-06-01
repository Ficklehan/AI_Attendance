<template>
  <div class="stat-overview" :class="{ compact }">
    <div
      v-for="(item, index) in items"
      :key="item.key"
      class="stat-card"
      :class="item.variant || item.key"
      :style="{ animationDelay: `${index * 0.08}s` }"
    >
      <div class="stat-card__icon-wrap">
        <!-- Normal / Check -->
        <svg v-if="item.key === 'normal'" width="20" height="20" viewBox="0 0 24 24" fill="none">
          <path d="M9 12L11 14L15 10" stroke="currentColor" stroke-width="2.5" stroke-linecap="round" stroke-linejoin="round"/>
          <circle cx="12" cy="12" r="9" stroke="currentColor" stroke-width="2"/>
        </svg>
        <!-- Handwriting / Pen -->
        <svg v-else-if="item.key === 'handwriting'" width="20" height="20" viewBox="0 0 24 24" fill="none">
          <path d="M17 3L21 7L8 20H4V16L17 3Z" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"/>
          <path d="M14 6L18 10" stroke="currentColor" stroke-width="2" stroke-linecap="round"/>
        </svg>
        <!-- Blurred / Eye -->
        <svg v-else-if="item.key === 'blurred'" width="20" height="20" viewBox="0 0 24 24" fill="none">
          <path d="M1 12C1 12 5 4 12 4C19 4 23 12 23 12C23 12 19 20 12 20C5 20 1 12 1 12Z" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"/>
          <circle cx="12" cy="12" r="3" stroke="currentColor" stroke-width="2"/>
          <line x1="2" y1="2" x2="22" y2="22" stroke="currentColor" stroke-width="2" stroke-linecap="round"/>
        </svg>
        <!-- Night / Moon -->
        <svg v-else-if="item.key === 'night'" width="20" height="20" viewBox="0 0 24 24" fill="none">
          <path d="M21 12.79A9 9 0 1111.21 3 7 7 0 0021 12.79Z" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"/>
          <circle cx="17" cy="6" r="1" fill="currentColor" opacity="0.5"/>
          <circle cx="20" cy="10" r="0.7" fill="currentColor" opacity="0.3"/>
        </svg>
        <!-- Absent / X Circle -->
        <svg v-else-if="item.key === 'absent'" width="20" height="20" viewBox="0 0 24 24" fill="none">
          <circle cx="12" cy="12" r="9" stroke="currentColor" stroke-width="2"/>
          <path d="M15 9L9 15M9 9L15 15" stroke="currentColor" stroke-width="2" stroke-linecap="round"/>
        </svg>
        <!-- Deleted / Trash -->
        <svg v-else width="20" height="20" viewBox="0 0 24 24" fill="none">
          <path d="M3 6H21" stroke="currentColor" stroke-width="2" stroke-linecap="round"/>
          <path d="M8 6V4C8 3.44772 8.44772 3 9 3H15C15.5523 3 16 3.44772 16 4V6" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"/>
          <path d="M5 6L6 20C6 20.5523 6.44772 21 7 21H17C17.5523 21 18 20.5523 18 20L19 6" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"/>
        </svg>
      </div>
      <div class="stat-card__info">
        <div class="stat-card__value">{{ item.value }}</div>
        <div class="stat-card__label">{{ item.label }}</div>
      </div>
    </div>
  </div>
</template>

<script setup>
defineProps({
  items: { type: Array, required: true },
  compact: { type: Boolean, default: false }
})
</script>

<style lang="scss" scoped>
.stat-overview {
  display: grid;
  grid-template-columns: repeat(6, 1fr);
  gap: $space-3;
  margin-bottom: $space-5;

  &.compact {
    grid-template-columns: repeat(3, 1fr);
  }

  @media (max-width: 1100px) {
    grid-template-columns: repeat(3, 1fr);
  }

  @media (max-width: 600px) {
    grid-template-columns: repeat(2, 1fr);
  }
}

.stat-card {
  display: flex;
  align-items: center;
  gap: $space-3;
  padding: $space-4;
  border-radius: $radius-lg;
  background: $bg-surface;
  border: 1px solid rgba($border, 0.6);
  opacity: 0;
  animation: fadeUp 0.4s $ease-out forwards;
  transition: transform $duration-base $ease-smooth,
              box-shadow $duration-base $ease-smooth,
              border-color $duration-base $ease-smooth;

  &:hover {
    transform: translateY(-2px);
    box-shadow: $shadow-md;
  }

  &.normal {
    border-color: rgba($success, 0.2);
    &:hover { border-color: rgba($success, 0.4); }
    .stat-card__icon-wrap {
      background: $success-light;
      color: $success;
    }
    .stat-card__value { color: $success-dark; }
  }

  &.handwriting {
    border-color: rgba($info, 0.15);
    &:hover { border-color: rgba($info, 0.35); }
    .stat-card__icon-wrap {
      background: $info-light;
      color: $info;
    }
    .stat-card__value { color: $info; }
  }

  &.blurred {
    border-color: rgba($warning, 0.15);
    &:hover { border-color: rgba($warning, 0.35); }
    .stat-card__icon-wrap {
      background: $warning-light;
      color: $warning;
    }
    .stat-card__value { color: $warning-dark; }
  }

  &.night {
    border-color: rgba(#7C6BC4, 0.15);
    &:hover { border-color: rgba(#7C6BC4, 0.35); }
    .stat-card__icon-wrap {
      background: #F3F0FF;
      color: #6B5BB3;
    }
    .stat-card__value { color: #6B5BB3; }
  }

  &.absent {
    border-color: rgba($danger, 0.12);
    &:hover { border-color: rgba($danger, 0.3); }
    .stat-card__icon-wrap {
      background: $danger-light;
      color: $danger;
    }
    .stat-card__value { color: $danger-dark; }
  }

  &.deleted {
    border-color: rgba($text-tertiary, 0.12);
    &:hover { border-color: rgba($text-tertiary, 0.25); }
    .stat-card__icon-wrap {
      background: $bg-muted;
      color: $text-tertiary;
    }
    .stat-card__value { color: $text-secondary; }
  }
}

.stat-card__icon-wrap {
  width: 40px;
  height: 40px;
  border-radius: $radius-md;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}

.stat-card__info {
  flex: 1;
  min-width: 0;
}

.stat-card__value {
  font-size: $font-size-2xl;
  font-weight: $font-weight-bold;
  line-height: $line-height-tight;
  font-variant-numeric: tabular-nums;
  color: $text-strong;
}

.stat-card__label {
  font-size: $font-size-xs;
  color: $text-tertiary;
  margin-top: 2px;
  font-weight: $font-weight-medium;
}
</style>
