<template>
  <el-dialog
    v-model="visible"
    :title="title"
    width="520px"
    :close-on-click-modal="false"
    append-to-body
  >
    <el-form
      v-if="mode === 'depart'"
      ref="formRef"
      :model="form"
      :rules="rules"
      label-width="112px"
    >
      <el-form-item label="车次">
        <span class="action-value">{{ trip?.tripNo }} · {{ trip?.tripName }}</span>
      </el-form-item>
      <el-form-item label="配载票数">
        <span class="action-value">
          {{ trip?.totalWaybillCount || 0 }} 票 / {{ formatQuantity(trip?.totalWeight) }} kg /
          {{ formatQuantity(trip?.totalVolume) }} m³
        </span>
      </el-form-item>
      <el-form-item label="预计到达时间" prop="planArriveTime">
        <el-date-picker
          v-model="form.planArriveTime"
          type="datetime"
          value-format="YYYY-MM-DDTHH:mm:ss"
          format="YYYY-MM-DD HH:mm"
          placeholder="请选择预计到达时间"
          class="action-control"
        />
      </el-form-item>
      <p class="action-alert">发车后车辆信息与成本费用锁定，配载运单不可再移除。</p>
    </el-form>

    <el-form v-else ref="formRef" :model="form" :rules="rules" label-width="80px">
      <el-form-item :label="mode === 'close' ? '关闭原因' : '取消原因'" prop="reason">
        <el-input
          v-model="form.reason"
          type="textarea"
          :rows="3"
          maxlength="500"
          show-word-limit
          :placeholder="mode === 'close' ? '请填写关闭原因' : '请填写取消发车原因'"
        />
      </el-form-item>
      <p class="action-alert">
        {{
          mode === 'close'
            ? '关闭仅限发车前，未发运的配载运单会释放回待配载池。'
            : '取消后车次回到装车配载，运单状态回退为分拨装车。'
        }}
      </p>
    </el-form>

    <template #footer>
      <el-button :disabled="saving" @click="close">取消</el-button>
      <el-button
        :type="mode === 'close' ? 'danger' : 'primary'"
        :loading="saving"
        @click="submit"
      >
        {{ mode === 'close' ? '确认关闭' : mode === 'depart' ? '确认发车' : '确认取消发车' }}
      </el-button>
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
import { computed, reactive, ref, watch } from 'vue'
import { ElMessage, type FormInstance, type FormRules } from 'element-plus'
import { cancelDepartTrip, closeTrip, departTrip } from '@/api/transport'
import { formatQuantity } from '@/utils/operationDict'
import type { TripRecord } from '@/types/transport'

const props = defineProps<{
  modelValue: boolean
  mode: 'depart' | 'cancel' | 'close'
  trip: TripRecord | null
}>()

const emit = defineEmits<{
  'update:modelValue': [value: boolean]
  done: []
}>()

const visible = computed({
  get: () => props.modelValue,
  set: (value: boolean) => emit('update:modelValue', value)
})

const formRef = ref<FormInstance>()
const saving = ref(false)
const form = reactive({ planArriveTime: '', reason: '' })

const title = computed(() => {
  if (props.mode === 'depart') return `发车确认 · ${props.trip?.tripNo || ''}`
  if (props.mode === 'close') return `关闭车次 · ${props.trip?.tripNo || ''}`
  return `取消发车 · ${props.trip?.tripNo || ''}`
})

const rules = computed<FormRules>(() =>
  props.mode === 'depart'
    ? {
        planArriveTime: [
          { required: true, message: '预计到达时间不能为空', trigger: 'change' }
        ]
      }
    : {
        reason: [{ required: true, message: '操作原因不能为空', trigger: 'blur' }]
      }
)

function defaultArriveTime(): string {
  const base = props.trip?.planArriveTime
  if (base) return base
  const date = new Date(Date.now() + 10 * 3600 * 1000)
  const pad = (value: number) => String(value).padStart(2, '0')
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}T${pad(
    date.getHours()
  )}:${pad(date.getMinutes())}:00`
}

async function submit() {
  if (!props.trip || !formRef.value) return
  const valid = await formRef.value.validate().catch(() => false)
  if (!valid) return
  saving.value = true
  try {
    if (props.mode === 'depart') {
      await departTrip(props.trip.id, form.planArriveTime)
      ElMessage.success('已发车')
    } else if (props.mode === 'close') {
      await closeTrip(props.trip.id, form.reason.trim())
      ElMessage.success('车次已关闭')
    } else {
      await cancelDepartTrip(props.trip.id, form.reason.trim())
      ElMessage.success('已取消发车')
    }
    close()
    emit('done')
  } catch {
    // 请求层按响应体 code 提示
  } finally {
    saving.value = false
  }
}

function close() {
  visible.value = false
}

watch(
  () => props.modelValue,
  (open) => {
    if (!open) return
    formRef.value?.clearValidate()
    form.reason = ''
    form.planArriveTime = props.mode === 'depart' ? defaultArriveTime() : ''
  },
  { immediate: true }
)
</script>

<style scoped>
.action-control {
  width: 100%;
}

.action-value {
  color: var(--color-text-primary);
  line-height: 1.6;
}

.action-alert {
  margin: 4px 0 0;
  padding: 8px 12px;
  border-radius: var(--radius-xs);
  background: var(--el-color-warning-light-9);
  color: var(--el-color-warning-dark-2);
  font-size: var(--font-size-extra-small);
  line-height: 1.6;
}
</style>
