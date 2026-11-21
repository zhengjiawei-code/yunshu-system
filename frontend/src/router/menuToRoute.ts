import type { RouteRecordRaw } from 'vue-router'
import type { MenuNode } from '@/types/menu'

/**
 * 菜单树 → 扁平路由（规范 5.2）
 * - 目录与菜单都挂到 BasicLayout.children 下，路径直接使用绝对路径
 * - 目录本身不渲染页面，redirect 到第一个叶子菜单
 * - 组件缺失时统一落到占位页，禁止白屏
 */
const viewModules = import.meta.glob('../views/**/*.vue')
const Placeholder = () => import('@/views/_placeholder/index.vue')

function resolveComponent(component?: string) {
  if (!component) return Placeholder
  const loader = viewModules[`../views/${component}.vue`]
  if (!loader) {
    console.warn(`[router] 组件 ${component} 不存在，已使用占位页`)
    return Placeholder
  }
  return loader
}

function firstLeafPath(node: MenuNode): string | undefined {
  if (node.menuType === 2) return node.routePath
  for (const child of node.children || []) {
    const path = firstLeafPath(child)
    if (path) return path
  }
  return undefined
}

export function menuToRoutes(menus: MenuNode[], parents: string[] = []): RouteRecordRaw[] {
  const routes: RouteRecordRaw[] = []

  for (const menu of menus) {
    if (menu.menuType === 3 || !menu.status) continue
    const nextParents = [...parents, menu.menuName]

    if (menu.menuType === 1) {
      const children = menuToRoutes(menu.children || [], nextParents)
      routes.push(...children)
      if (menu.routePath) {
        routes.push({
          path: menu.routePath,
          name: `Dir_${menu.id}`,
          redirect: firstLeafPath(menu),
          meta: { title: menu.menuName, parents },
          children: []
        })
      }
      continue
    }

    if (!menu.routePath) continue
    routes.push({
      path: menu.routePath,
      name: `Menu_${menu.id}`,
      component: resolveComponent(menu.component),
      meta: {
        title: menu.menuName,
        icon: menu.icon,
        affix: !!menu.affix,
        keepAlive: menu.keepAlive !== false,
        menuId: menu.id,
        terminalType: menu.terminalType,
        parents,
        componentPath: menu.component
      }
    })
  }

  return routes
}
