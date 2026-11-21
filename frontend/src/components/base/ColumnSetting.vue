<template>
  <div class="column-setting" v-loading="loading">
    <div class="column-setting__head">
      <span class="column-setting__title">列设置</span>
      <el-button link type="primary" size="small" :disabled="saving" @click="emit('reset')">
        恢复默认
      </el-button>
    </div>
    <p class="column-setting__tip">拖拽调整顺序；保存后当前账号在所有设备生效</p>

    <div class="column-setting__list">
      <div
        v-for="(item, index) in states"
        :key="item.key"
        class="column-setting__row"
        :class="{ 'is-dragging': dragIndex === index }"
        draggable="true"
        @dragstart="onDragStart(index)"
        @dragover.prevent
        @drop="onDrop(index)"
        @dragend="onDragEnd"
      >
        <el-icon class="column-setting__handle"><Rank /></el-icon>

        <el-checkbox
          :model-value="item.visible"
          :disabled="defOf(item.key)?.locked"
          @update:model-value="(value: boolean) => (item.visible = value)"
        />

        <span class="column-setting__label" :title="defOf(item.key)?.label">
          {{ defOf(item.key)?.label || item.key }}
        </span>

        <el-tooltip content="该列是否可排序" placement="top">
          <el-switch
            v-model="item.sortable"
            size="small"
            inline-prompt
            active-text="排"
            inactive-text="—"
            class="column-setting__sortable"
          />
        </el-tooltip>

        <el-select v-model="item.filterType" size="small" class="column-setting__type">
          <el-option
            v-for="option in filterTypeOptions"
            :key="option.value"
            :label="option.label"
            :value="option.value"
          />
        </el-select>

        <div class="column-setting__default">
          <column-filter
            v-if="item.filterType !== 'none'"
            :type="item.filterType"
            :options="defOf(item.key)?.filterOptions"
            :model-value="item.defaultFilter"
            placeholder="默认值"
            @update:model-value="(value: any) => (item.defaultFilter = value)"
          />
          <span v-else class="column-setting__placeholder">—</span>
        </div>
      </div>
    </div>

    <div class="column-setting__footer">
      <span class="column-setting__hint">筛选项留空表示无默认查询值</span>
      <el-button type="primary" size="small" :loading="saving" @click="emit('save')">保存</el-button>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import { Rank } from '@element-plus/icons-vue'
import type { ColumnDef, ColumnItemState, FilterType } from '@/composables/useColumnConfig'
import ColumnFilter from '@/components/base/ColumnFilter.vue'

/**
 * 统一列设置面板：五个列表页共用，不允许页面各自实现。
 * 只负责编辑状态与交互，持久化由 useColumnConfig 统一调用后端接口。
 */
const props = defineProps<{
  schema: ColumnDef[]
  states: ColumnItemState[]
  loading?: boolean
  saving?: boolean
}>()

const emit = defineEmits<{
  'update:states': [value: ColumnItemState[]]
  save: []
  reset: []
}>()

const filterTypeOptions: Array<{ label: string; value: FilterType }> = [
  { label: '无筛选', value: 'none' },
  { label: '文本', value: 'text' },
  { label: '单选下拉', value: 'select' },
  { label: '多选下拉', value: 'multiselect' },
  { label: '日期', value: 'date' },
  { label: '日期区间', value: 'daterange' },
  { label: '数值区间', value: 'numberrange' }
]

const dragIndex = ref<number | null>(null)

function defOf(key: string): ColumnDef | undefined {
  return props.schema.find((item) => item.key === key)
}

function onDragStart(index: number) {
  dragIndex.value = index
}

function onDragEnd() {
  dragIndex.value = null
}

function onDrop(target: number) {
  const from = dragIndex.value
  dragIndex.value = null
  if (from === null || from === target) return
  const next = [...props.states]
  const [moved] = next.splice(from, 1)
  next.splice(target, 0, moved)
  emit('update:states', next)
}
</script>

<style scoped>
.column-setting {
  width: 100%;
}

.column-setting__head {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.column-setting__title {
  font-size: var(--font-size-base);
  font-weight: 600;
  color: var(--color-text-primary);
}

.column-setting__tip {
  margin: 2px 0 8px;
  font-size: var(--font-size-extra-small);
  color: var(--color-text-placeholder);
}

.column-setting__list {
  max-height: 52vh;
  overflow-y: auto;
  border: 1px solid var(--color-border-light);
  border-radius: 4px;
}

.column-setting__row {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 4px 8px;
  border-bottom: 1px solid var(--color-border-lighter);
  cursor: grab;
}

.column-setting__row:last-child {
  border-bottom: none;
}

.column-setting__row.is-dragging {
  opacity: 0.5;
}

.column-setting__handle {
  flex: none;
  color: var(--color-text-placeholder);
  cursor: grab;
}

.column-setting__label {
  flex: 1;
  min-width: 0;
  overflow: hidden;
  white-space: nowrap;
  text-overflow: ellipsis;
  font-size: var(--font-size-small);
  color: var(--color-text-regular);
}

.column-setting__sortable {
  flex: none;
}

.column-setting__type {
  flex: none;
  width: 96px;
}

.column-setting__default {
  flex: none;
  width: 150px;
}

.column-setting__placeholder {
  font-size: var(--font-size-extra-small);
  color: var(--color-text-placeholder);
}

.column-setting__footer {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-top: 8px;
}

.column-setting__hint {
  font-size: var(--font-size-extra-small);
  color: var(--color-text-placeholder);
}
</style>
