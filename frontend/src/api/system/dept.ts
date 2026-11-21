import request from '@/utils/request'
import type { DeptNode, DeptPage, DeptQuery, DeptRow, DeptSavePayload, DeptType } from '@/types/dept'

const useMock = import.meta.env.VITE_USE_MOCK === 'true'

/* ---------- mock 内存仓库（仅本地预览，刷新页面即还原） ---------- */
let mockSeq = 200
let mockTree: DeptNode[] = [
  {
    id: 1, parentId: 0, deptName: '总部', deptCode: 'HQ', deptType: 1, deptNature: 1,
    isLeaf: false,
    allowShip: true, allowArrival: true, allowPrepaidUnpaid: false, allowCollectUnpaidSign: false,
    leader: '张伟', phone: '021-58880000', address: '上海市青浦区华新镇华隆路 1 号',
    sortOrder: 10, status: true, remark: '系统内置总部，不可删除、不可禁用、不可移动、类型不可修改',
    updateTime: '2026-11-20 10:00:00',
    children: [
      {
        id: 2, parentId: 1, deptName: '华东公司', deptCode: 'EAST', deptType: 2, deptNature: 1,
        isLeaf: false,
        allowShip: true, allowArrival: true, allowPrepaidUnpaid: true, allowCollectUnpaidSign: false,
        leader: '李明', phone: '021-58881001', address: '上海市青浦区华徐公路 88 号',
        sortOrder: 10, status: true, remark: '华东区域公司',
        updateTime: '2026-11-19 16:20:00',
        children: [
          {
            id: 21, parentId: 2, deptName: '上海分拨中心', deptCode: 'SH_FB', deptType: 4, deptNature: 1,
            isLeaf: false,
            allowShip: true, allowArrival: true, allowPrepaidUnpaid: false, allowCollectUnpaidSign: true,
            leader: '王强', phone: '021-58882001', address: '上海市嘉定区安亭镇物流园 6 号库',
            sortOrder: 10, status: true, updateTime: '2026-11-18 09:35:00',
            children: [
              {
                id: 211, parentId: 21, deptName: '浦东中转部', deptCode: 'SH_PD', deptType: 3, deptNature: 1,
                isLeaf: false,
                allowShip: true, allowArrival: false, allowPrepaidUnpaid: false, allowCollectUnpaidSign: false,
                leader: '赵敏', phone: '021-58882101', sortOrder: 10, status: true,
                updateTime: '2026-11-17 14:02:00'
              },
              {
                id: 212, parentId: 21, deptName: '浦东网点', deptCode: 'SH_PD_WD', deptType: 5, deptNature: 2,
                isLeaf: true,
                allowShip: true, allowArrival: true, allowPrepaidUnpaid: false, allowCollectUnpaidSign: false,
                leader: '周涛', phone: '13800138001', sortOrder: 20, status: true,
                updateTime: '2026-11-16 11:48:00'
              }
            ]
          },
          {
            id: 22, parentId: 2, deptName: '杭州网点', deptCode: 'HZ_WD', deptType: 5, deptNature: 3,
            isLeaf: true,
            allowShip: true, allowArrival: true, allowPrepaidUnpaid: false, allowCollectUnpaidSign: false,
            leader: '陈磊', phone: '13900139002', sortOrder: 20, status: false,
            remark: '暂停收发货', updateTime: '2026-11-15 15:30:00'
          },
          {
            id: 23, parentId: 2, deptName: '运营部', deptCode: 'EAST_OP', deptType: 3, deptNature: 1,
            isLeaf: false,
            allowShip: false, allowArrival: false, allowPrepaidUnpaid: false, allowCollectUnpaidSign: false,
            leader: '刘洋', phone: '021-58883001', sortOrder: 30, status: true,
            updateTime: '2026-11-14 10:12:00'
          }
        ]
      },
      {
        id: 3, parentId: 1, deptName: '华南公司', deptCode: 'SOUTH', deptType: 2, deptNature: 2,
        isLeaf: false,
        allowShip: true, allowArrival: true, allowPrepaidUnpaid: false, allowCollectUnpaidSign: true,
        leader: '黄勇', phone: '020-38880001', address: '广州市白云区物流大道 18 号',
        sortOrder: 20, status: true, updateTime: '2026-11-13 17:05:00',
        children: [
          {
            id: 31, parentId: 3, deptName: '深圳网点', deptCode: 'SZ_WD', deptType: 5, deptNature: 3,
            isLeaf: false,
            allowShip: true, allowArrival: true, allowPrepaidUnpaid: false, allowCollectUnpaidSign: false,
            leader: '吴静', phone: '13700137001', sortOrder: 10, status: true,
            updateTime: '2026-11-12 09:20:00'
          }
        ]
      },
      {
        id: 4, parentId: 1, deptName: '广州分拨中心', deptCode: 'GZ_FB', deptType: 4, deptNature: 1,
        isLeaf: false,
        allowShip: true, allowArrival: true, allowPrepaidUnpaid: false, allowCollectUnpaidSign: false,
        leader: '郑凯', phone: '020-38881001', sortOrder: 30, status: true,
        updateTime: '2026-11-11 13:40:00'
      }
    ]
  }
]

function clone(nodes: DeptNode[]): DeptNode[] {
  return nodes.map((node) => ({ ...node, children: node.children ? clone(node.children) : undefined }))
}

function findDept(nodes: DeptNode[], id: number): DeptNode | undefined {
  for (const node of nodes) {
    if (node.id === id) return node
    const found = node.children ? findDept(node.children, id) : undefined
    if (found) return found
  }
  return undefined
}

function findParentOf(nodes: DeptNode[], id: number): DeptNode | undefined {
  for (const node of nodes) {
    if (node.children?.some((child) => child.id === id)) return node
    const found = node.children ? findParentOf(node.children, id) : undefined
    if (found) return found
  }
  return undefined
}

function collectIds(node: DeptNode): number[] {
  const ids = [node.id]
  node.children?.forEach((child) => ids.push(...collectIds(child)))
  return ids
}

function siblingsOf(parentId: number): DeptNode[] {
  if (!parentId) return mockTree
  return findDept(mockTree, parentId)?.children || []
}

function fail(message: string): never {
  throw { code: 400, message }
}

function now(): string {
  const d = new Date()
  const pad = (n: number) => String(n).padStart(2, '0')
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())} ${pad(d.getHours())}:${pad(d.getMinutes())}:${pad(d.getSeconds())}`
}

function applyMock(id: number, payload: DeptSavePayload) {
  const node = findDept(mockTree, id)
  if (!node) fail('机构不存在')
  Object.assign(node, {
    deptName: payload.deptName,
    deptType: payload.deptType,
    deptNature: payload.deptType === 1 ? 1 : payload.deptNature,
    isLeaf: payload.deptType === 5 ? payload.isLeaf ?? node.isLeaf : false,
    allowShip: payload.allowShip ?? node.allowShip,
    allowArrival: payload.allowArrival ?? node.allowArrival,
    allowPrepaidUnpaid: payload.allowPrepaidUnpaid ?? node.allowPrepaidUnpaid,
    allowCollectUnpaidSign: payload.allowCollectUnpaidSign ?? node.allowCollectUnpaidSign,
    leader: payload.leader,
    phone: payload.phone,
    address: payload.address,
    sortOrder: payload.sortOrder,
    status: payload.status,
    remark: payload.remark,
    updateTime: now()
  })

  if (node.parentId !== payload.parentId) {
    const oldParent = findParentOf(mockTree, id)
    if (oldParent?.children) oldParent.children = oldParent.children.filter((child) => child.id !== id)
    const newParent = findDept(mockTree, payload.parentId)
    if (!newParent) fail('上级机构不存在')
    newParent.children = newParent.children || []
    newParent.children.push(node)
    node.parentId = payload.parentId
  }
}

/* ---------- 对外接口（与 org-management-fields.md 第 6 节一一对应） ---------- */

/** 平铺收集某机构的全部后代，附带上级名称与完整路径（mock 用） */
function collectRows(root: DeptNode): DeptRow[] {
  const rows: DeptRow[] = []
  const walk = (node: DeptNode, parentName: string, parentPath: string) => {
    const children = [...(node.children || [])].sort(
      (a, b) => (a.sortOrder || 0) - (b.sortOrder || 0) || a.id - b.id
    )
    children.forEach((child) => {
      const path = `${parentPath} / ${child.deptName}`
      rows.push({
        ...child,
        children: undefined,
        parentName,
        parentPath: path,
        // mock 无创建时间字段，按 id 派生一个稳定日期供日期区间筛选演示
        createTime: child.createTime || mockCreateTime(child.id)
      })
      walk(child, child.deptName, path)
    })
  }
  walk(root, root.deptName, root.deptName)
  return rows
}

function mockCreateTime(id: number): string {
  const day = String(10 + (id % 9)).padStart(2, '0')
  return `2026-11-${day} 09:30:00`
}

/** 日期区间按天比较：入参 yyyy-MM-dd，行值为 yyyy-MM-dd HH:mm:ss */
function inMockDateRange(value: string | undefined, start?: string, end?: string): boolean {
  if (!start && !end) return true
  if (!value) return false
  const day = value.slice(0, 10)
  if (start && day < start) return false
  if (end && day > end) return false
  return true
}

/** 与后端 buildComparator 白名单保持一致 */
const MOCK_SORT_FIELDS = new Set([
  'deptName', 'parentName', 'deptType', 'deptNature', 'deptCode', 'leader', 'phone', 'isLeaf', 'status',
  'createByName', 'updateByName', 'createTime', 'updateTime', 'sortOrder'
])

/** 服务端排序的 mock 等价实现：字符串按中文比较，布尔/数字按语义比较，null 永远排最后 */
function sortMockRows(rows: DeptRow[], sortBy?: string, sortOrder?: string): DeptRow[] {
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

/** GET /api/v1/system/depts/page —— 服务端筛选 + 排序 + 分页 */
export async function getDeptPage(query: DeptQuery): Promise<DeptPage> {
  if (useMock) {
    const root = findDept(mockTree, query.rootId ?? 1)
    let rows = root ? collectRows(root) : []
    const name = query.deptName?.trim().toLowerCase()
    const code = query.deptCode?.trim().toLowerCase()
    const leader = query.leader?.trim().toLowerCase()
    const phone = query.phone?.trim().toLowerCase()
    rows = rows.filter((row) => {
      if (name && !row.deptName.toLowerCase().includes(name)) return false
      if (code && !row.deptCode.toLowerCase().includes(code)) return false
      if (leader && !(row.leader || '').toLowerCase().includes(leader)) return false
      if (phone && !(row.phone || '').toLowerCase().includes(phone)) return false
      if (query.deptType != null && row.deptType !== query.deptType) return false
      if (query.deptNature != null && row.deptNature !== query.deptNature) return false
      if (query.parentId != null && row.parentId !== query.parentId) return false
      if (query.isLeaf != null && !!row.isLeaf !== query.isLeaf) return false
      if (query.status != null && row.status !== query.status) return false
      if (!inMockDateRange(row.createTime, query.createTimeStart, query.createTimeEnd)) return false
      if (!inMockDateRange(row.updateTime, query.updateTimeStart, query.updateTimeEnd)) return false
      // 用户表未建：创建人/修改人筛选整体忽略，与后端降级一致
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
  return request({ url: '/v1/system/depts/page', method: 'get', params: query })
}

/** GET /api/v1/system/depts/tree —— 全量机构树（含禁用） */
export async function getDeptTree(): Promise<DeptNode[]> {
  if (useMock) return clone(mockTree)
  return request({ url: '/v1/system/depts/tree', method: 'get' })
}

/** GET /api/v1/system/depts/{id} */
export async function getDeptDetail(id: number): Promise<DeptNode> {
  if (useMock) {
    const node = findDept(mockTree, id)
    if (!node) fail('机构不存在')
    return clone([node])[0]
  }
  return request({ url: `/v1/system/depts/${id}`, method: 'get' })
}

/** POST /api/v1/system/depts */
export async function createDept(payload: DeptSavePayload): Promise<{ id: number }> {
  if (useMock) {
    if (!findDept(mockTree, payload.parentId)) fail('上级机构不存在')
    if (payload.deptType === 1) fail('系统只允许一个总部，不能新增总部')
    const siblings = siblingsOf(payload.parentId)
    if (siblings.some((item) => item.deptName === payload.deptName)) {
      fail(`同一上级下已存在同名机构：${payload.deptName}`)
    }
    let codeExists = false
    const walk = (nodes: DeptNode[]) => nodes.forEach((item) => {
      if (item.deptCode.toUpperCase() === payload.deptCode.toUpperCase()) codeExists = true
      if (item.children) walk(item.children)
    })
    walk(mockTree)
    if (codeExists) fail(`机构编码已存在：${payload.deptCode}`)

    const node: DeptNode = {
      id: mockSeq++, parentId: payload.parentId, deptName: payload.deptName,
      deptCode: payload.deptCode, deptType: payload.deptType as DeptType,
      deptNature: payload.deptNature,
      isLeaf: payload.deptType === 5 && !!payload.isLeaf,
      allowShip: payload.allowShip ?? true, allowArrival: payload.allowArrival ?? true,
      allowPrepaidUnpaid: payload.allowPrepaidUnpaid ?? false,
      allowCollectUnpaidSign: payload.allowCollectUnpaidSign ?? false,
      leader: payload.leader, phone: payload.phone, address: payload.address,
      sortOrder: payload.sortOrder, status: payload.status, remark: payload.remark,
      updateTime: now()
    }
    const parent = findDept(mockTree, payload.parentId)
    if (!parent) fail('上级机构不存在')
    parent.children = parent.children || []
    parent.children.push(node)
    return { id: node.id }
  }
  return request({ url: '/v1/system/depts', method: 'post', data: payload })
}

/** PUT /api/v1/system/depts/{id} */
export async function updateDept(id: number, payload: DeptSavePayload): Promise<void> {
  if (useMock) {
    const node = findDept(mockTree, id)
    if (!node) fail('机构不存在')
    if (node.deptCode !== payload.deptCode) fail('机构编码创建后不可修改')
    if (id === 1 && payload.parentId !== 0) fail('系统只允许一个总部，不能移动总部')
    if (id === 1 && payload.deptType !== 1) fail('内置总部的机构类型不可修改')
    if (id === 1 && !payload.status) fail('内置总部不可禁用')
    if (id === 1 && payload.deptNature !== 1) fail('内置总部的机构性质固定为自营，不可修改')
    if (payload.isLeaf && payload.deptType !== 5) fail('仅网点可标记为末端机构')
    if (payload.parentId && collectIds(node).includes(payload.parentId)) {
      fail('上级机构不能是自己或自己的子机构')
    }
    const siblings = siblingsOf(payload.parentId).filter((item) => item.id !== id)
    if (siblings.some((item) => item.deptName === payload.deptName)) {
      fail(`同一上级下已存在同名机构：${payload.deptName}`)
    }
    applyMock(id, payload)
    return
  }
  return request({ url: `/v1/system/depts/${id}`, method: 'put', data: payload })
}

/** DELETE /api/v1/system/depts/{id} */
export async function deleteDept(id: number): Promise<void> {
  if (useMock) {
    if (id === 1) fail('内置总部不允许删除')
    const node = findDept(mockTree, id)
    if (!node) fail('机构不存在')
    if (node.children?.length) fail('该机构下存在子机构，请先删除子机构')
    const parent = findParentOf(mockTree, id)
    if (parent?.children) {
      parent.children = parent.children.filter((child) => child.id !== id)
    } else {
      mockTree = mockTree.filter((child) => child.id !== id)
    }
    return
  }
  return request({ url: `/v1/system/depts/${id}`, method: 'delete' })
}

/** PUT /api/v1/system/depts/sort —— 二期拖拽排序使用 */
export async function sortDepts(ids: number[]): Promise<void> {
  if (useMock) return
  return request({ url: '/v1/system/depts/sort', method: 'put', data: { ids } })
}

/** GET /api/v1/system/depts/options —— 精简树，供用户/角色等选择器使用 */
export async function getDeptOptions(type?: DeptType): Promise<DeptNode[]> {
  if (useMock) return clone(mockTree)
  return request({ url: '/v1/system/depts/options', method: 'get', params: type ? { type } : undefined })
}
