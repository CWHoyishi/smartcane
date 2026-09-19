<template>
  <div class="page">
    <el-card>
      <PageHeader title="设备实时监测" :subtitle="refreshHint">
        <el-select
          v-model="deviceSn"
          placeholder="请选择设备"
          class="device-select"
          clearable
          @change="onDeviceChange"
        >
          <el-option
            v-for="item in deviceList"
            :key="item.deviceSn"
            :label="item.deviceSn + (item.elderName ? ' (' + item.elderName + ')' : '')"
            :value="item.deviceSn"
          />
        </el-select>
        <el-button @click="toggleAutoRefresh">{{ autoRefresh ? '暂停刷新' : '开启刷新' }}</el-button>
        <el-button type="primary" :loading="loading" @click="loadLatest">立即刷新</el-button>
      </PageHeader>

      <template v-if="latestData">
        <div class="stat-grid section">
          <StatCard
            label="心率"
            :value="latestData.heartRate"
            unit="次/分"
            tone="danger"
            hint="有效区间 50 ~ 120 次/分"
          />
          <StatCard
            label="血氧饱和度"
            :value="latestData.bloodOxygen"
            unit="%"
            tone="primary"
            hint="正常不低于 90%"
          />
          <StatCard label="摔倒状态" :value="fallText" :tone="fallTone" hint="由 MPU6050 倾角判定" />
        </div>

        <div class="location section">
          <div class="location__head">
            <span class="card-title">位置信息</span>
            <StatusTag :status="hasLocation ? 'online' : 'offline'" :text="hasLocation ? '有定位' : '暂无定位'" />
            <span class="toolbar__spacer"></span>
            <span class="text-muted location__time">最后上报 {{ latestData.reportTime || '--' }}</span>
          </div>
          <div class="location__body">
            <div class="coord">
              <span class="coord__label">纬度 Lat</span>
              <span class="coord__value mono">{{ hasLocation ? latestData.lat : '--' }}</span>
            </div>
            <div class="coord">
              <span class="coord__label">经度 Lon</span>
              <span class="coord__value mono">{{ hasLocation ? latestData.lon : '--' }}</span>
            </div>
            <el-button v-if="hasLocation" link type="primary" @click="copyCoords">复制坐标</el-button>
            <router-link v-if="hasLocation" class="location__link" to="/map">在地图上查看</router-link>
          </div>
        </div>
      </template>

      <EmptyState
        v-else-if="!loading"
        text="暂无监测数据"
        hint="请选择设备，或确认设备已向平台上报数据"
      />
    </el-card>
  </div>
</template>

<script setup>
import { computed, onMounted, onUnmounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { sensorApi, deviceApi } from '@/api'
import PageHeader from '@/components/PageHeader.vue'
import StatCard from '@/components/StatCard.vue'
import StatusTag from '@/components/StatusTag.vue'
import EmptyState from '@/components/EmptyState.vue'

const REFRESH_SECONDS = 10

const deviceSn = ref('')
const latestData = ref(null)
const loading = ref(false)
const autoRefresh = ref(true)
const countdown = ref(REFRESH_SECONDS)
const deviceList = ref([])

let timer = null
let countdownTimer = null

/** lat=0 也是有效坐标（不能当成"无定位"过滤掉），只用 null/undefined 判断是否存在 */
const hasLocation = computed(() => {
  const data = latestData.value
  return !!data && data.lat != null && data.lon != null
})

const fallText = computed(() => (latestData.value?.fallStatus === 1 ? '摔倒告警' : '正常'))
const fallTone = computed(() => (latestData.value?.fallStatus === 1 ? 'danger' : 'success'))
const refreshHint = computed(() =>
  autoRefresh.value ? `自动刷新中，${countdown.value}s 后更新` : '自动刷新已暂停'
)

/** 加载设备下拉列表 */
const loadDeviceList = async () => {
  try {
    const res = await deviceApi.list()
    deviceList.value = res.data || []
    // 自动选中第一个设备
    if (deviceList.value.length > 0 && !deviceSn.value) {
      deviceSn.value = deviceList.value[0].deviceSn
      loadLatest()
    }
  } catch (e) {
    console.error('加载设备列表失败', e)
  }
}

/** 设备切换时立即刷新 */
const onDeviceChange = () => {
  if (deviceSn.value) {
    loadLatest()
  } else {
    latestData.value = null
  }
}

const stopAutoRefresh = () => {
  autoRefresh.value = false
  if (timer) {
    clearInterval(timer)
    timer = null
  }
  if (countdownTimer) {
    clearInterval(countdownTimer)
    countdownTimer = null
  }
}

/** 每10秒自动刷新 */
const startAutoRefresh = () => {
  stopAutoRefresh()
  autoRefresh.value = true
  countdown.value = REFRESH_SECONDS
  countdownTimer = setInterval(() => {
    countdown.value = countdown.value > 1 ? countdown.value - 1 : REFRESH_SECONDS
  }, 1000)
  timer = setInterval(() => {
    loadLatest()
  }, REFRESH_SECONDS * 1000)
}

const toggleAutoRefresh = () => {
  if (autoRefresh.value) {
    stopAutoRefresh()
  } else {
    startAutoRefresh()
  }
}

const loadLatest = async () => {
  if (!deviceSn.value) {
    return
  }
  try {
    loading.value = true
    const res = await sensorApi.getLatest(deviceSn.value)
    latestData.value = res.data
  } catch (e) {
    latestData.value = null
  } finally {
    loading.value = false
  }
}

const copyCoords = async () => {
  const text = latestData.value.lat + ', ' + latestData.value.lon
  try {
    await navigator.clipboard.writeText(text)
    ElMessage.success('坐标已复制')
  } catch (e) {
    // 剪贴板 API 只在 https/localhost 下可用，内网 http 部署时会走到这里
    ElMessage.warning('复制失败，请手动记录：' + text)
  }
}

onMounted(() => {
  loadDeviceList()
  startAutoRefresh()
})

onUnmounted(() => {
  stopAutoRefresh()
})
</script>

<style scoped>
.device-select {
  width: 240px;
}

.location__head {
  display: flex;
  align-items: center;
  gap: 10px;
}

.location__time {
  font-size: 12px;
}

.location__body {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 12px 32px;
  margin-top: 14px;
}

.coord {
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.coord__label {
  font-size: 12px;
  color: var(--sc-text-3);
}

.coord__value {
  font-size: 20px;
  font-weight: 600;
  color: var(--sc-text-1);
}

.location__link {
  font-size: 13px;
  color: var(--sc-primary);
  text-decoration: none;
}

.location__link:hover {
  text-decoration: underline;
}
</style>