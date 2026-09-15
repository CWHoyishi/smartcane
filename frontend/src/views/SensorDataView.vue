<template>
  <el-card>
    <template #header>
      <span>传感器数据查询</span>
    </template>
    <el-form :inline="true" :model="queryForm" style="margin-bottom: 20px">
      <el-form-item label="设备序列号">
        <el-input v-model="queryForm.deviceSn" placeholder="请输入设备序列号" clearable />
      </el-form-item>
      <el-form-item label="开始时间">
        <el-date-picker
          v-model="queryForm.startTime"
          type="datetime"
          placeholder="选择开始时间"
          value-format="YYYY-MM-DD HH:mm:ss"
        />
      </el-form-item>
      <el-form-item label="结束时间">
        <el-date-picker
          v-model="queryForm.endTime"
          type="datetime"
          placeholder="选择结束时间"
          value-format="YYYY-MM-DD HH:mm:ss"
        />
      </el-form-item>
      <el-form-item label="摔倒状态">
        <el-select v-model="queryForm.fallStatus" placeholder="全部" clearable style="width: 120px">
          <el-option label="正常" :value="0" />
          <el-option label="摔倒告警" :value="1" />
        </el-select>
      </el-form-item>
      <el-form-item>
        <el-button type="primary" @click="loadData">查询</el-button>
        <el-button @click="resetQuery">重置</el-button>
      </el-form-item>
    </el-form>
    <el-table :data="dataList" border style="width: 100%">
      <el-table-column prop="id" label="ID" width="80" />
      <el-table-column prop="deviceSn" label="设备序列号" width="180" />
      <el-table-column prop="heartRate" label="心率(次/分)" width="120" />
      <el-table-column prop="bloodOxygen" label="血氧(%)" width="100" />
      <el-table-column prop="lat" label="纬度" width="130" />
      <el-table-column prop="lon" label="经度" width="130" />
      <el-table-column prop="fallStatus" label="摔倒状态" width="100">
        <template #default="{ row }">
          <el-tag :type="row.fallStatus === 1 ? 'danger' : 'success'">
            {{ row.fallStatus === 1 ? '摔倒告警' : '正常' }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="reportTime" label="上报时间" width="200" />
    </el-table>
    <el-pagination
      style="margin-top: 20px; justify-content: flex-end; display: flex"
      :current-page="queryForm.pageNum"
      :page-size="queryForm.pageSize"
      :total="total"
      layout="total, prev, pager, next"
      @current-change="handlePageChange"
    />
  </el-card>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { sensorApi } from '@/api'

const queryForm = ref({
  deviceSn: '',
  startTime: null,
  endTime: null,
  fallStatus: null,
  pageNum: 1,
  pageSize: 10
})

const dataList = ref([])
const total = ref(0)

const loadData = async () => {
  const res = await sensorApi.page(queryForm.value)
  dataList.value = res.data.records || []
  total.value = res.data.total || 0
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
  loadData()
}

onMounted(() => {
  loadData()
})
</script>
