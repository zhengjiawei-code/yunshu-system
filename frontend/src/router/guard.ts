import type { Router } from 'vue-router'
import { ElMessage } from 'element-plus'
import { useUserStore } from '@/stores/user'
import { menuToRoutes } from './menuToRoute'

const APP_TITLE = import.meta.env.VITE_APP_TITLE || '运输管理系统'

/**
 * 全局守卫（规范 5.3）
 * 1. 白名单直接放行
 * 2. 无 token → /login?redirect=
 * 3. 已登录未加载菜单 → 拉取 auth/info，生成并挂载动态路由
 * 4. 设置页面标题
 */
export function setupRouterGuard(router: Router) {
  router.beforeEach(async (to) => {
    const userStore = useUserStore()

    if (to.meta.whitelist) {
      if (to.path === '/login' && userStore.token) return { path: '/' }
      return true
    }

    if (!userStore.token) {
      return { path: '/login', query: { redirect: to.fullPath } }
    }

    if (!userStore.loaded) {
      try {
        await userStore.fetchAuthInfo()
        menuToRoutes(userStore.menus).forEach((route) => router.addRoute('Root', route))
        // 动态路由挂载后重新匹配，不能用 next() 直接放行
        return { path: to.path, query: to.query, hash: to.hash, replace: true }
      } catch (error) {
        userStore.logout()
        ElMessage.error('获取用户权限失败，请重新登录')
        return { path: '/login', query: { redirect: to.fullPath } }
      }
    }

    return true
  })

  router.afterEach((to) => {
    const title = to.meta.title as string | undefined
    document.title = title ? `${title} - ${APP_TITLE}` : APP_TITLE
  })
}
