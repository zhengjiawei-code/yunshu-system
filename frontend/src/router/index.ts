import { createRouter, createWebHistory, type RouteRecordRaw } from 'vue-router'
import BasicLayout from '@/layouts/BasicLayout.vue'

/**
 * 静态路由（规范 5.1）
 * /dashboard 为固定 affix 首页，不占菜单表（D1 已定）
 */
export const constantRoutes: RouteRecordRaw[] = [
  {
    path: '/login',
    name: 'Login',
    component: () => import('@/views/login/index.vue'),
    meta: { title: '登录', whitelist: true }
  },
  {
    path: '/404',
    name: 'NotFoundPage',
    component: () => import('@/views/error/404.vue'),
    meta: { title: '页面不存在', whitelist: true }
  },
  {
    path: '/',
    name: 'Root',
    component: BasicLayout,
    redirect: '/dashboard',
    children: [
      {
        path: 'dashboard',
        name: 'Dashboard',
        component: () => import('@/views/dashboard/index.vue'),
        meta: { title: '首页', icon: 'HomeFilled', affix: true, parents: [] }
      },
      {
        path: 'operation/waybill/:id/amend',
        name: 'OperationWaybillAmend',
        component: () => import('@/views/operation/waybill/amend.vue'),
        meta: { title: '运单改单', parents: ['运营管理'] }
      },
      {
        path: 'operation/waybill/:id',
        name: 'OperationWaybillDetail',
        component: () => import('@/views/operation/waybill/detail.vue'),
        meta: { title: '运单详情', parents: ['运营管理'] }
      },
      {
        // 车次配载页（F2 实现，当前挂占位页，保证创建车次后跳转可用）
        path: 'transport/loading/detail',
        name: 'TransportLoadingDetail',
        component: () => import('@/views/_placeholder/index.vue'),
        meta: { title: '车次配载', parents: ['运输管理'] }
      }
    ]
  },
  {
    path: '/:pathMatch(.*)*',
    name: 'CatchAll',
    component: () => import('@/views/error/404.vue'),
    // 不能标记 whitelist：直链首次进入时动态路由尚未注册，会先命中兜底路由；
    // 若放行则守卫不会加载菜单，导致已登录用户直链访问一律 404。
    meta: { title: '页面不存在' }
  }
]

const router = createRouter({
  history: createWebHistory(),
  routes: constantRoutes,
  scrollBehavior: () => ({ top: 0 })
})

export default router
