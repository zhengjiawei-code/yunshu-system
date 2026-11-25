-- ============================================================================
-- V32 回滚：删除车辆 / 司机最小主数据
-- 手工执行草稿，不属于 Flyway classpath
-- 警告：DROP TABLE 数据不可逆
-- ============================================================================
SET NAMES utf8mb4;

DROP TABLE IF EXISTS `sys_driver`;
DROP TABLE IF EXISTS `sys_vehicle`;

-- DELETE FROM `flyway_schema_history` WHERE `version` = '32';
