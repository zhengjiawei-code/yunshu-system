<template>
  <el-dialog
    v-model="visible"
    title="批量授权"
    width="620px"
    :close-on-click-modal="false"
    destroy-on-close
    @open="handleOpen"
  >
    <el-form :model="form" label-width="96px">
      <el-form-item label="路由">
        <el-select
          v-model="form.routeIds"
          multiple
          filterable
          collapse-tags
          :max-collapse-tags="4"
          placeholder="选择要授权的路由（最多 50 条）"
          class="full-width"
        >
          <el-option
            v-for="item in routes"
            :key="item.id"
            :label="`${item.routeCode}｜${item.routeName}`"
            :value="item.id"
          />
        </el-select>
      </el-form-item>
      <el-form-item label="授权分公司">
        <el-select
          v-model="form.companyDeptIds"
          multiple
          filterable
          collapse-tags
          :max-collapse-tags="4"
          placeholder="选择分公司（最多 50 家）"
          class="full-width"
        >
          <el-option
            v-for="item in companyOptions"
            :key="item.id"
            :label="item.deptName"
            :value="item.id"
          />
        </el-select>
      </el-form-item>
    </el-form>

    <template #footer>
      <el-button @click="visible = false">取消</el-button>
      <el-button type="primary" :loading="saving" @click="handleSubmit">确定授权</el-button>
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
import { computed, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import type { DeptNode } from '@/types/dept'
import type { RouteRecord } from '@/types/route'
import { getDeptOptions } from '@/api/system/dept'
import { batchGrantRoutes } from '@/api/system/route'

const props = defineProps<{
  modelValue: boolean
  routeScope: 1 | 2
  routes: RouteRecord[]
}>()

const emit = defineEmits<{
  'update:modelValue': [value: boolean]
  changed: []
}>()

const visible = computed({
  get: () => props.modelValue,
  set: (value: boolean) => emit('update:modelValue', value)
})

const saving = ref(false)
const companyOptions = ref<DeptNode[]>([])
const form = reactive<{ routeIds: number[]; companyDeptIds: number[] }>({
  routeIds: [],
  companyDeptIds: []
})

async function handleOpen() {
  form.routeIds = []
  form.companyDeptIds = []
  try {
    companyOptions.value = await getDeptOptions(2)
  } catch {
    // 忽略
  }
}

async function handleSubmit() {
  if (form.routeIds.length === 0) {
    ElMessage.warning('请选择路由')
    return
  }
  if (form.companyDeptIds.length === 0) {
    ElMessage.warning('请选择授权分公司')
    return
  }
  saving.value = true
  try {
    const result = await batchGrantRoutes(props.routeScope, form.routeIds, form.companyDeptIds)
    ElMessage.success(result.message)
    visible.value = false
    emit('changed')
  } catch (error) {
    const message = (error as { message?: string })?.message
    if (message) ElMessage.error(message)
  } finally {
    saving.value = false
  }
}
</script>

<style scoped>
.full-width {
  width: 100%;
}
</style>
