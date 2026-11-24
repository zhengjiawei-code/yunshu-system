<template>
  <page-drawer v-model="visible" :title="isEdit ? '编辑路由（短驳/干线）' : '新增路由（短驳/干线）'" :size="720">
    <el-form ref="formRef" v-loading="loading" :model="form" :rules="rules" label-width="104px">
      <el-form-item label="路由编码" v-if="isEdit">
        <el-input :model-value="record?.routeCode" readonly />
      </el-form-item>

      <el-form-item label="路由类型" prop="routeCategory">
        <el-radio-group v-model="form.routeCategory">
          <el-radio :value="1">短驳</el-radio>
          <el-radio :value="2">干线</el-radio>
        </el-radio-group>
      </el-form-item>

      <el-form-item label="始发网点" prop="originDeptId">
        <el-tree-select
          v-model="form.originDeptId"
          :data="scopeTree"
          :props="{ label: 'deptName', children: 'children' }"
          node-key="id"
          check-strictly
          filterable
          clearable
          placeholder="请选择始发分拨中心/网点"
          class="full-width"
        />
      </el-form-item>

      <el-form-item label="运达网点" prop="destDeptId">
        <el-tree-select
          v-model="form.destDeptId"
          :data="scopeTree"
          :props="{ label: 'deptName', children: 'children' }"
          node-key="id"
          check-strictly
          filterable
          clearable
          placeholder="请选择运达分拨中心/网点"
          class="full-width"
        />
      </el-form-item>

      <el-form-item label="线路名称" prop="routeName">
        <el-input
          v-model="form.routeName"
          maxlength="100"
          placeholder="干线默认“运达网点名+线路”，可修改"
          @input="nameTouched = true"
        />
        <p class="form-hint">干线在运达网点变更且未手改名称时自动生成；留空保存时由服务端兜底生成</p>
      </el-form-item>

      <el-form-item label="库区" prop="warehouseArea">
        <el-input v-model="form.warehouseArea" maxlength="100" placeholder="打印标签直接取该值" />
      </el-form-item>

      <el-form-item label="里程（km）" prop="mileage">
        <el-input-number
          v-model="form.mileage"
          :max="99999999"
          :precision="2"
          :controls="false"
          placeholder="选填"
          class="full-width"
        />
      </el-form-item>

      <el-form-item label="时效（小时）" prop="transitHours">
        <el-input-number
          v-model="form.transitHours"
          :max="99999999"
          :precision="2"
          :controls="false"
          placeholder="选填"
          class="full-width"
        />
      </el-form-item>

      <el-form-item label="线路联系人" prop="contactName">
        <el-input v-model="form.contactName" maxlength="50" placeholder="选填" />
      </el-form-item>

      <el-form-item label="联系电话" prop="contactPhone">
        <el-input v-model="form.contactPhone" maxlength="20" placeholder="选填" />
      </el-form-item>

      <el-form-item label="线路客服" prop="serviceName">
        <el-input v-model="form.serviceName" maxlength="50" placeholder="选填" />
      </el-form-item>

      <el-form-item label="客服电话" prop="servicePhone">
        <el-input v-model="form.servicePhone" maxlength="20" placeholder="选填" />
      </el-form-item>

      <el-form-item label="状态" prop="status">
        <el-radio-group v-model="form.status">
          <el-radio :value="true">启用</el-radio>
          <el-radio :value="false">停用</el-radio>
        </el-radio-group>
      </el-form-item>

      <el-form-item label="排序号" prop="sortOrder">
        <el-input-number v-model="form.sortOrder" :min="0" :max="999999" controls-position="right" />
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
import { computed, reactive, ref, watch } from 'vue'
import { ElMessage, type FormInstance, type FormRules } from 'element-plus'
import type { ManageScopeNode } from '@/types/dept'
import type { RouteRecord, RouteSavePayload } from '@/types/route'
import { createRoute, getRouteDetail, updateRoute } from '@/api/system/route'
import PageDrawer from '@/components/base/PageDrawer.vue'

const props = defineProps<{
  modelValue: boolean
  mode: 'create' | 'edit'
  record: RouteRecord | null
  scopeTree: ManageScopeNode[]
}>()

const emit = defineEmits<{
  'update:modelValue': [value: boolean]
  saved: []
}>()

const visible = computed({
  get: () => props.modelValue,
  set: (value: boolean) => emit('update:modelValue', value)
})

const formRef = ref<FormInstance>()
const submitting = ref(false)
const loading = ref(false)
const nameTouched = ref(false)
const isEdit = computed(() => props.mode === 'edit')

const form = reactive<{
  routeCategory: 1 | 2
  originDeptId?: number
  destDeptId?: number
  routeName: string
  warehouseArea: string
  mileage?: number
  transitHours?: number
  contactName: string
  contactPhone: string
  serviceName: string
  servicePhone: string
  status: boolean
  sortOrder: number
  remark: string
}>({
  routeCategory: 1,
  originDeptId: undefined,
  destDeptId: undefined,
  routeName: '',
  warehouseArea: '',
  mileage: undefined,
  transitHours: undefined,
  contactName: '',
  contactPhone: '',
  serviceName: '',
  servicePhone: '',
  status: true,
  sortOrder: 0,
  remark: ''
})

/** 选填数值项：留空放行，填了仍校验不能为负 */
function nonNegative(message: string) {
  return (_rule: unknown, value: unknown, callback: (error?: Error) => void) => {
    if (value !== undefined && value !== null && Number(value) < 0) {
      callback(new Error(message))
      return
    }
    callback()
  }
}

const rules: FormRules = {
  routeCategory: [{ required: true, message: '请选择路由类型', trigger: 'change' }],
  originDeptId: [{ required: true, message: '请选择始发网点', trigger: 'change' }],
  destDeptId: [{ required: true, message: '请选择运达网点', trigger: 'change' }],
  mileage: [{ validator: nonNegative('里程不能为负'), trigger: 'change' }],
  transitHours: [{ validator: nonNegative('时效不能为负'), trigger: 'change' }],
  remark: [{ max: 500, message: '备注不能超过 500 个字符', trigger: 'blur' }]
}

function resetForm() {
  form.routeCategory = 1
  form.originDeptId = undefined
  form.destDeptId = undefined
  form.routeName = ''
  form.warehouseArea = ''
  form.mileage = undefined
  form.transitHours = undefined
  form.contactName = ''
  form.contactPhone = ''
  form.serviceName = ''
  form.servicePhone = ''
  form.status = true
  form.sortOrder = 0
  form.remark = ''
  nameTouched.value = false
  formRef.value?.clearValidate()
}

function findNode(nodes: ManageScopeNode[], id?: number): ManageScopeNode | undefined {
  if (id === undefined || id === null) return undefined
  for (const node of nodes) {
    if (node.id === id) return node
    const found = findNode(node.children || [], id)
    if (found) return found
  }
  return undefined
}

/** 干线自动名称：运达网点名+线路；用户手改后不再覆盖 */
function applyAutoName() {
  if (form.routeCategory !== 2 || nameTouched.value) return
  const dest = findNode(props.scopeTree, form.destDeptId)
  if (dest) form.routeName = `${dest.deptName}线路`
}

watch(
  () => form.routeCategory,
  () => {
    applyAutoName()
    formRef.value?.clearValidate()
  }
)

watch(
  () => form.destDeptId,
  () => applyAutoName()
)

async function fillDetail(id: number) {
  loading.value = true
  try {
    const detail = await getRouteDetail(id)
    form.routeCategory = detail.routeCategory
    form.originDeptId = detail.originDeptId
    form.destDeptId = detail.destDeptId
    form.routeName = detail.routeName
    form.warehouseArea = detail.warehouseArea || ''
    form.mileage = detail.mileage ?? undefined
    form.transitHours = detail.transitHours ?? undefined
    form.contactName = detail.contactName || ''
    form.contactPhone = detail.contactPhone || ''
    form.serviceName = detail.serviceName || ''
    form.servicePhone = detail.servicePhone || ''
    form.status = detail.status
    form.sortOrder = detail.sortOrder
    form.remark = detail.remark || ''
    nameTouched.value = true
  } catch (error) {
    const message = (error as { message?: string })?.message
    if (message) ElMessage.error(message)
  } finally {
    loading.value = false
  }
}

watch(
  () => props.modelValue,
  async (open) => {
    if (!open) return
    resetForm()
    if (isEdit.value && props.record) {
      await fillDetail(props.record.id)
    }
  }
)

async function handleSubmit() {
  if (!formRef.value) return
  const valid = await formRef.value.validate().catch(() => false)
  if (!valid) return
  if (!form.originDeptId || !form.destDeptId) return
  if (form.originDeptId === form.destDeptId) {
    ElMessage.error('始发网点与运达网点不能相同')
    return
  }
  submitting.value = true
  try {
    const payload: RouteSavePayload = {
      routeCategory: form.routeCategory,
      originDeptId: form.originDeptId,
      destDeptId: form.destDeptId,
      routeName: form.routeName.trim() || undefined,
      warehouseArea: form.warehouseArea.trim() || undefined,
      mileage: form.mileage,
      transitHours: form.transitHours,
      contactName: form.contactName.trim() || undefined,
      contactPhone: form.contactPhone.trim() || undefined,
      serviceName: form.serviceName.trim() || undefined,
      servicePhone: form.servicePhone.trim() || undefined,
      status: form.status,
      sortOrder: form.sortOrder,
      remark: form.remark.trim() || undefined
    }
    if (isEdit.value && props.record) {
      await updateRoute(props.record.id, payload)
      ElMessage.success('保存成功')
    } else {
      const result = await createRoute(payload)
      ElMessage({ type: result.reused ? 'warning' : 'success', message: result.message })
    }
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

.form-hint {
  margin: 4px 0 0;
  font-size: var(--font-size-extra-small);
  color: var(--color-text-placeholder);
  line-height: 1.5;
}
</style>
