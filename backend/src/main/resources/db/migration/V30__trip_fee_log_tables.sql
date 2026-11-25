-- ============================================================================
-- V30 · 车次费用明细 / 车次操作日志
-- 对应方案：transport-loading-design-v1.3.md 3.4 / 3.6 / 第 12 节 V30
-- ============================================================================
SET NAMES utf8mb4;

CREATE TABLE `biz_trip_fee` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键ID，自增',
  `trip_id` BIGINT UNSIGNED NOT NULL COMMENT '车次ID：逻辑引用biz_trip.id',
  `trip_dest_id` BIGINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '子车次ID：0=车次级费用（全车录入区）；>0=目的网点级费用（到站卸车页录入的到站卸车费/其他费）',
  `fee_code` VARCHAR(16) NOT NULL COMMENT '费用项编码：引用sys_fee_type.fee_code，如FEE0010',
  `fee_name` VARCHAR(64) NOT NULL COMMENT '费用项名称快照',
  `fee_category` TINYINT UNSIGNED NOT NULL DEFAULT 2 COMMENT '费用类型快照：1=收入，2=成本，3=代收，4=代付；本表实际只落2=成本',
  `amount` DECIMAL(14,2) NOT NULL DEFAULT 0 COMMENT '费用金额(元)：≥0；trip_dest_id=0时为全车该项总额，>0时为该子车次录入额',
  `profit_enabled` TINYINT(1) NOT NULL DEFAULT 0 COMMENT '参与毛利快照：1=参与，0=不参与；保存时从sys_fee_type复制',
  `profit_direction` TINYINT UNSIGNED NULL DEFAULT NULL COMMENT '参与方向快照：1=收入，2=成本；profit_enabled=0时为空',
  `fee_status` TINYINT UNSIGNED NOT NULL DEFAULT 1 COMMENT '费用状态：1=有效，2=已失效（取消到站/取消卸车时置2；金额保留用于审计，不参与汇总与分摊）',
  `invalid_reason` VARCHAR(200) NULL DEFAULT NULL COMMENT '失效原因：取消到站/取消卸车时写入，如「取消卸车（子车次CT20261125001-01）」；有效时为空',
  `sort_order` INT NOT NULL DEFAULT 0 COMMENT '排序号：费用录入区展示顺序',
  `dept_id` BIGINT UNSIGNED NOT NULL COMMENT '归属机构ID：=主车次dept_id；数据权限锚点',
  `create_by` BIGINT UNSIGNED NULL DEFAULT NULL COMMENT '创建人用户ID',
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_by` BIGINT UNSIGNED NULL DEFAULT NULL COMMENT '最后修改人用户ID',
  `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '最后修改时间',
  `deleted` TINYINT(1) NOT NULL DEFAULT 0 COMMENT '逻辑删除：0=正常，1=已删除；唯一键不含deleted，删除后重录需复用原行（服务层upsert）',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_trip_fee` (`trip_id`, `trip_dest_id`, `fee_code`),
  KEY `idx_trip` (`trip_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci
  COMMENT='车次费用明细表：只存车次录入的成本项；四类运费收入不进本表';

CREATE TABLE `biz_trip_log` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键ID，自增',
  `trip_id` BIGINT UNSIGNED NOT NULL COMMENT '车次ID：逻辑引用biz_trip.id',
  `trip_no` VARCHAR(32) NOT NULL COMMENT '车次编号快照：不联表即可展示',
  `action_type` TINYINT UNSIGNED NOT NULL COMMENT '动作类型：1=创建，2=修改，3=配载，4=移除配载，5=发车，6=取消发车，7=到站，8=完成卸车，9=关闭，10=删除，11=车辆信息变更，12=费用信息变更，13=运单改单刷新，14=运单作废解除配载，15=目的网点变更，16=取消到站，17=取消卸车',
  `dest_id` BIGINT UNSIGNED NULL DEFAULT NULL COMMENT '子车次ID：到站/卸车/取消到站/取消卸车动作记录；其他动作为空',
  `before_snapshot` JSON NULL DEFAULT NULL COMMENT '操作前快照：状态变更类必填，记录车次状态、运单集合、费用汇总等关键字段',
  `after_snapshot` JSON NULL DEFAULT NULL COMMENT '操作后快照：结构同before_snapshot',
  `reason` VARCHAR(500) NULL DEFAULT NULL COMMENT '操作原因：取消发车、取消到站、取消卸车、关闭、删除时必填',
  `operator_id` BIGINT UNSIGNED NOT NULL COMMENT '操作人用户ID',
  `operator_name` VARCHAR(50) NOT NULL COMMENT '操作人姓名快照',
  `dept_id` BIGINT UNSIGNED NOT NULL COMMENT '操作机构ID：数据权限锚点',
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '操作时间：写日志时的服务器时间',
  PRIMARY KEY (`id`),
  KEY `idx_trip_time` (`trip_id`, `create_time`),
  KEY `idx_action` (`action_type`, `create_time`),
  KEY `idx_dept` (`dept_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci
  COMMENT='车次操作日志表：配载、发车、到站、卸车、取消与费用变更的全量留痕';
