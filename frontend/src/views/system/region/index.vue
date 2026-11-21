<template>
  <div class="page-container">
    <div class="page-header">
      <div>
        <h2 class="page-header__title">区划管理</h2>
        <p class="page-header__desc">
          维护省/市/区县三级行政区划基础数据，供机构地址与路由区域引用；导入按编码幂等，可重复执行
        </p>
      </div>
      <div class="page-header__actions">
        <el-button :icon="Refresh" @click="loadAll">刷新</el-button>
        <el-button type="primary" :icon="Upload" @click="openImport">导入区划</el-button>
        <el-tooltip
          :content="stats?.onlineSyncEnabled ? '从官方源在线拉取区划数据' : '在线同步已关闭，请使用离线 JSON 导入'"
          placement="top"
        >
          <span class="btn-wrap">
            <el-button
              :disabled="!stats?.onlineSyncEnabled"
              :loading="syncing"
              :icon="Cloudy"
              @click="handleSyncOnline"
            >
              在线更新
            </el-button>
          </span>
        </el-tooltip>
      </div>
    </div>

    <div class="page-content">
      <el-alert
        v-if="stats && !stats.onlineSyncEnabled"
        class="region-offline-tip"
        type="info"
        :closable="false"
        show-icon
        title="在线同步已关闭"
        description="当前环境未开启外网拉取，请使用本地 JSON 文件导入；导入按 code 幂等，重复导入不会产生重复数据。"
      />

      <div v-loading="statsLoading" class="region-stats">
        <div class="stat-card">
          <span class="stat-card__label">区划总数</span>
          <span class="stat-card__value">{{ stats?.total ?? '—' }}</span>
        </div>
        <div class="stat-card">
          <span class="stat-card__label">省级</span>
          <span class="stat-card__value">{{ stats?.provinceCount ?? '—' }}</span>
        </div>
        <div class="stat-card">
          <span class="stat-card__label">市级</span>
          <span class="stat-card__value">{{ stats?.cityCount ?? '—' }}</span>
        </div>
        <div class="stat-card">
          <span class="stat-card__label">区县级</span>
          <span class="stat-card__value">{{ stats?.districtCount ?? '—' }}</span>
        </div>
        <div class="stat-card">
          <span class="stat-card__label">数据版本</span>
          <span class="stat-card__value stat-card__value--text">{{ stats?.version || '—' }}</span>
        </div>
        <div class="stat-card">
          <span class="stat-card__label">最近同步时间</span>
          <span class="stat-card__value stat-card__value--text">
            {{ stats?.lastSyncTime || '—' }}
          </span>
        </div>
      </div>

      <div class="region-log-head">
        <span class="region-log-head__title">同步日志</span>
        <span class="region-log-head__count">共 {{ logTotal }} 条</span>
      </div>

      <div class="region-table-wrap">
        <el-table
          v-loading="logLoading"
          :data="logs"
          row-key="id"
          height="100%"
          empty-text="暂无同步记录"
          show-overflow-tooltip
          class="region-table"
        >
          <el-table-column prop="startedAt" label="开始时间" min-width="170" />
          <el-table-column label="触发方式" min-width="100" align="center">
            <template #default="{ row }">{{ triggerText(row.triggerType) }}</template>
          </el-table-column>
          <el-table-column label="操作人" min-width="110">
            <template #default="{ row }">{{ row.triggeredByName || '—' }}</template>
          </el-table-column>
          <el-table-column label="文件名" min-width="180">
            <template #default="{ row }">{{ row.fileName || '—' }}</template>
          </el-table-column>
          <el-table-column label="版本" min-width="100" align="center">
            <template #default="{ row }">{{ row.dataVersion || '—' }}</template>
          </el-table-column>
          <el-table-column label="状态" min-width="100" align="center">
            <template #default="{ row }">
              <status-tag :type="logStatus(row.status).type" :text="logStatus(row.status).text" />
            </template>
          </el-table-column>
          <el-table-column label="进度" min-width="140">
            <template #default="{ row }">
              <el-progress
                :percentage="row.progress ?? 0"
                :stroke-width="6"
                :status="row.status === 'partial' ? 'warning' : ''"
              />
            </template>
          </el-table-column>
          <el-table-column prop="totalCount" label="总数" min-width="80" align="right" />
          <el-table-column prop="addedCount" label="新增" min-width="80" align="right" />
          <el-table-column prop="changedCount" label="修改" min-width="80" align="right" />
          <el-table-column prop="unchangedCount" label="无变化" min-width="90" align="right" />
          <el-table-column prop="disabledCount" label="待停用" min-width="90" align="right" />
          <el-table-column label="操作" width="100" fixed="right" align="right">
            <template #default="{ row }">
              <el-button link type="primary" size="small" @click="openLogDetail(row)">
                差异详情
              </el-button>
            </template>
          </el-table-column>
        </el-table>
      </div>

      <div class="region-log-pager">
        <el-pagination
          v-model:current-page="logPage"
          v-model:page-size="logPageSize"
          size="small"
          background
          :total="logTotal"
          :page-sizes="[10, 20, 50]"
          layout="total, sizes, prev, pager, next"
          @current-change="loadLogs"
          @size-change="handleLogSizeChange"
        />
      </div>
    </div>

    <!-- 导入：选文件 → 预检 → 差异确认 → 执行 -->
    <el-dialog
      v-model="importVisible"
      title="导入区划数据"
      width="760px"
      :close-on-click-modal="false"
      :close-on-press-escape="!importing"
      :show-close="!importing"
      @closed="resetImport"
    >
      <el-alert
        class="import-tip"
        type="info"
        :closable="false"
        show-icon
        title="支持三级扁平 JSON 数组或 pca 嵌套 JSON；按 code 幂等 UPSERT，不重复插入"
        description="文件内缺少的编码只会进入「待停用」报告，不会自动停用已有数据。"
      />

      <el-form label-width="90px" class="import-form">
        <el-form-item label="数据文件">
          <el-upload
            ref="uploadRef"
            drag
            accept=".json,application/json"
            :auto-upload="false"
            :limit="1"
            :disabled="importing"
            :file-list="fileList"
            :on-change="handleFileChange"
            :on-remove="handleFileRemove"
            :on-exceed="handleFileExceed"
            class="import-upload"
          >
            <el-icon class="el-icon--upload"><UploadFilled /></el-icon>
            <div class="el-upload__text">将 JSON 文件拖到此处，或<em>点击选择</em></div>
            <template #tip>
              <div class="el-upload__tip">仅支持 .json，单文件不超过 20MB</div>
            </template>
          </el-upload>
        </el-form-item>
        <el-form-item label="数据版本">
          <el-input
            v-model="importVersion"
            maxlength="12"
            clearable
            placeholder="选填，如 2025Q4"
            :disabled="importing"
            class="version-input"
          />
        </el-form-item>
      </el-form>

      <div v-if="importing || importProgress > 0" class="import-progress">
        <el-progress
          :percentage="importProgress"
          :status="importProgress === 100 ? 'success' : ''"
          :stroke-width="10"
        />
        <p class="import-progress__text">{{ importing ? '正在导入，请勿关闭窗口…' : '导入完成' }}</p>
        <p v-if="importedResult" class="import-progress__text">
          已写入同步日志 #{{ importedResult.logId }}，关闭窗口后可在下方日志查看差异详情。
        </p>
      </div>

      <template v-if="checkResult">
        <div class="check-summary">
          <div class="check-summary__item">
            <span class="check-summary__label">文件条数</span>
            <span class="check-summary__value">{{ checkResult.total }}</span>
          </div>
          <div class="check-summary__item">
            <span class="check-summary__label">新增</span>
            <span class="check-summary__value">{{ checkResult.added }}</span>
          </div>
          <div class="check-summary__item">
            <span class="check-summary__label">修改</span>
            <span class="check-summary__value">{{ checkResult.changed }}</span>
          </div>
          <div class="check-summary__item">
            <span class="check-summary__label">无变化</span>
            <span class="check-summary__value">{{ checkResult.unchanged }}</span>
          </div>
          <div class="check-summary__item">
            <span class="check-summary__label">待停用</span>
            <span class="check-summary__value">{{ checkResult.disabled }}</span>
          </div>
          <div class="check-summary__item" :class="{ 'is-error': checkResult.invalid > 0 }">
            <span class="check-summary__label">校验失败</span>
            <span class="check-summary__value">{{ checkResult.invalid }}</span>
          </div>
        </div>

        <el-alert
          v-if="checkResult.invalid > 0"
          class="check-alert"
          type="warning"
          :closable="false"
          show-icon
          title="文件校验未通过，请修正后重新预检，无法直接导入"
        />

        <el-tabs v-if="hasDiff" class="diff-tabs" model-value="added">
          <el-tab-pane :label="`新增 ${checkResult.added}`" name="added">
            <div class="diff-list">
              <status-tag
                v-for="item in checkResult.addedList"
                :key="item.code"
                type="primary"
                :text="`${item.fullName}（${item.code}）`"
                class="diff-tag"
              />
              <span v-if="!checkResult.addedList.length" class="diff-empty">无</span>
            </div>
          </el-tab-pane>
          <el-tab-pane :label="`修改 ${checkResult.changed}`" name="changed">
            <div class="diff-list">
              <status-tag
                v-for="item in checkResult.changedList"
                :key="item.code"
                type="warning"
                :text="`${item.fullName}（${item.code}）`"
                class="diff-tag"
              />
              <span v-if="!checkResult.changedList.length" class="diff-empty">无</span>
            </div>
          </el-tab-pane>
          <el-tab-pane :label="`待停用 ${checkResult.disabled}`" name="disabled">
            <div class="diff-list">
              <status-tag
                v-for="item in checkResult.disabledList"
                :key="item.code"
                type="info"
                :text="`${item.fullName}（${item.code}）`"
                class="diff-tag"
              />
              <span v-if="!checkResult.disabledList.length" class="diff-empty">无</span>
            </div>
          </el-tab-pane>
          <el-tab-pane v-if="checkResult.errors.length" :label="`错误 ${checkResult.invalid}`" name="errors">
            <div class="diff-list">
              <status-tag
                v-for="(item, index) in checkResult.errors"
                :key="index"
                type="info"
                :text="item"
                class="diff-tag"
              />
            </div>
          </el-tab-pane>
        </el-tabs>
        <p v-if="checkResult.truncated" class="diff-truncated">
          差异明细较多，仅展示前 50 条；导入结果以统计数字为准。
        </p>
      </template>

      <template #footer>
        <el-button :disabled="importing" @click="importVisible = false">取消</el-button>
        <el-button :loading="checking" :disabled="!selectedFile || importing" @click="handlePrecheck">
          预检
        </el-button>
        <el-button
          type="primary"
          :loading="importing"
          :disabled="!canImport"
          @click="handleImport"
        >
          开始导入
        </el-button>
      </template>
    </el-dialog>

    <!-- 日志差异详情 -->
    <page-drawer v-model="logDetailVisible" title="同步日志详情" :size="720">
      <div v-if="logDetail" class="log-detail">
        <div class="log-detail__meta">
          <div><span>任务号</span>{{ logDetail.jobId }}</div>
          <div><span>触发方式</span>{{ triggerText(logDetail.triggerType) }}</div>
          <div><span>文件名</span>{{ logDetail.fileName || '—' }}</div>
          <div><span>数据版本</span>{{ logDetail.dataVersion || '—' }}</div>
          <div><span>开始时间</span>{{ logDetail.startedAt || '—' }}</div>
          <div><span>结束时间</span>{{ logDetail.finishedAt || '—' }}</div>
          <div><span>状态</span>{{ logStatus(logDetail.status).text }}</div>
          <div><span>进度</span>{{ logDetail.progress ?? 0 }}%</div>
          <div><span>总数 / 新增 / 修改 / 无变化 / 待停用</span>
            {{ logDetail.totalCount }} / {{ logDetail.addedCount }} / {{ logDetail.changedCount }} /
            {{ logDetail.unchangedCount }} / {{ logDetail.disabledCount }}
          </div>
          <div v-if="logDetail.errorMsg"><span>错误信息</span>{{ logDetail.errorMsg }}</div>
        </div>

        <template v-if="logDetail.report">
          <div class="log-detail__section">
            <p class="log-detail__title">新增 {{ logDetail.report.added?.length || 0 }}</p>
            <div class="diff-list">
              <status-tag
                v-for="item in logDetail.report.added || []"
                :key="`a-${item.code}`"
                type="primary"
                :text="`${item.fullName}（${item.code}）`"
                class="diff-tag"
              />
              <span v-if="!(logDetail.report.added || []).length" class="diff-empty">无</span>
            </div>
          </div>
          <div class="log-detail__section">
            <p class="log-detail__title">修改 {{ logDetail.report.changed?.length || 0 }}</p>
            <div class="diff-list">
              <status-tag
                v-for="item in logDetail.report.changed || []"
                :key="`c-${item.code}`"
                type="warning"
                :text="`${item.fullName}（${item.code}）`"
                class="diff-tag"
              />
              <span v-if="!(logDetail.report.changed || []).length" class="diff-empty">无</span>
            </div>
          </div>
          <div class="log-detail__section">
            <p class="log-detail__title">待停用 {{ logDetail.report.disabled?.length || 0 }}</p>
            <div class="diff-list">
              <status-tag
                v-for="item in logDetail.report.disabled || []"
                :key="`d-${item.code}`"
                type="info"
                :text="`${item.fullName}（${item.code}）`"
                class="diff-tag"
              />
              <span v-if="!(logDetail.report.disabled || []).length" class="diff-empty">无</span>
            </div>
          </div>
          <div v-if="(logDetail.report.errors || []).length" class="log-detail__section">
            <p class="log-detail__title">校验失败 {{ logDetail.report.errors?.length }}</p>
            <div class="diff-list">
              <status-tag
                v-for="(item, index) in logDetail.report.errors"
                :key="`e-${index}`"
                type="info"
                :text="item"
                class="diff-tag"
              />
            </div>
          </div>
          <p v-if="logDetail.report.truncated" class="diff-truncated">
            差异明细较多，仅保留前 50 条。
          </p>
        </template>
      </div>
      <template #footer>
        <el-button @click="logDetailVisible = false">关闭</el-button>
      </template>
    </page-drawer>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { ElMessage, type UploadFile, type UploadInstance, type UploadRawFile, type UploadUserFile } from 'element-plus'
import { Cloudy, Refresh, Upload, UploadFilled } from '@element-plus/icons-vue'
import {
  getRegionStats,
  getRegionSyncLogDetail,
  getRegionSyncLogs,
  importRegionFile,
  precheckRegionFile,
  syncRegionOnline
} from '@/api/system/region'
import type {
  RegionImportCheck,
  RegionLogDetail,
  RegionStats,
  RegionSyncLog
} from '@/types/region'
import StatusTag from '@/components/base/StatusTag.vue'
import PageDrawer from '@/components/base/PageDrawer.vue'

defineOptions({ name: 'SystemRegion' })

const MAX_FILE_SIZE = 20 * 1024 * 1024

const stats = ref<RegionStats>()
const statsLoading = ref(false)
const syncing = ref(false)

const logs = ref<RegionSyncLog[]>([])
const logLoading = ref(false)
const logTotal = ref(0)
const logPage = ref(1)
const logPageSize = ref(20)

const importVisible = ref(false)
const importing = ref(false)
const importProgress = ref(0)
const checking = ref(false)
const selectedFile = ref<File>()
const fileList = ref<UploadUserFile[]>([])
const uploadRef = ref<UploadInstance>()
const importVersion = ref('')
const checkResult = ref<RegionImportCheck>()
const importedResult = ref<{ logId: number; status: string }>()

const logDetailVisible = ref(false)
const logDetail = ref<RegionLogDetail>()

const hasDiff = computed(() => {
  const result = checkResult.value
  if (!result) return false
  return result.added > 0 || result.changed > 0 || result.disabled > 0 || result.errors.length > 0
})

const canImport = computed(
  () => !!selectedFile.value && !!checkResult.value && checkResult.value.invalid === 0 && !importing.value
)

function triggerText(type: string): string {
  if (type === 'offline') return '离线导入'
  if (type === 'online') return '在线同步'
  if (type === 'init') return '初始化'
  return type || '—'
}

/** 状态色遵循“禁用亮红亮绿”：成功用主色，部分成功用橙色，失败用中性灰 */
function logStatus(status: string): { type: 'primary' | 'warning' | 'info'; text: string } {
  if (status === 'success') return { type: 'primary', text: '成功' }
  if (status === 'partial') return { type: 'warning', text: '部分成功' }
  if (status === 'running') return { type: 'info', text: '执行中' }
  return { type: 'info', text: '失败' }
}

async function loadStats() {
  statsLoading.value = true
  try {
    stats.value = await getRegionStats()
  } catch (error) {
    const message = (error as { message?: string })?.message
    if (message) ElMessage.error(message)
  } finally {
    statsLoading.value = false
  }
}

async function loadLogs() {
  logLoading.value = true
  try {
    const result = await getRegionSyncLogs(logPage.value, logPageSize.value)
    logs.value = result.records
    logTotal.value = result.total
  } catch (error) {
    const message = (error as { message?: string })?.message
    if (message) ElMessage.error(message)
  } finally {
    logLoading.value = false
  }
}

function handleLogSizeChange() {
  logPage.value = 1
  loadLogs()
}

async function loadAll() {
  await Promise.all([loadStats(), loadLogs()])
}

function openImport() {
  importVisible.value = true
}

function resetImport() {
  selectedFile.value = undefined
  fileList.value = []
  importVersion.value = ''
  checkResult.value = undefined
  importedResult.value = undefined
  importProgress.value = 0
  importing.value = false
  checking.value = false
  uploadRef.value?.clearFiles()
}

function handleFileChange(file: UploadFile) {
  const raw = file.raw
  if (!raw) return
  if (!raw.name.toLowerCase().endsWith('.json')) {
    ElMessage.warning('仅支持 .json 文件')
    uploadRef.value?.clearFiles()
    return
  }
  if (raw.size > MAX_FILE_SIZE) {
    ElMessage.warning('文件不能超过 20MB')
    uploadRef.value?.clearFiles()
    return
  }
  selectedFile.value = raw
  fileList.value = [{ name: raw.name, raw } as UploadUserFile]
  checkResult.value = undefined
  importedResult.value = undefined
  importProgress.value = 0
}

function handleFileRemove() {
  selectedFile.value = undefined
  checkResult.value = undefined
  importedResult.value = undefined
  importProgress.value = 0
}

function handleFileExceed(files: File[]) {
  uploadRef.value?.clearFiles()
  const file = files[0] as UploadRawFile | undefined
  if (!file) return
  uploadRef.value?.handleStart(file)
  selectedFile.value = file
  fileList.value = [{ name: file.name, raw: file } as UploadUserFile]
  checkResult.value = undefined
  importedResult.value = undefined
}

async function handlePrecheck() {
  if (!selectedFile.value) return
  checking.value = true
  checkResult.value = undefined
  try {
    checkResult.value = await precheckRegionFile(selectedFile.value, importVersion.value || undefined)
    if (checkResult.value.invalid > 0) {
      ElMessage.warning(`预检发现 ${checkResult.value.invalid} 条校验错误`)
    } else {
      ElMessage.success('预检完成')
    }
  } catch (error) {
    const message = (error as { message?: string })?.message
    if (message) ElMessage.error(message)
  } finally {
    checking.value = false
  }
}

async function handleImport() {
  if (!selectedFile.value || !canImport.value) return
  importing.value = true
  importProgress.value = 5
  const timer = setInterval(() => {
    if (importProgress.value < 90) importProgress.value += 5
  }, 400)
  try {
    const result = await importRegionFile(selectedFile.value, importVersion.value || undefined)
    importProgress.value = 100
    importedResult.value = { logId: result.logId, status: result.status }
    ElMessage.success(
      `导入完成：新增 ${result.added}、修改 ${result.changed}、无变化 ${result.unchanged}`
    )
    await loadAll()
  } catch (error) {
    importProgress.value = 0
    const message = (error as { message?: string })?.message
    if (message) ElMessage.error(message)
    await loadLogs()
  } finally {
    clearInterval(timer)
    importing.value = false
  }
}

async function handleSyncOnline() {
  if (!stats.value?.onlineSyncEnabled) {
    ElMessage.warning('在线同步已关闭，请使用离线 JSON 导入')
    return
  }
  syncing.value = true
  try {
    const result = await syncRegionOnline()
    ElMessage.success(`在线同步完成：新增 ${result.added}、修改 ${result.changed}`)
    await loadAll()
  } catch (error) {
    const message = (error as { message?: string })?.message
    if (message) ElMessage.error(message)
  } finally {
    syncing.value = false
  }
}

async function openLogDetail(row: RegionSyncLog) {
  logDetail.value = undefined
  logDetailVisible.value = true
  try {
    logDetail.value = await getRegionSyncLogDetail(row.id)
  } catch (error) {
    const message = (error as { message?: string })?.message
    if (message) ElMessage.error(message)
  }
}

onMounted(loadAll)
</script>

<style scoped>
.page-header__actions {
  display: flex;
  align-items: center;
  gap: var(--spacing-sm);
}

.btn-wrap {
  display: inline-flex;
}

.region-offline-tip {
  margin-bottom: var(--spacing-sm);
}

.region-stats {
  display: grid;
  grid-template-columns: repeat(6, minmax(0, 1fr));
  gap: var(--spacing-sm);
  margin-bottom: var(--spacing-base);
}

.stat-card {
  display: flex;
  flex-direction: column;
  gap: 6px;
  padding: 12px 14px;
  border: 1px solid var(--color-border-light);
  border-radius: var(--radius-sm);
  background-color: var(--color-bg-subtle);
}

.stat-card__label {
  font-size: var(--font-size-extra-small);
  color: var(--color-text-secondary);
}

.stat-card__value {
  font-family: var(--font-family-number);
  font-size: 20px;
  font-weight: var(--font-weight-semibold);
  color: var(--color-text-primary);
}

.stat-card__value--text {
  font-family: inherit;
  font-size: var(--font-size-small);
  font-weight: var(--font-weight-medium);
}

.region-log-head {
  display: flex;
  align-items: baseline;
  gap: var(--spacing-sm);
  margin-bottom: var(--spacing-sm);
}

.region-log-head__title {
  font-size: var(--font-size-base);
  font-weight: var(--font-weight-semibold);
  color: var(--color-text-primary);
}

.region-log-head__count {
  font-size: var(--font-size-extra-small);
  color: var(--color-text-placeholder);
}

.region-table-wrap {
  flex: 1;
  min-height: 0;
}

.region-log-pager {
  display: flex;
  justify-content: flex-end;
  margin-top: var(--spacing-sm);
}

.region-table :deep(.el-table__cell .cell) {
  line-height: 1.4;
}

.import-tip {
  margin-bottom: var(--spacing-base);
}

.import-form {
  margin-bottom: var(--spacing-sm);
}

.import-upload {
  width: 100%;
}

.version-input {
  width: 220px;
}

.import-progress {
  margin: var(--spacing-sm) 0 var(--spacing-base);
}

.import-progress__text {
  margin-top: 6px;
  font-size: var(--font-size-extra-small);
  color: var(--color-text-secondary);
}

.check-summary {
  display: grid;
  grid-template-columns: repeat(6, minmax(0, 1fr));
  gap: var(--spacing-sm);
  margin-bottom: var(--spacing-sm);
}

.check-summary__item {
  display: flex;
  flex-direction: column;
  gap: 4px;
  padding: 10px 12px;
  border: 1px solid var(--color-border-light);
  border-radius: var(--radius-sm);
  background-color: var(--color-bg-subtle);
}

.check-summary__item.is-error {
  border-color: var(--color-warning);
}

.check-summary__label {
  font-size: var(--font-size-extra-small);
  color: var(--color-text-secondary);
}

.check-summary__value {
  font-family: var(--font-family-number);
  font-size: var(--font-size-base);
  font-weight: var(--font-weight-semibold);
  color: var(--color-text-primary);
}

.check-alert {
  margin-bottom: var(--spacing-sm);
}

.diff-tabs {
  margin-top: var(--spacing-sm);
}

.diff-list {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  max-height: 220px;
  overflow: auto;
  padding: 4px 0;
}

.diff-tag {
  max-width: 100%;
}

.diff-empty {
  font-size: var(--font-size-extra-small);
  color: var(--color-text-placeholder);
}

.diff-truncated {
  margin-top: var(--spacing-sm);
  font-size: var(--font-size-extra-small);
  color: var(--color-text-secondary);
}

.log-detail__meta {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 8px 16px;
  padding: 12px 14px;
  margin-bottom: var(--spacing-base);
  border: 1px solid var(--color-border-light);
  border-radius: var(--radius-sm);
  background-color: var(--color-bg-subtle);
  font-size: var(--font-size-small);
  color: var(--color-text-primary);
}

.log-detail__meta span {
  display: inline-block;
  min-width: 110px;
  color: var(--color-text-secondary);
}

.log-detail__section {
  margin-bottom: var(--spacing-base);
}

.log-detail__title {
  margin-bottom: 6px;
  font-size: var(--font-size-small);
  font-weight: var(--font-weight-medium);
  color: var(--color-text-primary);
}
</style>
