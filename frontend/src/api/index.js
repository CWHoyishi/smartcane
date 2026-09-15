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
  delete(id) {
    return request({
      url: `/device/delete/${id}`,
      method: 'delete'
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
