-- V16 回滚：删除直达/外转路由的“提供服务”两列（不实际执行）
ALTER TABLE `sys_route_transfer`
  DROP COLUMN `delivery_enabled`,
  DROP COLUMN `pickup_enabled`;
