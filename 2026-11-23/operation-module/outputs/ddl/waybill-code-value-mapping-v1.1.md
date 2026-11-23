# 运单模块码值对照表 v1.1（DDL ↔ 方案 v1.13）

> 用途：供独立复审逐项核对 DDL 的枚举、默认值与方案字典是否一致。
> 唯一字典来源：`operation-module-design-v1.13.md`（以下简称"方案"）。
> DDL 文件：`waybill-ddl-v1.1-pending-review.sql`（V13，待审未执行）。
> 行号说明：`方案 Lxxx` 指 v1.13 文件行号；`DDL Lxxx` 指 v1.1 文件行号。行号以本次交付版本为准。

## 1. 运单主表 `biz_waybill` 枚举

| 字段 | DDL 新值 | 默认值 | 方案出处 | DDL 行 | 核对 |
|---|---|---|---|---|---|
| `status` 运单状态 | 10=待揽收，20=已揽收，25=已拆单（母单），30=已装车，40=运输中，50=已到达，55=部分到达，60=已签收（含 Q28 强制签收），70=异常，90=已作废 | **20** | 方案 9.1（L1712-1725）、7.1（L1154-1155） | DDL L31 | ✅ |
| `biz_type` 业务类型 | 1=整车，2=零担 | **2** | 方案 4.1 #8（L753）、7.1（L1164-1165） | DDL L36 | ✅ |
| `pay_method` 支付方式 | 1=现付，2=到付，3=月结，4=回付，5=多笔付 | **无默认**（未选拦截） | 方案 4.1 #25（L770）、5.2（L910-942）、7.1（L1226-1227） | DDL L67 | ✅ |
| `delivery_method` 送货方式 | 1=送货，2=自提，3=大车直送（展示顺序≠码值） | 无默认 | 方案 4.1 #42（L787）、7.1（L1262-1263） | DDL L85 | ✅ |
| `route_end_type` 线路末端类型 | 1=直达，2=外转 | 无默认（必填） | 方案 4.1 派生表（L811）、7.1（L1192-1193） | DDL L50 | ✅ |
| `need_receipt` 是否回单 | 0=否，1=是 | **0** | 方案 4.1 #47（L792）、7.1（L1272-1273） | DDL L90 | ✅ |
| `wait_notice` 等通知放货 | 0=否，1=是 | **0** | 方案 4.1 #49（L794）、7.1（L1276-1277） | DDL L92 | ✅ |
| `is_child` 是否子单 | 0=母单，1=子单 | **0** | 方案 4.1 #55（L800）、7.1（L1150-1151） | DDL L29 | ✅ |
| `deleted` 逻辑删除（全表） | 0=正常，1=已删除 | **0** | 方案 7.1/7.2/7.3/7.5/7.6 | DDL L99、L148、L170、L239 | ✅ |

> 注：`status` 三个语义错位项已在 DDL 修正为 **30=已装车、40=运输中、50=已到达**；默认值由 10 改为 20（开单即已揽收）。

## 2. 运单主表关键默认值（非枚举）

| 字段 | 类型 | DDL 默认值 | 方案出处 | DDL 行 |
|---|---|---|---|---|
| `is_child` | TINYINT(1) | 0 | 4.1 #55 / 7.1 | L29 |
| `child_seq` | SMALLINT UNSIGNED | NULL | 4.1 #56（L801） | L30 |
| `route_node_count` | TINYINT UNSIGNED | 0 | 4.1 派生表（L810） | L49 |
| `quantity` | INT UNSIGNED | 0（业务校验 ≥1） | 4.1 #21（L766） | L63 |
| `weight` / `volume` | DECIMAL(12,3) | 0.000（业务校验 >0） | 4.1 #22/#23（L767-768） | L64-65 |
| `freight_fee` 等 7 项费用 | DECIMAL(12,2) | 0.00 | 4.1 #26-#32（L771-777） | L68-74 |
| `declared_value` | DECIMAL(14,2) | 0.00 | 4.1 #33（L778） | L75 |
| 四项支付金额 | DECIMAL(12,2) | 0.00 | 4.1 #38-#41（L783-786） | L77-80 |
| `cash_back` / `owe_back` | DECIMAL(12,2) | 0.00，且 ≥0 | 4.1 #34/#35（L779-780） | L81-82 |
| `cod_amount` | DECIMAL(12,2) | 0.00，且 ≥0 | 4.1 #43（L788） | L86 |
| `receipt_count` | INT UNSIGNED | 0 | 4.1 #48（L793） | L91 |
| `status` | TINYINT UNSIGNED | 20 | 9.1 / 7.1 | L31 |

## 3. 路由节点表 `biz_waybill_route_node` 枚举

| 字段 | DDL 值 | 可空规则 | 方案出处 | DDL 行 |
|---|---|---|---|---|
| `route_scope` 路由域 | **1=短驳/干线（sys_route），2=外转/直达（sys_route_transfer）** | NOT NULL | 方案 7.2（L1343-1344） | DDL L129 |
| `route_category` 短驳/干线类型 | 1=短驳，2=干线 | `route_scope=2` 时为空 | 方案 7.2（L1349-1350） | DDL L132 |
| `transfer_type` 外转/直达类型 | 1=直达，2=外转 | `route_scope=1` 时为空 | 方案 7.2（L1351-1352） | DDL L133 |

> `route_scope` 已恢复方案语义：**1 不是"直达/外转"**，而是"短驳/干线"；外转/直达归 2。v1.0 的语义错位已修正。

## 4. 客户 / 预开单 / 操作日志枚举

| 表.字段 | DDL 值 | 默认值 | 方案出处 | DDL 行 |
|---|---|---|---|---|
| `biz_customer.status` | 1=启用，0=停用 | **1** | 方案 7.3（L1403-1404） | DDL L163 |
| `biz_pre_waybill.status` | 10=待接单，20=已接单，30=已揽收，90=已撤单 | **10** | 方案 9.4、7.5（L1478-1479） | DDL L204 |
| `biz_waybill_log.action_type` | **1=拆单，2=合单，3=撤销拆单，4=作废，5=强制签收** | 无默认（必填） | 方案 7.6（L1582-1583） | DDL L260 |

> 拆/合/撤/作废/强制签收的**原因**统一落 `biz_waybill_log.reason`，不写 `biz_waybill.remark`（方案 7.6 L1589、4.1 #50 L795、9.2 L1750）。

## 5. 派生金额语义（服务端计算并强制覆盖）

| 字段 | 公式 / 规则 | 可负性 | 方案出处 | DDL 行 |
|---|---|---|---|---|
| `total_fee` 运费合计 | ΣF = 运费+送货费+提货费+其他费+回单费+制单费+保费；**不含声明价值** | ≥0（各项 ≥0） | 方案 5.1（L893-895）、7.1（L1244-1245） | DDL L76 |
| `rebate_total` 返款合计 | `cash_back + owe_back` | **≥0**（两项均 ≥0） | 方案 5.1（L894）、7.1（L1258-1259） | DDL L83 |
| `valid_freight` 有效运费 | `total_fee − rebate_total` | **可为负**（Q17） | 方案 5.1（L895）、7.1（L1260-1261） | DDL L84 |
| `prepaid_fee` 现付运费 | 1 现付=total_fee；2/3/4=0；5 多笔付读客户端分摊并校验四项合计=total_fee | **≥0** | 方案 5.2（L910-942）、5.4（L944-960） | DDL L77 |
| `collect_fee` 到付运费 | 2 到付=total_fee；1/3/4=0；5 同上 | **≥0** | 同上 | DDL L78 |
| `monthly_fee` 月结运费 | 3 月结=total_fee；1/2/4=0；5 同上 | **≥0** | 同上 | DDL L79 |
| `return_fee` 回付运费 | 4 回付=total_fee；1/2/3=0；5 同上 | **≥0** | 同上 | DDL L80 |
| `cash_back` / `owe_back` | 手工录入，独立于运费合计与支付分摊 | **≥0** | 方案 4.1 #34/#35、5.1 | DDL L81-82 |
| `declared_value` 声明价值 | 手工录入；**不计入**运费合计、返款合计、有效运费与支付分摊 | ≥0 | 方案 4.1 #33（L778）、5.1（L891） | DDL L75 |

> DDL 中以上字段全部写明"服务端计算并强制覆盖，客户端传值无效"（`total_fee`/`rebate_total`/`valid_freight`/四项支付金额），不再出现"手填"表述。

## 6. 审查清单中的类型 / 长度 / 索引对照

| 项 | DDL v1.1 | 方案出处 | DDL 行 |
|---|---|---|---|
| `declared_value` | `DECIMAL(14,2)` | 4.1 #33（L778） | L75 |
| `weight` / `volume` | `DECIMAL(12,3)` | 4.1 #22/#23（L767-768） | L64-65 |
| `related_no` | `VARCHAR(64)` | 4.1 #2（L747） | L25 |
| `dest_name` | `VARCHAR(200)` | 4.1 #3（L748） | L43 |
| `goods_remark` | `VARCHAR(500)` | 4.1 #24（L769） | L66 |
| `dest_*_name` | `VARCHAR(64)` | 4.1 #4-#6（L749-751） | L38/40/42 |
| 区划编码 `dest_*_code` / `dest_region_code` | `VARCHAR(12)` | 4.1 #4-#6、7.2 | L37/39/41、L138 |
| `quantity` | `INT UNSIGNED` | 4.1 #21（L766） | L63 |
| `receipt_count` | `INT UNSIGNED` | 4.1 #48（L793） | L91 |
| `child_seq` | `SMALLINT UNSIGNED` | 4.1 #56（L801） | L30 |
| `create_by` | 可空 `BIGINT UNSIGNED DEFAULT NULL` | 7.1（L1280-1281） | L94 |
| `dest_hub_id` / `dest_hub_name` | 可空 | 4.1 #58（L803）、7.1（L1184-1187） | L46-47 |
| `idx_dest` | 复合 `(dest_province_code, dest_city_code, dest_district_code)` | 7.1 索引理由（L1327） | L113 |
| 运单列表索引 | `idx_child_deleted_time (is_child, deleted, create_time)` | 7.1（L1300） | L108 |
| 预开单提交时间索引 | `idx_submit_time (submit_time)` | 7.5 | L243 |
| 路由节点 `idx_waybill_no` | **已删除**（无按单号查节点接口，单号在运单主表） | 7.2 索引（L1383-1386） | L149-152 |
| 序列表 | `seq_date DATE` 主键 + `current_val INT UNSIGNED`，**无 seq_type** | 方案 8.2（L1656-1683）、7.4（L1439-1463） | L181-196 |

## 7. 核对结论

- 枚举项 **15 组**（运单 9、路由节点 3、客户 1、预开单 1、操作日志 1）全部与方案 v1.13 一致；默认值逐项一致。
- 派生金额 6 组 + 声明价值 1 项语义一致，且 DDL 注释统一为"服务端计算并强制覆盖"。
- 类型/长度/索引 17 项与审查清单一致；`FLOAT/DOUBLE` 为 0。
- 本表未覆盖的普通文本/金额字段（如发收货人、地址、银行信息）不涉及枚举，类型与方案 4.1 一致，见 `waybill-field-mapping-v1.0.md` 与 DDL 逐列注释。
