<template>
  <div class="page-container">
    <div class="page-header">
      <div>
        <h2 class="page-header__title">{{ title }}</h2>
        <p class="page-header__desc">该页面已完成路由与菜单挂载，业务内容待开发</p>
      </div>
    </div>

    <div class="page-content">
      <el-descriptions :column="2" border>
        <el-descriptions-item label="菜单名称">{{ title }}</el-descriptions-item>
        <el-descriptions-item label="路由地址">{{ route.path }}</el-descriptions-item>
        <el-descriptions-item label="组件路径">{{ componentPath || '—' }}</el-descriptions-item>
        <el-descriptions-item label="所属目录">{{ parents.join(' / ') || '—' }}</el-descriptions-item>
        <el-descriptions-item label="终端">{{ terminalType }}</el-descriptions-item>
      </el-descriptions>

      <div class="empty-state">
        骨架占位页：后续在此实现列表 / 表单 / 抽屉，遵循《前端规范》五项列表能力
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { useRoute } from 'vue-router'

const route = useRoute()

const title = computed(() => (route.meta.title as string) || '页面')
const parents = computed(() => (route.meta.parents as string[] | undefined) || [])
const componentPath = computed(() => (route.meta.componentPath as string) || '')
const terminalType = computed(() => (route.meta.terminalType as string) || 'PC')
</script>

<style scoped>
.page-content :deep(.el-descriptions) {
  margin-bottom: var(--spacing-base);
}
</style>
