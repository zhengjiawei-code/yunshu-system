<template>
  <el-sub-menu v-if="visibleChildren.length" :index="menu.routePath || String(menu.id)">
    <template #title>
      <el-icon><component :is="resolveIcon(menu.icon)" /></el-icon>
      <span>{{ menu.menuName }}</span>
    </template>
    <sidebar-item v-for="child in visibleChildren" :key="child.id" :menu="child" />
  </el-sub-menu>
  <el-menu-item v-else :index="menu.routePath">
    <el-icon><component :is="resolveIcon(menu.icon)" /></el-icon>
    <template #title>{{ menu.menuName }}</template>
  </el-menu-item>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import type { MenuNode } from '@/types/menu'
import { resolveIcon } from '@/utils/icon'

defineOptions({ name: 'SidebarItem' })

const props = defineProps<{ menu: MenuNode }>()

/** 可见子节点：排除按钮与隐藏项；无子节点的目录不渲染为可展开项 */
const visibleChildren = computed(() =>
  (props.menu.children || []).filter(
    (child) => child.menuType !== 3 && child.visible && child.status
  )
)
</script>
