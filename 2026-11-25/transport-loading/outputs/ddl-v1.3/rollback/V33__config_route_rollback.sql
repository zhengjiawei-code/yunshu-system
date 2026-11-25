-- ============================================================================
-- V33 回滚：删除 sys_config（含 2 键种子）并移除 sys_route.volume_weight_ratio
-- 手工执行草稿，不属于 Flyway classpath
-- 警告：配置与线路重泡比覆盖值丢失，不可逆
-- ============================================================================
SET NAMES utf8mb4;

DROP TABLE IF EXISTS `sys_config`;

ALTER TABLE `sys_route`
  DROP COLUMN `volume_weight_ratio`;

-- DELETE FROM `flyway_schema_history` WHERE `version` = '33';
