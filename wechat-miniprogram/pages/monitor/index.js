const api = require('../../utils/api')

const REFRESH_INTERVAL = 10 // 秒

/**
 * 安全解析经纬度值
 * 数据库 Double 类型 → Java Double → JSON number/null → JS number/null
 * 处理所有边界情况：null, undefined, 0, 字符串, NaN
 */
function parseCoord(val) {
  if (val === null || val === undefined || val === '') return null
  const num = Number(val)
  if (isNaN(num)) return null
  return num
}

Page({
  data: {
    devices: [],
    deviceNames: [],
    deviceIndex: 0,
    currentDeviceSn: '',
    currentDeviceName: '',
    sensorData: {
      heartRate: '--',
      bloodOxygen: '--',
      lat: null,
      lon: null,
      fallStatus: 0,
      reportTime: ''
    },
    countdown: REFRESH_INTERVAL,
    // 状态
    loading: false,
    errorMsg: ''
  },

  timer: null,
  countdownTimer: null,

  onLoad() {
    this.loadDevices()
  },

  onUnload() {
    this.clearTimers()
  },

  onShow() {
    // 每次回到页面时刷新数据
    if (this.data.currentDeviceSn) {
      this.fetchSensorData()
    }
  },

  /** 加载设备列表 - 仅从后端数据库获取 */
  async loadDevices() {
    this.setData({ loading: true, errorMsg: '' })
    try {
      const res = await api.device.list()
      console.log('[Monitor] 设备列表响应:', JSON.stringify(res))
      if (res && res.data && Array.isArray(res.data) && res.data.length > 0) {
        const list = res.data
        const names = list.map(d => d.deviceSn + (d.elderName ? ' ' + d.elderName : ''))
        this.setData({
          devices: list,
          deviceNames: names,
          deviceIndex: 0,
          currentDeviceSn: list[0].deviceSn,
          currentDeviceName: list[0].elderName || '',
          loading: false
        })
        console.log('[Monitor] 加载设备成功:', list.length, '台')
        this.fetchSensorData()
        this.startAutoRefresh()
      } else {
        this.setData({
          loading: false,
          errorMsg: '未找到已绑定设备，请先在设备管理页面绑定设备'
        })
      }
    } catch (e) {
      console.error('[Monitor] 加载设备列表失败:', e)
      this.setData({
        loading: false,
        errorMsg: '无法连接服务器，请检查网络或确认后端服务已启动'
      })
    }
  },

  /** 拉取传感器数据 - 仅从后端数据库获取最新一条 */
  async fetchSensorData() {
    const sn = this.data.currentDeviceSn
    if (!sn) return

    try {
      const res = await api.sensor.getLatest(sn)
      console.log('[Monitor] 传感器数据响应:', JSON.stringify(res))

      if (res && res.data) {
        const raw = res.data
        console.log('[Monitor] lat=%s(%s) lon=%s(%s)', raw.lat, typeof raw.lat, raw.lon, typeof raw.lon)

        this.setData({
          sensorData: {
            heartRate: raw.heartRate != null ? raw.heartRate : '--',
            bloodOxygen: raw.bloodOxygen != null ? raw.bloodOxygen : '--',
            lat: parseCoord(raw.lat),
            lon: parseCoord(raw.lon),
            fallStatus: raw.fallStatus != null ? raw.fallStatus : 0,
            reportTime: raw.reportTime || ''
          },
          errorMsg: ''
        })
        console.log('[Monitor] 处理后 lat=%s lon=%s', this.data.sensorData.lat, this.data.sensorData.lon)
        this.setData({ countdown: REFRESH_INTERVAL })
        return
      }
      // 后端返回了 code != 200
      this.setData({ errorMsg: res.message || '暂无传感器数据' })
    } catch (e) {
      console.error('[Monitor] 获取传感器数据失败:', e)
      this.setData({ errorMsg: '获取数据失败，请下拉刷新重试' })
    }
    this.setData({ countdown: REFRESH_INTERVAL })
  },

  /** 启动自动刷新 */
  startAutoRefresh() {
    this.clearTimers()
    this.timer = setInterval(() => { this.fetchSensorData() }, REFRESH_INTERVAL * 1000)
    this.countdownTimer = setInterval(() => {
      let cd = this.data.countdown - 1
      if (cd <= 0) cd = REFRESH_INTERVAL
      this.setData({ countdown: cd })
    }, 1000)
  },

  clearTimers() {
    if (this.timer) { clearInterval(this.timer); this.timer = null }
    if (this.countdownTimer) { clearInterval(this.countdownTimer); this.countdownTimer = null }
  },

  /** 设备切换 */
  onDeviceChange(e) {
    const idx = Number(e.detail.value)
    const device = this.data.devices[idx]
    if (!device) return
    this.setData({
      deviceIndex: idx,
      currentDeviceSn: device.deviceSn,
      currentDeviceName: device.elderName || '',
      sensorData: {
        heartRate: '--',
        bloodOxygen: '--',
        lat: null,
        lon: null,
        fallStatus: 0,
        reportTime: ''
      },
      errorMsg: ''
    })
    this.fetchSensorData()
  },

  /** 下拉刷新 */
  onPullDownRefresh() {
    this.loadDevices().then(() => {
      wx.stopPullDownRefresh()
    })
  }
})