-- V16：直达/外转路由增加“提供服务”两个布尔列
-- 历史数据靠默认值自动成为“自提+送货”，不补数、不改已有列、不删数据
ALTER TABLE `sys_route_transfer`
  ADD COLUMN `pickup_enabled` TINYINT(1) NOT NULL DEFAULT 1
    COMMENT '是否提供自提：1=是 0=否' AFTER `transfer_type`,
  ADD COLUMN `delivery_enabled` TINYINT(1) NOT NULL DEFAULT 1
    COMMENT '是否提供送货：1=是 0=否' AFTER `pickup_enabled`;
