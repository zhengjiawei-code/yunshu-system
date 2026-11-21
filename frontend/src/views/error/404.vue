<template>
  <div class="not-found">
    <div class="not-found__code">404</div>
    <p class="not-found__text">页面不存在或已被移除</p>
    <el-button type="primary" @click="router.replace('/dashboard')">返回首页</el-button>
  </div>
</template>

<script setup lang="ts">
import { useRouter } from 'vue-router'

const router = useRouter()
</script>

<style scoped>
.not-found {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: var(--spacing-base);
  height: 100vh;
  background-color: var(--color-bg-page);
}

.not-found__code {
  font-family: var(--font-family-number);
  font-size: 72px;
  font-weight: var(--font-weight-semibold);
  color: var(--color-primary-light-3);
  letter-spacing: 0.06em;
}

.not-found__text {
  margin: 0;
  font-size: var(--font-size-base);
  color: var(--color-text-secondary);
}
</style>
