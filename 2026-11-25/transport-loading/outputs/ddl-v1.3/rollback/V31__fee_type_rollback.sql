-- ============================================================================
-- V31 回滚：删除费用类型配置表（含 16 项种子）
-- 手工执行草稿，不属于 Flyway classpath
-- 警告：DROP TABLE 数据不可逆
-- ============================================================================
SET NAMES utf8mb4;

DROP TABLE IF EXISTS `sys_fee_type`;

-- DELETE FROM `flyway_schema_history` WHERE `version` = '31';
