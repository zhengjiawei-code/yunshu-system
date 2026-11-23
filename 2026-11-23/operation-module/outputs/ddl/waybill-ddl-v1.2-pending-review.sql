-- ============================================================================
-- 云枢运输管理系统 · 运营管理（揽收开单 + 运单管理）建表 DDL · 待审稿 v1.2
-- ----------------------------------------------------------------------------
-- 文件：waybill-ddl-v1.2-pending-review.sql（不覆盖 v1.1；正式落地名 V13__operation_waybill.sql，内容一致）
-- 对应方案：operation-module-design-v1.14.md（唯一字典来源）
-- Flyway 版本：V13
--   - 已核对 backend/src/main/resources/db/migration/ 实际最新为 V12__backfill_menu_button_audit.sql
--   - 正式落库文件名：V13__operation_waybill.sql；回滚脚本：V13__operation_waybill_rollback.sql
-- 状态：复审通过后收尾稿；本轮不执行、不连 yunshu 库、不改任何现有表结构
-- 覆盖：7 张表（运单、路由节点、客户、运单号序列、预开单号序列、预开单、运单操作日志）
-- 约定：
--   - utf8mb4 / utf8mb4_0900_ai_ci；金额一律 DECIMAL，禁止 FLOAT/DOUBLE；
--   - 不建物理外键；每张表、每个字段均有中文 COMMENT，枚举逐项列出；
--   - 派生金额由服务端计算并强制覆盖，客户端传值无效（详见 5.1/5.4）；
--   - 运单 quantity/weight/volume、预开单 quantity/weight 不设 DEFAULT，必须显式传值；预开单 volume 保留默认 0（客户端可不申报）；服务层校验 quantity>=1、weight>0、volume>0。
-- ============================================================================

SET NAMES utf8mb4;

-- ----------------------------------------------------------------------------
-- 1. 运单主表（母单 + 子单同表；列表只显示母单；58 项字段口径见方案 4.1）
-- ----------------------------------------------------------------------------
CREATE TABLE `biz_waybill` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键ID，自增',
  `waybill_no` VARCHAR(32) NOT NULL COMMENT '运单号：母单=YD+yyyyMMdd+6位序列，子单=母单号-两位序号（如YD20261123000126-01）；全局唯一，生成后不可修改',
  `related_no` VARCHAR(64) NULL DEFAULT NULL COMMENT '关联单号：客户订单号/项目号等外部单号，可空、可重复，仅追溯与检索',
  `pre_order_no` VARCHAR(32) NULL DEFAULT NULL COMMENT '来源预开单号：由预开单接单生成时写入，直接开单为空；唯一，防止一张预开单重复生成运单',
  `parent_id` BIGINT UNSIGNED NULL DEFAULT NULL COMMENT '母单ID：子单指向母单biz_waybill.id；母单为NULL；仅两级，子单不可再拆；不建物理外键',
  `parent_no` VARCHAR(32) NULL DEFAULT NULL COMMENT '母单号快照：子单=所属母单waybill_no，母单为空；仅用于按母单查子单与打印追溯，不出列表列（v1.11.5）',
  `is_child` TINYINT(1) NOT NULL DEFAULT 0 COMMENT '是否子单：0=母单，1=子单；列表只显示母单（0）；运单号后“母”徽标仅带子单的母单显示',
  `child_seq` SMALLINT UNSIGNED NULL DEFAULT NULL COMMENT '子单序号：子单为1..99，母单为NULL；与parent_id组合唯一；序号不回收',
  `status` TINYINT UNSIGNED NOT NULL DEFAULT 20 COMMENT '运单状态：10=待揽收，20=已揽收（默认，开单即20），25=已拆单（母单），30=已装车，40=运输中，50=已到达，55=部分到达，60=已签收（含Q28强制签收），70=异常，90=已作废',
  `dept_id` BIGINT UNSIGNED NOT NULL COMMENT '开单网点ID：引用sys_dept.id；开单时取当前机构；数据权限归属机构',
  `dept_name` VARCHAR(50) NOT NULL COMMENT '开单网点名称快照：开单时写入，机构改名不影响历史单据',
  `sales_id` BIGINT UNSIGNED NULL DEFAULT NULL COMMENT '业务员ID：引用系统用户ID，可空',
  `sales_name` VARCHAR(50) NULL DEFAULT NULL COMMENT '业务员姓名快照：可空',
  `biz_type` TINYINT UNSIGNED NOT NULL DEFAULT 2 COMMENT '业务类型：1=整车，2=零担；开单页默认2零担',
  `dest_province_code` VARCHAR(12) NOT NULL COMMENT '目的省行政编码：取自区划表，如440000；统一VARCHAR(12)',
  `dest_province_name` VARCHAR(64) NOT NULL COMMENT '目的省名称快照：如广东省',
  `dest_city_code` VARCHAR(12) NOT NULL COMMENT '目的市行政编码：如440100；统一VARCHAR(12)',
  `dest_city_name` VARCHAR(64) NOT NULL COMMENT '目的市名称快照：如广州市',
  `dest_district_code` VARCHAR(12) NOT NULL COMMENT '目的地区县行政编码：同名区县靠编码区分，如广州白云区440111、贵阳白云区520113；统一VARCHAR(12)',
  `dest_district_name` VARCHAR(64) NOT NULL COMMENT '目的地区县名称快照：如白云区',
  `dest_name` VARCHAR(200) NOT NULL COMMENT '目的地展示名快照：省+市+区县，如广东省广州市白云区',
  `origin_hub_id` BIGINT UNSIGNED NOT NULL COMMENT '始发网点ID：引用sys_dept.id；默认取开单网点（=dept_id），随选定路由首节点确定；只读派生，前端不可编辑（Q29）',
  `origin_hub_name` VARCHAR(50) NOT NULL COMMENT '始发网点名称快照：随origin_hub_id写入，用于列表/导出/打印，网点改名不影响历史单据',
  `dest_hub_id` BIGINT UNSIGNED NULL DEFAULT NULL COMMENT '运达网点ID：引用sys_dept.id；末段目标本身是有归属机构的网点(dept_type=5且启用)时取该网点；末段仅到区划、无归属网点时回落为始发网点(=origin_hub_id)；不得取路由中间节点；只读派生，前端不可编辑（Q29）',
  `dest_hub_name` VARCHAR(50) NULL DEFAULT NULL COMMENT '运达网点名称快照：随dest_hub_id写入；末段无归属网点时回落为始发网点名称，不再为空',
  `route_name` VARCHAR(255) NOT NULL COMMENT '线路名称快照：完整链路“节点 → 节点 → 节点”；存储可保留原路由名，展示层去掉“（直）/（外转）”后缀，类型由route_end_type表达',
  `route_node_count` TINYINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '线路节点数：链路中机构节点+终点区划节点的个数（含起点），用于列表展示与校验',
  `route_end_type` TINYINT UNSIGNED NOT NULL COMMENT '线路末端类型：1=直达，2=外转；用于列表/详情徽标与打印',
  `customer_id` BIGINT UNSIGNED NULL DEFAULT NULL COMMENT '客户ID：命中客户主数据时写入biz_customer.id；未命中为NULL',
  `customer_name` VARCHAR(100) NOT NULL DEFAULT '' COMMENT '客户名称快照：由发货人自动带出；未命中客户主数据时写空字符串（非NULL），不阻断开单',
  `customer_phone` VARCHAR(20) NULL DEFAULT NULL COMMENT '客户联系电话快照：由发货人带出或后续补充，可空',
  `sender_name` VARCHAR(50) NOT NULL COMMENT '发货人姓名：必填',
  `sender_phone` VARCHAR(20) NOT NULL COMMENT '发货人电话：必填，用于揽收联系；列表支持模糊搜索',
  `sender_address` VARCHAR(200) NOT NULL COMMENT '发货详细地址（不含省市区县）：必填，用于上门提货与打印',
  `receiver_name` VARCHAR(50) NOT NULL COMMENT '收货人姓名：必填',
  `receiver_phone` VARCHAR(20) NOT NULL COMMENT '收货人电话：必填，用于派送联系；打印标签不打印该字段（Q27口径）',
  `receiver_address` VARCHAR(200) NOT NULL COMMENT '收货详细地址（不含省市区县）：必填，用于派送与打印',
  `goods_name` VARCHAR(100) NOT NULL COMMENT '货物名称：必填',
  `goods_type` VARCHAR(50) NULL DEFAULT NULL COMMENT '货物类型：如普货/易碎/冷链等，可空',
  `package_type` VARCHAR(50) NULL DEFAULT NULL COMMENT '包装类型：如纸箱/木架/托盘等，可空',
  `quantity` INT UNSIGNED NOT NULL COMMENT '件数：必填且≥1；无默认值，必须显式传值，服务端校验；母单=子单件数之和（展示口径）',
  `weight` DECIMAL(12,3) NOT NULL COMMENT '重量(kg)：必填且>0，保留3位小数；无默认值，必须显式传值，服务端校验；母单=子单重量之和',
  `volume` DECIMAL(12,3) NOT NULL COMMENT '体积(m³)：必填且>0，保留3位小数；无默认值，必须显式传值，服务端校验；母单=子单体积之和',
  `goods_remark` VARCHAR(500) NULL DEFAULT NULL COMMENT '货物备注：货物特殊说明、装卸要求等，可空',
  `pay_method` TINYINT UNSIGNED NOT NULL COMMENT '支付方式：1=现付，2=到付，3=月结，4=回付，5=多笔付；无默认值，未选拦截',
  `freight_fee` DECIMAL(12,2) NOT NULL DEFAULT 0.00 COMMENT '运费(元)：主运费，≥0，DECIMAL(12,2)',
  `delivery_fee` DECIMAL(12,2) NOT NULL DEFAULT 0.00 COMMENT '送货费(元)：≥0，计入运费合计',
  `pickup_fee` DECIMAL(12,2) NOT NULL DEFAULT 0.00 COMMENT '提货费(元)：≥0，计入运费合计',
  `other_fee` DECIMAL(12,2) NOT NULL DEFAULT 0.00 COMMENT '其他费(元)：≥0，计入运费合计',
  `receipt_fee` DECIMAL(12,2) NOT NULL DEFAULT 0.00 COMMENT '回单费(元)：≥0，计入运费合计',
  `make_bill_fee` DECIMAL(12,2) NOT NULL DEFAULT 0.00 COMMENT '制单费(元)：≥0，计入运费合计',
  `insurance_fee` DECIMAL(12,2) NOT NULL DEFAULT 0.00 COMMENT '保费(元)：≥0，本期手填，后期由费率配置计算（Q21）',
  `declared_value` DECIMAL(14,2) NOT NULL DEFAULT 0.00 COMMENT '声明价值(元)：货物保价金额，≥0；仅记录与展示，不计入运费合计、返款合计、有效运费与支付分摊（v1.6）',
  `total_fee` DECIMAL(12,2) NOT NULL DEFAULT 0.00 COMMENT '运费合计(元)=运费+送货费+提货费+其他费+回单费+制单费+保费；服务端计算并强制覆盖，客户端传值无效；不含声明价值',
  `prepaid_fee` DECIMAL(12,2) NOT NULL DEFAULT 0.00 COMMENT '现付运费(元)：服务端按支付方式计算并强制覆盖；1现付=total_fee，2/3/4=0，5多笔付读取客户端分摊并校验四项合计=total_fee后落库；≥0',
  `collect_fee` DECIMAL(12,2) NOT NULL DEFAULT 0.00 COMMENT '到付运费(元)：服务端按支付方式计算并强制覆盖；2到付=total_fee，1/3/4=0，5多笔付读取客户端分摊并校验四项合计=total_fee后落库；≥0',
  `monthly_fee` DECIMAL(12,2) NOT NULL DEFAULT 0.00 COMMENT '月结运费(元)：服务端按支付方式计算并强制覆盖；3月结=total_fee，1/2/4=0，5多笔付读取客户端分摊并校验四项合计=total_fee后落库；≥0',
  `return_fee` DECIMAL(12,2) NOT NULL DEFAULT 0.00 COMMENT '回付运费(元)：服务端按支付方式计算并强制覆盖；4回付=total_fee，1/2/3=0，5多笔付读取客户端分摊并校验四项合计=total_fee后落库；≥0',
  `cash_back` DECIMAL(12,2) NOT NULL DEFAULT 0.00 COMMENT '现返(元)：≥0，不参与运费合计与支付分摊，通过返款合计影响有效运费',
  `owe_back` DECIMAL(12,2) NOT NULL DEFAULT 0.00 COMMENT '欠返(元)：≥0，不参与运费合计与支付分摊，通过返款合计影响有效运费',
  `rebate_total` DECIMAL(12,2) NOT NULL DEFAULT 0.00 COMMENT '返款合计(元)=现返+欠返；服务端计算并强制覆盖，客户端传值无效；因两项均≥0，本值≥0',
  `valid_freight` DECIMAL(12,2) NOT NULL DEFAULT 0.00 COMMENT '有效运费(元)=total_fee−rebate_total；服务端计算并强制覆盖，客户端传值无效；可为负（Q17）',
  `delivery_method` TINYINT UNSIGNED NOT NULL COMMENT '送货方式：1=送货，2=自提，3=大车直送；码值固定；展示顺序为自提/送货/大车直送，与码值无关',
  `cod_amount` DECIMAL(12,2) NOT NULL DEFAULT 0.00 COMMENT '代收货款(元)：≥0，默认0；独立于运费合计，不参与支付分摊',
  `payee_name` VARCHAR(50) NULL DEFAULT NULL COMMENT '收款人姓名：代收货款>0时必填，可空',
  `bank_name` VARCHAR(100) NULL DEFAULT NULL COMMENT '开户行：代收货款>0时必填，可空',
  `bank_card_no` VARCHAR(32) NULL DEFAULT NULL COMMENT '银行卡号：代收货款>0时必填；列表/详情/导出脱敏展示，可空',
  `need_receipt` TINYINT(1) NOT NULL DEFAULT 0 COMMENT '是否回单：0=否，1=是，默认0',
  `receipt_count` INT UNSIGNED NOT NULL DEFAULT 0 COMMENT '回单份数：need_receipt=1时必填且≥1；否则0',
  `wait_notice` TINYINT(1) NOT NULL DEFAULT 0 COMMENT '等通知放货：0=否，1=是，默认0；到货后暂不派送，等客户通知',
  `remark` VARCHAR(500) NULL DEFAULT NULL COMMENT '运单备注：开单补充说明，可空；拆/合/撤/作废/强制签收原因不写此处，统一写biz_waybill_log',
  `create_by` BIGINT UNSIGNED NULL DEFAULT NULL COMMENT '开单员用户ID：由数据权限自动填充为当前登录人，可空',
  `create_by_name` VARCHAR(50) NOT NULL COMMENT '开单员姓名快照：保存时取当前登录人姓名',
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '开单时间：服务器时间，列表默认倒序',
  `update_by` BIGINT UNSIGNED NULL DEFAULT NULL COMMENT '最后修改人用户ID：由数据权限自动填充',
  `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '最后修改时间：数据库自动刷新',
  `deleted` TINYINT(1) NOT NULL DEFAULT 0 COMMENT '逻辑删除：0=正常，1=已删除；查询默认过滤deleted=0；作废用status=90而非删除',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_waybill_no` (`waybill_no`),
  UNIQUE KEY `uk_parent_child` (`parent_id`, `child_seq`),
  UNIQUE KEY `uk_pre_order_no` (`pre_order_no`),
  KEY `idx_parent_no` (`parent_no`),
  KEY `idx_related_no` (`related_no`),
  KEY `idx_dept_time` (`dept_id`, `create_time`),
  KEY `idx_create_time` (`create_time`),
  KEY `idx_child_deleted_time` (`is_child`, `deleted`, `create_time`),
  KEY `idx_customer` (`customer_id`),
  KEY `idx_customer_name` (`customer_name`),
  KEY `idx_sender_phone` (`sender_phone`),
  KEY `idx_receiver_phone` (`receiver_phone`),
  KEY `idx_dest` (`dest_province_code`, `dest_city_code`, `dest_district_code`),
  KEY `idx_origin_hub` (`origin_hub_id`),
  KEY `idx_dest_hub` (`dest_hub_id`),
  KEY `idx_status` (`status`),
  KEY `idx_pay_method` (`pay_method`),
  KEY `idx_create_by` (`create_by`),
  KEY `idx_sales` (`sales_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='运单主表：母单与子单同表；列表只显示母单；含58项字段口径、网点快照与派生金额';

-- ----------------------------------------------------------------------------
-- 2. 运单路由节点表（按方案 7.2 的分段快照结构）
-- ----------------------------------------------------------------------------
CREATE TABLE `biz_waybill_route_node` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键ID，自增',
  `waybill_id` BIGINT UNSIGNED NOT NULL COMMENT '运单ID，逻辑引用biz_waybill.id；与运单同事务写入，运单删除时一并逻辑删除',
  `seq` TINYINT UNSIGNED NOT NULL COMMENT '链路顺序号：从1开始按运输顺序递增；用于轨迹展示与打印顺序',
  `route_scope` TINYINT UNSIGNED NOT NULL COMMENT '路由域：1=短驳/干线（sys_route），2=外转/直达（sys_route_transfer）',
  `route_id` BIGINT UNSIGNED NOT NULL COMMENT '来源路由ID：按route_scope指向对应路由表；仅作来源追溯，展示以快照字段为准',
  `route_code` VARCHAR(32) NOT NULL COMMENT '来源路由编码快照：短驳DB/干线GX或区划编码_ZT/_WZ；用于打印与对账',
  `route_category` TINYINT UNSIGNED NULL DEFAULT NULL COMMENT '短驳/干线类型快照：1=短驳，2=干线；route_scope=2时为空',
  `transfer_type` TINYINT UNSIGNED NULL DEFAULT NULL COMMENT '外转/直达类型快照：1=直达，2=外转；route_scope=1时为空',
  `from_dept_id` BIGINT UNSIGNED NOT NULL COMMENT '起点机构ID：引用sys_dept.id；第一段为开单网点',
  `from_dept_name` VARCHAR(50) NOT NULL COMMENT '起点机构名称快照：保存时写入，机构改名不影响历史',
  `to_dept_id` BIGINT UNSIGNED NULL DEFAULT NULL COMMENT '到达机构ID：引用sys_dept.id；外转/直达末段到达的是区划，此列为空',
  `to_dept_name` VARCHAR(50) NULL DEFAULT NULL COMMENT '到达机构名称快照：外转/直达末段为空',
  `dest_region_code` VARCHAR(12) NULL DEFAULT NULL COMMENT '末段目的地行政区划编码：引用sys_region.code；仅route_scope=2时填写',
  `dest_region_name` VARCHAR(200) NULL DEFAULT NULL COMMENT '末段目的地行政区划全路径名称快照：仅route_scope=2时填写',
  `carrier_id` BIGINT UNSIGNED NULL DEFAULT NULL COMMENT '承运商ID：引用sys_carrier.id；仅外转末段可能填写',
  `carrier_name` VARCHAR(64) NULL DEFAULT NULL COMMENT '承运商名称快照：仅外转末段可能填写',
  `warehouse_area` VARCHAR(100) NULL DEFAULT NULL COMMENT '库区快照：取干线路由文本字段，打印标签直接使用，不联表',
  `mileage` DECIMAL(10,2) NULL DEFAULT NULL COMMENT '本段里程(km)快照：空值表示未配置',
  `transit_hours` DECIMAL(10,2) NULL DEFAULT NULL COMMENT '本段运输时效(h)快照：空值表示未配置',
  `dept_id` BIGINT UNSIGNED NOT NULL COMMENT '归属机构ID：与运单dept_id一致，数据权限锚点',
  `create_by` BIGINT UNSIGNED NULL DEFAULT NULL COMMENT '创建人用户ID：由数据权限自动填充',
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间：与运单同一事务写入',
  `deleted` TINYINT(1) NOT NULL DEFAULT 0 COMMENT '逻辑删除：0=未删除，1=已删除；随运单作废/删除同步',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_waybill_seq` (`waybill_id`, `seq`),
  KEY `idx_route` (`route_scope`, `route_id`),
  KEY `idx_carrier` (`carrier_id`, `create_time`),
  KEY `idx_dept` (`dept_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='运单路由节点快照表：保存开单时选中的完整链路，供详情轨迹与打印使用；不随路由配置变化';

-- ----------------------------------------------------------------------------
-- 3. 客户主数据表（简易口径）
-- ----------------------------------------------------------------------------
CREATE TABLE `biz_customer` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键ID，自增；被biz_waybill.customer_id逻辑引用',
  `customer_name` VARCHAR(100) NOT NULL COMMENT '客户名称：企业或个人名称；同一归属机构下由应用层校验不重名，不建全局唯一索引（deleted=0范围内）',
  `contact_phone` VARCHAR(20) NULL DEFAULT NULL COMMENT '客户联系电话：手机或座机，7-20位，可空；匹配同名客户时用于消歧',
  `address` VARCHAR(200) NULL DEFAULT NULL COMMENT '客户地址：选填，用于开单辅助带出或联系',
  `status` TINYINT(1) NOT NULL DEFAULT 1 COMMENT '客户状态：1=启用，0=停用；停用客户不再参与新开单匹配',
  `remark` VARCHAR(500) NULL DEFAULT NULL COMMENT '备注：客户特殊说明，可空',
  `dept_id` BIGINT UNSIGNED NOT NULL COMMENT '归属机构ID：引用sys_dept.id；数据权限锚点',
  `create_by` BIGINT UNSIGNED NULL DEFAULT NULL COMMENT '创建人用户ID：由数据权限自动填充，可空',
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_by` BIGINT UNSIGNED NULL DEFAULT NULL COMMENT '最后修改人用户ID：由数据权限自动填充，可空',
  `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '最后修改时间',
  `deleted` TINYINT(1) NOT NULL DEFAULT 0 COMMENT '逻辑删除：0=正常，1=已删除；被运单引用时不物理删除',
  PRIMARY KEY (`id`),
  KEY `idx_name` (`customer_name`),
  KEY `idx_phone` (`contact_phone`),
  KEY `idx_dept` (`dept_id`),
  KEY `idx_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='简易客户表：仅承载开单与运单当前需要的名称、电话、地址、状态与归属机构；不预置信用、等级、结算方式';

-- ----------------------------------------------------------------------------
-- 4. 运单号日流水表（方案 8.2 取号 SQL：seq_date + current_val）
-- ----------------------------------------------------------------------------
CREATE TABLE `biz_waybill_no_seq` (
  `seq_date` DATE NOT NULL COMMENT '流水日期：按服务器自然日切分，每天一行',
  `current_val` INT UNSIGNED NOT NULL DEFAULT 0 COMMENT '当日已分配的最大流水号：从1开始，每次原子加1；用于生成运单号后6位',
  `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '最后分配时间：便于排查异常与清理历史行',
  PRIMARY KEY (`seq_date`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='运单号日流水表：仅做并发安全编号分配，不含业务数据；登记数据权限豁免（无dept_id）';

-- ----------------------------------------------------------------------------
-- 5. 预开单号日流水表（结构与运单号流水一致）
-- ----------------------------------------------------------------------------
CREATE TABLE `biz_pre_waybill_no_seq` (
  `seq_date` DATE NOT NULL COMMENT '流水日期：按服务器自然日切分，每天一行',
  `current_val` INT UNSIGNED NOT NULL DEFAULT 0 COMMENT '当日已分配的最大流水号：从1开始，每次原子加1；用于生成预开单号后6位',
  `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '最后分配时间：便于排查异常与清理历史行',
  PRIMARY KEY (`seq_date`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='预开单号日流水表：仅做并发安全编号分配，不含业务数据；登记数据权限豁免（无dept_id）';

-- ----------------------------------------------------------------------------
-- 6. 预开单表（客户申报，接单后生成正式运单）
-- ----------------------------------------------------------------------------
CREATE TABLE `biz_pre_waybill` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键ID，自增；被biz_waybill.pre_order_no逻辑关联',
  `pre_order_no` VARCHAR(32) NOT NULL COMMENT '预开单号：YJ+yyyyMMdd+6位日内流水，全平台唯一；客户端提交时由服务端生成',
  `status` TINYINT UNSIGNED NOT NULL DEFAULT 10 COMMENT '预开单状态：10=待接单，20=已接单，30=已揽收（已生成正式运单），90=已撤单',
  `customer_id` BIGINT UNSIGNED NULL DEFAULT NULL COMMENT '客户ID：引用biz_customer.id；客户端账号绑定客户时写入，可空',
  `customer_name` VARCHAR(100) NOT NULL COMMENT '客户名称快照：客户端申报值；接单与生成运单时沿用，开单页只读',
  `customer_phone` VARCHAR(20) NULL DEFAULT NULL COMMENT '客户联系电话快照：客户端申报值，可空；开单页只读',
  `sender_name` VARCHAR(50) NOT NULL COMMENT '发货人姓名：客户端申报，接单后可补录修正',
  `sender_phone` VARCHAR(20) NOT NULL COMMENT '发货人电话：7-20位；客户端申报，接单后可补录修正',
  `sender_address` VARCHAR(200) NOT NULL COMMENT '发货详细地址：客户端申报，接单后可补录修正',
  `receiver_name` VARCHAR(50) NOT NULL COMMENT '收货人姓名：客户端申报，接单后可补录修正',
  `receiver_phone` VARCHAR(20) NOT NULL COMMENT '收货人电话：7-20位；客户端申报，接单后可补录修正',
  `receiver_address` VARCHAR(200) NOT NULL COMMENT '收货详细地址：客户端申报，接单后可补录修正',
  `dest_province_code` VARCHAR(12) NOT NULL COMMENT '目的地省编码：引用sys_region.code，统一VARCHAR(12)',
  `dest_province_name` VARCHAR(64) NOT NULL COMMENT '目的地省名称快照：区划改名不影响历史',
  `dest_city_code` VARCHAR(12) NOT NULL COMMENT '目的地市编码：引用sys_region.code，统一VARCHAR(12)',
  `dest_city_name` VARCHAR(64) NOT NULL COMMENT '目的地市名称快照',
  `dest_district_code` VARCHAR(12) NOT NULL COMMENT '目的地区县编码：引用sys_region.code，路由末段精确匹配依据；统一VARCHAR(12)',
  `dest_district_name` VARCHAR(64) NOT NULL COMMENT '目的地区县名称快照',
  `dest_name` VARCHAR(200) NOT NULL COMMENT '目的地全路径展示名：省+市+区县自动拼接，列表与导出使用',
  `goods_name` VARCHAR(100) NOT NULL COMMENT '货物名称：客户端申报，接单后可补录修正',
  `quantity` INT UNSIGNED NOT NULL COMMENT '件数：正整数，客户端申报，列表合计项；无默认值，必须显式传值，服务端校验≥1',
  `weight` DECIMAL(12,3) NOT NULL COMMENT '重量(kg)：最多3位小数，客户端申报，列表合计项；无默认值，必须显式传值，服务端校验>0',
  `volume` DECIMAL(12,3) NOT NULL DEFAULT 0.000 COMMENT '体积(m³)：最多3位小数，客户端申报，列表合计项；客户端可不申报，未申报按0落库（N4）；申报时服务端校验>0',
  `goods_remark` VARCHAR(500) NULL DEFAULT NULL COMMENT '货物备注：客户端申报的货物特殊说明，可空',
  `expect_pickup_time` DATETIME NULL DEFAULT NULL COMMENT '期望提货时间：客户希望上门提货的时间，可空',
  `submit_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '提交时间：客户端提交时服务端写入；列表默认按此字段倒序',
  `accept_by` BIGINT UNSIGNED NULL DEFAULT NULL COMMENT '接单人用户ID：引用sys_user.id；接单时写入，退回时清空',
  `accept_by_name` VARCHAR(50) NULL DEFAULT NULL COMMENT '接单人姓名快照：接单时写入，避免用户改名影响历史展示',
  `accept_time` DATETIME NULL DEFAULT NULL COMMENT '接单时间：接单动作的服务端时间；退回时清空',
  `waybill_no` VARCHAR(32) NULL DEFAULT NULL COMMENT '生成的运单号：确认揽收后回写；未生成时为空；与biz_waybill.waybill_no一致',
  `dept_id` BIGINT UNSIGNED NOT NULL COMMENT '归属机构ID：引用sys_dept.id；预开单受理网点；数据权限锚点',
  `dept_name` VARCHAR(50) NOT NULL COMMENT '归属机构名称快照：受理网点名称，列表与导出使用',
  `version` INT UNSIGNED NOT NULL DEFAULT 0 COMMENT '乐观锁版本号：接单/确认揽收/退回时校验并加1，防止重复接单与并发覆盖',
  `create_by` BIGINT UNSIGNED NULL DEFAULT NULL COMMENT '创建人用户ID：客户端提交时写入客户账号ID或系统账号，可空；接单/确认揽收不覆盖该字段',
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间：记录入库时间',
  `update_by` BIGINT UNSIGNED NULL DEFAULT NULL COMMENT '最后修改人用户ID：接单/退回/确认揽收时更新为操作人',
  `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '最后修改时间：数据库自动刷新',
  `deleted` TINYINT(1) NOT NULL DEFAULT 0 COMMENT '逻辑删除：0=正常，1=已删除；已撤单用status=90，不用删除',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_pre_order_no` (`pre_order_no`),
  KEY `idx_status_submit` (`status`, `submit_time`),
  KEY `idx_submit_time` (`submit_time`),
  KEY `idx_dept_status` (`dept_id`, `status`),
  KEY `idx_customer` (`customer_id`),
  KEY `idx_customer_name` (`customer_name`),
  KEY `idx_dest` (`dest_province_code`, `dest_city_code`, `dest_district_code`),
  KEY `idx_expect_pickup` (`expect_pickup_time`),
  KEY `idx_accept_by` (`accept_by`),
  KEY `idx_waybill_no` (`waybill_no`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='预开单表：客户客户端自助申报的待揽收单据；接单、补录并确认揽收后生成运单；行级数据权限按dept_id过滤';

-- ----------------------------------------------------------------------------
-- 7. 运单操作日志表（拆/合/撤/作废/强制签收统一落点）
-- ----------------------------------------------------------------------------
CREATE TABLE `biz_waybill_log` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键ID，自增',
  `waybill_id` BIGINT UNSIGNED NOT NULL COMMENT '母单ID：引用biz_waybill.id；本表所有日志统一挂母单（子单相关操作也记母单ID），按母单汇总查询；子单明细见快照children[]',
  `waybill_no` VARCHAR(32) NOT NULL COMMENT '母单号快照：便于按母单号直接查日志；本期不支持按子单号反查（限制见方案7.6）',
  `action_type` TINYINT UNSIGNED NOT NULL COMMENT '动作类型：1=拆单，2=合单，3=撤销拆单，4=作废，5=强制签收',
  `before_snapshot` JSON NOT NULL COMMENT '操作前快照：必填；固定结构 {"waybill":{"id","no","status","parent_id","parent_no"},"children":[{"id","no","seq","status","quantity","weight","volume"}]}，无子单时 children 为空数组',
  `after_snapshot` JSON NOT NULL COMMENT '操作后快照：必填；结构同 before_snapshot，记录动作完成后的状态',
  `reason` VARCHAR(500) NULL DEFAULT NULL COMMENT '操作原因：action_type=3撤销拆单/4作废/5强制签收必填；1拆单/2合单可空；不写入biz_waybill.remark',
  `operator_id` BIGINT UNSIGNED NOT NULL COMMENT '操作人用户ID：引用sys_user.id',
  `operator_name` VARCHAR(50) NOT NULL COMMENT '操作人姓名快照：操作时写入，避免用户改名影响历史',
  `dept_id` BIGINT UNSIGNED NOT NULL COMMENT '操作机构ID：引用sys_dept.id；数据权限锚点',
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '操作时间：服务器时间',
  PRIMARY KEY (`id`),
  KEY `idx_waybill` (`waybill_id`, `create_time`),
  KEY `idx_waybill_no` (`waybill_no`),
  KEY `idx_action` (`action_type`, `create_time`),
  KEY `idx_dept` (`dept_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='运单操作日志表：拆单、合单、撤销拆单、作废、强制签收的动作与前后快照统一落此表；撤销拆单/作废/强制签收必须写原因；日志统一挂母单';
