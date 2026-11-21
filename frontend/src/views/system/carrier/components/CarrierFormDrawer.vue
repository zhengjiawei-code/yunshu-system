<template>
  <page-drawer v-model="visible" :title="isEdit ? '编辑承运商' : '新增承运商'" :size="560">
    <el-form ref="formRef" v-loading="loading" :model="form" :rules="rules" label-width="96px">
      <el-form-item label="承运商编码" prop="carrierCode">
        <el-input v-model="form.carrierCode" maxlength="32" placeholder="请输入承运商编码" />
      </el-form-item>
      <el-form-item label="承运商名称" prop="carrierName">
        <el-input v-model="form.carrierName" maxlength="64" placeholder="请输入承运商名称" />
      </el-form-item>
      <el-form-item label="联系人" prop="contact">
        <el-input v-model="form.contact" maxlength="32" placeholder="请输入联系人" />
      </el-form-item>
      <el-form-item label="联系电话" prop="phone">
        <el-input v-model="form.phone" maxlength="20" placeholder="请输入联系电话" />
      </el-form-item>
      <el-form-item label="归属机构">
        <el-input :model-value="deptName" readonly placeholder="当前登录机构（自动写入）" />
        <p class="form-hint">归属机构由系统按创建人主机构写入，不可修改</p>
      </el-form-item>
      <el-form-item label="状态" prop="status">
        <el-radio-group v-model="form.status">
          <el-radio :value="true">启用</el-radio>
          <el-radio :value="false">停用</el-radio>
        </el-radio-group>
      </el-form-item>
      <el-form-item label="排序号" prop="sortOrder">
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
import { computed, reactive, ref, watch } from 'vue'
import { ElMessage, type FormInstance, type FormRules } from 'element-plus'
import { useUserStore } from '@/stores/user'
import type { CarrierRecord, CarrierSavePayload } from '@/types/carrier'
import { createCarrier, getCarrierDetail, updateCarrier } from '@/api/system/carrier'
import PageDrawer from '@/components/base/PageDrawer.vue'

const props = defineProps<{
  modelValue: boolean
  mode: 'create' | 'edit'
  record: CarrierRecord | null
}>()

const emit = defineEmits<{
  'update:modelValue': [value: boolean]
  saved: []
}>()

const visible = computed({
  get: () => props.modelValue,
  set: (value: boolean) => emit('update:modelValue', value)
})

const userStore = useUserStore()
const formRef = ref<FormInstance>()
const submitting = ref(false)
const loading = ref(false)
const isEdit = computed(() => props.mode === 'edit')
const deptName = ref('')

const form = reactive<CarrierSavePayload>({
  carrierCode: '',
  carrierName: '',
  contact: '',
  phone: '',
  status: true,
  sortOrder: 0,
  remark: ''
})

const rules: FormRules = {
  carrierCode: [{ required: true, message: '请输入承运商编码', trigger: 'blur' }],
  carrierName: [{ required: true, message: '请输入承运商名称', trigger: 'blur' }],
  contact: [{ required: true, message: '请输入联系人', trigger: 'blur' }],
  phone: [{ required: true, message: '请输入联系电话', trigger: 'blur' }],
  remark: [{ max: 500, message: '备注不能超过 500 个字符', trigger: 'blur' }]
}

function resetForm() {
  form.carrierCode = ''
  form.carrierName = ''
  form.contact = ''
  form.phone = ''
  form.status = true
  form.sortOrder = 0
  form.remark = ''
  deptName.value = userStore.user?.deptName || ''
  formRef.value?.clearValidate()
}

async function fillDetail(id: number) {
  loading.value = true
  try {
    const detail = await getCarrierDetail(id)
    form.carrierCode = detail.carrierCode
    form.carrierName = detail.carrierName
    form.contact = detail.contact
    form.phone = detail.phone
    form.status = detail.status
    form.sortOrder = detail.sortOrder
    form.remark = detail.remark || ''
    deptName.value = detail.deptName || ''
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
  submitting.value = true
  try {
    const payload: CarrierSavePayload = {
      carrierCode: form.carrierCode.trim(),
      carrierName: form.carrierName.trim(),
      contact: form.contact.trim(),
      phone: form.phone.trim(),
      status: form.status,
      sortOrder: form.sortOrder,
      remark: form.remark?.trim() || undefined
    }
    if (isEdit.value && props.record) {
      await updateCarrier(props.record.id, payload)
      ElMessage.success('保存成功')
    } else {
      await createCarrier(payload)
      ElMessage.success('新增成功')
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
.form-hint {
  margin: 4px 0 0;
  font-size: var(--font-size-extra-small);
  color: var(--color-text-placeholder);
  line-height: 1.5;
}
</style>
