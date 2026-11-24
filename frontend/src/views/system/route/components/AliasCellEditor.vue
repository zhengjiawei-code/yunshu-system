<template>
  <div class="alias-editor">
    <el-input
      v-if="editing"
      ref="inputRef"
      v-model="draft"
      size="small"
      maxlength="64"
      :disabled="saving"
      @keyup.enter="commit"
      @keyup.esc="cancel"
      @blur="commit"
    />
    <span
      v-else
      class="alias-editor__value"
      :class="{ 'is-editable': editable }"
      :title="row.alias || ''"
      @click="startEdit"
    >{{ row.alias || '—' }}</span>
  </div>
</template>

<script setup lang="ts">
import { nextTick, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { updateTransferAlias } from '@/api/system/route'
import type { TransferRecord } from '@/types/route'

const props = defineProps<{
  row: TransferRecord
  editable: boolean
}>()

const emit = defineEmits<{
  updated: [alias: string]
}>()

const editing = ref(false)
const saving = ref(false)
const draft = ref('')
const inputRef = ref<{ focus: () => void } | null>(null)

function startEdit() {
  if (!props.editable || editing.value) return
  draft.value = props.row.alias || ''
  editing.value = true
  nextTick(() => inputRef.value?.focus())
}

function cancel() {
  if (saving.value) return
  editing.value = false
  draft.value = props.row.alias || ''
}

async function commit() {
  // Enter 触发后紧接着 blur 时避免重复提交
  if (!editing.value || saving.value) return
  const value = draft.value.trim()
  if (!value) {
    ElMessage.warning('别名不能为空')
    editing.value = false
    draft.value = props.row.alias || ''
    return
  }
  if (value === (props.row.alias || '')) {
    editing.value = false
    return
  }
  saving.value = true
  try {
    await updateTransferAlias(props.row.id, value)
    ElMessage.success('已保存')
    emit('updated', value)
    editing.value = false
  } catch (error) {
    const message = (error as { message?: string })?.message
    ElMessage.error(message || '保存失败')
    editing.value = false
    draft.value = props.row.alias || ''
  } finally {
    saving.value = false
  }
}
</script>

<style scoped>
.alias-editor {
  min-height: 24px;
}

.alias-editor__value {
  display: inline-block;
  max-width: 100%;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  vertical-align: middle;
}

.alias-editor__value.is-editable {
  cursor: pointer;
  border-bottom: 1px dashed var(--el-color-primary-light-5);
}

.alias-editor__value.is-editable:hover {
  color: var(--el-color-primary);
}
</style>
