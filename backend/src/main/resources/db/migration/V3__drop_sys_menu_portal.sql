-- ============================================================
-- V3__drop_sys_menu_portal.sql
-- 目的：删除菜单“端归属”维度（portal_type 字段与 idx_portal 索引）
-- 目标库：yunshu（仅此库）
-- 状态：已获家伟批准（2026-11-20），随新代码启动由 Flyway 执行
-- 执行记录：备份表已于 2026-11-20 16:2x 先行创建并写入 54 行（53 ADMIN / 1 CLIENT）
-- 回滚：见同目录 rollback_V3__restore_sys_menu_portal.sql
-- 注意：
--   1. MySQL DDL 隐式提交，无法用事务回滚；
--   2. 第一步先把原值写入备份表，回滚依赖该表，执行期间不要删除；
--   3. 执行前必须停止后端，避免旧代码（仍查询 portal_type）报 Unknown column；
--   4. 删除后 54 条菜单不再区分内部端/客户端，预开单成为普通菜单。
-- ============================================================

SET NAMES utf8mb4;

-- 1) 备份原端归属值（回滚依据；本次数据：53 条 ADMIN、1 条 CLIENT=预开单）
CREATE TABLE IF NOT EXISTS sys_menu_portal_backup_20261120 (
  menu_id       BIGINT UNSIGNED NOT NULL COMMENT '菜单ID，对应 sys_menu.id',
  portal_type   VARCHAR(20) NOT NULL COMMENT '删除前的端归属：ADMIN=公司内部端，CLIENT=客户端',
  backed_up_at  DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '备份时间',
  PRIMARY KEY (menu_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
  COMMENT='V3 删除 portal_type 前的值备份，仅用于回滚，确认稳定后可另行清理';

INSERT INTO sys_menu_portal_backup_20261120 (menu_id, portal_type)
SELECT id, portal_type FROM sys_menu
ON DUPLICATE KEY UPDATE portal_type = VALUES(portal_type), backed_up_at = CURRENT_TIMESTAMP;

-- 2) 删除索引 idx_portal(portal_type, status)
ALTER TABLE sys_menu DROP INDEX idx_portal;

-- 3) 删除字段 portal_type
ALTER TABLE sys_menu DROP COLUMN portal_type;
