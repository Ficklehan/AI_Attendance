<template>
  <div class="stat-overview" :class="{ compact, 'single-row': singleRow, clickable }">
    <button
      v-for="item in items"
      :key="item.key"
      type="button"
      class="stat-card"
      :class="[
        item.variant || item.key,
        {
          'is-active': activeKey === item.key,
          'is-empty': Number(item.value) === 0,
        },
      ]"
      :disabled="false"
      :tabindex="clickable ? 0 : -1"
      :aria-pressed="clickable ? activeKey === item.key : undefined"
      :title="clickable ? (activeKey === item.key ? clearHint : filterHint) : undefined"
      @click="onCardClick(item)"
    >
      <div class="stat-card__icon-wrap">
        <!-- Normal / Check -->
        <svg v-if="item.key === 'normal' || item.key === 'attendanceOk'" width="20" height="20" viewBox="0 0 24 24" fill="none">
          <path d="M9 12L11 14L15 10" stroke="currentColor" stroke-width="2.5" stroke-linecap="round" stroke-linejoin="round"/>
          <circle cx="12" cy="12" r="9" stroke="currentColor" stroke-width="2"/>
        </svg>
        <!-- Handwriting / Pen -->
        <svg v-else-if="item.key === 'handwriting' || item.key === 'ocrWrong'" width="20" height="20" viewBox="0 0 24 24" fill="none">
          <path d="M17 3L21 7L8 20H4V16L17 3Z" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"/>
          <path d="M14 6L18 10" stroke="currentColor" stroke-width="2" stroke-linecap="round"/>
        </svg>
        <!-- Blurred / Eye -->
        <svg v-else-if="item.key === 'blurred' || item.key === 'pendingException'" width="20" height="20" viewBox="0 0 24 24" fill="none">
          <path d="M1 12C1 12 5 4 12 4C19 4 23 12 23 12C23 12 19 20 12 20C5 20 1 12 1 12Z" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"/>
          <circle cx="12" cy="12" r="3" stroke="currentColor" stroke-width="2"/>
          <line x1="2" y1="2" x2="22" y2="22" stroke="currentColor" stroke-width="2" stroke-linecap="round"/>
        </svg>
        <!-- Night / Moon -->
        <svg v-else-if="item.key === 'night' || item.key === 'shiftVariance'" width="20" height="20" viewBox="0 0 24 24" fill="none">
          <path d="M21 12.79A9 9 0 1111.21 3 7 7 0 0021 12.79Z" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"/>
          <circle cx="17" cy="6" r="1" fill="currentColor" opacity="0.5"/>
          <circle cx="20" cy="10" r="0.7" fill="currentColor" opacity="0.3"/>
        </svg>
        <!-- Absent / X Circle -->
        <svg v-else-if="item.key === 'absent' || item.key === 'paperWrong'" width="20" height="20" viewBox="0 0 24 24" fill="none">
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
    </button>
  </div>
</template>

<script setup>
const props = defineProps({
  items: { type: Array, required: true },
  compact: { type: Boolean, default: false },
  singleRow: { type: Boolean, default: false },
  clickable: { type: Boolean, default: false },
  activeKey: { type: String, default: '' },
  filterHint: { type: String, default: '' },
  clearHint: { type: String, default: '' },
})

const emit = defineEmits(['select'])

const onCardClick = (item) => {
  if (!props.clickable || !item) return
  emit('select', item.key)
}
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

  &.single-row {
    display: flex;
    flex-wrap: nowrap;
    gap: 8px;
    overflow-x: auto;
    margin-bottom: 12px;

    .stat-card {
      flex: 1 1 0;
      min-width: 0;
      padding: 8px 10px;
      gap: 8px;
    }

    .stat-card__icon-wrap {
      width: 28px;
      height: 28px;
      border-radius: 6px;

      svg {
        width: 15px;
        height: 15px;
      }
    }

    .stat-card__value {
      font-size: 18px;
    }

    .stat-card__label {
      font-size: 12px;
    }
  }

  @media (max-width: 1100px) {
    &:not(.single-row) {
      grid-template-columns: repeat(3, 1fr);
    }
  }

  @media (max-width: 600px) {
    &:not(.single-row) {
      grid-template-columns: repeat(2, 1fr);
    }
  }
}

.stat-card {
  display: flex;
  align-items: center;
  gap: $space-3;
  padding: $space-4;
  border-radius: 8px;
  background: linear-gradient(180deg, #fafbfc 0%, #f2f4f7 100%);
  border: 1px solid #eaecf0;
  text-align: left;
  font: inherit;
  color: inherit;
  appearance: none;
  -webkit-appearance: none;
  cursor: default;
  transition: border-color $duration-fast $ease-smooth,
              background $duration-fast $ease-smooth;

  &:hover {
    background: #f2f4f7;
    border-color: #eaecf0;
  }

  .stat-card__icon-wrap {
    color: $text-secondary;
    background: transparent;
  }

  .stat-card__value {
    color: $text-strong;
  }

  &.is-empty {
    opacity: 0.72;
  }
}

.stat-overview.clickable .stat-card {
  cursor: pointer;

  &:focus-visible {
    outline: 2px solid rgba($primary, 0.4);
    outline-offset: 2px;
  }

  &.is-active {
    border-color: #eaecf0;
    background: #f2f4f7;
    box-shadow: inset 3px 0 0 $text-secondary;
  }

  &.is-active.normal,
  &.is-active.attendanceOk,
  &.is-active.blurred,
  &.is-active.pendingException,
  &.is-active.handwriting,
  &.is-active.ocrWrong,
  &.is-active.absent,
  &.is-active.paperWrong,
  &.is-active.night,
  &.is-active.shiftVariance,
  &.is-active.deleted {
    border-color: #eaecf0;
    background: #f2f4f7;
  }
}

.stat-card__icon-wrap {
  width: 40px;
  height: 40px;
  border-radius: 6px;
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
  font-size: 12px;
  color: $text-secondary;
  margin-top: 2px;
  font-weight: $font-weight-semibold;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}
</style>
