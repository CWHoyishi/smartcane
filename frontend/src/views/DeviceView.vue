<template>
  <el-card>
    <template #header>
      <div style="display: flex; justify-content: space-between; align-items: center">
        <span>设备列表</span>
        <el-button type="primary" @click="openDialog()">新增设备</el-button>
      </div>
    </template>
    <el-table :data="deviceList" border style="width: 100%">
      <el-table-column prop="id" label="ID" width="80" />
      <el-table-column prop="deviceSn" label="设备序列号" width="200" />
      <el-table-column prop="elderName" label="绑定老人" width="150" />
      <el-table-column prop="elderPhone" label="联系电话" width="150" />
      <el-table-column prop="guardianPhone" label="监护人电话" width="150" />
      <el-table-column prop="deviceStatus" label="在线状态" width="100">
        <template #default="{ row }">
          <el-tag :type="row.deviceStatus === 1 ? 'success' : 'info'">
            {{ row.deviceStatus === 1 ? '在线' : '离线' }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="createTime" label="录入时间" width="200" />
      <el-table-column label="操作" width="180">
        <template #default="{ row }">
          <el-button type="primary" size="small" @click="openDialog(row)">编辑</el-button>
          <el-button type="danger" size="small" @click="deleteDevice(row)">删除</el-button>
        </template>
      </el-table-column>
    </el-table>
    <el-dialog v-model="dialogVisible" :title="isEdit ? '编辑设备' : '新增设备'" width="500px">
      <el-form :model="deviceForm" label-width="110px">
        <el-form-item label="设备序列号">
          <el-input v-model="deviceForm.deviceSn" :disabled="isEdit" />
        </el-form-item>
        <el-form-item label="绑定老人姓名">
          <el-input v-model="deviceForm.elderName" />
        </el-form-item>
        <el-form-item label="老人联系电话">
          <el-input v-model="deviceForm.elderPhone" />
        </el-form-item>
        <el-form-item label="监护人电话">
          <el-input v-model="deviceForm.guardianPhone" />
        </el-form-item>
        <el-form-item label="在线状态">
          <el-switch
            v-model="deviceForm.deviceStatus"
            :active-value="1"
            :inactive-value="0"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" @click="saveDevice">保存</el-button>
      </template>
    </el-dialog>
  </el-card>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { deviceApi } from '@/api'

const deviceList = ref([])
const dialogVisible = ref(false)
const isEdit = ref(false)
const deviceForm = ref({
  id: null,
  deviceSn: '',
  elderName: '',
  elderPhone: '',
  guardianPhone: '',
  deviceStatus: 1
})

const loadList = async () => {
  const res = await deviceApi.list()
  deviceList.value = res.data || []
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
      guardianPhone: '',
      deviceStatus: 1
    }
  }
  dialogVisible.value = true
}

const saveDevice = async () => {
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
  }
}

const deleteDevice = async (row) => {
  try {
    await ElMessageBox.confirm('确定要删除该设备吗？', '提示', {
      confirmButtonText: '确定',
      cancelButtonText: '取消',
      type: 'warning'
    })
    await deviceApi.delete(row.id)
    ElMessage.success('删除成功')
    loadList()
  } catch (e) {
    console.error(e)
  }
}

onMounted(() => {
  loadList()
})
</script>
