<template>
  <el-dialog
    v-model="visible"
    title="权限分配"
    width="560px"
    :close-on-click-modal="false"
    destroy-on-close
    class="role-auth-dialog"
    @opened="applyCheckedKeys"
  >
    <div v-if="role" class="auth-head">
      <span class="auth-head__name">{{ role.roleName }}</span>
      <span class="auth-head__code mono">{{ role.roleCode }}</span>
    </div>

    <p class="auth-tip">
      勾选该角色可访问的菜单与按钮；目录按子节点自动联动，保存后整体覆盖原授权。
    </p>

    <div v-loading="loading" class="auth-tree-wrap">
      <el-tree
        ref="treeRef"
        :data="menuTree"
        :props="{ label: 'menuName', children: 'children' }"
        node-key="id"
        show-checkbox
        default-expand-all
        :expand-on-click-node="false"
      >
        <template #default="{ data }">
          <span class="tree-node">
            <span class="tree-node__label">{{ data.menuName }}</span>
            <span class="tree-node__type">{{ typeName(data.menuType) }}</span>
            <span v-if="!data.status" class="tree-node__badge">禁用</span>
            <span v-if="!data.visible" class="tree-node__badge">隐藏</span>
          </span>
        </template>
      </el-tree>
    </div>

    <template #footer>
      <el-button @click="visible = false">取消</el-button>
      <el-button type="primary" :loading="submitting" @click="handleSubmit">保存</el-button>
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
import { computed, nextTick, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import type { MenuNode } from '@/types/menu'
import type { RoleRecord } from '@/types/role'
import { getMenuTree } from '@/api/system/menu'
import { getRoleMenuIds, saveRoleMenus } from '@/api/system/role'

const props = defineProps<{
  modelValue: boolean
  role: RoleRecord | null
}>()

const emit = defineEmits<{
  'update:modelValue': [value: boolean]
  saved: []
}>()

const visible = computed({
  get: () => props.modelValue,
  set: (value: boolean) => emit('update:modelValue', value)
})

const treeRef = ref<any>()
const loading = ref(false)
const submitting = ref(false)
const menuTree = ref<MenuNode[]>([])

function typeName(type: number): string {
  if (type === 1) return '目录'
  if (type === 2) return '菜单'
  return '按钮'
}

/** 只收集叶子节点ID：父级勾选/半选由 el-tree 联动推导 */
function leafIds(nodes: MenuNode[]): number[] {
  const ids: number[] = []
  nodes.forEach((node) => {
    if (node.children?.length) {
      ids.push(...leafIds(node.children))
    } else {
      ids.push(node.id)
    }
  })
  return ids
}

/** 待回填的叶子节点ID：数据加载与弹窗渲染完成时机不一致，两个时机都尝试回填 */
const pendingKeys = ref<number[] | null>(null)

function applyCheckedKeys() {
  const keys = pendingKeys.value
  if (!keys || !treeRef.value) return
  treeRef.value.setCheckedKeys(keys)
  pendingKeys.value = null
}

watch(
  () => props.modelValue,
  async (open) => {
    if (!open) {
      pendingKeys.value = null
      return
    }
    if (!props.role) return
    loading.value = true
    try {
      const [tree, authorized] = await Promise.all([
        getMenuTree(),
        getRoleMenuIds(props.role.id)
      ])
      menuTree.value = tree
      // 回显只勾叶子，父级状态由联动推导
      const leaves = new Set(leafIds(tree))
      pendingKeys.value = authorized.filter((id) => leaves.has(id))
      await nextTick()
      applyCheckedKeys()
    } catch (error) {
      const message = (error as { message?: string })?.message
      if (message) ElMessage.error(message)
    } finally {
      loading.value = false
    }
  }
)

async function handleSubmit() {
  const role = props.role
  const tree = treeRef.value
  if (!role || !tree) return

  // 提交已勾选 + 半选父级，保证目录/菜单可见
  const checked: number[] = tree.getCheckedKeys()
  const halfChecked: number[] = tree.getHalfCheckedKeys()
  const menuIds = Array.from(new Set([...checked, ...halfChecked]))

  submitting.value = true
  try {
    await saveRoleMenus(role.id, menuIds)
    ElMessage.success('权限已保存')
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
.auth-head {
  display: flex;
  align-items: baseline;
  gap: 8px;
  padding-bottom: 10px;
  border-bottom: 1px solid var(--color-border-light);
}

.auth-head__name {
  font-size: var(--font-size-base);
  font-weight: var(--font-weight-semibold);
  color: var(--color-text-primary);
}

.auth-head__code {
  font-size: var(--font-size-extra-small);
  color: var(--color-text-placeholder);
}

.mono {
  font-family: var(--font-family-number);
}

.auth-tip {
  margin: 10px 0;
  font-size: var(--font-size-extra-small);
  color: var(--color-text-placeholder);
  line-height: 1.6;
}

.auth-tree-wrap {
  max-height: 420px;
  min-height: 160px;
  overflow: auto;
  padding: 8px;
  border: 1px solid var(--color-border-light);
  border-radius: var(--radius-sm);
  background-color: var(--color-bg-subtle);
}

.tree-node {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  min-width: 0;
}

.tree-node__label {
  font-size: var(--font-size-small);
  color: var(--color-text-primary);
}

.tree-node__type {
  padding: 0 4px;
  font-size: 10px;
  line-height: 16px;
  color: var(--color-text-secondary);
  border: 1px solid var(--color-border-light);
  border-radius: var(--radius-xs);
}

.tree-node__badge {
  padding: 0 4px;
  font-size: 10px;
  line-height: 16px;
  color: var(--color-text-secondary);
  background-color: var(--color-bg-disabled);
  border-radius: var(--radius-xs);
}
</style>
