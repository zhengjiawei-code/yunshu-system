-- ============================================================
-- rollback_V3__restore_sys_menu_portal.sql
-- 用途：V3 执行后如需紧急回滚，恢复 portal_type 字段、索引与原值
-- 前提：sys_menu_portal_backup_20261120 仍存在
-- 注意：
--   1. 本脚本不修改 flyway_schema_history；回滚后 V3 仍显示已执行，
--      需人工登记本次回滚，避免后续误判；
--   2. 恢复顺序必须是“加字段 → 回填 → 加索引”，否则索引建在空值上无意义；
--   3. 回填以备份表为准；备份表缺失时无法还原 CLIENT/ADMIN 区分。
-- ============================================================

SET NAMES utf8mb4;

-- 1) 加回字段（位置与 V1 保持一致：icon 之后）
ALTER TABLE sys_menu
  ADD COLUMN portal_type VARCHAR(20) NOT NULL DEFAULT 'ADMIN'
    COMMENT '端归属：ADMIN=公司内部端（内部员工使用），CLIENT=客户端（发货客户自助使用）；决定菜单下发给哪个端'
    AFTER icon;

-- 2) 回填原值
UPDATE sys_menu m
  JOIN sys_menu_portal_backup_20261120 b ON b.menu_id = m.id
SET m.portal_type = b.portal_type;

-- 3) 加回索引
ALTER TABLE sys_menu ADD KEY idx_portal (portal_type, status);
