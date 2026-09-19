<template>
  <div class="page">
    <el-card>
      <PageHeader :title="'健康统计（' + (mode === 'daily' ? '日报' : '周报') + '）'" :subtitle="'当前周期 ' + periodText">
        <el-radio-group v-model="mode" @change="load">
          <el-radio-button value="daily">日报</el-radio-button>
          <el-radio-button value="weekly">周报</el-radio-button>
        </el-radio-group>
        <el-select v-model="deviceSn" placeholder="请选择设备" class="device-select" @change="load">
          <el-option
            v-for="item in deviceList"
            :key="item.deviceSn"
            :label="item.deviceSn + (item.elderName ? ' (' + item.elderName + ')' : '')"
            :value="item.deviceSn"
          />
        </el-select>
        <el-button :loading="loading" @click="load">刷新</el-button>
        <el-button v-if="isAdminUser" type="primary" :loading="rebuilding" @click="rebuild">重算统计</el-button>
      </PageHeader>

      <!-- 口径说明收进 tooltip：这段文字常驻会把标题栏压得很长 -->
      <div class="calc-note section">
        <el-tooltip placement="top" raw-content :content="calcTip">
          <span class="calc-note__label">统计口径 <el-icon><QuestionFilled /></el-icon></span>
        </el-tooltip>
      </div>

      <div class="stat-grid section">
        <StatCard
          label="平均心率"
          :value="current.avgHeartRate"
          unit="次/分"
          tone="danger"
          hint="仅统计有效读数"
        />
        <StatCard
          label="平均血氧"
          :value="current.avgBloodOxygen"
          unit="%"
          tone="primary"
          hint="仅统计有效读数"
        />
        <StatCard label="异常次数" :value="abnormalTotal" unit="次" tone="warning" hint="心率 / 血氧 / 摔倒合计" />
        <StatCard label="在线时长" :value="current.activeMinutes" unit="分钟" tone="success" hint="相邻采样间隔 ≤5 分钟累计" />
      </div>

      <div class="chart-grid section">
        <div class="chart-card">
          <div class="card-title">平均心率趋势</div>
          <TrendChart :labels="labels" :values="heartRates" color="#f53f3f" type="line" />
        </div>
        <div class="chart-card">
          <div class="card-title">平均血氧趋势</div>
          <TrendChart :labels="labels" :values="bloodOxygens" color="#4e6ef2" type="line" />
        </div>
        <div class="chart-card">
          <div class="card-title">异常次数趋势</div>
          <TrendChart :labels="labels" :values="abnormalCounts" color="#ff7d00" type="bar" />
        </div>
        <div class="chart-card">
          <div class="card-title">在线时长趋势（分钟）</div>
          <TrendChart :labels="labels" :values="activeMinutes" color="#36b37e" type="bar" />
        </div>
      </div>

      <el-table
        v-if="mode === 'daily'"
        v-loading="loading"
        :data="rows"
        stripe
        size="small"
        class="section"
      >
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
        <el-table-column prop="activeMinutes" label="在线时长(分钟)" />
        <template #empty>
          <EmptyState text="当前区间暂无统计" hint="可在管理员账号下点「重算统计」回填最近 7 天" />
        </template>
      </el-table>

      <el-table v-else v-loading="loading" :data="rows" stripe size="small" class="section">
        <el-table-column label="周区间" width="200">
          <template #default="{ row }"><span class="mono">{{ row.weekStart }} ~ {{ row.weekEnd }}</span></template>
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
        <el-table-column prop="activeMinutes" label="在线时长(分钟)" />
        <template #empty>
          <EmptyState text="当前区间暂无统计" hint="可在管理员账号下点「重算统计」回填最近 7 天" />
        </template>
      </el-table>
    </el-card>
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { deviceApi, healthStatApi } from '@/api'
import { getUser } from '@/utils/auth'
import PageHeader from '@/components/PageHeader.vue'
import StatCard from '@/components/StatCard.vue'
import EmptyState from '@/components/EmptyState.vue'
import TrendChart from '@/components/TrendChart.vue'

/** 趋势图默认窗口：日报最近 7 天、周报最近 4 周 */
const DAILY_DAYS = 7
const WEEKLY_WEEKS = 4

const CALC_TIP = [
  '平均心率 / 血氧只统计有效读数（0 视为未佩戴，不计入）',
  '异常次数 = 心率 &lt;50 或 &gt;120 + 血氧 &lt;90 + 摔倒',
  '在线时长按相邻采样间隔 ≤5 分钟累计（与设备离线判定同一口径）',
  '数据由服务端每 10 分钟预聚合，管理员可手动重算'
].join('<br>')

const mode = ref('daily')
const deviceSn = ref('')
const deviceList = ref([])
const rows = ref([])
const loading = ref(false)
const rebuilding = ref(false)

// 重算是管理员专属操作，监护人看不到入口
const isAdminUser = computed(() => (getUser() || {}).role === 'ADMIN')

const calcTip = CALC_TIP

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

<style scoped>
.device-select {
  width: 240px;
}

.calc-note {
  margin-top: 14px;
  font-size: 13px;
  color: var(--sc-text-3);
}

.calc-note__label {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  cursor: help;
  border-bottom: 1px dashed var(--sc-border);
}

.chart-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(340px, 1fr));
  gap: var(--sc-gap);
}

.chart-card {
  padding: 14px 16px;
  border: 1px solid var(--sc-border);
  border-radius: var(--sc-radius-card);
  background: var(--sc-surface);
}

.chart-card .card-title {
  margin-bottom: 8px;
}
</style>