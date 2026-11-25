-- ============================================================================
-- V29 · 车次配载明细 / 费用分摊明细
-- 对应方案：transport-loading-design-v1.3.md 3.3 / 3.5 / 第 12 节 V29
-- ============================================================================
SET NAMES utf8mb4;

CREATE TABLE `biz_trip_waybill` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键ID，自增',
  `trip_id` BIGINT UNSIGNED NOT NULL COMMENT '主车次ID：逻辑引用biz_trip.id',
  `trip_dest_id` BIGINT UNSIGNED NOT NULL COMMENT '子车次ID：逻辑引用biz_trip_dest.id；按运单下一节点自动匹配',
  `sub_trip_no` VARCHAR(40) NOT NULL COMMENT '子车次编号快照：便于打印与对账不联表',
  `waybill_id` BIGINT UNSIGNED NOT NULL COMMENT '配载单元ID：逻辑引用biz_waybill.id；整票=母单(is_child=0)，拆单=子单(is_child=1)',
  `waybill_no` VARCHAR(32) NOT NULL COMMENT '配载单元号快照：整票=母单号，拆单=子单号（母单号-两位序号）',
  `parent_no` VARCHAR(32) NULL DEFAULT NULL COMMENT '母单号快照：整票为空；拆单时=子单所属母单号，用于右栏/打印/卸车展示母子关系',
  `is_child` TINYINT(1) NOT NULL DEFAULT 0 COMMENT '是否子单快照：0=整票，1=子单；随waybill_id一并快照，避免打印联表',
  `split_ratio` DECIMAL(9,6) NULL DEFAULT NULL COMMENT '拆单件数占比：子单件数÷母单原件数，保留6位；整票为NULL；仅展示/审计，金额以子单快照为准',
  `route_node_seq` TINYINT UNSIGNED NOT NULL COMMENT '本段路由节点序号：运单路由节点表中与目的网点匹配的seq，用于到站后回写当前节点',
  `dest_hub_id` BIGINT UNSIGNED NOT NULL COMMENT '本段到达网点ID：=运单当前路由下一节点，必须等于子车次目的网点',
  `dest_hub_name` VARCHAR(50) NOT NULL COMMENT '本段到达网点名称快照',
  `dest_name` VARCHAR(200) NULL DEFAULT NULL COMMENT '运单最终目的地快照：省+市+区县，用于运输清单展示',
  `customer_name` VARCHAR(100) NULL DEFAULT NULL COMMENT '客户名称快照',
  `receiver_name` VARCHAR(50) NULL DEFAULT NULL COMMENT '收货人姓名快照：运输清单与末端联系使用',
  `receiver_phone` VARCHAR(20) NULL DEFAULT NULL COMMENT '收货人电话快照',
  `goods_name` VARCHAR(100) NULL DEFAULT NULL COMMENT '货物名称快照',
  `quantity` INT UNSIGNED NOT NULL COMMENT '本配载单元件数：整票=运单件数；子单=拆单件数（本车份额）；用于汇总、超载校验与清单',
  `weight` DECIMAL(12,3) NOT NULL COMMENT '本配载单元重量(kg)：整票=运单重量；子单=按件数占比折算；用于超重、装载率与分摊基数',
  `volume` DECIMAL(12,3) NOT NULL COMMENT '本配载单元体积(m³)：整票=运单体积；子单=按件数占比折算；用于超容、装载率与分摊基数',
  `prepaid_fee` DECIMAL(12,2) NOT NULL DEFAULT 0 COMMENT '现付运费快照(元)：配载单元现付运费（子单=按件数拆分后金额），收入项',
  `collect_fee` DECIMAL(12,2) NOT NULL DEFAULT 0 COMMENT '到付运费快照(元)：配载单元到付运费（子单=拆分后金额），收入项',
  `return_fee` DECIMAL(12,2) NOT NULL DEFAULT 0 COMMENT '回付运费快照(元)：配载单元回付运费（子单=拆分后金额），收入项',
  `monthly_fee` DECIMAL(12,2) NOT NULL DEFAULT 0 COMMENT '月结运费快照(元)：配载单元月结运费（子单=拆分后金额），收入项',
  `total_fee` DECIMAL(12,2) NOT NULL DEFAULT 0 COMMENT '运费合计快照(元)：=现付+到付+回付+月结；服务端计算',
  `rebate_total` DECIMAL(12,2) NOT NULL DEFAULT 0 COMMENT '返款合计快照(元)：配载单元返款（子单=按件数拆分的现返+欠返），毛利中扣减一次',
  `valid_freight` DECIMAL(12,2) NOT NULL DEFAULT 0 COMMENT '有效运费快照(元)：配载单元有效运费（子单=拆分后值），仅展示',
  `allocated_fee` DECIMAL(14,2) NOT NULL DEFAULT 0 COMMENT '本票成本分摊合计(元)：本票全部成本项分摊结果之和；服务端计算，用于成本对账',
  `profit_cost` DECIMAL(14,2) NOT NULL DEFAULT 0 COMMENT '本票参与毛利的成本分摊合计(元)：仅汇总profit_enabled=1且有效的成本项；服务端计算',
  `gross_profit` DECIMAL(14,2) NOT NULL DEFAULT 0 COMMENT '本票毛利(元)：=本票四类运费合计−本票返款−profit_cost；服务端计算',
  `status_before_load` TINYINT UNSIGNED NOT NULL COMMENT '配载前单元状态：加入车次前的状态（整票20/60，子单20），移除或关闭车次时用于还原',
  `load_status` TINYINT UNSIGNED NOT NULL DEFAULT 1 COMMENT '配载状态：1=已配载，2=已发车，3=已到站，4=已卸车；按配载单元独立流转',
  `load_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '配载时间',
  `load_by` BIGINT UNSIGNED NULL DEFAULT NULL COMMENT '配载操作人用户ID',
  `load_by_name` VARCHAR(50) NULL DEFAULT NULL COMMENT '配载操作人姓名快照',
  `dept_id` BIGINT UNSIGNED NOT NULL COMMENT '归属机构ID：=主车次dept_id；数据权限锚点',
  `create_by` BIGINT UNSIGNED NULL DEFAULT NULL COMMENT '创建人用户ID',
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_by` BIGINT UNSIGNED NULL DEFAULT NULL COMMENT '最后修改人用户ID',
  `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '最后修改时间',
  `deleted` TINYINT(1) NOT NULL DEFAULT 0 COMMENT '逻辑删除：0=正常，1=已移除配载；活跃唯一性由行锁+服务层校验保证，不建唯一索引',
  PRIMARY KEY (`id`),
  KEY `idx_trip_status` (`trip_id`, `load_status`),
  KEY `idx_trip_dest` (`trip_dest_id`),
  KEY `idx_waybill` (`waybill_id`, `deleted`),
  KEY `idx_parent_no` (`parent_no`),
  KEY `idx_dept` (`dept_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci
  COMMENT='车次配载明细表：一行=一个配载单元（整票母单或拆单子单），含收入/返款/成本/毛利快照';

CREATE TABLE `biz_trip_waybill_fee` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键ID，自增',
  `trip_id` BIGINT UNSIGNED NOT NULL COMMENT '车次ID：逻辑引用biz_trip.id',
  `trip_waybill_id` BIGINT UNSIGNED NOT NULL COMMENT '配载明细ID：逻辑引用biz_trip_waybill.id',
  `waybill_id` BIGINT UNSIGNED NOT NULL COMMENT '运单ID：冗余，便于按运单查费用',
  `fee_code` VARCHAR(16) NOT NULL COMMENT '费用项编码：引用sys_fee_type.fee_code，如FEE0010',
  `fee_name` VARCHAR(64) NOT NULL COMMENT '费用项名称快照',
  `alloc_method` TINYINT UNSIGNED NOT NULL COMMENT '分摊方式快照：取车次biz_trip.alloc_method（全车统一），1=重量，2=体积，3=重泡比计费重量',
  `basis_value` DECIMAL(14,3) NOT NULL DEFAULT 0 COMMENT '本票分摊基数：按方式取重量/体积/计费重量',
  `basis_total` DECIMAL(16,3) NOT NULL DEFAULT 0 COMMENT '车次分摊基数合计：同方式下全部配载运单基数之和',
  `alloc_ratio` DECIMAL(12,8) NOT NULL DEFAULT 0 COMMENT '分摊比例：basis_value÷basis_total，保留8位',
  `amount` DECIMAL(14,2) NOT NULL DEFAULT 0 COMMENT '本票分摊金额(元)：含尾差调整，全部运单合计必须等于费用项总额',
  `dept_id` BIGINT UNSIGNED NOT NULL COMMENT '归属机构ID：=主车次dept_id；数据权限锚点',
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间：重算分摊时按配载明细+费用项覆盖更新',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_tw_fee` (`trip_waybill_id`, `fee_code`),
  KEY `idx_trip` (`trip_id`),
  KEY `idx_waybill` (`waybill_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci
  COMMENT='费用分摊明细表：每个配载单元×每个成本项一行，是逐票毛利与车次对账的唯一取数来源';
