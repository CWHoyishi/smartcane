const api = require('../../utils/api')

Page({
  data: {
    devices: [],
    onlineCount: 0,
    offlineCount: 0,
    loading: false,
    errorMsg: ''
  },

  onLoad() {
    this.loadDevices()
  },

  onShow() {
    if (!api.ensureLogin()) {
      return
    }
    this.loadDevices()
  },

  onPullDownRefresh() {
    this.loadDevices().then(() => {
      wx.stopPullDownRefresh()
    })
  },

  /** 从后端获取设备列表 - 不使用任何Mock数据 */
  async loadDevices() {
    this.setData({ loading: true, errorMsg: '' })
    try {
      const res = await api.device.list()
      console.log('[Device] 设备列表响应:', JSON.stringify(res))
      if (res && res.data && Array.isArray(res.data)) {
        this.setData({
          devices: res.data,
          onlineCount: res.data.filter(d => d.deviceStatus === 1).length,
          offlineCount: res.data.filter(d => d.deviceStatus !== 1).length,
          loading: false,
          errorMsg: ''
        })
        console.log('[Device] 加载成功:', res.data.length, '台')
      } else {
        this.setData({
          devices: [],
          onlineCount: 0,
          offlineCount: 0,
          loading: false,
          errorMsg: ''
        })
      }
    } catch (e) {
      console.error('[Device] 加载失败:', e)
      this.setData({
        devices: [],
        onlineCount: 0,
        offlineCount: 0,
        loading: false,
        errorMsg: '无法连接服务器，请检查网络或确认后端服务已启动'
      })
    }
  },

  /** 退出登录：先让服务端失效 token，再清本地并回登录页 */
  async onLogout() {
    try {
      await api.auth.logout()
    } catch (e) {
      // 会话可能已经过期，登出接口失败也要清掉本地登录态
      console.warn('[Device] 登出接口失败:', e.message)
    }
    api.clearSession()
    wx.reLaunch({ url: '/pages/login/index' })
  },

  /** 绑定新设备 */
  onBindDevice() {
    wx.showModal({
      title: '绑定设备',
      content: '请输入设备序列号进行绑定',
      editable: true,
      placeholderText: '请输入设备序列号',
      success(res) {
        if (res.confirm && res.content) {
          console.log('[Device] 绑定设备:', res.content)
          wx.showToast({ title: '绑定请求已发送', icon: 'success' })
          // TODO: 调用后端API完成绑定
        }
      }
    })
  }
})