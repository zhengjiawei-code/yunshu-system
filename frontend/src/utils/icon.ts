import * as ElIcons from '@element-plus/icons-vue'
import { Menu as FallbackIcon } from '@element-plus/icons-vue'
import type { Component } from 'vue'

/**
 * 图标白名单解析：后端只存 Element Plus 图标组件名（如 Setting）。
 * 找不到时用 Menu 兜底并在控制台告警，避免整块菜单渲染失败。
 */
const iconMap = ElIcons as unknown as Record<string, Component>

export function resolveIcon(name?: string): Component {
  if (!name) return FallbackIcon
  const icon = iconMap[name]
  if (!icon) {
    console.warn(`[icon] 未找到图标 "${name}"，已回退为 Menu`)
    return FallbackIcon
  }
  return icon
}

/** 供菜单管理页做白名单校验 */
export const ICON_NAMES = Object.keys(iconMap).sort()
