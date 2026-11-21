<template>
  <page-drawer v-model="visible" :title="drawerTitle" :size="640">
    <el-form
      ref="formRef"
      :model="form"
      :rules="rules"
      label-width="100px"
      label-position="right"
      class="menu-form"
    >
      <el-form-item label="上级节点" prop="parentId">
        <el-tree-select
          v-model="form.parentId"
          :data="parentOptions"
          :props="{ label: 'menuName', children: 'children' }"
          node-key="id"
          check-strictly
          :render-after-expand="false"
          :clearable="isDirectory"
          :placeholder="isDirectory ? '不选则为顶级目录' : '请选择上级目录'"
          class="form-control"
        />
      </el-form-item>

      <el-form-item label="类型">
        <span class="readonly-text">{{ isDirectory ? '目录' : '菜单' }}</span>
      </el-form-item>

      <el-form-item label="菜单名称" prop="menuName">
        <el-input v-model="form.menuName" maxlength="20" show-word-limit placeholder="2-20 个字" />
      </el-form-item>

      <el-form-item label="路由地址" prop="routePath">
        <el-input
          v-model="form.routePath"
          :disabled="routeLocked"
          :placeholder="isDirectory ? '如 /system' : parentPathHint"
        >
          <template v-if="routeLocked" #suffix>
            <el-tooltip content="目录下已有内容，路由地址不可修改" placement="top">
              <el-icon><Lock /></el-icon>
            </el-tooltip>
          </template>
        </el-input>
        <p v-if="routeLocked" class="form-hint form-hint--warning">
          该目录下已有内容，路由地址已锁定
        </p>
      </el-form-item>

      <el-form-item v-if="!isDirectory" label="组件路径" prop="component">
        <el-select
          v-model="form.component"
          class="form-control"
          filterable
          allow-create
          default-first-option
          placeholder="选择 src/views 下的页面组件"
        >
          <el-option v-for="item in componentOptions" :key="item" :label="item" :value="item" />
        </el-select>
        <p v-if="componentMissing" class="form-hint form-hint--warning">
          该组件文件当前不存在，保存后页面会落到占位页
        </p>
      </el-form-item>

      <el-form-item v-if="!isDirectory" label="权限标识" prop="perms">
        <el-input v-model="form.perms" placeholder="选填，如 system:user:list" />
      </el-form-item>

      <el-form-item label="图标" prop="icon">
        <icon-picker v-model="form.icon" class="form-control" />
      </el-form-item>

      <el-form-item v-if="isDirectory" label="默认跳转" prop="redirect">
        <el-input v-model="form.redirect" placeholder="留空则自动跳转到第一个菜单" />
      </el-form-item>

      <el-form-item v-if="!isDirectory" label="终端类型" prop="terminalType">
        <el-radio-group v-model="form.terminalType" :disabled="true">
          <el-radio value="PC">PC</el-radio>
          <el-radio value="APP">APP</el-radio>
          <el-radio value="PC,APP">PC+APP</el-radio>
        </el-radio-group>
        <p class="form-hint">本期只建设 PC 端，APP 端字段预留</p>
      </el-form-item>

      <el-form-item v-if="!isDirectory" label="是否缓存">
        <el-switch v-model="form.keepAlive" />
      </el-form-item>

      <el-form-item v-if="!isDirectory" label="固定标签">
        <el-switch v-model="form.affix" />
      </el-form-item>

      <el-form-item label="排序" prop="sortOrder">
        <el-input-number v-model="form.sortOrder" :min="0" :max="9999" :step="10" controls-position="right" />
      </el-form-item>

      <el-form-item label="显示">
        <el-switch v-model="form.visible" />
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
import { Lock } from '@element-plus/icons-vue'
import type { MenuNode, TerminalType } from '@/types/menu'
import type { MenuSavePayload } from '@/api/system/menu'
import { createMenu, getViewComponents, updateMenu } from '@/api/system/menu'
import { findNode, getDepth, maxSort, toDirectoryOptions, walk } from '@/utils/tree'
import PageDrawer from '@/components/base/PageDrawer.vue'
import IconPicker from '@/components/base/IconPicker.vue'

const props = defineProps<{
  modelValue: boolean
  mode: 'create' | 'edit'
  menuType: 1 | 2
  parentId: number
  record: MenuNode | null
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
const componentOptions = ref<string[]>([])

const form = reactive({
  parentId: 0 as number | null,
  menuName: '',
  routePath: '',
  component: '',
  perms: '',
  icon: '',
  redirect: '',
  terminalType: 'PC' as TerminalType,
  keepAlive: true,
  affix: false,
  sortOrder: 10,
  visible: true,
  status: true,
  remark: ''
})

const isDirectory = computed(() => props.menuType === 1)
const isEdit = computed(() => props.mode === 'edit')
const drawerTitle = computed(() => {
  const typeName = isDirectory.value ? '目录' : '菜单'
  return isEdit.value ? `编辑${typeName}` : `新增${typeName}`
})

const record = computed(() => props.record)
const routeLocked = computed(
  () => isDirectory.value && isEdit.value && !!record.value?.children?.length
)

const parentOptions = computed(() => toDirectoryOptions(props.tree, record.value?.id))

const currentParent = computed(() =>
  form.parentId ? findNode(props.tree, form.parentId) : undefined
)

const parentPathHint = computed(() => {
  const parentPath = currentParent.value?.routePath
  return parentPath ? `如 ${parentPath}/list` : '如 /system/user'
})

const componentMissing = computed(
  () => !isDirectory.value && !!form.component && !componentOptions.value.includes(form.component)
)

/* ---------- 校验 ---------- */
function siblingsOf(parentId: number | null): MenuNode[] {
  if (!parentId) return props.tree
  return findNode(props.tree, parentId)?.children || []
}

function nameExists(name: string, parentId: number | null) {
  const trimmed = name.trim()
  return siblingsOf(parentId).some(
    (node) => node.menuName === trimmed && node.id !== record.value?.id
  )
}

function pathExists(path: string) {
  let exists = false
  walk(props.tree, (node) => {
    if (node.id !== record.value?.id && node.routePath === path) exists = true
  })
  return exists
}

const rules: FormRules = {
  parentId: [
    {
      validator: (_rule, value, callback) => {
        if (isDirectory.value) return callback()
        if (!value) return callback(new Error('请选择上级目录'))
        const parent = findNode(props.tree, value)
        if (!parent || parent.menuType !== 1) return callback(new Error('上级节点必须是目录'))
        callback()
      },
      trigger: 'change'
    }
  ],
  menuName: [
    { required: true, message: '请输入菜单名称', trigger: 'blur' },
    { min: 2, max: 20, message: '菜单名称为 2-20 个字', trigger: 'blur' },
    {
      validator: (_rule, value, callback) => {
        if (value && nameExists(value, form.parentId)) {
          return callback(new Error('同级下已存在同名节点'))
        }
        callback()
      },
      trigger: 'blur'
    }
  ],
  routePath: [
    { required: true, message: '请输入路由地址', trigger: 'blur' },
    {
      validator: (_rule, value, callback) => {
        if (!value) return callback()
        if (!/^\/[a-z0-9-]+(\/[a-z0-9-]+)*$/.test(value)) {
          return callback(new Error('只允许小写字母、数字、中划线，且以 / 开头'))
        }
        if (value.length > 200) return callback(new Error('路由地址不能超过 200 字符'))
        if (pathExists(value)) return callback(new Error('路由地址已存在'))
        callback()
      },
      trigger: 'blur'
    }
  ],
  component: [{ required: true, message: '请选择组件路径', trigger: 'change' }],
  perms: [
    {
      validator: (_rule, value, callback) => {
        if (!value) return callback()
        if (!/^[a-z][a-z0-9-]*(:[a-z][a-z0-9-]*){1,2}$/.test(value)) {
          return callback(new Error('格式如 system:user:add'))
        }
        callback()
      },
      trigger: 'blur'
    }
  ],
  icon: [{ required: true, message: '请选择图标', trigger: 'change' }],
  redirect: [
    {
      validator: (_rule, value, callback) => {
        if (!value) return callback()
        if (!/^\/[a-z0-9-/]+$/.test(value)) {
          return callback(new Error('默认跳转必须以 / 开头'))
        }
        callback()
      },
      trigger: 'blur'
    }
  ],
  sortOrder: [{ required: true, message: '请输入排序号', trigger: 'blur' }],
  remark: [{ max: 500, message: '备注不能超过 500 字', trigger: 'blur' }]
}

/* ---------- 初始化 ---------- */
function resetForm() {
  const recordNode = record.value
  if (isEdit.value && recordNode) {
    form.parentId = recordNode.parentId || 0
    form.menuName = recordNode.menuName
    form.routePath = recordNode.routePath || ''
    form.component = recordNode.component || ''
    form.perms = recordNode.perms || ''
    form.icon = recordNode.icon || ''
    form.redirect = ''
    form.terminalType = recordNode.terminalType
    form.keepAlive = recordNode.keepAlive !== false
    form.affix = !!recordNode.affix
    form.sortOrder = recordNode.sortOrder
    form.visible = recordNode.visible
    form.status = recordNode.status
    form.remark = recordNode.remark || ''
  } else {
    const parent = props.parentId ? findNode(props.tree, props.parentId) : undefined
    const siblings = siblingsOf(props.parentId || 0)
    form.parentId = props.parentId || 0
    form.menuName = ''
    form.routePath = ''
    form.component = ''
    form.perms = ''
    form.icon = isDirectory.value ? 'Folder' : 'Menu'
    form.redirect = ''
    form.terminalType = parent?.terminalType === 'PC,APP' ? 'PC' : parent?.terminalType || 'PC'
    form.keepAlive = true
    form.affix = false
    form.sortOrder = maxSort(siblings) + 10
    form.visible = true
    form.status = true
    form.remark = ''
  }
  formRef.value?.clearValidate()
}

watch(
  () => props.modelValue,
  async (open) => {
    if (!open) return
    componentOptions.value = getViewComponents()
    const current = record.value?.component
    if (current && !componentOptions.value.includes(current)) {
      componentOptions.value.unshift(current)
    }
    resetForm()
  }
)

/* ---------- 提交 ---------- */
async function handleSubmit() {
  if (!formRef.value) return
  const valid = await formRef.value.validate().catch(() => false)
  if (!valid) return

  const parentId = form.parentId || 0
  const depth = parentId ? getDepth(props.tree, parentId) : 0
  if (depth + 1 > 4) {
    ElMessage.error('菜单层级过深，最多 4 级（不含按钮）')
    return
  }

  const routePath = form.routePath.trim().replace(/\/+$/, '')
  if (!isDirectory.value) {
    const parent = findNode(props.tree, parentId)
    if (parent?.routePath && !routePath.startsWith(`${parent.routePath}/`)) {
      ElMessage.error(`菜单路由必须以 ${parent.routePath}/ 开头`)
      return
    }
  }

  if (componentMissing.value) {
    ElMessage.warning('组件文件不存在，已按警告保存')
  }

  const payload: MenuSavePayload = {
    parentId,
    menuType: props.menuType,
    menuName: form.menuName.trim(),
    routePath,
    sortOrder: form.sortOrder,
    visible: form.visible,
    status: form.status,
    remark: form.remark || undefined
  }

  if (isDirectory.value) {
    payload.redirect = form.redirect || undefined
    payload.icon = form.icon
  } else {
    payload.component = form.component
    payload.perms = form.perms || undefined
    payload.icon = form.icon
    payload.terminalType = form.terminalType
    payload.keepAlive = form.keepAlive
    payload.affix = form.affix
  }

  submitting.value = true
  try {
    if (isEdit.value && record.value) {
      await updateMenu(record.value.id, payload)
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
.menu-form {
  padding-right: 4px;
}

.form-control {
  width: 100%;
}

.readonly-text {
  font-size: var(--font-size-small);
  color: var(--color-text-regular);
}

.form-hint {
  margin: 4px 0 0;
  font-size: var(--font-size-extra-small);
  color: var(--color-text-placeholder);
  line-height: 1.5;
}

.form-hint--warning {
  color: var(--color-warning);
}
</style>
