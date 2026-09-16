const api = require('../../utils/api')

/** 绘图区尺寸（rpx），必须与 health/index.wxss 里的 .chart-plot 一致 */
const CHART_W = 620
const CHART_H = 220

const DAILY_DAYS = 7
const WEEKLY_WEEKS = 4

/**
 * 折线几何：小程序没有 SVG/canvas 依赖，用「旋转的细 view」拼出线段。
 * 纵轴按当组数据的最小/最大值归一，否则心率 60~90 的波动会被压成一条直线。
 */
function buildLine(values) {
  const valid = values.filter(v => v !== null && v !== undefined)
  if (valid.length < 2) {
    return { lines: [], dots: [], empty: true }
  }
  const max = Math.max.apply(null, valid)
  const min = Math.min.apply(null, valid)
  const span = max - min || 1
  const lastIndex = values.length - 1

  const coords = values.map((v, i) => {
    if (v === null || v === undefined) {
      return null
    }
    return {
      x: Math.round((CHART_W * i) / lastIndex),
      y: Math.round(CHART_H - ((v - min) / span) * CHART_H)
    }
  })

  const dots = []
  const lines = []
  let prev = null
  coords.forEach((point, i) => {
    if (!point) {
      // 缺数据的周期断开折线，不要连成一条假趋势
      prev = null
      return
    }
    dots.push({ id: i, left: point.x - 5, top: point.y - 5 })
    if (prev) {
      const dx = point.x - prev.x
      const dy = point.y - prev.y
      lines.push({
        id: i,
        left: prev.x,
        top: prev.y - 2,
        width: Math.round(Math.sqrt(dx * dx + dy * dy)),
        angle: ((Math.atan2(dy, dx) * 180) / Math.PI).toFixed(2)
      })
    }
    prev = point
  })
  return { lines: lines, dots: dots, empty: false }
}

/** 柱状几何：从 0 起算，高度按最大值归一 */
function buildBars(values) {
  const valid = values.filter(v => v !== null && v !== undefined)
  const max = valid.length ? Math.max.apply(null, valid) : 0
  const slot = CHART_W / Math.max(values.length, 1)
  const barWidth = Math.round(slot * 0.5)
  const bars = values.map((v, i) => {
    const value = v === null || v === undefined ? 0 : v
    return {
      id: i,
      left: Math.round(slot * i + (slot - barWidth) / 2),
      width: barWidth,
      height: max > 0 ? Math.round((value / max) * CHART_H) : 0
    }
  })
  return { bars: bars, empty: max <= 0 }
}

/** 横轴最多显示 5 个标签，避免挤在一起 */
function pickLabels(labels) {
  const total = labels.length
  if (total === 0) {
    return []
  }
  if (total <= 5) {
    return labels
  }
  const step = Math.ceil(total / 5)
  const picked = []
  for (let i = 0; i < total; i += step) {
    picked.push(labels[i])
  }
  if (picked[picked.length - 1] !== labels[total - 1]) {
    picked.push(labels[total - 1])
  }
  return picked
}

/** 均值缺样本时后端返回 null，界面统一显示 --，不要显示成 0 */
function showValue(value) {
  return value === null || value === undefined ? '--' : value
}

Page({
  data: {
    mode: 'daily',
    loading: false,
    errorMsg: '',
    rangeText: '',
    summary: { avgHeartRate: '--', avgBloodOxygen: '--', abnormalCount: 0, activeMinutes: 0 },
    chartLabels: [],
    heartRateChart: { lines: [], dots: [], empty: true },
    bloodOxygenChart: { lines: [], dots: [], empty: true },
    abnormalChart: { bars: [], empty: true },
    activeChart: { bars: [], empty: true },
    rows: []
  },

  onLoad() {
    this.loadStats()
  },

  onPullDownRefresh() {
    this.loadStats().then(() => {
      wx.stopPullDownRefresh()
    })
  },

  /** 日报/周报切换 */
  onModeChange(e) {
    const mode = e.currentTarget.dataset.mode
    if (mode === this.data.mode) {
      return
    }
    this.setData({ mode: mode }, () => {
      this.loadStats()
    })
  },

  async loadStats() {
    this.setData({ loading: true, errorMsg: '' })
    try {
      const devRes = await api.device.list()
      const deviceList = (devRes && devRes.data) || []
      if (deviceList.length === 0) {
        this.setData({ loading: false, errorMsg: '未找到已绑定设备，请先在设备管理页面绑定设备' })
        return
      }
      const deviceSn = deviceList[0].deviceSn

      const res = this.data.mode === 'daily'
        ? await api.healthStat.daily(deviceSn, DAILY_DAYS)
        : await api.healthStat.weekly(deviceSn, WEEKLY_WEEKS)

      this.render(deviceSn, (res && res.data) || [])
    } catch (e) {
      console.error('[Health] 加载统计失败:', e)
      this.setData({ loading: false, errorMsg: '无法连接服务器，请检查网络或确认后端服务已启动' })
    }
  },

  /** 把接口返回的统计行折算成概览、图表几何和明细列表 */
  render(deviceSn, rows) {
    const isDaily = this.data.mode === 'daily'
    const labels = rows.map(row => String((isDaily ? row.statDate : row.weekStart) || '').slice(5))
    const heartRates = rows.map(row => (row.avgHeartRate === null || row.avgHeartRate === undefined ? null : Number(row.avgHeartRate)))
    const bloodOxygens = rows.map(row => (row.avgBloodOxygen === null || row.avgBloodOxygen === undefined ? null : Number(row.avgBloodOxygen)))
    const abnormalCounts = rows.map(row => (row.fallCount || 0) + (row.heartRateAbnormalCount || 0) + (row.bloodOxygenAbnormalCount || 0))
    const activeMinutes = rows.map(row => row.activeMinutes || 0)

    const current = rows.length > 0 ? rows[rows.length - 1] : null
    const summary = current
      ? {
          avgHeartRate: showValue(current.avgHeartRate),
          avgBloodOxygen: showValue(current.avgBloodOxygen),
          abnormalCount: (current.fallCount || 0) + (current.heartRateAbnormalCount || 0) + (current.bloodOxygenAbnormalCount || 0),
          activeMinutes: current.activeMinutes || 0
        }
      : { avgHeartRate: '--', avgBloodOxygen: '--', abnormalCount: 0, activeMinutes: 0 }

    const rangeText = rows.length > 0
      ? (isDaily
          ? rows[0].statDate + ' ~ ' + rows[rows.length - 1].statDate
          : rows[0].weekStart + ' ~ ' + rows[rows.length - 1].weekEnd)
      : ''

    // 最近的在最前面，和实时数据页的习惯保持一致
    const list = rows.slice().reverse().map(row => ({
      id: isDaily ? row.statDate : row.weekStart,
      period: isDaily ? row.statDate : row.weekStart + ' ~ ' + row.weekEnd,
      avgHeartRate: showValue(row.avgHeartRate),
      avgBloodOxygen: showValue(row.avgBloodOxygen),
      abnormalCount: (row.fallCount || 0) + (row.heartRateAbnormalCount || 0) + (row.bloodOxygenAbnormalCount || 0),
      activeMinutes: row.activeMinutes || 0,
      sampleCount: row.sampleCount || 0
    }))

    this.setData({
      deviceSn: deviceSn,
      loading: false,
      errorMsg: '',
      rangeText: rangeText,
      summary: summary,
      chartLabels: pickLabels(labels),
      heartRateChart: buildLine(heartRates),
      bloodOxygenChart: buildLine(bloodOxygens),
      abnormalChart: buildBars(abnormalCounts),
      activeChart: buildBars(activeMinutes),
      rows: list
    })
  }
})