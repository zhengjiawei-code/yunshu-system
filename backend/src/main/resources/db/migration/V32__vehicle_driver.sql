-- ============================================================================
-- V32 · 车辆 / 司机最小主数据（D1）
-- 对应方案：transport-loading-design-v1.3.md 3.10 / 第 12 节 V32
-- ============================================================================
SET NAMES utf8mb4;

CREATE TABLE `sys_vehicle` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键ID，自增',
  `plate_no` VARCHAR(20) NOT NULL COMMENT '车牌号：唯一，车次选择/快速新增的唯一键',
  `vehicle_type` VARCHAR(50) NULL DEFAULT NULL COMMENT '车辆型号：文本，本期不建车型表（菜单1903后续补）',
  `vehicle_load` DECIMAL(12,3) NULL DEFAULT NULL COMMENT '核定载重(kg)：车次超重校验来源，选择后带出、不可改',
  `vehicle_volume` DECIMAL(12,3) NULL DEFAULT NULL COMMENT '核定容积(m³)：车次超容校验来源，选择后带出、不可改',
  `owner_name` VARCHAR(50) NULL DEFAULT NULL COMMENT '车主名称：自有/外协车主',
  `owner_phone` VARCHAR(20) NULL DEFAULT NULL COMMENT '车主电话',
  `status` TINYINT UNSIGNED NOT NULL DEFAULT 1 COMMENT '状态：1=启用，0=停用；停用后不在选择器出现',
  `dept_id` BIGINT UNSIGNED NOT NULL COMMENT '归属机构ID：车辆所属机构；数据权限锚点',
  `create_by` BIGINT UNSIGNED NULL DEFAULT NULL COMMENT '创建人用户ID',
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_by` BIGINT UNSIGNED NULL DEFAULT NULL COMMENT '最后修改人用户ID',
  `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '最后修改时间',
  `deleted` TINYINT(1) NOT NULL DEFAULT 0 COMMENT '逻辑删除：0=正常，1=已删除；同一车牌第二次删除会与历史删除行唯一键冲突，恢复原行即可',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_plate_no` (`plate_no`, `deleted`),
  KEY `idx_dept_status` (`dept_id`, `status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci
  COMMENT='车辆最小主数据表：车次选择与快速新增使用，完整运力管理后续建设';

CREATE TABLE `sys_driver` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键ID，自增',
  `driver_name` VARCHAR(50) NOT NULL COMMENT '司机姓名',
  `driver_phone` VARCHAR(20) NOT NULL COMMENT '司机电话：车次发车前必填项来源；不唯一，允许重名同号不同人',
  `license_no` VARCHAR(30) NULL DEFAULT NULL COMMENT '驾驶证号',
  `status` TINYINT UNSIGNED NOT NULL DEFAULT 1 COMMENT '状态：1=启用，0=停用；停用后不在选择器出现',
  `dept_id` BIGINT UNSIGNED NOT NULL COMMENT '归属机构ID：司机所属机构；数据权限锚点',
  `create_by` BIGINT UNSIGNED NULL DEFAULT NULL COMMENT '创建人用户ID',
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_by` BIGINT UNSIGNED NULL DEFAULT NULL COMMENT '最后修改人用户ID',
  `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '最后修改时间',
  `deleted` TINYINT(1) NOT NULL DEFAULT 0 COMMENT '逻辑删除：0=正常，1=已删除',
  PRIMARY KEY (`id`),
  KEY `idx_name_phone` (`driver_name`, `driver_phone`),
  KEY `idx_dept_status` (`dept_id`, `status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci
  COMMENT='司机最小主数据表：车次选择与快速新增使用';
