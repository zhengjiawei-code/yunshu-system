-- ============================================================================
-- V27 回滚：biz_waybill 加列 + 索引 + 存量回填
-- 手工执行草稿，不属于 Flyway classpath，不会自动执行
-- 前置：已完成全库备份（backups/2026-11-25/pre-v27-yunshu.sql）
-- 警告：DROP COLUMN 会丢失 load_status/current_hub_* 等运行数据，不可逆
-- ============================================================================
SET NAMES utf8mb4;

ALTER TABLE `biz_waybill`
  DROP INDEX `idx_parent_deleted`,
  DROP INDEX `idx_inventory_time`,
  DROP INDEX `idx_current_hub`;

ALTER TABLE `biz_waybill`
  DROP COLUMN `load_status`,
  DROP COLUMN `inventory_time`,
  DROP COLUMN `current_node_seq`,
  DROP COLUMN `current_hub_name`,
  DROP COLUMN `current_hub_id`;

-- 回滚后必须清理 Flyway 历史（需家伟确认后执行，或改走 flyway repair）
-- DELETE FROM `flyway_schema_history` WHERE `version` = '27';
