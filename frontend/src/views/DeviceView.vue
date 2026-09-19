<template>
  <div class="page">
    <el-card>
      <PageHeader title="设备管理" subtitle="录入设备并维护监护人联系方式">
        <el-button type="primary" @click="openDialog()">新增设备</el-button>
      </PageHeader>

      <div class="stat-grid section">
        <StatCard label="设备总数" :value="deviceList.length" unit="台" hint="含在线与离线" />
        <StatCard label="在线设备" :value="onlineCount" unit="台" tone="success" hint="最近一次上报正常的设备" />
        <StatCard label="离线设备" :value="offlineCount" unit="台" tone="warning" hint="需检查设备供电或网络" />
      </div>

      <el-table v-loading="loading" :data="deviceList" stripe class="section">
        <el-table-column prop="id" label="ID" width="80" />
        <el-table-column label="设备序列号" min-width="180">
          <template #default="{ row }"><span class="mono">{{ row.deviceSn }}</span></template>
        </el-table-column>
        <el-table-column prop="elderName" label="绑定老人" width="120">
          <template #default="{ row }">{{ row.elderName || '--' }}</template>
        </el-table-column>
        <el-table-column prop="elderPhone" label="联系电话" width="140">
          <template #default="{ row }"><span class="mono">{{ row.elderPhone || '--' }}</span></template>
        </el-table-column>
        <el-table-column prop="guardianPhone" label="监护人电话" width="140">
          <template #default="{ row }"><span class="mono">{{ row.guardianPhone || '--' }}</span></template>
        </el-table-column>
        <el-table-column label="在线状态" width="110">
          <template #default="{ row }">
            <StatusTag :status="row.deviceStatus === 1 ? 'online' : 'offline'" />
          </template>
        </el-table-column>
        <el-table-column label="最后上报" min-width="170">
          <template #default="{ row }"><span class="mono">{{ row.lastReportTime || '--' }}</span></template>
        </el-table-column>
        <el-table-column label="录入时间" min-width="170">
          <template #default="{ row }"><span class="mono">{{ row.createTime || '--' }}</span></template>
        </el-table-column>
        <el-table-column label="操作" width="130" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="openDialog(row)">编辑</el-button>
            <el-button link type="danger" @click="deleteDevice(row)">删除</el-button>
          </template>
        </el-table-column>
        <template #empty>
          <EmptyState text="还没有录入设备" hint="点击右上角「新增设备」开始录入">
            <el-button type="primary" @click="openDialog()">新增设备</el-button>
          </EmptyState>
        </template>
      </el-table>
    </el-card>

    <el-dialog v-model="dialogVisible" :title="isEdit ? '编辑设备' : '新增设备'" width="480px">
      <el-form :model="deviceForm" label-width="110px">
        <el-form-item label="设备序列号">
          <el-input v-model="deviceForm.deviceSn" :disabled="isEdit" placeholder="云平台分配的 DeviceId" />
        </el-form-item>
        <el-form-item label="绑定老人姓名">
          <el-input v-model="deviceForm.elderName" placeholder="如：张三" />
        </el-form-item>
        <el-form-item label="老人联系电话">
          <el-input v-model="deviceForm.elderPhone" maxlength="11" placeholder="11 位手机号" />
        </el-form-item>
        <el-form-item label="监护人电话">
          <el-input v-model="deviceForm.guardianPhone" maxlength="11" placeholder="用于接收摔倒告警短信" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="saveDevice">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { deviceApi } from '@/api'
import PageHeader from '@/components/PageHeader.vue'
import StatCard from '@/components/StatCard.vue'
import StatusTag from '@/components/StatusTag.vue'
import EmptyState from '@/components/EmptyState.vue'

const deviceList = ref([])
const loading = ref(false)
const saving = ref(false)
const dialogVisible = ref(false)
const isEdit = ref(false)
const deviceForm = ref({
  id: null,
  deviceSn: '',
  elderName: '',
  elderPhone: '',
  guardianPhone: ''
})

const onlineCount = computed(() => deviceList.value.filter((item) => item.deviceStatus === 1).length)
const offlineCount = computed(() => deviceList.value.length - onlineCount.value)

const loadList = async () => {
  loading.value = true
  try {
    const res = await deviceApi.list()
    deviceList.value = res.data || []
  } finally {
    loading.value = false
  }
}

const openDialog = (row = null) => {
  if (row) {
    isEdit.value = true
    deviceForm.value = { ...row }
  } else {
    isEdit.value = false
    deviceForm.value = {
      id: null,
      deviceSn: '',
      elderName: '',
      elderPhone: '',
      guardianPhone: ''
    }
  }
  dialogVisible.value = true
}

const saveDevice = async () => {
  saving.value = true
  try {
    if (isEdit.value) {
      await deviceApi.update(deviceForm.value)
      ElMessage.success('更新成功')
    } else {
      await deviceApi.add(deviceForm.value)
      ElMessage.success('添加成功')
    }
    dialogVisible.value = false
    loadList()
  } catch (e) {
    console.error(e)
  } finally {
    saving.value = false
  }
}

const deleteDevice = async (row) => {
  try {
    await ElMessageBox.confirm('确定要删除该设备吗？', '提示', {
      confirmButtonText: '确定',
      cancelButtonText: '取消',
      type: 'warning'
    })
  } catch (e) {
    return
  }
  try {
    await deviceApi.delete(row.id)
    ElMessage.success('删除成功')
    loadList()
  } catch (e) {
    // 设备仍有历史数据时后端返回 409 拒绝删除，需操作者再确认一次才带 force 删除
    if (e.code !== 409) {
      return
    }
    try {
      await ElMessageBox.confirm(e.message, '该设备存在历史数据', {
        confirmButtonText: '一并删除',
        cancelButtonText: '取消',
        type: 'error'
      })
      await deviceApi.delete(row.id, true)
      ElMessage.success('删除成功')
      loadList()
    } catch (err) {
      console.error(err)
    }
  }
}

onMounted(() => {
  loadList()
})
</script>