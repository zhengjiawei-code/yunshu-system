/** 角色记录：角色只承载菜单/按钮权限，不含数据范围 */
export interface RoleRecord {
  id: number
  roleName: string
  roleCode: string
  roleDesc?: string
  status: boolean
  remark?: string
  createBy?: number
  createTime?: string
  updateBy?: number
  /** 最后修改人姓名：由后端关联 sys_user 解析；未建表时为空 */
  updateByName?: string
  updateTime?: string
}

/** 角色列表查询参数：筛选、排序与分页均走服务端 */
export interface RoleQuery {
  roleName?: string
  roleCode?: string
  roleDesc?: string
  updateByName?: string
  updateTimeStart?: string
  updateTimeEnd?: string
  /** 服务端排序字段（白名单），空表示默认 id asc */
  sortBy?: string
  sortOrder?: 'asc' | 'desc'
  pageNum?: number
  pageSize?: number
}

export interface RolePage {
  records: RoleRecord[]
  total: number
}

/** 新增/编辑载荷：编码创建后不可修改 */
export interface RoleSavePayload {
  roleName: string
  roleCode: string
  roleDesc?: string
  status: boolean
  remark?: string
}
