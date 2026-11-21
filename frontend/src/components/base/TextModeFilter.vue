<template>
  <el-popover
    v-model:visible="menuVisible"
    placement="bottom-start"
    :width="196"
    trigger="click"
    :show-arrow="false"
    popper-class="text-mode-filter__popper"
  >
    <template #reference>
      <el-input
        :model-value="modelValue"
        size="small"
        clearable
        :placeholder="placeholderText"
        @update:model-value="onInput"
        @clear="onClear"
        @keyup.enter="emit('change')"
      >
        <template #suffix>
          <el-tooltip :content="currentLabel" placement="top">
            <el-icon class="text-mode-filter__icon" :class="{ 'is-active': currentMode !== 'like' }">
              <Filter />
            </el-icon>
          </el-tooltip>
        </template>
      </el-input>
    </template>

    <div class="text-mode-filter__menu">
      <button
        v-for="option in TEXT_MATCH_MODE_OPTIONS"
        :key="option.value"
        type="button"
        class="text-mode-filter__item"
        :class="{ 'is-active': currentMode === option.value }"
        @click="selectMode(option.value)"
      >
        <span class="text-mode-filter__label">{{ option.label }}</span>
        <span class="text-mode-filter__sample">{{ option.sample }}</span>
      </button>
    </div>
  </el-popover>
</template>

<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { Filter } from '@element-plus/icons-vue'
import {
  DEFAULT_TEXT_MATCH_MODE,
  TEXT_MATCH_MODE_OPTIONS,
  type TextMatchMode
} from '@/utils/textFilter'

/**
 * 文本多模式表头控件：输入后自动弹出匹配方式菜单，选中即查询。
 * 默认不受控时也能记住本次选择；父组件可传 mode 并监听 update:mode 接管。
 */
const props = withDefaults(
  defineProps<{
    modelValue?: string
    mode?: TextMatchMode
    placeholder?: string
  }>(),
  {
    modelValue: '',
    mode: DEFAULT_TEXT_MATCH_MODE,
    placeholder: '请输入'
  }
)

const emit = defineEmits<{
  'update:modelValue': [value: string]
  'update:mode': [value: TextMatchMode]
  change: []
}>()

const menuVisible = ref(false)
const innerMode = ref<TextMatchMode>(props.mode)

watch(
  () => props.mode,
  (value) => {
    if (value) innerMode.value = value
  }
)

const currentMode = computed(() => innerMode.value)
const currentLabel = computed(
  () => TEXT_MATCH_MODE_OPTIONS.find((option) => option.value === innerMode.value)?.label || '模糊'
)
const placeholderText = computed(() =>
  innerMode.value === 'batch' ? '多个值用逗号/空格/顿号分隔' : props.placeholder
)

function onInput(value: string) {
  emit('update:modelValue', value)
  if (!menuVisible.value) menuVisible.value = true
  emit('change')
}

function onClear() {
  emit('update:modelValue', '')
  menuVisible.value = false
  emit('change')
}

function selectMode(mode: TextMatchMode) {
  innerMode.value = mode
  emit('update:mode', mode)
  menuVisible.value = false
  emit('change')
}
</script>

<style scoped>
.text-mode-filter__icon {
  color: var(--color-text-placeholder);
  cursor: pointer;
}

.text-mode-filter__icon.is-active {
  color: var(--color-primary);
}

.text-mode-filter__menu {
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.text-mode-filter__item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  padding: 4px 6px;
  border: none;
  border-radius: 4px;
  background: transparent;
  color: var(--color-text-regular);
  font-size: var(--font-size-small);
  cursor: pointer;
}

.text-mode-filter__item:hover,
.text-mode-filter__item.is-active {
  background: var(--color-bg-hover);
  color: var(--color-primary);
}

.text-mode-filter__sample {
  font-size: var(--font-size-extra-small);
  color: var(--color-text-placeholder);
}
</style>
