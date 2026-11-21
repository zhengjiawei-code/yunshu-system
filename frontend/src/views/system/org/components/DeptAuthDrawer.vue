<template>
  <page-drawer v-model="visible" title="机构授权" :size="560">
    <div v-if="record" class="auth-head">
      <div class="auth-head__name">{{ record.deptName }}</div>
      <div class="auth-head__meta">
        <span class="mono">{{ record.deptCode }}</span>
        <span class="auth-head__dot">·</span>
        <span>{{ typeName(record.deptType) }}</span>
      </div>
    </div>

    <section class="auth-section">
      <h4 class="auth-section__title">业务能力</h4>
      <div class="auth-list">
        <div class="auth-item">
          <div class="auth-item__main">
            <div class="auth-item__title">能否发货</div>
            <div class="auth-item__desc">开启后，该机构可作为发货机构开单；开单模块保存时校验</div>
          </div>
          <el-switch v-model="form.allowShip" />
        </div>
        <div class="auth-item">
          <div class="auth-item__main">
            <div class="auth-item__title">能否到货</div>
            <div class="auth-item__desc">开启后，该机构可作为到货机构；到货卸车模块校验</div>
          </div>
          <el-switch v-model="form.allowArrival" />
        </div>
        <div class="auth-item">
          <div class="auth-item__main">
            <div class="auth-item__title">现付未付开单</div>
            <div class="auth-item__desc">开启后，客户现付运费未结清也可开单；资金风险，默认关闭</div>
          </div>
          <el-switch v-model="form.allowPrepaidUnpaid" />
        </div>
        <div class="auth-item">
          <div class="auth-item__main">
            <div class="auth-item__title">到付未付签收</div>
            <div class="auth-item__desc">开启后，到付运费未结清也可签收；资金风险，默认关闭</div>
          </div>
          <el-switch v-model="form.allowCollectUnpaidSign" />
        </div>
      </div>
    </section>

    <section class="auth-section">
      <h4 class="auth-section__title">扩展授权</h4>
      <p class="auth-section__placeholder">
        后续新增的机构级授权项将在此分区维护，不影响上方业务能力开关。
      </p>
    </section>

    <p class="auth-note">
      业务授权按机构独立生效，<strong>不随父机构自动继承</strong>；这里的开关控制“这个机构能不能做这类业务”，
      与角色的菜单/按钮功能权限是两回事，两者同时校验。
    </p>

    <template #footer>
      <el-button @click="visible = false">取消</el-button>
      <el-button type="primary" :loading="submitting" @click="handleSubmit">保存</el-button>
    </template>
  </page-drawer>
</template>

<script setup lang="ts">
import { computed, reactive, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import type { DeptNode, DeptSavePayload } from '@/types/dept'
import { updateDept } from '@/api/system/dept'
import PageDrawer from '@/components/base/PageDrawer.vue'

const props = defineProps<{
  modelValue: boolean
  record: DeptNode | null
}>()

const emit = defineEmits<{
  'update:modelValue': [value: boolean]
  saved: []
}>()

const TYPE_NAME: Record<number, string> = {
  1: '总部',
  2: '公司',
  3: '部门',
  4: '分拨中心',
  5: '网点'
}

const visible = computed({
  get: () => props.modelValue,
  set: (value: boolean) => emit('update:modelValue', value)
})

const submitting = ref(false)

const form = reactive({
  allowShip: true,
  allowArrival: true,
  allowPrepaidUnpaid: false,
  allowCollectUnpaidSign: false
})

function typeName(type: number): string {
  return TYPE_NAME[type] || '—'
}

watch(
  () => props.modelValue,
  (open) => {
    if (!open || !props.record) return
    form.allowShip = props.record.allowShip
    form.allowArrival = props.record.allowArrival
    form.allowPrepaidUnpaid = props.record.allowPrepaidUnpaid
    form.allowCollectUnpaidSign = props.record.allowCollectUnpaidSign
  }
)

async function handleSubmit() {
  const record = props.record
  if (!record) return

  // 授权抽屉只改 4 个开关，其余字段原样回传，避免后端校验或搜索路径缺字段
  const payload: DeptSavePayload = {
    parentId: record.parentId,
    deptType: record.deptType,
    deptNature: record.deptNature ?? 1,
    isLeaf: record.isLeaf,
    deptName: record.deptName,
    deptCode: record.deptCode,
    allowShip: form.allowShip,
    allowArrival: form.allowArrival,
    allowPrepaidUnpaid: form.allowPrepaidUnpaid,
    allowCollectUnpaidSign: form.allowCollectUnpaidSign,
    leader: record.leader,
    phone: record.phone,
    address: record.address,
    sortOrder: record.sortOrder,
    status: record.status,
    remark: record.remark
  }

  submitting.value = true
  try {
    await updateDept(record.id, payload)
    ElMessage.success('授权已保存')
    visible.value = false
    emit('saved')
  } catch (error) {
    const message = (error as { message?: string })?.message
    if (message) ElMessage.error(message)
  } finally {
    submitting.value = false
  }
}
</script>

<style scoped>
.auth-head {
  padding-bottom: 12px;
  margin-bottom: 16px;
  border-bottom: 1px solid var(--color-border-light);
}

.auth-head__name {
  font-size: var(--font-size-base);
  font-weight: var(--font-weight-semibold);
  color: var(--color-text-primary);
}

.auth-head__meta {
  display: flex;
  align-items: center;
  gap: 6px;
  margin-top: 4px;
  font-size: var(--font-size-extra-small);
  color: var(--color-text-placeholder);
}

.auth-head__dot {
  color: var(--color-text-placeholder);
}

.mono {
  font-family: var(--font-family-number);
}

.auth-section + .auth-section {
  margin-top: 20px;
}

.auth-section__title {
  margin: 0 0 10px;
  font-size: var(--font-size-small);
  font-weight: var(--font-weight-medium);
  color: var(--color-text-primary);
}

.auth-section__placeholder {
  margin: 0;
  padding: 14px;
  font-size: var(--font-size-extra-small);
  color: var(--color-text-placeholder);
  line-height: 1.6;
  text-align: center;
  border: 1px dashed var(--color-border-light);
  border-radius: var(--radius-sm);
  background-color: var(--color-bg-subtle);
}

.auth-list {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.auth-item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  padding: 12px 14px;
  border: 1px solid var(--color-border-light);
  border-radius: var(--radius-sm);
}

.auth-item__main {
  min-width: 0;
}

.auth-item__title {
  font-size: var(--font-size-small);
  font-weight: var(--font-weight-medium);
  color: var(--color-text-primary);
}

.auth-item__desc {
  margin-top: 2px;
  font-size: var(--font-size-extra-small);
  color: var(--color-text-placeholder);
  line-height: 1.5;
}

.auth-note {
  margin: 16px 0 0;
  padding: 10px 12px;
  font-size: var(--font-size-extra-small);
  color: var(--color-text-secondary);
  line-height: 1.6;
  background: var(--color-bg-subtle);
  border-radius: var(--radius-xs);
}
</style>
