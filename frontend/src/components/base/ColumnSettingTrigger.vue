<template>
  <el-popover placement="bottom-end" :width="560" trigger="click">
    <template #reference>
      <el-button
        ref="triggerRef"
        :size="size"
        :icon="Setting"
        class="column-setting-trigger"
        aria-label="列设置"
      />
    </template>
    <column-setting
      :schema="schema"
      :states="states"
      :loading="loading"
      :saving="saving"
      @update:states="emit('update:states', $event)"
      @save="emit('save')"
      @reset="emit('reset')"
    />
  </el-popover>
  <el-tooltip
    content="列设置"
    placement="top"
    :show-after="300"
    :virtual-ref="triggerEl"
    virtual-triggering
  />
</template>

<script setup lang="ts">
import { computed, ref } from 'vue'
import { Setting } from '@element-plus/icons-vue'
import type { ColumnDef, ColumnItemState } from '@/composables/useColumnConfig'
import ColumnSetting from './ColumnSetting.vue'

withDefaults(
  defineProps<{
    schema: ColumnDef[]
    states: ColumnItemState[]
    loading?: boolean
    saving?: boolean
    size?: 'large' | 'default' | 'small'
  }>(),
  { loading: false, saving: false, size: 'default' }
)

const emit = defineEmits<{
  'update:states': [value: ColumnItemState[]]
  save: []
  reset: []
}>()

/** tooltip 与 popover 共用同一个按钮，但互不嵌套，避免遮挡点击 */
const triggerRef = ref<{ $el?: HTMLElement }>()
const triggerEl = computed(() => triggerRef.value?.$el)
</script>

<style scoped>
/* 图标按钮：正方形，宽高对齐相邻按钮尺寸，确保同一行同一高度 */
.column-setting-trigger {
  padding: 0;
  width: var(--el-component-size);
}

.column-setting-trigger.el-button--small {
  width: var(--el-component-size-small);
}

.column-setting-trigger.el-button--large {
  width: var(--el-component-size-large);
}
</style>
