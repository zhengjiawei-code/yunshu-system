<template>
  <el-drawer
    v-model="visible"
    class="page-drawer"
    :title="title"
    :size="`${size}px`"
    :close-on-click-modal="false"
    destroy-on-close
  >
    <div class="page-drawer__body">
      <slot />
    </div>
    <template #footer>
      <div class="page-drawer__footer">
        <slot name="footer" />
      </div>
    </template>
  </el-drawer>
</template>

<script setup lang="ts">
import { computed } from 'vue'

const props = withDefaults(
  defineProps<{
    modelValue: boolean
    title: string
    size?: number
  }>(),
  { size: 640 }
)

const emit = defineEmits<{ 'update:modelValue': [value: boolean] }>()

const visible = computed({
  get: () => props.modelValue,
  set: (value: boolean) => emit('update:modelValue', value)
})
</script>

<style scoped>
.page-drawer__body {
  padding: 20px;
}

.page-drawer__footer {
  display: flex;
  justify-content: flex-end;
  gap: var(--spacing-sm);
}
</style>
