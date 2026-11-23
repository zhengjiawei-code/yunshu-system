# 运单模块待审 DDL 一页纸摘要 v1.1

> 文件：`waybill-ddl-v1.1-pending-review.sql`（不覆盖 v1.0）
> 对应方案：`operation-module-design-v1.13.md`
> Flyway：仓库最新 `V12__backfill_menu_button_audit.sql`，本稿接续 **V13**；正式名 `V13__operation_waybill.sql`
> 状态：**待独立复审 + 家伟确认；未执行、未连 yunshu 库**

## 1. 表清单与规模

| # | 表 | 列数 | 说明 |
|---|---|---|---|
| 1 | `biz_waybill` | 77 | 运单主表，母单/子单同表；58 项逻辑字段占 64 列，13 个技术/派生列 |
| 2 | `biz_waybill_route_node` | 23 | 路由分段快照（短驳/干线/外转/直达） |
| 3 | `biz_customer` | 12 | 简易客户表 |
| 4 | `biz_waybill_no_seq` | 3 | 运单号日流水（`seq_date` 主键） |
| 5 | `biz_pre_waybill_no_seq` | 3 | 预开单号日流水（同构） |
| 6 | `biz_pre_waybill` | 38 | 客户申报预开单 |
| 7 | `biz_waybill_log` | 11 | 拆/合/撤/作废/强制签收审计 |
| | **合计** | **167** | 7 张表 |

## 2. 相对 v1.0 的关键修正

1. `biz_type`：**1=整车、2=零担，默认 2**（v1.0 反了）。
2. `pay_method`：**4=回付、5=多笔付**，无默认。
3. `status`：**默认 20**；**30=已装车、40=运输中、50=已到达**。
4. 派生金额：`total_fee`/`rebate_total`/`valid_freight`/四项支付金额统一"服务端计算并强制覆盖，客户端传值无效"；`valid_freight` 可为负，`cash_back/owe_back ≥0`。
5. 路由节点表按方案 7.2 重写为 23 列，`route_scope` 恢复 **1=短驳/干线、2=外转/直达**。
6. 新增 `biz_waybill_log`，拆/合/撤/作废/强制签收原因不写运单备注。
7. 客户表删 `settle_type/credit_limit`，补 `create_by/update_by`，电话可空。
8. 序列表改为 `seq_date DATE` + `current_val`，删除 `seq_type`。
9. 类型/长度/索引按审查清单对齐（`DECIMAL(14,2)/(12,3)`、区划 `VARCHAR(12)`、`INT UNSIGNED`、复合 `idx_dest` 等）。

## 3. 关键索引

- `biz_waybill`：`uk_waybill_no`、`uk_parent_child(parent_id,child_seq)`、`uk_pre_order_no`、`idx_dept_time`、`idx_child_deleted_time(is_child,deleted,create_time)`、`idx_dest(省,市,区县)`、`idx_origin_hub`、`idx_dest_hub`。
- `biz_waybill_route_node`：`uk_waybill_seq(waybill_id,seq)`、`idx_route(route_scope,route_id)`、`idx_dept`。
- `biz_pre_waybill`：`uk_pre_order_no`、`idx_status_submit`、`idx_submit_time`、`idx_dept_status`、`idx_dest`。
- `biz_waybill_log`：`idx_waybill(waybill_id,create_time)`、`idx_waybill_no`、`idx_action(action_type,create_time)`、`idx_dept`。
- 两套序列表：`seq_date` 主键。

## 4. 验证结果（临时空库，MySQL 8.0.46）

| 检查 | 结果 |
|---|---|
| 7 张表建表 | 通过 |
| 总列数 | 167（77/23/12/3/3/38/11） |
| `COMMENT` 为空 | 0 |
| `FLOAT/DOUBLE` | 0 |
| 字符集 | utf8mb4 / utf8mb4_0900_ai_ci |
| 物理外键 | 0 |
| 枚举抽查 | `status=20`、`biz_type=2`、`pay_method NULL`、`route_scope` 语义、`action_type=5` 项均正确 |

## 5. 执行前置

1. 独立复审通过 → 家伟确认 → 团队 DB 变更流程（评审、开发库 dry-run、备份、执行、校验、回滚脚本）。
2. `biz_waybill_no_seq`、`biz_pre_waybill_no_seq` 加入 `DataScopeTables.EXEMPT_TABLES`。
3. Q25/Q26 若后续拍板影响字段/索引，先改 DDL 再执行。

## 6. 配套文件

- 码值对照：`waybill-code-value-mapping-v1.1.md`
- 审查销项：`waybill-ddl-v1.1-review-closure.md`
- 58 项对照：`waybill-field-mapping-v1.1.md`
- 方案：`../operation-module-design-v1.13.md`
