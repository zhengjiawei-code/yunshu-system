<template>
  <page-drawer v-model="visible" title="编辑路由（直达/外转）" :size="560">
    <el-form ref="formRef" v-loading="loading" :model="form" :rules="rules" label-width="104px">
      <el-form-item label="适用网点">
        <el-input :model-value="record?.applyDeptName" readonly />
        <p class="form-hint">适用网点由顶部“配置网点”决定，编辑不可修改</p>
      </el-form-item>

      <el-form-item label="目的地">
        <el-input :model-value="record?.regionFullName" readonly />
      </el-form-item>

      <el-form-item label="路由编码">
        <el-input :model-value="record?.routeCode" readonly />
        <p class="form-hint">编码由“区划编码_ZT/_WZ”生成，类型切换时自动重写</p>
      </el-form-item>

      <el-form-item label="路由类型">
        <el-radio-group :model-value="record?.transferType" disabled>
          <el-radio :value="1">直达</el-radio>
          <el-radio :value="2">外转</el-radio>
        </el-radio-group>
        <p class="form-hint">类型请在列表行内标签或工具栏“切直达/切外转”切换</p>
      </el-form-item>

      <el-form-item label="路由名称" prop="routeName">
        <el-input v-model="form.routeName" maxlength="128" placeholder="留空默认使用目的地全路径" />
      </el-form-item>

      <el-form-item v-if="record?.transferType === 2" label="承运商" prop="carrierId">
        <el-select
          v-model="form.carrierId"
          filterable
          clearable
          placeholder="外转可选，按管理范围过滤"
          class="full-width"
        >
          <el-option
            v-for="item in carrierOptions"
            :key="item.id"
            :label="`${item.carrierName}（${item.carrierCode}）`"
            :value="item.id"
          />
        </el-select>
      </el-form-item>

      <el-form-item label="状态" prop="status">
        <el-radio-group v-model="form.status">
          <el-radio :value="true">启用</el-radio>
          <el-radio :value="false">停用</el-radio>
        </el-radio-group>
      </el-form-item>

      <el-form-item label="排序" prop="sortOrder">
        <el-input-number v-model="form.sortOrder" :min="0" :max="9999" controls-position="right" />
      </el-form-item>

      <el-form-item label="备注" prop="remark">
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

    <template #footer>
      <el-button @click="visible = false">取消</el-button>
      <el-button type="primary" :loading="submitting" @click="handleSubmit">确定</el-button>
    </template>
  </page-drawer>
</template>

<script setup lang="ts">
import { reactive, ref, watch } from 'vue'
import { ElMessage, type FormInstance, type FormRules } from 'element-plus'
import type { CarrierRecord } from '@/types/carrier'
import type { TransferEditPayload, TransferRecord } from '@/types/route'
import { getCarrierPage } from '@/api/system/carrier'
import { updateTransfer } from '@/api/system/route'
import PageDrawer from '@/components/base/PageDrawer.vue'

defineOptions({ name: 'TransferFormDrawer' })

const props = defineProps<{
  modelValue: boolean
  record: TransferRecord | null
}>()

const emit = defineEmits<{
  (event: 'update:modelValue', value: boolean): void
  (event: 'saved'): void
}>()

const visible = ref(props.modelValue)
watch(
  () => props.modelValue,
  (value) => (visible.value = value)
)
watch(visible, (value) => emit('update:modelValue', value))

const formRef = ref<FormInstance>()
const submitting = ref(false)
const loading = ref(false)
const carrierOptions = ref<CarrierRecord[]>([])

const form = reactive<TransferEditPayload>({
  routeName: '',
  carrierId: null,
  status: true,
  sortOrder: 0,
  remark: ''
})

const rules: FormRules = {
  routeName: [{ max: 128, message: '路由名称不能超过 128 个字符', trigger: 'blur' }],
  sortOrder: [{ type: 'number', min: 0, max: 9999, message: '排序号范围 0-9999', trigger: 'blur' }]
}

watch(
  () => props.record,
  (record) => {
    if (!record) return
    form.routeName = record.routeName ?? ''
    form.carrierId = record.carrierId ?? null
    form.status = record.status
    form.sortOrder = record.sortOrder ?? 0
    form.remark = record.remark ?? ''
  },
  { immediate: true }
)

watch(visible, async (value) => {
  if (!value) return
  await loadCarriers()
})

async function loadCarriers() {
  loading.value = true
  try {
    const result = await getCarrierPage({ pageNum: 1, pageSize: 200, status: true })
    carrierOptions.value = result.records
  } catch (error) {
    const message = (error as { message?: string })?.message
    if (message) ElMessage.error(message)
  } finally {
    loading.value = false
  }
}

async function handleSubmit() {
  if (!props.record) return
  const valid = await formRef.value?.validate().catch(() => false)
  if (!valid) return
  submitting.value = true
  try {
    const payload: TransferEditPayload = {
      routeName: form.routeName?.trim() || undefined,
      carrierId: props.record.transferType === 2 ? form.carrierId ?? null : null,
      status: form.status,
      sortOrder: form.sortOrder,
      remark: form.remark?.trim() || undefined
    }
    await updateTransfer(props.record.id, payload)
    ElMessage.success('保存成功')
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
.full-width {
  width: 100%;
}
</style>
