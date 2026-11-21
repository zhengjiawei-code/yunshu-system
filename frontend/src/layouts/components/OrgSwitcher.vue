<template>
  <!-- 机构切换器：顶栏右侧，显示当前机构，下拉切换；切换只变数据范围，不动菜单权限 -->
  <el-dropdown trigger="click" placement="bottom-end" @command="handleSelect">
    <button type="button" class="org-switcher" :title="`当前机构：${currentName || '未分配'}`">
      <el-icon class="org-switcher__icon"><OfficeBuilding /></el-icon>
      <span class="org-switcher__name">{{ currentName || '未分配机构' }}</span>
      <el-icon class="org-switcher__caret"><ArrowDown /></el-icon>
    </button>
    <template #dropdown>
      <div class="org-dropdown">
        <div class="org-dropdown__head">切换机构</div>
        <div v-if="orgs.length === 0" class="org-dropdown__empty">无可切换机构</div>
        <el-scrollbar v-else max-height="280px">
          <el-dropdown-menu class="org-dropdown__menu">
            <el-dropdown-item
              v-for="org in orgs"
              :key="org.id"
              :command="org.id"
              :class="{ 'is-active': org.id === currentOrgId }"
              :disabled="org.id === currentOrgId"
            >
              <span class="org-item__name" :title="org.name">{{ org.name }}</span>
              <el-icon v-if="org.id === currentOrgId" class="org-item__check"><Check /></el-icon>
            </el-dropdown-item>
          </el-dropdown-menu>
        </el-scrollbar>
        <div class="org-dropdown__tip">切换后当前页面数据将按新机构重新加载，标签页保留</div>
      </div>
    </template>
  </el-dropdown>
</template>

<script setup lang="ts">
import { computed, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { ArrowDown, Check, OfficeBuilding } from '@element-plus/icons-vue'
import { useUserStore } from '@/stores/user'

const emit = defineEmits<{ (e: 'switched'): void }>()

const userStore = useUserStore()
const switching = ref(false)

const orgs = computed(() => userStore.loginableOrgs)
const currentOrgId = computed(() => userStore.currentOrgId)
const currentName = computed(() => userStore.currentOrgName)

async function handleSelect(orgId: number) {
  if (switching.value || orgId === currentOrgId.value) return
  const target = orgs.value.find((item) => item.id === orgId)
  if (!target) return
  try {
    await ElMessageBox.confirm(
      `切换到「${target.name}」？当前页面数据将按新机构重新加载。`,
      '切换机构',
      { confirmButtonText: '切换', cancelButtonText: '取消', type: 'warning' }
    )
  } catch {
    return
  }
  switching.value = true
  try {
    await userStore.switchOrg(orgId)
    emit('switched')
    ElMessage.success(`已切换到「${target.name}」`)
  } finally {
    switching.value = false
  }
}
</script>

<style scoped>
.org-switcher {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  height: 32px;
  max-width: 220px;
  padding: 0 10px;
  border: 1px solid var(--color-border-light);
  border-radius: var(--radius-sm);
  background-color: var(--color-white);
  color: var(--color-text-primary);
  font-size: var(--font-size-small);
  cursor: pointer;
  transition: var(--transition-fast);
}

.org-switcher:hover {
  background-color: var(--color-bg-hover);
  border-color: var(--color-primary-light-5);
}

.org-switcher__icon {
  font-size: 14px;
  color: var(--color-primary);
  flex-shrink: 0;
}

.org-switcher__name {
  min-width: 0;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
  font-weight: var(--font-weight-medium);
}

.org-switcher__caret {
  font-size: 12px;
  color: var(--color-text-placeholder);
  flex-shrink: 0;
}

.org-dropdown {
  min-width: 220px;
  padding: 4px 0;
}

.org-dropdown__head {
  padding: 6px 12px;
  color: var(--color-text-secondary);
  font-size: var(--font-size-small);
}

.org-dropdown__empty {
  padding: 14px 0;
  text-align: center;
  color: var(--color-text-placeholder);
  font-size: var(--font-size-small);
}

.org-dropdown__menu {
  padding: 0;
}

.org-item__name {
  flex: 1;
  min-width: 0;
  max-width: 180px;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.org-item__check {
  margin-left: 8px;
  font-size: 14px;
  color: var(--color-primary);
}

.org-dropdown__tip {
  margin-top: 4px;
  padding: 8px 12px 4px;
  border-top: 1px solid var(--color-border-light);
  color: var(--color-text-placeholder);
  font-size: 12px;
  line-height: 1.5;
}

:deep(.el-dropdown-menu__item.is-active) {
  color: var(--color-primary);
  font-weight: var(--font-weight-medium);
}
</style>
