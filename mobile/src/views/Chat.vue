<template>
  <div class="chat-container">
    <div class="chat-header">
      <span class="header-title">AI助手</span>
      <span class="header-subtitle">智能识别 · 随时答疑</span>
    </div>

    <div class="chat-messages" ref="messagesRef">
      <div v-if="messages.length === 0" class="empty-state">
        <div class="empty-icon">🤖</div>
        <div class="empty-text">我是AI考勤助手，有什么可以帮助你的？</div>
        <div class="quick-questions">
          <button
            v-for="q in quickQuestions"
            :key="q"
            class="quick-btn"
            @click="sendQuickQuestion(q)"
          >
            {{ q }}
          </button>
        </div>
      </div>

      <div
        v-for="(msg, index) in messages"
        :key="index"
        :class="['message-item', msg.role]"
      >
        <div class="message-avatar">
          <span v-if="msg.role === 'user'">👤</span>
          <span v-else>🤖</span>
        </div>
        <div class="message-content">
          <div v-if="msg.type === 'text'" class="text-content">
            {{ msg.content }}
          </div>
          <div v-else-if="msg.type === 'image'" class="image-content">
            <img :src="msg.content" alt="图片" @click="previewImage(msg.content)" />
          </div>
          <div v-if="msg.loading" class="loading-dots">
            <span></span>
            <span></span>
            <span></span>
          </div>
        </div>
      </div>
    </div>

    <div class="chat-input-area">
      <div class="input-tools">
        <button class="tool-btn" @click="chooseImage">
          <span>📷</span>
        </button>
      </div>
      <div class="input-wrapper">
        <input
          v-model="inputText"
          type="text"
          class="chat-input"
          placeholder="输入消息..."
          @keyup.enter="sendMessage"
        />
      </div>
      <button class="send-btn" :disabled="!inputText.trim() && !pendingImage" @click="sendMessage">
        发送
      </button>
    </div>

    <input
      ref="imageInputRef"
      type="file"
      accept="image/*"
      style="display: none"
      @change="handleImageSelect"
    />

    <div v-if="previewImg" class="image-preview" @click="closePreview">
      <img :src="previewImg" alt="预览" />
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted, nextTick, watch } from 'vue'
import { chatApi } from '@/api'

const messagesRef = ref(null)
const imageInputRef = ref(null)

const messages = ref([])
const inputText = ref('')
const pendingImage = ref(null)
const previewImg = ref('')

const quickQuestions = [
  '如何使用拍照识别？',
  '支持哪些格式？',
  '识别准确率怎样？'
]

const scrollToBottom = () => {
  nextTick(() => {
    if (messagesRef.value) {
      messagesRef.value.scrollTop = messagesRef.value.scrollHeight
    }
  })
}

const addMessage = (role, content, type = 'text', loading = false) => {
  messages.value.push({ role, content, type, loading })
  scrollToBottom()
}

const sendQuickQuestion = (question) => {
  inputText.value = question
  sendMessage()
}

const chooseImage = () => {
  imageInputRef.value?.click()
}

const handleImageSelect = (event) => {
  const file = event.target.files[0]
  if (file) {
    const reader = new FileReader()
    reader.onload = (e) => {
      pendingImage.value = e.target.result
    }
    reader.readAsDataURL(file)
  }
}

const sendMessage = async () => {
  const text = inputText.value.trim()
  const hasImage = !!pendingImage.value

  if (!text && !hasImage) return

  if (hasImage) {
    addMessage('user', pendingImage.value, 'image')
  }
  if (text) {
    addMessage('user', text, 'text')
  }

  inputText.value = ''
  pendingImage.value = null

  const assistantMsg = { role: 'assistant', content: '', type: 'text', loading: true }
  messages.value.push(assistantMsg)
  scrollToBottom()

  try {
    let result
    if (hasImage) {
      const blob = await (await fetch(pendingImage.value || messages.value[messages.value.length - 2].content)).blob()
      const file = new File([blob], 'image.jpg', { type: 'image/jpeg' })
      result = await chatApi.analyzeImage(file)
    } else {
      result = await chatApi.sendMessage({ message: text })
    }

    assistantMsg.loading = false
    assistantMsg.content = result?.data?.message || '识别完成，请查看结果'
  } catch (error) {
    assistantMsg.loading = false
    assistantMsg.content = '抱歉，处理失败，请稍后重试'
    console.error(error)
  }
}

const previewImage = (src) => {
  previewImg.value = src
}

const closePreview = () => {
  previewImg.value = ''
}

watch(messages, () => {
  scrollToBottom()
}, { deep: true })

onMounted(() => {
  addMessage('assistant', '你好！我是AI考勤助手，你可以直接和我对话，或者发送图片让我识别考勤表。')
})
</script>

<style scoped>
.chat-container {
  height: 100vh;
  display: flex;
  flex-direction: column;
  background: var(--bg-color);
  max-width: 480px;
  margin: 0 auto;
}

.chat-header {
  background: white;
  padding: 16px;
  padding-top: calc(16px + env(safe-area-inset-top));
  text-align: center;
  border-bottom: 1px solid var(--border-color);
}

.header-title {
  display: block;
  font-size: 18px;
  font-weight: 600;
  color: var(--text-primary);
  margin-bottom: 4px;
}

.header-subtitle {
  font-size: 13px;
  color: var(--text-muted);
}

.chat-messages {
  flex: 1;
  overflow-y: auto;
  padding: 16px;
}

.empty-state {
  display: flex;
  flex-direction: column;
  align-items: center;
  padding: 48px 24px;
}

.empty-icon {
  font-size: 64px;
  margin-bottom: 16px;
  opacity: 0.5;
}

.empty-text {
  text-align: center;
  color: var(--text-muted);
  font-size: 14px;
  margin-bottom: 24px;
}

.quick-questions {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  justify-content: center;
}

.quick-btn {
  padding: 8px 16px;
  background: white;
  border: 1px solid var(--border-color);
  border-radius: 16px;
  font-size: 13px;
  color: var(--text-secondary);
  cursor: pointer;
  transition: all 0.2s;
}

.quick-btn:active {
  background: var(--bg-color);
}

.message-item {
  display: flex;
  gap: 12px;
  margin-bottom: 20px;
}

.message-item.user {
  flex-direction: row-reverse;
}

.message-avatar {
  width: 40px;
  height: 40px;
  border-radius: 50%;
  background: var(--bg-color);
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
  font-size: 20px;
}

.message-content {
  max-width: 70%;
}

.text-content {
  padding: 12px 16px;
  border-radius: 16px;
  font-size: 15px;
  line-height: 1.6;
}

.message-item.user .text-content {
  background: linear-gradient(135deg, #5B8FF9 0%, #7B68EE 100%);
  color: white;
  border-bottom-right-radius: 4px;
}

.message-item.assistant .text-content {
  background: white;
  color: var(--text-primary);
  border-bottom-left-radius: 4px;
}

.image-content img {
  max-width: 100%;
  border-radius: 12px;
  cursor: pointer;
}

.loading-dots {
  display: inline-flex;
  gap: 4px;
  padding: 12px 16px;
}

.loading-dots span {
  width: 8px;
  height: 8px;
  background: var(--text-muted);
  border-radius: 50%;
  animation: dotBounce 1.4s infinite ease-in-out both;
}

.loading-dots span:nth-child(1) {
  animation-delay: -0.32s;
}

.loading-dots span:nth-child(2) {
  animation-delay: -0.16s;
}

@keyframes dotBounce {
  0%, 80%, 100% {
    transform: scale(0);
    opacity: 0.5;
  }
  40% {
    transform: scale(1);
    opacity: 1;
  }
}

.chat-input-area {
  display: flex;
  align-items: flex-end;
  gap: 8px;
  padding: 12px 16px;
  padding-bottom: calc(12px + env(safe-area-inset-bottom));
  background: white;
  border-top: 1px solid var(--border-color);
}

.input-tools {
  display: flex;
  gap: 8px;
}

.tool-btn {
  width: 40px;
  height: 40px;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 24px;
  cursor: pointer;
  border-radius: 8px;
  background: var(--bg-color);
}

.input-wrapper {
  flex: 1;
  display: flex;
  align-items: center;
  background: var(--bg-color);
  border-radius: 20px;
  padding: 8px 16px;
}

.chat-input {
  flex: 1;
  border: none;
  background: transparent;
  font-size: 15px;
  outline: none;
}

.send-btn {
  padding: 10px 20px;
  background: linear-gradient(135deg, #5B8FF9 0%, #7B68EE 100%);
  color: white;
  border: none;
  border-radius: 20px;
  font-size: 14px;
  font-weight: 500;
  cursor: pointer;
  transition: opacity 0.2s;
}

.send-btn:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}

.send-btn:active:not(:disabled) {
  opacity: 0.8;
}

.image-preview {
  position: fixed;
  top: 0;
  left: 0;
  right: 0;
  bottom: 0;
  background: rgba(0, 0, 0, 0.95);
  display: flex;
  align-items: center;
  justify-content: center;
  z-index: 1000;
  max-width: 480px;
  margin: 0 auto;
}

.image-preview img {
  max-width: 100%;
  max-height: 100%;
  object-fit: contain;
}
</style>
