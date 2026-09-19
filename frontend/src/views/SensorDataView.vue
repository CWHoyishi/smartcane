<template>
  <div class="page">
    <el-card>
      <PageHeader title="传感器数据查询" subtitle="按设备与时间范围筛选原始采样记录">
        <el-button type="primary" @click="loadData">查询</el-button>
        <el-button @click="resetQuery">重置</el-button>
      </PageHeader>

      <div class="toolbar section">
        <el-input v-model="queryForm.deviceSn" placeholder="设备序列号" clearable class="w-220" />
        <el-date-picker
          v-model="dateRange"
          type="datetimerange"
          start-placeholder="开始时间"
          end-placeholder="结束时间"
          range-separator="至"
          value-format="YYYY-MM-DD HH:mm:ss"
          class="w-360"
        />
        <el-select v-model="queryForm.fallStatus" placeholder="摔倒状态" clearable class="w-140">
          <el-option label="正常" :value="0" />
          <el-option label="摔倒告警" :value="1" />
        </el-select>
      </div>

      <el-table v-loading="loading" :data="dataList" stripe class="section">
        <el-table-column prop="id" label="ID" width="80" />
        <el-table-column label="设备序列号" min-width="170">
          <template #default="{ row }"><span class="mono">{{ row.deviceSn }}</span></template>
        </el-table-column>
        <el-table-column label="心率(次/分)" width="130">
          <template #default="{ row }">
            <span :class="{ 'value-abnormal': isHeartRateAbnormal(row.heartRate) }">
              {{ row.heartRate ?? '--' }}
            </span>
          </template>
        </el-table-column>
        <el-table-column label="血氧(%)" width="110">
          <template #default="{ row }">
            <span :class="{ 'value-abnormal': isBloodOxygenAbnormal(row.bloodOxygen) }">
              {{ row.bloodOxygen ?? '--' }}
            </span>
          </template>
        </el-table-column>
        <el-table-column label="纬度" width="130">
          <template #default="{ row }"><span class="mono">{{ row.lat ?? '--' }}</span></template>
        </el-table-column>
        <el-table-column label="经度" width="130">
          <template #default="{ row }"><span class="mono">{{ row.lon ?? '--' }}</span></template>
        </el-table-column>
        <el-table-column label="摔倒状态" width="120">
          <template #default="{ row }">
            <StatusTag :status="row.fallStatus === 1 ? 'fall' : 'normal'" />
          </template>
        </el-table-column>
        <el-table-column label="上报时间" min-width="170">
          <template #default="{ row }"><span class="mono">{{ row.reportTime }}</span></template>
        </el-table-column>
        <template #empty>
          <EmptyState text="没有符合条件的采样记录" hint="可放宽时间范围或清空筛选条件" />
        </template>
      </el-table>

      <el-pagination
        v-if="total > 0"
        class="section"
        background
        layout="total, prev, pager, next"
        :current-page="queryForm.pageNum"
        :page-size="queryForm.pageSize"
        :total="total"
        @current-change="handlePageChange"
      />
    </el-card>
  </div>
</template>

<script setup>
import { onMounted, ref } from 'vue'
import { sensorApi } from '@/api'
import PageHeader from '@/components/PageHeader.vue'
import StatusTag from '@/components/StatusTag.vue'
import EmptyState from '@/components/EmptyState.vue'

const queryForm = ref({
  deviceSn: '',
  startTime: null,
  endTime: null,
  fallStatus: null,
  pageNum: 1,
  pageSize: 10
})

/** 界面上是一个时间范围控件，提交给后端时仍拆成 startTime / endTime 两个字段 */
const dateRange = ref([])
const dataList = ref([])
const total = ref(0)
const loading = ref(false)

// 阈值与健康统计口径一致：心率 <50 或 >120 异常；血氧 0 表示未佩戴，不算异常
const isHeartRateAbnormal = (value) => value != null && (value < 50 || value > 120)
const isBloodOxygenAbnormal = (value) => value != null && value > 0 && value < 90

const applyDateRange = () => {
  const valid = Array.isArray(dateRange.value) && dateRange.value.length === 2
  queryForm.value.startTime = valid ? dateRange.value[0] : null
  queryForm.value.endTime = valid ? dateRange.value[1] : null
}

const loadData = async () => {
  applyDateRange()
  loading.value = true
  try {
    const res = await sensorApi.page(queryForm.value)
    dataList.value = res.data.records || []
    total.value = res.data.total || 0
  } finally {
    loading.value = false
  }
}

const handlePageChange = (page) => {
  queryForm.value.pageNum = page
  loadData()
}

const resetQuery = () => {
  queryForm.value = {
    deviceSn: '',
    startTime: null,
    endTime: null,
    fallStatus: null,
    pageNum: 1,
    pageSize: 10
  }
  dateRange.value = []
  loadData()
}

onMounted(() => {
  loadData()
})
</script>

<style scoped>
.w-220 {
  width: 220px;
}

.w-360 {
  width: 360px;
}

.w-140 {
  width: 140px;
}

/* 异常读数只改颜色，不加背景：整列飘红会让表格看起来很吵 */
.value-abnormal {
  color: var(--sc-danger);
  font-weight: 600;
}
</style>