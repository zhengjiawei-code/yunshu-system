-- ============================================================================
-- V14 回滚脚本（不放入 db/migration，避免被 Flyway 当作版本迁移）
-- 顺序：先删货物明细子表，再删主表新增列（反向依赖）
-- 适用：仅回滚 V14；回滚后需执行 flyway repair 清理 V14 历史记录（若版本已应用）
-- ============================================================================

SET NAMES utf8mb4;

DROP TABLE IF EXISTS `biz_waybill_goods`;

ALTER TABLE `biz_waybill`
  DROP COLUMN `receiver_district_name`,
  DROP COLUMN `receiver_district_code`,
  DROP COLUMN `receiver_city_name`,
  DROP COLUMN `receiver_city_code`,
  DROP COLUMN `receiver_province_name`,
  DROP COLUMN `receiver_province_code`,
  DROP COLUMN `receiver_company`,
  DROP COLUMN `sender_company`;
