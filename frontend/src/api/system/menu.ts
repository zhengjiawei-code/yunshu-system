import request from '@/utils/request'
import { buildMenuManageTree } from '@/mock/menuManage'
import type { MenuNode, TerminalType } from '@/types/menu'
import { cloneTree, findNode, findParent, maxSort } from '@/utils/tree'

/** 保存载荷：三类节点共用，按 menuType 裁剪字段 */
export interface MenuSavePayload {
  parentId: number
  menuType: 1 | 2 | 3
  menuName: string
  routePath?: string
  component?: string
  perms?: string
  icon?: string
  redirect?: string
  terminalType?: TerminalType
  keepAlive?: boolean
  affix?: boolean
  sortOrder: number
  visible: boolean
  status: boolean
  remark?: string
}

const useMock = import.meta.env.VITE_USE_MOCK === 'true'

/* ---------- mock 内存仓库（仅本地预览，刷新页面即还原） ---------- */
let mockTree = buildMenuManageTree()
let mockSeq = 3200

function now() {
  const d = new Date()
  const pad = (n: number) => String(n).padStart(2, '0')
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())} ${pad(d.getHours())}:${pad(d.getMinutes())}`
}

function fail(message: string): never {
  throw { code: 400, message }
}

function sortRecursive(nodes: MenuNode[]): MenuNode[] {
  return nodes
    .sort((a, b) => a.sortOrder - b.sortOrder || a.id - b.id)
    .map((node) => (node.children ? { ...node, children: sortRecursive(node.children) } : node))
}

function siblingsOf(parentId: number): MenuNode[] {
  if (!parentId) return mockTree
  return findNode(mockTree, parentId)?.children || []
}

function createMock(payload: MenuSavePayload) {
  const siblings = siblingsOf(payload.parentId)
  const node: MenuNode = {
    id: mockSeq++,
    parentId: payload.parentId,
    menuName: payload.menuName,
    menuType: payload.menuType,
    routePath: payload.routePath,
    component: payload.component,
    perms: payload.perms,
    icon: payload.icon,
    terminalType: payload.terminalType || 'PC',
    sortOrder: payload.sortOrder || maxSort(siblings) + 10,
    visible: payload.visible,
    status: payload.status,
    keepAlive: payload.keepAlive,
    affix: payload.affix,
    remark: payload.remark,
    updateTime: now(),
    children: undefined
  }
  if (!payload.parentId) {
    mockTree.push(node)
  } else {
    const parent = findNode(mockTree, payload.parentId)
    if (!parent) fail('上级节点不存在')
    parent.children = parent.children || []
    parent.children.push(node)
  }
  return { id: node.id }
}

function updateMock(id: number, payload: MenuSavePayload) {
  const node = findNode(mockTree, id)
  if (!node) fail('菜单不存在')

  Object.assign(node, {
    menuName: payload.menuName,
    routePath: payload.routePath,
    component: payload.component,
    perms: payload.perms,
    icon: payload.icon,
    terminalType: payload.terminalType || node.terminalType,
    sortOrder: payload.sortOrder,
    visible: payload.visible,
    status: payload.status,
    keepAlive: payload.keepAlive,
    affix: payload.affix,
    remark: payload.remark,
    updateTime: now()
  })

  // 换父级
  if (node.parentId !== payload.parentId) {
    const oldParent = findParent(mockTree, id)
    if (oldParent?.children) {
      oldParent.children = oldParent.children.filter((child) => child.id !== id)
    } else {
      mockTree = mockTree.filter((child) => child.id !== id)
    }
    if (!payload.parentId) {
      mockTree.push(node)
    } else {
      const newParent = findNode(mockTree, payload.parentId)
      if (!newParent) fail('上级节点不存在')
      newParent.children = newParent.children || []
      newParent.children.push(node)
    }
    node.parentId = payload.parentId
  }
}

/* ---------- 对外接口（与方案 10.7 一一对应） ---------- */

/** 服务端排序的 mock 等价实现：只排同级兄弟节点，层级与缩进不变 */
function sortSiblings(nodes: MenuNode[], sortBy: string, desc: boolean): MenuNode[] {
  const dir = desc ? -1 : 1
  return [...nodes]
    .sort((a, b) => {
      const av = (a as unknown as Record<string, unknown>)[sortBy]
      const bv = (b as unknown as Record<string, unknown>)[sortBy]
      if (av == null && bv == null) return a.id - b.id
      if (av == null) return 1
      if (bv == null) return -1
      if (typeof av === 'string' && typeof bv === 'string') {
        const diff = av.localeCompare(bv, 'zh-CN')
        return diff === 0 ? a.id - b.id : diff * dir
      }
      const an = Number(av)
      const bn = Number(bv)
      return an === bn ? a.id - b.id : (an < bn ? -1 : 1) * dir
    })
    .map((node) => (node.children ? { ...node, children: sortSiblings(node.children, sortBy, desc) } : node))
}

/**
 * GET /api/v1/system/menus/tree —— 管理页全量树（含禁用/隐藏/按钮）
 * sortBy/sortOrder 为同级排序参数，与后端 /tree 接口一一对应。
 */
export async function getMenuTree(params?: {
  sortBy?: string
  sortOrder?: 'asc' | 'desc'
}): Promise<MenuNode[]> {
  if (useMock) {
    mockTree = sortRecursive(mockTree)
    if (!params?.sortBy) return cloneTree(mockTree)
    return cloneTree(sortSiblings(mockTree, params.sortBy, params.sortOrder === 'desc'))
  }
  return request({ url: '/v1/system/menus/tree', method: 'get', params })
}

/** GET /api/v1/system/menus/{id} */
export async function getMenuDetail(id: number): Promise<MenuNode> {
  if (useMock) {
    const node = findNode(mockTree, id)
    if (!node) fail('菜单不存在')
    return cloneTree([node])[0]
  }
  return request({ url: `/v1/system/menus/${id}`, method: 'get' })
}

/** POST /api/v1/system/menus */
export async function createMenu(payload: MenuSavePayload): Promise<{ id: number }> {
  if (useMock) return createMock(payload)
  return request({ url: '/v1/system/menus', method: 'post', data: payload })
}

/** PUT /api/v1/system/menus/{id} */
export async function updateMenu(id: number, payload: MenuSavePayload): Promise<void> {
  if (useMock) {
    updateMock(id, payload)
    return
  }
  return request({ url: `/v1/system/menus/${id}`, method: 'put', data: payload })
}

/** DELETE /api/v1/system/menus/{id} */
export async function deleteMenu(id: number): Promise<void> {
  if (useMock) {
    const node = findNode(mockTree, id)
    if (!node) fail('菜单不存在')
    if (node.children?.length) fail('存在子节点，请先删除子节点')
    const parent = findParent(mockTree, id)
    if (parent?.children) {
      parent.children = parent.children.filter((child) => child.id !== id)
    } else {
      mockTree = mockTree.filter((child) => child.id !== id)
    }
    return
  }
  return request({ url: `/v1/system/menus/${id}`, method: 'delete' })
}

/** PUT /api/v1/system/menus/sort —— 二期拖拽排序使用 */
export async function sortMenus(ids: number[]): Promise<void> {
  if (useMock) return
  return request({ url: '/v1/system/menus/sort', method: 'put', data: { ids } })
}

/** 前端本地能力：可选组件列表（不调后端） */
const viewModules = import.meta.glob('../../views/**/*.vue')

export function getViewComponents(): string[] {
  return Object.keys(viewModules)
    .map((key) => key.replace('../../views/', '').replace(/\.vue$/, ''))
    .filter((key) => !key.startsWith('_') && !key.startsWith('error/'))
    .sort()
}
