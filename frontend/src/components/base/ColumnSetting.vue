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

        <el-tooltip
          v-if="!defOf(item.key)?.filterHidden"
          :content="canFilter(item.key) ? '是否启用该列筛选' : '该列不提供筛选'"
          placement="top"
        >
          <el-switch
            :model-value="isFilterEnabled(item)"
            size="small"
            inline-prompt
            active-text="筛"
            inactive-text="—"
            :disabled="!canFilter(item.key)"
            class="column-setting__enable"
            @update:model-value="(value: boolean) => toggleFilter(item, value)"
          />
        </el-tooltip>

        <el-tag size="small" type="info" class="column-setting__shape">
          {{ shapeLabel(item) }}
        </el-tag>

        <el-select
          v-if="hasMode(item)"
          :model-value="modeValueOf(item)"
          size="small"
          class="column-setting__mode"
          @update:model-value="(value: FilterMode) => setMode(item, value)"
        >
          <el-option
            v-for="option in modeOptions(item)"
            :key="option.value"
            :label="option.label"
            :value="option.value"
          />
        </el-select>
        <span v-else class="column-setting__fixed">{{ fixedHint(item) }}</span>

        <div class="column-setting__default">
          <template v-if="!isFilterEnabled(item)">
            <span class="column-setting__placeholder">—</span>
          </template>
          <template v-else-if="isDate(item)">
            <span class="column-setting__placeholder">按日期区间查询</span>
          </template>
          <template v-else-if="isTextMulti(item)">
            <div class="column-setting__default-multi">
              <el-select
                :model-value="defaultModeOf(item)"
                size="small"
                class="column-setting__default-mode"
                @update:model-value="(value: TextMatchMode) => setDefaultMode(item, value)"
              >
                <el-option
                  v-for="option in TEXT_MATCH_MODE_OPTIONS"
                  :key="option.value"
                  :label="option.label"
                  :value="option.value"
                />
              </el-select>
              <el-input
                :model-value="defaultTextOf(item)"
                size="small"
                clearable
                placeholder="默认值"
                @update:model-value="(value: string) => setDefaultText(item, value)"
              />
            </div>
          </template>
          <template v-else>
            <column-filter
              :type="item.filterType"
              :select-multiple="item.selectMultiple === true"
              :options="defOf(item.key)?.filterOptions"
              :model-value="item.defaultFilter"
              placeholder="默认值"
              @update:model-value="(value: any) => (item.defaultFilter = value)"
            />
          </template>
        </div>
      </div>
    </div>

    <div class="column-setting__footer">
      <span class="column-setting__hint">筛选项留空表示无默认查询值；日期固定按区间查询</span>
      <el-button type="primary" size="small" :loading="saving" @click="emit('save')">保存</el-button>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import { Rank } from '@element-plus/icons-vue'
import type {
  ColumnDef,
  ColumnItemState,
  FilterMode,
  FilterType
} from '@/composables/useColumnConfig'
import {
  TEXT_MATCH_MODE_OPTIONS,
  isTextMatchMode,
  type TextMatchMode
} from '@/utils/textFilter'
import ColumnFilter from '@/components/base/ColumnFilter.vue'

/**
 * 统一列设置面板：所有列表页共用，不允许页面各自实现。
 * 只负责编辑状态与交互，持久化由 useColumnConfig 统一调用后端接口。
 *
 * v1.1 口径：筛选类型跟随列声明的真实形态，不允许用户改控件类型；
 * 用户只能通过“启用筛选”开关决定该列是否参与筛选。
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

const FILTER_TYPE_LABELS: Record<FilterType, string> = {
  none: '—',
  text: '文本',
  select: '下拉',
  multiselect: '多选下拉',
  date: '日期',
  daterange: '日期区间',
  numberrange: '数值区间'
}

const dragIndex = ref<number | null>(null)

function defOf(key: string): ColumnDef | undefined {
  return props.schema.find((item) => item.key === key)
}

function schemaTypeOf(key: string): FilterType {
  return defOf(key)?.filterType || 'text'
}

function canFilter(key: string): boolean {
  return schemaTypeOf(key) !== 'none'
}

function isFilterEnabled(item: ColumnItemState): boolean {
  return item.filterType !== 'none'
}

function shapeLabel(item: ColumnItemState): string {
  if (!isFilterEnabled(item) || !canFilter(item.key)) return '—'
  return FILTER_TYPE_LABELS[schemaTypeOf(item.key)] || '文本'
}

function hasMode(item: ColumnItemState): boolean {
  return (
    isFilterEnabled(item) &&
    canFilter(item.key) &&
    (item.filterType === 'text' || item.filterType === 'select')
  )
}

function isDate(item: ColumnItemState): boolean {
  return item.filterType === 'date' || item.filterType === 'daterange'
}

function isTextMulti(item: ColumnItemState): boolean {
  return item.filterType === 'text' && item.filterMode === 'multi'
}

function fixedHint(item: ColumnItemState): string {
  if (!isFilterEnabled(item) || !canFilter(item.key)) return '—'
  if (isDate(item)) return '固定按日期区间查询'
  if (item.filterType === 'numberrange') return '固定区间'
  return '—'
}

function modeOptions(item: ColumnItemState): Array<{ label: string; value: FilterMode }> {
  if (item.filterType === 'text') {
    return [
      { label: '单模式', value: 'single' },
      { label: '多模式', value: 'multi' }
    ]
  }
  return [
    { label: '单选', value: 'single' },
    { label: '多选', value: 'multi' }
  ]
}

function modeValueOf(item: ColumnItemState): FilterMode {
  if (item.filterType === 'text') {
    return item.filterMode === 'multi' ? 'multi' : 'single'
  }
  return item.selectMultiple ? 'multi' : 'single'
}

function isPlainObject(value: unknown): value is Record<string, unknown> {
  return typeof value === 'object' && value !== null && !Array.isArray(value)
}

function defaultModeOf(item: ColumnItemState): TextMatchMode {
  if (isPlainObject(item.defaultFilter) && isTextMatchMode(item.defaultFilter.mode)) {
    return item.defaultFilter.mode
  }
  return 'like'
}

function defaultTextOf(item: ColumnItemState): string {
  const value = item.defaultFilter
  if (isPlainObject(value)) {
    return value.value === null || value.value === undefined ? '' : String(value.value)
  }
  return value === null || value === undefined ? '' : String(value)
}

function setDefaultMode(item: ColumnItemState, mode: TextMatchMode) {
  item.defaultFilter = { mode, value: defaultTextOf(item) }
}

function setDefaultText(item: ColumnItemState, value: string) {
  item.defaultFilter = { mode: defaultModeOf(item), value }
}

function toggleFilter(item: ColumnItemState, enabled: boolean) {
  if (!enabled) {
    item.filterType = 'none'
    item.filterMode = null
    item.selectMultiple = null
    item.defaultFilter = null
    return
  }
  const type = schemaTypeOf(item.key)
  const def = defOf(item.key)
  item.filterType = type
  item.filterMode = type === 'text' ? (def?.filterMode === 'multi' ? 'multi' : 'single') : null
  item.selectMultiple = type === 'select' ? def?.selectMultiple === true : null
  item.defaultFilter = type === 'date' || type === 'daterange' ? null : undefined
}

function setMode(item: ColumnItemState, value: FilterMode) {
  if (item.filterType === 'text') {
    const nextMulti = value === 'multi'
    if (nextMulti && item.filterMode !== 'multi') {
      item.defaultFilter = { mode: defaultModeOf(item), value: defaultTextOf(item) }
    } else if (!nextMulti && item.filterMode === 'multi') {
      item.defaultFilter = defaultTextOf(item) || null
    }
    item.filterMode = nextMulti ? 'multi' : 'single'
    return
  }
  if (item.filterType === 'select') {
    const nextMultiple = value === 'multi'
    if (nextMultiple && !item.selectMultiple) {
      const current = item.defaultFilter
      item.defaultFilter =
        current === null || current === undefined || current === '' ? null : [current]
    } else if (!nextMultiple && item.selectMultiple) {
      item.defaultFilter = Array.isArray(item.defaultFilter)
        ? item.defaultFilter[0] ?? null
        : item.defaultFilter
    }
    item.selectMultiple = nextMultiple
  }
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
  gap: 5px;
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
  min-width: 40px;
  overflow: hidden;
  white-space: nowrap;
  text-overflow: ellipsis;
  font-size: var(--font-size-small);
  color: var(--color-text-regular);
}

.column-setting__sortable,
.column-setting__enable {
  flex: none;
}

.column-setting__shape {
  flex: none;
  width: 68px;
  justify-content: center;
}

.column-setting__mode {
  flex: none;
  width: 84px;
}

.column-setting__fixed {
  flex: none;
  width: 84px;
  text-align: center;
  font-size: var(--font-size-extra-small);
  color: var(--color-text-placeholder);
}

.column-setting__default {
  flex: none;
  width: 188px;
}

.column-setting__default-multi {
  display: flex;
  align-items: center;
  gap: 4px;
  width: 100%;
}

.column-setting__default-mode {
  flex: none;
  width: 76px;
}

.column-setting__default-multi :deep(.el-input) {
  flex: 1;
  min-width: 0;
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
