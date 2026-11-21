<template>
  <el-tag class="status-tag" :type="type" size="small" effect="light">{{ text }}</el-tag>
</template>

<script setup lang="ts">
/** 全站状态标签统一出口，页面禁止直接写 el-tag */
defineProps<{
  type?: 'primary' | 'success' | 'info' | 'warning' | 'danger'
  text: string
}>()
</script>

<style scoped>
.status-tag {
  padding: 0 7px;
  border-radius: var(--radius-xs);
  font-size: var(--font-size-extra-small);
  line-height: 20px;
}
</style>
