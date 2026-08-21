<template>
  <a-drawer
    v-model:open="visible"
    placement="right"
    width="440"
    class="notification-drawer"
    :body-style="{ padding: 0 }"
    @close="handleClose"
  >
    <template #title>
      <div class="drawer-head">
        <span class="drawer-head__icon" aria-hidden="true">
          <BellOutlined />
        </span>
        <div class="drawer-head__text">
          <span class="drawer-head__title">{{ $t('notification.title') }}</span>
          <span v-if="unreadCount > 0" class="drawer-head__meta">
            {{ $t('notification.unreadCount', { count: unreadCount }) }}
          </span>
        </div>
      </div>
    </template>

    <div class="notification-panel">
      <div class="notification-toolbar">
        <a-button size="small" class="toolbar-btn" :loading="loading" @click="fetchList">
          {{ $t('notification.refresh') }}
        </a-button>
        <a-button size="small" class="toolbar-btn" :disabled="!hasUnread" @click="handleReadAll">
          {{ $t('notification.markAllRead') }}
        </a-button>
        <a-button size="small" class="toolbar-btn toolbar-btn--danger" :disabled="items.length === 0" @click="handleClearAll">
          {{ $t('notification.clearAll') }}
        </a-button>
      </div>

      <a-spin :spinning="loading && items.length === 0" class="notification-spin">
        <div v-if="!loading && items.length === 0" class="notification-empty">
          <div class="notification-empty__icon" aria-hidden="true">
            <BellOutlined />
          </div>
          <p class="notification-empty__title">{{ $t('notification.empty') }}</p>
          <p class="notification-empty__hint">{{ $t('notification.emptyHint') }}</p>
        </div>

        <div v-else class="notification-list">
          <article
            v-for="item in displayItems"
            :key="item.id"
            class="notification-card"
            :class="{ 'notification-card--unread': !item.read }"
            @click="handleClick(item)"
          >
            <div class="notification-card__accent" aria-hidden="true" />
            <div class="notification-card__main">
              <header class="notification-card__header">
                <span v-if="item.parsed.badge" class="notification-card__badge">
                  {{ item.parsed.badge }}
                </span>
                <h4 class="notification-card__title">{{ item.parsed.headline }}</h4>
                <span v-if="!item.read" class="notification-card__dot" :title="$t('notification.unread')" />
              </header>
              <p v-if="item.parsed.body" class="notification-card__body">{{ item.parsed.body }}</p>
              <footer class="notification-card__footer">
                <time class="notification-card__time">{{ formatTime(item.createdAt) }}</time>
                <span v-if="item.link" class="notification-card__action">{{ $t('notification.viewTask') }}</span>
              </footer>
            </div>
          </article>
        </div>
      </a-spin>
    </div>
  </a-drawer>
</template>

<script setup>
import { computed, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import { Modal, message } from 'ant-design-vue'
import { BellOutlined } from '@ant-design/icons-vue'
import { useI18n } from 'vue-i18n'
import {
  listNotifications,
  markNotificationRead,
  markAllNotificationsRead,
  clearAllNotifications,
} from '@/api/notification'

const props = defineProps({
  open: { type: Boolean, default: false },
})

const emit = defineEmits(['update:open', 'read'])

const { t, locale } = useI18n()
const router = useRouter()

const visible = computed({
  get: () => props.open,
  set: (v) => emit('update:open', v),
})

const loading = ref(false)
const items = ref([])

const hasUnread = computed(() => items.value.some((n) => !n.read))
const unreadCount = computed(() => items.value.filter((n) => !n.read).length)

const stripBodyHeader = (body) => {
  const lines = String(body || '').split('\n')
  if (lines.length > 0 && /^【[^】]+】\s*$/.test(lines[0].trim())) {
    return lines.slice(1).join('\n').trim()
  }
  return String(body || '').trim()
}

const parseItem = (item) => {
  const rawTitle = String(item?.title || '').trim()
  const rawBody = stripBodyHeader(item?.body)
  const titleMatch = rawTitle.match(/^【([^】]+)】(.*)$/)
  if (titleMatch) {
    const rest = titleMatch[2]?.trim()
    return {
      badge: titleMatch[1],
      headline: rest || titleMatch[1],
      body: rawBody,
    }
  }
  return {
    badge: '',
    headline: rawTitle || t('notification.title'),
    body: rawBody,
  }
}

const displayItems = computed(() => {
  void locale.value
  return items.value.map((item) => ({
    ...item,
    parsed: parseItem(item),
  }))
})

watch(
  () => props.open,
  (open) => {
    if (open) fetchList()
  },
)

watch(locale, () => {
  if (props.open) fetchList()
})

const fetchList = async () => {
  loading.value = true
  try {
    const res = await listNotifications({ current: 1, size: 50 })
    items.value = res.data?.records || []
  } catch (e) {
    console.error(e)
  } finally {
    loading.value = false
  }
}

const handleReadAll = async () => {
  try {
    await markAllNotificationsRead()
    items.value = items.value.map((n) => ({ ...n, read: true }))
    emit('read')
    message.success(t('notification.allReadDone'))
  } catch (e) {
    console.error(e)
  }
}

const handleClearAll = () => {
  Modal.confirm({
    title: t('notification.clearAll'),
    content: t('notification.clearAllConfirm'),
    okText: t('common.confirm'),
    cancelText: t('common.cancel'),
    okType: 'danger',
    onOk: async () => {
      try {
        await clearAllNotifications()
        items.value = []
        emit('read')
        message.success(t('notification.clearAllDone'))
      } catch (e) {
        console.error(e)
      }
    },
  })
}

const handleClick = async (item) => {
  let removed = false
  let taskDeleted = false
  if (!item.read) {
    try {
      const res = await markNotificationRead(item.id)
      removed = Boolean(res.data?.removed)
      taskDeleted = Boolean(res.data?.taskDeleted)
      if (!removed) {
        item.read = true
      }
      emit('read')
    } catch (e) {
      console.error(e)
    }
  }
  if (removed || taskDeleted) {
    items.value = items.value.filter((n) => n.id !== item.id)
    if (taskDeleted) {
      message.info(t('notification.taskDeleted'))
    }
    return
  }
  if (item.link) {
    try {
      const url = new URL(item.link, window.location.origin)
      const path = url.pathname.replace(/^\/(clockai|attendance)/, '') || '/tasks'
      router.push(path + url.search)
      visible.value = false
    } catch {
      if (item.link.includes('/tasks/')) {
        const taskId = item.link.split('/tasks/')[1]?.split(/[?#]/)[0]
        if (taskId) {
          router.push(`/tasks/${taskId}`)
          visible.value = false
        }
      }
    }
  }
}

const formatTime = (value) => {
  if (!value) return ''
  return String(value).replace('T', ' ').slice(0, 16)
}

const handleClose = () => {
  visible.value = false
}
</script>

<style scoped lang="scss">
.drawer-head {
  display: flex;
  align-items: center;
  gap: 10px;
}

.drawer-head__icon {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 32px;
  height: 32px;
  border-radius: $radius-md;
  background: $primary-light;
  color: $primary;
  font-size: 16px;
}

.drawer-head__text {
  display: flex;
  flex-direction: column;
  gap: 2px;
  min-width: 0;
}

.drawer-head__title {
  font-size: $font-size-lg;
  font-weight: $font-weight-semibold;
  color: $text-strong;
  letter-spacing: -0.02em;
  line-height: 1.2;
}

.drawer-head__meta {
  font-size: $font-size-xs;
  color: $primary;
  font-weight: $font-weight-medium;
}

.notification-panel {
  display: flex;
  flex-direction: column;
  min-height: 100%;
  background: $bg-muted;
}

.notification-toolbar {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  padding: 14px 16px;
  background: $bg-surface;
  border-bottom: 1px solid $border-light;
}

.toolbar-btn {
  border-radius: $radius-md !important;
  font-size: $font-size-sm;

  &--danger:not(:disabled) {
    color: $danger-dark !important;
    border-color: rgba($danger, 0.25) !important;

    &:hover {
      color: $danger-dark !important;
      border-color: rgba($danger, 0.4) !important;
      background: $danger-light !important;
    }
  }
}

.notification-spin {
  display: block;
  min-height: 240px;
}

.notification-empty {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 56px 24px;
  text-align: center;
}

.notification-empty__icon {
  width: 56px;
  height: 56px;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  background: $bg-surface;
  color: $text-tertiary;
  font-size: 22px;
  margin-bottom: 14px;
  box-shadow: 0 4px 14px rgba(28, 26, 46, 0.06);
}

.notification-empty__title {
  margin: 0 0 6px;
  font-size: $font-size-md;
  font-weight: $font-weight-semibold;
  color: $text-primary;
}

.notification-empty__hint {
  margin: 0;
  font-size: $font-size-sm;
  color: $text-tertiary;
  line-height: 1.5;
  max-width: 260px;
}

.notification-list {
  display: flex;
  flex-direction: column;
  gap: 10px;
  padding: 14px 16px 20px;
}

.notification-card {
  position: relative;
  display: flex;
  border-radius: $radius-lg;
  border: 1px solid $border;
  background: $bg-surface;
  overflow: hidden;
  cursor: pointer;
  transition: border-color $duration-fast, box-shadow $duration-fast, transform $duration-fast;

  &:hover {
    border-color: rgba($primary, 0.28);
    box-shadow: 0 6px 18px rgba(28, 26, 46, 0.07);
    transform: translateY(-1px);
  }

  &--unread {
    border-color: rgba($primary, 0.22);
    background: linear-gradient(180deg, #fff 0%, $primary-lighter 100%);

    .notification-card__accent {
      opacity: 1;
    }
  }
}

.notification-card__accent {
  flex: 0 0 4px;
  background: $primary-gradient;
  opacity: 0.35;
}

.notification-card__main {
  flex: 1;
  min-width: 0;
  padding: 12px 14px;
}

.notification-card__header {
  display: flex;
  align-items: flex-start;
  gap: 8px;
  margin-bottom: 6px;
}

.notification-card__badge {
  flex: 0 0 auto;
  max-width: 42%;
  padding: 2px 8px;
  border-radius: 999px;
  background: $primary-light;
  color: $primary-dark;
  font-size: 11px;
  font-weight: $font-weight-semibold;
  line-height: 1.45;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.notification-card__title {
  flex: 1;
  min-width: 0;
  margin: 0;
  font-size: $font-size-sm;
  font-weight: $font-weight-semibold;
  color: $text-strong;
  line-height: 1.45;
  letter-spacing: -0.01em;
}

.notification-card__dot {
  flex: 0 0 8px;
  width: 8px;
  height: 8px;
  margin-top: 5px;
  border-radius: 50%;
  background: $primary;
  box-shadow: 0 0 0 3px $primary-glow;
}

.notification-card__body {
  margin: 0 0 10px;
  font-size: 13px;
  line-height: 1.65;
  color: $text-secondary;
  white-space: pre-wrap;
  word-break: break-word;
  display: -webkit-box;
  -webkit-line-clamp: 6;
  -webkit-box-orient: vertical;
  overflow: hidden;
}

.notification-card__footer {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
}

.notification-card__time {
  font-size: $font-size-xs;
  color: $text-tertiary;
  font-variant-numeric: tabular-nums;
}

.notification-card__action {
  font-size: $font-size-xs;
  font-weight: $font-weight-medium;
  color: $primary;
}
</style>

<style lang="scss">
.notification-drawer {
  .ant-drawer-header {
    padding: 16px 20px;
    border-bottom: 1px solid $border-light;
  }

  .ant-drawer-header-title {
    flex: 1;
  }

  .ant-drawer-close {
    margin-inline-end: 0;
    color: $text-tertiary;

    &:hover {
      color: $text-strong;
      background: $bg-hover;
    }
  }

  .ant-drawer-body {
    padding: 0;
    background: $bg-muted;
  }
}
</style>
