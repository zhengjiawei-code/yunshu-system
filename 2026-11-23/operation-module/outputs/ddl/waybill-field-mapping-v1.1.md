# 运单主表 58 项字段 ↔ DDL 字段对照（v1.1）

> 对应方案：`operation-module-design-v1.13.md` 4.1（L744-803）；对应 DDL：`waybill-ddl-v1.1-pending-review.sql`（表 `biz_waybill`）。
> 口径：方案 58 项为**逻辑字段**；DDL 物理列 77 个，其中 **64 个**承载 58 项（#4/#5/#6/#7/#57/#58 共 6 项按"编码+名称"或"ID+名称"成对落列，58+6=64），其余 **13 个**为技术/派生列，见文末。
> 本表逐项标注两处行号：`方案行` = v1.13 4.1 行号；`DDL行` = v1.1 脚本行号。

| # | 分组 | 字段（中文） | DDL 字段（biz_waybill） | 方案行 | DDL 行 |
|---|---|---|---|---|---|
| 1 | 单据 | 运单号 | `waybill_no` | L746 | L24 |
| 2 | 单据 | 关联单号 | `related_no` | L747 | L25 |
| 3 | 目的地 | 目的地 | `dest_name` | L748 | L43 |
| 4 | 目的地 | 目的地省 | `dest_province_name` + `dest_province_code` | L749 | L38 / L37 |
| 5 | 目的地 | 目的市 | `dest_city_name` + `dest_city_code` | L750 | L40 / L39 |
| 6 | 目的地 | 目的地区县 | `dest_district_name` + `dest_district_code` | L751 | L42 / L41 |
| 7 | 基础 | 业务员 | `sales_name` + `sales_id` | L752 | L35 / L34 |
| 8 | 基础 | 业务类型 | `biz_type` | L753 | L36 |
| 9 | 基础 | 线路 | `route_name` | L754 | L48 |
| 10 | 发货方 | 发货人 | `sender_name` | L755 | L54 |
| 11 | 发货方 | 发货电话 | `sender_phone` | L756 | L55 |
| 12 | 发货方 | 发货地址 | `sender_address` | L757 | L56 |
| 13 | 发货方 | 客户名称 | `customer_name` | L758 | L52 |
| 14 | 发货方 | 客户电话 | `customer_phone` | L759 | L53 |
| 15 | 收货方 | 收货人 | `receiver_name` | L760 | L57 |
| 16 | 收货方 | 收货电话 | `receiver_phone` | L761 | L58 |
| 17 | 收货方 | 收货地址 | `receiver_address` | L762 | L59 |
| 18 | 货物 | 货物名称 | `goods_name` | L763 | L60 |
| 19 | 货物 | 货物类型 | `goods_type` | L764 | L61 |
| 20 | 货物 | 包装类型 | `package_type` | L765 | L62 |
| 21 | 货物 | 件数 | `quantity` | L766 | L63 |
| 22 | 货物 | 重量 | `weight` | L767 | L64 |
| 23 | 货物 | 体积 | `volume` | L768 | L65 |
| 24 | 货物 | 货物备注 | `goods_remark` | L769 | L66 |
| 25 | 支付 | 支付方式 | `pay_method` | L770 | L67 |
| 26 | 费用 | 运费 | `freight_fee` | L771 | L68 |
| 27 | 费用 | 送货费 | `delivery_fee` | L772 | L69 |
| 28 | 费用 | 提货费 | `pickup_fee` | L773 | L70 |
| 29 | 费用 | 其他费 | `other_fee` | L774 | L71 |
| 30 | 费用 | 回单费 | `receipt_fee` | L775 | L72 |
| 31 | 费用 | 制单费 | `make_bill_fee` | L776 | L73 |
| 32 | 费用 | 保费 | `insurance_fee` | L777 | L74 |
| 33 | 费用 | 声明价值 | `declared_value` | L778 | L75 |
| 34 | 费用 | 现返 | `cash_back` | L779 | L81 |
| 35 | 费用 | 欠返 | `owe_back` | L780 | L82 |
| 36 | 费用（派生） | 返款合计 | `rebate_total` | L781 | L83 |
| 37 | 费用（派生） | 有效运费 | `valid_freight` | L782 | L84 |
| 38 | 支付 | 现付运费 | `prepaid_fee` | L783 | L77 |
| 39 | 支付 | 到付运费 | `collect_fee` | L784 | L78 |
| 40 | 支付 | 月结运费 | `monthly_fee` | L785 | L79 |
| 41 | 支付 | 回付运费 | `return_fee` | L786 | L80 |
| 42 | 其他 | 送货方式 | `delivery_method` | L787 | L85 |
| 43 | 代收 | 代收货款 | `cod_amount` | L788 | L86 |
| 44 | 代收 | 收款人 | `payee_name` | L789 | L87 |
| 45 | 代收 | 银行名称 | `bank_name` | L790 | L88 |
| 46 | 代收 | 银行卡号 | `bank_card_no` | L791 | L89 |
| 47 | 其他 | 是否回单 | `need_receipt` | L792 | L90 |
| 48 | 其他 | 回单数量 | `receipt_count` | L793 | L91 |
| 49 | 其他 | 是否等通知 | `wait_notice` | L794 | L92 |
| 50 | 其他 | 备注 | `remark` | L795 | L93 |
| 51 | 审计 | 开单员 | `create_by_name` | L796 | L95 |
| 52 | 审计 | 开单时间 | `create_time` | L797 | L96 |
| 53 | 子母单 | 母单ID | `parent_id` | L798 | L27 |
| 54 | 子母单 | 母单号 | `parent_no` | L799 | L28 |
| 55 | 子母单 | 是否子单 | `is_child` | L800 | L29 |
| 56 | 子母单 | 子单序号 | `child_seq` | L801 | L30 |
| 57 | 网点 | 始发网点 | `origin_hub_id` + `origin_hub_name` | L802 | L44 / L45 |
| 58 | 网点 | 运达网点 | `dest_hub_id` + `dest_hub_name` | L803 | L46 / L47 |

## DDL 中的技术/派生列（13 个，不计入 58 项）

| 字段 | DDL 行 | 说明 |
|---|---|---|
| `id` | L23 | 主键 |
| `pre_order_no` | L26 | 来源预开单号（单据关联） |
| `status` | L31 | 运单状态（流程字段，见 9.1） |
| `dept_id` | L32 | 归属机构ID（数据权限锚点） |
| `dept_name` | L33 | 归属机构名称快照 |
| `route_node_count` | L49 | 线路节点数（展示/校验） |
| `route_end_type` | L50 | 线路末端类型（徽标） |
| `customer_id` | L51 | 客户ID（关联主数据） |
| `total_fee` | L76 | 运费合计（服务端派生） |
| `create_by` | L94 | 创建人ID（数据权限填充） |
| `update_by` | L97 | 更新人ID |
| `update_time` | L98 | 更新时间 |
| `deleted` | L99 | 逻辑删除 |

## 核对结论

- 58 项逐项有落列，**无删减、无合并丢失**；6 个成对字段按 12 列落库，合计 64 列。
- 64 + 13 = **77 列**，与 `information_schema` 实测一致。
- 与 v1.0 对照相比，列名未变；仅类型/长度/默认值/注释按审查清单修正（见 `waybill-code-value-mapping-v1.1.md` 第 6 节）。
