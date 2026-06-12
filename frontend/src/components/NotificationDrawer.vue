<template>
  <a-drawer
    v-model:open="visible"
    :title="$t('notification.title')"
    placement="right"
    width="420"
    class="notification-drawer"
    @close="handleClose"
  >
    <div class="notification-toolbar">
      <a-button size="small" :loading="loading" @click="fetchList">
        {{ $t('notification.refresh') }}
      </a-button>
      <a-button size="small" :disabled="!hasUnread" @click="handleReadAll">
        {{ $t('notification.markAllRead') }}
      </a-button>
      <a-button size="small" danger :disabled="items.length === 0" @click="handleClearAll">
        {{ $t('notification.clearAll') }}
      </a-button>
    </div>

    <a-spin :spinning="loading && items.length === 0">
      <a-empty v-if="!loading && items.length === 0" :description="$t('notification.empty')" />
      <div v-else class="notification-list">
        <div
          v-for="item in items"
          :key="item.id"
          class="notification-item"
          :class="{ 'notification-item--unread': !item.read }"
          @click="handleClick(item)"
        >
          <div class="notification-item__title">{{ item.title }}</div>
          <div class="notification-item__body">{{ item.body }}</div>
          <div class="notification-item__time">{{ formatTime(item.createdAt) }}</div>
        </div>
      </div>
    </a-spin>
  </a-drawer>
</template>

<script setup>
import { computed, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import { Modal, message } from 'ant-design-vue'
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

const { t } = useI18n()
const router = useRouter()

const visible = computed({
  get: () => props.open,
  set: (v) => emit('update:open', v),
})

const loading = ref(false)
const items = ref([])

const hasUnread = computed(() => items.value.some((n) => !n.read))

watch(
  () => props.open,
  (open) => {
    if (open) fetchList()
  }
)

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
      const path = url.pathname.replace(/^\/attendance/, '') || '/tasks'
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
.notification-toolbar {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-bottom: 16px;
}

.notification-list {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.notification-item {
  padding: 12px;
  border-radius: 8px;
  border: 1px solid #f0f0f0;
  cursor: pointer;
  transition: background 0.15s;

  &:hover {
    background: #fafafa;
  }

  &--unread {
    border-color: #91caff;
    background: #f0f7ff;
  }
}

.notification-item__title {
  font-weight: 600;
  margin-bottom: 4px;
}

.notification-item__body {
  font-size: 13px;
  color: #595959;
  white-space: pre-wrap;
  margin-bottom: 6px;
}

.notification-item__time {
  font-size: 12px;
  color: #8c8c8c;
}
</style>
