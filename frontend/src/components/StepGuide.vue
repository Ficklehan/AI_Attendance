<template>
  <div class="step-guide">
    <div
      v-for="(step, index) in steps"
      :key="index"
      class="step-guide__item"
      :style="{ animationDelay: `${index * 0.12}s` }"
    >
      <div class="step-guide__icon" :class="`step-${index}`">
        <!-- Step 1: Upload -->
        <svg v-if="index === 0" width="24" height="24" viewBox="0 0 24 24" fill="none">
          <path d="M12 16V4M12 4L8 8.5M12 4L16 8.5" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"/>
          <path d="M4 14V18C4 19.1046 4.89543 20 6 20H18C19.1046 20 20 19.1046 20 18V14" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"/>
        </svg>
        <!-- Step 2: AI Recognition -->
        <svg v-else-if="index === 1" width="24" height="24" viewBox="0 0 24 24" fill="none">
          <path d="M12 2C6.477 2 2 6.477 2 12C2 17.523 6.477 22 12 22C17.523 22 22 17.523 22 12" stroke="currentColor" stroke-width="2" stroke-linecap="round"/>
          <circle cx="9" cy="10" r="1.5" fill="currentColor"/>
          <circle cx="15" cy="10" r="1.5" fill="currentColor"/>
          <path d="M9 15C9.5 16 10.5 16.5 12 16.5C13.5 16.5 14.5 16 15 15" stroke="currentColor" stroke-width="2" stroke-linecap="round"/>
          <path d="M18 5L22 1M22 1V5M22 1H18" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"/>
        </svg>
        <!-- Step 3: Confirm -->
        <svg v-else width="24" height="24" viewBox="0 0 24 24" fill="none">
          <rect x="3" y="4" width="18" height="16" rx="3" stroke="currentColor" stroke-width="2"/>
          <path d="M8 12L11 15L16 9" stroke="currentColor" stroke-width="2.5" stroke-linecap="round" stroke-linejoin="round"/>
          <line x1="20" y1="9" x2="20" y2="15" stroke="currentColor" stroke-width="2" stroke-linecap="round"/>
        </svg>
      </div>
      <div class="step-guide__content">
        <div class="step-guide__title">{{ step.title }}</div>
        <div class="step-guide__desc">{{ step.desc }}</div>
      </div>
      <div v-if="index < steps.length - 1" class="step-guide__arrow" aria-hidden="true">
        <svg width="20" height="12" viewBox="0 0 20 12" fill="none">
          <path d="M1 6H17M17 6L13 2M17 6L13 10" stroke="currentColor" stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round"/>
        </svg>
      </div>
    </div>
  </div>
</template>

<script setup>
defineProps({
  steps: {
    type: Array,
    required: true
  }
})
</script>

<style lang="scss" scoped>
.step-guide {
  display: flex;
  align-items: center;
  gap: $space-3;
  padding: $space-5 $space-6;
  background: $bg-surface;
  border-radius: $radius-xl;
  box-shadow: $shadow-card;
  border: 1px solid rgba($border, 0.5);

  @media (max-width: 768px) {
    flex-direction: column;
    gap: $space-4;
    padding: $space-5;
  }
}

.step-guide__item {
  display: flex;
  align-items: center;
  gap: $space-3;
  flex: 1;
  opacity: 0;
  animation: fadeUp 0.5s $ease-out forwards;

  @media (max-width: 768px) {
    width: 100%;
  }
}

.step-guide__icon {
  width: 52px;
  height: 52px;
  border-radius: $radius-lg;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
  color: white;
  transition: transform $duration-base $ease-bounce;

  &:hover {
    transform: scale(1.08);
  }

  &.step-0 {
    background: $primary-gradient-deep;
    box-shadow: 0 4px 14px rgba($primary, 0.3);
  }

  &.step-1 {
    background: $primary-gradient;
    box-shadow: 0 4px 14px rgba($primary, 0.3);
  }

  &.step-2 {
    background: linear-gradient(135deg, $success 0%, #5DD99A 100%);
    box-shadow: 0 4px 14px rgba($success, 0.3);
  }
}

.step-guide__content {
  flex: 1;
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
  color: $text-tertiary;
  line-height: $line-height-normal;
}

.step-guide__arrow {
  color: $border;
  flex-shrink: 0;
  margin: 0 $space-1;
  display: flex;
  align-items: center;

  @media (max-width: 768px) {
    transform: rotate(90deg);
    margin: $space-1 0;
  }
}
</style>
