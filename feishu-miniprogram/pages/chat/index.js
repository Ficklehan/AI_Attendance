const App = getApp()

Page({
  data: {
    messages: [
      {
        id: 0,
        isUser: false,
        content: '您好！我是AI考勤助手，请问有什么可以帮您的？\n\n您可以：\n📷 发送考勤表图片进行识别\n❓ 询问关于考勤数据的问题\n📊 查询统计信息',
        time: new Date().toISOString()
      }
    ],
    inputText: '',
    selectedImage: null,
    isLoading: false,
    scrollToId: ''
  },

  onLoad: function () {
    dd.setNavigationBarTitle({ title: 'AI助手' })
  },

  onShow: function () {
    this.scrollToBottom()
  },

  onInput: function(e) {
    this.setData({
      inputText: e.detail.value
    })
  },

  chooseImage: async function () {
    try {
      const res = await dd.chooseImage({
        count: 1,
        sizeType: ['compressed'],
        sourceType: ['album', 'camera']
      })
      
      if (res.tempFilePaths.length > 0) {
        this.setData({
          selectedImage: res.tempFilePaths[0]
        })
      }
    } catch (error) {
      console.error('选择图片失败:', error)
    }
  },

  removeSelectedImage: function () {
    this.setData({
      selectedImage: null
    })
  },

  sendMessage: async function () {
    const text = this.data.inputText.trim()
    const image = this.data.selectedImage

    if (!text && !image) {
      return
    }

    const userMsg = {
      id: Date.now(),
      isUser: true,
      content: text || '[图片]',
      image: image,
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

    try {
      let response

      if (image) {
        response = await this.sendImageMessage(image)
      } else {
        response = await this.sendTextMessage(text)
      }

      const aiMsg = {
        id: Date.now() + 1,
        isUser: false,
        content: response,
        time: new Date().toISOString()
      }

      this.setData({
        messages: [...this.data.messages, userMsg, aiMsg],
        isLoading: false
      })

      setTimeout(() => {
        this.scrollToBottom()
      }, 100)
    } catch (error) {
      console.error('发送消息失败:', error)
      
      const errorMsg = {
        id: Date.now() + 1,
        isUser: false,
        content: '抱歉，我暂时无法回答您的问题，请稍后再试。',
        time: new Date().toISOString()
      }

      this.setData({
        messages: [...this.data.messages, userMsg, errorMsg],
        isLoading: false
      })

      setTimeout(() => {
        this.scrollToBottom()
      }, 100)
    }
  },

  sendTextMessage: async function (text) {
    const res = await dd.httpRequest({
      url: `${App.globalData.baseUrl}/api/chat/completion`,
      method: 'POST',
      data: {
        message: text,
        country: App.globalData.currentCountry
      },
      header: {
        'Content-Type': 'application/json',
        'Authorization': App.globalData.token ? `Bearer ${App.globalData.token}` : ''
      }
    })

    if (res.data && res.data.success) {
      return res.data.data.content
    } else {
      throw new Error(res.data.message || '请求失败')
    }
  },

  sendImageMessage: async function (imagePath) {
    return new Promise((resolve, reject) => {
      dd.uploadFile({
        url: `${App.globalData.baseUrl}/api/chat/image`,
        filePath: imagePath,
        name: 'file',
        header: {
          'Authorization': App.globalData.token ? `Bearer ${App.globalData.token}` : '',
          'X-Country': App.globalData.currentCountry
        },
        success: (res) => {
          try {
            const data = JSON.parse(res.data)
            if (data.success) {
              resolve(data.data.content)
            } else {
              reject(new Error(data.message))
            }
          } catch {
            reject(new Error('解析失败'))
          }
        },
        fail: (error) => {
          reject(error)
        }
      })
    })
  },

  clearChat: function () {
    dd.showModal({
      title: '清空对话',
      content: '确定要清空所有对话记录吗？',
      success: (res) => {
        if (res.confirm) {
          this.setData({
            messages: [
              {
                id: 0,
                isUser: false,
                content: '您好！我是AI考勤助手，请问有什么可以帮您的？',
                time: new Date().toISOString()
              }
            ]
          })
        }
      }
    })
  },

  scrollToBottom: function () {
    const lastIndex = this.data.messages.length - 1
    this.setData({
      scrollToId: `msg-${lastIndex}`
    })
  },

  formatTime: function (time) {
    if (!time) return ''
    const date = new Date(time)
    const hour = date.getHours().toString().padStart(2, '0')
    const minute = date.getMinutes().toString().padStart(2, '0')
    return `${hour}:${minute}`
  }
})
