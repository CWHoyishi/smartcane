<template>
  <div class="empty-state">
    <svg class="empty-state__icon" viewBox="0 0 64 64" aria-hidden="true">
      <rect x="10" y="16" width="44" height="32" rx="6" fill="none" stroke="currentColor" stroke-width="3" />
      <path d="M10 26h44" stroke="currentColor" stroke-width="3" />
      <circle cx="18" cy="21" r="2" fill="currentColor" />
      <circle cx="25" cy="21" r="2" fill="currentColor" />
    </svg>
    <div class="empty-state__text">{{ text }}</div>
    <div v-if="hint" class="empty-state__hint">{{ hint }}</div>
    <div v-if="$slots.default" class="empty-state__action">
      <slot />
    </div>
  </div>
</template>

<script setup>
/**
 * 统一空态：图标 + 说明 + 可选提示/操作。
 * 用内联 SVG 而不是 el-empty 的默认插图，是为了让两端视觉一致（小程序端同为这套灰阶）。
 */
defineProps({
  text: { type: String, required: true },
  hint: { type: String, default: '' }
})
</script>

<style scoped>
.empty-state {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 8px;
  padding: 48px 16px;
  color: var(--sc-text-3);
}

.empty-state__icon {
  width: 64px;
  height: 64px;
  color: var(--sc-border);
}

.empty-state__text {
  font-size: 14px;
  color: var(--sc-text-2);
}

.empty-state__hint {
  font-size: 12px;
}

.empty-state__action {
  margin-top: 8px;
}
</style>