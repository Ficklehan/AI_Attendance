<template>
  <div
    class="step-guide"
    :class="{
      'step-guide--compact': compact,
      'step-guide--atelier': atelier,
    }"
  >
    <div
      v-for="(step, index) in steps"
      :key="index"
      class="step-guide__item"
      :style="{ animationDelay: `${index * 0.1}s` }"
    >
      <div class="step-guide__visual" :class="`step-guide__visual--${index}`" aria-hidden="true">
        <!-- Upload: paper sheet + arrow -->
        <svg v-if="index === 0" class="step-guide__illu" viewBox="0 0 48 48" fill="none">
          <rect x="12" y="8" width="22" height="28" rx="3" fill="currentColor" class="illu-fill" opacity="0.12"/>
          <rect x="12" y="8" width="22" height="28" rx="3" stroke="currentColor" stroke-width="1.6"/>
          <path d="M16 15h14M16 19.5h10M16 24h12" stroke="currentColor" stroke-width="1.5" stroke-linecap="round" opacity="0.55"/>
          <circle cx="34" cy="34" r="9" fill="#FF7E5D" opacity="0.95"/>
          <path d="M34 30v6.5M31.2 33.2L34 30.2l2.8 3" stroke="#fff" stroke-width="1.7" stroke-linecap="round" stroke-linejoin="round"/>
        </svg>
        <!-- AI scan: lens over lines -->
        <svg v-else-if="index === 1" class="step-guide__illu" viewBox="0 0 48 48" fill="none">
          <rect x="8" y="12" width="24" height="24" rx="3" fill="currentColor" class="illu-fill" opacity="0.1"/>
          <rect x="8" y="12" width="24" height="24" rx="3" stroke="currentColor" stroke-width="1.6"/>
          <path d="M12 18h12M12 22.5h9M12 27h11" stroke="currentColor" stroke-width="1.4" stroke-linecap="round" opacity="0.45"/>
          <circle cx="30" cy="28" r="9" stroke="#5B6CF7" stroke-width="2"/>
          <path d="M36.5 34.5L41 39" stroke="#5B6CF7" stroke-width="2.2" stroke-linecap="round"/>
          <path d="M26 28h8" stroke="#FF7E5D" stroke-width="1.6" stroke-linecap="round" opacity="0.9"/>
        </svg>
        <!-- Confirm: stamp check -->
        <svg v-else class="step-guide__illu" viewBox="0 0 48 48" fill="none">
          <rect x="11" y="9" width="22" height="26" rx="3" fill="currentColor" class="illu-fill" opacity="0.1"/>
          <rect x="11" y="9" width="22" height="26" rx="3" stroke="currentColor" stroke-width="1.6"/>
          <path d="M15 16h14M15 20.5h10" stroke="currentColor" stroke-width="1.4" stroke-linecap="round" opacity="0.45"/>
          <circle cx="31" cy="31" r="10" fill="#34C77B" opacity="0.95"/>
          <path d="M26.5 31.2l3 3 6-6.5" stroke="#fff" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"/>
        </svg>
      </div>
      <div class="step-guide__body">
        <div class="step-guide__index">{{ String(index + 1).padStart(2, '0') }}</div>
        <div class="step-guide__content">
          <div class="step-guide__title">{{ step.title }}</div>
          <div class="step-guide__desc">{{ step.desc }}</div>
        </div>
      </div>
      <div v-if="index < steps.length - 1" class="step-guide__connector" aria-hidden="true">
        <span class="step-guide__connector-line" />
      </div>
    </div>
  </div>
</template>

<script setup>
defineProps({
  steps: {
    type: Array,
    required: true,
  },
  compact: {
    type: Boolean,
    default: false,
  },
  atelier: {
    type: Boolean,
    default: false,
  },
})
</script>

<style lang="scss" scoped>
.step-guide {
  display: flex;
  align-items: stretch;
  gap: 0;
  padding: $space-5 $space-6;
  background: $bg-surface;
  border-radius: $radius-xl;
  box-shadow: $shadow-card;
  border: 1px solid rgba($border, 0.5);

  @media (max-width: 768px) {
    flex-direction: column;
    gap: $space-3;
    padding: $space-4;
  }
}

.step-guide__item {
  position: relative;
  display: flex;
  align-items: center;
  gap: $space-3;
  flex: 1;
  min-width: 0;
  opacity: 0;
  animation: fadeUp 0.45s $ease-out forwards;
}

.step-guide__visual {
  flex: 0 0 auto;
  width: 52px;
  height: 52px;
  border-radius: 14px;
  display: grid;
  place-items: center;
  color: $text-strong;
  background: linear-gradient(160deg, #FFFDFB 0%, #F3F0F7 100%);
  border: 1px solid rgba($border, 0.9);
  box-shadow: inset 0 1px 0 rgba(255, 255, 255, 0.9);

  &--0 { color: #4A3F6B; }
  &--1 { color: #3E4CC4; }
  &--2 { color: #1A7A4E; }
}

.step-guide__illu {
  width: 40px;
  height: 40px;
}

.step-guide__body {
  display: flex;
  flex-direction: column;
  gap: 2px;
  min-width: 0;
  flex: 1;
}

.step-guide__index {
  font-size: 10px;
  font-weight: 700;
  letter-spacing: 0.08em;
  color: $text-tertiary;
  font-variant-numeric: tabular-nums;
}

.step-guide__content {
  min-width: 0;
}

.step-guide__title {
  font-size: $font-size-md;
  font-weight: $font-weight-semibold;
  color: $text-strong;
  margin-bottom: 2px;
  line-height: $line-height-tight;
}

.step-guide__desc {
  font-size: $font-size-sm;
  color: $text-secondary;
  line-height: 1.4;
}

.step-guide__connector {
  flex: 0 0 28px;
  display: flex;
  align-items: center;
  justify-content: center;
  align-self: center;

  @media (max-width: 768px) {
    display: none;
  }
}

.step-guide__connector-line {
  display: block;
  width: 100%;
  height: 0;
  border-top: 1.5px dashed rgba($text-tertiary, 0.45);
}

/* ── Atelier / compact：嵌在首页顶栏 ── */
.step-guide--atelier,
.step-guide--compact {
  padding: 0;
  gap: 0;
  background: transparent;
  border: 0;
  box-shadow: none;
  border-radius: 0;
  width: 100%;
  height: 100%;
  align-items: stretch;

  .step-guide__item {
    padding: 0 $space-2;
    gap: $space-2;
  }

  .step-guide__item:first-child {
    padding-left: 0;
  }

  .step-guide__item:last-child {
    padding-right: 0;
  }

  .step-guide__visual {
    width: 44px;
    height: 44px;
    border-radius: 12px;
  }

  .step-guide__illu {
    width: 34px;
    height: 34px;
  }

  .step-guide__title {
    font-size: $font-size-sm;
    margin-bottom: 0;
  }

  .step-guide__desc {
    font-size: 11px;
    line-height: 1.3;
    color: $text-tertiary;
    display: -webkit-box;
    -webkit-line-clamp: 2;
    -webkit-box-orient: vertical;
    overflow: hidden;
  }

  .step-guide__connector {
    flex-basis: 20px;
  }

  @media (max-width: 960px) {
    flex-wrap: wrap;
    row-gap: $space-3;

    .step-guide__item {
      flex: 1 1 160px;
      padding: 0;
    }

    .step-guide__connector {
      display: none;
    }
  }
}
</style>
