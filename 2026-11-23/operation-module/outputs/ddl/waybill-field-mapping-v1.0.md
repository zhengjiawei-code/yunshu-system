# 运单主表 58 项字段 ↔ DDL 字段对照（v1.0）

> 对应文档：`operation-module-design-v1.12.md` 4.1；对应 DDL：`waybill-ddl-v1.0-pending-review.sql`。
> 口径：文档 58 项为**逻辑字段**；DDL 物理列 77 个，其中 **64 个**承载 58 项逻辑字段（#4/#5/#6/#7/#57/#58 共 6 项按“编码+名称”或“ID+名称”成对落列，故 58+6=64），其余 **13 个**为技术/派生列（主键、预开单关联、状态、客户ID、机构、线路统计、合计、审计、删除标记）。

| 文档项号 | 分组 | 字段 | 对应 DDL 字段（biz_waybill） |
|---|---|---|---|
| 1 | 单据 | 运单号 | `waybill_no` |
| 2 | 单据 | 关联单号 | `related_no` |
| 3 | 目的地 | 目的地 | `dest_name` |
| 4 | 目的地 | 目的地省 | `dest_province_name` + `dest_province_code` |
| 5 | 目的地 | 目的市 | `dest_city_name` + `dest_city_code` |
| 6 | 目的地 | 目的地区县 | `dest_district_name` + `dest_district_code` |
| 7 | 基础 | 业务员 | `sales_name` + `sales_id` |
| 8 | 基础 | 业务类型 | `biz_type` |
| 9 | 基础 | 线路 | `route_name` |
| 10 | 发货方 | 发货人 | `sender_name` |
| 11 | 发货方 | 发货电话 | `sender_phone` |
| 12 | 发货方 | 发货地址 | `sender_address` |
| 13 | 发货方 | 客户名称 | `customer_name` |
| 14 | 发货方 | 客户电话 | `customer_phone` |
| 15 | 收货方 | 收货人 | `receiver_name` |
| 16 | 收货方 | 收货电话 | `receiver_phone` |
| 17 | 收货方 | 收货地址 | `receiver_address` |
| 18 | 货物 | 货物名称 | `goods_name` |
| 19 | 货物 | 货物类型 | `goods_type` |
| 20 | 货物 | 包装类型 | `package_type` |
| 21 | 货物 | 件数 | `quantity` |
| 22 | 货物 | 重量 | `weight` |
| 23 | 货物 | 体积 | `volume` |
| 24 | 货物 | 货物备注 | `goods_remark` |
| 25 | 支付 | 支付方式 | `pay_method` |
| 26 | 费用 | 运费 | `freight_fee` |
| 27 | 费用 | 送货费 | `delivery_fee` |
| 28 | 费用 | 提货费 | `pickup_fee` |
| 29 | 费用 | 其他费 | `other_fee` |
| 30 | 费用 | 回单费 | `receipt_fee` |
| 31 | 费用 | 制单费 | `make_bill_fee` |
| 32 | 费用 | 保费 | `insurance_fee` |
| 33 | 费用 | 声明价值 | `declared_value` |
| 34 | 费用 | 现返 | `cash_back` |
| 35 | 费用 | 欠返 | `owe_back` |
| 36 | 费用（派生） | 返款合计 | `rebate_total` |
| 37 | 费用（派生） | 有效运费 | `valid_freight` |
| 38 | 支付 | 现付运费 | `prepaid_fee` |
| 39 | 支付 | 到付运费 | `collect_fee` |
| 40 | 支付 | 月结运费 | `monthly_fee` |
| 41 | 支付 | 回付运费 | `return_fee` |
| 42 | 其他 | 送货方式 | `delivery_method` |
| 43 | 代收 | 代收货款 | `cod_amount` |
| 44 | 代收 | 收款人 | `payee_name` |
| 45 | 代收 | 银行名称 | `bank_name` |
| 46 | 代收 | 银行卡号 | `bank_card_no` |
| 47 | 其他 | 是否回单 | `need_receipt` |
| 48 | 其他 | 回单数量 | `receipt_count` |
| 49 | 其他 | 是否等通知 | `wait_notice` |
| 50 | 其他 | 备注 | `remark` |
| 51 | 审计 | 开单员 | `create_by_name` |
| 52 | 审计 | 开单时间 | `create_time` |
| 53 | 子母单 | 母单ID | `parent_id` |
| 54 | 子母单 | 母单号 | `parent_no` |
| 55 | 子母单 | 是否子单 | `is_child` |
| 56 | 子母单 | 子单序号 | `child_seq` |
| 57 | 网点 | 始发网点 | `origin_hub_id` + `origin_hub_name` |
| 58 | 网点 | 运达网点 | `dest_hub_id` + `dest_hub_name` |

## DDL 中的技术/派生列（不计入 58 项）

| 字段 | 说明 |
|---|---|
| `id` | 主键 |
| `pre_order_no` | 来源预开单号（单据关联，不计入 58 项） |
| `status` | 运单状态（流程字段，不计入 58 项） |
| `dept_id` | 归属机构ID |
| `dept_name` | 归属机构名称快照 |
| `route_node_count` | 线路节点数（展示用） |
| `route_end_type` | 线路末端类型（徽标用） |
| `customer_id` | 客户ID（关联主数据，不计入 58 项） |
| `total_fee` | 费用合计（派生） |
| `create_by` | 创建人ID |
| `update_by` | 更新人ID |
| `update_time` | 更新时间 |
| `deleted` | 逻辑删除 |
