<template>
  <div class="camera-container">
    <div class="camera-header">
      <button class="back-btn" @click="goBack">
        <span>←</span>
      </button>
      <span class="header-title">拍照扫描</span>
      <div class="header-right"></div>
    </div>

    <div class="camera-content">
      <div class="camera-view">
        <video
          v-if="hasCamera"
          ref="videoRef"
          class="video-element"
          autoplay
          playsinline
          muted
        ></video>
        <div v-else class="camera-placeholder">
          <div class="placeholder-icon">📷</div>
          <div class="placeholder-text">点击下方按钮选择图片</div>
        </div>

        <div v-if="showGuide" class="scan-guide">
          <div class="scan-frame">
            <div class="corner top-left"></div>
            <div class="corner top-right"></div>
            <div class="corner bottom-left"></div>
            <div class="corner bottom-right"></div>
            <div class="scan-line"></div>
          </div>
          <div class="scan-tips">
            <div class="tip-item">
              <span class="tip-icon">💡</span>
              <span class="tip-text">确保光线充足</span>
            </div>
            <div class="tip-item">
              <span class="tip-icon">📏</span>
              <span class="tip-text">保持表格平整</span>
            </div>
            <div class="tip-item">
              <span class="tip-icon">👁️</span>
              <span class="tip-text">正对表格拍摄</span>
            </div>
          </div>
        </div>
      </div>

      <div class="camera-controls">
        <button class="control-btn" @click="toggleGuide">
          <span v-if="showGuide">隐藏引导</span>
          <span v-else>显示引导</span>
        </button>
        <button class="control-btn" @click="chooseFromAlbum">
          <span>📂</span>
        </button>
      </div>

      <div class="capture-area">
        <button class="capture-btn" @click="captureImage">
          <div class="capture-inner"></div>
        </button>
      </div>
    </div>

    <div v-if="previewImage" class="preview-modal" @click="closePreview">
      <div class="preview-content" @click.stop>
        <img :src="previewImage" alt="预览" class="preview-img" />
        <div class="preview-actions">
          <button class="preview-btn cancel" @click="closePreview">重拍</button>
          <button class="preview-btn confirm" @click="confirmImage">确认</button>
        </div>
      </div>
    </div>

    <input
      ref="fileInputRef"
      type="file"
      accept="image/*"
      style="display: none"
      @change="handleFileSelect"
    />
  </div>
</template>

<script setup>
import { ref, onMounted, onUnmounted } from 'vue'
import { useRouter } from 'vue-router'

const router = useRouter()

const videoRef = ref(null)
const fileInputRef = ref(null)
const hasCamera = ref(false)
const showGuide = ref(true)
const previewImage = ref('')
const stream = ref(null)

const goBack = () => {
  router.back()
}

const initCamera = async () => {
  try {
    stream.value = await navigator.mediaDevices.getUserMedia({
      video: {
        facingMode: 'environment',
        width: { ideal: 1920 },
        height: { ideal: 1080 }
      }
    })

    if (videoRef.value) {
      videoRef.value.srcObject = stream.value
      hasCamera.value = true
    }
  } catch (error) {
    console.error('摄像头初始化失败:', error)
    hasCamera.value = false
  }
}

const captureImage = () => {
  if (hasCamera.value && videoRef.value) {
    const canvas = document.createElement('canvas')
    canvas.width = videoRef.value.videoWidth
    canvas.height = videoRef.value.videoHeight
    const ctx = canvas.getContext('2d')
    ctx.drawImage(videoRef.value, 0, 0)
    previewImage.value = canvas.toDataURL('image/jpeg', 0.9)
  } else {
    chooseFromAlbum()
  }
}

const chooseFromAlbum = () => {
  fileInputRef.value?.click()
}

const handleFileSelect = (event) => {
  const file = event.target.files[0]
  if (file) {
    const reader = new FileReader()
    reader.onload = (e) => {
      previewImage.value = e.target.result
    }
    reader.readAsDataURL(file)
  }
}

const closePreview = () => {
  previewImage.value = ''
}

const confirmImage = () => {
  sessionStorage.setItem('capturedImage', previewImage.value)
  router.push('/')
}

const toggleGuide = () => {
  showGuide.value = !showGuide.value
}

onMounted(() => {
  initCamera()
})

onUnmounted(() => {
  if (stream.value) {
    stream.value.getTracks().forEach(track => track.stop())
  }
})
</script>

<style scoped>
.camera-container {
  position: fixed;
  top: 0;
  left: 0;
  right: 0;
  bottom: 0;
  background: #000;
  display: flex;
  flex-direction: column;
  max-width: 480px;
  margin: 0 auto;
}

.camera-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 16px;
  padding-top: calc(16px + env(safe-area-inset-top));
  background: rgba(0, 0, 0, 0.5);
  position: relative;
  z-index: 10;
}

.back-btn {
  width: 40px;
  height: 40px;
  display: flex;
  align-items: center;
  justify-content: center;
  color: white;
  font-size: 24px;
  cursor: pointer;
}

.header-title {
  color: white;
  font-size: 18px;
  font-weight: 500;
}

.header-right {
  width: 40px;
}

.camera-content {
  flex: 1;
  display: flex;
  flex-direction: column;
  position: relative;
}

.camera-view {
  flex: 1;
  position: relative;
  background: #111;
}

.video-element {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.camera-placeholder {
  width: 100%;
  height: 100%;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  color: white;
}

.placeholder-icon {
  font-size: 64px;
  margin-bottom: 16px;
  opacity: 0.5;
}

.placeholder-text {
  opacity: 0.6;
  font-size: 14px;
}

.scan-guide {
  position: absolute;
  top: 0;
  left: 0;
  right: 0;
  bottom: 0;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  pointer-events: none;
}

.scan-frame {
  position: relative;
  width: 80%;
  aspect-ratio: 4/3;
  border-radius: 8px;
}

.corner {
  position: absolute;
  width: 32px;
  height: 32px;
  border-color: #5B8FF9;
  border-style: solid;
  border-width: 0;
}

.corner.top-left {
  top: 0;
  left: 0;
  border-top-width: 4px;
  border-left-width: 4px;
  border-top-left-radius: 8px;
}

.corner.top-right {
  top: 0;
  right: 0;
  border-top-width: 4px;
  border-right-width: 4px;
  border-top-right-radius: 8px;
}

.corner.bottom-left {
  bottom: 0;
  left: 0;
  border-bottom-width: 4px;
  border-left-width: 4px;
  border-bottom-left-radius: 8px;
}

.corner.bottom-right {
  bottom: 0;
  right: 0;
  border-bottom-width: 4px;
  border-right-width: 4px;
  border-bottom-right-radius: 8px;
}

.scan-line {
  position: absolute;
  left: 8px;
  right: 8px;
  height: 3px;
  background: linear-gradient(90deg, transparent, #5B8FF9, transparent);
  animation: scanMove 2s ease-in-out infinite;
}

@keyframes scanMove {
  0%, 100% {
    top: 8px;
    opacity: 0;
  }
  10% {
    opacity: 1;
  }
  90% {
    opacity: 1;
  }
  50% {
    top: calc(100% - 16px);
  }
}

.scan-tips {
  position: absolute;
  bottom: 40px;
  display: flex;
  flex-direction: column;
  gap: 12px;
  align-items: center;
}

.tip-item {
  display: flex;
  align-items: center;
  gap: 8px;
  background: rgba(0, 0, 0, 0.6);
  padding: 8px 16px;
  border-radius: 20px;
}

.tip-icon {
  font-size: 16px;
}

.tip-text {
  color: white;
  font-size: 14px;
}

.camera-controls {
  display: flex;
  justify-content: space-around;
  padding: 16px;
  background: rgba(0, 0, 0, 0.3);
}

.control-btn {
  padding: 10px 20px;
  background: rgba(255, 255, 255, 0.15);
  border-radius: 20px;
  color: white;
  font-size: 14px;
  cursor: pointer;
  border: none;
}

.capture-area {
  display: flex;
  justify-content: center;
  padding: 24px;
  padding-bottom: calc(24px + env(safe-area-inset-bottom));
  background: rgba(0, 0, 0, 0.5);
}

.capture-btn {
  width: 72px;
  height: 72px;
  border-radius: 50%;
  background: rgba(255, 255, 255, 0.2);
  border: 4px solid white;
  display: flex;
  align-items: center;
  justify-content: center;
  cursor: pointer;
  transition: transform 0.2s;
}

.capture-btn:active {
  transform: scale(0.95);
}

.capture-inner {
  width: 56px;
  height: 56px;
  border-radius: 50%;
  background: white;
}

.preview-modal {
  position: fixed;
  top: 0;
  left: 0;
  right: 0;
  bottom: 0;
  background: rgba(0, 0, 0, 0.95);
  display: flex;
  flex-direction: column;
  z-index: 1000;
  max-width: 480px;
  margin: 0 auto;
}

.preview-content {
  flex: 1;
  display: flex;
  flex-direction: column;
}

.preview-img {
  flex: 1;
  width: 100%;
  object-fit: contain;
}

.preview-actions {
  display: flex;
  gap: 16px;
  padding: 16px;
  padding-bottom: calc(16px + env(safe-area-inset-bottom));
  background: #000;
}

.preview-btn {
  flex: 1;
  padding: 14px;
  border-radius: 24px;
  font-size: 16px;
  font-weight: 500;
  cursor: pointer;
  border: none;
}

.preview-btn.cancel {
  background: #333;
  color: white;
}

.preview-btn.confirm {
  background: linear-gradient(135deg, #5B8FF9 0%, #7B68EE 100%);
  color: white;
}
</style>
