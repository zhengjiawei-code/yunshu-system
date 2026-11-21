-- =====================================================================
-- 运输管理系统 · V7：区划基础数据 + 机构地址结构化 + 末端网点适用分公司
-- 数据库：MySQL 8.0；字符集 utf8mb4 / utf8mb4_0900_ai_ci
-- 前置：V6 已执行（sys_dept 已存在）
-- 设计依据：2026-11-21/route-region-design/outputs/route-region-design-v2.1.md
-- 说明：
--   1) 区划数据不写入 Flyway，由区域管理页离线 JSON 幂等导入；
--   2) sys_dept 只存省/市/区县编码，名称统一 join sys_region，避免双写漂移；
--   3) 末端网点适用分公司为多对多关系，决定分公司管理员的管理范围与机构选择器；
--   4) 本脚本仅新增表/列/索引，不修改、不删除任何既有数据。
-- =====================================================================

SET NAMES utf8mb4;

-- ---------------------------------------------------------------------
-- 1. 行政区划表
-- ---------------------------------------------------------------------
CREATE TABLE `sys_region` (
  `code`        VARCHAR(12)  NOT NULL
                COMMENT '行政区划编码（主键）：省市县为 GB/T 2260 六位，乡级为统计局 9/12 位',
  `name`        VARCHAR(64)  NOT NULL
                COMMENT '区划名称（本级）',
  `parent_code` VARCHAR(12)  NOT NULL DEFAULT '0'
                COMMENT '上级区划编码；省级固定为 0',
  `level`       TINYINT UNSIGNED NOT NULL
                COMMENT '层级：1=省 2=市 3=区县 4=乡级',
  `full_name`   VARCHAR(200) NOT NULL DEFAULT ''
                COMMENT '全路径名称，如 广东省广州市白云区；由服务端按父子链计算',
  `source`      VARCHAR(16)  NOT NULL DEFAULT 'offline'
                COMMENT '数据来源：offline=离线JSON online=在线同步 init=初始化',
  `version`     VARCHAR(12)  NOT NULL DEFAULT ''
                COMMENT '数据版本，如 2025Q4 或 YYYYMM',
  `sync_time`   DATETIME     DEFAULT NULL
                COMMENT '最近一次导入/同步时间',
  `status`      TINYINT(1)   NOT NULL DEFAULT 1
                COMMENT '状态：1=有效 0=停用（历史区划保留，不物理删除）',
  `create_time` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP
                COMMENT '创建时间',
  `update_time` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
                COMMENT '更新时间',
  PRIMARY KEY (`code`),
  KEY `idx_parent_code` (`parent_code`),
  KEY `idx_name` (`name`),
  KEY `idx_level` (`level`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci
  COMMENT = '行政区划表：省/市/区县/乡级基础数据，平台级共享，不参与数据权限过滤';

-- ---------------------------------------------------------------------
-- 2. 区划导入/同步任务日志
-- ---------------------------------------------------------------------
CREATE TABLE `sys_region_sync_log` (
  `id`              BIGINT UNSIGNED NOT NULL AUTO_INCREMENT
                    COMMENT '主键',
  `job_id`          VARCHAR(32)  NOT NULL
                    COMMENT '任务ID（UUID），用于幂等与报告查询',
  `trigger_type`    VARCHAR(16)  NOT NULL
                    COMMENT '触发方式：offline=离线JSON online=在线同步',
  `triggered_by`    BIGINT UNSIGNED DEFAULT NULL
                    COMMENT '触发人用户ID；豁免表不触发自动填充，由服务层显式写入',
  `file_name`       VARCHAR(200) DEFAULT NULL
                    COMMENT '离线导入文件名（在线同步为空）',
  `data_version`    VARCHAR(12)  DEFAULT NULL
                    COMMENT '本次导入的数据版本',
  `started_at`      DATETIME     NOT NULL
                    COMMENT '开始时间',
  `finished_at`     DATETIME     DEFAULT NULL
                    COMMENT '结束时间',
  `status`          VARCHAR(16)  NOT NULL
                    COMMENT '状态：pending/running/success/failed/partial',
  `progress`        INT          NOT NULL DEFAULT 0
                    COMMENT '进度百分比 0-100',
  `total_count`     INT          NOT NULL DEFAULT 0
                    COMMENT '文件/远端总条数',
  `added_count`     INT          NOT NULL DEFAULT 0
                    COMMENT '新增条数',
  `changed_count`   INT          NOT NULL DEFAULT 0
                    COMMENT '变更条数',
  `unchanged_count` INT          NOT NULL DEFAULT 0
                    COMMENT '无变化条数',
  `disabled_count`  INT          NOT NULL DEFAULT 0
                    COMMENT '待停用（文件中缺失）条数',
  `report_json`     MEDIUMTEXT   DEFAULT NULL
                    COMMENT '差异明细 JSON：新增/改动/待停用/校验失败',
  `error_msg`       VARCHAR(500) DEFAULT NULL
                    COMMENT '失败原因',
  `create_time`     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP
                    COMMENT '创建时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_job_id` (`job_id`),
  KEY `idx_started_at` (`started_at`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci
  COMMENT = '区划导入/同步任务日志：记录过程、差异与可追溯报告';

-- ---------------------------------------------------------------------
-- 3. 机构表增加省/市/区县编码
-- ---------------------------------------------------------------------
ALTER TABLE `sys_dept`
  ADD COLUMN `province_code` VARCHAR(12) DEFAULT NULL
    COMMENT '省编码，引用 sys_region.code（level=1）' AFTER `address`,
  ADD COLUMN `city_code` VARCHAR(12) DEFAULT NULL
    COMMENT '市编码，引用 sys_region.code（level=2）' AFTER `province_code`,
  ADD COLUMN `district_code` VARCHAR(12) DEFAULT NULL
    COMMENT '区县编码，引用 sys_region.code（level=3）' AFTER `city_code`,
  ADD KEY `idx_region` (`province_code`, `city_code`, `district_code`);

-- ---------------------------------------------------------------------
-- 4. 末端网点适用分公司关系
-- ---------------------------------------------------------------------
CREATE TABLE `sys_dept_apply_company` (
  `id`              BIGINT UNSIGNED NOT NULL AUTO_INCREMENT
                    COMMENT '主键',
  `leaf_dept_id`    BIGINT UNSIGNED NOT NULL
                    COMMENT '末端网点ID，引用 sys_dept.id，要求 is_leaf=1',
  `company_dept_id` BIGINT UNSIGNED NOT NULL
                    COMMENT '适用分公司ID，引用 sys_dept.id，要求 dept_type=2 且启用',
  `create_by`       BIGINT UNSIGNED DEFAULT NULL
                    COMMENT '创建人用户ID；豁免表不触发自动填充，由服务层显式写入',
  `create_time`     DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
                    COMMENT '创建时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_leaf_company` (`leaf_dept_id`, `company_dept_id`),
  KEY `idx_company` (`company_dept_id`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci
  COMMENT = '末端网点适用分公司关系表：支撑共用末端网点跨分公司可见与选择';
