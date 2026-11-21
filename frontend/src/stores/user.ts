import { defineStore } from 'pinia'
import { getAuthInfo, login } from '@/api/auth'
import type { MenuNode, UserInfo } from '@/types/menu'

const TOKEN_KEY = 'yunshu_token'

interface UserState {
  token: string
  user: UserInfo | null
  roles: string[]
  perms: string[]
  menus: MenuNode[]
  loaded: boolean
}

export const useUserStore = defineStore('user', {
  state: (): UserState => ({
    token: localStorage.getItem(TOKEN_KEY) || '',
    user: null,
    roles: [],
    perms: [],
    menus: [],
    loaded: false
  }),
  getters: {
    displayName: (state) => state.user?.realName || state.user?.username || '',
    avatarText(): string {
      return this.displayName.slice(0, 1).toUpperCase() || '?'
    }
  },
  actions: {
    async login(username: string, password: string) {
      const { token } = await login({ username, password })
      this.token = token
      localStorage.setItem(TOKEN_KEY, token)
    },
    async fetchAuthInfo() {
      const info = await getAuthInfo()
      this.user = info.user
      this.roles = info.roles
      this.perms = info.perms
      this.menus = info.menus
      this.loaded = true
      return info
    },
    hasPerm(perm: string) {
      return this.roles.includes('admin') || this.perms.includes(perm)
    },
    logout() {
      this.token = ''
      this.user = null
      this.roles = []
      this.perms = []
      this.menus = []
      this.loaded = false
      localStorage.removeItem(TOKEN_KEY)
    }
  }
})
