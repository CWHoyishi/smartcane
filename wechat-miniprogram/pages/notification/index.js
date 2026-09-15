const api = require('../../utils/api')

/**
 * 安全解析经纬度值
 */
function parseCoord(val) {
  if (val === null || val === undefined || val === '') return null
  const num = Number(val)
  if (isNaN(num)) return null
  return num
}

/** 格式化时间 */
function formatTime(timeStr) {
  if (!timeStr) return ''
  return timeStr
}

Page({
  data: {
    notifications: [],
    filteredList: [],
    // 筛选
    typeFilter: 'all',
    // 详情弹窗
    showDetail: false,
    currentDetail: null,
    // 状态
    loading: false,
    errorMsg: ''
  },

  onLoad() {
    this.loadNotifications()
  },

  onPullDownRefresh() {
    this.loadNotifications().then(() => {
      wx.stopPullDownRefresh()
    })
  },

  /** 从后端加载告警通知 - 不使用任何Mock数据 */
  async loadNotifications() {
    this.setData({ loading: true, errorMsg: '' })

    try {
      // 1. 获取设备列表，构建设备名称映射
      const devRes = await api.device.list()
      console.log('[Notification] 设备列表:', JSON.stringify(devRes))

      const nameMap = {}
      if (devRes && devRes.data && Array.isArray(devRes.data)) {
        devRes.data.forEach(d => {
          nameMap[d.deviceSn] = d.elderName || ''
        })
      }

      // 2. 获取摔倒告警记录
      console.log('[Notification] 请求告警记录...')
      const res = await api.sensor.listFallAlarms()
      console.log('[Notification] 告警响应:', JSON.stringify(res))

      if (res && res.data && Array.isArray(res.data)) {
        console.log('[Notification] 获取到 ' + res.data.length + ' 条告警')

        const notifications = res.data.map(item => {
          console.log('[Notification] 告警#%s lat=%s(%s) lon=%s(%s)', item.id, item.lat, typeof item.lat, item.lon, typeof item.lon)
          return {
            id: item.id,
            deviceSn: item.deviceSn,
            elderName: nameMap[item.deviceSn] || '',
            alarmType: 'FALL_FALL',
            alarmMessage: '检测到老人可能发生摔倒，请立即确认！',
            isRead: true,
            createTime: formatTime(item.reportTime || item.createTime),
            sensorData: {
              heartRate: item.heartRate,
              bloodOxygen: item.bloodOxygen,
              lat: parseCoord(item.lat),
              lon: parseCoord(item.lon)
            }
          }
        })

        this.setData({
          notifications: notifications,
          loading: false,
          errorMsg: ''
        })
        this.updateFilteredList()
      } else {
        this.setData({
          notifications: [],
          loading: false,
          errorMsg: ''
        })
        this.updateFilteredList()
      }
    } catch (e) {
      console.error('[Notification] 加载失败:', e)
      this.setData({
        notifications: [],
        filteredList: [],
        loading: false,
        errorMsg: '无法连接服务器，请检查网络或确认后端服务已启动'
      })
    }
  },

  /** 更新筛选列表 */
  updateFilteredList() {
    let list = this.data.notifications
    if (this.data.typeFilter !== 'all') {
      list = list.filter(item => item.alarmType === this.data.typeFilter)
    }
    this.setData({ filteredList: list })
  },

  /** 类型筛选 */
  onTypeFilter(e) {
    const type = e.currentTarget.dataset.type
    this.setData({ typeFilter: type })
    this.updateFilteredList()
  },

  /** 查看详情 */
  onViewDetail(e) {
    const id = e.currentTarget.dataset.id
    const item = this.data.notifications.find(n => n.id === id)
    if (item) {
      this.setData({
        showDetail: true,
        currentDetail: item
      })
    }
  },

  /** 关闭详情弹窗 */
  onCloseDetail() {
    this.setData({
      showDetail: false,
      currentDetail: null
    })
  }
})