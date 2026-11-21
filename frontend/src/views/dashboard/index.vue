<template>
  <div class="page-container">
    <div class="page-header">
      <div>
        <h2 class="page-header__title">工作台</h2>
        <p class="page-header__desc">运输管理系统 · 一期骨架，业务模块待接入</p>
      </div>
    </div>

    <div class="metrics">
      <div v-for="item in metrics" :key="item.label" class="metric-card">
        <span class="metric-card__label">{{ item.label }}</span>
        <span class="metric-card__value">{{ item.value }}</span>
        <span class="metric-card__hint">{{ item.hint }}</span>
      </div>
    </div>

    <div class="page-content">
      <div class="panel-head">
        <span class="panel-head__title">工程状态</span>
      </div>
      <div class="status-list">
        <div v-for="row in statusRows" :key="row.name" class="status-row">
          <span class="status-row__name">{{ row.name }}</span>
          <span class="status-tag" :class="`status-tag--${row.level}`">{{ row.status }}</span>
        </div>
      </div>
      <div class="empty-state">
        业务页面尚未开发，点击左侧菜单可查看各模块占位页
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
const metrics = [
  { label: '一级目录', value: '14', hint: '系统 / 运营 / 运输 …' },
  { label: '内部端菜单', value: '39', hint: '不含客户端预开单' },
  { label: '功能页面', value: '40', hint: '含客户端预开单 1 个' },
  { label: '本期终端', value: 'PC', hint: 'APP 端字段已预留' }
]

const statusRows = [
  { name: '设计令牌 / 全局样式', status: '已完成', level: 'success' },
  { name: '基础布局（顶栏 / 侧栏 / 标签栏）', status: '已完成', level: 'success' },
  { name: '静态路由与动态菜单渲染', status: '已完成', level: 'success' },
  { name: '请求层封装', status: '已完成', level: 'success' },
  { name: '业务页面 / 接口联调', status: '待开发', level: 'warning' }
]
</script>

<style scoped>
.metrics {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: var(--spacing-base);
  margin-bottom: var(--spacing-base);
}

.metric-card {
  display: flex;
  flex-direction: column;
  gap: 6px;
  padding: var(--spacing-lg);
  background-color: var(--color-bg-card);
  border: 1px solid var(--color-border-light);
  border-radius: var(--radius-md);
  box-shadow: var(--shadow-xs);
}

.metric-card__label {
  font-size: var(--font-size-small);
  color: var(--color-text-secondary);
}

.metric-card__value {
  font-family: var(--font-family-number);
  font-size: var(--font-size-big);
  font-weight: var(--font-weight-semibold);
  color: var(--color-text-primary);
  font-variant-numeric: tabular-nums;
}

.metric-card__hint {
  font-size: var(--font-size-extra-small);
  color: var(--color-text-placeholder);
}

.panel-head {
  margin-bottom: var(--spacing-md);
}

.panel-head__title {
  font-size: var(--font-size-base);
  font-weight: var(--font-weight-semibold);
  color: var(--color-text-primary);
}

.status-list {
  display: flex;
  flex-direction: column;
}

.status-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 10px 0;
  font-size: var(--font-size-small);
  border-bottom: 1px solid var(--color-border-light);
}

.status-row:last-child {
  border-bottom: none;
}

.status-row__name {
  color: var(--color-text-regular);
}

@media (max-width: 1100px) {
  .metrics {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}
</style>
