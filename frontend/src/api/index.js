import request from '@/utils/request'

export const deviceApi = {
  list() {
    return request({
      url: '/device/list',
      method: 'get'
    })
  },
  getById(id) {
    return request({
      url: `/device/${id}`,
      method: 'get'
    })
  },
  getByDeviceSn(deviceSn) {
    return request({
      url: `/device/sn/${deviceSn}`,
      method: 'get'
    })
  },
  add(data) {
    return request({
      url: '/device/add',
      method: 'post',
      data
    })
  },
  update(data) {
    return request({
      url: '/device/update',
      method: 'put',
      data
    })
  },
  // force=false 时后端会拒绝删除仍有历史采样/告警的设备
  delete(id, force = false) {
    return request({
      url: `/device/delete/${id}`,
      method: 'delete',
      params: { force }
    })
  }
}

export const healthStatApi = {
  daily(deviceSn, days = 7) {
    return request({
      url: `/health-stat/daily/${deviceSn}`,
      method: 'get',
      params: { days }
    })
  },
  weekly(deviceSn, weeks = 4) {
    return request({
      url: `/health-stat/weekly/${deviceSn}`,
      method: 'get',
      params: { weeks }
    })
  },
  // 重算要按天扫采样明细，比普通查询慢，单独放宽超时
  rebuild(days = 7) {
    return request({
      url: '/health-stat/rebuild',
      method: 'post',
      params: { days },
      timeout: 60000
    })
  }
}

export const sensorApi = {
  page(data) {
    return request({
      url: '/sensor/page',
      method: 'post',
      data
    })
  },
  listByDeviceSn(deviceSn) {
    return request({
      url: `/sensor/list/${deviceSn}`,
      method: 'get'
    })
  },
  getLatest(deviceSn) {
    return request({
      url: `/sensor/latest/${deviceSn}`,
      method: 'get'
    })
  },
  listFallAlarms() {
    return request({
      url: '/sensor/fall-alarms',
      method: 'get'
    })
  },
  report(data) {
    return request({
      url: '/sensor/report',
      method: 'post',
      data
    })
  }
}
