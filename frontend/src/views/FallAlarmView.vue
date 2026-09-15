<template>
  <el-card>
    <template #header>
      <div style="display: flex; justify-content: space-between; align-items: center">
        <span>摔倒告警记录</span>
        <el-button type="primary" @click="loadAlarms">刷新</el-button>
      </div>
    </template>
    <el-alert
      v-if="alarmList.length > 0"
      title="检测到摔倒告警！请及时联系监护人和老人。"
      type="error"
      :closable="false"
      style="margin-bottom: 20px"
    />
    <el-table :data="alarmList" border style="width: 100%">
      <el-table-column prop="id" label="ID" width="80" />
      <el-table-column prop="deviceSn" label="设备序列号" width="180" />
      <el-table-column prop="heartRate" label="心率(次/分)" width="120" />
      <el-table-column prop="bloodOxygen" label="血氧(%)" width="100" />
      <el-table-column prop="lat" label="纬度" width="130" />
      <el-table-column prop="lon" label="经度" width="130" />
      <el-table-column prop="reportTime" label="告警时间" width="200" />
    </el-table>
    <el-empty v-if="alarmList.length === 0" description="暂无摔倒告警记录" />
  </el-card>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { sensorApi } from '@/api'

const alarmList = ref([])

const loadAlarms = async () => {
  const res = await sensorApi.listFallAlarms()
  alarmList.value = res.data || []
}

onMounted(() => {
  loadAlarms()
})
</script>
