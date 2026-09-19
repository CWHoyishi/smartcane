<template>
  <div class="stat-card" :class="'stat-card--' + tone">
    <div class="stat-card__label">{{ label }}</div>
    <div class="stat-card__value">
      <span class="stat-card__num">{{ displayValue }}</span>
      <span v-if="unit" class="stat-card__unit">{{ unit }}</span>
    </div>
    <div class="stat-card__hint">{{ hint }}</div>
  </div>
</template>

<script setup>
import { computed } from 'vue'

const props = defineProps({
  label: { type: String, required: true },
  value: { type: [String, Number], default: null },
  unit: { type: String, default: '' },
  /** default / primary / success / warning / danger */
  tone: { type: String, default: 'default' },
  hint: { type: String, default: '' }
})

/**
 * 0 是有效读数（心率/血氧为 0 表示设备判断为未佩戴，不代表无数据），
 * 所以只有 null / undefined / 空串 / NaN 才显示为 --。
 */
const displayValue = computed(() => {
  const value = props.value
  if (value === null || value === undefined || value === '') {
    return '--'
  }
  if (typeof value === 'number' && Number.isNaN(value)) {
    return '--'
  }
  return value
})
</script>

<style scoped>
.stat-card {
  display: flex;
  flex-direction: column;
  gap: 6px;
  padding: 16px 18px;
  background: var(--sc-surface);
  border: 1px solid var(--sc-border);
  /* 用左侧色条表达语义，而不是整卡铺底色：铺底色会压住数字本身的可读性 */
  border-left: 3px solid var(--sc-border);
  border-radius: var(--sc-radius-card);
}

.stat-card--primary {
  border-left-color: var(--sc-primary);
}

.stat-card--success {
  border-left-color: var(--sc-success);
}

.stat-card--warning {
  border-left-color: var(--sc-warning);
}

.stat-card--danger {
  border-left-color: var(--sc-danger);
}

.stat-card__label {
  font-size: 13px;
  color: var(--sc-text-3);
}

.stat-card__value {
  display: flex;
  align-items: baseline;
  gap: 4px;
}

.stat-card__num {
  font-size: 28px;
  font-weight: 600;
  line-height: 1.2;
  color: var(--sc-text-1);
}

.stat-card--primary .stat-card__num {
  color: var(--sc-primary);
}

.stat-card--success .stat-card__num {
  color: var(--sc-success);
}

.stat-card--warning .stat-card__num {
  color: var(--sc-warning);
}

.stat-card--danger .stat-card__num {
  color: var(--sc-danger);
}

.stat-card__unit {
  font-size: 13px;
  color: var(--sc-text-3);
}

.stat-card__hint {
  min-height: 16px;
  font-size: 12px;
  color: var(--sc-text-3);
}
</style>