-- ============================================================================
-- V31 · 费用类型配置表 + 16 项种子（按 4.1 口径：14 成本 + 现返/欠返）
-- 对应方案：transport-loading-design-v1.3.md 3.8 / 4.1 / 第 12 节 V31
-- ============================================================================
SET NAMES utf8mb4;

CREATE TABLE `sys_fee_type` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键ID，自增',
  `fee_code` VARCHAR(16) NOT NULL COMMENT '费用项编码：成本/代收/代付为FEE+4位流水（如FEE0010）；种子预置，生成后不可改；收入内置四项INC01-INC04为代码级虚拟行，不落本表',
  `fee_name` VARCHAR(64) NOT NULL COMMENT '费用项名称：从代码级费用项目录选择，创建后不可修改；如「现付运输费」',
  `fee_category` TINYINT UNSIGNED NOT NULL DEFAULT 2 COMMENT '费用类型：1=收入，2=成本，3=代收，4=代付；内置收入四项恒为1；新增时四类可选',
  `enabled` TINYINT(1) NOT NULL DEFAULT 1 COMMENT '是否启用：1=启用（成本项参与车次费用录入与分摊），0=停用；内置收入四项恒为启用',
  `profit_enabled` TINYINT(1) NOT NULL DEFAULT 1 COMMENT '是否参与毛利计算：1=参与，0=不参与；代收/代付强制为0；收入/成本默认1，可按项关闭',
  `profit_direction` TINYINT UNSIGNED NULL DEFAULT 2 COMMENT '参与方向：1=收入（加项），2=成本（减项）；代收/代付必须为空；收入必须为1，成本必须为2',
  `source_field` VARCHAR(64) NULL DEFAULT NULL COMMENT '取数来源标识：收入内置项对应运单字段（如prepaid_fee）；成本项对应biz_trip./biz_trip_dest.字段名；代收/代付与新建收入为空',
  `sort_order` INT NOT NULL DEFAULT 0 COMMENT '排序号：费用配置列表与车次费用录入区展示顺序，升序',
  `remark` VARCHAR(500) NULL DEFAULT NULL COMMENT '备注：业务说明、口径或停用原因',
  `dept_id` BIGINT UNSIGNED NOT NULL COMMENT '归属机构ID：固定总部ID=1；本表登记数据权限豁免（总部全局配置）',
  `create_by` BIGINT UNSIGNED NULL DEFAULT NULL COMMENT '创建人用户ID；种子为NULL',
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_by` BIGINT UNSIGNED NULL DEFAULT NULL COMMENT '最后修改人用户ID',
  `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '最后修改时间',
  `deleted` TINYINT(1) NOT NULL DEFAULT 0 COMMENT '逻辑删除：0=正常，1=已删除；配置页不做删除，仅启停',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_fee_code` (`fee_code`),
  KEY `idx_enabled_sort` (`enabled`, `sort_order`),
  KEY `idx_category_sort` (`fee_category`, `sort_order`),
  KEY `idx_name` (`fee_name`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci
  COMMENT='费用类型配置表：收入/成本/代收/代付统一配置（收入四项为代码只读行，不落库）';

INSERT INTO `sys_fee_type`
(`fee_code`,`fee_name`,`fee_category`,`enabled`,`profit_enabled`,`profit_direction`,`source_field`,`sort_order`,`remark`,`dept_id`) VALUES
('FEE0010','现付运输费',2,1,1,2,'biz_trip.prepaid_transport_fee',10,'车次成本项，参与分摊与毛利',1),
('FEE0011','回付运输费',2,1,1,2,'biz_trip.return_transport_fee',20,'车次成本项，参与分摊与毛利',1),
('FEE0012','到站付运输费',2,1,1,2,'biz_trip.arrival_transport_fee',30,'车次成本项，参与分摊与毛利',1),
('FEE0013','现付油卡费',2,1,1,2,'biz_trip.prepaid_oil_card_fee',40,'车次成本项，本次调整为参与毛利',1),
('FEE0014','整车过磅费',2,1,1,2,'biz_trip.whole_weighing_fee',50,'车次成本项，参与分摊与毛利',1),
('FEE0015','整车保险费',2,1,1,2,'biz_trip.whole_insurance_fee',60,'车次成本项，参与分摊与毛利',1),
('FEE0016','分拨装车费',2,1,1,2,'biz_trip.bulk_load_fee',70,'车次成本项，参与分摊与毛利',1),
('FEE0017','分拨卸车费',2,1,1,2,'biz_trip.bulk_unload_fee',80,'车次成本项，参与分摊与毛利',1),
('FEE0018','分拨其他费',2,1,1,2,'biz_trip.bulk_other_fee',90,'车次成本项，参与分摊与毛利',1),
('FEE0019','到站卸车费',2,1,1,2,'biz_trip_dest.arrival_unload_fee',100,'车次级费用，仅干线+末端网点，在到站卸车页内联录入',1),
('FEE0020','到站其他费',2,1,1,2,'biz_trip_dest.arrival_other_fee',110,'车次级费用，与到站卸车费同页同范围录入',1),
('FEE0021','提货成本',2,0,1,2,'biz_trip.total_pickup_cost',120,'本期停用，字段预留',1),
('FEE0022','派送费',2,0,1,2,'biz_trip.total_delivery_fee',130,'本期停用，字段预留',1),
('FEE0023','外转费',2,0,1,2,'biz_trip.total_transfer_fee',140,'本期停用，字段预留',1),
('FEE0024','现返',2,1,0,NULL,NULL,150,'运单级成本项（取biz_waybill.cash_back），不参与毛利、不进车次录入与分摊',1),
('FEE0025','欠返',2,1,0,NULL,NULL,160,'运单级成本项（取biz_waybill.owe_back），不参与毛利、不进车次录入与分摊',1)
ON DUPLICATE KEY UPDATE
  fee_name=VALUES(fee_name),fee_category=VALUES(fee_category),enabled=VALUES(enabled),
  profit_enabled=VALUES(profit_enabled),profit_direction=VALUES(profit_direction),
  source_field=VALUES(source_field),sort_order=VALUES(sort_order),remark=VALUES(remark),deleted=0;
