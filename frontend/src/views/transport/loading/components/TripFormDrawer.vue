<template>
  <page-drawer
    v-model="visible"
    :title="mode === 'create' ? '创建车次' : `修改车次 ${detail?.trip.tripNo || ''}`"
    :size="720"
  >
    <div v-loading="loading" class="trip-form-wrap">
      <el-form ref="formRef" :model="form" :rules="rules" label-width="120px" label-position="right">
        <div class="form-section-title">基础信息</div>

        <el-form-item label="车次名称" prop="tripName">
          <el-input
            v-model="form.tripName"
            maxlength="64"
            show-word-limit
            placeholder="如：济南—青岛干线"
          />
        </el-form-item>

        <el-form-item label="车次类型">
          <el-radio-group v-model="form.tripCategory" disabled>
            <el-radio :value="1">干线</el-radio>
            <el-radio :value="2">短驳（预留）</el-radio>
          </el-radio-group>
          <span class="form-hint">本期仅支持干线，短驳字段预留</span>
        </el-form-item>

        <el-form-item label="始发网点" prop="originDeptId">
          <el-select
            v-model="form.originDeptId"
            filterable
            :disabled="destLocked"
            placeholder="请选择始发网点"
            class="form-control"
          >
            <el-option
              v-for="item in deptOptions"
              :key="item.id"
              :label="item.name"
              :value="item.id"
            />
          </el-select>
          <span v-if="destLocked" class="form-hint">已有配载，始发网点不可修改</span>
          <span v-else-if="mode === 'create' && !form.originDeptId" class="form-hint">
            当前机构非分拨中心/网点时，需手动选择
          </span>
        </el-form-item>

        <el-form-item label="目的网点" prop="destDeptIds">
          <el-select
            v-model="form.destDeptIds"
            multiple
            filterable
            clearable
            collapse-tags
            collapse-tags-tooltip
            :disabled="destLocked"
            placeholder="至少选择 1 个目的网点"
            class="form-control"
          >
            <el-option
              v-for="item in deptOptions"
              :key="item.id"
              :label="item.name"
              :value="item.id"
            />
          </el-select>
          <span class="form-hint">可多个，保存后按目的网点自动生成子车次</span>
        </el-form-item>

        <el-form-item label="计划发车时间" prop="planDepartTime">
          <el-date-picker
            v-model="form.planDepartTime"
            type="datetime"
            value-format="YYYY-MM-DDTHH:mm:ss"
            format="YYYY-MM-DD HH:mm"
            placeholder="请选择计划发车时间"
            :disabled="planDepartLocked"
            class="form-control"
          />
        </el-form-item>

        <el-form-item label="计划到站时间">
          <el-date-picker
            v-model="form.planArriveTime"
            type="datetime"
            value-format="YYYY-MM-DDTHH:mm:ss"
            format="YYYY-MM-DD HH:mm"
            placeholder="选填，用于生成子车次计划到站"
            :disabled="destLocked"
            class="form-control"
          />
          <span v-if="destLocked" class="form-hint">已有配载运单，计划到站时间不可修改</span>
        </el-form-item>

        <el-form-item label="备注">
          <el-input
            v-model="form.remark"
            type="textarea"
            :rows="3"
            maxlength="500"
            show-word-limit
            placeholder="选填"
          />
        </el-form-item>
      </el-form>
    </div>

    <template #footer>
      <el-button :disabled="saving" @click="close">取消</el-button>
      <el-button type="primary" :loading="saving" @click="submit">保存</el-button>
    </template>
  </page-drawer>
</template>

<script setup lang="ts">
import { computed, reactive, ref, watch } from 'vue'
import { ElMessage, type FormInstance, type FormRules } from 'element-plus'
import PageDrawer from '@/components/base/PageDrawer.vue'
import { useUserStore } from '@/stores/user'
import { createTrip, getTripDetail, getTripOptions, updateTrip, updateTripDest } from '@/api/transport'
import type { TripActionDone, TripDetail } from '@/types/transport'

const props = defineProps<{
  modelValue: boolean
  mode: 'create' | 'edit'
  tripId?: number
}>()

const emit = defineEmits<{
  'update:modelValue': [value: boolean]
  saved: [payload: TripActionDone]
}>()

const visible = computed({
  get: () => props.modelValue,
  set: (value: boolean) => emit('update:modelValue', value)
})

const userStore = useUserStore()
const formRef = ref<FormInstance>()
const loading = ref(false)
const saving = ref(false)
const detail = ref<TripDetail | null>(null)
const deptOptions = ref<Array<{ id: number; name: string }>>([])
const initialDestIds = ref<number[]>([])
const initialOriginId = ref<number | undefined>(undefined)
const initialPlanArrive = ref('')

const form = reactive<{
  tripName: string
  tripCategory: number
  originDeptId: number | undefined
  planDepartTime: string
  planArriveTime: string
  destDeptIds: number[]
  remark: string
}>({
  tripName: '',
  tripCategory: 1,
  originDeptId: undefined,
  planDepartTime: '',
  planArriveTime: '',
  destDeptIds: [],
  remark: ''
})

const rules: FormRules = {
  tripName: [{ required: true, message: '请填写车次名称', trigger: 'blur' }],
  originDeptId: [{ required: true, message: '请选择始发网点', trigger: 'change' }],
  planDepartTime: [{ required: true, message: '请选择计划发车时间', trigger: 'change' }],
  destDeptIds: [
    {
      type: 'array',
      required: true,
      min: 1,
      message: '目的网点至少选择 1 个',
      trigger: 'change'
    }
  ]
}

/** 已发车后计划发车时间锁定 */
const planDepartLocked = computed(() => {
  const status = detail.value?.trip.tripStatus
  return props.mode === 'edit' && status !== undefined && status >= 30
})

/** 已有配载或非车次计划状态时，目的网点与计划到站不可改（后端 D10 规则） */
const destLocked = computed(() => {
  const trip = detail.value?.trip
  if (props.mode !== 'edit' || !trip) return false
  return trip.tripStatus !== 10 || (trip.totalWaybillCount || 0) > 0
})

function isoAfterHours(hours: number): string {
  const date = new Date(Date.now() + hours * 3600 * 1000)
  const pad = (value: number) => String(value).padStart(2, '0')
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}T${pad(
    date.getHours()
  )}:${pad(date.getMinutes())}:00`
}

function commonPlanArrive(dests: NonNullable<TripDetail['dests']>): string {
  const values = dests.map((item) => item.planArriveTime || '')
  return values.length ? values[0] : ''
}

/** 始发网点必须显式选择：仅当前机构本身是分拨中心/网点时带出，否则留空强校验 */
function ensureDefaultOrigin() {
  if (props.mode !== 'create' || form.originDeptId || !deptOptions.value.length) return
  const current = deptOptions.value.find((item) => item.id === userStore.currentOrgId)
  form.originDeptId = current?.id
}

function resetCreate() {
  detail.value = null
  initialDestIds.value = []
  initialOriginId.value = undefined
  initialPlanArrive.value = ''
  form.tripName = ''
  form.tripCategory = 1
  form.originDeptId = undefined
  form.planDepartTime = isoAfterHours(2)
  form.planArriveTime = ''
  form.destDeptIds = []
  form.remark = ''
  ensureDefaultOrigin()
}

async function loadEdit(id: number) {
  loading.value = true
  try {
    const result = await getTripDetail(id)
    detail.value = result
    initialDestIds.value = result.dests.map((item) => item.destDeptId)
    form.tripName = result.trip.tripName || ''
    form.tripCategory = result.trip.tripCategory || 1
    form.originDeptId = result.trip.originDeptId
    initialOriginId.value = result.trip.originDeptId
    form.planDepartTime = result.trip.planDepartTime || ''
    form.planArriveTime = commonPlanArrive(result.dests)
    initialPlanArrive.value = form.planArriveTime
    form.destDeptIds = [...initialDestIds.value]
    form.remark = result.trip.remark || ''
  } catch {
    // 请求层已提示
  } finally {
    loading.value = false
  }
}

async function loadDeptOptions() {
  if (!deptOptions.value.length) {
    try {
      const options = await getTripOptions('dept')
      deptOptions.value = options
        .filter((item) => !!item.name)
        .map((item) => ({ id: item.id, name: item.name as string }))
    } catch {
      // 下拉失败不阻断抽屉，保存时后端会再次校验
    }
  }
  ensureDefaultOrigin()
}

/** 计划到站时间落到每个子车次（#22）；创建后子车次已生成 */
async function applyPlanArrive(tripId: number, destIds: number[], planArriveTime: string) {
  if (!planArriveTime || !destIds.length) return
  try {
    await Promise.all(
      destIds.map((destId) => updateTripDest(tripId, destId, { planArriveTime }))
    )
  } catch {
    ElMessage.warning('车次已保存，但计划到站时间同步子车次失败，请在配载页调整')
  }
}

function sameDestIds(next: number[]): boolean {
  if (next.length !== initialDestIds.value.length) return false
  const a = [...next].sort((x, y) => x - y)
  const b = [...initialDestIds.value].sort((x, y) => x - y)
  return a.every((value, index) => value === b[index])
}

async function submit() {
  if (!formRef.value) return
  const valid = await formRef.value.validate().catch(() => false)
  if (!valid) return
  if (form.planArriveTime && form.planDepartTime && form.planArriveTime <= form.planDepartTime) {
    ElMessage.warning('计划到站时间必须晚于计划发车时间')
    return
  }

  saving.value = true
  try {
    if (props.mode === 'create') {
      const result = await createTrip({
        tripName: form.tripName.trim(),
        originDeptId: form.originDeptId as number,
        planDepartTime: form.planDepartTime,
        destDeptIds: form.destDeptIds,
        remark: form.remark.trim() || undefined
      })
      await applyPlanArrive(
        result.id,
        result.subTrips.map((item) => item.id),
        form.planArriveTime
      )
      ElMessage.success('车次已创建，已进入装车配载')
      close()
      emit('saved', { id: result.id, tripNo: result.tripNo })
      return
    }

    const id = props.tripId as number
    const destChanged = !destLocked.value && !sameDestIds(form.destDeptIds)
    const originChanged = !destLocked.value && form.originDeptId !== initialOriginId.value
    await updateTrip(id, {
      tripName: form.tripName.trim(),
      planDepartTime: form.planDepartTime,
      remark: form.remark.trim() || '',
      ...(originChanged ? { originDeptId: form.originDeptId as number } : {}),
      ...(destChanged ? { destDeptIds: form.destDeptIds } : {})
    })
    const arriveChanged = form.planArriveTime !== initialPlanArrive.value
    if (form.planArriveTime && (arriveChanged || destChanged)) {
      const latest = await getTripDetail(id)
      await applyPlanArrive(
        id,
        latest.dests.map((item) => item.id),
        form.planArriveTime
      )
    }
    ElMessage.success('车次已保存')
    close()
    emit('saved', { id, tripNo: detail.value?.trip.tripNo })
  } catch {
    // 请求层按响应体 code 提示，此处不重复弹窗
  } finally {
    saving.value = false
  }
}

function close() {
  visible.value = false
}

watch(
  () => [props.modelValue, props.mode, props.tripId] as const,
  ([open]) => {
    if (!open) return
    formRef.value?.clearValidate()
    if (props.mode === 'edit' && props.tripId) {
      loadEdit(props.tripId)
    } else {
      resetCreate()
    }
    loadDeptOptions()
  },
  { immediate: true }
)
</script>

<style scoped>
.trip-form-wrap {
  min-height: 120px;
}

.form-control {
  width: 100%;
}

.form-hint {
  margin-left: 8px;
  color: var(--color-text-placeholder);
  font-size: var(--font-size-extra-small);
  line-height: 1.4;
}

:deep(.el-date-editor.form-control) {
  width: 100%;
}
</style>
