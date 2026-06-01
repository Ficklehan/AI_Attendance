const { t, getLocale } = require('../../utils/i18n')

const STORAGE_KEY = '_imagePreviewSession'
const MIN_SCALE = 1
const MAX_SCALE = 5
const ZOOM_BUTTON_FACTOR = 1.35
const DOUBLE_TAP_MS = 280
const DOUBLE_TAP_SCALE = 2.5

const TEXT_FALLBACK = {
  'zh-CN': {
    title: '查看图片',
    rotate: '旋转',
    close: '关闭',
    loadError: '图片加载失败',
    prev: '上一张',
    next: '下一张',
    zoomIn: '放大',
    zoomOut: '缩小',
    zoomReset: '还原',
    gestureHint: '左右滑动切换 · 双指捏合缩放 · 双击放大'
  },
  'en-US': {
    title: 'Image preview',
    rotate: 'Rotate',
    close: 'Close',
    loadError: 'Failed to load image',
    prev: 'Previous',
    next: 'Next',
    zoomIn: 'Zoom in',
    zoomOut: 'Zoom out',
    zoomReset: 'Reset',
    gestureHint: 'Swipe to switch · Pinch to zoom · Double-tap'
  },
  'es-ES': {
    title: 'Vista previa',
    rotate: 'Rotar',
    close: 'Cerrar',
    loadError: 'Error al cargar la imagen',
    prev: 'Anterior',
    next: 'Siguiente',
    zoomIn: 'Acercar',
    zoomOut: 'Alejar',
    zoomReset: 'Restablecer',
    gestureHint: 'Deslizar para cambiar · Pellizcar para zoom · Doble toque'
  }
}

function getAppSafe() {
  try {
    return getApp()
  } catch (e) {
    return null
  }
}

function readSession() {
  try {
    const app = getAppSafe()
    if (app && app.globalData.imagePreviewSession && app.globalData.imagePreviewSession.images) {
      return app.globalData.imagePreviewSession
    }
  } catch (e) {
    // ignore
  }
  try {
    const stored = tt.getStorageSync(STORAGE_KEY)
    if (stored && stored.images && stored.images.length) {
      return stored
    }
  } catch (e) {
    // ignore
  }
  return null
}

function touchDistance(a, b) {
  const dx = a.clientX - b.clientX
  const dy = a.clientY - b.clientY
  return Math.sqrt(dx * dx + dy * dy)
}

function touchCenter(a, b) {
  return {
    x: (a.clientX + b.clientX) / 2,
    y: (a.clientY + b.clientY) / 2
  }
}

function clamp(value, min, max) {
  return Math.max(min, Math.min(max, value))
}

function pickText(key) {
  const fullKey = `imagePreview.${key}`
  const translated = t(fullKey)
  if (translated && translated !== fullKey) {
    return translated
  }
  const locale = getLocale()
  const bucket = TEXT_FALLBACK[locale] || TEXT_FALLBACK['zh-CN']
  return bucket[key] || TEXT_FALLBACK['zh-CN'][key] || fullKey
}

function buildTexts() {
  return {
    title: pickText('title'),
    rotate: pickText('rotate'),
    close: pickText('close'),
    loadError: pickText('loadError'),
    prev: pickText('prev'),
    next: pickText('next'),
    zoomIn: pickText('zoomIn'),
    zoomOut: pickText('zoomOut'),
    zoomReset: pickText('zoomReset'),
    gestureHint: pickText('gestureHint')
  }
}

Page({
  data: {
    images: [],
    currentIndex: 0,
    rotateDeg: 0,
    fileName: '',
    counterText: '',
    loadError: false,
    scale: 1,
    offsetX: 0,
    offsetY: 0,
    zoomPercent: 100,
    useTransform: false,
    transformStyle: '',
    imageTransformClass: '',
    swiperDisabled: false,
    texts: {}
  },

  onLoad: function (options) {
    try {
      const sys = tt.getSystemInfoSync()
      this._viewport = {
        width: sys.windowWidth || 375,
        height: Math.floor((sys.windowHeight || 667) * 0.58)
      }
    } catch (e) {
      this._viewport = { width: 375, height: 400 }
    }
    this._gesture = null
    this._lastTapEnd = 0
    this._captureGesture = false

    const session = readSession()
    const images = ((session && session.images) || []).map((item, index) => ({
      ...item,
      key: item.key || `img-${index}`,
      fileName: item.fileName || item.key || `image-${index + 1}`
    }))
    let currentIndex = Number(options.index)
    if (Number.isNaN(currentIndex) && session && session.index != null) {
      currentIndex = session.index
    }
    if (Number.isNaN(currentIndex)) {
      currentIndex = 0
    }
    currentIndex = Math.max(0, Math.min(currentIndex, Math.max(0, images.length - 1)))

    this.setData({
      images,
      currentIndex,
      texts: buildTexts()
    })
    tt.setNavigationBarTitle({ title: pickText('title') })

    if (!images.length) {
      tt.showToast({ title: pickText('loadError'), icon: 'none' })
      setTimeout(() => tt.navigateBack(), 1200)
      return
    }

    this.updateMeta()
    this.resetZoom()
  },

  onShow: function () {
    this.setData({ texts: buildTexts() })
  },

  onUnload: function () {
    try {
      const app = getAppSafe()
      if (app) {
        app.globalData.imagePreviewSession = null
      }
      tt.removeStorageSync(STORAGE_KEY)
    } catch (e) {
      // ignore
    }
  },

  clampOffset: function (scale, offsetX, offsetY) {
    const vp = this._viewport || { width: 375, height: 400 }
    const maxX = Math.max(0, (vp.width * (scale - 1)) / 2)
    const maxY = Math.max(0, (vp.height * (scale - 1)) / 2)
    return {
      offsetX: clamp(offsetX, -maxX, maxX),
      offsetY: clamp(offsetY, -maxY, maxY)
    }
  },

  applyZoom: function (scale, offsetX, offsetY, options) {
    const opts = options || {}
    let nextScale = clamp(scale, MIN_SCALE, MAX_SCALE)
    let nextOffsetX = offsetX
    let nextOffsetY = offsetY
    if (nextScale <= 1.02) {
      nextScale = 1
      nextOffsetX = 0
      nextOffsetY = 0
    } else if (!opts.skipClamp) {
      const clamped = this.clampOffset(nextScale, nextOffsetX, nextOffsetY)
      nextOffsetX = clamped.offsetX
      nextOffsetY = clamped.offsetY
    }
    const useTransform = nextScale > 1.02
    const transformStyle = useTransform
      ? `transform: translate(${nextOffsetX}px, ${nextOffsetY}px) scale(${nextScale});`
      : ''
    this.setData({
      scale: nextScale,
      offsetX: nextOffsetX,
      offsetY: nextOffsetY,
      zoomPercent: Math.round(nextScale * 100),
      useTransform,
      transformStyle,
      imageTransformClass: useTransform ? 'preview-image-transform' : '',
      swiperDisabled: useTransform
    })
  },

  resetZoom: function () {
    this.applyZoom(1, 0, 0, { skipClamp: true })
    this._captureGesture = false
  },

  updateMeta: function () {
    const img = this.data.images[this.data.currentIndex] || {}
    const total = this.data.images.length
    const current = this.data.currentIndex + 1
    this.setData({
      fileName: img.fileName || '',
      counterText: total > 1 ? `${current} / ${total}` : '',
      loadError: false
    })
  },

  onSwiperChange: function (e) {
    const detail = e.detail || {}
    const nextIndex = typeof detail.current === 'number' ? detail.current : 0
    if (nextIndex === this.data.currentIndex) {
      return
    }
    this._gesture = null
    this._captureGesture = false
    this.setData({
      currentIndex: nextIndex,
      rotateDeg: 0
    }, () => {
      this.resetZoom()
      this.updateMeta()
    })
  },

  onImageLoad: function () {
    this.setData({ loadError: false })
  },

  onImageError: function (e) {
    const idx = Number(e.currentTarget.dataset.index)
    const index = Number.isNaN(idx) ? this.data.currentIndex : idx
    const images = this.data.images.slice()
    const img = images[index]
    if (!img) {
      this.setData({ loadError: true })
      return
    }
    if (img.url && img.displayUrl !== img.url) {
      images[index] = { ...img, displayUrl: img.url }
      this.setData({ images, loadError: false })
      return
    }
    if (index === this.data.currentIndex) {
      this.setData({ loadError: true })
    }
  },

  onRotate: function () {
    const rotateDeg = (this.data.rotateDeg + 90) % 360
    this.resetZoom()
    this.setData({ rotateDeg })
  },

  onZoomIn: function () {
    const scale = clamp(this.data.scale * ZOOM_BUTTON_FACTOR, MIN_SCALE, MAX_SCALE)
    this.applyZoom(scale, this.data.offsetX, this.data.offsetY)
  },

  onZoomOut: function () {
    const scale = clamp(this.data.scale / ZOOM_BUTTON_FACTOR, MIN_SCALE, MAX_SCALE)
    this.applyZoom(scale, this.data.offsetX, this.data.offsetY)
  },

  onZoomReset: function () {
    this.resetZoom()
  },

  onTouchStart: function (e) {
    const touches = e.touches || []
    if (touches.length >= 2) {
      this._captureGesture = true
      this._gesture = {
        type: 'pinch',
        startDistance: touchDistance(touches[0], touches[1]),
        startScale: this.data.scale,
        startOffsetX: this.data.offsetX,
        startOffsetY: this.data.offsetY,
        startCenter: touchCenter(touches[0], touches[1])
      }
      this._lastTapEnd = 0
      return
    }
    if (touches.length === 1) {
      const touch = touches[0]
      if (this.data.scale > 1.02) {
        this._captureGesture = true
        this._gesture = {
          type: 'pan',
          startX: touch.clientX,
          startY: touch.clientY,
          baseOffsetX: this.data.offsetX,
          baseOffsetY: this.data.offsetY
        }
      } else {
        this._captureGesture = false
        this._gesture = {
          type: 'tap',
          startX: touch.clientX,
          startY: touch.clientY
        }
      }
    }
  },

  onTouchMove: function (e) {
    if (!this._captureGesture || !this._gesture) {
      return
    }
    const touches = e.touches || []
    const gesture = this._gesture
    if (gesture.type === 'pinch' && touches.length >= 2) {
      const distance = touchDistance(touches[0], touches[1])
      if (!gesture.startDistance) {
        return
      }
      const scale = gesture.startScale * (distance / gesture.startDistance)
      const center = touchCenter(touches[0], touches[1])
      const offsetX = gesture.startOffsetX + (center.x - gesture.startCenter.x)
      const offsetY = gesture.startOffsetY + (center.y - gesture.startCenter.y)
      this.applyZoom(scale, offsetX, offsetY, { skipClamp: true })
      return
    }
    if (gesture.type === 'pan' && touches.length === 1) {
      const touch = touches[0]
      const offsetX = gesture.baseOffsetX + (touch.clientX - gesture.startX)
      const offsetY = gesture.baseOffsetY + (touch.clientY - gesture.startY)
      this.applyZoom(this.data.scale, offsetX, offsetY, { skipClamp: true })
    }
  },

  onTouchEnd: function () {
    const gesture = this._gesture
    if (!gesture) {
      this._captureGesture = false
      return
    }
    if (gesture.type === 'tap') {
      const now = Date.now()
      if (now - this._lastTapEnd < DOUBLE_TAP_MS) {
        if (this.data.scale > 1.05) {
          this.resetZoom()
        } else {
          this.applyZoom(DOUBLE_TAP_SCALE, 0, 0)
        }
        this._lastTapEnd = 0
      } else {
        this._lastTapEnd = now
      }
    }
    if (this.data.scale <= 1.02) {
      this.resetZoom()
    } else {
      this.applyZoom(this.data.scale, this.data.offsetX, this.data.offsetY)
    }
    this._gesture = null
    this._captureGesture = false
  },

  goToIndex: function (nextIndex) {
    if (this.data.images.length <= 1) {
      return
    }
    const total = this.data.images.length
    const index = ((nextIndex % total) + total) % total
    if (index === this.data.currentIndex) {
      return
    }
    this.setData({
      currentIndex: index,
      rotateDeg: 0
    }, () => {
      this.resetZoom()
      this.updateMeta()
    })
  },

  onPrev: function () {
    this.goToIndex(this.data.currentIndex - 1)
  },

  onNext: function () {
    this.goToIndex(this.data.currentIndex + 1)
  },

  onClose: function () {
    tt.navigateBack()
  }
})
