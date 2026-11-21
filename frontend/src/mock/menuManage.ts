import { internalMenus } from './menus'
import type { MenuNode } from '@/types/menu'

/**
 * 菜单管理页使用的全量树（含按钮）
 * - internalMenus 已包含预开单，共 40 个菜单
 * - 按钮示例用于验证 type=3 的展示与编辑
 * 后端就绪后由 GET /api/v1/system/menus/tree 返回同结构数据。
 */

/** 按钮样例：id / 父菜单 / 名称 / 权限标识 / 排序 */
const BUTTON_SEEDS: Array<[number, number, string, string, number]> = [
  [3101, 1102, '新增用户', 'system:user:add', 10],
  [3102, 1102, '编辑用户', 'system:user:edit', 20],
  [3103, 1102, '删除用户', 'system:user:delete', 30],
  [3104, 1105, '新增目录', 'system:menu:dir:add', 10],
  [3105, 1105, '新增菜单', 'system:menu:add', 20],
  [3106, 1105, '新增按钮', 'system:menu:btn:add', 30],
  [3107, 1105, '编辑菜单', 'system:menu:edit', 40],
  [3108, 1105, '删除菜单', 'system:menu:delete', 50]
]

function cloneNode(node: MenuNode, parentId: number): MenuNode {
  return {
    ...node,
    parentId,
    keepAlive: node.keepAlive ?? true,
    affix: node.affix ?? false,
    updateTime: node.updateTime || '2026-11-20 09:30',
    children: node.children?.map((child) => cloneNode(child, node.id))
  }
}

function sortTree(nodes: MenuNode[]): MenuNode[] {
  return nodes
    .sort((a, b) => a.sortOrder - b.sortOrder || a.id - b.id)
    .map((node) => (node.children ? { ...node, children: sortTree(node.children) } : node))
}

export function buildMenuManageTree(): MenuNode[] {
  const tree = internalMenus.map((node) => cloneNode(node, 0))

  BUTTON_SEEDS.forEach(([id, parentId, menuName, perms, sortOrder]) => {
    const parent = findNode(tree, parentId)
    if (!parent) return
    parent.children = parent.children || []
    parent.children.push({
      id,
      parentId,
      menuName,
      menuType: 3,
      perms,
      terminalType: parent.terminalType,
      sortOrder,
      visible: true,
      status: true,
      updateTime: '2026-11-20 09:30'
    })
  })

  return sortTree(tree)
}

function findNode(nodes: MenuNode[], id: number): MenuNode | undefined {
  for (const node of nodes) {
    if (node.id === id) return node
    const found = node.children ? findNode(node.children, id) : undefined
    if (found) return found
  }
  return undefined
}
