<template>
  <el-popover v-model:visible="visible" placement="bottom-start" :width="360" trigger="click">
    <template #reference>
      <div class="icon-picker__trigger" tabindex="0">
        <el-icon v-if="modelValue" class="icon-picker__preview">
          <component :is="resolveIcon(modelValue)" />
        </el-icon>
        <span class="icon-picker__name">{{ modelValue || '请选择图标' }}</span>
        <el-icon class="icon-picker__arrow"><ArrowDown /></el-icon>
      </div>
    </template>

    <div class="icon-picker__panel">
      <el-input v-model="keyword" placeholder="搜索图标名称" clearable size="small" />
      <div class="icon-picker__grid">
        <button
          v-for="name in filteredIcons"
          :key="name"
          type="button"
          class="icon-picker__item"
          :class="{ 'is-active': name === modelValue }"
          :title="name"
          @click="handleSelect(name)"
        >
          <el-icon><component :is="resolveIcon(name)" /></el-icon>
        </button>
      </div>
      <p v-if="!filteredIcons.length" class="icon-picker__empty">没有匹配的图标</p>
    </div>
  </el-popover>
</template>

<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { ArrowDown } from '@element-plus/icons-vue'
import { ICON_NAMES, resolveIcon } from '@/utils/icon'

defineProps<{ modelValue: string }>()
const emit = defineEmits<{ 'update:modelValue': [value: string] }>()

const visible = ref(false)
const keyword = ref('')

const filteredIcons = computed(() => {
  const key = keyword.value.trim().toLowerCase()
  if (!key) return ICON_NAMES
  return ICON_NAMES.filter((name) => name.toLowerCase().includes(key))
})

watch(visible, (open) => {
  if (open) keyword.value = ''
})

function handleSelect(name: string) {
  emit('update:modelValue', name)
  visible.value = false
}
</script>

<style scoped>
.icon-picker__trigger {
  display: flex;
  align-items: center;
  gap: 8px;
  height: 32px;
  padding: 0 10px;
  border: 1px solid var(--color-border);
  border-radius: var(--radius-sm);
  background-color: var(--color-white);
  cursor: pointer;
  transition: var(--transition-fast);
}

.icon-picker__trigger:hover {
  border-color: var(--color-primary-light-3);
}

.icon-picker__preview {
  font-size: 16px;
  color: var(--color-primary);
}

.icon-picker__name {
  flex: 1;
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  font-size: var(--font-size-small);
  color: var(--color-text-regular);
}

.icon-picker__arrow {
  font-size: 12px;
  color: var(--color-text-placeholder);
}

.icon-picker__panel {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.icon-picker__grid {
  display: grid;
  grid-template-columns: repeat(8, 1fr);
  gap: 4px;
  max-height: 240px;
  overflow-y: auto;
}

.icon-picker__item {
  display: flex;
  align-items: center;
  justify-content: center;
  height: 32px;
  padding: 0;
  border: 1px solid transparent;
  border-radius: var(--radius-xs);
  background-color: transparent;
  color: var(--color-text-regular);
  font-size: 16px;
  cursor: pointer;
  transition: var(--transition-fast);
}

.icon-picker__item:hover {
  background-color: var(--color-primary-light-5);
  color: var(--color-primary);
}

.icon-picker__item.is-active {
  border-color: var(--color-primary-light-4);
  background-color: var(--color-primary-light-5);
  color: var(--color-primary);
}

.icon-picker__empty {
  margin: 8px 0;
  text-align: center;
  font-size: var(--font-size-small);
  color: var(--color-text-placeholder);
}
</style>
