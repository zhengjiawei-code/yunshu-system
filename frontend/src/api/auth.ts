import request from '@/utils/request'
import { internalMenus } from '@/mock/menus'
import type { AuthInfo } from '@/types/menu'

export interface LoginPayload {
  username: string
  password: string
}

export interface AuthOrgItem {
  id: number
  name: string
}

export interface CurrentOrgInfo {
  currentOrgId: number | null
  currentOrgName: string | null
  orgs: AuthOrgItem[]
}

export interface ProfileInfo {
  id: number
  username: string
  realName: string
  phone?: string | null
  email?: string | null
  deptId?: number | null
  deptName?: string | null
  userType?: 1 | 2
  roles: string[]
}

export interface ProfilePayload {
  realName: string
  phone?: string
  email?: string
}

export interface PasswordPayload {
  oldPassword: string
  newPassword: string
  confirmPassword: string
}

const useMock = import.meta.env.VITE_USE_MOCK === 'true'

/** 登录。骨架阶段无后端时走本地 mock，返回固定 token。 */
export async function login(payload: LoginPayload): Promise<{ token: string }> {
  if (useMock) {
    if (!payload.username || !payload.password) {
      throw { code: 400, message: '请输入账号和密码' }
    }
    return { token: `mock-token-${Date.now()}` }
  }
  return request({ url: '/v1/auth/login', method: 'post', data: payload })
}

/** 获取当前用户信息 + 菜单树 + 权限。骨架阶段返回内部端 mock 菜单。 */
export async function getAuthInfo(): Promise<AuthInfo> {
  if (useMock) {
    return {
      user: { id: 1, username: 'admin', realName: '管理员', orgId: 1, orgName: '总部' },
      roles: ['admin'],
      perms: [],
      menus: internalMenus
    }
  }
  return request({ url: '/v1/auth/info', method: 'get' })
}

/** 当前机构与可切换机构列表 */
export async function getCurrentOrg(): Promise<CurrentOrgInfo> {
  return request({ url: '/v1/auth/current-org', method: 'get' })
}

/** 切换当前机构：服务端校验可登录范围，越界返回明确文案 */
export async function switchCurrentOrg(orgId: number): Promise<CurrentOrgInfo> {
  return request({ url: '/v1/auth/current-org', method: 'put', data: { orgId } })
}

/** 个人资料 */
export async function getProfile(): Promise<ProfileInfo> {
  return request({ url: '/v1/auth/profile', method: 'get' })
}

/** 修改个人资料（姓名/手机/邮箱） */
export async function updateProfile(data: ProfilePayload): Promise<void> {
  return request({ url: '/v1/auth/profile', method: 'put', data })
}

/** 修改密码；成功后服务端注销全部会话，需重新登录 */
export async function updatePassword(data: PasswordPayload): Promise<void> {
  return request({ url: '/v1/auth/password', method: 'put', data })
}

/** 退出登录：销毁服务端 token */
export async function logoutApi(): Promise<void> {
  if (useMock) return
  return request({ url: '/v1/auth/logout', method: 'post' })
}
