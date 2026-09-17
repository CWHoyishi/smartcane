<template>
  <el-card>
    <template #header>
      <div style="display: flex; justify-content: space-between; align-items: center">
        <span>告警处理</span>
        <div style="display: flex; align-items: center; gap: 12px">
          <el-radio-group v-model="query.status" @change="handleFilterChange">
            <el-radio-button :value="null">全部</el-radio-button>
            <el-radio-button :value="0">待处理</el-radio-button>
            <el-radio-button :value="1">已确认</el-radio-button>
            <el-radio-button :value="2">误报</el-radio-button>
          </el-radio-group>
          <el-button type="primary" @click="loadAlarms">刷新</el-button>
        </div>
      </div>
    </template>

    <el-alert
      v-if="pendingCount > 0"
      :title="`有 ${pendingCount} 条待处理告警，请尽快联系老人或监护人确认。`"
      type="error"
      :closable="false"
      style="margin-bottom: 20px"
    />

    <el-table v-loading="loading" :data="rows" border style="width: 100%">
      <el-table-column prop="reportTime" label="告警时间" width="180" />
      <el-table-column prop="deviceSn" label="设备序列号" width="170" />
      <el-table-column label="老人" width="110">
        <template #default="{ row }">{{ elderName(row.deviceSn) }}</template>
      </el-table-column>
      <el-table-column label="类型" width="110">
        <template #default="{ row }">
          <el-tag :type="row.alarmType === 'FALL' ? 'danger' : 'warning'">
            {{ alarmTypeText(row.alarmType) }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="级别" width="90">
        <template #default="{ row }">{{ levelText(row.level) }}</template>
      </el-table-column>
      <el-table-column label="状态" width="100">
        <template #default="{ row }">
          <el-tag :type="statusTagType(row.status)">{{ statusText(row.status) }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="alarmValue" label="触发依据" width="140" />
      <el-table-column label="处理情况" min-width="200">
        <template #default="{ row }">
          <template v-if="row.status === 0">--</template>
          <template v-else>
            {{ row.handleTime }}
            <span v-if="row.handledBy"> · {{ row.handledBy }}</span>
            <div v-if="row.handleNote" style="color: #909399">{{ row.handleNote }}</div>
          </template>
        </template>
      </el-table-column>
      <el-table-column label="操作" width="190" fixed="right">
        <template #default="{ row }">
          <template v-if="row.status === 0">
            <el-button link type="primary" @click="openHandle(row, 1)">确认已处置</el-button>
            <el-button link type="warning" @click="openHandle(row, 2)">标记误报</el-button>
          </template>
          <span v-else style="color: #909399">已处理</span>
        </template>
      </el-table-column>
    </el-table>

    <el-empty v-if="!loading && rows.length === 0" description="暂无告警记录" />

    <el-pagination
      style="margin-top: 20px; justify-content: flex-end; display: flex"
      :current-page="query.pageNum"
      :page-size="query.pageSize"
      :total="total"
      layout="total, prev, pager, next"
      @current-change="handlePageChange"
    />

    <el-dialog
      v-model="handleVisible"
      :title="handleForm.status === 1 ? '确认已处置' : '标记误报'"
      width="440px"
    >
      <el-form label-width="80px">
        <el-form-item label="告警">
          <span>{{ currentRow ? `${currentRow.reportTime} ${currentRow.deviceSn}` : '' }}</span>
        </el-form-item>
        <el-form-item label="处理人">
          <el-input v-model="handleForm.handledBy" placeholder="如：家属 张三" maxlength="32" />
        </el-form-item>
        <el-form-item label="备注">
          <el-input
            v-model="handleForm.handleNote"
            type="textarea"
            :rows="3"
            maxlength="200"
            show-word-limit
            placeholder="选填，如「已电话确认，老人无碍」"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="handleVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="submitHandle">提交</el-button>
      </template>
    </el-dialog>
  </el-card>
</template>

<script setup>
import { reactive, ref, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { alarmApi, deviceApi } from '@/api'

/** 状态与级别的展示映射：后端存的是数字，界面上不能直接给用户看数字 */
const STATUS_TEXT = { 0: '待处理', 1: '已确认', 2: '误报' }
const STATUS_TAG = { 0: 'danger', 1: 'success', 2: 'info' }

const loading = ref(false)
const submitting = ref(false)
const rows = ref([])
const total = ref(0)
const pendingCount = ref(0)
/** deviceSn → 老人姓名：列表里显示姓名比显示设备号好认 */
const elderNames = ref({})

const query = reactive({ status: null, pageNum: 1, pageSize: 10 })

const handleVisible = ref(false)
const currentRow = ref(null)
const handleForm = reactive({ id: null, status: 1, handledBy: '', handleNote: '' })

const statusText = (status) => STATUS_TEXT[status] ?? '未知'
const statusTagType = (status) => STATUS_TAG[status] ?? 'info'
const levelText = (level) => (level === 3 ? '紧急' : level === 2 ? '重要' : '--')
const alarmTypeText = (type) => (type === 'FALL' ? '摔倒' : type || '未知')
const elderName = (deviceSn) => elderNames.value[deviceSn] || '--'

const loadDevices = async () => {
  try {
    const res = await deviceApi.list()
    const map = {}
    for (const device of res.data || []) {
      map[device.deviceSn] = device.elderName || ''
    }
    elderNames.value = map
  } catch (e) {
    // 老人姓名只是辅助信息，取不到不影响告警列表与处置
    console.warn('[Alarm] 设备列表加载失败:', e.message)
  }
}

const loadPendingCount = async () => {
  try {
    const res = await alarmApi.listPending()
    pendingCount.value = (res.data || []).length
  } catch (e) {
    console.warn('[Alarm] 待处理数量加载失败:', e.message)
  }
}

const loadAlarms = async () => {
  loading.value = true
  try {
    const res = await alarmApi.page({ ...query })
    rows.value = res.data?.records || []
    total.value = res.data?.total || 0
  } finally {
    loading.value = false
  }
  // 待处理数量不受当前筛选影响，刷新时一并更新
  loadPendingCount()
}

const handleFilterChange = () => {
  query.pageNum = 1
  loadAlarms()
}

const handlePageChange = (pageNum) => {
  query.pageNum = pageNum
  loadAlarms()
}

const openHandle = (row, status) => {
  currentRow.value = row
  handleForm.id = row.id
  handleForm.status = status
  handleForm.handledBy = ''
  handleForm.handleNote = ''
  handleVisible.value = true
}

const submitHandle = async () => {
  submitting.value = true
  try {
    await alarmApi.ack({ ...handleForm })
    ElMessage.success(handleForm.status === 1 ? '已确认处置' : '已标记为误报')
    handleVisible.value = false
    loadAlarms()
  } catch (e) {
    // 业务错误（如「该告警已处理」）已由 request 拦截器弹出提示，这里只留日志
    console.warn('[Alarm] 处置失败:', e.message)
  } finally {
    submitting.value = false
  }
}

onMounted(() => {
  loadDevices()
  loadAlarms()
})
</script>