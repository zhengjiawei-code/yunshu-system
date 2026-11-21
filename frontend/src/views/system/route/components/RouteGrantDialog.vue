<template>
  <el-dialog
    v-model="visible"
    title="路由授权"
    width="720px"
    :close-on-click-modal="false"
    destroy-on-close
    @open="handleOpen"
  >
    <div v-loading="loading" class="grant-body">
      <div class="route-summary">
        <span class="route-summary__code">{{ route?.routeCode }}</span>
        <span class="route-summary__name">{{ route?.routeName }}</span>
      </div>

      <el-alert
        v-if="hasAll"
        type="info"
        :closable="false"
        show-icon
        title="已授权全部分公司（动态，含后续新增）"
        description="新增分公司自动生效，无需逐家授权。如需精确控制，可先取消全部授权再按公司授权。"
        class="grant-alert"
      />

      <div v-else-if="isHq" class="grant-add">
        <el-select
          v-model="selectedCompanyIds"
          multiple
          filterable
          collapse-tags
          :max-collapse-tags="3"
          placeholder="选择要授权的分公司"
          class="grant-add__select"
        >
          <el-option
            v-for="item in companyOptions"
            :key="item.id"
            :label="item.deptName"
            :value="item.id"
          />
        </el-select>
        <el-button type="primary" :loading="saving" @click="handleGrant">添加授权</el-button>
      </div>

      <el-table :data="grants" border size="small" class="grant-table">
        <el-table-column label="分公司" min-width="180">
          <template #default="{ row }">{{ row.companyName || '—' }}</template>
        </el-table-column>
        <el-table-column label="授权来源" width="120" align="center">
          <template #default="{ row }">
            <status-tag :type="sourceTag(row.grantSource)" :text="row.grantSourceName" />
          </template>
        </el-table-column>
        <el-table-column label="授权人" width="120">
          <template #default="{ row }">{{ row.grantByName || '—' }}</template>
        </el-table-column>
        <el-table-column label="授权时间" width="180" align="center">
          <template #default="{ row }">{{ row.grantTime || '—' }}</template>
        </el-table-column>
        <el-table-column label="操作" width="110" align="right">
          <template #default="{ row }">
            <el-button
              v-if="canRevoke(row)"
              link
              type="danger"
              size="small"
              @click="handleRevoke(row)"
            >
              {{ row.companyDeptId === 0 ? '取消全部授权' : '取消授权' }}
            </el-button>
            <span v-else>—</span>
          </template>
        </el-table-column>
      </el-table>

      <p v-if="grants.length === 0" class="grant-empty">暂无授权记录，取消授权后路由将自动停用</p>
    </div>

    <template #footer>
      <el-button @click="visible = false">关闭</el-button>
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
import { computed, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import type { DeptNode } from '@/types/dept'
import type { RouteGrantRecord } from '@/types/route'
import { getDeptOptions } from '@/api/system/dept'
import { getRouteGrants, grantRoute, revokeRouteGrant } from '@/api/system/route'
import StatusTag from '@/components/base/StatusTag.vue'

const props = defineProps<{
  modelValue: boolean
  routeScope: 1 | 2
  route: { id: number; routeCode: string; routeName: string } | null
  isHq: boolean
}>()

const emit = defineEmits<{
  'update:modelValue': [value: boolean]
  changed: []
}>()

const visible = computed({
  get: () => props.modelValue,
  set: (value: boolean) => emit('update:modelValue', value)
})

const loading = ref(false)
const saving = ref(false)
const grants = ref<RouteGrantRecord[]>([])
const companyOptions = ref<DeptNode[]>([])
const selectedCompanyIds = ref<number[]>([])

const hasAll = computed(() => grants.value.some((item) => item.companyDeptId === 0))

function sourceTag(source: number) {
  if (source === 3) return 'info' as const
  if (source === 2) return 'warning' as const
  return 'primary' as const
}

function canRevoke(row: RouteGrantRecord) {
  if (props.isHq) return true
  return row.companyDeptId !== 0
}

async function loadGrants() {
  if (!props.route) return
  loading.value = true
  try {
    grants.value = await getRouteGrants(props.routeScope, props.route.id)
  } catch (error) {
    const message = (error as { message?: string })?.message
    if (message) ElMessage.error(message)
  } finally {
    loading.value = false
  }
}

async function loadCompanies() {
  if (!props.isHq) return
  try {
    companyOptions.value = await getDeptOptions(2)
  } catch {
    // 忽略
  }
}

async function handleOpen() {
  selectedCompanyIds.value = []
  await Promise.all([loadGrants(), loadCompanies()])
}

async function handleGrant() {
  if (!props.route) return
  if (selectedCompanyIds.value.length === 0) {
    ElMessage.warning('请选择要授权的分公司')
    return
  }
  saving.value = true
  try {
    const added = await grantRoute(props.routeScope, props.route.id, selectedCompanyIds.value)
    ElMessage.success(`已新增 ${added} 条授权`)
    selectedCompanyIds.value = []
    await loadGrants()
    emit('changed')
  } catch (error) {
    const message = (error as { message?: string })?.message
    if (message) ElMessage.error(message)
  } finally {
    saving.value = false
  }
}

async function handleRevoke(row: RouteGrantRecord) {
  if (!props.route) return
  const label = row.companyDeptId === 0 ? '全部授权' : `「${row.companyName}」的授权`
  try {
    await ElMessageBox.confirm(`确定取消${label}吗？`, '取消授权', {
      confirmButtonText: '确定',
      cancelButtonText: '取消',
      type: 'warning'
    })
  } catch {
    return
  }
  try {
    await revokeRouteGrant(props.routeScope, props.route.id, row.companyDeptId)
    ElMessage.success('已取消授权')
    await loadGrants()
    emit('changed')
  } catch (error) {
    const message = (error as { message?: string })?.message
    if (message) ElMessage.error(message)
  }
}
</script>

<style scoped>
.grant-body {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.route-summary {
  display: flex;
  align-items: baseline;
  gap: 8px;
}

.route-summary__code {
  font-family: var(--font-family-number);
  font-size: var(--font-size-extra-small);
  color: var(--color-text-secondary);
}

.route-summary__name {
  font-weight: var(--font-weight-medium);
  color: var(--color-text-primary);
}

.grant-alert {
  margin-bottom: 4px;
}

.grant-add {
  display: flex;
  gap: 8px;
}

.grant-add__select {
  flex: 1;
}

.grant-table {
  width: 100%;
}

.grant-empty {
  margin: 0;
  font-size: var(--font-size-extra-small);
  color: var(--color-text-placeholder);
}
</style>
