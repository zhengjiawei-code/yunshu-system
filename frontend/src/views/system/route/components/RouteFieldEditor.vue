<template>
  <div class="route-field-editor" :class="{ 'is-right': align === 'right' }">
    <el-input
      v-if="editing"
      ref="inputRef"
      v-model="draft"
      class="route-field-editor__input"
      size="small"
      :maxlength="maxlength"
      :disabled="saving"
      @keyup.enter="commit"
      @keyup.esc="cancel"
      @blur="commit"
    />
    <span
      v-else
      class="route-field-editor__value"
      :class="{ 'is-editable': editable }"
      :title="displayText === '—' ? '' : displayText"
      @click="startEdit"
    >{{ displayText }}</span>
  </div>
</template>

<script setup lang="ts">
import { computed, nextTick, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { updateRouteField, updateTransferField } from '@/api/system/route'
import type {
  RouteEditableField,
  RouteRecord,
  TransferEditableField,
  TransferRecord
} from '@/types/route'

/**
 * 路由列表内联编辑：单击录入，回车/失焦保存，Esc 取消。
 * 只提交当前字段（后端单字段增量更新），失败不改界面值。
 */
const props = withDefaults(
  defineProps<{
    row: RouteRecord | TransferRecord
    field: RouteEditableField | TransferEditableField
    editable: boolean
    /** target=transfer 时走外转单字段接口 */
    target?: 'route' | 'transfer'
    /** number：负数拒绝、四舍五入保留两位小数；text：按 maxlength 限制 */
    type?: 'text' | 'number'
    maxlength?: number
    align?: 'left' | 'right'
  }>(),
  {
    target: 'route',
    type: 'text',
    maxlength: undefined,
    align: 'left'
  }
)

const emit = defineEmits<{
  updated: [value: string | number | null]
}>()

const editing = ref(false)
const saving = ref(false)
const draft = ref('')
const inputRef = ref<{ focus: () => void } | null>(null)

const currentValue = computed(() => {
  const value = (props.row as unknown as Record<string, unknown>)[props.field]
  return value === null || value === undefined || value === '' ? null : value
})

const displayText = computed(() => {
  if (currentValue.value === null) return '—'
  if (props.type === 'number') return Number(currentValue.value).toFixed(2)
  return String(currentValue.value)
})

const fieldLabel = computed(() => {
  const labels: Record<RouteEditableField | TransferEditableField, string> = {
    warehouseArea: '库区',
    mileage: '里程',
    transitHours: '时效',
    contactName: '线路联系人',
    contactPhone: '联系电话',
    serviceName: '线路客服',
    servicePhone: '客服电话'
  }
  return labels[props.field]
})

function startEdit() {
  if (!props.editable || editing.value) return
  draft.value = currentValue.value === null ? '' : String(currentValue.value)
  editing.value = true
  nextTick(() => inputRef.value?.focus())
}

function cancel() {
  if (saving.value) return
  editing.value = false
  draft.value = ''
}

async function commit() {
  // Enter 触发后紧跟 blur 时避免重复提交
  if (!editing.value || saving.value) return
  const raw = draft.value.trim()

  let nextText = raw
  let nextValue: string | number | null = raw || null
  if (props.type === 'number' && raw) {
    const num = Number(raw)
    if (!Number.isFinite(num)) {
      ElMessage.warning(`${fieldLabel.value}请输入数字`)
      cancel()
      return
    }
    if (num < 0) {
      ElMessage.warning(`${fieldLabel.value}不能为负数`)
      cancel()
      return
    }
    const rounded = Number(num.toFixed(2))
    if (rounded !== num) {
      ElMessage.info(`${fieldLabel.value}已保留两位小数`)
    }
    nextText = rounded.toFixed(2)
    nextValue = rounded
  }

  // 与原值一致时不发请求
  if (props.type === 'number') {
    const current = currentValue.value === null ? null : Number(Number(currentValue.value).toFixed(2))
    if (current === nextValue) {
      cancel()
      return
    }
  } else if ((currentValue.value === null ? '' : String(currentValue.value)) === raw) {
    cancel()
    return
  }

  saving.value = true
  try {
    const saved =
      props.target === 'transfer'
        ? await updateTransferField(props.row.id, props.field as TransferEditableField, nextText)
        : await updateRouteField(props.row.id, props.field as RouteEditableField, nextText)
    if (props.type === 'number') {
      emit('updated', saved === null ? null : Number(saved))
    } else {
      emit('updated', saved ?? null)
    }
    ElMessage.success('已保存')
    editing.value = false
  } catch (error) {
    const err = error as { message?: string; __toasted?: boolean }
    if (!err.__toasted) ElMessage.error(err.message || '保存失败')
    // 未改动 row，界面保持原值
    editing.value = false
    draft.value = ''
  } finally {
    saving.value = false
  }
}
</script>

<style scoped>
.route-field-editor {
  min-width: 0;
  min-height: 24px;
}

.route-field-editor.is-right {
  text-align: right;
}

.route-field-editor__value {
  display: inline-block;
  max-width: 100%;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  vertical-align: middle;
}

.route-field-editor__value.is-editable {
  cursor: pointer;
  padding: 0 4px;
  border: 1px dashed #dcdfe6;
  border-radius: 2px;
  line-height: 20px;
}

.route-field-editor__value.is-editable:hover {
  color: var(--el-color-primary);
  border-color: #c0c4cc;
}

.route-field-editor__input {
  width: 100%;
  min-width: 0;
}

.route-field-editor__input :deep(.el-input__inner) {
  padding: 0 6px;
  text-align: inherit;
}
</style>
