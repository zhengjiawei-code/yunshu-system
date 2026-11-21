-- =====================================================================
-- 运输管理系统 · 用户与授权关联表（V6）
-- 文件：V6__init_sys_user.sql（暂存目录，本轮仅产出，未执行）
-- 数据库：MySQL 8.0；字符集 utf8mb4 / utf8mb4_0900_ai_ci
-- 前置：V5 已执行（sys_role / sys_role_menu 已存在）
-- 说明：
--   1) 用户主体规则（2026-11-20 家伟裁定）：内部员工(user_type=1) dept_id 必填；
--      客户账号(user_type=2) dept_id 可空，data_scope 对客户不适用，不得虚构机构。
--   2) 角色与数据权限解耦：角色只管菜单/按钮；data_scope 挂在用户上，默认 4；
--      过滤由 yunshu.data-scope.enabled 控制，本期默认关闭。
--   3) sys_user_org 表示“额外可登录机构”，不含主机构 dept_id（主机构见 sys_user.dept_id）。
--   4) sys_user_menu 为账号级直授：1=额外授权、0=显式禁止（禁止级联子孙，禁止优先）。
--   5) 本脚本不对任何已有表执行 DROP/ALTER；执行前须由家伟确认并备份 yunshu 库。
-- =====================================================================

SET NAMES utf8mb4;

-- ---------------------------------------------------------------------
-- 1. 用户表
-- ---------------------------------------------------------------------
CREATE TABLE `sys_user` (
  `id`          BIGINT UNSIGNED NOT NULL AUTO_INCREMENT
                COMMENT '用户主键ID；被业务表 create_by/update_by 引用；1=内置超级管理员',
  `username`    VARCHAR(50)     NOT NULL
                COMMENT '登录账号：2-20 位字母/数字/下划线，deleted=0 范围唯一，创建后不可修改',
  `password`    VARCHAR(100)    NOT NULL
                COMMENT '密码：BCrypt 密文，禁止存明文；重置密码走独立接口，不回显',
  `real_name`   VARCHAR(50)     NOT NULL
                COMMENT '真实姓名：2-20 个字符，展示在操作人/经办人/审批人',
  `phone`       VARCHAR(20)     DEFAULT NULL
                COMMENT '手机号：选填，格式校验，用于联系与后续找回密码',
  `email`       VARCHAR(100)    DEFAULT NULL
                COMMENT '邮箱：选填，格式校验',
  `dept_id`     BIGINT UNSIGNED DEFAULT NULL
                COMMENT '所属主机构ID：内部员工(user_type=1)必填，客户(user_type=2)可空；引用 sys_dept.id',
  `status`      TINYINT(1)      NOT NULL DEFAULT 1
                COMMENT '状态：1=启用（可登录） 0=禁用（禁止登录）；admin 不可禁用',
  `user_type`   TINYINT         NOT NULL DEFAULT 1
                COMMENT '用户类型：1=内部员工 2=客户；不参与功能权限下发；客户不适用数据范围过滤',
  `data_scope`  TINYINT UNSIGNED NOT NULL DEFAULT 4
                COMMENT '数据范围：1=全部 3=本机构 4=本机构及以下 5=仅本人（2=自定义机构预留未实现）；默认 4；仅内部员工适用；过滤开关默认关闭',
  `remark`      VARCHAR(500)    DEFAULT NULL
                COMMENT '备注：最多 500 字符',
  `create_by`   BIGINT UNSIGNED DEFAULT NULL
                COMMENT '创建人用户ID，关联 sys_user.id',
  `create_time` DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP
                COMMENT '创建时间',
  `update_by`   BIGINT UNSIGNED DEFAULT NULL
                COMMENT '最后修改人用户ID，关联 sys_user.id',
  `update_time` DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
                COMMENT '最后修改时间',
  `deleted`     TINYINT(1)      NOT NULL DEFAULT 0
                COMMENT '逻辑删除：0=未删除 1=已删除；查询必须带 deleted=0',
  PRIMARY KEY (`id`),
  KEY `idx_username` (`username`),
  KEY `idx_dept` (`dept_id`),
  KEY `idx_status` (`status`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci
  COMMENT = '用户表：登录账号与人员信息；角色决定功能权限，data_scope 决定数据范围（默认关闭过滤）';

-- ---------------------------------------------------------------------
-- 2. 用户-角色关联表
-- ---------------------------------------------------------------------
CREATE TABLE `sys_user_role` (
  `user_id`     BIGINT UNSIGNED NOT NULL
                COMMENT '用户ID，引用 sys_user.id',
  `role_id`     BIGINT UNSIGNED NOT NULL
                COMMENT '角色ID，引用 sys_role.id；角色全机构通用，不带 org_id',
  `create_by`   BIGINT UNSIGNED DEFAULT NULL
                COMMENT '分配操作人用户ID',
  `create_time` DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP
                COMMENT '分配时间',
  PRIMARY KEY (`user_id`, `role_id`),
  KEY `idx_role` (`role_id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci
  COMMENT = '用户-角色关联表：一个用户可多角色；解除分配为物理删除';

-- ---------------------------------------------------------------------
-- 3. 用户-额外可登录机构表
-- ---------------------------------------------------------------------
CREATE TABLE `sys_user_org` (
  `user_id`     BIGINT UNSIGNED NOT NULL
                COMMENT '用户ID，引用 sys_user.id',
  `org_id`      BIGINT UNSIGNED NOT NULL
                COMMENT '额外可登录机构ID，引用 sys_dept.id；不包含主机构 dept_id',
  `create_by`   BIGINT UNSIGNED DEFAULT NULL
                COMMENT '授权操作人用户ID',
  `create_time` DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP
                COMMENT '授权时间',
  PRIMARY KEY (`user_id`, `org_id`),
  KEY `idx_org` (`org_id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci
  COMMENT = '用户-额外可登录机构表：主机构由 sys_user.dept_id 表达，此处只存额外机构';

-- ---------------------------------------------------------------------
-- 4. 用户-菜单直接授权表（账号级加减）
-- ---------------------------------------------------------------------
CREATE TABLE `sys_user_menu` (
  `id`          BIGINT UNSIGNED NOT NULL AUTO_INCREMENT
                COMMENT '主键ID，自增',
  `user_id`     BIGINT UNSIGNED NOT NULL
                COMMENT '用户ID，引用 sys_user.id；账号级直授，全机构生效',
  `menu_id`     BIGINT UNSIGNED NOT NULL
                COMMENT '菜单/按钮ID，引用 sys_menu.id；必须存在、未删除且启用',
  `grant_type`  TINYINT         NOT NULL DEFAULT 1
                COMMENT '授权类型：1=额外授权（角色权限之外追加） 0=显式禁止（剔除该节点并级联其全部子孙）',
  `create_by`   BIGINT UNSIGNED DEFAULT NULL
                COMMENT '操作人用户ID',
  `create_time` DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP
                COMMENT '授权时间；整体覆盖保存时重建，不保留历史',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_user_menu` (`user_id`, `menu_id`),
  KEY `idx_user_id` (`user_id`),
  KEY `idx_menu_id` (`menu_id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci
  COMMENT = '用户-菜单直接授权表：在角色权限之上做账号级加减，禁止优先并级联子孙';

-- ---------------------------------------------------------------------
-- 5. 内置超级管理员账号（密码 123456，BCrypt）
--    哈希已用 bcrypt 校验：checkpw("123456") = true
-- ---------------------------------------------------------------------
INSERT INTO `sys_user`
  (`id`, `username`, `password`, `real_name`, `dept_id`, `status`, `user_type`, `data_scope`, `remark`)
VALUES
  (1, 'admin', '$2a$10$ao1cv30klBy6ZIMIszFjze0fAxLILQepiSZb/IX/mDoTAs7S3m8GW', '管理员', 1, 1, 1, 4,
   '系统内置超级管理员，不可删除、不可禁用');

-- 绑定内置 admin 角色（V5 已建 id=1）
INSERT INTO `sys_user_role` (`user_id`, `role_id`) VALUES (1, 1);
