<template>
  <div class="breadcrumb">
    <router-link to="/dashboard" class="breadcrumb__home">首页</router-link>
    <template v-for="(name, index) in parents" :key="`${name}-${index}`">
      <span class="breadcrumb__sep">/</span>
      <span class="breadcrumb__item">{{ name }}</span>
    </template>
    <template v-if="currentTitle">
      <span class="breadcrumb__sep">/</span>
      <span class="breadcrumb__item breadcrumb__item--current">{{ currentTitle }}</span>
    </template>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { useRoute } from 'vue-router'

const route = useRoute()

const parents = computed(() => (route.meta.parents as string[] | undefined) || [])
const currentTitle = computed(() => (route.meta.title as string | undefined) || '')
</script>

<style scoped>
.breadcrumb {
  display: flex;
  align-items: center;
  gap: 6px;
  min-width: 0;
  font-size: var(--font-size-small);
  color: var(--color-text-secondary);
  white-space: nowrap;
}

.breadcrumb__home {
  color: var(--color-text-secondary);
}

.breadcrumb__home:hover {
  color: var(--color-primary);
}

.breadcrumb__sep {
  color: var(--color-text-placeholder);
}

.breadcrumb__item {
  overflow: hidden;
  text-overflow: ellipsis;
}

.breadcrumb__item--current {
  color: var(--color-text-primary);
  font-weight: var(--font-weight-medium);
}
</style>
