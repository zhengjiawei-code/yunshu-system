-- V19 回滚：删除直达/外转路由别名列（不实际执行）
ALTER TABLE `sys_route_transfer`
  DROP COLUMN `alias`;
