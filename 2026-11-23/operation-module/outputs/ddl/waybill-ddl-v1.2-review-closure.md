# 复审销项表 · 运单模块 DDL v1.2

> 复审结论：**通过，无阻断**；两条必须改（R1/R2）+ 若干建议（R3/S1/S2/S3/S6）本轮收尾。
> 对应方案：`operation-module-design-v1.14.md`
> 被审/交付 DDL：`waybill-ddl-v1.2-pending-review.sql`；正式落地名 `V13__operation_waybill.sql`（内容一致，`diff` 通过）
> 回滚脚本：`V13__operation_waybill_rollback.sql`
> 状态：**待家伟最终确认后执行；本轮未连接 yunshu、未对真实库执行 DDL**

## 1. 必须改

| ID | 复审要求 | 处理结果 | 证据 |
|---|---|---|---|
| R1 | 必填数值字段去掉 `DEFAULT 0`：运单 `quantity/weight/volume`、预开单 `quantity/weight`；预开单 `volume` 保留默认 0；方案与服务层校验对齐 | **已改**：运单三项、预开单 `quantity/weight` 均为 `NOT NULL` 无默认；预开单 `volume` 保留 `DEFAULT 0.000`。方案 4.1 #21-23、4.4 #15-17、5.4 第 8 条、7.1、7.5 同步 | DDL L64-66、L224-226；方案 4.1、4.4、5.4、7.1、7.5 |
| R2 | 日志表补契约：固定 JSON 结构、原因必填规则、快照必填、按子单反查口径 | **已改**：`before/after_snapshot` 改 `JSON NOT NULL`，注释写明 `waybill{id,no,status,parent_id,parent_no}` + `children[{id,no,seq,status,quantity,weight,volume}]`；**3/4/5 必填 reason，1/2 可空**；五类动作均写快照；日志统一挂母单，**本期只按母单查**，不提供子单号反查（限制写入 7.6） | DDL L260-265；方案 7.6 L1581-1648 |

## 2. 一并处理

| ID | 复审要求 | 处理结果 | 证据 |
|---|---|---|---|
| R3 | 承运商统计在范围内，补 `idx_carrier` | **已加** `KEY idx_carrier (carrier_id, create_time)` | DDL L153；方案 7.2 L1397 |
| S1 | `customer_name DEFAULT ''`、`route_node_count DEFAULT 0` 与方案 7.1 不一致 | **方案侧补齐**：7.1 SQL 写明两个默认值，与 DDL 一致 | 方案 7.1；DDL L50、L53 |
| S3 | 随 DDL 交付回滚脚本，方案 17.1 写失败清理顺序与 `flyway repair` | **已交付** `V13__operation_waybill_rollback.sql`（反向 DROP：日志→路由节点→运单→预开单→预开单号序列→运单号序列→客户）；17.1 写 5 步清理流程与 repair | 回滚脚本 L1-42；方案 17.1 L2110-2124 |
| S6 | `biz_pre_waybill.create_by`、`biz_waybill_log.reason` 注释统一表述 | **已统一**：create_by 写明"客户端写入客户账号ID或系统账号，可空；接单/确认揽收不覆盖"；reason 按 R2 规则统一 | DDL L237、L265；方案 7.5、7.6 |
| S2 | 日志权限先不扩范围，标注待定项 | **未扩范围**：7.7 数据权限不变（按操作机构 `dept_id` 隔离）；方案新增 **Q30 待定**"分公司是否需要看到总部对本公司运单的操作记录" | 方案 7.6 设计理由、3.4.9 Q30、12.4 Q30 |

## 3. 验收结果

### 3.1 临时空库实建（MySQL 8.0.46，独立 datadir/端口，未连 yunshu）

- **7 张表全部创建成功**。
- 字段总数：**167**

| 表 | 字段数 |
|---|---|
| `biz_waybill` | 77 |
| `biz_waybill_route_node` | 23 |
| `biz_customer` | 12 |
| `biz_waybill_no_seq` | 3 |
| `biz_pre_waybill_no_seq` | 3 |
| `biz_pre_waybill` | 38 |
| `biz_waybill_log` | 11 |

### 3.2 `information_schema` 复核

| 检查项 | 实测 |
|---|---|
| 空注释列 | **0** |
| `FLOAT/DOUBLE` 列 | **0** |
| `biz_waybill.quantity/weight/volume` `COLUMN_DEFAULT` | **NULL / NULL / NULL** |
| `biz_pre_waybill.quantity/weight` | **NULL / NULL** |
| `biz_pre_waybill.volume` | **0.000** |
| `biz_waybill_log.before_snapshot/after_snapshot` | `IS_NULLABLE=NO`，`json` |
| `idx_carrier` | **存在**：`biz_waybill_route_node(carrier_id,create_time)` |
| 物理外键 | 0 |

### 3.3 回滚脚本验证

- 在已建好 7 表的临时库执行回滚脚本；
- `information_schema.tables` 计数 = **0**，7 张表全部 DROP 干净；
- DROP 顺序按依赖反向，重复执行安全（`IF EXISTS`）。

### 3.4 索引清单（7 张表）

| 表 | 索引 |
|---|---|
| `biz_waybill` | `PRIMARY(id)`、`uk_waybill_no`、`uk_parent_child(parent_id,child_seq)`、`uk_pre_order_no`、`idx_dept_time(dept_id,create_time)`、`idx_child_deleted_time(is_child,deleted,create_time)`、`idx_dest(省,市,区县)`、`idx_origin_hub`、`idx_dest_hub`、`idx_status`、`idx_pay_method`、`idx_sales`、`idx_customer`、`idx_customer_name`、`idx_sender_phone`、`idx_receiver_phone`、`idx_related_no`、`idx_create_by`、`idx_create_time`、`idx_parent_no` |
| `biz_waybill_route_node` | `PRIMARY(id)`、`uk_waybill_seq(waybill_id,seq)`、`idx_route(route_scope,route_id)`、`idx_carrier(carrier_id,create_time)`、`idx_dept` |
| `biz_customer` | `PRIMARY(id)`、`idx_name`、`idx_phone`、`idx_dept`、`idx_status` |
| `biz_pre_waybill` | `PRIMARY(id)`、`uk_pre_order_no`、`idx_status_submit(status,submit_time)`、`idx_submit_time`、`idx_dept_status`、`idx_customer`、`idx_customer_name`、`idx_accept_by`、`idx_dest(省,市,区县)`、`idx_expect_pickup`、`idx_waybill_no` |
| `biz_waybill_log` | `PRIMARY(id)`、`idx_waybill(waybill_id,create_time)`、`idx_waybill_no`、`idx_action(action_type,create_time)`、`idx_dept` |
| `biz_waybill_no_seq` | `PRIMARY(seq_date)` |
| `biz_pre_waybill_no_seq` | `PRIMARY(seq_date)` |

## 4. 交付清单

| 文件 | 说明 |
|---|---|
| `waybill-ddl-v1.2-pending-review.sql` | 待审定稿 DDL（7 表/167 列） |
| `V13__operation_waybill.sql` | 正式落地名，与 v1.2 `diff` 一致 |
| `V13__operation_waybill_rollback.sql` | 反向 DROP 回滚脚本 |
| `waybill-code-value-mapping-v1.2.md` | 默认值/日志契约/索引对照 |
| `operation-module-design-v1.14.md` | 方案 v1.14（新建不覆盖 v1.13） |
| 本文件 | 复审六项销项与验收证据 |

## 5. 边界声明

- 未连接、未修改 `yunshu` 库；未对真实库执行任何 DDL。
- 未写业务代码、未提交 Git、未改动其他模块。
- v1.0/v1.1 DDL、v1.12/v1.13 文档均保留，未覆盖。
