const api = require('../../utils/api')

/** 每页条数：与后端分页接口的默认上限（200）不冲突，列表短一些更适合手机 */
const PAGE_SIZE = 20

/** 状态筛选：key 用于界面高亮，status 为 null 表示不传该条件（查全部） */
const STATUS_FILTERS = [
  { key: 'all', label: '全部', status: null },
  { key: 'pending', label: '待处理', status: 0 },
  { key: 'acked', label: '已确认', status: 1 },
  { key: 'false', label: '误报', status: 2 }
]

const STATUS_TEXT = { 0: '待处理', 1: '已确认', 2: '误报' }

/** 告警类型文案：目前只有摔倒会告警，历史数据里可能还有已停用的心率/血氧 */
function alarmTypeText(type) {
  if (type === 'FALL') return '摔倒告警'
  if (type === 'HEART_RATE') return '心率告警(已停用)'
  if (type === 'BLOOD_OXYGEN') return '血氧告警(已停用)'
  return '未知告警'
}

/** 把后端告警记录折成界面直接可用的形状，wxml 里不写判断逻辑 */
function toItem(record, nameMap) {
  const status = record.status === null || record.status === undefined ? 0 : record.status
  return {
    id: record.id,
    deviceSn: record.deviceSn,
    elderName: nameMap[record.deviceSn] || '',
    typeText: alarmTypeText(record.alarmType),
    typeClass: record.alarmType === 'FALL' ? 'type-fall' : 'type-other',
    level: record.level === 3 ? '紧急' : record.level === 2 ? '重要' : '',
    status: status,
    statusText: STATUS_TEXT[status] || '未知',
    statusClass: status === 0 ? 'status-pending' : status === 1 ? 'status-acked' : 'status-false',
    time: record.reportTime || record.createTime || '',
    alarmValue: record.alarmValue || '',
    handledBy: record.handledBy || '',
    handleTime: record.handleTime || '',
    handleNote: record.handleNote || '',
    pending: status === 0
  }
}

Page({
  data: {
    filters: STATUS_FILTERS,
    activeFilter: 'all',
    list: [],
    pageNum: 1,
    hasMore: false,
    pendingCount: 0,
    loading: false,
    loadingMore: false,
    errorMsg: '',
    showDetail: false,
    currentDetail: null
  },

  onLoad() {
    // 设备号 → 老人姓名，只在显示时用；放在实例上而不是 data，避免每次刷新都往视图层塞整张表
    this.nameMap = {}
    this.loadDevices()
    this.loadPendingCount()
    this.loadList(true)
  },

  onPullDownRefresh() {
    this.loadDevices()
    this.loadPendingCount()
    this.loadList(true).then(() => {
      wx.stopPullDownRefresh()
    })
  },

  onReachBottom() {
    if (this.data.hasMore && !this.data.loadingMore && !this.data.loading) {
      this.loadList(false)
    }
  },

  /** 设备列表只用于把设备号翻成老人姓名，失败不影响告警列表本身 */
  async loadDevices() {
    try {
      const res = await api.device.list()
      const map = {}
      const devices = (res && res.data) || []
      devices.forEach(device => {
        map[device.deviceSn] = device.elderName || ''
      })
      this.nameMap = map
      // 姓名拿到后补刷一次已有列表的显示
      if (this.data.list.length > 0) {
        this.setData({
          list: this.data.list.map(item => Object.assign({}, item, { elderName: map[item.deviceSn] || '' }))
        })
      }
    } catch (e) {
      console.warn('[Notification] 设备列表加载失败:', e.message)
    }
  },

  /** 待处理条数：顶部提示用，与当前筛选无关 */
  async loadPendingCount() {
    try {
      const res = await api.alarm.pending()
      this.setData({ pendingCount: ((res && res.data) || []).length })
    } catch (e) {
      console.warn('[Notification] 待处理数量加载失败:', e.message)
    }
  },

  /**
   * 加载告警列表
   * @param {boolean} reset true 表示切换到第 1 页（切换筛选或下拉刷新），false 表示加载下一页
   */
  async loadList(reset) {
    const filter = STATUS_FILTERS.find(item => item.key === this.data.activeFilter) || STATUS_FILTERS[0]
    const pageNum = reset ? 1 : this.data.pageNum + 1
    this.setData(reset ? { loading: true, errorMsg: '' } : { loadingMore: true })

    try {
      const res = await api.alarm.page({
        status: filter.status,
        pageNum: pageNum,
        pageSize: PAGE_SIZE
      })
      const page = (res && res.data) || {}
      const items = (page.records || []).map(record => toItem(record, this.nameMap))

      this.setData({
        list: reset ? items : this.data.list.concat(items),
        pageNum: pageNum,
        // 返回条数不足一页就说明没有下一页了
        hasMore: items.length === PAGE_SIZE,
        loading: false,
        loadingMore: false,
        errorMsg: ''
      })
    } catch (e) {
      console.error('[Notification] 加载告警失败:', e)
      this.setData({
        list: reset ? [] : this.data.list,
        loading: false,
        loadingMore: false,
        errorMsg: '无法连接服务器，请检查网络或确认后端服务已启动'
      })
    }
  },

  /** 顶部筛选切换 */
  onFilterSelect(e) {
    const key = e.currentTarget.dataset.key
    if (key === this.data.activeFilter) {
      return
    }
    this.setData({ activeFilter: key, list: [], pageNum: 1 })
    this.loadList(true)
  },

  /** 查看详情 */
  onViewDetail(e) {
    const id = e.currentTarget.dataset.id
    const item = this.data.list.find(record => record.id === id)
    if (item) {
      this.setData({ showDetail: true, currentDetail: item })
    }
  },

  onCloseDetail() {
    this.setData({ showDetail: false, currentDetail: null })
  },

  /**
   * 处置告警：确认已处置（1）或标记误报（2）。
   * 备注用 wx.showModal 的 editable 输入框收集，避免为一个可选字段再做一个弹层。
   */
  async onAck(e) {
    const id = e.currentTarget.dataset.id
    const status = Number(e.currentTarget.dataset.status)
    const actionText = status === 1 ? '确认已处置' : '标记为误报'

    const modal = await new Promise(resolve => {
      wx.showModal({
        title: actionText,
        editable: true,
        placeholderText: '可填写处理备注（选填）',
        success: resolve,
        fail: () => resolve({ confirm: false })
      })
    })
    if (!modal.confirm) {
      return
    }

    try {
      const res = await api.alarm.ack({
        id: id,
        status: status,
        handledBy: '小程序',
        handleNote: modal.content || ''
      })
      if (res && res.code === 200) {
        wx.showToast({ title: '已处理', icon: 'success' })
        this.setData({ showDetail: false, currentDetail: null })
        this.loadPendingCount()
        this.loadList(true)
      } else {
        wx.showToast({ title: (res && res.message) || '处理失败', icon: 'none' })
      }
    } catch (err) {
      console.error('[Notification] 处置失败:', err)
      wx.showToast({ title: '网络异常，请重试', icon: 'none' })
    }
  }
})