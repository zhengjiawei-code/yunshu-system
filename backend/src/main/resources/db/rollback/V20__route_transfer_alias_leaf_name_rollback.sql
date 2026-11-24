-- V20 回滚：别名恢复为目的地全路径，并恢复上一版注释（不实际执行）
UPDATE `sys_route_transfer`
SET `alias` = `region_full_name`;

ALTER TABLE `sys_route_transfer`
  MODIFY COLUMN `alias` VARCHAR(64) NOT NULL COMMENT '别名：开单目的地展示用，默认等于目的地全称';
