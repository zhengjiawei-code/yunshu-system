<template>
  <el-dialog
    v-model="visible"
    title="账号权限配置"
    width="640px"
    :close-on-click-modal="false"
    destroy-on-close
    class="user-perm-dialog"
    @opened="applyCheckedKeys"
  >
    <div v-if="user" class="perm-head">
      <span class="perm-head__name">{{ user.realName }}</span>
      <span class="perm-head__code mono">{{ user.username }}</span>
      <span class="perm-head__roles">角色：{{ user.roleNames.length ? user.roleNames.join('、') : '无' }}</span>
    </div>

    <el-tabs v-model="activeTab" v-loading="loading">
      <el-tab-pane label="额外授权" name="grant">
        <p class="perm-tip">
          在角色权限之上追加菜单/按钮；角色已含节点置灰不可重复勾选，统计只计直接授权行。
        </p>
        <div class="perm-tree-wrap">
          <el-tree
            ref="grantTreeRef"
            :data="menuTree"
            :props="grantTreeProps"
            node-key="id"
            show-checkbox
            default-expand-all
            :expand-on-click-node="false"
          >
            <template #default="{ data }">
              <span class="tree-node">
                <span class="tree-node__label">{{ data.menuName }}</span>
                <span class="tree-node__type">{{ typeName(data.menuType) }}</span>
                <span v-if="roleMenuSet.has(data.id)" class="tree-node__badge">角色已含</span>
              </span>
            </template>
          </el-tree>
        </div>
      </el-tab-pane>

      <el-tab-pane label="显式禁止" name="deny">
        <p class="perm-tip">
          禁止优先于角色与额外授权；勾选父级会级联禁止其全部子孙（由后端计算）。
        </p>
        <div class="perm-tree-wrap">
          <el-tree
            ref="denyTreeRef"
            :data="menuTree"
            :props="{ label: 'menuName', children: 'children' }"
            node-key="id"
            show-checkbox
            check-strictly
            default-expand-all
            :expand-on-click-node="false"
          >
            <template #default="{ data }">
              <span class="tree-node">
                <span class="tree-node__label">{{ data.menuName }}</span>
                <span class="tree-node__type">{{ typeName(data.menuType) }}</span>
              </span>
            </template>
          </el-tree>
        </div>
      </el-tab-pane>
    </el-tabs>

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
import type { UserRecord } from '@/types/user'
import { getMenuTree } from '@/api/system/menu'
import { getUserPerms, saveUserPerms } from '@/api/system/user'

const props = defineProps<{
  modelValue: boolean
  user: UserRecord | null
}>()

const emit = defineEmits<{
  'update:modelValue': [value: boolean]
  saved: []
}>()

const visible = computed({
  get: () => props.modelValue,
  set: (value: boolean) => emit('update:modelValue', value)
})

const activeTab = ref('grant')
const loading = ref(false)
const submitting = ref(false)
const menuTree = ref<MenuNode[]>([])
const grantTreeRef = ref<any>()
const denyTreeRef = ref<any>()

const roleMenuSet = ref<Set<number>>(new Set())
const grantedIds = ref<number[]>([])
const deniedIds = ref<number[]>([])

const grantTreeProps = computed(() => ({
  label: 'menuName',
  children: 'children',
  disabled: (data: MenuNode) => roleMenuSet.value.has(data.id)
}))

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

function applyCheckedKeys() {
  const grantTree = grantTreeRef.value
  const denyTree = denyTreeRef.value
  if (!grantTree || !denyTree) return
  const leaves = new Set(leafIds(menuTree.value))
  const grantKeys = grantedIds.value.filter(
    (id) => leaves.has(id) && !roleMenuSet.value.has(id)
  )
  grantTree.setCheckedKeys(grantKeys)
  denyTree.setCheckedKeys(deniedIds.value)
}

watch(
  () => props.modelValue,
  async (open) => {
    if (!open || !props.user) return
    activeTab.value = 'grant'
    loading.value = true
    try {
      const [tree, perms] = await Promise.all([getMenuTree(), getUserPerms(props.user.id)])
      menuTree.value = tree
      roleMenuSet.value = new Set(perms.roleMenuIds)
      grantedIds.value = perms.grantedIds
      deniedIds.value = perms.deniedIds
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
  const user = props.user
  const grantTree = grantTreeRef.value
  const denyTree = denyTreeRef.value
  if (!user || !grantTree || !denyTree) return

  // 额外授权：已勾选 + 半选父级，剔除角色已含节点（避免重复计数）
  const grantChecked: number[] = grantTree.getCheckedKeys()
  const grantHalf: number[] = grantTree.getHalfCheckedKeys()
  const granted = Array.from(new Set([...grantChecked, ...grantHalf]))
    .filter((id) => !roleMenuSet.value.has(id))
  const denied: number[] = denyTree.getCheckedKeys()

  submitting.value = true
  try {
    await saveUserPerms(user.id, granted, denied)
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
.perm-head {
  display: flex;
  align-items: baseline;
  gap: 8px;
  padding-bottom: 10px;
  border-bottom: 1px solid var(--color-border-light);
}

.perm-head__name {
  font-size: var(--font-size-base);
  font-weight: var(--font-weight-semibold);
  color: var(--color-text-primary);
}

.perm-head__code {
  font-size: var(--font-size-extra-small);
  color: var(--color-text-placeholder);
}

.perm-head__roles {
  margin-left: auto;
  font-size: var(--font-size-extra-small);
  color: var(--color-text-secondary);
}

.mono {
  font-family: var(--font-family-number);
}

.perm-tip {
  margin: 8px 0 10px;
  font-size: var(--font-size-extra-small);
  color: var(--color-text-placeholder);
  line-height: 1.6;
}

.perm-tree-wrap {
  max-height: 400px;
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
