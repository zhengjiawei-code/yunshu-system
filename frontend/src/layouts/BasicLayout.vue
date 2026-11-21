<template>
  <el-container class="layout">
    <el-aside class="sidebar" :class="{ 'is-collapse': isCollapse }">
      <div class="brand">
        <span class="brand__mark">运</span>
        <span v-show="!isCollapse" class="brand__text">
          <span class="brand__name">运输管理系统</span>
          <span class="brand__sub">YUNSHU SYSTEM</span>
        </span>
      </div>

      <el-scrollbar class="nav-scroll">
        <el-menu
          class="nav"
          :default-active="activeMenu"
          :collapse="isCollapse"
          :collapse-transition="false"
          router
        >
          <el-menu-item index="/dashboard">
            <el-icon><HomeFilled /></el-icon>
            <template #title>首页</template>
          </el-menu-item>
          <sidebar-item
            v-for="menu in userStore.menus"
            :key="menu.id"
            :menu="menu"
          />
        </el-menu>
      </el-scrollbar>
    </el-aside>

    <el-container class="workspace">
      <el-header class="header">
        <div class="header__left">
          <button
            type="button"
            class="icon-btn"
            :aria-label="isCollapse ? '展开导航' : '收起导航'"
            @click="isCollapse = !isCollapse"
          >
            <el-icon><Expand v-if="isCollapse" /><Fold v-else /></el-icon>
          </button>
          <span class="header__divider" />
          <breadcrumb />
        </div>

        <div class="header__middle">
          <tags-view />
        </div>

        <div class="header__right">
          <el-button
            class="header-refresh"
            :icon="Refresh"
            circle
            title="刷新当前页面"
            aria-label="刷新当前页面"
            @click="handleRefresh"
          />
          <span class="header__divider" />
          <org-switcher @switched="handleOrgSwitched" />
          <span class="header__divider" />
          <el-dropdown trigger="click" @command="handleCommand">
            <button type="button" class="user-chip">
              <span class="user-chip__avatar">{{ userStore.avatarText }}</span>
              <span class="user-chip__name">{{ userStore.displayName }}</span>
              <el-icon class="user-chip__caret"><ArrowDown /></el-icon>
            </button>
            <template #dropdown>
              <el-dropdown-menu>
                <el-dropdown-item command="profile">
                  <el-icon><User /></el-icon>个人中心
                </el-dropdown-item>
                <el-dropdown-item divided command="logout">
                  <el-icon><SwitchButton /></el-icon>退出登录
                </el-dropdown-item>
              </el-dropdown-menu>
            </template>
          </el-dropdown>
        </div>
      </el-header>

      <el-main class="main">
        <router-view v-slot="{ Component, route: currentRoute }">
          <transition name="page" mode="out-in">
            <keep-alive :max="15">
              <component :is="Component" :key="currentRoute.fullPath + refreshKey" />
            </keep-alive>
          </transition>
        </router-view>
      </el-main>
    </el-container>

    <profile-dialog v-model="profileVisible" />
  </el-container>
</template>

<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { ArrowDown, Expand, Fold, HomeFilled, Refresh, SwitchButton, User } from '@element-plus/icons-vue'
import { logoutApi } from '@/api/auth'
import { useUserStore } from '@/stores/user'
import { useTagsStore } from '@/stores/tags'
import SidebarItem from './components/SidebarItem.vue'
import TagsView from './components/TagsView.vue'
import Breadcrumb from './components/Breadcrumb.vue'
import OrgSwitcher from './components/OrgSwitcher.vue'
import ProfileDialog from './components/ProfileDialog.vue'

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()
const tagsStore = useTagsStore()

const isCollapse = ref(false)
const refreshKey = ref(0)
const profileVisible = ref(false)

const activeMenu = computed(() => route.path)

function handleRefresh() {
  refreshKey.value += 1
}

/** 切换机构后：重建当前页（含 keep-alive 缓存），标签页保留；不重算菜单权限 */
function handleOrgSwitched() {
  refreshKey.value += 1
}

async function handleCommand(command: string) {
  if (command === 'profile') {
    profileVisible.value = true
    return
  }
  if (command !== 'logout') return
  try {
    await ElMessageBox.confirm('确定要退出登录吗？', '提示', {
      confirmButtonText: '确定',
      cancelButtonText: '取消',
      type: 'warning'
    })
  } catch {
    return
  }
  try {
    await logoutApi()
  } catch {
    // 会话已失效时忽略，本地继续清理
  }
  userStore.logout()
  tagsStore.reset()
  ElMessage.success('已退出登录')
  router.push('/login')
}

function handleResize() {
  if (window.innerWidth < 1180) isCollapse.value = true
}

onMounted(() => {
  handleResize()
  window.addEventListener('resize', handleResize)
})

onBeforeUnmount(() => {
  window.removeEventListener('resize', handleResize)
})
</script>

<style scoped>
.layout {
  height: 100vh;
  width: 100%;
  background-color: var(--color-bg-page);
}

/* 侧边栏 */
.sidebar {
  width: var(--sidebar-width);
  display: flex;
  flex-direction: column;
  overflow: hidden;
  background-color: var(--color-sidebar-bg);
  border-right: 1px solid var(--color-sidebar-border);
  transition: width var(--duration-slow) var(--ease-out);
}

.sidebar.is-collapse {
  width: var(--sidebar-collapse-width);
}

.brand {
  height: var(--logo-height);
  flex-shrink: 0;
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 0 12px;
  overflow: hidden;
  white-space: nowrap;
  background-color: var(--color-sidebar-logo-bg);
  border-bottom: 1px solid var(--color-sidebar-border);
}

.sidebar.is-collapse .brand {
  padding: 0 16px;
}

.brand__mark {
  width: 28px;
  height: 28px;
  flex-shrink: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  border-radius: var(--radius-xs);
  background-color: rgba(255, 255, 255, 0.1);
  border: 1px solid rgba(255, 255, 255, 0.16);
  color: var(--color-sidebar-text-active);
  font-size: var(--font-size-base);
  font-weight: var(--font-weight-semibold);
}

.brand__text {
  display: flex;
  flex-direction: column;
  line-height: 1.25;
  min-width: 0;
}

.brand__name {
  color: var(--color-sidebar-text-active);
  font-size: var(--font-size-base);
  font-weight: var(--font-weight-semibold);
  letter-spacing: 0.06em;
}

.brand__sub {
  margin-top: 3px;
  color: var(--color-sidebar-text);
  font-size: 9px;
  letter-spacing: 0.16em;
  opacity: 0.55;
}

.nav-scroll {
  flex: 1;
  min-height: 0;
}

.nav-scroll :deep(.el-scrollbar__view) {
  padding: 10px 0;
}

.nav {
  border-right: none;
  background-color: transparent;
  padding: 6px;
}

.nav :deep(.el-menu-item),
.nav :deep(.el-sub-menu__title) {
  position: relative;
  height: 36px;
  line-height: 36px;
  margin: 1px 0;
  padding-left: 10px !important;
  padding-right: 8px;
  border-radius: var(--radius-xs);
  color: var(--color-sidebar-text);
  font-size: 13px;
  letter-spacing: 0.01em;
  transition: background-color var(--duration-fast) var(--ease-out),
    color var(--duration-fast) var(--ease-out);
}

.nav :deep(.el-menu-item .el-icon),
.nav :deep(.el-sub-menu__title .el-icon) {
  width: 15px;
  margin-right: 8px;
  font-size: 15px;
  color: var(--color-sidebar-text);
  transition: color var(--duration-fast) var(--ease-out);
}

.nav :deep(.el-menu-item:hover),
.nav :deep(.el-sub-menu__title:hover) {
  background-color: var(--color-sidebar-bg-light);
  color: var(--color-sidebar-text-active);
}

.nav :deep(.el-menu-item:hover .el-icon),
.nav :deep(.el-sub-menu__title:hover .el-icon) {
  color: var(--color-sidebar-text-active);
}

.nav :deep(.el-menu-item.is-active) {
  background-color: var(--color-sidebar-bg-active);
  color: var(--color-sidebar-text-active);
  font-weight: var(--font-weight-medium);
}

.nav :deep(.el-menu-item.is-active .el-icon) {
  color: var(--color-sidebar-text-active);
}

.nav :deep(.el-menu-item.is-active)::before {
  content: '';
  position: absolute;
  left: 0;
  top: 50%;
  transform: translateY(-50%);
  width: 3px;
  height: 16px;
  border-radius: 0 2px 2px 0;
  background-color: var(--color-sidebar-accent);
}

.nav :deep(.el-menu--inline) {
  background-color: transparent;
}

.nav :deep(.el-sub-menu .el-menu-item) {
  min-width: 0;
  height: 34px;
  line-height: 34px;
  padding-left: 34px !important;
  font-size: 13px;
  color: var(--color-sidebar-text);
}

.nav :deep(.el-sub-menu__icon-arrow) {
  right: 10px;
  font-size: 12px;
  color: var(--color-sidebar-text);
  opacity: 0.6;
}

.nav.el-menu--collapse {
  padding: 0 8px;
}

.nav.el-menu--collapse :deep(.el-menu-item.is-active)::before {
  display: none;
}

.nav.el-menu--collapse :deep(.el-menu-item),
.nav.el-menu--collapse :deep(.el-sub-menu__title) {
  padding: 0 !important;
  display: flex;
  align-items: center;
  justify-content: center;
}

.nav.el-menu--collapse :deep(.el-menu-item .el-icon),
.nav.el-menu--collapse :deep(.el-sub-menu__title .el-icon) {
  margin-right: 0;
}

/* 顶栏 */
.workspace {
  min-width: 0;
}

.header {
  height: var(--header-height);
  flex-shrink: 0;
  display: flex;
  align-items: center;
  gap: var(--spacing-sm);
  padding: 0 var(--spacing-lg);
  background-color: var(--color-white);
  border-bottom: 1px solid var(--color-border-light);
}

.header__left,
.header__right {
  display: flex;
  align-items: center;
  gap: var(--spacing-sm);
  flex-shrink: 0;
  min-width: 0;
}

.header__left {
  max-width: 320px;
}

.header__middle {
  flex: 1;
  min-width: 0;
  height: 100%;
  display: flex;
  align-items: center;
}

.icon-btn {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 32px;
  height: 32px;
  padding: 0;
  border: none;
  border-radius: var(--radius-sm);
  background-color: transparent;
  color: var(--color-text-secondary);
  font-size: 18px;
  cursor: pointer;
  transition: var(--transition-fast);
}

.icon-btn:hover {
  background-color: var(--color-bg-hover);
  color: var(--color-text-primary);
}

.header__divider {
  width: 1px;
  height: 20px;
  background-color: var(--color-border-light);
}

.user-chip {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 4px 8px 4px 4px;
  border: 1px solid transparent;
  border-radius: var(--radius-pill);
  background-color: transparent;
  cursor: pointer;
  transition: var(--transition-fast);
}

.user-chip:hover {
  background-color: var(--color-bg-hover);
  border-color: var(--color-border-light);
}

.user-chip__avatar {
  width: 30px;
  height: 30px;
  flex-shrink: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  border-radius: var(--radius-circle);
  background-color: var(--color-primary-light-5);
  color: var(--color-primary);
  font-size: var(--font-size-small);
  font-weight: var(--font-weight-semibold);
}

.user-chip__name {
  font-size: var(--font-size-small);
  font-weight: var(--font-weight-medium);
  color: var(--color-text-primary);
}

.user-chip__caret {
  font-size: 12px;
  color: var(--color-text-placeholder);
}

/* 主内容区 */
.main {
  flex: 1;
  min-height: 0;
  padding: 0;
  overflow-y: auto;
  background-color: var(--color-bg-page);
}

.page-enter-active,
.page-leave-active {
  transition: opacity var(--duration-base) var(--ease-out),
    transform var(--duration-base) var(--ease-out);
}

.page-enter-from {
  opacity: 0;
  transform: translateY(4px);
}

.page-leave-to {
  opacity: 0;
}
</style>
