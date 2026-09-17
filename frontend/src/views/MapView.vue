<template>
  <el-card>
    <template #header>
      <div style="display: flex; align-items: center; justify-content: space-between">
        <span>实时位置地图</span>
        <div style="display: flex; align-items: center; gap: 12px">
          <span style="color: #909399; font-size: 13px">每 10 秒自动刷新，后端数据源约 60 秒拉取一次</span>
          <el-button type="primary" size="small" @click="loadLocations">立即刷新</el-button>
        </div>
      </div>
    </template>
    <div ref="mapContainer" class="map-container"></div>
  </el-card>
</template>

<script setup>
import { ref, onMounted, onBeforeUnmount } from 'vue'
import L from 'leaflet'
import 'leaflet/dist/leaflet.css'
import { deviceApi } from '@/api'

const DEFAULT_CENTER = [31.2304, 121.4737]
const REFRESH_INTERVAL_MS = 10 * 1000

const mapContainer = ref(null)
let map = null
let markerLayer = null
let timer = null

const isValidCoordinate = (item) => {
  if (item.lat == null || item.lon == null) {
    return false
  }
  const lat = Number(item.lat)
  const lon = Number(item.lon)
  // 演示数据存在 lat=0 的占位坐标，直接画会落到赤道，过滤掉
  return Number.isFinite(lat) && Number.isFinite(lon) && Math.abs(lat) > 0.000001
}

const statusText = (item) => {
  if (item.fallStatus === 1) {
    return '摔倒告警'
  }
  return item.deviceStatus === 1 ? '在线' : '离线'
}

const markerColor = (item) => {
  if (item.fallStatus === 1) {
    return '#f56c6c'
  }
  return item.deviceStatus === 1 ? '#409eff' : '#909399'
}

const popupHtml = (item) => {
  const heartRate = item.heartRate == null ? '--' : item.heartRate
  const bloodOxygen = item.bloodOxygen == null ? '--' : item.bloodOxygen
  return '<div style="min-width: 200px">' +
    '<div style="font-weight: 600; margin-bottom: 6px">' + (item.elderName || '未绑定老人') + '</div>' +
    '<div>设备：' + item.deviceSn + '</div>' +
    '<div>状态：' + statusText(item) + '</div>' +
    '<div>心率：' + heartRate + ' 次/分</div>' +
    '<div>血氧：' + bloodOxygen + '%</div>' +
    '<div>上报：' + (item.reportTime || '--') + '</div>' +
  '</div>'
}

const renderLocations = (items) => {
  markerLayer.clearLayers()
  const valid = items.filter(isValidCoordinate)
  if (valid.length === 0) {
    return
  }
  const bounds = L.latLngBounds([])
  valid.forEach((item) => {
    const lat = Number(item.lat)
    const lon = Number(item.lon)
    const color = markerColor(item)
    const marker = L.circleMarker([lat, lon], {
      radius: 8,
      color: color,
      weight: 2,
      fillColor: color,
      fillOpacity: 0.8
    }).bindPopup(popupHtml(item))
    marker.addTo(markerLayer)
    bounds.extend([lat, lon])
  })
  map.fitBounds(bounds, { padding: [30, 30], maxZoom: 15 })
}

const loadLocations = async () => {
  try {
    const res = await deviceApi.latestLocations()
    if (res.data && Array.isArray(res.data)) {
      renderLocations(res.data)
    }
  } catch (err) {
    console.warn('[Map] 获取最新位置失败', err)
  }
}

onMounted(() => {
  map = L.map(mapContainer.value, {
    center: DEFAULT_CENTER,
    zoom: 12
  })
  L.tileLayer('https://webrd0{s}.is.autonavi.com/appmaptile?lang=zh_cn&size=1&scale=1&style=8&x={x}&y={y}&z={z}', {
    subdomains: ['1', '2', '3', '4'],
    maxZoom: 18,
    attribution: '高德地图'
  }).addTo(map)
  markerLayer = L.layerGroup().addTo(map)
  loadLocations()
  timer = setInterval(loadLocations, REFRESH_INTERVAL_MS)
})

onBeforeUnmount(() => {
  if (timer) {
    clearInterval(timer)
    timer = null
  }
  if (map) {
    map.remove()
    map = null
  }
})
</script>

<style scoped>
.map-container {
  height: calc(100vh - 190px);
  min-height: 480px;
  border-radius: 6px;
}
</style>