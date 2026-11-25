-- ============================================================================
-- V33 · 系统全局配置表 + 2 键种子；线路表新增重泡比列
-- 对应方案：transport-loading-design-v1.3.md 3.11 / 3.12 / 4.3 / 6.11 / 第 12 节 V33
-- ============================================================================
SET NAMES utf8mb4;

CREATE TABLE `sys_config` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键ID，自增',
  `config_key` VARCHAR(64) NOT NULL COMMENT '配置键：全局唯一，如fee.volume_weight_ratio、gross.formula',
  `config_value` TEXT NOT NULL COMMENT '配置值：字符串存储；数值型为数值字符串（如333），JSON型存JSON文本（如毛利公式）',
  `config_name` VARCHAR(100) NOT NULL COMMENT '配置名称：中文展示名，如「全局默认重泡比(kg/m³)」',
  `value_type` VARCHAR(16) NOT NULL DEFAULT 'string' COMMENT '值类型：string/number/bool/json',
  `remark` VARCHAR(500) NULL DEFAULT NULL COMMENT '说明：业务含义、取值范围',
  `create_by` BIGINT UNSIGNED NULL DEFAULT NULL COMMENT '创建人用户ID；种子为NULL',
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_by` BIGINT UNSIGNED NULL DEFAULT NULL COMMENT '最后修改人用户ID',
  `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '最后修改时间',
  `deleted` TINYINT(1) NOT NULL DEFAULT 0 COMMENT '逻辑删除：0=正常，1=已删除；平台配置不做删除',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_config_key` (`config_key`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci
  COMMENT='系统全局配置表：重泡比默认值与运单毛利公式等平台级配置，需登记数据权限豁免';

INSERT INTO `sys_config` (`config_key`,`config_value`,`config_name`,`value_type`,`remark`) VALUES
('fee.volume_weight_ratio','333','全局默认重泡比(kg/m³)','number','取值>0；线路未配置时回落此值')
ON DUPLICATE KEY UPDATE
  config_value=VALUES(config_value),config_name=VALUES(config_name),value_type=VALUES(value_type),remark=VALUES(remark),deleted=0;

INSERT INTO `sys_config` (`config_key`,`config_value`,`config_name`,`value_type`,`remark`) VALUES
('gross.formula',
 '{"version":1,"rebateDeduct":true,"items":[{"feeCode":"INC01","name":"现付运费","side":"add"},{"feeCode":"INC02","name":"到付运费","side":"add"},{"feeCode":"INC03","name":"回付运费","side":"add"},{"feeCode":"INC04","name":"月结运费","side":"add"},{"feeCode":"FEE0010","name":"现付运输费","side":"sub"},{"feeCode":"FEE0011","name":"回付运输费","side":"sub"},{"feeCode":"FEE0012","name":"到站付运输费","side":"sub"},{"feeCode":"FEE0013","name":"现付油卡费","side":"sub"},{"feeCode":"FEE0014","name":"整车过磅费","side":"sub"},{"feeCode":"FEE0015","name":"整车保险费","side":"sub"},{"feeCode":"FEE0016","name":"分拨装车费","side":"sub"},{"feeCode":"FEE0017","name":"分拨卸车费","side":"sub"},{"feeCode":"FEE0018","name":"分拨其他费","side":"sub"},{"feeCode":"FEE0019","name":"到站卸车费","side":"sub"},{"feeCode":"FEE0020","name":"到站其他费","side":"sub"}],"updatedBy":"system","updatedAt":"2026-11-25 00:00:00","history":[]}',
 '运单毛利计算公式','json','单票公式：收入加项 − 返款(rebateDeduct=true时) − 成本减项；version自增，history保留最近20版')
ON DUPLICATE KEY UPDATE
  config_value=VALUES(config_value),config_name=VALUES(config_name),value_type=VALUES(value_type),remark=VALUES(remark),deleted=0;

ALTER TABLE `sys_route`
  ADD COLUMN `volume_weight_ratio` DECIMAL(10,2) NULL DEFAULT NULL
    COMMENT '线路重泡比(kg/m³)：线路级覆盖；为空或0时回落sys_config.fee.volume_weight_ratio全局值；新建线路前端默认带出全局值',
  ALGORITHM=INSTANT;
