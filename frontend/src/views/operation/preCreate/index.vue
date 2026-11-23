<template>
  <div class="redirect-tip">正在打开预开单列表…</div>
</template>

<script setup lang="ts">
import { onMounted } from 'vue'
import { useRouter } from 'vue-router'

const router = useRouter()

onMounted(() => {
  router.replace({ path: '/operation/create', query: { tab: 'pre' } })
})
</script>

<style scoped>
.redirect-tip {
  padding: 24px;
  color: var(--color-text-secondary);
}
</style>
