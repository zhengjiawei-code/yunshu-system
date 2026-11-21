-- =====================================================================
-- 运输管理系统 · 菜单权限表
-- 文件：V1__init_sys_menu.sql（Flyway 迁移脚本，本轮仅产出，未执行）
-- 数据库：MySQL 8.0
-- 字符集：utf8mb4 / utf8mb4_0900_ai_ci
-- 说明：本表同时承载目录、菜单、按钮三级节点，是侧栏渲染、动态路由、
--       按钮级权限校验的唯一数据源；不含角色/用户关系表（见方案 9.3）。
-- =====================================================================

SET NAMES utf8mb4;

CREATE TABLE `sys_menu` (
  `id`            BIGINT UNSIGNED NOT NULL AUTO_INCREMENT
                  COMMENT '节点主键ID，自增；被 sys_role_menu.menu_id 与 sys_user_menu.menu_id 引用',
  `parent_id`     BIGINT UNSIGNED NOT NULL DEFAULT 0
                  COMMENT '上级节点ID；0=顶级节点；目录的父级只能是0或目录，菜单的父级必须是目录，按钮的父级必须是菜单',
  `menu_name`     VARCHAR(50)     NOT NULL
                  COMMENT '节点显示名称；目录/菜单展示在侧栏、面包屑与标签页，按钮展示在角色授权树；同一父节点下不可重名',
  `menu_type`     TINYINT UNSIGNED NOT NULL DEFAULT 1
                  COMMENT '节点类型：1=目录（仅分组，不对应页面）2=菜单（对应一个前端页面组件并生成路由）3=按钮（页面内操作权限点，不生成路由）',
  `route_path`    VARCHAR(200)    DEFAULT NULL
                  COMMENT '前端路由地址：目录存路径段（如 /system），菜单存绝对路径（如 /system/user），按钮为NULL；非空时全局唯一',
  `component`     VARCHAR(200)    DEFAULT NULL
                  COMMENT '前端组件路径，相对 src/views 且不含 .vue 后缀（如 system/user/index）；仅菜单填写，目录与按钮为NULL',
  `redirect`      VARCHAR(200)    DEFAULT NULL
                  COMMENT '目录默认跳转地址，通常指向第一个子菜单；留空由前端自动选择；仅目录使用，菜单与按钮为NULL',
  `perms`         VARCHAR(100)    DEFAULT NULL
                  COMMENT '权限标识，格式为 模块:实体:动作（如 system:user:add）；按钮必填且全局唯一，菜单选填，目录为NULL',
  `icon`          VARCHAR(64)     DEFAULT NULL
                  COMMENT 'Element Plus 图标组件名（如 Setting）；目录与菜单必填，按钮为NULL',
  `portal_type`   VARCHAR(20)     NOT NULL DEFAULT 'ADMIN'
                  COMMENT '端归属：ADMIN=公司内部端（内部员工使用），CLIENT=客户端（发货客户自助使用）；决定菜单下发给哪个端',
  `terminal_type` VARCHAR(20)     NOT NULL DEFAULT 'PC'
                  COMMENT '适用终端：PC=电脑浏览器，APP=移动端，PC,APP=两端通用；本期只建设PC端，其余取值仅作预留',
  `sort_order`    INT             NOT NULL DEFAULT 0
                  COMMENT '同级排序号，升序排列（数值越小越靠前）；同一父节点下建议按10递增，便于后续插入',
  `visible`       TINYINT(1)      NOT NULL DEFAULT 1
                  COMMENT '侧栏是否显示：1=显示，0=隐藏；隐藏后不在侧栏出现，但已授权用户仍可通过URL直接访问',
  `status`        TINYINT(1)      NOT NULL DEFAULT 1
                  COMMENT '启用状态：1=启用（正常下发路由并参与权限校验），0=禁用（不生成路由，按钮鉴权直接拒绝）',
  `keep_alive`    TINYINT(1)      NOT NULL DEFAULT 1
                  COMMENT '前端页面是否缓存：1=缓存（切走再返回保留筛选与滚动位置），0=不缓存；仅菜单类型有效',
  `affix`         TINYINT(1)      NOT NULL DEFAULT 0
                  COMMENT '标签页是否固定：1=固定标签且不可关闭，0=可关闭；仅菜单类型有效',
  `remark`        VARCHAR(500)    DEFAULT NULL
                  COMMENT '备注，记录该节点的业务用途、负责人或变更原因，便于运维排查',
  `create_by`     BIGINT UNSIGNED DEFAULT NULL
                  COMMENT '创建人用户ID，关联 sys_user.id；系统初始化数据为NULL',
  `create_time`   DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP
                  COMMENT '创建时间，插入时自动生成，后续更新不变',
  `update_by`     BIGINT UNSIGNED DEFAULT NULL
                  COMMENT '最后修改人用户ID，关联 sys_user.id；系统初始化数据为NULL',
  `update_time`   DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
                  COMMENT '最后修改时间，插入时生成，更新时自动刷新',
  `deleted`       TINYINT(1)      NOT NULL DEFAULT 0
                  COMMENT '逻辑删除标记：0=未删除，1=已删除；查询必须带 deleted=0，删除前需校验子节点与角色/用户引用',
  PRIMARY KEY (`id`),
  KEY `idx_parent_sort` (`parent_id`, `sort_order`),
  KEY `idx_type_status` (`menu_type`, `status`),
  KEY `idx_portal` (`portal_type`, `status`),
  KEY `idx_perms` (`perms`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci
  COMMENT = '菜单权限表（系统管理模块）：存储后台的目录、菜单、按钮三级节点。目录仅用于分组，菜单对应一个前端页面并生成路由，按钮对应页面内操作权限点；同时记录端归属、适用终端、排序、显隐与启用状态，供侧栏渲染、动态路由生成和按钮级鉴权使用。';
