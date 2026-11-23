# 运单模块码值 / 默认值对照表 v1.2

> 用途：复审收尾版（R1/R2/R3/S1/S6）的逐项核对依据。
> 方案：`operation-module-design-v1.14.md`；DDL：`waybill-ddl-v1.2-pending-review.sql`（正式名 `V13__operation_waybill.sql`，内容一致）。
> **枚举值未变**：`status`、`biz_type`、`pay_method`、`delivery_method`、`route_end_type`、`need_receipt`、`wait_notice`、`is_child`、`route_scope`、`route_category`、`transfer_type`、客户/预开单状态、`action_type` 全部沿用 `waybill-code-value-mapping-v1.1.md`，本表不重复。

## 1. 必填数值字段默认值（R1）

| 表.字段 | 类型 | v1.1 默认 | **v1.2 默认** | 方案出处 | DDL 行 |
|---|---|---|---|---|---|
| `biz_waybill.quantity` | `INT UNSIGNED` | 0 | **无（NULL）** | 4.1 #21、7.1 | L64 |
| `biz_waybill.weight` | `DECIMAL(12,3)` | 0.000 | **无（NULL）** | 4.1 #22、7.1 | L65 |
| `biz_waybill.volume` | `DECIMAL(12,3)` | 0.000 | **无（NULL）** | 4.1 #23、7.1 | L66 |
| `biz_pre_waybill.quantity` | `INT UNSIGNED` | 0 | **无（NULL）** | 4.4 #15、7.5 | L224 |
| `biz_pre_waybill.weight` | `DECIMAL(12,3)` | 0.000 | **无（NULL）** | 4.4 #16、7.5 | L225 |
| `biz_pre_waybill.volume` | `DECIMAL(12,3)` | 0.000 | **0.000（保留）** | 4.4 #17、7.5（N4） | L226 |

服务层校验：运单 `quantity>=1`、`weight>0`、`volume>0`；预开单 `quantity>=1`、`weight>0`，`volume` 未申报按 0、申报则 >0（方案 5.4 第 8 条）。

## 2. 操作日志契约（R2）

| 项 | v1.2 规则 | 方案出处 | DDL 行 |
|---|---|---|---|
| `before_snapshot` | `JSON NOT NULL`；固定结构 `waybill{id,no,status,parent_id,parent_no}` + `children[{id,no,seq,status,quantity,weight,volume}]`；无子单写空数组 | 7.6 快照契约 | L263 |
| `after_snapshot` | `JSON NOT NULL`；结构同前 | 7.6 | L264 |
| `reason` | `action_type=3 撤销拆单 / 4 作废 / 5 强制签收` **必填**；`1 拆单 / 2 合单` 可空 | 7.6 规则表 | L265 |
| 快照必填范围 | 1/2/3/4/5 五类动作**均必须写**前后快照 | 7.6 | L263-264 |
| 日志归属 | 统一挂母单：`waybill_id`=母单ID、`waybill_no`=母单号 | 7.6 | L260-261 |
| 查询口径 | 本期只按母单查；不提供按子单号反查，子单相关记录在 `children[]` 过滤 | 7.6 限制条 | L261 |

## 3. 默认值/索引收口（R3/S1）

| 项 | v1.2 值 | 方案出处 | DDL 行 |
|---|---|---|---|
| `biz_waybill.customer_name` | `NOT NULL DEFAULT ''` | 7.1（v1.14 补默认值） | L53 |
| `biz_waybill.route_node_count` | `TINYINT UNSIGNED NOT NULL DEFAULT 0` | 7.1（v1.14 补默认值） | L50 |
| 路由节点承运商索引 | `KEY idx_carrier (carrier_id, create_time)` | 7.2（v1.14/R3） | L153 |

## 4. 核对结论

- 六个改动项与方案 v1.14 逐项一致；枚举值相对 v1.1 无变化。
- 临时库实测 `COLUMN_DEFAULT`：运单三项与预开单 quantity/weight 均为 `NULL`，预开单 volume 为 `0.000`；`idx_carrier` 存在；日志两快照 `IS_NULLABLE=NO`。
