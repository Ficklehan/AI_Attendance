const { t } = require('../../utils/i18n')
const { ensureCountryConfigured } = require('../../utils/preferences')
const {
  isIosPlatform,
  extractTempImagePath,
  formatCameraError,
  shouldRecreateContext,
} = require('../../utils/cameraCapture')

const CAMERA_ID = 'camera'
const INIT_DONE_TIMEOUT_MS = 8000
const IOS_READY_DELAY_MS = 500

Page({
  data: {
    flashMode: 'off',
    cameraReady: false,
    initHint: '',
    texts: {}
  },

  onLoad: function (options) {
    this._fromChat = options && options.from === 'chat'
    this._isIos = isIosPlatform()
    this._sameLayerRender = true
    this._cameraInitDone = false
    this._initDoneTimer = null
    this._readyDelayTimer = null
    this._photoInProgress = false
    this._retriedPhoto = false
    this._iosAutoFallback = this._isIos
    if (!ensureCountryConfigured()) {
      return
    }
    this.refreshTexts()
    this.setData({ initHint: t('camera.starting') })
    this.requestCameraPermission(() => {
      this.armInitDoneTimeout()
    })
  },

  onShow: function () {
    if (this._needReinitContext) {
      this._needReinitContext = false
      this._cameraInitDone = false
      this.setData({ initHint: t('camera.starting'), cameraReady: false })
      this.armInitDoneTimeout()
    }
  },

  onUnload: function () {
    this.clearInitDoneTimeout()
    this.clearReadyDelayTimer()
  },

  goBack: function () {
    tt.navigateBack()
  },

  refreshTexts: function () {
    this.setData({
      texts: {
        title: t('camera.title'),
        scanHint: this._isIos ? t('camera.iosSystemCameraHint') : t('camera.scanHint'),
        scanGuide: t('camera.scanGuide'),
        guideTitle: t('camera.guideTitle'),
        tipLight: t('camera.tipLight'),
        tipFlat: t('camera.tipFlat'),
        tipAngle: t('camera.tipAngle'),
        gallery: t('camera.gallery'),
        systemCamera: t('camera.systemCamera'),
        flashOn: t('camera.flashOn'),
        flashOff: t('camera.flashOff')
      }
    })
    tt.setNavigationBarTitle({ title: t('camera.title') })
  },

  clearInitDoneTimeout: function () {
    if (this._initDoneTimer) {
      clearTimeout(this._initDoneTimer)
      this._initDoneTimer = null
    }
  },

  clearReadyDelayTimer: function () {
    if (this._readyDelayTimer) {
      clearTimeout(this._readyDelayTimer)
      this._readyDelayTimer = null
    }
  },

  armInitDoneTimeout: function () {
    this.clearInitDoneTimeout()
    this._initDoneTimer = setTimeout(() => {
      if (!this._cameraInitDone) {
        this.setData({
          cameraReady: false,
          initHint: t('camera.previewUnavailable')
        })
      }
    }, INIT_DONE_TIMEOUT_MS)
  },

  markCameraReady: function () {
    this._cameraInitDone = true
    this.clearInitDoneTimeout()
    this.clearReadyDelayTimer()

    const applyReady = () => {
      this.refreshCameraContext(true)
      this.setData({ cameraReady: true, initHint: '' })
    }

    if (this._isIos) {
      this._readyDelayTimer = setTimeout(applyReady, IOS_READY_DELAY_MS)
      return
    }
    applyReady()
  },

  refreshCameraContext: function (forceNew) {
    if (!forceNew && this.cameraContext && !shouldRecreateContext(this._isIos)) {
      return !!this.cameraContext
    }
    this.cameraContext = null
    try {
      if (typeof tt.createCameraContext !== 'function') {
        return false
      }
      this.cameraContext = tt.createCameraContext(CAMERA_ID)
      return !!this.cameraContext
    } catch (e) {
      console.warn('createCameraContext failed', e)
      return false
    }
  },

  requestCameraPermission: function (onGranted) {
    const done = typeof onGranted === 'function' ? onGranted : function () {}

    tt.getSetting({
      success: (res) => {
        const auth = res.authSetting && res.authSetting['scope.camera']
        if (auth === true) {
          done()
          return
        }
        if (auth === false) {
          this.setData({
            cameraReady: false,
            initHint: t('camera.permissionDenied')
          })
          return
        }
        tt.authorize({
          scope: 'scope.camera',
          success: () => done(),
          fail: () => {
            this.setData({
              cameraReady: false,
              initHint: t('camera.permissionDenied')
            })
          }
        })
      },
      fail: () => done()
    })
  },

  openCameraSettings: function () {
    tt.openSetting({
      success: (res) => {
        if (res.authSetting && res.authSetting['scope.camera']) {
          this._cameraInitDone = false
          this.setData({ initHint: t('camera.starting'), cameraReady: false })
          this.armInitDoneTimeout()
        }
      }
    })
  },

  onInitHintTap: function () {
    const hint = this.data.initHint || ''
    if (hint === t('camera.permissionDenied')) {
      this.openCameraSettings()
      return
    }
    if (hint === t('camera.previewUnavailable') || hint === t('camera.unsupported')) {
      this.chooseFromCamera()
    }
  },

  onCameraInserted: function (e) {
    const detail = (e && e.detail) || {}
    console.log('camera inserted', detail)
    this._sameLayerRender = detail.isRenderInSameLayer !== false
  },

  onCameraInitDone: function (e) {
    console.log('camera init done', e && e.detail)
    this._retriedPhoto = false
    this.markCameraReady()
  },

  onCameraStop: function () {
    this.cameraContext = null
    this._cameraInitDone = false
    this.setData({ cameraReady: false, initHint: t('camera.starting') })
    this._needReinitContext = true
  },

  toggleFlash: function () {
    this.setData({
      flashMode: this.data.flashMode === 'off' ? 'torch' : 'off'
    })
  },

  takePhoto: function () {
    if (this._photoInProgress || this._chooseInProgress) {
      return
    }

    // iOS 上组件 takePhoto 在非同层渲染时几乎必败，快门直接走系统相机。
    if (this._isIos) {
      this.chooseFromCamera()
      return
    }

    if (!this.data.cameraReady) {
      tt.showToast({ title: t('camera.warming'), icon: 'none' })
      return
    }

    if (!this.refreshCameraContext(true)) {
      this.handleCaptureFailure(t('camera.notReady'), { preferSystemCamera: true })
      return
    }

    this.invokeTakePhoto('high')
  },

  invokeTakePhoto: function (quality, allowRetry) {
    if (!this.cameraContext) {
      this.handleCaptureFailure(t('camera.notReady'), { preferSystemCamera: true })
      return
    }

    this._photoInProgress = true
    const options = { quality: quality || 'high' }
    if (!this._isIos) {
      options.selfieMirror = false
    }

    this.cameraContext.takePhoto({
      quality: options.quality,
      selfieMirror: options.selfieMirror,
      success: (res) => {
        const path = extractTempImagePath(res)
        this._photoInProgress = false
        if (path) {
          this.processPhoto(path)
          return
        }
        console.warn('takePhoto empty path', res)
        if (allowRetry !== false && quality === 'high') {
          this.invokeTakePhoto('medium', false)
          return
        }
        this.handleCaptureFailure(t('camera.photoFail'), { preferSystemCamera: true })
      },
      fail: (error) => {
        this._photoInProgress = false
        console.error('takePhoto fail', error)
        const msg = formatCameraError(error)
        if (msg.indexOf('not found') >= 0 && !this._retriedPhoto) {
          this._retriedPhoto = true
          this._cameraInitDone = false
          this.setData({ cameraReady: false, initHint: t('camera.starting') })
          this.armInitDoneTimeout()
          setTimeout(() => this.takePhoto(), 600)
          return
        }
        if (allowRetry !== false && quality === 'high') {
          this.invokeTakePhoto('medium', false)
          return
        }
        this.handleCaptureFailure(t('camera.photoFail'), { preferSystemCamera: true, error: msg })
      }
    })
  },

  handleCaptureFailure: function (reason, options) {
    const opts = options || {}
    if (this._iosAutoFallback || this._isIos) {
      this.chooseFromCamera({ silent: true, fromFallback: true })
      return
    }
    this.fallbackCapture(reason, opts.preferSystemCamera !== false)
  },

  fallbackCapture: function (reason, preferCamera) {
    tt.showModal({
      title: t('camera.photoFail'),
      content: reason + '\n' + t('camera.fallbackHint'),
      confirmText: preferCamera ? t('camera.systemCamera') : t('camera.openAlbum'),
      cancelText: t('common.cancel'),
      success: (res) => {
        if (!res.confirm) return
        if (preferCamera) {
          this.chooseFromCamera()
        } else {
          this.chooseFromGallery()
        }
      }
    })
  },

  processPhoto: function (imagePath) {
    if (this._fromChat) {
      const pages = getCurrentPages()
      const prev = pages && pages.length >= 2 ? pages[pages.length - 2] : null
      if (prev && typeof prev.sendImageForRecognition === 'function') {
        prev.sendImageForRecognition(imagePath)
        tt.navigateBack()
        return
      }
    }
    const encoded = encodeURIComponent(imagePath)
    tt.navigateTo({
      url: `/pages/recognizing/index?imagePath=${encoded}`
    })
  },

  chooseFromCamera: function (options) {
    const opts = options || {}
    if (this._chooseInProgress) {
      return
    }
    this._chooseInProgress = true
    tt.chooseImage({
      count: 1,
      sizeType: ['compressed'],
      sourceType: ['camera'],
      success: (res) => {
        this._chooseInProgress = false
        if (res.tempFilePaths && res.tempFilePaths.length > 0) {
          this.processPhoto(res.tempFilePaths[0])
        } else if (!opts.silent) {
          tt.showToast({ title: t('camera.photoFail'), icon: 'none' })
        } else if (!opts.fromFallback) {
          this.fallbackCapture(t('camera.photoFail'), true)
        }
      },
      fail: (error) => {
        this._chooseInProgress = false
        console.error('系统相机选图失败', error)
        if (opts.silent && opts.fromFallback) {
          this.fallbackCapture(t('camera.photoFail'), true)
          return
        }
        tt.showToast({ title: t('camera.photoFail'), icon: 'none' })
      }
    })
  },

  chooseFromGallery: function () {
    tt.chooseImage({
      count: 1,
      sizeType: ['compressed'],
      sourceType: ['album'],
      success: (res) => {
        if (res.tempFilePaths && res.tempFilePaths.length > 0) {
          this.processPhoto(res.tempFilePaths[0])
        }
      },
      fail: (error) => {
        console.error('选择图片失败:', error)
      }
    })
  },

  onCameraError: function (error) {
    console.error('相机错误:', error)
    this.cameraContext = null
    this._cameraInitDone = false
    this.setData({
      cameraReady: false,
      initHint: t('camera.cameraFail')
    })
    tt.showToast({ title: t('camera.cameraFail'), icon: 'none' })
  }
})
