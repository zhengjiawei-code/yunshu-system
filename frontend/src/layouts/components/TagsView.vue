<template>
  <div class="tags-inline">
    <div class="tags-scroll">
      <div class="tags-inner">
        <div
          v-for="tag in tagsStore.visitedViews"
          :key="tag.key"
          class="tag-item"
          :class="{ 'is-active': tag.key === route.fullPath }"
          @click="openTag(tag)"
        >
          <span class="tag-title">{{ tag.title }}</span>
          <el-icon v-if="!tag.affix" class="tag-close" @click.stop="closeTag(tag)">
            <Close />
          </el-icon>
        </div>
      </div>
    </div>

    <el-dropdown trigger="click" @command="handleCommand">
      <el-icon class="tags-actions"><ArrowDown /></el-icon>
      <template #dropdown>
        <el-dropdown-menu>
          <el-dropdown-item command="others">关闭其他</el-dropdown-item>
          <el-dropdown-item command="all">关闭全部</el-dropdown-item>
        </el-dropdown-menu>
      </template>
    </el-dropdown>
  </div>
</template>

<script setup lang="ts">
import { watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ArrowDown, Close } from '@element-plus/icons-vue'
import { useTagsStore, type TagView } from '@/stores/tags'

const route = useRoute()
const router = useRouter()
const tagsStore = useTagsStore()

watch(
  () => route.fullPath,
  () => {
    const title = route.meta.title as string | undefined
    if (!title) return
    tagsStore.addView({ path: route.path, fullPath: route.fullPath, title })
  },
  { immediate: true }
)

function openTag(tag: TagView) {
  if (tag.key !== route.fullPath) router.push(tag.fullPath)
}

function closeTag(tag: TagView) {
  const next = tagsStore.delView(tag.key)
  if (!next) return
  if (tag.key === route.fullPath) router.push(next.fullPath)
}

function handleCommand(command: string) {
  if (command === 'others') {
    tagsStore.delOthers(route.fullPath)
    return
  }
  tagsStore.delAll()
  if (route.path !== '/dashboard') router.push('/dashboard')
}
</script>

<style scoped>
.tags-inline {
  display: flex;
  align-items: center;
  gap: var(--spacing-xs);
  width: 100%;
  height: 100%;
  min-width: 0;
}

.tags-scroll {
  flex: 1;
  min-width: 0;
  height: 100%;
  overflow-x: auto;
  overflow-y: hidden;
  scrollbar-width: none;
}

.tags-scroll::-webkit-scrollbar {
  display: none;
}

.tags-inner {
  display: flex;
  align-items: center;
  gap: 2px;
  height: 100%;
  width: max-content;
  white-space: nowrap;
}

.tag-item {
  display: inline-flex;
  align-items: center;
  flex: 0 0 auto;
  gap: 3px;
  height: 22px;
  padding: 0 8px;
  border: 1px solid transparent;
  border-radius: var(--radius-xs);
  font-size: var(--font-size-extra-small);
  color: var(--color-text-secondary);
  cursor: pointer;
  user-select: none;
  transition: background-color var(--duration-fast) var(--ease-out),
    color var(--duration-fast) var(--ease-out);
}

.tag-title {
  max-width: 180px;
  overflow: hidden;
  text-overflow: ellipsis;
}

.tag-item:hover {
  color: var(--color-text-primary);
  background-color: var(--color-bg-hover);
}

.tag-item.is-active {
  color: var(--color-primary);
  background-color: var(--color-primary-light-5);
  border-color: var(--color-primary-light-4);
  font-weight: var(--font-weight-medium);
}

.tag-close {
  font-size: 11px;
  border-radius: var(--radius-circle);
  transition: var(--transition-fast);
}

.tag-close:hover {
  background-color: var(--color-primary-light-3);
  color: var(--color-white);
}

.tags-actions {
  flex-shrink: 0;
  padding: 4px;
  font-size: 14px;
  color: var(--color-text-placeholder);
  cursor: pointer;
  border-radius: var(--radius-xs);
  transition: var(--transition-fast);
}

.tags-actions:hover {
  color: var(--color-primary);
  background-color: var(--color-bg-hover);
}
</style>
