import { defineStore } from 'pinia'

export interface TagView {
  key: string
  path: string
  fullPath: string
  title: string
  affix: boolean
}

const STORAGE_KEY = 'yunshu_tags_view'

const HOME_TAG: TagView = {
  key: '/dashboard',
  path: '/dashboard',
  fullPath: '/dashboard',
  title: '首页',
  affix: true
}

function semanticKey(path: string) {
  return path.split('?')[0].split('#')[0]
}

function readCache(): TagView[] {
  try {
    const raw = sessionStorage.getItem(STORAGE_KEY)
    if (!raw) return []
    const list = JSON.parse(raw)
    if (!Array.isArray(list)) return []
    const seen = new Set<string>()
    return list.filter((tag: TagView) => {
      const key = semanticKey(tag.key)
      if (!key || key === '/dashboard' || seen.has(key)) return false
      seen.add(key)
      return true
    })
  } catch {
    return []
  }
}

export const useTagsStore = defineStore('tags', {
  state: () => ({
    visitedViews: [HOME_TAG, ...readCache()]
  }),
  actions: {
    persist() {
      const cacheable = this.visitedViews.filter((tag) => !tag.affix)
      sessionStorage.setItem(STORAGE_KEY, JSON.stringify(cacheable))
    },
    addView(view: { path: string; fullPath: string; title: string }) {
      if (!view.path || view.path === '/login') return
      const skeleton = semanticKey(view.fullPath)
      const existing = this.visitedViews.find((tag) => semanticKey(tag.key) === skeleton)
      if (existing) {
        existing.key = view.fullPath
        existing.fullPath = view.fullPath
        existing.path = view.path
        existing.title = view.title || existing.title
        this.persist()
        return
      }
      this.visitedViews.push({
        key: view.fullPath,
        path: view.path,
        fullPath: view.fullPath,
        title: view.title,
        affix: false
      })
      this.persist()
    },
    /** 删除标签，返回删除后应该跳转的标签（不需要跳转时返回 null） */
    delView(key: string): TagView | null {
      const index = this.visitedViews.findIndex((tag) => tag.key === key)
      if (index === -1) return null
      if (this.visitedViews[index].affix) return null
      this.visitedViews.splice(index, 1)
      this.persist()
      return this.visitedViews[index] || this.visitedViews[index - 1] || HOME_TAG
    },
    delOthers(key: string) {
      this.visitedViews = this.visitedViews.filter((tag) => tag.affix || tag.key === key)
      this.persist()
    },
    delAll() {
      this.visitedViews = this.visitedViews.filter((tag) => tag.affix)
      this.persist()
    },
    reset() {
      this.visitedViews = [HOME_TAG]
      sessionStorage.removeItem(STORAGE_KEY)
    }
  }
})
