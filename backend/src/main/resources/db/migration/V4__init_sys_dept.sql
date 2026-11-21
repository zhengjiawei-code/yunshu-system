-- =====================================================================
-- 运输管理系统 · 机构表（sys_dept）
-- 文件：V4__init_sys_dept.sql
-- 状态：**待家伟确认，尚未执行**；确认后才放入 backend/src/main/resources/db/migration，
--       由 Flyway 在启动时执行（当前放置目录不会被 Flyway 扫描）。
-- 目标库：yunshu（仅此库）
-- 数据库：MySQL 8.0；字符集 utf8mb4 / utf8mb4_0900_ai_ci
-- 设计依据：2026-11-20/system-management/outputs/system-management-design.md 2.2 / 4.x，
--           org-management-fields.md v1.2
-- 业务说明：
--   1) 机构是单表树（parent_id 自关联），最多 5 级；
--   2) 全库唯一总部：内置 id=1、parent_id=0、dept_type=1，不可删除/禁用/移动/改类型；
--   3) 四个业务开关是“机构级业务能力”，不属于功能权限（不写 sys_role_menu），不随父机构继承；
--   4) 编码全局唯一、同级名称唯一由 Service 在 deleted=0 范围内校验：
--      逻辑删除后旧行仍占用编码，若建唯一索引会阻止复用，故与 sys_menu 保持一致不建唯一索引；
--   5) 父子类型矩阵、层级上限、总部保护、删除拦截均由 Service 校验（见 4.x），DDL 不建触发器；
--   6) 2026-11-20 追加 is_leaf（是否末端）：表尚未创建，直接并入本脚本，不单独出 V5。
--   7) 2026-11-20 追加 dept_nature（机构性质，自营/加盟/合作）：表尚未创建，直接并入本脚本，不单独出 V5。
--   8) is_leaf 是业务标识，只表示“该网点是否为末端网点”，与结构上是否有下级无关；
--      不要在机构增删/移动逻辑中当作结构叶子去联动。
-- 回滚：DROP TABLE sys_dept（本期为新表，无历史数据依赖；仅需确认无业务已引用）
-- =====================================================================

SET NAMES utf8mb4;

CREATE TABLE `sys_dept` (
  `id`                         BIGINT UNSIGNED NOT NULL AUTO_INCREMENT
                               COMMENT '机构主键ID，自增；被 sys_user.dept_id 与 sys_user_org.org_id 引用；id=1 固定为内置总部',
  `parent_id`                  BIGINT UNSIGNED NOT NULL DEFAULT 0
                               COMMENT '上级机构ID；0=顶级（仅内置总部使用）；其余机构必须指向一个存在的机构；禁止形成环',
  `dept_name`                  VARCHAR(50)     NOT NULL
                               COMMENT '机构名称；展示在机构树、用户所属机构与单据机构字段；同一上级下不可重名（deleted=0 范围内）',
  `dept_code`                  VARCHAR(50)     NOT NULL
                               COMMENT '机构编码；2-20 位字母/数字/下划线，全局唯一（deleted=0 范围内由 Service 校验）；创建后不可修改，供对接与导入使用',
  `dept_type`                  TINYINT UNSIGNED NOT NULL DEFAULT 3
                               COMMENT '机构类型：1=总部 2=公司 3=部门 4=分拨中心 5=网点；决定允许的父子关系（见设计 4.2 矩阵）；仅 1 可作为树根且全库唯一',
  `dept_nature`                TINYINT UNSIGNED NOT NULL DEFAULT 1
                               COMMENT '机构性质：1=自营 2=加盟 3=合作；必填，默认自营；内置总部（id=1）固定为 1，不可修改（Service 校验）；与 dept_type 正交，不参与父子矩阵判断',
  `is_leaf`                    TINYINT(1)      NOT NULL DEFAULT 0
                               COMMENT '是否末端：1=末端网点，0=非末端；仅 dept_type=5（网点）可置 1，其它类型必须为 0（Service 校验并自动清空）；纯业务标识，与该机构结构上是否有下级无关',
  `allow_ship`                 TINYINT(1)      NOT NULL DEFAULT 1
                               COMMENT '能否发货：1=允许该机构作为发货机构开单，0=禁止；开单模块保存时校验；不随父机构继承',
  `allow_arrival`              TINYINT(1)      NOT NULL DEFAULT 1
                               COMMENT '能否到货：1=允许该机构作为到货机构，0=禁止；到货卸车模块校验；不随父机构继承',
  `allow_prepaid_unpaid`       TINYINT(1)      NOT NULL DEFAULT 0
                               COMMENT '现付未付开单：1=客户现付运费未结清时允许开单，0=必须先收款；资金风险项，默认关闭；开单模块校验',
  `allow_collect_unpaid_sign`  TINYINT(1)      NOT NULL DEFAULT 0
                               COMMENT '到付未付签收：1=到付运费未结清时允许签收，0=必须先结清；资金风险项，默认关闭；签收模块校验',
  `leader`                     VARCHAR(50)     DEFAULT NULL
                               COMMENT '负责人姓名；仅展示与联系，不参与鉴权与业务计算',
  `phone`                      VARCHAR(20)     DEFAULT NULL
                               COMMENT '机构联系电话；手机或座机，仅展示与联系，不参与业务计算',
  `address`                    VARCHAR(200)    DEFAULT NULL
                               COMMENT '机构详细地址；用于单据展示与打印，不参与业务计算',
  `sort_order`                 INT             NOT NULL DEFAULT 0
                               COMMENT '同级排序号，升序排列（数值越小越靠前）；同一上级下建议按 10 递增，便于中间插入',
  `status`                     TINYINT(1)      NOT NULL DEFAULT 1
                               COMMENT '状态：1=启用（可被分配用户、可作为业务机构、可作为上级新增机构），0=禁用（不可新分配用户、不可在其下新增机构）；总部不可禁用',
  `remark`                     VARCHAR(500)    DEFAULT NULL
                               COMMENT '备注；记录变更原因或业务说明，不参与业务计算',
  `create_by`                  BIGINT UNSIGNED DEFAULT NULL
                               COMMENT '创建人用户ID，关联 sys_user.id；种子数据为 NULL',
  `create_time`                DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP
                               COMMENT '创建时间；插入时自动生成，后续更新不变',
  `update_by`                  BIGINT UNSIGNED DEFAULT NULL
                               COMMENT '最后修改人用户ID，关联 sys_user.id；种子数据为 NULL',
  `update_time`                DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
                               COMMENT '最后修改时间；更新时由数据库自动刷新，列表“最后修改时间”列直接展示',
  `deleted`                    TINYINT(1)      NOT NULL DEFAULT 0
                               COMMENT '逻辑删除：0=未删除，1=已删除；查询必须带 deleted=0；删除前校验子机构与用户引用',
  PRIMARY KEY (`id`),
  KEY `idx_parent_sort` (`parent_id`, `sort_order`),
  KEY `idx_code` (`dept_code`),
  KEY `idx_status` (`status`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci
  COMMENT = '机构表：集团-公司-部门-分拨中心-网点 的树形组织架构（最多 5 级），同时承载机构级业务授权开关（能否发货/到货、现付未付开单、到付未付签收）。不含角色与功能权限，功能权限见 sys_role_menu / sys_user_menu。';

-- 内置总部（全库唯一根，id 固定为 1）：
-- 不可删除、不可禁用、不可移动、不可改类型；编码固定 HQ，创建后不可修改。
INSERT INTO `sys_dept`
  (`id`, `parent_id`, `dept_name`, `dept_code`, `dept_type`, `dept_nature`,
   `allow_ship`, `allow_arrival`, `allow_prepaid_unpaid`, `allow_collect_unpaid_sign`,
   `leader`, `phone`, `address`, `sort_order`, `status`, `remark`)
VALUES
  (1, 0, '总部', 'HQ', 1, 1,
   1, 1, 0, 0,
   NULL, NULL, NULL, 10, 1, '系统内置总部，不可删除、不可禁用、不可移动、类型不可修改');
