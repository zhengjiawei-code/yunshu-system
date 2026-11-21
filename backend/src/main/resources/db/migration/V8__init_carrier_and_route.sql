-- ============================================================
-- V8：承运商 + 路由两表 + 路由授权
-- 依据：2026-11-21/route-region-design/outputs/route-region-design-v2.1.md 第三部分
-- 说明：四张表均豁免数据权限行级过滤，可见性由服务层显式计算
-- ============================================================

CREATE TABLE `sys_carrier` (
  `id`           BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
  `carrier_code` VARCHAR(32)  NOT NULL COMMENT '承运商编码，机构内唯一（应用层校验）',
  `carrier_name` VARCHAR(64)  NOT NULL COMMENT '承运商名称',
  `contact`      VARCHAR(32)  NOT NULL COMMENT '联系人',
  `phone`        VARCHAR(20)  NOT NULL COMMENT '联系电话',
  `status`       TINYINT(1)   NOT NULL DEFAULT 1 COMMENT '状态：1=启用 0=停用',
  `sort_order`   INT          NOT NULL DEFAULT 0 COMMENT '排序号，升序',
  `remark`       VARCHAR(500) DEFAULT NULL COMMENT '备注',
  `dept_id`      BIGINT UNSIGNED NOT NULL COMMENT '归属机构ID：可见性锚点，新增时按当前主机构写入',
  `create_by`    BIGINT UNSIGNED DEFAULT NULL COMMENT '创建人用户ID',
  `create_time`  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_by`    BIGINT UNSIGNED DEFAULT NULL COMMENT '最后修改人用户ID',
  `update_time`  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '最后修改时间',
  `deleted`      TINYINT(1)   NOT NULL DEFAULT 0 COMMENT '逻辑删除：0=未删除 1=已删除',
  PRIMARY KEY (`id`),
  KEY `idx_code` (`carrier_code`),
  KEY `idx_name` (`carrier_name`),
  KEY `idx_status` (`status`),
  KEY `idx_dept` (`dept_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci
  COMMENT='承运商表：外转/直达路由的默认承运商主数据';

CREATE TABLE `sys_route` (
  `id`               BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
  `route_code`       VARCHAR(32)  NOT NULL COMMENT '路由编码：短驳 DB/干线 GX + 6 位流水，创建后不可改',
  `route_category`   TINYINT UNSIGNED NOT NULL COMMENT '路由类型：1=短驳 2=干线',
  `origin_dept_id`   BIGINT UNSIGNED NOT NULL COMMENT '始发网点ID，引用 sys_dept.id',
  `dest_dept_id`     BIGINT UNSIGNED NOT NULL COMMENT '到达网点ID，引用 sys_dept.id',
  `route_name`       VARCHAR(100) NOT NULL COMMENT '线路名称：干线默认“到达网点名+线路”，允许修改',
  `warehouse_area`   VARCHAR(100) DEFAULT NULL COMMENT '库区：干线必填，手工填写；打印标签直接取该值',
  `mileage`          DECIMAL(10,2) DEFAULT NULL COMMENT '线路里程（公里），2 位小数；干线必填',
  `transit_hours`    DECIMAL(10,2) DEFAULT NULL COMMENT '运输时效（小时），2 位小数；干线必填',
  `contact_name`     VARCHAR(50)  DEFAULT NULL COMMENT '线路联系人；干线必填',
  `contact_phone`    VARCHAR(20)  DEFAULT NULL COMMENT '线路联系电话；干线必填',
  `service_name`     VARCHAR(50)  DEFAULT NULL COMMENT '线路客服姓名；选填',
  `service_phone`    VARCHAR(20)  DEFAULT NULL COMMENT '线路客服电话；选填',
  `owner_company_id` BIGINT UNSIGNED NOT NULL COMMENT '创建公司ID（总部或分公司）：归属展示、编辑与删除权限',
  `status`           TINYINT(1)   NOT NULL DEFAULT 1 COMMENT '状态：1=启用 0=禁用',
  `sort_order`       INT          NOT NULL DEFAULT 0 COMMENT '排序号，升序',
  `remark`           VARCHAR(500) DEFAULT NULL COMMENT '备注',
  `dept_id`          BIGINT UNSIGNED NOT NULL COMMENT '归属机构ID（创建人主机构）：审计留痕',
  `create_by`        BIGINT UNSIGNED DEFAULT NULL COMMENT '创建人用户ID',
  `create_time`      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_by`        BIGINT UNSIGNED DEFAULT NULL COMMENT '最后修改人用户ID',
  `update_time`      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '最后修改时间',
  `deleted`          TINYINT(1)   NOT NULL DEFAULT 0 COMMENT '逻辑删除：0=未删除 1=已删除',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_route_code` (`route_code`),
  KEY `idx_business_key` (`route_category`, `origin_dept_id`, `dest_dept_id`),
  KEY `idx_owner_company` (`owner_company_id`),
  KEY `idx_category_status` (`route_category`, `status`),
  KEY `idx_origin` (`origin_dept_id`),
  KEY `idx_dest` (`dest_dept_id`),
  KEY `idx_name` (`route_name`),
  KEY `idx_dept` (`dept_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci
  COMMENT='短驳/干线路由表：网点与分拨中心之间的运输线路及线路属性';

CREATE TABLE `sys_route_transfer` (
  `id`               BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
  `route_code`       VARCHAR(32)  NOT NULL COMMENT '路由编码：区划编码_ZT（直达）/_WZ（外转），自动生成',
  `route_name`       VARCHAR(128) NOT NULL COMMENT '路由名称：默认区划全路径，可修改',
  `transfer_type`    TINYINT UNSIGNED NOT NULL COMMENT '类型：1=直达 2=外转',
  `apply_dept_id`    BIGINT UNSIGNED NOT NULL COMMENT '适用网点ID，引用 sys_dept.id；业务字段',
  `region_code`      VARCHAR(12)  NOT NULL COMMENT '目的地行政区划编码，引用 sys_region.code',
  `region_level`     TINYINT UNSIGNED NOT NULL COMMENT '目的地粒度：2=市 3=区县 4=乡级',
  `region_full_name` VARCHAR(200) NOT NULL COMMENT '目的地全路径名称（展示用）',
  `carrier_id`       BIGINT UNSIGNED DEFAULT NULL COMMENT '默认承运商ID；仅外转可填',
  `owner_company_id` BIGINT UNSIGNED NOT NULL COMMENT '创建公司ID（总部或分公司）：归属展示、编辑与删除权限',
  `status`           TINYINT(1)   NOT NULL DEFAULT 1 COMMENT '状态：1=启用 0=停用',
  `sort_order`       INT          NOT NULL DEFAULT 0 COMMENT '排序号，升序',
  `remark`           VARCHAR(500) DEFAULT NULL COMMENT '备注',
  `dept_id`          BIGINT UNSIGNED NOT NULL COMMENT '归属机构ID（创建人主机构）：审计留痕',
  `create_by`        BIGINT UNSIGNED DEFAULT NULL COMMENT '创建人用户ID',
  `create_time`      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_by`        BIGINT UNSIGNED DEFAULT NULL COMMENT '最后修改人用户ID',
  `update_time`      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '最后修改时间',
  `deleted`          TINYINT(1)   NOT NULL DEFAULT 0 COMMENT '逻辑删除：0=未删除 1=已删除',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_route_code` (`route_code`),
  KEY `idx_business_key` (`apply_dept_id`, `region_code`, `transfer_type`),
  KEY `idx_owner_company` (`owner_company_id`),
  KEY `idx_region` (`region_code`),
  KEY `idx_type_status` (`transfer_type`, `status`),
  KEY `idx_carrier` (`carrier_id`),
  KEY `idx_dept` (`dept_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci
  COMMENT='外转/直达路由表：适用网点 + 目的地行政区划配置，支持直达与外转并存与跨公司授权';

CREATE TABLE `sys_route_company` (
  `id`              BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
  `route_scope`     TINYINT UNSIGNED NOT NULL COMMENT '路由域：1=短驳/干线(sys_route) 2=外转/直达(sys_route_transfer)',
  `route_id`        BIGINT UNSIGNED NOT NULL COMMENT '路由ID，按 route_scope 指向对应路由表',
  `company_dept_id` BIGINT UNSIGNED NOT NULL COMMENT '被授权分公司ID，引用 sys_dept.id；0=全部分公司（动态覆盖后续新增，不展开快照）',
  `grant_source`    TINYINT UNSIGNED NOT NULL COMMENT '授权来源：1=创建自动 2=手动 3=总部全部（company_dept_id=0）',
  `grant_by`        BIGINT UNSIGNED DEFAULT NULL COMMENT '授权人用户ID',
  `grant_time`      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '授权时间',
  `remark`          VARCHAR(200) DEFAULT NULL COMMENT '备注：同键自动补授权、批量授权说明等',
  `create_time`     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_route_company` (`route_scope`, `route_id`, `company_dept_id`),
  KEY `idx_company` (`company_dept_id`),
  KEY `idx_route` (`route_scope`, `route_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci
  COMMENT='路由授权分公司表：自动/手动/全部三类来源，支撑动态全量与审计回收';
