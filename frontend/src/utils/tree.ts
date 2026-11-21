import type { MenuNode } from '@/types/menu'

/** 树工具：菜单管理页专用，保持纯函数、不改入参 */

export function cloneTree(nodes: MenuNode[]): MenuNode[] {
  return nodes.map((node) => ({
    ...node,
    children: node.children ? cloneTree(node.children) : undefined
  }))
}

export function walk(
  nodes: MenuNode[],
  visit: (node: MenuNode, parentId: number, depth: number) => void,
  parentId = 0,
  depth = 1
) {
  nodes.forEach((node) => {
    visit(node, parentId, depth)
    if (node.children?.length) walk(node.children, visit, node.id, depth + 1)
  })
}

export function findNode(nodes: MenuNode[], id: number): MenuNode | undefined {
  for (const node of nodes) {
    if (node.id === id) return node
    const found = node.children ? findNode(node.children, id) : undefined
    if (found) return found
  }
  return undefined
}

export function findParent(nodes: MenuNode[], id: number): MenuNode | undefined {
  for (const node of nodes) {
    if (node.children?.some((child) => child.id === id)) return node
    const found = node.children ? findParent(node.children, id) : undefined
    if (found) return found
  }
  return undefined
}

/** 节点深度：顶级=1，只算目录/菜单，按钮不单独占层 */
export function getDepth(nodes: MenuNode[], id: number, depth = 1): number {
  for (const node of nodes) {
    if (node.id === id) return depth
    if (node.children?.length) {
      const found = getDepth(node.children, id, node.menuType === 3 ? depth : depth + 1)
      if (found) return found
    }
  }
  return 0
}

export function collectDescendants(node: MenuNode, includeSelf = false): MenuNode[] {
  const result: MenuNode[] = includeSelf ? [node] : []
  const walkChildren = (children?: MenuNode[]) => {
    children?.forEach((child) => {
      result.push(child)
      walkChildren(child.children)
    })
  }
  walkChildren(node.children)
  return result
}

/** 判断 targetId 是否是 ancestorId 自身或其后代（用于父级环检测） */
export function isSelfOrDescendant(nodes: MenuNode[], ancestorId: number, targetId: number): boolean {
  if (ancestorId === targetId) return true
  const ancestor = findNode(nodes, ancestorId)
  if (!ancestor) return false
  return collectDescendants(ancestor).some((node) => node.id === targetId)
}

export function maxSort(siblings: MenuNode[]): number {
  return siblings.reduce((max, node) => Math.max(max, node.sortOrder || 0), 0)
}

/** 只保留目录节点，用于“上级目录”选择；excludeId 会连同其后代一起排除 */
export function toDirectoryOptions(nodes: MenuNode[], excludeId?: number): MenuNode[] {
  const result: MenuNode[] = []
  nodes.forEach((node) => {
    if (node.menuType !== 1 || node.id === excludeId) return
    result.push({
      ...node,
      children: node.children ? toDirectoryOptions(node.children, excludeId) : undefined
    })
  })
  return result
}

/** 筛选树：命中节点保留完整子树，命中子节点的祖先链保留 */
export function filterTree(nodes: MenuNode[], predicate: (node: MenuNode) => boolean): MenuNode[] {
  const result: MenuNode[] = []
  nodes.forEach((node) => {
    if (predicate(node)) {
      result.push(cloneTree([node])[0])
      return
    }
    if (node.children?.length) {
      const children = filterTree(node.children, predicate)
      if (children.length) result.push({ ...node, children })
    }
  })
  return result
}

/** 需要展开的节点：所有带子节点的节点 */
export function collectExpandKeys(nodes: MenuNode[], onlyRoot = false): number[] {
  const keys: number[] = []
  nodes.forEach((node) => {
    if (node.children?.length) {
      keys.push(node.id)
      if (!onlyRoot) keys.push(...collectExpandKeys(node.children))
    }
  })
  return keys
}
