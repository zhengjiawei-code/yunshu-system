-- ============================================================================
-- V28 回滚：删除车次主表 / 子车次 / 车次号流水
-- 手工执行草稿，不属于 Flyway classpath
-- 警告：DROP TABLE 数据不可逆；先确认 V29/V30 已回滚
-- ============================================================================
SET NAMES utf8mb4;

DROP TABLE IF EXISTS `biz_trip_dest`;
DROP TABLE IF EXISTS `biz_trip`;
DROP TABLE IF EXISTS `biz_trip_no_seq`;

-- DELETE FROM `flyway_schema_history` WHERE `version` = '28';
