-- ============================================================================
-- V30 回滚：删除车次费用明细 / 车次操作日志
-- 手工执行草稿，不属于 Flyway classpath
-- 警告：DROP TABLE 数据不可逆
-- ============================================================================
SET NAMES utf8mb4;

DROP TABLE IF EXISTS `biz_trip_log`;
DROP TABLE IF EXISTS `biz_trip_fee`;

-- DELETE FROM `flyway_schema_history` WHERE `version` = '30';
