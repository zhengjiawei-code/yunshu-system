<template>
  <page-drawer
    :model-value="modelValue"
    :title="`子车次（目的网点） · ${trip?.tripNo || ''}`"
    :size="720"
    @update:model-value="(value: boolean) => emit('update:modelValue', value)"
  >
    <div class="dest-toolbar">
      <el-button type="primary" size="small" @click="openAdd">+ 新增目的网点</el-button>
      <span class="dest-toolbar__hint">发车后新增的子车次直接置「在途」；删除需该网点无配载运单</span>
    </div>

    <el-table :data="dests" v-loading="loading" size="small" border class="dest-table" show-overflow-tooltip>
      <el-table-column prop="subTripNo" label="子车次号" min-width="140" />
      <el-table-column prop="destDeptName" label="目的网点" min-width="120" />
      <el-table-column label="票数" align="right" width="72">
        <template #default="{ row }">{{ row.totalWaybillCount ?? 0 }}</template>
      </el-table-column>
      <el-table-column label="件数" align="right" width="80">
        <template #default="{ row }">{{ formatQuantity(row.totalQuantity) }}</template>
      </el-table-column>
      <el-table-column label="重量kg" align="right" width="100">
        <template #default="{ row }">{{ formatQuantity(row.totalWeight) }}</template>
      </el-table-column>
      <el-table-column label="体积m³" align="right" width="96">
        <template #default="{ row }">{{ formatQuantity(row.totalVolume) }}</template>
      </el-table-column>
      <el-table-column label="状态" width="88">
        <template #default="{ row }">
          <status-tag :type="subStatusTagType(row.subStatus)" :text="subStatusText(row.subStatus)" />
        </template>
      </el-table-column>
      <el-table-column label="计划到站" min-width="140">
        <template #default="{ row }">{{ formatDateTime(row.planArriveTime) }}</template>
      </el-table-column>
      <el-table-column label="实际到站" min-width="140">
        <template #default="{ row }">{{ formatDateTime(row.arriveTime) }}</template>
      </el-table-column>
      <el-table-column label="卸车时间" min-width="140">
        <template #default="{ row }">{{ formatDateTime(row.unloadTime) }}</template>
      </el-table-column>
      <el-table-column label="操作" width="140" fixed="right">
        <template #default="{ row }">
          <el-button link type="primary" size="small" @click="openEdit(row)">修改</el-button>
          <el-button link type="danger" size="small" @click="remove(row)">删除</el-button>
        </template>
      </el-table-column>
    </el-table>

    <template #footer>
      <el-button @click="close">关闭</el-button>
    </template>

    <el-dialog v-model="addVisible" title="新增目的网点" width="430px" append-to-body destroy-on-close>
      <el-form label-width="96px">
        <el-form-item label="目的网点" required>
          <el-select v-model="addDestDeptId" filterable placeholder="请选择目的网点" class="w-full">
            <el-option
              v-for="item in deptOptions"
              :key="item.id"
              :label="item.name"
              :value="item.id"
              :disabled="usedDeptIds.has(item.id)"
            />
          </el-select>
        </el-form-item>
      </el-form>
      <div class="dialog-hint">新增不受车次状态限制；发车后新增的子车次直接置「在途」。</div>
      <template #footer>
        <el-button @click="addVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="submitAdd">确定</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="editVisible" title="修改子车次" width="430px" append-to-body destroy-on-close>
      <el-form label-width="96px">
        <el-form-item label="子车次号">
          <span class="mono">{{ editing?.subTripNo }}</span>
        </el-form-item>
        <el-form-item label="目的网点">
          <span>{{ editing?.destDeptName }}</span>
        </el-form-item>
        <el-form-item label="计划到站">
          <el-date-picker
            v-model="editPlanArrive"
            type="datetime"
            value-format="YYYY-MM-DDTHH:mm:ss"
            format="YYYY-MM-DD HH:mm"
            placeholder="请选择计划到站时间"
            class="w-full"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="editVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="submitEdit">保存</el-button>
      </template>
    </el-dialog>
  </page-drawer>
</template>

<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import PageDrawer from '@/components/base/PageDrawer.vue'
import StatusTag from '@/components/base/StatusTag.vue'
import { addTripDest, deleteTripDest, getTripOptions, updateTripDest } from '@/api/transport'
import type { TripDest, TripRecord } from '@/types/transport'
import { formatDateTime, formatQuantity } from '@/utils/operationDict'

const props = defineProps<{
  modelValue: boolean
  trip: TripRecord | null
  dests: TripDest[]
}>()

const emit = defineEmits<{
  'update:modelValue': [value: boolean]
  refresh: []
}>()

const loading = ref(false)
const saving = ref(false)
const deptOptions = ref<Array<{ id: number; name: string }>>([])

const addVisible = ref(false)
const addDestDeptId = ref<number | undefined>(undefined)

const editVisible = ref(false)
const editing = ref<TripDest | null>(null)
const editPlanArrive = ref('')

const usedDeptIds = computed(() => new Set(props.dests.map((item) => item.destDeptId)))

function subStatusText(value?: number | null): string {
  switch (value) {
    case 10:
      return '待发车'
    case 20:
      return '在途'
    case 30:
      return '已到站'
    case 40:
      return '已卸车'
    default:
      return '—'
  }
}

function subStatusTagType(value?: number | null): 'primary' | 'success' | 'info' | 'warning' {
  if (value === 30) return 'warning'
  if (value === 40) return 'success'
  if (value === 20) return 'primary'
  return 'info'
}

async function loadDeptOptions() {
  if (deptOptions.value.length) return
  try {
    const options = await getTripOptions('dept')
    deptOptions.value = options
      .filter((item) => !!item.name)
      .map((item) => ({ id: item.id, name: item.name as string }))
  } catch {
    // 请求层已提示
  }
}

function openAdd() {
  loadDeptOptions()
  addDestDeptId.value = undefined
  addVisible.value = true
}

async function submitAdd() {
  if (!props.trip) return
  if (!addDestDeptId.value) {
    ElMessage.warning('请选择目的网点')
    return
  }
  saving.value = true
  try {
    await addTripDest(props.trip.id, { destDeptId: addDestDeptId.value })
    ElMessage.success('目的网点已新增')
    addVisible.value = false
    emit('refresh')
  } catch {
    // 请求层已提示
  } finally {
    saving.value = false
  }
}

function openEdit(row: TripDest) {
  editing.value = row
  editPlanArrive.value = row.planArriveTime || ''
  editVisible.value = true
}

async function submitEdit() {
  if (!props.trip || !editing.value) return
  saving.value = true
  try {
    await updateTripDest(props.trip.id, editing.value.id, {
      planArriveTime: editPlanArrive.value || undefined
    })
    ElMessage.success('子车次已修改')
    editVisible.value = false
    emit('refresh')
  } catch {
    // 请求层已提示
  } finally {
    saving.value = false
  }
}

async function remove(row: TripDest) {
  if (!props.trip) return
  if ((row.totalWaybillCount ?? 0) > 0) {
    ElMessage.warning('该目的网点下仍有配载运单，请先移除配载')
    return
  }
  if (props.dests.length <= 1) {
    ElMessage.warning('至少保留一个目的网点')
    return
  }
  try {
    await ElMessageBox.confirm(`确认删除子车次 ${row.subTripNo}（${row.destDeptName}）？`, '删除目的网点', {
      type: 'warning',
      confirmButtonText: '删除',
      cancelButtonText: '取消'
    })
  } catch {
    return
  }
  try {
    await deleteTripDest(props.trip.id, row.id)
    ElMessage.success('目的网点已删除')
    emit('refresh')
  } catch {
    // 请求层已提示
  }
}

function close() {
  emit('update:modelValue', false)
}

watch(
  () => props.modelValue,
  (open) => {
    if (open) loadDeptOptions()
  }
)
</script>

<style scoped>
.dest-toolbar {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-bottom: 10px;
}

.dest-toolbar__hint {
  color: var(--color-text-placeholder);
  font-size: var(--font-size-extra-small);
}

.dest-table :deep(.el-table__header th) {
  height: 44px;
}

.w-full {
  width: 100%;
}

.dialog-hint {
  padding: 8px 10px;
  border-radius: var(--radius-xs);
  background: var(--color-bg-subtle);
  color: var(--color-text-secondary);
  font-size: var(--font-size-extra-small);
  line-height: 1.7;
}

.mono {
  font-family: var(--font-family-number);
}
</style>
