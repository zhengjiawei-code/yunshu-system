-- V19：直达/外转路由增加“别名”，默认等于目的地全称
-- 1) 新增可空列
ALTER TABLE `sys_route_transfer`
  ADD COLUMN `alias` VARCHAR(64) NULL COMMENT '别名：开单目的地展示用，默认等于目的地全称' AFTER `region_full_name`;

-- 2) 历史数据回填目的地全称
UPDATE `sys_route_transfer` SET `alias` = `region_full_name`;

-- 3) 收紧为非空
ALTER TABLE `sys_route_transfer`
  MODIFY COLUMN `alias` VARCHAR(64) NOT NULL COMMENT '别名：开单目的地展示用，默认等于目的地全称';
