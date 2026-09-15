App({
  globalData: {
    // 后端API地址，部署时改为实际地址
    baseUrl: 'http://localhost:8080/api',
    currentDeviceSn: '',
    currentDeviceInfo: null
  },

  onLaunch() {
    console.log('[App] 智能拐杖小程序启动')
  }
})
