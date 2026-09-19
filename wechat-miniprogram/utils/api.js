/**
 * API请求封装
 * 对接Spring Boot后端
 *
 * 鉴权：token 存本地缓存，每个请求带 Authorization: Bearer <token>；
 * 服务端用业务码 401 表示登录失效，在这里统一跳回登录页，各页面不用重复判断。
 */

const BASE_URL = 'http://localhost:8080/api'
const TOKEN_KEY = 'smartcane_token'
const USER_KEY = 'smartcane_user'

function getToken() {
  return wx.getStorageSync(TOKEN_KEY) || ''
}

function getUser() {
  return wx.getStorageSync(USER_KEY) || null
}

function setSession(token, user) {
  wx.setStorageSync(TOKEN_KEY, token)
  wx.setStorageSync(USER_KEY, user || {})
}

function clearSession() {
  wx.removeStorageSync(TOKEN_KEY)
  wx.removeStorageSync(USER_KEY)
}

/**
 * 页面 onShow 调用：未登录直接回登录页。
 * 用 reLaunch 而不是 redirectTo：目标页可能是 tabBar 页，redirectTo 跳不过去。
 * @returns {boolean} 已登录返回 true
 */
function ensureLogin() {
  if (getToken()) {
    return true
  }
  wx.reLaunch({ url: '/pages/login/index' })
  return false
}

/**
 * 封装wx.request为Promise
 * @param {Object} options - 请求配置
 * @param {string} options.url - 接口路径（不含baseUrl）
 * @param {string} [options.method='GET'] - 请求方法
 * @param {Object} [options.data] - 请求数据
 * @returns {Promise<Object>}
 */
function request(options) {
  return new Promise((resolve, reject) => {
    const header = { 'Content-Type': 'application/json' }
    const token = getToken()
    if (token) {
      header.Authorization = 'Bearer ' + token
    }
    wx.request({
      url: BASE_URL + options.url,
      method: options.method || 'GET',
      data: options.data || {},
      header,
      success(res) {
        const body = res.data
        if (res.statusCode >= 200 && res.statusCode < 300 && body && body.code === 200) {
          resolve(body)
          return
        }
        const code = body && body.code ? body.code : res.statusCode
        const message = (body && body.message) || ('请求失败(' + code + ')')
        if (code === 401 && options.url.indexOf('/auth/login') !== 0) {
          clearSession()
          wx.reLaunch({ url: '/pages/login/index' })
        } else {
          wx.showToast({ title: message, icon: 'none' })
        }
        console.error('[API] 请求失败:', code, options.url, message)
        reject(new Error(message))
      },
      fail(err) {
        console.error('[API] 请求异常:', err, options.url)
        reject(err)
      }
    })
  })
}

module.exports = {
  BASE_URL,
  getToken,
  getUser,
  setSession,
  clearSession,
  ensureLogin,

  /** 登录鉴权接口 */
  auth: {
    login: (username, password) => request({ url: '/auth/login', method: 'POST', data: { username, password } }),
    logout: () => request({ url: '/auth/logout', method: 'POST' }),
    me: () => request({ url: '/auth/me' })
  },

  /** 设备相关接口 */
  device: {
    list: () => request({ url: '/device/list' }),
    getBySn: (sn) => request({ url: '/device/sn/' + sn })
  },

  /** 健康统计接口（日/周报，服务端定时预聚合） */
  healthStat: {
    daily: (deviceSn, days) => request({ url: '/health-stat/daily/' + deviceSn, data: { days } }),
    weekly: (deviceSn, weeks) => request({ url: '/health-stat/weekly/' + deviceSn, data: { weeks } })
  },

  /** 告警闭环接口：查询 + 处置（1 已确认 / 2 误报） */
  alarm: {
    page: (data) => request({ url: '/alarm/page', method: 'POST', data }),
    pending: () => request({ url: '/alarm/pending' }),
    listByDeviceSn: (deviceSn) => request({ url: '/alarm/list/' + deviceSn }),
    ack: (data) => request({ url: '/alarm/ack', method: 'PUT', data })
  },

  /** 传感器数据接口 */
  sensor: {
    getLatest: (deviceSn) => request({ url: '/sensor/latest/' + deviceSn }),
    list: (deviceSn) => request({ url: '/sensor/list/' + deviceSn }),
    page: (data) => request({ url: '/sensor/page', method: 'POST', data })
  }
}