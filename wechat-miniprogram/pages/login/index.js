const api = require('../../utils/api')

Page({
  data: {
    username: '',
    password: '',
    loading: false
  },

  onLoad() {
    // 已登录就不用再登一次
    if (api.getToken()) {
      wx.reLaunch({ url: '/pages/monitor/index' })
    }
  },

  onUsernameInput(e) {
    this.setData({ username: e.detail.value })
  },

  onPasswordInput(e) {
    this.setData({ password: e.detail.value })
  },

  async onLogin() {
    const username = this.data.username.trim()
    const password = this.data.password
    if (!username || !password) {
      wx.showToast({ title: '请输入账号和密码', icon: 'none' })
      return
    }
    this.setData({ loading: true })
    try {
      const res = await api.auth.login(username, password)
      api.setSession(res.data.token, res.data)
      wx.showToast({ title: '登录成功', icon: 'success' })
      setTimeout(() => wx.reLaunch({ url: '/pages/monitor/index' }), 500)
    } catch (e) {
      // 失败原因已由 api.request 统一 toast，这里只恢复按钮
      console.warn('[Login] 登录失败:', e.message)
      this.setData({ loading: false })
    }
  }
})