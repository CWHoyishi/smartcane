<template>
  <div class="page">
    <el-card>
      <PageHeader title="告警处理" subtitle="摔倒告警的确认与误报标记，处置记录永久保留">
        <el-radio-group v-model="query.status" @change="handleFilterChange">
          <el-radio-button :value="null">全部</el-radio-button>
          <el-radio-button :value="0">待处理</el-radio-button>
          <el-radio-button :value="1">已确认</el-radio-button>
          <el-radio-button :value="2">误报</el-radio-button>
        </el-radio-group>
        <el-button :loading="loading" @click="loadAlarms">刷新</el-button>
      </PageHeader>

      <div v-if="pendingCount > 0" class="alarm-banner section">
        <span class="alarm-banner__dot"></span>
        <span>有 <b>{{ pendingCount }}</b> 条待处理告警，请尽快联系老人或监护人确认。</span>
        <span class="toolbar__spacer"></span>
        <el-button link type="danger" @click="showPendingOnly">只看待处理</el-button>
      </div>

      <el-table
        v-loading="loading"
        :data="rows"
        stripe
        :row-class-name="rowClassName"
        class="section"
      >
        <el-table-column label="告警时间" width="170">
          <template #default="{ row }"><span class="mono">{{ row.reportTime }}</span></template>
        </el-table-column>
        <el-table-column label="设备序列号" width="170">
          <template #default="{ row }"><span class="mono">{{ row.deviceSn }}</span></template>
        </el-table-column>
        <el-table-column label="老人" width="110">
          <template #default="{ row }">{{ elderName(row.deviceSn) }}</template>
        </el-table-column>
        <el-table-column label="类型" width="110">
          <template #default="{ row }">
            <StatusTag
              :status="row.alarmType === 'FALL' ? 'fall' : ''"
              :text="alarmTypeText(row.alarmType)"
              :tone="row.alarmType === 'FALL' ? 'danger' : 'warning'"
            />
          </template>
        </el-table-column>
        <el-table-column label="级别" width="90">
          <template #default="{ row }">{{ levelText(row.level) }}</template>
        </el-table-column>
        <el-table-column label="状态" width="120">
          <template #default="{ row }">
            <StatusTag :status="statusKey(row.status)" />
          </template>
        </el-table-column>
        <el-table-column prop="alarmValue" label="触发依据" width="140" />
        <el-table-column label="处理情况" min-width="200">
          <template #default="{ row }">
            <span v-if="row.status === 0" class="text-muted">--</span>
            <template v-else>
              <span class="mono">{{ row.handleTime }}</span>
              <span v-if="row.handledBy" class="text-muted"> · {{ row.handledBy }}</span>
              <div v-if="row.handleNote" class="text-muted handle-note">{{ row.handleNote }}</div>
            </template>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="180" fixed="right">
          <template #default="{ row }">
            <template v-if="row.status === 0">
              <el-button link type="primary" @click="openHandle(row, 1)">确认已处置</el-button>
              <el-button link type="warning" @click="openHandle(row, 2)">标记误报</el-button>
            </template>
            <span v-else class="text-muted">已处理</span>
          </template>
        </el-table-column>
        <template #empty>
          <EmptyState text="暂无告警记录" hint="设备判定摔倒时会自动生成告警" />
        </template>
      </el-table>

      <el-pagination
        v-if="total > 0"
        class="section"
        background
        layout="total, prev, pager, next"
        :current-page="query.pageNum"
        :page-size="query.pageSize"
        :total="total"
        @current-change="handlePageChange"
      />
    </el-card>

    <el-dialog
      v-model="handleVisible"
      :title="handleForm.status === 1 ? '确认已处置' : '标记误报'"
      width="440px"
    >
      <el-form label-width="80px">
        <el-form-item label="告警">
          <span class="mono">{{ currentRow ? `${currentRow.reportTime} ${currentRow.deviceSn}` : '' }}</span>
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
  </div>
</template>

<script setup>
import { computed, reactive, ref, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { alarmApi, deviceApi } from '@/api'
import PageHeader from '@/components/PageHeader.vue'
import StatusTag from '@/components/StatusTag.vue'
import EmptyState from '@/components/EmptyState.vue'

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

const levelText = (level) => (level === 3 ? '紧急' : level === 2 ? '重要' : '--')
const alarmTypeText = (type) => (type === 'FALL' ? '摔倒' : type || '未知')
const elderName = (deviceSn) => elderNames.value[deviceSn] || '--'

/** 数字状态 → StatusTag 的关键字，颜色与文案都由组件统一维护 */
const statusKey = (status) => (status === 0 ? 'pending' : status === 1 ? 'confirmed' : 'falseAlarm')

/** 待处理行左侧加红色色条：一屏几十行时比看标签更快定位 */
const rowClassName = ({ row }) => (row.status === 0 ? 'row-pending' : '')

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

const showPendingOnly = () => {
  query.status = 0
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

<style scoped>
.alarm-banner {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 12px 16px;
  font-size: 14px;
  color: #d92c2c;
  background: #ffecec;
  border: 1px solid #ffd6d6;
  border-radius: var(--sc-radius-card);
}

.alarm-banner__dot {
  flex: none;
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background: var(--sc-danger);
}

.handle-note {
  font-size: 12px;
}

/* 待处理行：只给首列加色条，避免整行铺色干扰文字对比度 */
:deep(.el-table__row.row-pending > td:first-child) {
  box-shadow: inset 3px 0 0 var(--sc-danger);
}
</style>