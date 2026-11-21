/** 菜单类型：1 目录 / 2 菜单 / 3 按钮 */
export type MenuType = 1 | 2 | 3

/** 终端：本期只做 PC，APP 仅预留 */
export type TerminalType = 'PC' | 'APP' | 'PC,APP'

export interface MenuNode {
  id: number
  parentId: number
  menuName: string
  menuType: MenuType
  routePath?: string
  component?: string
  perms?: string
  icon?: string
  terminalType: TerminalType
  sortOrder: number
  visible: boolean
  status: boolean
  keepAlive?: boolean
  affix?: boolean
  remark?: string
  updateTime?: string
  children?: MenuNode[]
}

export interface AuthOrg {
  id: number
  name: string
}

export interface UserInfo {
  id: number
  username: string
  realName: string
  /** 主机构（兼容旧字段名） */
  orgId?: number | null
  orgName?: string | null
  deptId?: number | null
  deptName?: string | null
  /** 1=内部员工 2=客户 */
  userType?: 1 | 2
  phone?: string | null
  email?: string | null
  /** 主机构 + 额外可登录机构；客户为空 */
  authOrgs?: AuthOrg[]
  /** 当前机构（数据范围锚点），默认主机构 */
  currentOrgId?: number | null
  currentOrgName?: string | null
}

export interface AuthInfo {
  user: UserInfo
  roles: string[]
  perms: string[]
  menus: MenuNode[]
}
