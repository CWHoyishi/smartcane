<template>
  <el-card>
    <template #header>
      <div style="display: flex; justify-content: space-between; align-items: center; gap: 12px">
        <span>健康统计（{{ mode === 'daily' ? '日报' : '周报' }}）</span>
        <div style="display: flex; align-items: center; gap: 12px">
          <el-radio-group v-model="mode" @change="load">
            <el-radio-button value="daily">日报</el-radio-button>
            <el-radio-button value="weekly">周报</el-radio-button>
          </el-radio-group>
          <el-select
            v-model="deviceSn"
            placeholder="请选择设备"
            style="width: 250px"
            @change="load"
          >
            <el-option
              v-for="item in deviceList"
              :key="item.deviceSn"
              :label="item.deviceSn + (item.elderName ? ' (' + item.elderName + ')' : '')"
              :value="item.deviceSn"
            />
          </el-select>
          <el-button :loading="loading" @click="load">刷新</el-button>
          <el-button type="primary" :loading="rebuilding" @click="rebuild">重算统计</el-button>
        </div>
      </div>
    </template>

    <div style="color: #909399; font-size: 13px; margin-bottom: 16px">
      统计口径：平均心率/血氧取有效读数（0 视为未佩戴不计入），异常次数统计心率 &lt;50 或 &gt;120、血氧 &lt;90、摔倒；活动时长按相邻采样间隔 ≤5 分钟且位移 ≥10 米累计。
      数据由服务端定时预聚合（每 10 分钟重算今天与昨天），当前周期：{{ periodText }}
    </div>

    <div style="display: grid; grid-template-columns: repeat(4, 1fr); gap: 16px">
      <el-card shadow="hover">
        <div style="text-align: center">
          <div style="font-size: 14px; color: #606266; margin-bottom: 8px">平均心率</div>
          <div style="font-size: 32px; font-weight: bold; color: #f56c6c">
            {{ current.avgHeartRate ?? '--' }}
            <span style="font-size: 14px; font-weight: normal">次/分</span>
          </div>
        </div>
      </el-card>
      <el-card shadow="hover">
        <div style="text-align: center">
          <div style="font-size: 14px; color: #606266; margin-bottom: 8px">平均血氧</div>
          <div style="font-size: 32px; font-weight: bold; color: #409eff">
            {{ current.avgBloodOxygen ?? '--' }}
            <span style="font-size: 14px; font-weight: normal">%</span>
          </div>
        </div>
      </el-card>
      <el-card shadow="hover">
        <div style="text-align: center">
          <div style="font-size: 14px; color: #606266; margin-bottom: 8px">异常次数</div>
          <div style="font-size: 32px; font-weight: bold; color: #e6a23c">
            {{ abnormalTotal }}
            <span style="font-size: 14px; font-weight: normal">次</span>
          </div>
        </div>
      </el-card>
      <el-card shadow="hover">
        <div style="text-align: center">
          <div style="font-size: 14px; color: #606266; margin-bottom: 8px">活动时长</div>
          <div style="font-size: 32px; font-weight: bold; color: #67c23a">
            {{ current.activeMinutes ?? '--' }}
            <span style="font-size: 14px; font-weight: normal">分钟</span>
          </div>
        </div>
      </el-card>
    </div>

    <div style="display: grid; grid-template-columns: repeat(2, 1fr); gap: 16px; margin-top: 16px">
      <el-card shadow="never">
        <div style="font-size: 14px; color: #606266; margin-bottom: 8px">平均心率趋势</div>
        <TrendChart :labels="labels" :values="heartRates" color="#f56c6c" type="line" />
      </el-card>
      <el-card shadow="never">
        <div style="font-size: 14px; color: #606266; margin-bottom: 8px">平均血氧趋势</div>
        <TrendChart :labels="labels" :values="bloodOxygens" color="#409eff" type="line" />
      </el-card>
      <el-card shadow="never">
        <div style="font-size: 14px; color: #606266; margin-bottom: 8px">异常次数趋势</div>
        <TrendChart :labels="labels" :values="abnormalCounts" color="#e6a23c" type="bar" />
      </el-card>
      <el-card shadow="never">
        <div style="font-size: 14px; color: #606266; margin-bottom: 8px">活动时长趋势（分钟）</div>
        <TrendChart :labels="labels" :values="activeMinutes" color="#67c23a" type="bar" />
      </el-card>
    </div>

    <el-table v-if="mode === 'daily'" :data="rows" border size="small" style="margin-top: 16px">
      <el-table-column prop="statDate" label="日期" width="120" />
      <el-table-column prop="sampleCount" label="采样条数" width="100" />
      <el-table-column label="平均心率" width="110">
        <template #default="{ row }">{{ row.avgHeartRate ?? '--' }}</template>
      </el-table-column>
      <el-table-column label="平均血氧" width="110">
        <template #default="{ row }">{{ row.avgBloodOxygen ?? '--' }}</template>
      </el-table-column>
      <el-table-column prop="fallCount" label="摔倒" width="90" />
      <el-table-column prop="heartRateAbnormalCount" label="心率异常" width="110" />
      <el-table-column prop="bloodOxygenAbnormalCount" label="血氧异常" width="110" />
      <el-table-column prop="activeMinutes" label="活动时长(分钟)" />
    </el-table>

    <el-table v-else :data="rows" border size="small" style="margin-top: 16px">
      <el-table-column label="周区间" width="200">
        <template #default="{ row }">{{ row.weekStart }} ~ {{ row.weekEnd }}</template>
      </el-table-column>
      <el-table-column prop="statDays" label="统计天数" width="100" />
      <el-table-column prop="sampleCount" label="采样条数" width="100" />
      <el-table-column label="平均心率" width="110">
        <template #default="{ row }">{{ row.avgHeartRate ?? '--' }}</template>
      </el-table-column>
      <el-table-column label="平均血氧" width="110">
        <template #default="{ row }">{{ row.avgBloodOxygen ?? '--' }}</template>
      </el-table-column>
      <el-table-column prop="fallCount" label="摔倒" width="90" />
      <el-table-column prop="heartRateAbnormalCount" label="心率异常" width="110" />
      <el-table-column prop="bloodOxygenAbnormalCount" label="血氧异常" width="110" />
      <el-table-column prop="activeMinutes" label="活动时长(分钟)" />
    </el-table>
  </el-card>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { deviceApi, healthStatApi } from '@/api'
import TrendChart from '@/components/TrendChart.vue'

/** 趋势图默认窗口：日报最近 7 天、周报最近 4 周 */
const DAILY_DAYS = 7
const WEEKLY_WEEKS = 4

const mode = ref('daily')
const deviceSn = ref('')
const deviceList = ref([])
const rows = ref([])
const loading = ref(false)
const rebuilding = ref(false)

/** 概览卡片取区间内最后一个统计周期（日报=今天，周报=本周） */
const current = computed(() => rows.value[rows.value.length - 1] || {})

const periodText = computed(() => {
  if (mode.value === 'daily') {
    return current.value.statDate || '--'
  }
  return current.value.weekStart ? `${current.value.weekStart} ~ ${current.value.weekEnd}` : '--'
})

const labels = computed(() =>
  rows.value.map((row) => (mode.value === 'daily' ? row.statDate : row.weekStart).slice(5))
)

const heartRates = computed(() => rows.value.map((row) => row.avgHeartRate))
const bloodOxygens = computed(() => rows.value.map((row) => row.avgBloodOxygen))
const abnormalCounts = computed(() =>
  rows.value.map(
    (row) => (row.fallCount || 0) + (row.heartRateAbnormalCount || 0) + (row.bloodOxygenAbnormalCount || 0)
  )
)
const activeMinutes = computed(() => rows.value.map((row) => row.activeMinutes))
const abnormalTotal = computed(
  () =>
    (current.value.fallCount || 0) +
    (current.value.heartRateAbnormalCount || 0) +
    (current.value.bloodOxygenAbnormalCount || 0)
)

const loadDeviceList = async () => {
  try {
    const res = await deviceApi.list()
    deviceList.value = res.data || []
    if (!deviceSn.value && deviceList.value.length > 0) {
      deviceSn.value = deviceList.value[0].deviceSn
    }
  } catch (e) {
    console.error('加载设备列表失败', e)
  }
}

const load = async () => {
  if (!deviceSn.value) {
    return
  }
  loading.value = true
  try {
    const res =
      mode.value === 'daily'
        ? await healthStatApi.daily(deviceSn.value, DAILY_DAYS)
        : await healthStatApi.weekly(deviceSn.value, WEEKLY_WEEKS)
    rows.value = res.data || []
  } catch (e) {
    rows.value = []
  } finally {
    loading.value = false
  }
}

/** 手动重算：服务端按天扫采样明细，日期取最近 7 天，用于回填或演示时立即出数 */
const rebuild = async () => {
  rebuilding.value = true
  try {
    const res = await healthStatApi.rebuild(DAILY_DAYS)
    ElMessage.success(`已重算 ${res.data} 行统计`)
    await load()
  } catch (e) {
    console.error('重算统计失败', e)
  } finally {
    rebuilding.value = false
  }
}

onMounted(async () => {
  await loadDeviceList()
  await load()
})
</script>