<template>
  <el-card>
    <template #header>
      <div style="display: flex; justify-content: space-between; align-items: center">
        <span>设备实时监测</span>
        <el-select
          v-model="deviceSn"
          placeholder="请选择设备"
          style="width: 250px"
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
        <el-button type="primary" @click="loadLatest">刷新</el-button>
        <span style="color: #909399; font-size: 13px; margin-left: 10px">
          {{ autoRefresh ? '自动刷新中，' + countdown + 's 后更新' : '自动刷新已暂停' }}
        </span>
      </div>
    </template>
    <div v-if="latestData" style="display: grid; grid-template-columns: repeat(2, 1fr); gap: 20px">
      <el-card shadow="hover">
        <div style="text-align: center">
          <div style="font-size: 16px; color: #606266; margin-bottom: 10px">心率</div>
          <div style="font-size: 48px; font-weight: bold; color: #f56c6c">
            {{ latestData.heartRate || '--' }}
            <span style="font-size: 20px; font-weight: normal">次/分</span>
          </div>
        </div>
      </el-card>
      <el-card shadow="hover">
        <div style="text-align: center">
          <div style="font-size: 16px; color: #606266; margin-bottom: 10px">血氧饱和度</div>
          <div style="font-size: 48px; font-weight: bold; color: #409eff">
            {{ latestData.bloodOxygen || '--' }}
            <span style="font-size: 20px; font-weight: normal">%</span>
          </div>
        </div>
      </el-card>
      <el-card shadow="hover">
        <div style="text-align: center">
          <div style="font-size: 16px; color: #606266; margin-bottom: 10px">位置信息</div>
          <div style="font-size: 18px; color: #303133; line-height: 1.8">
            <div>纬度：{{ latestData.lat != null ? latestData.lat : '--' }}</div>
            <div>经度：{{ latestData.lon != null ? latestData.lon : '--' }}</div>
          </div>
        </div>
      </el-card>
      <el-card shadow="hover">
        <div style="text-align: center">
          <div style="font-size: 16px; color: #606266; margin-bottom: 10px">摔倒状态</div>
          <div :style="{ fontSize: '28px', fontWeight: 'bold', color: latestData.fallStatus === 1 ? '#f56c6c' : '#67c23a' }">
            {{ latestData.fallStatus === 1 ? '⚠ 摔倒告警' : '正常' }}
          </div>
        </div>
      </el-card>
    </div>
    <div v-if="latestData" style="margin-top: 20px; text-align: center; color: #909399">
      最后上报时间：{{ latestData.reportTime }}
    </div>
    <el-empty v-if="!latestData && !loading" description="暂无数据，请输入设备序列号查询" />
  </el-card>
</template>

<script setup>
import { ref, onMounted, onUnmounted } from 'vue'
import { sensorApi, deviceApi } from '@/api'

const deviceSn = ref('')
const latestData = ref(null)
const loading = ref(false)
const autoRefresh = ref(true)
const countdown = ref(10)
const deviceList = ref([])

let timer = null
let countdownTimer = null

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

/** 每10秒自动刷新 */
const startAutoRefresh = () => {
  stopAutoRefresh()
  autoRefresh.value = true
  countdown.value = 10
  // 倒计时
  countdownTimer = setInterval(() => {
    if (countdown.value > 1) {
      countdown.value--
    } else {
      countdown.value = 10
    }
  }, 1000)
  // 定时拉取
  timer = setInterval(() => {
    loadLatest()
  }, 10000)
}

const stopAutoRefresh = () => {
  autoRefresh.value = false
  if (timer) { clearInterval(timer); timer = null }
  if (countdownTimer) { clearInterval(countdownTimer); countdownTimer = null }
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

onMounted(() => {
  loadDeviceList()
  startAutoRefresh()
})

onUnmounted(() => {
  stopAutoRefresh()
})
</script>
