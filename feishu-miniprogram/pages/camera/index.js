const { t } = require('../../utils/i18n')
const { ensureCountryConfigured } = require('../../utils/preferences')

const CAMERA_ID = 'camera'
const BOOTSTRAP_MAX_ATTEMPTS = 12
const BOOTSTRAP_INTERVAL_MS = 350

Page({
  data: {
    cameraPosition: 'back',
    flashMode: 'off',
    cameraReady: false,
    initHint: '',
    texts: {}
  },

  onLoad: function (options) {
    this._fromChat = options && options.from === 'chat'
    if (!ensureCountryConfigured()) {
      return
    }
    this.refreshTexts()
    this.setData({ initHint: t('camera.starting') })
    this.requestCameraPermission(() => {
      this.scheduleCameraBootstrap()
    })
  },

  onReady: function () {
    this._pageReady = true
    this.scheduleCameraBootstrap()
  },

  onShow: function () {
    if (this._needReinitContext) {
      this._needReinitContext = false
      this.setData({ initHint: t('camera.starting'), cameraReady: false })
      this.scheduleCameraBootstrap()
    }
  },

  onUnload: function () {
    this.clearBootstrapTimer()
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
        flashOn: t('camera.flashOn'),
        flashOff: t('camera.flashOff'),
        flip: t('camera.flip')
      }
    })
    tt.setNavigationBarTitle({ title: t('camera.title') })
  },

  clearBootstrapTimer: function () {
    if (this._bootstrapTimer) {
      clearTimeout(this._bootstrapTimer)
      this._bootstrapTimer = null
    }
  },

  scheduleCameraBootstrap: function () {
    if (!this._pageReady) {
      return
    }
    this.clearBootstrapTimer()
    this._bootstrapAttempt = 0
    this.bootstrapCameraStep()
  },

  bootstrapCameraStep: function () {
    const attempt = this._bootstrapAttempt || 0
    this._bootstrapAttempt = attempt + 1

    if (this.initCameraContext()) {
      return
    }

    if (attempt >= BOOTSTRAP_MAX_ATTEMPTS) {
      this.setData({
        cameraReady: false,
        initHint: t('camera.previewUnavailable')
      })
      return
    }

    this._bootstrapTimer = setTimeout(() => {
      this.bootstrapCameraStep()
    }, BOOTSTRAP_INTERVAL_MS)
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
          this.setData({ initHint: t('camera.starting') })
          this.scheduleCameraBootstrap()
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
      this.chooseFromGallery()
    }
  },

  initCameraContext: function () {
    try {
      if (typeof tt.createCameraContext !== 'function') {
        this.setData({
          cameraReady: false,
          initHint: t('camera.unsupported')
        })
        return false
      }
      this.cameraContext = tt.createCameraContext(CAMERA_ID)
      this.setData({ cameraReady: true, initHint: '' })
      this.clearBootstrapTimer()
      return true
    } catch (e) {
      console.warn('createCameraContext 失败', e)
      this.cameraContext = null
      return false
    }
  },

  onCameraInitDone: function (e) {
    console.log('camera init done', e.detail)
    this._retriedPhoto = false
    this.initCameraContext()
  },

  onCameraStop: function () {
    this.cameraContext = null
    this.setData({ cameraReady: false, initHint: t('camera.starting') })
    this._needReinitContext = true
  },

  toggleCamera: function () {
    this.cameraContext = null
    this.setData({
      cameraPosition: this.data.cameraPosition === 'back' ? 'front' : 'back',
      cameraReady: false,
      initHint: t('camera.switching')
    })
    this.scheduleCameraBootstrap()
  },

  toggleFlash: function () {
    this.setData({
      flashMode: this.data.flashMode === 'off' ? 'on' : 'off'
    })
  },

  takePhoto: function () {
    const run = () => {
      if (!this.cameraContext && !this.initCameraContext()) {
        this.fallbackCapture(t('camera.notReady'))
        return
      }
      this.cameraContext.takePhoto({
        quality: 'normal',
        success: (res) => {
          if (res.tempImagePath) {
            this.processPhoto(res.tempImagePath)
          } else {
            this.fallbackCapture(t('camera.photoFail'))
          }
        },
        fail: (error) => {
          console.error('拍照失败:', error)
          const msg = (error && error.errString) || ''
          if (msg.indexOf('not found') >= 0 && !this._retriedPhoto) {
            this._retriedPhoto = true
            this.scheduleCameraBootstrap()
            setTimeout(() => this.takePhoto(), 500)
            return
          }
          this.fallbackCapture(t('camera.photoFail'))
        }
      })
    }

    if (!this.data.cameraReady) {
      tt.showToast({ title: t('camera.warming'), icon: 'none' })
      this.scheduleCameraBootstrap()
      setTimeout(run, 500)
      return
    }
    run()
  },

  fallbackCapture: function (reason) {
    tt.showModal({
      title: t('camera.photoFail'),
      content: reason + '\n' + t('camera.fallbackHint'),
      confirmText: t('camera.openAlbum'),
      cancelText: t('common.cancel'),
      success: (res) => {
        if (res.confirm) {
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
    this.setData({
      cameraReady: false,
      initHint: t('camera.cameraFail')
    })
    tt.showToast({ title: t('camera.cameraFail'), icon: 'none' })
  }
})
