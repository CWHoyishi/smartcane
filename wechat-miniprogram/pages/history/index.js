const api = require('../../utils/api')

/**
 * 安全解析经纬度值 - 处理 null/undefined/NaN 等边界情况
 */
function parseCoord(val) {
  if (val === null || val === undefined || val === '') return null
  const num = Number(val)
  if (isNaN(num)) return null
  return num
}

/** 获取今天的 yyyy-MM-dd */
function getToday() {
  const d = new Date()
  const y = d.getFullYear()
  const m = String(d.getMonth() + 1).padStart(2, '0')
  const day = String(d.getDate()).padStart(2, '0')
  return y + '-' + m + '-' + day
}

/** 从 reportTime 中提取日期部分 yyyy-MM-dd，兼容多种格式 */
function extractDate(reportTime) {
  if (!reportTime) return ''
  // 格式1: "2026-06-23 13:46:54" → 取前10位
  // 格式2: "2026/06/23 13:46:54" → 替换 /
  const s = String(reportTime).trim()
  if (s.length >= 10 && s[4] === '-' && s[7] === '-') {
    return s.substring(0, 10)
  }
  if (s.length >= 10 && s[4] === '/' && s[7] === '/') {
    return s.substring(0, 4) + '-' + s.substring(5, 7) + '-' + s.substring(8, 10)
  }
  // 兜底：尝试用 new Date 解析后格式化
  const d = new Date(s)
  if (!isNaN(d.getTime())) {
    const y = d.getFullYear()
    const m = String(d.getMonth() + 1).padStart(2, '0')
    const day = String(d.getDate()).padStart(2, '0')
    return y + '-' + m + '-' + day
  }
  return ''
}

Page({
  data: {
    // 日期筛选（yyyy-MM-dd）
    selectedDate: '',
    todayDate: '',       // 用于picker的end属性，固定为今天
    // 设备
    deviceSn: '',
    currentDeviceName: '',
    // 数据
    allData: [],
    filteredList: [],
    // 状态
    loading: false,
    errorMsg: ''
  },

  onLoad() {
    const today = getToday()
    this.setData({ selectedDate: today, todayDate: today })
    this.loadHistoryData()
  },

  onShow() {
    api.ensureLogin()
  },

  onPullDownRefresh() {
    this.loadHistoryData().then(() => {
      wx.stopPullDownRefresh()
    })
  },

  /** 从后端加载数据 - 不使用任何Mock数据 */
  async loadHistoryData() {
    this.setData({ loading: true, errorMsg: '' })

    try {
      // 1. 获取设备列表
      const devRes = await api.device.list()

      let deviceSn = ''
      let deviceName = ''

      if (devRes && devRes.data && Array.isArray(devRes.data) && devRes.data.length > 0) {
        deviceSn = devRes.data[0].deviceSn
        deviceName = devRes.data[0].elderName || ''
      }

      if (!deviceSn) {
        this.setData({
          loading: false,
          errorMsg: '未找到已绑定设备，请先在设备管理页面绑定设备'
        })
        return
      }

      // 2. 获取传感器历史数据
      const res = await api.sensor.list(deviceSn)

      if (res && res.data && Array.isArray(res.data)) {
        const list = res.data.map(item => ({
          ...item,
          reportTime: item.reportTime || '',
          _datePart: extractDate(item.reportTime),
          lat: parseCoord(item.lat),
          lon: parseCoord(item.lon)
        }))

        console.log('[History] 原始数据日期样例:', list.length > 0 ? list[0].reportTime : '无')
        console.log('[History] 提取的日期样例:', list.length > 0 ? list[0]._datePart : '无')
        console.log('[History] 所有记录日期部分:', list.map(i => i._datePart))

        this.setData({
          deviceSn: deviceSn,
          currentDeviceName: deviceName,
          allData: list,
          loading: false,
          errorMsg: ''
        })

        // 3. 按当前选择的日期过滤
        this.filterByDate(this.data.selectedDate)
      } else {
        this.setData({
          deviceSn: deviceSn,
          currentDeviceName: deviceName,
          allData: [],
          filteredList: [],
          loading: false,
          errorMsg: ''
        })
        this.filterByDate(this.data.selectedDate)
      }
    } catch (e) {
      console.error('[History] 加载失败:', e)
      this.setData({
        allData: [],
        filteredList: [],
        loading: false,
        errorMsg: '无法连接服务器，请检查网络或确认后端服务已启动'
      })
    }
  },

  /** 按选择的日期(yyyy-MM-dd)过滤当天数据 */
  filterByDate(dateStr) {
    const list = this.data.allData

    if (!list || list.length === 0) {
      console.log('[History] 过滤: allData为空, dateStr=', dateStr)
      this.setData({ filteredList: [], selectedDate: dateStr })
      return
    }

    if (!dateStr) {
      this.setData({ filteredList: list, selectedDate: dateStr })
      return
    }

    // 使用预提取的 _datePart 进行精确匹配
    const filtered = list.filter(item => {
      return item._datePart === dateStr
    })

    console.log('[History] 过滤: 目标日期=' + dateStr + ', 匹配数=' + filtered.length + ', 总数=' + list.length)

    this.setData({ filteredList: filtered, selectedDate: dateStr })
  },

  /** 日期选择器变化回调 */
  onDateChange(e) {
    const selected = e.detail.value // "2026-06-23"
    console.log('[History] 选择日期:', selected)
    this.filterByDate(selected)
  }
})