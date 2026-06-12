const App = getApp()
const { isApiSuccess, getApiData, getApiMessage } = require('../../utils/response')
const { t } = require('../../utils/i18n')
const { translateErrorMessage } = require('../../utils/translateError')
const { getCountry } = require('../../utils/preferences')
const { startRecognition } = require('../../utils/recognitionUpload')
const { taskApi, chatApi } = require('../../utils/api')
const { parseRecords } = require('../../utils/task')
const { calculateRecordStats } = require('../../utils/recordDisplay')
const { runWithCountryGate } = require('../../utils/countryGate')

const CHAT_TEXT_FALLBACK = {
  actionCamera: '拍照',
  imageUploaded: '已上传图片',
  recognitionStarted: '图片已收到，正在识别。完成后我会在这里给你概览统计。',
  recognitionFailed: '识别失败：{message}',
  recognitionSummary: '识别完成：共 {total} 条，正常 {normal} 条，需核对 {issue} 条（模糊 {blurred}，缺勤 {absent}）。',
  recognitionSummaryClean: '识别完成：共 {total} 条，正常 {normal} 条，未发现明显异常。',
  reviewAction: '去核对'
}

function formatText(template, params) {
  let text = template || ''
  if (params && typeof params === 'object') {
    Object.keys(params).forEach((key) => {
      text = text.replace(new RegExp(`\\{${key}\\}`, 'g'), String(params[key]))
    })
  }
  return text
}

Page({
  data: {
    messages: [],
    inputText: '',
    selectedImage: null,
    isLoading: false,
    scrollToId: '',
    texts: {}
  },

  onLoad: function () {
    this.refreshTexts()
  },

  onShow: function () {
    this.refreshTexts()
    this.scrollToBottom()
  },

  refreshTexts: function () {
    const welcome = {
      id: 0,
      isUser: false,
      content: t('chat.welcome'),
      time: new Date().toISOString()
    }
    const hasWelcome = (this.data.messages || []).some((m) => m.id === 0)
    this.setData({
      texts: {
        title: t('chat.title'),
        placeholder: t('chat.placeholder'),
        send: t('chat.send'),
        thinking: t('chat.thinking'),
        actionCamera: this.chatText('actionCamera'),
        actionImage: t('chat.actionImage'),
        actionClear: t('chat.actionClear'),
        assistantHint: t('chat.assistantHint'),
        imageHint: t('chat.imageHint'),
        imageUploaded: t('chat.imageUploaded'),
        recognitionStarted: t('chat.recognitionStarted'),
        recognitionFailed: t('chat.recognitionFailed')
      },
      messages: hasWelcome ? this.data.messages : [welcome]
    })
    tt.setNavigationBarTitle({ title: t('chat.title') })
  },

  onInput: function(e) {
    this.setData({
      inputText: e.detail.value
    })
  },

  chatText: function (key, params) {
    const fullKey = `chat.${key}`
    const value = t(fullKey, params)
    if (value && value !== fullKey) return value
    return formatText(CHAT_TEXT_FALLBACK[key] || fullKey, params)
  },

  chooseImage: function () {
    if (this.data.isLoading) return
    runWithCountryGate(() => {
      tt.chooseImage({
        count: 1,
        sizeType: ['compressed'],
        sourceType: ['album'],
        success: (res) => {
          if (res.tempFilePaths.length > 0) {
            this.sendImageForRecognition(res.tempFilePaths[0])
          }
        },
        fail: (error) => {
          console.error('选择图片失败:', error)
        }
      })
    })
  },

  takePhoto: function () {
    if (this.data.isLoading) return
    runWithCountryGate(() => {
      tt.navigateTo({
        url: '/pages/camera/index?from=chat'
      })
    })
  },

  removeSelectedImage: function () {
    this.setData({
      selectedImage: null
    })
  },

  sendImageForRecognition: function (imagePath) {
    const userMsg = {
      id: Date.now(),
      isUser: true,
      content: this.chatText('imageUploaded'),
      image: imagePath,
      time: new Date().toISOString()
    }
    const progressMsg = {
      id: Date.now() + 1,
      isUser: false,
      content: this.chatText('recognitionStarted'),
      time: new Date().toISOString()
    }

    this.setData({
      messages: [...this.data.messages, userMsg, progressMsg],
      inputText: '',
      selectedImage: null,
      isLoading: true
    })
    setTimeout(() => this.scrollToBottom(), 100)

    startRecognition(imagePath)
      .then((result) => this.buildRecognitionOverview(result))
      .then((overview) => {
        const aiMsg = {
          id: Date.now() + 2,
          isUser: false,
          content: overview.content,
          taskId: overview.taskId,
          actionText: overview.actionText,
          time: new Date().toISOString()
        }
        this.setData({
          messages: [...this.data.messages, aiMsg],
          isLoading: false
        })
        setTimeout(() => this.scrollToBottom(), 100)
      })
      .catch((error) => {
        console.error('聊天页图片识别失败:', error)
        const errorMsg = {
          id: Date.now() + 2,
          isUser: false,
          content: this.chatText('recognitionFailed', {
            message: translateErrorMessage(error, t('recognizing.errorDefault'))
          }),
          time: new Date().toISOString()
        }
        this.setData({
          messages: [...this.data.messages, errorMsg],
          isLoading: false
        })
        setTimeout(() => this.scrollToBottom(), 100)
      })
  },

  buildRecognitionOverview: function (result) {
    const taskId = result && result.taskId
    const withAction = (content) => ({
      content,
      taskId,
      actionText: taskId ? this.chatText('reviewAction') : ''
    })
    const fallback = () => withAction(this.chatText('recognitionSummary', {
      total: (result && result.rowCount) || 0,
      normal: 0,
      issue: 0,
      blurred: 0,
      absent: 0
    }))

    if (!result || !result.taskId) {
      return Promise.resolve(fallback())
    }

    return taskApi.getTaskDetail(result.taskId).then((res) => {
      if (!res || !isApiSuccess(res)) return fallback()
      const task = getApiData(res) || {}
      const records = parseRecords(task.rawData || task.confirmedData)
      const stats = calculateRecordStats(records)
      const issue = Math.max(0, (stats.total || 0) - (stats.normal || 0) - (stats.deleted || 0))
      const key = issue > 0 ? 'recognitionSummary' : 'recognitionSummaryClean'
      return withAction(this.chatText(key, {
        total: stats.total || 0,
        normal: stats.normal || 0,
        issue,
        blurred: stats.blurred || 0,
        absent: stats.absent || 0
      }))
    }).catch((error) => {
      console.warn('获取识别概览失败', error)
      return fallback()
    })
  },

  openTaskFromMessage: function (e) {
    const dataset = (e.currentTarget && e.currentTarget.dataset) || {}
    const taskId = dataset.taskId || dataset.taskid
    if (!taskId) return
    tt.navigateTo({ url: `/pages/result/index?id=${taskId}` })
  },

  sendMessage: function () {
    const text = this.data.inputText.trim()
    const image = this.data.selectedImage

    if (!text && !image) {
      return
    }

    if (image) {
      this.setData({
        inputText: '',
        selectedImage: null
      })
      this.sendImageForRecognition(image)
      return
    }

    const userMsg = {
      id: Date.now(),
      isUser: true,
      content: text,
      time: new Date().toISOString()
    }

    this.setData({
      messages: [...this.data.messages, userMsg],
      inputText: '',
      selectedImage: null,
      isLoading: true
    })

    setTimeout(() => {
      this.scrollToBottom()
    }, 100)

    const requestPromise = this.sendTextMessage(text)

    requestPromise.then((response) => {
      const aiMsg = {
        id: Date.now() + 1,
        isUser: false,
        content: response,
        time: new Date().toISOString()
      }

      this.setData({
        messages: [...this.data.messages, aiMsg],
        isLoading: false
      })

      setTimeout(() => {
        this.scrollToBottom()
      }, 100)
    }).catch((error) => {
      console.error('发送消息失败:', error)
      
      const errorMsg = {
        id: Date.now() + 1,
        isUser: false,
        content: t('chat.sendFail'),
        time: new Date().toISOString()
      }

      this.setData({
        messages: [...this.data.messages, errorMsg],
        isLoading: false
      })

      setTimeout(() => {
        this.scrollToBottom()
      }, 100)
    })
  },

  sendTextMessage: function (text) {
    return chatApi.sendMessage({
      message: text,
      country: getCountry()
    }).then((body) => {
      if (!body || !isApiSuccess(body)) {
        throw new Error(getApiMessage(body))
      }
      const payload = getApiData(body) || {}
      return payload.content
    })
  },

  clearChat: function () {
    tt.showModal({
      title: t('chat.clearTitle'),
      content: t('chat.clearContent'),
      confirmText: t('common.confirm'),
      cancelText: t('common.cancel'),
      success: (res) => {
        if (res.confirm) {
          this.setData({
            messages: [
              {
                id: 0,
                isUser: false,
                content: t('chat.welcome'),
                time: new Date().toISOString()
              }
            ]
          })
        }
      }
    })
  },

  scrollToBottom: function () {
    const messages = this.data.messages || []
    const last = messages[messages.length - 1]
    this.setData({
      scrollToId: last ? `msg-${last.id}` : ''
    })
  },

  goBack: function () {
    tt.navigateBack()
  },

  formatTime: function (time) {
    if (!time) return ''
    const date = new Date(time)
    const hour = date.getHours().toString().padStart(2, '0')
    const minute = date.getMinutes().toString().padStart(2, '0')
    return `${hour}:${minute}`
  }
})
