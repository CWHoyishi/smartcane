<template>
  <span class="status-tag" :class="'status-tag--' + toneValue">
    <span class="status-tag__dot"></span>{{ textValue }}
  </span>
</template>

<script setup>
import { computed } from 'vue'

/**
 * 状态标签：把在线/离线/告警等状态的颜色和文案收敛到一处。
 * 用 status 关键字查表；表里没有的状态（如告警级别）可以直接传 text + tone。
 */
const props = defineProps({
  status: { type: String, default: '' },
  text: { type: String, default: '' },
  tone: { type: String, default: '' }
})

const PRESET = {
  online: { text: '在线', tone: 'success' },
  offline: { text: '离线', tone: 'muted' },
  fall: { text: '摔倒告警', tone: 'danger' },
  normal: { text: '正常', tone: 'success' },
  pending: { text: '待处理', tone: 'danger' },
  confirmed: { text: '已确认', tone: 'primary' },
  falseAlarm: { text: '误报', tone: 'muted' }
}

const preset = computed(() => PRESET[props.status] || { text: '', tone: 'muted' })
const textValue = computed(() => props.text || preset.value.text || '--')
const toneValue = computed(() => props.tone || preset.value.tone)
</script>

<style scoped>
.status-tag {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 0 10px;
  font-size: 12px;
  line-height: 22px;
  border-radius: 999px;
  background: var(--sc-bg);
  color: var(--sc-text-2);
  white-space: nowrap;
}

.status-tag__dot {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: currentColor;
}

.status-tag--success {
  background: #e8f8f1;
  color: #1f8f61;
}

.status-tag--warning {
  background: #fff3e6;
  color: #d96500;
}

.status-tag--danger {
  background: #ffecec;
  color: #d92c2c;
}

.status-tag--primary {
  background: var(--sc-primary-light);
  color: var(--sc-primary);
}

.status-tag--muted {
  background: var(--sc-bg);
  color: var(--sc-text-3);
}
</style>