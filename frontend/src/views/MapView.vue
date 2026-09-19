<template>
  <div class="page">
    <el-card>
      <PageHeader title="实时位置地图" subtitle="每 10 秒自动刷新，后端数据源约 60 秒拉取一次">
        <StatusTag :text="positionSummary" tone="primary" />
        <el-button type="primary" :loading="loading" @click="loadLocations">立即刷新</el-button>
      </PageHeader>
      <div ref="mapContainer" class="map-container section"></div>
    </el-card>
  </div>
</template>

<script setup>
import { ref, onMounted, onBeforeUnmount } from 'vue'
import L from 'leaflet'
import 'leaflet/dist/leaflet.css'
import { deviceApi } from '@/api'
import PageHeader from '@/components/PageHeader.vue'
import StatusTag from '@/components/StatusTag.vue'

const DEFAULT_CENTER = [31.2304, 121.4737]
const REFRESH_INTERVAL_MS = 10 * 1000

// 与 tokens.css 保持同值：Leaflet 的标记只能收具体色值，收不到 CSS 变量
const COLOR_FALL = '#f53f3f'
const COLOR_ONLINE = '#4e6ef2'
const COLOR_OFFLINE = '#86909c'

const mapContainer = ref(null)
const positionSummary = ref('加载中...')
const loading = ref(false)
let map = null
let markerLayer = null
let timer = null

const isValidCoordinate = (item) => {
  if (item.lat == null || item.lon == null) {
    return false
  }
  const lat = Number(item.lat)
  const lon = Number(item.lon)
  return Number.isFinite(lat) && Number.isFinite(lon) && lat >= -90 && lat <= 90 && lon >= -180 && lon <= 180
}

const statusText = (item) => {
  if (item.fallStatus === 1) {
    return '摔倒告警'
  }
  return item.deviceStatus === 1 ? '在线' : '离线'
}

const markerColor = (item) => {
  if (item.fallStatus === 1) {
    return COLOR_FALL
  }
  return item.deviceStatus === 1 ? COLOR_ONLINE : COLOR_OFFLINE
}

const popupHtml = (item) => {
  const heartRate = item.heartRate == null ? '--' : item.heartRate
  const bloodOxygen = item.bloodOxygen == null ? '--' : item.bloodOxygen
  const statusColor = markerColor(item)
  return '<div style="min-width:200px;font-size:13px;line-height:1.7">' +
    '<div style="font-weight:600;font-size:14px;margin-bottom:4px">' + (item.elderName || '未绑定老人') + '</div>' +
    '<div style="color:#86909c">设备 ' + item.deviceSn + '</div>' +
    '<div>状态：<span style="color:' + statusColor + ';font-weight:600">' + statusText(item) + '</span></div>' +
    '<div>心率：' + heartRate + ' 次/分</div>' +
    '<div>血氧：' + bloodOxygen + '%</div>' +
    '<div style="color:#86909c">上报：' + (item.reportTime || '--') + '</div>' +
    '</div>'
}

const renderLocations = (items) => {
  markerLayer.clearLayers()
  const valid = items.filter(isValidCoordinate)
  positionSummary.value = '有效定位 ' + valid.length + ' / 设备 ' + items.length
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
  loading.value = true
  try {
    const res = await deviceApi.latestLocations()
    if (res.data && Array.isArray(res.data)) {
      renderLocations(res.data)
    }
  } catch (err) {
    console.warn('[Map] 获取最新位置失败', err)
  } finally {
    loading.value = false
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
  height: calc(100vh - 230px);
  min-height: 480px;
  border: 1px solid var(--sc-border);
  border-radius: var(--sc-radius-card);
  /* Leaflet 的图层会溢出圆角，必须裁掉 */
  overflow: hidden;
}

/* 弹出气泡内的默认字号偏小，压制一下 Leaflet 的默认样式 */
.map-container :deep(.leaflet-popup-content) {
  margin: 12px 14px;
}

.map-container :deep(.leaflet-popup-content-wrapper) {
  border-radius: var(--sc-radius-card);
}
</style>