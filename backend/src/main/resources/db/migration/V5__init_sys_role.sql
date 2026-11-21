-- =====================================================================
-- 运输管理系统 · 角色与角色-菜单关联表（V5）
-- 文件：V5__init_sys_role.sql（暂存目录，本轮仅产出，未执行）
-- 数据库：MySQL 8.0；字符集 utf8mb4 / utf8mb4_0900_ai_ci
-- 前置：V4 已执行（sys_dept 已存在）
-- 说明：
--   1) 角色与数据权限彻底解耦：本表不含 data_scope；数据范围在 sys_user.data_scope（用户模块迁移中新增）。
--   2) 角色只控制菜单与按钮可见性；平台配置类接口（角色/菜单/用户/机构）不受数据权限影响。
--   3) 内置 admin 角色固定 id=1，不可删除/禁用/改权限；运行时按“拥有 admin 角色=全部菜单”直通，
--      种子授权仅作初始数据，后续新增菜单无需回填 admin 的 sys_role_menu。
--   4) 执行前须由家伟确认；执行前备份 yunshu 库；本脚本不对任何已有表执行 DROP/ALTER。
-- =====================================================================

SET NAMES utf8mb4;

-- ---------------------------------------------------------------------
-- 1. 角色表
-- ---------------------------------------------------------------------
CREATE TABLE `sys_role` (
  `id`          BIGINT UNSIGNED NOT NULL AUTO_INCREMENT
                COMMENT '角色主键ID，自增；被 sys_user_role.role_id 与 sys_role_menu.role_id 引用；1=内置超级管理员',
  `role_name`   VARCHAR(20)     NOT NULL
                COMMENT '角色名称：2-20 字符，全局唯一；用于展示与用户分配下拉',
  `role_code`   VARCHAR(20)     NOT NULL
                COMMENT '角色编码：2-20 位字母/数字/下划线，全局唯一且创建后不可修改；admin 为内置超管标识',
  `role_desc`   VARCHAR(200)    DEFAULT NULL
                COMMENT '角色描述：最多 200 字符，仅展示',
  `status`      TINYINT UNSIGNED NOT NULL DEFAULT 1
                COMMENT '状态：1=启用 0=禁用；禁用角色不参与菜单/按钮下发；admin 固定为 1',
  `remark`      VARCHAR(500)    DEFAULT NULL
                COMMENT '备注：最多 500 字符',
  `create_by`   BIGINT UNSIGNED DEFAULT NULL
                COMMENT '创建人ID，关联 sys_user.id',
  `create_time` DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP
                COMMENT '创建时间',
  `update_by`   BIGINT UNSIGNED DEFAULT NULL
                COMMENT '最后修改人ID，关联 sys_user.id',
  `update_time` DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
                COMMENT '最后修改时间',
  `deleted`     TINYINT(1)      NOT NULL DEFAULT 0
                COMMENT '逻辑删除标记：0=未删除 1=已删除；查询必须带 deleted=0',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_role_code` (`role_code`),
  UNIQUE KEY `uk_role_name` (`role_name`),
  KEY `idx_status` (`status`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci
  COMMENT = '角色表：仅承载菜单/按钮权限，不含数据范围；数据范围见 sys_user.data_scope';

-- ---------------------------------------------------------------------
-- 2. 角色-菜单关联表
-- ---------------------------------------------------------------------
CREATE TABLE `sys_role_menu` (
  `id`          BIGINT UNSIGNED NOT NULL AUTO_INCREMENT
                COMMENT '主键ID，自增',
  `role_id`     BIGINT UNSIGNED NOT NULL
                COMMENT '角色ID，关联 sys_role.id',
  `menu_id`     BIGINT UNSIGNED NOT NULL
                COMMENT '菜单/按钮节点ID，关联 sys_menu.id',
  `create_time` DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP
                COMMENT '授权时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_role_menu` (`role_id`, `menu_id`),
  KEY `idx_role_id` (`role_id`),
  KEY `idx_menu_id` (`menu_id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci
  COMMENT = '角色-菜单关联表：整体覆盖式保存，提交含半选父节点';

-- ---------------------------------------------------------------------
-- 3. 内置超级管理员角色种子
-- ---------------------------------------------------------------------
INSERT INTO `sys_role`
  (`id`, `role_name`, `role_code`, `role_desc`, `status`, `remark`)
VALUES
  (1, '超级管理员', 'admin', '系统内置角色，拥有全部菜单与按钮权限，不可删除、不可禁用、不可修改权限',
   1, '系统内置，禁止删除/禁用/改权限');

-- ---------------------------------------------------------------------
-- 4. 内置角色初始授权：当前全量菜单与按钮
--    运行时 admin 直通全量，本段仅保证初始化后授权树回显完整
-- ---------------------------------------------------------------------
INSERT INTO `sys_role_menu` (`role_id`, `menu_id`)
SELECT 1, `id` FROM `sys_menu` WHERE `deleted` = 0;
