-- ============================================================================
-- 云枢运输管理系统 · 运营管理（揽收开单 + 运单管理）回滚脚本
-- ----------------------------------------------------------------------------
-- 对应正向脚本：V13__operation_waybill.sql（7 张表）
-- 用途：建表失败或需要整体回退时，按依赖反向清理
-- 状态：仅随 DDL 交付；本轮不执行、不连 yunshu 库
-- 说明：
--   - 全部表无物理外键，DROP 顺序仍按逻辑依赖从子到父排列；
--   - 使用 IF EXISTS，重复执行安全；
--   - 本脚本会删除 7 张业务表及其全部数据，执行前必须确认已备份；
--   - 回滚后在 Flyway 中执行 `flyway repair` 清理 V13 失败记录（见方案 17.1）。
-- ============================================================================

SET NAMES utf8mb4;

-- 1. 审计日志（依赖运单）
DROP TABLE IF EXISTS `biz_waybill_log`;

-- 2. 路由节点快照（依赖运单）
DROP TABLE IF EXISTS `biz_waybill_route_node`;

-- 3. 运单主表（依赖客户、预开单号）
DROP TABLE IF EXISTS `biz_waybill`;

-- 4. 预开单（依赖客户、预开单号序列）
DROP TABLE IF EXISTS `biz_pre_waybill`;

-- 5. 预开单号日流水
DROP TABLE IF EXISTS `biz_pre_waybill_no_seq`;

-- 6. 运单号日流水
DROP TABLE IF EXISTS `biz_waybill_no_seq`;

-- 7. 客户主数据（被运单、预开单引用，最后删除）
DROP TABLE IF EXISTS `biz_customer`;

-- 校验：应为 0
-- SELECT COUNT(*) FROM information_schema.tables
--  WHERE table_schema = DATABASE()
--    AND table_name IN ('biz_waybill','biz_waybill_route_node','biz_customer',
--                       'biz_waybill_no_seq','biz_pre_waybill_no_seq',
--                       'biz_pre_waybill','biz_waybill_log');
