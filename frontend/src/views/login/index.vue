<template>
  <div class="login">
    <aside class="login__brand">
      <div class="login__brand-inner">
        <div class="login__mark">运</div>
        <h1 class="login__title">运输管理系统</h1>
        <p class="login__subtitle">YUNSHU TRANSPORT MANAGEMENT</p>
        <p class="login__desc">
          开单揽收 · 运输调度 · 签收回单 · 财务结算<br />
          一套系统贯通运输全流程
        </p>
      </div>
    </aside>

    <main class="login__panel">
      <div class="login__form-wrap">
        <h2 class="login__form-title">账号登录</h2>
        <p class="login__form-desc">骨架阶段使用本地 mock，任意账号密码均可进入</p>

        <el-form ref="formRef" :model="form" :rules="rules" label-position="top" @submit.prevent>
          <el-form-item label="账号" prop="username">
            <el-input v-model="form.username" placeholder="请输入账号" size="large" />
          </el-form-item>
          <el-form-item label="密码" prop="password">
            <el-input
              v-model="form.password"
              type="password"
              placeholder="请输入密码"
              size="large"
              show-password
              @keyup.enter="handleLogin"
            />
          </el-form-item>
          <el-button
            type="primary"
            size="large"
            class="login__submit"
            :loading="loading"
            @click="handleLogin"
          >
            登录
          </el-button>
        </el-form>
      </div>
    </main>
  </div>
</template>

<script setup lang="ts">
import { reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, type FormInstance, type FormRules } from 'element-plus'
import { useUserStore } from '@/stores/user'

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()

const formRef = ref<FormInstance>()
const loading = ref(false)
const form = reactive({ username: '', password: '' })

const rules: FormRules = {
  username: [{ required: true, message: '请输入账号', trigger: 'blur' }],
  password: [{ required: true, message: '请输入密码', trigger: 'blur' }]
}

async function handleLogin() {
  if (!formRef.value) return
  const valid = await formRef.value.validate().catch(() => false)
  if (!valid) return

  loading.value = true
  try {
    await userStore.login(form.username, form.password)
    const redirect = (route.query.redirect as string) || '/dashboard'
    await router.replace(redirect)
    ElMessage.success('登录成功')
  } catch (error) {
    const message = (error as { message?: string })?.message || '登录失败'
    ElMessage.error(message)
  } finally {
    loading.value = false
  }
}
</script>

<style scoped>
.login {
  display: flex;
  width: 100%;
  height: 100vh;
  background-color: var(--color-bg-page);
}

.login__brand {
  flex: 0 0 46%;
  display: flex;
  align-items: center;
  justify-content: center;
  background: var(--gradient-brand-panel);
  color: var(--color-white);
}

.login__brand-inner {
  max-width: 420px;
  padding: var(--spacing-section);
}

.login__mark {
  width: 44px;
  height: 44px;
  display: flex;
  align-items: center;
  justify-content: center;
  margin-bottom: var(--spacing-xl);
  border: 1px solid rgba(255, 255, 255, 0.24);
  border-radius: var(--radius-sm);
  background-color: rgba(255, 255, 255, 0.1);
  font-size: var(--font-size-large);
  font-weight: var(--font-weight-semibold);
}

.login__title {
  margin: 0;
  font-size: var(--font-size-huge);
  font-weight: var(--font-weight-semibold);
  letter-spacing: 0.08em;
}

.login__subtitle {
  margin: var(--spacing-sm) 0 var(--spacing-xl);
  font-size: var(--font-size-extra-small);
  letter-spacing: 0.22em;
  opacity: 0.55;
}

.login__desc {
  margin: 0;
  font-size: var(--font-size-base);
  line-height: var(--line-height-loose);
  color: rgba(255, 255, 255, 0.72);
}

.login__panel {
  flex: 1;
  display: flex;
  align-items: center;
  justify-content: center;
  background-color: var(--color-white);
}

.login__form-wrap {
  width: 360px;
}

.login__form-title {
  margin: 0;
  font-size: var(--font-size-big);
  font-weight: var(--font-weight-semibold);
  color: var(--color-text-primary);
}

.login__form-desc {
  margin: var(--spacing-sm) 0 var(--spacing-xl);
  font-size: var(--font-size-small);
  color: var(--color-text-secondary);
}

.login__submit {
  width: 100%;
  margin-top: var(--spacing-sm);
}

@media (max-width: 860px) {
  .login__brand {
    display: none;
  }
}
</style>
