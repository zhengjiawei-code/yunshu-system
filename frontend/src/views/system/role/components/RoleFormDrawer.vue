<template>
  <page-drawer v-model="visible" :title="drawerTitle" :size="560">
    <el-form
      ref="formRef"
      :model="form"
      :rules="rules"
      label-width="90px"
      label-position="right"
      class="role-form"
    >
      <el-form-item label="角色名称" prop="roleName">
        <el-input v-model="form.roleName" maxlength="20" show-word-limit placeholder="2-20 个字符" />
      </el-form-item>

      <el-form-item label="角色编码" prop="roleCode">
        <el-input
          v-model="form.roleCode"
          :disabled="isEdit"
          maxlength="20"
          placeholder="2-20 位字母、数字或下划线"
        >
          <template v-if="isEdit" #suffix>
            <el-tooltip content="角色编码创建后不可修改" placement="top">
              <el-icon><Lock /></el-icon>
            </el-tooltip>
          </template>
        </el-input>
        <p v-if="isEdit" class="form-hint">编码用于权限判定与对接，创建后不可修改</p>
      </el-form-item>

      <el-form-item label="角色描述" prop="roleDesc">
        <el-input
          v-model="form.roleDesc"
          type="textarea"
          :rows="2"
          maxlength="200"
          show-word-limit
          placeholder="选填，简要说明该角色的职责范围"
        />
      </el-form-item>

      <el-form-item label="状态" prop="status">
        <el-radio-group v-model="form.status">
          <el-radio :value="true">启用</el-radio>
          <el-radio :value="false" :disabled="isAdmin">禁用</el-radio>
        </el-radio-group>
        <p v-if="isAdmin" class="form-hint">超级管理员角色不可禁用</p>
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
import { Lock } from '@element-plus/icons-vue'
import type { RoleRecord, RoleSavePayload } from '@/types/role'
import { createRole, updateRole } from '@/api/system/role'
import PageDrawer from '@/components/base/PageDrawer.vue'

const props = defineProps<{
  modelValue: boolean
  mode: 'create' | 'edit'
  record: RoleRecord | null
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

const form = reactive({
  roleName: '',
  roleCode: '',
  roleDesc: '',
  status: true,
  remark: ''
})

const isEdit = computed(() => props.mode === 'edit')
const isAdmin = computed(() => props.mode === 'edit' && props.record?.roleCode === 'admin')
const drawerTitle = computed(() => (isEdit.value ? '编辑角色' : '新增角色'))

const rules: FormRules = {
  roleName: [
    { required: true, message: '请输入角色名称', trigger: 'blur' },
    { min: 2, max: 20, message: '角色名称为 2-20 个字符', trigger: 'blur' }
  ],
  roleCode: [
    { required: true, message: '请输入角色编码', trigger: 'blur' },
    {
      pattern: /^\w{2,20}$/,
      message: '角色编码为 2-20 位字母、数字或下划线',
      trigger: 'blur'
    }
  ],
  roleDesc: [{ max: 200, message: '角色描述不能超过 200 个字符', trigger: 'blur' }],
  remark: [{ max: 500, message: '备注不能超过 500 个字符', trigger: 'blur' }]
}

function resetForm() {
  const record = props.record
  if (isEdit.value && record) {
    form.roleName = record.roleName
    form.roleCode = record.roleCode
    form.roleDesc = record.roleDesc || ''
    form.status = record.status
    form.remark = record.remark || ''
  } else {
    form.roleName = ''
    form.roleCode = ''
    form.roleDesc = ''
    form.status = true
    form.remark = ''
  }
  formRef.value?.clearValidate()
}

watch(
  () => props.modelValue,
  (open) => {
    if (open) resetForm()
  }
)

async function handleSubmit() {
  if (!formRef.value) return
  const valid = await formRef.value.validate().catch(() => false)
  if (!valid) return

  const payload: RoleSavePayload = {
    roleName: form.roleName.trim(),
    roleCode: form.roleCode.trim(),
    roleDesc: form.roleDesc.trim() || undefined,
    status: form.status,
    remark: form.remark.trim() || undefined
  }

  submitting.value = true
  try {
    if (isEdit.value && props.record) {
      await updateRole(props.record.id, payload)
      ElMessage.success('保存成功')
    } else {
      await createRole(payload)
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
.role-form {
  padding-right: 4px;
}

.form-hint {
  margin: 4px 0 0;
  font-size: var(--font-size-extra-small);
  color: var(--color-text-placeholder);
  line-height: 1.5;
}
</style>
