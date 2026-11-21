<template>
  <text-mode-filter
    v-if="type === 'text' && filterMode === 'multi'"
    :model-value="(modelValue as string | undefined) ?? ''"
    :mode="textMode"
    :placeholder="placeholder"
    @update:model-value="onUpdate"
    @update:mode="(value) => emit('update:mode', value)"
    @change="emit('change')"
  />

  <el-input
    v-else-if="type === 'text'"
    :model-value="(modelValue as string | undefined) ?? ''"
    size="small"
    clearable
    :placeholder="placeholder"
    @update:model-value="onUpdate"
    @input="emit('change')"
    @clear="emit('change')"
  />

  <el-select
    v-else-if="type === 'select'"
    :model-value="selectValue"
    size="small"
    :multiple="selectMultiple"
    :collapse-tags="selectMultiple"
    :collapse-tags-tooltip="selectMultiple"
    clearable
    :placeholder="placeholder"
    :teleported="true"
    @update:model-value="onUpdate"
    @change="emit('change')"
  >
    <el-option
      v-for="item in options"
      :key="String(item.value)"
      :label="item.label"
      :value="item.value"
    />
  </el-select>

  <el-select
    v-else-if="type === 'multiselect'"
    :model-value="(modelValue as Array<string | number>) ?? []"
    size="small"
    multiple
    collapse-tags
    collapse-tags-tooltip
    clearable
    :placeholder="placeholder"
    :teleported="true"
    @update:model-value="onUpdate"
    @change="emit('change')"
  >
    <el-option
      v-for="item in options"
      :key="String(item.value)"
      :label="item.label"
      :value="item.value"
    />
  </el-select>

  <el-date-picker
    v-else-if="type === 'date'"
    :model-value="(modelValue as string | undefined)"
    size="small"
    type="date"
    value-format="YYYY-MM-DD"
    :placeholder="placeholder"
    clearable
    :teleported="true"
    @update:model-value="onUpdate"
    @change="emit('change')"
  />

  <el-date-picker
    v-else-if="type === 'daterange'"
    :model-value="(modelValue as [string, string] | undefined)"
    size="small"
    type="daterange"
    value-format="YYYY-MM-DD"
    start-placeholder="开始"
    end-placeholder="结束"
    range-separator="~"
    unlink-panels
    clearable
    :teleported="true"
    @update:model-value="onUpdate"
    @change="emit('change')"
  />

  <div v-else-if="type === 'numberrange'" class="number-range">
    <el-input-number
      :model-value="numberRange[0]"
      size="small"
      :controls="false"
      :min="0"
      placeholder="最小"
      @update:model-value="(value: number | undefined) => onRangeUpdate(0, value)"
      @change="emit('change')"
    />
    <span class="number-range__split">~</span>
    <el-input-number
      :model-value="numberRange[1]"
      size="small"
      :controls="false"
      :min="0"
      placeholder="最大"
      @update:model-value="(value: number | undefined) => onRangeUpdate(1, value)"
      @change="emit('change')"
    />
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import type { ColumnOption, FilterMode, FilterType } from '@/composables/useColumnConfig'
import { DEFAULT_TEXT_MATCH_MODE, type TextMatchMode } from '@/utils/textFilter'
import TextModeFilter from '@/components/base/TextModeFilter.vue'

/**
 * 列筛选控件：类型由列配置决定，页面只负责把 v-model 值映射到查询参数。
 * 所有控件尺寸统一为 small，保证各列表页表头高度一致。
 */
const props = withDefaults(
  defineProps<{
    type?: FilterType
    // 通用筛选控件：值类型随控件变化，统一用 any 以避免各页面重复断言
    modelValue?: any
    options?: ColumnOption[]
    placeholder?: string
    /** 文本列查询模式：single 保持普通输入框，multi 使用多模式控件 */
    filterMode?: FilterMode | null
    /** 下拉列是否多选（type=select 时生效） */
    selectMultiple?: boolean
    /** 文本多模式当前匹配方式（表头运行时状态） */
    textMode?: TextMatchMode
  }>(),
  {
    type: 'text',
    placeholder: '请输入',
    filterMode: 'single',
    selectMultiple: false,
    textMode: DEFAULT_TEXT_MATCH_MODE
  }
)

const emit = defineEmits<{
  'update:modelValue': [value: any]
  'update:mode': [value: TextMatchMode]
  change: []
}>()

const options = computed(() => props.options || [])

const selectValue = computed(() => {
  if (props.selectMultiple) {
    if (Array.isArray(props.modelValue)) return props.modelValue
    if (props.modelValue === undefined || props.modelValue === null || props.modelValue === '') return []
    return [props.modelValue]
  }
  return Array.isArray(props.modelValue) ? props.modelValue[0] : props.modelValue
})

const numberRange = computed<[number | undefined, number | undefined]>(() => {
  const value = props.modelValue
  if (Array.isArray(value)) return [value[0] as number, value[1] as number]
  return [undefined, undefined]
})

function onUpdate(value: any) {
  emit('update:modelValue', value)
}

function onRangeUpdate(index: number, value: number | undefined) {
  const next: [number | undefined, number | undefined] = [...numberRange.value]
  next[index] = value
  emit('update:modelValue', next)
}
</script>

<style scoped>
.number-range {
  display: flex;
  align-items: center;
  gap: 4px;
  width: 100%;
}

.number-range :deep(.el-input-number) {
  flex: 1;
  min-width: 0;
}

.number-range__split {
  flex: none;
  font-size: var(--font-size-extra-small);
  color: var(--color-text-placeholder);
}
</style>
