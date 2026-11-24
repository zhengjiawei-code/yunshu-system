-- V20：别名口径修正为目的地末级区划名（短名），不再使用全路径
-- 1) 注释同步
ALTER TABLE `sys_route_transfer`
  MODIFY COLUMN `alias` VARCHAR(64) NOT NULL COMMENT '别名：开单目的地展示用，默认等于目的地名称（末级区划名）';

-- 2) 27 行全覆盖回填末级区划名（不加 WHERE）
UPDATE `sys_route_transfer` t
  JOIN `sys_region` r ON r.`code` = t.`region_code`
SET t.`alias` = r.`name`;
