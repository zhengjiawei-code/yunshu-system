-- ============================================================================
-- V29 回滚：删除配载明细 / 费用分摊明细
-- 手工执行草稿，不属于 Flyway classpath
-- 警告：DROP TABLE 数据不可逆
-- ============================================================================
SET NAMES utf8mb4;

DROP TABLE IF EXISTS `biz_trip_waybill_fee`;
DROP TABLE IF EXISTS `biz_trip_waybill`;

-- DELETE FROM `flyway_schema_history` WHERE `version` = '29';
