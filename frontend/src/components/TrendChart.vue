<template>
  <svg class="trend-chart" :viewBox="`0 0 ${width} ${height}`">
    <template v-if="scale">
      <!-- 网格与 Y 轴刻度 -->
      <g v-for="(line, i) in gridLines" :key="'grid-' + i">
        <line
          :x1="pad.left"
          :y1="line.y"
          :x2="width - pad.right"
          :y2="line.y"
          stroke="#ebeef5"
          stroke-width="1"
        />
        <text :x="pad.left - 6" :y="line.y + 4" text-anchor="end" font-size="11" fill="#909399">
          {{ line.label }}
        </text>
      </g>
      <!-- 折线：只连连续有值的点，缺数据的日期断开 -->
      <polyline
        v-for="(seg, i) in lineSegments"
        :key="'line-' + i"
        :points="seg"
        fill="none"
        :stroke="color"
        stroke-width="2"
      />
      <!-- 柱状 -->
      <rect
        v-for="(bar, i) in bars"
        :key="'bar-' + i"
        :x="bar.x"
        :y="bar.y"
        :width="bar.w"
        :height="bar.h"
        :fill="color"
        opacity="0.85"
      />
      <circle v-for="(dot, i) in dots" :key="'dot-' + i" :cx="dot.x" :cy="dot.y" r="3" :fill="color" />
      <!-- X 轴标签 -->
      <text
        v-for="(label, i) in xLabels"
        :key="'x-' + i"
        :x="label.x"
        :y="height - 8"
        text-anchor="middle"
        font-size="11"
        fill="#909399"
      >
        {{ label.text }}
      </text>
    </template>
    <text v-else :x="width / 2" :y="height / 2" text-anchor="middle" font-size="13" fill="#c0c4cc">
      暂无数据
    </text>
  </svg>
</template>

<script setup>
import { computed } from 'vue'

const props = defineProps({
  labels: { type: Array, default: () => [] },
  values: { type: Array, default: () => [] },
  color: { type: String, default: '#409eff' },
  // line 折线 / bar 柱状
  type: { type: String, default: 'line' }
})

const width = 600
const height = 200
const pad = { left: 46, right: 14, top: 14, bottom: 26 }

const plotWidth = width - pad.left - pad.right
const plotHeight = height - pad.top - pad.bottom

/** Y 轴取值范围：柱状从 0 起算最直观；折线只看波动，上下各留 10% 余量 */
const scale = computed(() => {
  const valid = props.values.filter(v => v != null && !Number.isNaN(v))
  if (!valid.length) {
    return null
  }
  const max = Math.max(...valid)
  const min = Math.min(...valid)
  const range = max - min
  const bottom = props.type === 'bar' ? 0 : Math.max(0, min - range * 0.1)
  let top = props.type === 'bar' ? Math.max(max, 1) : max + range * 0.1
  if (top <= bottom) {
    top = bottom + 1
  }
  return { bottom, top }
})

const pointCount = computed(() => Math.max(props.values.length, 1))

const xAt = (index) => {
  if (props.type === 'bar' || pointCount.value === 1) {
    return pad.left + (plotWidth * (index + 0.5)) / pointCount.value
  }
  return pad.left + (plotWidth * index) / (pointCount.value - 1)
}

const yAt = (value) => {
  const s = scale.value
  if (!s) {
    return pad.top + plotHeight
  }
  return pad.top + (plotHeight * (s.top - value)) / (s.top - s.bottom)
}

const gridLines = computed(() => {
  const s = scale.value
  if (!s) {
    return []
  }
  const ticks = 4
  const lineList = []
  for (let i = 0; i <= ticks; i++) {
    const value = s.bottom + ((s.top - s.bottom) * i) / ticks
    lineList.push({ y: yAt(value), label: formatTick(value) })
  }
  return lineList
})

const formatTick = (value) => (scale.value.top - scale.value.bottom < 20 ? value.toFixed(1) : value.toFixed(0))

const lineSegments = computed(() => {
  if (props.type !== 'line') {
    return []
  }
  const segments = []
  let current = []
  props.values.forEach((value, index) => {
    if (value == null || Number.isNaN(value)) {
      if (current.length > 1) {
        segments.push(current.join(' '))
      }
      current = []
      return
    }
    current.push(`${xAt(index).toFixed(1)},${yAt(value).toFixed(1)}`)
  })
  if (current.length > 1) {
    segments.push(current.join(' '))
  }
  return segments
})

const dots = computed(() =>
  props.values
    .map((value, index) => (value == null || Number.isNaN(value) ? null : { x: xAt(index), y: yAt(value) }))
    .filter(Boolean)
)

const bars = computed(() => {
  if (props.type !== 'bar') {
    return []
  }
  const slot = plotWidth / pointCount.value
  const barWidth = Math.max(Math.min(slot * 0.5, 28), 2)
  const baseline = yAt(0)
  return props.values
    .map((value, index) => {
      if (value == null || Number.isNaN(value)) {
        return null
      }
      const y = yAt(value)
      return {
        x: (xAt(index) - barWidth / 2).toFixed(1),
        y: y.toFixed(1),
        w: barWidth.toFixed(1),
        h: Math.max(baseline - y, 0).toFixed(1)
      }
    })
    .filter(Boolean)
})

/** 标签太密会重叠，最多显示 7 个，并保证最后一个一定显示 */
const xLabels = computed(() => {
  const total = props.labels.length
  if (!total) {
    return []
  }
  const step = Math.ceil(total / 7)
  const labelList = []
  for (let i = 0; i < total; i += step) {
    labelList.push({ x: xAt(i), text: props.labels[i] })
  }
  const lastIndex = total - 1
  if (labelList.length && labelList[labelList.length - 1].x !== xAt(lastIndex)) {
    labelList.push({ x: xAt(lastIndex), text: props.labels[lastIndex] })
  }
  return labelList
})
</script>

<style scoped>
.trend-chart {
  width: 100%;
  height: auto;
  display: block;
}
</style>