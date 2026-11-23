-- ============================================================================
-- 云枢运输管理系统 · 运营管理（揽收开单 + 运单管理）建表 DDL · 待审稿
-- ----------------------------------------------------------------------------
-- 文件：waybill-ddl-v1.0-pending-review.sql
-- 对应方案：operation-module-design-v1.12.md（v1.12 定稿）
-- Flyway 版本：V13
--   - 已核对 backend/src/main/resources/db/migration/ 实际最新为 V12__backfill_menu_button_audit.sql
--   - 本文件按接续版本 V13 命名，正式落库时文件名为 V13__operation_waybill.sql
-- 状态：待家伟审阅；本轮不执行、不连 yunshu 库、不改任何现有表结构
-- 执行前置：
--   1) 家伟逐表确认字段/类型/注释/索引；
--   2) 在空库 dry-run 通过（本文件已用临时空库验证可建）；
--   3) 备份目标库；
--   4) 再由开发按 Flyway 规范执行。
-- 约定：
--   - 字符集 utf8mb4 / utf8mb4_0900_ai_ci；金额一律 DECIMAL，禁止 FLOAT/DOUBLE；
--   - 不建物理外键，关联关系由应用层保证；
--   - 每张表、每个字段均带中文 COMMENT；枚举逐项列出。
-- ============================================================================

SET NAMES utf8mb4;

-- ----------------------------------------------------------------------------
-- 1. 运单主表（母单 + 子单同表；列表只显示母单；58 项字段口径见方案 4.1）
-- ----------------------------------------------------------------------------
CREATE TABLE `biz_waybill` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键ID，自增',
  `waybill_no` VARCHAR(32) NOT NULL COMMENT '运单号：母单=YD+yyyyMMdd+6位序列，子单=母单号-两位序号（如YD20261123000126-01）；全局唯一，生成后不可修改',
  `related_no` VARCHAR(32) NULL DEFAULT NULL COMMENT '关联单号：客户订单号/项目号等外部单号，可空，仅追溯用',
  `pre_order_no` VARCHAR(32) NULL DEFAULT NULL COMMENT '来源预开单号：由预开单接单生成时写入，直接开单为空；唯一，防止一张预开单重复生成运单',
  `parent_id` BIGINT UNSIGNED NULL DEFAULT NULL COMMENT '母单ID：子单指向母单biz_waybill.id；母单为NULL；不建物理外键',
  `parent_no` VARCHAR(32) NULL DEFAULT NULL COMMENT '母单号快照：子单=所属母单waybill_no，母单为空；仅用于按母单查子单与打印追溯，不出列表列（v1.11.5）',
  `is_child` TINYINT(1) NOT NULL DEFAULT 0 COMMENT '是否子单：0=母单，1=子单；列表只显示母单（0）；运单号后“母”徽标仅带子单的母单显示',
  `child_seq` TINYINT UNSIGNED NULL DEFAULT NULL COMMENT '子单序号：子单为1..N（最大99），母单为NULL；与parent_id组合唯一',
  `status` TINYINT UNSIGNED NOT NULL DEFAULT 10 COMMENT '运单状态：10=待揽收，20=已揽收，25=已拆单（母单），30=已发运/在途，40=已到达，50=派送中，55=部分到达，60=已签收（含Q28强制签收），70=异常，90=已作废',
  `dept_id` BIGINT UNSIGNED NOT NULL COMMENT '归属机构ID：开单网点/机构，引用sys_dept.id；数据权限与归属统计依据',
  `dept_name` VARCHAR(100) NOT NULL COMMENT '归属机构名称快照：开单时网点名称，机构改名不影响历史单据',
  `sales_id` BIGINT UNSIGNED NULL DEFAULT NULL COMMENT '业务员ID：引用系统用户ID，可空',
  `sales_name` VARCHAR(50) NULL DEFAULT NULL COMMENT '业务员姓名快照：可空',
  `biz_type` TINYINT UNSIGNED NOT NULL DEFAULT 1 COMMENT '业务类型：1=零担，2=整车；默认1零担',
  `dest_province_code` VARCHAR(12) NOT NULL COMMENT '目的省行政编码：取自区划表，如440000',
  `dest_province_name` VARCHAR(50) NOT NULL COMMENT '目的省名称快照：如广东省',
  `dest_city_code` VARCHAR(12) NOT NULL COMMENT '目的市行政编码：如440100',
  `dest_city_name` VARCHAR(50) NOT NULL COMMENT '目的市名称快照：如广州市',
  `dest_district_code` VARCHAR(12) NOT NULL COMMENT '目的地区县行政编码：同名区县靠编码区分，如广州白云区440111、贵阳白云区520113',
  `dest_district_name` VARCHAR(50) NOT NULL COMMENT '目的地区县名称快照：如白云区',
  `dest_name` VARCHAR(120) NOT NULL COMMENT '目的地展示名快照：省+市+区县，如广东省广州市白云区',
  `origin_hub_id` BIGINT UNSIGNED NOT NULL COMMENT '始发网点ID：引用sys_dept.id；默认取开单网点（=dept_id），随选定路由首节点确定；只读派生，前端不可编辑（Q29）',
  `origin_hub_name` VARCHAR(100) NOT NULL COMMENT '始发网点名称快照：随origin_hub_id写入，用于列表/导出/打印，网点改名不影响历史单据',
  `dest_hub_id` BIGINT UNSIGNED NOT NULL COMMENT '运达网点ID：引用sys_dept.id；取所选路由终点网点，与目的地区县对应；只读派生，前端不可编辑（Q29）',
  `dest_hub_name` VARCHAR(100) NOT NULL COMMENT '运达网点名称快照：随dest_hub_id写入，用于列表/导出/打印',
  `route_name` VARCHAR(500) NOT NULL COMMENT '线路名称快照：完整链路“节点 → 节点 → 节点”；存储可保留原路由名，展示层去掉“（直）/（外转）”后缀，类型由route_end_type表达',
  `route_node_count` TINYINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '线路节点数：路由链路节点总数，用于列表展示与校验',
  `route_end_type` TINYINT UNSIGNED NOT NULL COMMENT '线路末端类型：1=直达，2=外转；用于列表/详情徽标与打印',
  `customer_id` BIGINT UNSIGNED NULL DEFAULT NULL COMMENT '客户ID：命中客户主数据时写入biz_customer.id；未命中为NULL',
  `customer_name` VARCHAR(100) NOT NULL DEFAULT '' COMMENT '客户名称快照：由发货人自动带出；未命中客户主数据时写空字符串（非NULL），不阻断开单',
  `customer_phone` VARCHAR(20) NULL DEFAULT NULL COMMENT '客户联系电话快照：由发货人带出或后续补充，可空',
  `sender_name` VARCHAR(50) NOT NULL COMMENT '发货人姓名：必填',
  `sender_phone` VARCHAR(20) NOT NULL COMMENT '发货人电话：必填，用于揽收联系；列表支持模糊搜索',
  `sender_address` VARCHAR(255) NOT NULL COMMENT '发货地址：必填',
  `receiver_name` VARCHAR(50) NOT NULL COMMENT '收货人姓名：必填',
  `receiver_phone` VARCHAR(20) NOT NULL COMMENT '收货人电话：必填，用于派送联系；打印标签不打印该字段（Q27口径）',
  `receiver_address` VARCHAR(255) NOT NULL COMMENT '收货地址：必填',
  `goods_name` VARCHAR(100) NOT NULL COMMENT '货物名称：必填',
  `goods_type` VARCHAR(50) NULL DEFAULT NULL COMMENT '货物类型：如普货/易碎/冷链等，可空',
  `package_type` VARCHAR(50) NULL DEFAULT NULL COMMENT '包装类型：如纸箱/木架/托盘等，可空',
  `quantity` INT NOT NULL DEFAULT 0 COMMENT '件数：必填且≥1；母单=子单件数之和（展示口径）',
  `weight` DECIMAL(10,3) NOT NULL DEFAULT 0.000 COMMENT '重量(kg)：必填且>0，保留3位小数；母单=子单重量之和',
  `volume` DECIMAL(10,3) NOT NULL DEFAULT 0.000 COMMENT '体积(m³)：必填且>0，保留3位小数；母单=子单体积之和',
  `goods_remark` VARCHAR(255) NULL DEFAULT NULL COMMENT '货物备注：可空',
  `pay_method` TINYINT UNSIGNED NOT NULL COMMENT '支付方式：1=现付，2=到付，3=月结，4=回单付，5=其他；无默认值，未选拦截',
  `freight_fee` DECIMAL(12,2) NOT NULL DEFAULT 0.00 COMMENT '运费(元)：主运费，DECIMAL(12,2)',
  `delivery_fee` DECIMAL(12,2) NOT NULL DEFAULT 0.00 COMMENT '送货费(元)：送货上门费用',
  `pickup_fee` DECIMAL(12,2) NOT NULL DEFAULT 0.00 COMMENT '提货费(元)：上门提货费用',
  `other_fee` DECIMAL(12,2) NOT NULL DEFAULT 0.00 COMMENT '其他费(元)：其他附加费用',
  `receipt_fee` DECIMAL(12,2) NOT NULL DEFAULT 0.00 COMMENT '回单费(元)：纸质回单费用',
  `make_bill_fee` DECIMAL(12,2) NOT NULL DEFAULT 0.00 COMMENT '制单费(元)：制单/单据费用',
  `insurance_fee` DECIMAL(12,2) NOT NULL DEFAULT 0.00 COMMENT '保费(元)：本期手填，后期由费率配置计算（Q21）',
  `declared_value` DECIMAL(12,2) NOT NULL DEFAULT 0.00 COMMENT '声明价值(元)：货物声明价值，仅记录，不计入合计，不参与total_fee计算（v1.6）',
  `total_fee` DECIMAL(12,2) NOT NULL DEFAULT 0.00 COMMENT '费用合计(元)：=运费+送货费+提货费+其他费+回单费+制单费+保费，不含声明价值',
  `prepaid_fee` DECIMAL(12,2) NOT NULL DEFAULT 0.00 COMMENT '现付金额(元)：支付方式=现付时填写',
  `collect_fee` DECIMAL(12,2) NOT NULL DEFAULT 0.00 COMMENT '到付金额(元)：支付方式=到付时填写',
  `monthly_fee` DECIMAL(12,2) NOT NULL DEFAULT 0.00 COMMENT '月结金额(元)：支付方式=月结时填写',
  `return_fee` DECIMAL(12,2) NOT NULL DEFAULT 0.00 COMMENT '回单付金额(元)：支付方式=回单付时填写',
  `cash_back` DECIMAL(12,2) NOT NULL DEFAULT 0.00 COMMENT '现返(元)：主运费组现返金额，可为负；用于返款合计',
  `owe_back` DECIMAL(12,2) NOT NULL DEFAULT 0.00 COMMENT '欠返(元)：主运费组欠返金额，可为负；用于返款合计',
  `rebate_total` DECIMAL(12,2) NOT NULL DEFAULT 0.00 COMMENT '返款合计(元)：=现返+欠返，可为负',
  `valid_freight` DECIMAL(12,2) NOT NULL DEFAULT 0.00 COMMENT '有效运费(元)：应收运费口径；费率来源待Q17确认，本期按手填/默认0',
  `delivery_method` TINYINT UNSIGNED NOT NULL COMMENT '送货方式：1=送货，2=自提，3=大车直送；展示顺序为自提/送货/大车直送，码值含义不变',
  `cod_amount` DECIMAL(12,2) NOT NULL DEFAULT 0.00 COMMENT '代收货款(元)：0表示无代收',
  `payee_name` VARCHAR(50) NULL DEFAULT NULL COMMENT '收款人姓名：代收货款时填写，可空',
  `bank_name` VARCHAR(100) NULL DEFAULT NULL COMMENT '开户行：代收货款回款账户，可空',
  `bank_card_no` VARCHAR(40) NULL DEFAULT NULL COMMENT '银行卡号：代收货款回款账户，列表/详情脱敏展示，可空',
  `need_receipt` TINYINT(1) NOT NULL DEFAULT 0 COMMENT '是否回单：0=否，1=是',
  `receipt_count` INT NOT NULL DEFAULT 0 COMMENT '回单份数：need_receipt=1时必填且≥1；否则0',
  `wait_notice` TINYINT(1) NOT NULL DEFAULT 0 COMMENT '等通知放货：0=否，1=是；到货后暂不派送，等客户通知',
  `remark` VARCHAR(500) NULL DEFAULT NULL COMMENT '运单备注：可空',
  `create_by` BIGINT UNSIGNED NOT NULL COMMENT '创建人ID：开单员用户ID',
  `create_by_name` VARCHAR(50) NOT NULL COMMENT '创建人姓名快照：开单员姓名',
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间：开单时间',
  `update_by` BIGINT UNSIGNED NULL DEFAULT NULL COMMENT '更新人ID：最后操作人用户ID',
  `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间：最后操作时间',
  `deleted` TINYINT(1) NOT NULL DEFAULT 0 COMMENT '逻辑删除：0=正常，1=已删除；查询默认过滤deleted=0',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_waybill_no` (`waybill_no`),
  UNIQUE KEY `uk_parent_child` (`parent_id`, `child_seq`),
  UNIQUE KEY `uk_pre_order_no` (`pre_order_no`),
  KEY `idx_parent_no` (`parent_no`),
  KEY `idx_related_no` (`related_no`),
  KEY `idx_dept_time` (`dept_id`, `create_time`),
  KEY `idx_create_time` (`create_time`),
  KEY `idx_customer` (`customer_id`),
  KEY `idx_customer_name` (`customer_name`),
  KEY `idx_sender_phone` (`sender_phone`),
  KEY `idx_receiver_phone` (`receiver_phone`),
  KEY `idx_dest` (`dest_district_code`),
  KEY `idx_origin_hub` (`origin_hub_id`),
  KEY `idx_dest_hub` (`dest_hub_id`),
  KEY `idx_status` (`status`),
  KEY `idx_pay_method` (`pay_method`),
  KEY `idx_create_by` (`create_by`),
  KEY `idx_sales` (`sales_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='运单主表：母单与子单同表；列表只显示母单；含58项字段口径与网点快照';

-- ----------------------------------------------------------------------------
-- 2. 运单路由节点表（每张运单一条链路快照）
-- ----------------------------------------------------------------------------
CREATE TABLE `biz_waybill_route_node` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键ID，自增',
  `waybill_id` BIGINT UNSIGNED NOT NULL COMMENT '运单ID：引用biz_waybill.id，母单/子单均可挂节点',
  `waybill_no` VARCHAR(32) NOT NULL COMMENT '运单号快照：便于按单号直接查节点，不依赖关联查询',
  `seq` TINYINT UNSIGNED NOT NULL COMMENT '节点顺序：从1开始，按链路先后排列',
  `node_type` TINYINT UNSIGNED NOT NULL COMMENT '节点类型：1=网点，2=分拨中心，3=中转场，4=目的地',
  `node_id` BIGINT UNSIGNED NULL DEFAULT NULL COMMENT '节点ID：网点/分拨中心对应sys_dept.id；目的地可为空',
  `node_name` VARCHAR(100) NOT NULL COMMENT '节点名称快照：如济南程驰/济南分拨中心/广州/白云区',
  `route_scope` TINYINT UNSIGNED NOT NULL DEFAULT 1 COMMENT '节点作用域：1=干线（主链路），2=末端（派送段）',
  `route_category` TINYINT UNSIGNED NOT NULL DEFAULT 1 COMMENT '路由分类：1=主路由，2=备选路由',
  `transfer_type` TINYINT UNSIGNED NULL DEFAULT NULL COMMENT '转运类型：1=直达，2=外转；仅末段/中转节点填写，可空',
  `mileage` DECIMAL(10,2) NULL DEFAULT NULL COMMENT '里程(km)：该节点至下一节点或全程里程，可空，保留2位小数',
  `transit_hours` DECIMAL(10,2) NULL DEFAULT NULL COMMENT '时效(h)：该段或全程预计时长，可空，保留2位小数',
  `dept_id` BIGINT UNSIGNED NOT NULL COMMENT '归属机构ID：数据权限过滤用，引用sys_dept.id',
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_waybill_seq` (`waybill_id`, `seq`),
  KEY `idx_waybill_no` (`waybill_no`),
  KEY `idx_node` (`node_id`, `node_type`),
  KEY `idx_dept` (`dept_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='运单路由节点表：保存每张运单的链路节点快照，支撑详情时间轴与按节点统计';

-- ----------------------------------------------------------------------------
-- 3. 客户主数据表（简易可用版）
-- ----------------------------------------------------------------------------
CREATE TABLE `biz_customer` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键ID，自增',
  `customer_name` VARCHAR(100) NOT NULL COMMENT '客户名称：唯一，全局不重复',
  `contact_name` VARCHAR(50) NULL DEFAULT NULL COMMENT '联系人姓名：可空',
  `contact_phone` VARCHAR(20) NULL DEFAULT NULL COMMENT '联系电话：可空；开单时可由发货人电话匹配',
  `address` VARCHAR(255) NULL DEFAULT NULL COMMENT '客户地址：可空',
  `settle_type` TINYINT UNSIGNED NOT NULL DEFAULT 1 COMMENT '结算方式：1=现结，2=月结，3=到付，4=其他；默认现结',
  `credit_limit` DECIMAL(12,2) NOT NULL DEFAULT 0.00 COMMENT '授信额度(元)：0表示未设置；月结客户可用',
  `status` TINYINT UNSIGNED NOT NULL DEFAULT 1 COMMENT '客户状态：1=启用，0=停用；停用客户不再参与新开单匹配',
  `remark` VARCHAR(255) NULL DEFAULT NULL COMMENT '备注：可空',
  `dept_id` BIGINT UNSIGNED NOT NULL COMMENT '归属机构ID：客户归属网点，引用sys_dept.id',
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` TINYINT(1) NOT NULL DEFAULT 0 COMMENT '逻辑删除：0=正常，1=已删除；名称唯一性仅对deleted=0生效',
  PRIMARY KEY (`id`),
  KEY `idx_name_phone` (`customer_name`, `contact_phone`),
  KEY `idx_phone` (`contact_phone`),
  KEY `idx_dept` (`dept_id`),
  KEY `idx_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='客户主数据表：开单时按名称/电话匹配，未命中不阻断开单';

-- ----------------------------------------------------------------------------
-- 4. 运单号序列表（按天序列，生成 YD+yyyyMMdd+6位）
-- ----------------------------------------------------------------------------
CREATE TABLE `biz_waybill_no_seq` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键ID，自增',
  `seq_date` CHAR(8) NOT NULL COMMENT '序列日期：yyyyMMdd，如20261123；与seq_type组合唯一',
  `seq_type` TINYINT UNSIGNED NOT NULL DEFAULT 1 COMMENT '序列类型：1=运单号（YD），2=子单号（母单号-两位序号）；预留扩展',
  `current_no` INT UNSIGNED NOT NULL DEFAULT 0 COMMENT '当前序列值：每次取号+1；初始0',
  `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间：最后一次取号时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_date_type` (`seq_date`, `seq_type`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='运单号序列表：按日期与类型取号，支撑YD+yyyyMMdd+6位运单号生成';

-- ----------------------------------------------------------------------------
-- 5. 预开单号序列表（按天序列，生成 YD+yyyyMMdd+6位）
-- ----------------------------------------------------------------------------
CREATE TABLE `biz_pre_waybill_no_seq` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键ID，自增',
  `seq_date` CHAR(8) NOT NULL COMMENT '序列日期：yyyyMMdd，如20261123；唯一',
  `current_no` INT UNSIGNED NOT NULL DEFAULT 0 COMMENT '当前序列值：每次取号+1；初始0',
  `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间：最后一次取号时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_seq_date` (`seq_date`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='预开单号序列表：按日期取号，支撑预开单号生成';

-- ----------------------------------------------------------------------------
-- 6. 预开单表（客户申报，接单后生成正式运单）
-- ----------------------------------------------------------------------------
CREATE TABLE `biz_pre_waybill` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键ID，自增',
  `pre_order_no` VARCHAR(32) NOT NULL COMMENT '预开单号：YJ+yyyyMMdd+6位流水（预开单专用前缀，勿与运单YD混淆），全平台唯一',
  `status` TINYINT UNSIGNED NOT NULL DEFAULT 10 COMMENT '预开单状态：10=待接单，20=已接单，30=已揽收（已生成正式运单），90=已撤单',
  `customer_id` BIGINT UNSIGNED NULL DEFAULT NULL COMMENT '客户ID：命中客户主数据时写入biz_customer.id，可空',
  `customer_name` VARCHAR(100) NOT NULL DEFAULT '' COMMENT '客户名称快照：客户申报名称；未命中写空字符串',
  `customer_phone` VARCHAR(20) NOT NULL COMMENT '客户联系电话快照：客户端申报，必填',
  `sender_name` VARCHAR(50) NOT NULL COMMENT '发货人姓名：必填',
  `sender_phone` VARCHAR(20) NOT NULL COMMENT '发货人电话：必填',
  `sender_address` VARCHAR(200) NOT NULL COMMENT '发货地址：必填',
  `receiver_name` VARCHAR(50) NOT NULL COMMENT '收货人姓名：必填',
  `receiver_phone` VARCHAR(20) NOT NULL COMMENT '收货人电话：必填',
  `receiver_address` VARCHAR(200) NOT NULL COMMENT '收货地址：必填',
  `dest_province_code` CHAR(6) NOT NULL COMMENT '目的省行政编码：如440000',
  `dest_province_name` VARCHAR(64) NOT NULL COMMENT '目的省名称快照：如广东省',
  `dest_city_code` CHAR(6) NOT NULL COMMENT '目的市行政编码：如440100',
  `dest_city_name` VARCHAR(64) NOT NULL COMMENT '目的市名称快照：如广州市',
  `dest_district_code` CHAR(6) NOT NULL COMMENT '目的地区县行政编码：同名区县靠编码区分',
  `dest_district_name` VARCHAR(64) NOT NULL COMMENT '目的地区县名称快照：如白云区',
  `dest_name` VARCHAR(200) NOT NULL COMMENT '目的地展示名快照：省+市+区县',
  `goods_name` VARCHAR(100) NOT NULL COMMENT '货物名称：必填',
  `quantity` INT UNSIGNED NOT NULL DEFAULT 0 COMMENT '件数：客户申报值，必填且≥1',
  `weight` DECIMAL(12,3) NOT NULL DEFAULT 0.000 COMMENT '重量(kg)：客户申报值，必填且>0，3位小数',
  `volume` DECIMAL(12,3) NOT NULL DEFAULT 0.000 COMMENT '体积(m³)：客户申报值，可空，3位小数',
  `goods_remark` VARCHAR(500) NULL DEFAULT NULL COMMENT '货物备注：客户申报的特殊说明，可空',
  `expect_pickup_time` DATETIME NULL DEFAULT NULL COMMENT '期望提货时间：客户申报的期望上门提货时间，可空',
  `submit_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '提交时间：客户提交预开单的时间，列表默认排序字段',
  `accept_by` BIGINT UNSIGNED NULL DEFAULT NULL COMMENT '接单人ID：接单操作人用户ID，未接单为空',
  `accept_by_name` VARCHAR(50) NULL DEFAULT NULL COMMENT '接单人姓名快照：接单时写入，未接单为空',
  `accept_time` DATETIME NULL DEFAULT NULL COMMENT '接单时间：未接单为空',
  `waybill_no` VARCHAR(32) NULL DEFAULT NULL COMMENT '正式运单号：接单开单后回写，未开单为空',
  `dept_id` BIGINT UNSIGNED NOT NULL COMMENT '归属机构ID：预开单受理网点，引用sys_dept.id；数据权限锚点',
  `dept_name` VARCHAR(50) NOT NULL COMMENT '归属机构名称快照：预开单受理网点名称',
  `create_by` BIGINT UNSIGNED NULL DEFAULT NULL COMMENT '创建人ID：客户端提交时写入客户账号ID或系统账号',
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间：记录入库时间',
  `update_by` BIGINT UNSIGNED NULL DEFAULT NULL COMMENT '更新人ID：最后操作人用户ID',
  `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间：最后操作时间',
  `version` INT UNSIGNED NOT NULL DEFAULT 0 COMMENT '乐观锁版本号：接单并发控制，每次更新+1',
  `deleted` TINYINT(1) NOT NULL DEFAULT 0 COMMENT '逻辑删除：0=正常，1=已删除；查询默认过滤deleted=0',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_pre_order_no` (`pre_order_no`),
  KEY `idx_status_submit` (`status`, `submit_time`),
  KEY `idx_dept_status` (`dept_id`, `status`),
  KEY `idx_customer` (`customer_id`),
  KEY `idx_customer_name` (`customer_name`),
  KEY `idx_dest` (`dest_district_code`),
  KEY `idx_expect_pickup` (`expect_pickup_time`),
  KEY `idx_accept_by` (`accept_by`),
  KEY `idx_waybill_no` (`waybill_no`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='预开单表：客户申报信息，接单后带入开单并生成正式运单';
