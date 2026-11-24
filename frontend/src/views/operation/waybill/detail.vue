<template>
  <div class="detail-page">
    <div class="detail-head">
      <div class="head-left">
        <el-button link :icon="ArrowLeft" @click="goBack">返回</el-button>
        <span class="detail-no">{{ waybill?.waybillNo || '运单详情' }}</span>
        <el-tag v-if="waybill" size="small" effect="light" :type="statusTagType(waybill.status)">
          {{ statusText(waybill.status) }}
        </el-tag>
        <span v-if="waybill?.isChild === 1" class="chip">子单 · 母单 {{ waybill?.parentNo || '—' }}</span>
        <span v-else-if="detail?.childCount" class="chip">母单 · {{ detail?.childCount }} 个子单</span>
      </div>

      <div class="head-right">
        <el-tooltip
          :disabled="!!waybill && canAmend(waybill.status)"
          content="仅 20 已揽收 ~ 90 末端到车 可改单"
          placement="top"
        >
          <span class="btn-wrap">
            <el-button type="primary" :disabled="!waybill || !canAmend(waybill.status)" @click="goAmend">
              改单
            </el-button>
          </span>
        </el-tooltip>
        <el-tooltip :disabled="waybill?.status !== 900" content="该运单已作废" placement="top">
          <span class="btn-wrap">
            <el-button
              type="danger"
              plain
              :disabled="!waybill || waybill.status === 900"
              @click="openVoid"
            >
              作废
            </el-button>
          </span>
        </el-tooltip>
      </div>
    </div>

    <div v-if="waybill" class="detail-context">
      <span>开单网点：{{ waybill.deptName || '—' }}</span>
      <span>开单员：{{ waybill.createByName || '—' }}</span>
      <span>开单时间：{{ formatDateTime(waybill.createTime) }}</span>
      <span>最后更新：{{ formatDateTime(waybill.updateTime) }}</span>
    </div>

    <div v-loading="loading" class="detail-body">
      <template v-if="detail">
        <el-tabs v-model="activeTab" class="detail-tabs">
          <el-tab-pane label="运单详情" name="detail">
            <div class="tab-scroll">
              <waybill-form :model="form" :goods="detail.goods" readonly />
            </div>
          </el-tab-pane>

          <el-tab-pane label="运输轨迹" name="track">
            <el-empty description="运输轨迹将在下一阶段开放" :image-size="80" />
          </el-tab-pane>

          <el-tab-pane label="核销信息" name="verify">
            <el-empty description="核销信息将在下一阶段开放" :image-size="80" />
          </el-tab-pane>

          <el-tab-pane label="回单信息" name="receipt">
            <el-empty description="回单信息将在下一阶段开放" :image-size="80" />
          </el-tab-pane>

          <el-tab-pane label="签收信息" name="sign">
            <el-empty description="签收信息将在下一阶段开放" :image-size="80" />
          </el-tab-pane>

          <el-tab-pane label="改单信息" name="amend">
            <div class="tab-scroll amend-panel">
              <el-table
                :data="detail.amendLogs"
                row-key="id"
                border
                size="small"
                empty-text="暂无改单记录"
              >
                <el-table-column label="运单号" min-width="150" show-overflow-tooltip>
                  <template #default="{ row }">{{ row.waybillNo || '—' }}</template>
                </el-table-column>
                <el-table-column label="改单前后信息" min-width="240" show-overflow-tooltip>
                  <template #default="{ row }">
                    <div
                      v-for="(c, i) in row.changes"
                      :key="i"
                      class="change-line"
                      :title="`${c.label}：${c.before || '空'} → ${c.after || '空'}`"
                    >
                      <span class="change-label">{{ c.label }}：</span>
                      <span class="change-before">{{ c.before || '空' }}</span>
                      <span class="change-arrow">→</span>
                      <span class="change-after">{{ c.after || '空' }}</span>
                    </div>
                  </template>
                </el-table-column>
                <el-table-column label="改单时间" width="190" show-overflow-tooltip>
                  <template #default="{ row }">{{ formatDateTime(row.createTime) }}</template>
                </el-table-column>
                <el-table-column label="修改人" width="130" show-overflow-tooltip>
                  <template #default="{ row }">{{ row.operatorName || '-' }}</template>
                </el-table-column>
              </el-table>
            </div>
          </el-tab-pane>
        </el-tabs>
      </template>

      <el-empty v-else-if="!loading" description="无权访问或运单不存在">
        <el-button type="primary" @click="goBack">返回列表</el-button>
      </el-empty>
    </div>

    <waybill-void-dialog
      v-model:visible="voidVisible"
      :waybill-id="waybill?.id"
      :waybill-no="waybill?.waybillNo"
      :status="waybill?.status"
      :version="waybill?.updateTime"
      @done="loadDetail"
    />
  </div>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ArrowLeft } from '@element-plus/icons-vue'
import type { WaybillDetail } from '@/types/operation'
import { getWaybillDetail } from '@/api/operation'
import {
  canAmend,
  formatDateTime,
  statusTagType,
  statusText
} from '@/utils/operationDict'
import WaybillForm, { type WaybillFormModel } from './components/WaybillForm.vue'
import WaybillVoidDialog from './components/WaybillVoidDialog.vue'

defineOptions({ name: 'OperationWaybillDetail' })

const route = useRoute()
const router = useRouter()

const loading = ref(false)
const detail = ref<WaybillDetail | null>(null)
const waybill = ref<WaybillDetail['waybill'] | null>(null)
const form = reactive<WaybillFormModel>({})
const activeTab = ref('detail')
const voidVisible = ref(false)

function idOfRoute(): number {
  return Number(route.params.id)
}

async function loadDetail() {
  loading.value = true
  try {
    const result = await getWaybillDetail(idOfRoute())
    detail.value = result
    waybill.value = result.waybill
    Object.assign(form, result.waybill)
  } catch {
    detail.value = null
    waybill.value = null
  } finally {
    loading.value = false
  }
}

function goBack() {
  router.push('/operation/waybill')
}

function goAmend() {
  if (!waybill.value || !canAmend(waybill.value.status)) return
  router.push(`/operation/waybill/${waybill.value.id}/amend`)
}

function openVoid() {
  if (!waybill.value || waybill.value.status === 900) return
  voidVisible.value = true
}

onMounted(loadDetail)
</script>

<style scoped>
.detail-page {
  display: flex;
  flex-direction: column;
  height: 100%;
  padding: 10px 12px 0;
  box-sizing: border-box;
}

.detail-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  flex: none;
  padding-bottom: 8px;
}

.head-left {
  display: flex;
  align-items: center;
  gap: 10px;
  min-width: 0;
}

.detail-no {
  font-size: 16px;
  font-weight: var(--font-weight-medium);
  color: var(--color-text-primary);
  font-family: var(--font-family-number);
}

.chip {
  padding: 1px 8px;
  border-radius: 10px;
  background: var(--color-bg-subtle);
  color: var(--color-text-secondary);
  font-size: 12px;
}

.head-right {
  display: flex;
  gap: 8px;
  flex: none;
}

.btn-wrap {
  display: inline-flex;
}

.detail-context {
  display: flex;
  flex-wrap: wrap;
  gap: 4px 18px;
  flex: none;
  padding: 6px 10px;
  margin-bottom: 8px;
  border-radius: 4px;
  background: var(--color-bg-subtle);
  color: var(--color-text-secondary);
  font-size: 12px;
}

.detail-body {
  flex: 1;
  min-height: 0;
  display: flex;
  flex-direction: column;
}

.detail-tabs {
  flex: 1;
  min-height: 0;
  display: flex;
  flex-direction: column;
}

.detail-tabs :deep(.el-tabs__content) {
  flex: 1;
  min-height: 0;
  overflow: hidden;
}

.detail-tabs :deep(.el-tab-pane) {
  height: 100%;
}

.tab-scroll {
  height: 100%;
  overflow-y: auto;
  padding-right: 4px;
}

.amend-panel {
  padding-top: 2px;
}

/* 改单信息内联展示：与 luohuo 改单信息 tab 同款（每条变更一行） */
.change-line {
  line-height: 1.9;
  /* 超长内容省略号结尾，鼠标悬浮看全文（title） */
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.change-label {
  color: var(--color-text-secondary);
}

.change-before {
  color: var(--color-text-placeholder);
  text-decoration: line-through;
}

.change-arrow {
  margin: 0 6px;
  color: var(--color-text-secondary);
}

.change-after {
  color: var(--color-primary);
  font-weight: var(--font-weight-semibold);
}
</style>
