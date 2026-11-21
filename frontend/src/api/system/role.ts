import request from '@/utils/request'
import { buildMenuManageTree } from '@/mock/menuManage'
import type { MenuNode } from '@/types/menu'
import type { RolePage, RoleQuery, RoleRecord, RoleSavePayload } from '@/types/role'

const useMock = import.meta.env.VITE_USE_MOCK === 'true'

/* ---------- mock 内存仓库（仅本地预览，刷新页面即还原） ---------- */
let mockSeq = 10
let mockRoles: RoleRecord[] = [
  {
    id: 1, roleName: '超级管理员', roleCode: 'admin',
    roleDesc: '系统内置角色，拥有全部菜单与按钮权限',
    status: true, remark: '系统内置，禁止删除/禁用/改权限',
    updateTime: '2026-11-20 10:00:00'
  },
  {
    id: 2, roleName: '运营专员', roleCode: 'OPS',
    roleDesc: '负责开单与运单日常操作',
    status: true, updateTime: '2026-11-19 16:20:00'
  }
]
const mockRoleMenus = new Map<number, number[]>([[2, [1002, 1201, 1202]]])

function now(): string {
  const d = new Date()
  const pad = (n: number) => String(n).padStart(2, '0')
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())} ${pad(d.getHours())}:${pad(d.getMinutes())}:${pad(d.getSeconds())}`
}

function fail(message: string): never {
  throw { code: 400, message }
}

function allMenuIds(nodes: MenuNode[]): number[] {
  const ids: number[] = []
  const walk = (list: MenuNode[]) => list.forEach((node) => {
    ids.push(node.id)
    if (node.children) walk(node.children)
  })
  walk(nodes)
  return ids
}

/** 与后端 buildComparator 白名单保持一致 */
const MOCK_SORT_FIELDS = new Set([
  'roleName', 'roleCode', 'roleDesc', 'updateByName', 'updateTime', 'status', 'id'
])

function sortMockRows(rows: RoleRecord[], sortBy?: string, sortOrder?: string): RoleRecord[] {
  if (!sortBy || !MOCK_SORT_FIELDS.has(sortBy)) return rows
  const dir = sortOrder === 'desc' ? -1 : 1
  return [...rows].sort((a, b) => {
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
}

function inDateRange(value: string | undefined, start?: string, end?: string): boolean {
  if (!start && !end) return true
  if (!value) return false
  const day = value.slice(0, 10)
  if (start && day < start) return false
  if (end && day > end) return false
  return true
}

function findMock(id: number): RoleRecord {
  const role = mockRoles.find((item) => item.id === id)
  if (!role) fail('角色不存在或已删除')
  return role
}

/* ---------- 对外接口 ---------- */

/** GET /api/v1/system/roles/page —— 服务端筛选 + 排序 + 分页 */
export async function getRolePage(query: RoleQuery): Promise<RolePage> {
  if (useMock) {
    const name = query.roleName?.trim().toLowerCase()
    const code = query.roleCode?.trim().toLowerCase()
    const desc = query.roleDesc?.trim().toLowerCase()
    let rows = mockRoles.filter((row) => {
      if (name && !row.roleName.toLowerCase().includes(name)) return false
      if (code && !row.roleCode.toLowerCase().includes(code)) return false
      if (desc && !(row.roleDesc || '').toLowerCase().includes(desc)) return false
      if (!inDateRange(row.updateTime, query.updateTimeStart, query.updateTimeEnd)) return false
      // 用户表未建：最后修改人筛选整体忽略，与后端降级一致
      return true
    })
    rows = sortMockRows(rows, query.sortBy, query.sortOrder)
    const pageNum = query.pageNum && query.pageNum > 0 ? query.pageNum : 1
    const pageSize = query.pageSize && query.pageSize > 0 ? query.pageSize : 20
    return {
      records: rows.slice((pageNum - 1) * pageSize, pageNum * pageSize),
      total: rows.length
    }
  }
  return request({ url: '/v1/system/roles/page', method: 'get', params: query })
}

/** GET /api/v1/system/roles/{id} */
export async function getRoleDetail(id: number): Promise<RoleRecord> {
  if (useMock) return { ...findMock(id) }
  return request({ url: `/v1/system/roles/${id}`, method: 'get' })
}

/** POST /api/v1/system/roles */
export async function createRole(payload: RoleSavePayload): Promise<{ id: number }> {
  if (useMock) {
    if (mockRoles.some((item) => item.roleName === payload.roleName)) {
      fail(`角色名称已存在：${payload.roleName}`)
    }
    if (mockRoles.some((item) => item.roleCode === payload.roleCode)) {
      fail(`角色编码已存在：${payload.roleCode}`)
    }
    const role: RoleRecord = {
      id: mockSeq++,
      roleName: payload.roleName,
      roleCode: payload.roleCode,
      roleDesc: payload.roleDesc,
      status: payload.status,
      remark: payload.remark,
      updateTime: now()
    }
    mockRoles.push(role)
    return { id: role.id }
  }
  return request({ url: '/v1/system/roles', method: 'post', data: payload })
}

/** PUT /api/v1/system/roles/{id} */
export async function updateRole(id: number, payload: RoleSavePayload): Promise<void> {
  if (useMock) {
    const role = findMock(id)
    if (role.roleCode !== payload.roleCode) fail('角色编码创建后不可修改')
    if (role.roleCode === 'admin' && !payload.status) fail('超级管理员角色不允许禁用')
    if (mockRoles.some((item) => item.id !== id && item.roleName === payload.roleName)) {
      fail(`角色名称已存在：${payload.roleName}`)
    }
    Object.assign(role, {
      roleName: payload.roleName,
      roleDesc: payload.roleDesc,
      status: payload.status,
      remark: payload.remark,
      updateTime: now()
    })
    return
  }
  return request({ url: `/v1/system/roles/${id}`, method: 'put', data: payload })
}

/** DELETE /api/v1/system/roles/{id} */
export async function deleteRole(id: number): Promise<void> {
  if (useMock) {
    const role = findMock(id)
    if (role.roleCode === 'admin') fail('超级管理员角色不允许删除')
    mockRoles = mockRoles.filter((item) => item.id !== id)
    mockRoleMenus.delete(id)
    return
  }
  return request({ url: `/v1/system/roles/${id}`, method: 'delete' })
}

/** GET /api/v1/system/roles/options —— 启用角色下拉（用户管理绑定角色） */
export async function getRoleOptions(): Promise<RoleRecord[]> {
  if (useMock) return mockRoles.filter((item) => item.status)
  return request({ url: '/v1/system/roles/options', method: 'get' })
}

/** GET /api/v1/system/roles/{id}/menus —— 已授权菜单ID（admin 返回全量） */
export async function getRoleMenuIds(id: number): Promise<number[]> {
  if (useMock) {
    const role = findMock(id)
    return role.roleCode === 'admin'
      ? allMenuIds(buildMenuManageTree())
      : [...(mockRoleMenus.get(id) || [])]
  }
  return request({ url: `/v1/system/roles/${id}/menus`, method: 'get' })
}

/** PUT /api/v1/system/roles/{id}/menus —— 整体覆盖保存 */
export async function saveRoleMenus(id: number, menuIds: number[]): Promise<void> {
  if (useMock) {
    const role = findMock(id)
    if (role.roleCode === 'admin') fail('超级管理员角色不允许修改权限')
    mockRoleMenus.set(id, [...menuIds])
    return
  }
  return request({ url: `/v1/system/roles/${id}/menus`, method: 'put', data: { menuIds } })
}
