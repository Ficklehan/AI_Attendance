const { t } = require('../../utils/i18n')
const { ensureCountryConfigured } = require('../../utils/preferences')

const CAMERA_ID = 'camera'
const INIT_DONE_TIMEOUT_MS = 8000

Page({
  data: {
    flashMode: 'off',
    cameraReady: false,
    initHint: '',
    texts: {}
  },

  onLoad: function (options) {
    this._fromChat = options && options.from === 'chat'
    this._cameraInitDone = false
    this._initDoneTimer = null
    if (!ensureCountryConfigured()) {
      return
    }
    this.refreshTexts()
    this.setData({ initHint: t('camera.starting') })
    this.requestCameraPermission(() => {
      this.armInitDoneTimeout()
    })
  },

  onReady: function () {
    this._pageReady = true
    try {
      this.cameraContext = tt.createCameraContext(CAMERA_ID)
    } catch (e) {
      console.warn('createCameraContext onReady failed', e)
    }
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
  },

  goBack: function () {
    tt.navigateBack()
  },

  refreshTexts: function () {
    this.setData({
      texts: {
        title: t('camera.title'),
        scanHint: t('camera.scanHint'),
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
    try {
      this.cameraContext = tt.createCameraContext(CAMERA_ID)
    } catch (e) {
      console.warn('createCameraContext failed', e)
    }
    this.setData({ cameraReady: true, initHint: '' })
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

  ensureCameraContext: function () {
    if (this.cameraContext) {
      return true
    }
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

  takePhoto: function () {
    if (!this.data.cameraReady) {
      tt.showToast({ title: t('camera.warming'), icon: 'none' })
      return
    }

    if (!this.ensureCameraContext()) {
      this.fallbackCapture(t('camera.notReady'), true)
      return
    }

    this.cameraContext.takePhoto({
      quality: 'high',
      selfieMirror: false,
      success: (res) => {
        const path = res && (res.tempImagePath || res.tempFilePath)
        if (path) {
          this.processPhoto(path)
          return
        }
        console.warn('takePhoto empty path', res)
        this.fallbackCapture(t('camera.photoFail'), true)
      },
      fail: (error) => {
        console.error('takePhoto fail', error)
        const msg = [
          error && error.errMsg,
          error && error.errString,
          error && error.errNo
        ].filter(Boolean).join(' ')
        if (msg.indexOf('not found') >= 0 && !this._retriedPhoto) {
          this._retriedPhoto = true
          this._cameraInitDone = false
          this.setData({ cameraReady: false, initHint: t('camera.starting') })
          this.armInitDoneTimeout()
          setTimeout(() => this.takePhoto(), 600)
          return
        }
        this.fallbackCapture(t('camera.photoFail'), true)
      }
    })
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

  chooseFromCamera: function () {
    tt.chooseImage({
      count: 1,
      sizeType: ['compressed'],
      sourceType: ['camera'],
      success: (res) => {
        if (res.tempFilePaths && res.tempFilePaths.length > 0) {
          this.processPhoto(res.tempFilePaths[0])
        }
      },
      fail: (error) => {
        console.error('系统相机选图失败', error)
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
