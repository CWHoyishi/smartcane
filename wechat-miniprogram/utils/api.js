/**
 * API请求封装
 * 对接Spring Boot后端
 */

const BASE_URL = 'http://localhost:8080/api'

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
    wx.request({
      url: BASE_URL + options.url,
      method: options.method || 'GET',
      data: options.data || {},
      header: { 'Content-Type': 'application/json' },
      success(res) {
        if (res.statusCode >= 200 && res.statusCode < 300) {
          resolve(res.data)
        } else {
          console.error('[API] 请求失败:', res.statusCode, options.url)
          reject(new Error('HTTP ' + res.statusCode))
        }
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

  /** 设备相关接口 */
  device: {
    list: () => request({ url: '/device/list' }),
    getBySn: (sn) => request({ url: '/device/sn/' + sn })
  },

  /** 传感器数据接口 */
  sensor: {
    getLatest: (deviceSn) => request({ url: '/sensor/latest/' + deviceSn }),
    list: (deviceSn) => request({ url: '/sensor/list/' + deviceSn }),
    page: (data) => request({ url: '/sensor/page', method: 'POST', data }),
    listFallAlarms: () => request({ url: '/sensor/fall-alarms' })
  }
}
