<template>
  <el-input
    v-if="type === 'text'"
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
    :model-value="(modelValue as string | number | boolean | undefined)"
    size="small"
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
import type { ColumnOption, FilterType } from '@/composables/useColumnConfig'

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
  }>(),
  { type: 'text', placeholder: '请输入' }
)

const emit = defineEmits<{
  'update:modelValue': [value: any]
  change: []
}>()

const options = computed(() => props.options || [])

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
