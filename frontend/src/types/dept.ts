/** 机构类型：1 总部 / 2 公司 / 3 部门 / 4 分拨中心 / 5 网点 */
export type DeptType = 1 | 2 | 3 | 4 | 5

/** 机构性质：1 自营 / 2 加盟 / 3 合作；总部固定自营 */
export type DeptNature = 1 | 2 | 3

export interface DeptNode {
  id: number
  parentId: number
  deptName: string
  deptCode: string
  deptType: DeptType
  /** 机构性质：1 自营 / 2 加盟 / 3 合作；总部固定为 1 */
  deptNature: DeptNature
  /** 是否末端：仅网点可为 true；纯业务标识，与结构上是否有下级无关 */
  isLeaf: boolean
  allowShip: boolean
  allowArrival: boolean
  allowPrepaidUnpaid: boolean
  allowCollectUnpaidSign: boolean
  leader?: string
  phone?: string
  /** 详细地址（街道门牌），与省/市/区县编码并存 */
  address?: string
  provinceCode?: string
  cityCode?: string
  districtCode?: string
  provinceName?: string
  cityName?: string
  districtName?: string
  /** 末端网点适用分公司ID列表（仅 is_leaf=1） */
  applyCompanyIds?: number[]
  /** 末端网点适用分公司名称，逗号分隔 */
  applyCompanyNames?: string
  sortOrder: number
  status: boolean
  remark?: string
  createBy?: number
  /** 创建人姓名：由后端关联 sys_user 解析；sys_user 未建表时为 null，前端显示“—” */
  createByName?: string
  createTime?: string
  updateBy?: number
  /** 最后修改人姓名：同上，来自 sys_user 关联解析 */
  updateByName?: string
  updateTime?: string
  children?: DeptNode[]
}

/** 平铺列表行：附带上级名称与完整路径 */
export interface DeptRow extends DeptNode {
  parentName: string
  parentPath: string
  /** 所在地区：省+市+区县，未维护时为空 */
  regionName?: string
}

/** 机构列表查询参数：筛选、排序与分页均走服务端 */
export interface DeptQuery {
  rootId?: number
  deptName?: string
  deptCode?: string
  deptType?: DeptType | DeptType[]
  deptNature?: DeptNature | DeptNature[]
  parentId?: number | number[]
  leader?: string
  phone?: string
  isLeaf?: boolean | boolean[]
  status?: boolean | boolean[]
  provinceCode?: string
  cityCode?: string
  districtCode?: string
  applyCompanyId?: number | number[]
  createByName?: string
  updateByName?: string
  createTimeStart?: string
  createTimeEnd?: string
  updateTimeStart?: string
  updateTimeEnd?: string
  filterModes?: string
  /** 服务端排序字段（白名单），空表示默认 sort_order asc, id asc */
  sortBy?: string
  sortOrder?: 'asc' | 'desc'
  pageNum?: number
  pageSize?: number
}

export interface DeptPage {
  records: DeptRow[]
  total: number
}

/** 管理范围树节点：GET /depts/manage-scope */
export interface ManageScopeNode {
  id: number
  parentId: number
  deptName: string
  deptCode: string
  deptType: DeptType
  isLeaf: boolean
  status: boolean
  sortOrder: number
  children?: ManageScopeNode[]
}

/** 管理范围：总部全部；分公司=本公司子树∪适用末端网点 */
export interface ManageScope {
  scopeType: 'HQ' | 'COMPANY' | 'NONE'
  companyId?: number
  companyName?: string
  tree: ManageScopeNode[]
  sharedLeaves: ManageScopeNode[]
  total: number
}

/** 保存载荷：基础信息与授权分开保存，未携带的授权开关由后端保留原值 */
export interface DeptSavePayload {
  parentId: number
  deptType: DeptType
  /** 机构性质，必填；总部后端强制为自营 */
  deptNature: DeptNature
  /** 仅网点可传 true；其它类型后端会拒绝并清空 */
  isLeaf?: boolean
  deptName: string
  deptCode: string
  allowShip?: boolean
  allowArrival?: boolean
  allowPrepaidUnpaid?: boolean
  allowCollectUnpaidSign?: boolean
  leader?: string
  phone?: string
  address?: string
  provinceCode?: string
  cityCode?: string
  districtCode?: string
  applyCompanyIds?: number[]
  sortOrder: number
  status: boolean
  remark?: string
}
