<template>
  <page-drawer v-model="visible" :title="isEdit ? '编辑按钮' : '新增按钮'" :size="640">
    <el-form ref="formRef" :model="form" :rules="rules" label-width="100px" class="button-form">
      <el-form-item label="所属菜单">
        <span class="readonly-text">{{ parentMenu?.menuName || '—' }}</span>
      </el-form-item>

      <el-form-item label="按钮名称" prop="menuName">
        <el-input v-model="form.menuName" maxlength="20" show-word-limit placeholder="2-20 个字" />
      </el-form-item>

      <el-form-item label="权限标识" prop="perms">
        <el-input v-model="form.perms" placeholder="如 system:user:add" />
        <p class="form-hint">按钮权限标识全站唯一</p>
      </el-form-item>

      <el-form-item label="排序" prop="sortOrder">
        <el-input-number v-model="form.sortOrder" :min="0" :max="9999" :step="10" controls-position="right" />
      </el-form-item>

      <el-form-item label="状态">
        <el-switch v-model="form.status" inline-prompt active-text="启用" inactive-text="禁用" />
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
import type { MenuNode } from '@/types/menu'
import type { MenuSavePayload } from '@/api/system/menu'
import { createMenu, updateMenu } from '@/api/system/menu'
import { findNode, maxSort, walk } from '@/utils/tree'
import PageDrawer from '@/components/base/PageDrawer.vue'

const props = defineProps<{
  modelValue: boolean
  record: MenuNode | null
  parent: MenuNode | null
  tree: MenuNode[]
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

const isEdit = computed(() => !!props.record)
const parentMenu = computed(() => {
  if (props.record) return findNode(props.tree, props.record.parentId)
  return props.parent
})

const form = reactive({
  menuName: '',
  perms: '',
  sortOrder: 10,
  status: true,
  remark: ''
})

function permsExists(perms: string) {
  let exists = false
  walk(props.tree, (node) => {
    if (node.menuType !== 3) return
    if (node.id !== props.record?.id && node.perms === perms) exists = true
  })
  return exists
}

function nameExists(name: string) {
  const parent = parentMenu.value
  if (!parent) return false
  const trimmed = name.trim()
  return (parent.children || []).some(
    (node) => node.menuType === 3 && node.menuName === trimmed && node.id !== props.record?.id
  )
}

const rules: FormRules = {
  menuName: [
    { required: true, message: '请输入按钮名称', trigger: 'blur' },
    { min: 2, max: 20, message: '按钮名称为 2-20 个字', trigger: 'blur' },
    {
      validator: (_rule, value, callback) => {
        if (value && nameExists(value)) return callback(new Error('同一菜单下已存在同名按钮'))
        callback()
      },
      trigger: 'blur'
    }
  ],
  perms: [
    { required: true, message: '请输入权限标识', trigger: 'blur' },
    {
      validator: (_rule, value, callback) => {
        if (!value) return callback()
        if (!/^[a-z][a-z0-9-]*(:[a-z][a-z0-9-]*){1,2}$/.test(value)) {
          return callback(new Error('格式如 system:user:add'))
        }
        if (permsExists(value)) return callback(new Error('权限标识已存在'))
        callback()
      },
      trigger: 'blur'
    }
  ],
  remark: [{ max: 500, message: '备注不能超过 500 字', trigger: 'blur' }]
}

watch(
  () => props.modelValue,
  (open) => {
    if (!open) return
    if (props.record) {
      form.menuName = props.record.menuName
      form.perms = props.record.perms || ''
      form.sortOrder = props.record.sortOrder
      form.status = props.record.status
      form.remark = props.record.remark || ''
    } else {
      const siblings = (parentMenu.value?.children || []).filter((node) => node.menuType === 3)
      form.menuName = ''
      form.perms = ''
      form.sortOrder = maxSort(siblings) + 10
      form.status = true
      form.remark = ''
    }
    formRef.value?.clearValidate()
  }
)

async function handleSubmit() {
  if (!formRef.value || !parentMenu.value) return
  const valid = await formRef.value.validate().catch(() => false)
  if (!valid) return

  const parent = parentMenu.value
  const payload: MenuSavePayload = {
    parentId: parent.id,
    menuType: 3,
    menuName: form.menuName.trim(),
    perms: form.perms.trim(),
    terminalType: parent.terminalType,
    sortOrder: form.sortOrder,
    visible: true,
    status: form.status,
    remark: form.remark || undefined
  }

  submitting.value = true
  try {
    if (props.record) {
      await updateMenu(props.record.id, payload)
      ElMessage.success('保存成功')
    } else {
      await createMenu(payload)
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
.readonly-text {
  font-size: var(--font-size-small);
  color: var(--color-text-regular);
}

.form-hint {
  margin: 4px 0 0;
  font-size: var(--font-size-extra-small);
  color: var(--color-text-placeholder);
}
</style>
