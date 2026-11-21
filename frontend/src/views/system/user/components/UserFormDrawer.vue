<template>
  <page-drawer v-model="visible" :title="drawerTitle" :size="680">
    <el-form
      ref="formRef"
      :model="form"
      :rules="rules"
      label-width="96px"
      label-position="right"
      class="user-form"
    >
      <el-tabs v-model="activeTab" class="user-tabs">
        <el-tab-pane label="基础信息" name="basic">
          <el-form-item label="用户名" prop="username">
            <el-input
              v-model="form.username"
              :disabled="isEdit"
              maxlength="20"
              placeholder="2-20 位字母、数字或下划线"
            >
              <template v-if="isEdit" #suffix>
                <el-tooltip content="用户名创建后不可修改" placement="top">
                  <el-icon><Lock /></el-icon>
                </el-tooltip>
              </template>
            </el-input>
          </el-form-item>

          <template v-if="!isEdit">
            <el-form-item label="密码" prop="password">
              <el-input
                v-model="form.password"
                type="password"
                show-password
                maxlength="20"
                placeholder="6-20 位"
                autocomplete="new-password"
              />
            </el-form-item>
            <el-form-item label="确认密码" prop="confirmPassword">
              <el-input
                v-model="form.confirmPassword"
                type="password"
                show-password
                maxlength="20"
                placeholder="请再次输入密码"
                autocomplete="new-password"
              />
            </el-form-item>
          </template>

          <el-form-item label="真实姓名" prop="realName">
            <el-input v-model="form.realName" maxlength="20" placeholder="2-20 个字符" />
          </el-form-item>

          <el-form-item label="手机号" prop="phone">
            <el-input v-model="form.phone" maxlength="11" placeholder="请输入手机号" />
          </el-form-item>

          <el-form-item label="邮箱" prop="email">
            <el-input v-model="form.email" maxlength="100" placeholder="选填" />
          </el-form-item>

          <el-form-item label="用户类型" prop="userType">
            <el-radio-group v-model="form.userType" @change="onUserTypeChange">
              <el-radio :value="1">内部员工</el-radio>
              <el-radio :value="2">客户</el-radio>
            </el-radio-group>
          </el-form-item>

          <el-form-item label="所属机构" prop="deptId">
            <el-tree-select
              v-model="form.deptId"
              :data="deptTree"
              :props="{ label: 'deptName', children: 'children' }"
              node-key="id"
              check-strictly
              clearable
              placeholder="内部员工必选；客户可为空"
              class="full-width"
              @change="onDeptChange"
            />
            <p v-if="isCustomer" class="form-hint">客户账号允许为空，不得为填字段虚构机构</p>
          </el-form-item>

          <el-form-item v-if="!isCustomer" label="可登录机构" prop="authOrgIds">
            <el-tree-select
              v-model="form.authOrgIds"
              :data="deptTree"
              :props="{ label: 'deptName', children: 'children' }"
              node-key="id"
              multiple
              show-checkbox
              check-strictly
              clearable
              collapse-tags
              :max-collapse-tags="3"
              placeholder="默认仅所属机构"
              class="full-width"
            />
            <p class="form-hint">可额外授权其他机构登录；主机构始终包含</p>
          </el-form-item>

          <el-form-item label="角色" prop="roleIds">
            <el-select
              v-model="form.roleIds"
              multiple
              filterable
              :disabled="isSuperAdmin"
              placeholder="请选择角色"
              class="full-width"
            >
              <el-option
                v-for="role in roleOptions"
                :key="role.id"
                :label="`${role.roleName}（${role.roleCode}）`"
                :value="role.id"
              />
            </el-select>
            <p class="form-hint">
              至少选择 1 个角色{{ isSuperAdmin ? '；超级管理员的角色不可修改' : '' }}
            </p>
          </el-form-item>

          <el-form-item label="状态" prop="status">
            <el-radio-group v-model="form.status">
              <el-radio :value="true">启用</el-radio>
              <el-radio :value="false" :disabled="isSuperAdmin">禁用</el-radio>
            </el-radio-group>
            <p v-if="isSuperAdmin" class="form-hint">超级管理员不可禁用</p>
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
        </el-tab-pane>

        <el-tab-pane label="数据授权" name="scope">
          <el-form-item label="数据范围" prop="dataScope">
            <el-select
              v-model="form.dataScope"
              :disabled="isCustomer || isSuperAdmin"
              class="full-width"
            >
              <el-option
                v-for="item in dataScopeOptions"
                :key="item.value"
                :label="item.label"
                :value="item.value"
              />
            </el-select>
            <p v-if="isCustomer" class="form-hint">客户账号不适用数据范围，可见数据由客户域规则决定</p>
            <p v-else class="form-hint">
              默认「本机构及以下」；过滤开关 yunshu.data-scope.enabled 当前关闭，仅存档不生效
            </p>
          </el-form-item>
        </el-tab-pane>
      </el-tabs>
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
import type { DeptNode } from '@/types/dept'
import type { RoleRecord } from '@/types/role'
import type { DataScope, UserRecord, UserSavePayload } from '@/types/user'
import { createUser, getUserDetail, updateUser } from '@/api/system/user'
import { getDeptOptions } from '@/api/system/dept'
import { getRoleOptions } from '@/api/system/role'
import PageDrawer from '@/components/base/PageDrawer.vue'

const props = defineProps<{
  modelValue: boolean
  mode: 'create' | 'edit'
  record: UserRecord | null
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
const activeTab = ref('basic')
const deptTree = ref<DeptNode[]>([])
const roleOptions = ref<RoleRecord[]>([])

const dataScopeOptions: Array<{ label: string; value: DataScope }> = [
  { label: '全部数据', value: 1 },
  { label: '本机构数据', value: 3 },
  { label: '本机构及以下', value: 4 },
  { label: '仅本人数据', value: 5 }
]

const form = reactive<{
  username: string
  password: string
  confirmPassword: string
  realName: string
  phone: string
  email: string
  userType: 1 | 2
  deptId?: number
  authOrgIds: number[]
  roleIds: number[]
  status: boolean
  dataScope: DataScope
  remark: string
}>({
  username: '',
  password: '',
  confirmPassword: '',
  realName: '',
  phone: '',
  email: '',
  userType: 1,
  deptId: undefined,
  authOrgIds: [],
  roleIds: [],
  status: true,
  dataScope: 4,
  remark: ''
})

const isEdit = computed(() => props.mode === 'edit')
const isCustomer = computed(() => form.userType === 2)
const isSuperAdmin = computed(() => isEdit.value && props.record?.id === 1)
const drawerTitle = computed(() => (isEdit.value ? '编辑用户' : '新增用户'))

const rules: FormRules = {
  username: [
    { required: true, message: '请输入用户名', trigger: 'blur' },
    {
      pattern: /^\w{2,20}$/,
      message: '用户名为 2-20 位字母、数字或下划线',
      trigger: 'blur'
    }
  ],
  password: [
    { required: true, message: '请输入密码', trigger: 'blur' },
    { min: 6, max: 20, message: '密码长度为 6-20 位', trigger: 'blur' }
  ],
  confirmPassword: [
    { required: true, message: '请再次输入密码', trigger: 'blur' },
    {
      validator: (_rule: unknown, value: string, callback: (error?: Error) => void) => {
        if (value !== form.password) {
          callback(new Error('两次输入的密码不一致'))
          return
        }
        callback()
      },
      trigger: 'blur'
    }
  ],
  realName: [
    { required: true, message: '请输入真实姓名', trigger: 'blur' },
    { min: 2, max: 20, message: '真实姓名为 2-20 个字符', trigger: 'blur' }
  ],
  phone: [
    { required: true, message: '请输入手机号', trigger: 'blur' },
    { pattern: /^1[3-9]\d{9}$/, message: '手机号格式不正确', trigger: 'blur' }
  ],
  email: [{ type: 'email', message: '邮箱格式不正确', trigger: 'blur' }],
  deptId: [
    {
      validator: (_rule: unknown, value: number | undefined, callback: (error?: Error) => void) => {
        if (form.userType === 1 && !value) {
          callback(new Error('内部员工必须选择所属机构'))
          return
        }
        callback()
      },
      trigger: 'change'
    }
  ],
  roleIds: [
    {
      validator: (_rule: unknown, value: number[], callback: (error?: Error) => void) => {
        if (!value || value.length === 0) {
          callback(new Error('请至少选择 1 个角色'))
          return
        }
        callback()
      },
      trigger: 'change'
    }
  ],
  remark: [{ max: 500, message: '备注不能超过 500 个字符', trigger: 'blur' }]
}

function onUserTypeChange() {
  if (form.userType === 2) {
    form.authOrgIds = []
  }
  formRef.value?.validateField('deptId').catch(() => undefined)
}

function onDeptChange(value?: number) {
  // 可登录机构默认包含主机构
  if (value && !form.authOrgIds.includes(value)) {
    form.authOrgIds = [value, ...form.authOrgIds]
  }
}

async function loadOptions() {
  try {
    const [depts, roles] = await Promise.all([getDeptOptions(), getRoleOptions()])
    deptTree.value = depts
    roleOptions.value = roles
  } catch {
    // 选项失败不阻断填写，后端会再做校验
  }
}

function resetForm() {
  activeTab.value = 'basic'
  form.username = ''
  form.password = ''
  form.confirmPassword = ''
  form.realName = ''
  form.phone = ''
  form.email = ''
  form.userType = 1
  form.deptId = undefined
  form.authOrgIds = []
  form.roleIds = []
  form.status = true
  form.dataScope = 4
  form.remark = ''
  formRef.value?.clearValidate()
}

async function fillDetail(id: number) {
  loading.value = true
  try {
    const detail = await getUserDetail(id)
    form.username = detail.username
    form.realName = detail.realName
    form.phone = detail.phone || ''
    form.email = detail.email || ''
    form.userType = detail.userType
    form.deptId = detail.deptId
    form.authOrgIds = detail.authOrgIds || []
    form.roleIds = detail.roleIds || []
    form.status = detail.status
    form.dataScope = detail.dataScope || 4
    form.remark = detail.remark || ''
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
    await loadOptions()
    if (isEdit.value && props.record) {
      await fillDetail(props.record.id)
    }
  }
)

async function handleSubmit() {
  if (!formRef.value) return
  const valid = await formRef.value.validate().catch(() => false)
  if (!valid) return

  const payload: UserSavePayload = {
    username: form.username.trim(),
    realName: form.realName.trim(),
    phone: form.phone.trim(),
    email: form.email.trim() || undefined,
    userType: form.userType,
    deptId: form.deptId,
    authOrgIds: form.userType === 1 ? form.authOrgIds : [],
    roleIds: form.roleIds,
    status: form.status,
    dataScope: form.userType === 1 ? form.dataScope : undefined,
    remark: form.remark.trim() || undefined
  }
  if (!isEdit.value) {
    payload.password = form.password
  }

  submitting.value = true
  try {
    if (isEdit.value && props.record) {
      await updateUser(props.record.id, payload)
      ElMessage.success('保存成功')
    } else {
      await createUser(payload)
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
.user-form {
  padding-right: 4px;
}

.user-tabs :deep(.el-tabs__content) {
  padding-top: 12px;
}

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
