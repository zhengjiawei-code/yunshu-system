/** 用户类型：1 内部员工 / 2 客户 */
export type UserType = 1 | 2

/** 数据范围：1 全部 / 3 本机构 / 4 本机构及以下 / 5 仅本人（2 预留未实现） */
export type DataScope = 1 | 3 | 4 | 5

/** 用户记录：字段与后端 UserVO 对齐 */
export interface UserRecord {
  id: number
  username: string
  realName: string
  phone?: string
  email?: string
  deptId?: number
  deptName?: string
  /** 可登录机构名称：主机构在前，客户为空 */
  authOrgNames: string[]
  authOrgIds: number[]
  roleIds: number[]
  roleNames: string[]
  /** 额外授权条数（仅统计直接行） */
  grantedCount: number
  /** 显式禁止条数（仅统计直接行） */
  deniedCount: number
  status: boolean
  userType: UserType
  dataScope: DataScope
  remark?: string
  createBy?: number
  createTime?: string
  updateBy?: number
  updateByName?: string
  updateTime?: string
}

/** 用户列表查询参数：筛选、排序与分页均走服务端 */
export interface UserQuery {
  username?: string
  realName?: string
  phone?: string
  email?: string
  deptId?: number | number[]
  roleId?: number | number[]
  hasDirectPerm?: boolean | boolean[]
  status?: boolean | boolean[]
  updateByName?: string
  updateTimeStart?: string
  updateTimeEnd?: string
  filterModes?: string
  sortBy?: string
  sortOrder?: 'asc' | 'desc'
  pageNum?: number
  pageSize?: number
}

export interface UserPage {
  records: UserRecord[]
  total: number
}

/** 新增/编辑载荷：用户名创建后不可修改；密码只在新增时提交 */
export interface UserSavePayload {
  username: string
  password?: string
  realName: string
  phone?: string
  email?: string
  userType: UserType
  deptId?: number
  authOrgIds?: number[]
  roleIds: number[]
  status: boolean
  dataScope?: DataScope
  remark?: string
}

/** 直授回显：角色已含 / 额外授权 / 显式禁止 */
export interface UserPermInfo {
  roleMenuIds: number[]
  grantedIds: number[]
  deniedIds: number[]
}
