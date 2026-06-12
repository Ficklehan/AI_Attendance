<template>
  <section class="template-block">
    <header class="template-block__head">
      <div class="template-block__title-row">
        <span class="template-block__icon" aria-hidden="true">
          <BellOutlined />
        </span>
        <h4 class="template-block__title">{{ title }}</h4>
      </div>
      <p v-if="hint" class="template-block__hint">{{ hint }}</p>
    </header>

    <a-textarea
      :value="modelValue"
      class="template-block__input"
      :rows="rows"
      :placeholder="placeholder"
      @update:value="(v) => emit('update:modelValue', v)"
    />

    <div class="template-vars">
      <span class="template-vars__label">{{ $t('settings.reminders.insertVariable') }}</span>
      <div class="template-vars__chips">
        <button
          v-for="item in variables"
          :key="item.key"
          type="button"
          class="var-chip"
          @click="emit('insert', item.key)"
        >
          <span class="var-chip__label">{{ item.label }}</span>
          <code class="var-chip__token">{{ formatToken(item.key) }}</code>
        </button>
      </div>
      <a-button type="link" size="small" class="template-vars__reset" @click="emit('reset')">
        {{ resetLabel }}
      </a-button>
    </div>

    <div class="message-preview">
      <div class="message-preview__label">{{ previewLabel }}</div>
      <article class="message-preview__card">
        <header v-if="parsed.title" class="message-preview__title">
          {{ parsed.title }}
        </header>
        <div class="message-preview__body">
          <p
            v-for="(line, index) in parsed.bodyLines"
            :key="index"
            class="message-preview__line"
            v-html="highlightLine(line)"
          />
          <p v-if="!parsed.bodyLines.length" class="message-preview__empty">
            {{ $t('settings.reminders.previewEmpty') }}
          </p>
        </div>
      </article>
    </div>
  </section>
</template>

<script setup>
import { computed } from 'vue'
import { BellOutlined } from '@ant-design/icons-vue'

const props = defineProps({
  modelValue: { type: String, default: '' },
  title: { type: String, required: true },
  hint: { type: String, default: '' },
  previewLabel: { type: String, required: true },
  resetLabel: { type: String, required: true },
  placeholder: { type: String, default: '' },
  rows: { type: Number, default: 5 },
  variables: {
    type: Array,
    default: () => [],
  },
  previewText: { type: String, default: '' },
  highlightValues: {
    type: Array,
    default: () => [],
  },
})

const emit = defineEmits(['update:modelValue', 'insert', 'reset'])

const formatToken = (key) => `{${key}}`

const escapeHtml = (text) => String(text || '')
  .replace(/&/g, '&amp;')
  .replace(/</g, '&lt;')
  .replace(/>/g, '&gt;')
  .replace(/"/g, '&quot;')

const parsed = computed(() => {
  const raw = String(props.previewText || '')
  const lines = raw.split('\n').map((line) => line.trimEnd())
  const first = (lines[0] || '').trim()
  const titleMatch = first.match(/^【(.+)】$/)
  if (titleMatch) {
    return {
      title: titleMatch[1],
      bodyLines: lines.slice(1).filter((line) => line.trim()),
    }
  }
  return {
    title: '',
    bodyLines: lines.filter((line) => line.trim()),
  }
})

const highlightLine = (line) => {
  let html = escapeHtml(line)
  const values = [...(props.highlightValues || [])]
    .filter(Boolean)
    .sort((a, b) => String(b).length - String(a).length)
  for (const value of values) {
    const token = escapeHtml(value)
    if (!token) continue
    html = html.split(token).join(`<span class="preview-em">${token}</span>`)
  }
  return html
}
</script>

<style scoped lang="scss">
.template-block {
  padding: 16px;
  border: 1px solid $border;
  border-radius: $radius-lg;
  background: linear-gradient(180deg, #fff 0%, $bg-muted 100%);
}

.template-block__head {
  margin-bottom: 12px;
}

.template-block__title-row {
  display: flex;
  align-items: center;
  gap: 8px;
}

.template-block__icon {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 28px;
  height: 28px;
  border-radius: 8px;
  background: $primary-light;
  color: $primary;
  font-size: 14px;
}

.template-block__title {
  margin: 0;
  font-size: $font-size-md;
  font-weight: $font-weight-semibold;
  color: $text-strong;
  letter-spacing: -0.02em;
}

.template-block__hint {
  margin: 8px 0 0;
  padding: 8px 10px;
  border-radius: $radius-md;
  background: $info-light;
  border: 1px solid rgba($primary, 0.12);
  font-size: $font-size-sm;
  line-height: 1.5;
  color: $text-secondary;
}

.template-block__input {
  border-radius: $radius-md !important;
  font-size: 13px;
  line-height: 1.6;
  resize: vertical;

  &:focus {
    box-shadow: 0 0 0 2px $primary-glow;
  }
}

.template-vars {
  margin-top: 12px;
}

.template-vars__label {
  display: block;
  margin-bottom: 8px;
  font-size: $font-size-xs;
  font-weight: $font-weight-medium;
  color: $text-tertiary;
  text-transform: uppercase;
  letter-spacing: 0.04em;
}

.template-vars__chips {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.var-chip {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 4px 10px 4px 8px;
  border: 1px solid $border;
  border-radius: 999px;
  background: $bg-surface;
  cursor: pointer;
  transition: border-color $duration-fast, background $duration-fast, box-shadow $duration-fast;

  &:hover {
    border-color: rgba($primary, 0.35);
    background: $primary-lighter;
    box-shadow: 0 1px 4px rgba($primary, 0.08);
  }
}

.var-chip__label {
  font-size: $font-size-sm;
  color: $text-primary;
}

.var-chip__token {
  padding: 1px 6px;
  border-radius: 4px;
  background: rgba($primary, 0.08);
  font-family: ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, monospace;
  font-size: 11px;
  color: $primary-dark;
}

.template-vars__reset {
  margin-top: 4px;
  padding-left: 0;
}

.message-preview {
  margin-top: 16px;
}

.message-preview__label {
  margin-bottom: 8px;
  font-size: $font-size-sm;
  font-weight: $font-weight-medium;
  color: $text-secondary;
}

.message-preview__card {
  border-radius: $radius-lg;
  border: 1px solid $border;
  background: $bg-surface;
  overflow: hidden;
  box-shadow: 0 4px 14px rgba(28, 26, 46, 0.06);
}

.message-preview__title {
  padding: 10px 14px;
  background: linear-gradient(135deg, $primary 0%, #7b8cff 100%);
  color: $text-inverted;
  font-size: $font-size-sm;
  font-weight: $font-weight-semibold;
  letter-spacing: 0.02em;
}

.message-preview__body {
  padding: 12px 14px 14px;
}

.message-preview__line {
  margin: 0 0 8px;
  font-size: 13px;
  line-height: 1.65;
  color: $text-primary;
  word-break: break-word;

  &:last-child {
    margin-bottom: 0;
  }

  :deep(.preview-em) {
    display: inline;
    padding: 0 4px;
    border-radius: 4px;
    background: $primary-light;
    color: $primary-dark;
    font-weight: $font-weight-semibold;
  }
}

.message-preview__empty {
  margin: 0;
  font-size: $font-size-sm;
  color: $text-tertiary;
  font-style: italic;
}
</style>
