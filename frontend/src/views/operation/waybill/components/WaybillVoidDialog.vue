<template>
  <el-dialog
    :model-value="visible"
    title="作废运单"
    width="520px"
    :close-on-click-modal="false"
    @update:model-value="close"
    @closed="reset"
  >
    <div class="void-body">
      <div class="void-line">
        运单号 <b>{{ waybillNo || '—' }}</b>
        <span v-if="statusTextValue" class="void-status">当前状态：{{ statusTextValue }}</span>
      </div>
      <div class="void-tip">
        一期任意状态均可作废；作废后状态置为「已作废」，货物、路由与操作日志保留，不物理删除。
      </div>
      <el-input
        v-model="reason"
        type="textarea"
        :rows="4"
        maxlength="500"
        show-word-limit
        placeholder="请填写作废原因（必填，≤500 字）"
      />
    </div>
    <template #footer>
      <el-button @click="close(false)">取消</el-button>
      <el-button type="danger" :loading="submitting" @click="submit">确认作废</el-button>
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
import { computed, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { voidWaybill } from '@/api/operation'
import { statusText } from '@/utils/operationDict'

const props = defineProps<{
  visible: boolean
  waybillId?: number
  waybillNo?: string
  status?: number
  version?: string
}>()

const emit = defineEmits<{
  'update:visible': [value: boolean]
  done: []
}>()

const reason = ref('')
const submitting = ref(false)
const statusTextValue = computed(() => (props.status ? statusText(props.status) : ''))

function close(value = false) {
  emit('update:visible', value)
}

function reset() {
  reason.value = ''
  submitting.value = false
}

async function submit() {
  if (!props.waybillId) return
  const text = reason.value.trim()
  if (!text) {
    ElMessage.warning('请填写作废原因')
    return
  }
  if (!props.version) {
    ElMessage.warning('缺少运单版本信息，请刷新后重试')
    return
  }
  try {
    await ElMessageBox.confirm('作废后不可恢复，确认继续？', '二次确认', {
      confirmButtonText: '确认作废',
      cancelButtonText: '取消',
      type: 'warning'
    })
  } catch {
    return
  }
  submitting.value = true
  try {
    await voidWaybill(props.waybillId, { reason: text, version: props.version })
    ElMessage.success('运单已作废')
    emit('update:visible', false)
    emit('done')
  } catch {
    // 请求层已提示
  } finally {
    submitting.value = false
  }
}
</script>

<style scoped>
.void-body {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.void-line {
  color: var(--color-text-secondary);
}

.void-line b {
  color: var(--color-text-primary);
  font-family: var(--font-family-number);
}

.void-status {
  margin-left: 12px;
}

.void-tip {
  padding: 8px 10px;
  border-radius: 4px;
  background: #fdf7f5;
  border-left: 3px solid #e2b3ab;
  color: #8f4444;
  font-size: 12px;
  line-height: 1.6;
}
</style>
