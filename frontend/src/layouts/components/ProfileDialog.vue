<template>
  <el-dialog
    :model-value="modelValue"
    title="个人中心"
    width="520px"
    append-to-body
    destroy-on-close
    @update:model-value="(value: boolean) => emit('update:modelValue', value)"
    @open="handleOpen"
  >
    <el-tabs v-model="activeTab" class="profile-tabs">
      <el-tab-pane label="基本资料" name="profile">
        <el-form ref="profileFormRef" :model="profileForm" :rules="profileRules" label-width="88px">
          <el-form-item label="登录账号">
            <el-input :model-value="profile.username" disabled />
          </el-form-item>
          <el-form-item label="所属机构">
            <el-input :model-value="profile.deptName || '—'" disabled />
          </el-form-item>
          <el-form-item label="角色">
            <el-input :model-value="profile.roles.join('、') || '—'" disabled />
          </el-form-item>
          <el-form-item label="真实姓名" prop="realName">
            <el-input v-model="profileForm.realName" placeholder="请输入真实姓名" maxlength="20" />
          </el-form-item>
          <el-form-item label="手机号" prop="phone">
            <el-input v-model="profileForm.phone" placeholder="请输入手机号" maxlength="11" />
          </el-form-item>
          <el-form-item label="邮箱" prop="email">
            <el-input v-model="profileForm.email" placeholder="请输入邮箱" maxlength="64" />
          </el-form-item>
        </el-form>
      </el-tab-pane>

      <el-tab-pane label="修改密码" name="password">
        <el-form ref="passwordFormRef" :model="passwordForm" :rules="passwordRules" label-width="88px">
          <el-form-item label="原密码" prop="oldPassword">
            <el-input v-model="passwordForm.oldPassword" type="password" show-password placeholder="请输入原密码" />
          </el-form-item>
          <el-form-item label="新密码" prop="newPassword">
            <el-input v-model="passwordForm.newPassword" type="password" show-password placeholder="6-20 位字符" />
          </el-form-item>
          <el-form-item label="确认新密码" prop="confirmPassword">
            <el-input v-model="passwordForm.confirmPassword" type="password" show-password placeholder="请再次输入新密码" />
          </el-form-item>
          <div class="profile-tip">修改密码成功后需重新登录。</div>
        </el-form>
      </el-tab-pane>
    </el-tabs>

    <template #footer>
      <el-button @click="emit('update:modelValue', false)">取消</el-button>
      <el-button v-if="activeTab === 'profile'" type="primary" :loading="saving" @click="handleSaveProfile">
        保存修改
      </el-button>
      <el-button v-else type="primary" :loading="saving" @click="handleSavePassword">
        修改密码
      </el-button>
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
import { reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, type FormInstance, type FormRules } from 'element-plus'
import { getProfile, updatePassword, updateProfile, type ProfileInfo } from '@/api/auth'
import { useUserStore } from '@/stores/user'
import { useTagsStore } from '@/stores/tags'

defineProps<{ modelValue: boolean }>()
const emit = defineEmits<{ (e: 'update:modelValue', value: boolean): void }>()

const router = useRouter()
const userStore = useUserStore()
const tagsStore = useTagsStore()

const activeTab = ref<'profile' | 'password'>('profile')
const saving = ref(false)
const profileFormRef = ref<FormInstance>()
const passwordFormRef = ref<FormInstance>()

const profile = reactive<ProfileInfo>({
  id: 0,
  username: '',
  realName: '',
  phone: '',
  email: '',
  deptId: null,
  deptName: '',
  userType: 1,
  roles: []
})

const profileForm = reactive({ realName: '', phone: '', email: '' })

const passwordForm = reactive({ oldPassword: '', newPassword: '', confirmPassword: '' })

const profileRules: FormRules = {
  realName: [
    { required: true, message: '请输入真实姓名', trigger: 'blur' },
    { min: 2, max: 20, message: '真实姓名长度需为 2-20 个字符', trigger: 'blur' }
  ],
  phone: [{ pattern: /^1\d{10}$/, message: '手机号格式不正确', trigger: 'blur' }],
  email: [{ type: 'email', message: '邮箱格式不正确', trigger: 'blur' }]
}

const passwordRules: FormRules = {
  oldPassword: [{ required: true, message: '请输入原密码', trigger: 'blur' }],
  newPassword: [
    { required: true, message: '请输入新密码', trigger: 'blur' },
    { min: 6, max: 20, message: '新密码长度需为 6-20 个字符', trigger: 'blur' }
  ],
  confirmPassword: [
    { required: true, message: '请再次输入新密码', trigger: 'blur' },
    {
      validator: (_rule, value, callback) => {
        if (value !== passwordForm.newPassword) callback(new Error('两次输入的新密码不一致'))
        else callback()
      },
      trigger: 'blur'
    }
  ]
}

async function handleOpen() {
  activeTab.value = 'profile'
  passwordForm.oldPassword = ''
  passwordForm.newPassword = ''
  passwordForm.confirmPassword = ''
  const data = await getProfile()
  Object.assign(profile, data)
  profileForm.realName = data.realName || ''
  profileForm.phone = data.phone || ''
  profileForm.email = data.email || ''
  profileFormRef.value?.clearValidate()
}

async function handleSaveProfile() {
  if (!profileFormRef.value) return
  const valid = await profileFormRef.value.validate().catch(() => false)
  if (!valid) return
  saving.value = true
  try {
    await updateProfile({
      realName: profileForm.realName.trim(),
      phone: profileForm.phone.trim() || undefined,
      email: profileForm.email.trim() || undefined
    })
    await userStore.fetchAuthInfo()
    ElMessage.success('资料修改成功')
    emit('update:modelValue', false)
  } finally {
    saving.value = false
  }
}

async function handleSavePassword() {
  if (!passwordFormRef.value) return
  const valid = await passwordFormRef.value.validate().catch(() => false)
  if (!valid) return
  saving.value = true
  try {
    await updatePassword({
      oldPassword: passwordForm.oldPassword,
      newPassword: passwordForm.newPassword,
      confirmPassword: passwordForm.confirmPassword
    })
    ElMessage.success('密码修改成功，请重新登录')
    userStore.logout()
    tagsStore.reset()
    emit('update:modelValue', false)
    router.push('/login')
  } finally {
    saving.value = false
  }
}
</script>

<style scoped>
.profile-tabs {
  min-height: 260px;
}

.profile-tip {
  margin: 4px 0 0 88px;
  color: var(--color-text-placeholder);
  font-size: 12px;
  line-height: 1.6;
}
</style>
