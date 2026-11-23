# 运单相关建表 DDL · 一页纸审阅摘要（v1.0 待审）

> 对应方案：`operation-module-design-v1.12.md`（v1.12 定稿）
> DDL：`waybill-ddl-v1.0-pending-review.sql`
> 字段对照：`waybill-field-mapping-v1.0.md`
> **状态：待家伟审阅；本轮未执行、未连 yunshu 库、未改任何现有结构。**

## 1. 版本与执行

| 项 | 结论 |
|---|---|
| 实际最新 Flyway | `V12__backfill_menu_button_audit.sql` |
| 本文件接续版本 | **V13**（正式落库文件名建议 `V13__operation_waybill.sql`） |
| 是否冲突 | 不冲突；`db/migration/` 下无 V13 文件 |
| 本轮动作 | 只出 SQL 待审稿；不执行、不连库、不改表 |

## 2. 六张表与字段数

| 表 | 物理字段 | 说明 | 方案章节 |
|---|---:|---|---|
| `biz_waybill` | **77** | 运单主表，母单/子单同表；**58 项逻辑字段**（6 项按编码+名称/ID+名称成对落列，故 64 个物理列承载 58 项，其余 13 个为技术/派生列） | 7.1、4.1 |
| `biz_waybill_route_node` | **14** | 运单路由节点快照，支撑详情时间轴 | 7.2 |
| `biz_customer` | **13** | 客户主数据（简易可用版） | 7.3 |
| `biz_waybill_no_seq` | **5** | 运单号按天序列 | 7.4 |
| `biz_pre_waybill_no_seq` | **4** | 预开单号按天序列 | 7.4 |
| `biz_pre_waybill` | **38** | 预开单（客户申报），接单后生成运单 | 7.5、4.4 |
| **合计** | **151** | 不含操作日志表；Q28 强制签收写平台操作日志，不新增表 | — |

## 3. 关键索引

- `biz_waybill`：`uk_waybill_no(waybill_no)`、`uk_parent_child(parent_id,child_seq)`、`uk_pre_order_no(pre_order_no)`；`idx_parent_no`、`idx_dept_time(dept_id,create_time)`、`idx_dest(dest_district_code)`、`idx_origin_hub(origin_hub_id)`、`idx_dest_hub(dest_hub_id)`、`idx_status`、`idx_pay_method`、`idx_create_time`、客户/发货电话/收货电话/业务员等。
- `biz_waybill_route_node`：`uk_waybill_seq(waybill_id,seq)`、`idx_node(node_id,node_type)`、`idx_waybill_no`、`idx_dept`。
- `biz_customer`：`idx_name_phone(customer_name,contact_phone)`、`idx_phone`、`idx_dept`、`idx_status`。
- `biz_waybill_no_seq`：`uk_date_type(seq_date,seq_type)`。
- `biz_pre_waybill_no_seq`：`uk_seq_date(seq_date)`。
- `biz_pre_waybill`：`uk_pre_order_no(pre_order_no)`、`idx_status_submit(status,submit_time)`、`idx_dept_status(dept_id,status)`、`idx_dest(dest_district_code)`、`idx_expect_pickup`、`idx_accept_by`、`idx_waybill_no`。

## 4. 本轮新增字段

| 字段 | 类型 | 说明 |
|---|---|---|
| `origin_hub_id` | BIGINT UNSIGNED | 始发网点ID，只读派生（Q29） |
| `origin_hub_name` | VARCHAR(100) | 始发网点名称快照 |
| `dest_hub_id` | BIGINT UNSIGNED | 运达网点ID，只读派生（Q29） |
| `dest_hub_name` | VARCHAR(100) | 运达网点名称快照 |

> 网点 2 项逻辑字段、4 个物理列；字段总数 56→58 的口径见方案 4.1 #57/#58。Q29 已定：不开放人工修改，DDL 注释中已移除改派/调整原因/留痕表述。

## 5. 与 v1.12 文档对应

| 内容 | 章节 |
|---|---|
| 58 项字段清单与列名 | 4.1（对照文件逐项列出 #1–#58） |
| 预开单 26 项字段 | 4.4（DDL 落 38 个物理列：编码+名称、接单人ID+姓名、审计成对） |
| 运单主表 DDL 口径 | 7.1 |
| 路由节点表 | 7.2 |
| 客户表 | 7.3 |
| 两套序列 | 7.4 |
| 预开单表 | 7.5 |
| 网点只读与子母单差异 | 3.5（Q29）、3.4.7 |
| 状态机与强制签收 | 9.3（Q28） |

## 6. 已做验证（临时空库，不碰 yunshu）

| 项 | 结果 |
|---|---|
| MySQL 语法/建表 | MySQL **8.0.46** 临时空库 `ddl_check` 执行通过，6 张表全部创建成功 |
| 字段 COMMENT 为空 | **0**（`information_schema.columns` 统计） |
| FLOAT/DOUBLE | **0**（金额全部 DECIMAL，费用类为 `DECIMAL(12,2)`，重量/体积 `DECIMAL(10,3)` 或 `DECIMAL(12,3)`） |
| 58 项对照 | 文档 4.1 共 **58** 项，全部能在 `biz_waybill` 找到对应列；无缺失、无多余映射 |
| Flyway | 最新 V12，本文件接续 **V13**，无同名冲突 |
| 物理外键 | 未建（与应用层一致） |

复现（临时空库，不连 yunshu）：

```sql
-- 在临时库 ddl_check 执行本 DDL 后
SELECT COUNT(*) FROM information_schema.columns
 WHERE table_schema = 'ddl_check' AND column_comment = '';      -- 期望 0
SELECT COUNT(*) FROM information_schema.columns
 WHERE table_schema = 'ddl_check' AND data_type IN ('float','double'); -- 期望 0
```

## 7. 请家伟确认

1. V13 版本号与正式文件名 `V13__operation_waybill.sql` 是否认可；
2. 六张表范围是否齐全（操作日志复用平台能力，不单建表）；
3. `biz_pre_waybill` 以 4.4/7.5 为准落 38 列（含 `dept_id/dept_name`、`accept_by_name`、`submit_time`，不含货物类型/包装类型/预开单备注）；
4. 确认后我再移交开发按 Flyway 执行；本轮不执行。
