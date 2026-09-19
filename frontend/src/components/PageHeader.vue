<template>
  <div class="page-header">
    <div class="page-header__main">
      <span class="page-header__title">{{ title }}</span>
      <span v-if="subtitle" class="page-header__subtitle">{{ subtitle }}</span>
    </div>
    <div class="page-header__actions">
      <slot />
    </div>
  </div>
</template>

<script setup>
/**
 * 统一的页面标题栏：左侧标题 + 说明，右侧操作区。
 * 抽出来的原因：7 个页面原本各写一份 flex 内联样式，标题字号和间距很快就漂移了。
 */
defineProps({
  title: { type: String, required: true },
  subtitle: { type: String, default: '' }
})
</script>

<style scoped>
.page-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  flex-wrap: wrap;
  gap: 12px;
}

.page-header__main {
  display: flex;
  align-items: baseline;
  flex-wrap: wrap;
  gap: 10px;
}

.page-header__title {
  font-size: 16px;
  font-weight: 600;
  color: var(--sc-text-1);
}

.page-header__subtitle {
  font-size: 13px;
  color: var(--sc-text-3);
}

.page-header__actions {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 12px;
}
</style>