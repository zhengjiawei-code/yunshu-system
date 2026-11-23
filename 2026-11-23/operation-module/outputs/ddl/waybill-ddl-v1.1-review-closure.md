# 审查问题销项表 · 运单模块 DDL v1.1

> 被审对象：`waybill-ddl-v1.0-pending-review.sql`（6 表/151 列）
> 回改产出：`waybill-ddl-v1.1-pending-review.sql`（7 表/167 列，Flyway V13，**未执行**）
> 字典来源：`operation-module-design-v1.13.md`（v1.12 为基线，v1.13 为回改后版本）
> 结论：审查报告的 **6 条阻断 + 1 条序列表问题** 全部已改；**9 条回改要求**逐条落地；方案自身 **5 处矛盾**已修正并记录。空库实建 7 表通过，`COMMENT` 为空 **0**，`FLOAT/DOUBLE` **0**。建议进入独立复审，复审通过后再由家伟确认动库。

---

## 1. 阻断级问题销项

| ID | 审查问题 | 处理结果 | 证据（DDL v1.1 行 / 方案 v1.13 行） |
|---|---|---|---|
| B1 | `biz_waybill.biz_type` 码值反了：DDL 1=零担/2=整车，方案 1=整车/2=零担、默认 2 | **已改**：`1=整车，2=零担`，`NOT NULL DEFAULT 2`，注释同步 | DDL L36；方案 4.1 #8 L753、7.1 L1164-1165 |
| B2 | `pay_method` 的 `5` 写成"其他"，方案是"多笔付"；`4` 应为"回付" | **已改**：`1=现付，2=到付，3=月结，4=回付，5=多笔付`，无默认、未选拦截 | DDL L67；方案 4.1 #25 L770、5.2 L910-942 |
| B3 | `status` 默认值 10 应为 20；`30/40/50` 语义错位（应 30=已装车、40=运输中、50=已到达） | **已改**：默认 **20**；`10 待揽收 / 20 已揽收 / 25 已拆单 / 30 已装车 / 40 运输中 / 50 已到达 / 55 部分到达 / 60 已签收 / 70 异常 / 90 已作废` | DDL L31；方案 9.1 L1712-1725、7.1 L1154-1155 |
| B4 | 派生金额注释写成"手填" | **已改**：`total_fee`、`rebate_total`、`valid_freight`、四项支付金额全部写明"服务端计算并强制覆盖，客户端传值无效"；`valid_freight=total_fee−rebate_total` 可为负；`cash_back/owe_back` ≥0 | DDL L76-84；方案 5.1 L883-908、5.2 L910-942、5.4 L944-960、7.1 L1244-1261 |
| B5 | `biz_waybill_route_node` 与方案 7.2 完全不同，存不了 `route_id/route_code`、库区、承运商、`from/to_dept`、`dest_region_code`，且 `route_scope` 语义被改 | **已重写为 23 列**：`id/waybill_id/seq/route_scope/route_id/route_code/route_category/transfer_type/from_dept_id/from_dept_name/to_dept_id/to_dept_name/dest_region_code/dest_region_name/carrier_id/carrier_name/warehouse_area/mileage/transit_hours/dept_id/create_by/create_time/deleted`；`route_scope` 恢复 `1=短驳/干线、2=外转/直达` | DDL L125-153；方案 7.2 L1333-1389 |
| B6 | 缺审计落点（拆合撤、作废、强制签收原因无表可落） | **已新增第 7 张表 `biz_waybill_log`**：`waybill_id/waybill_no/action_type/before_snapshot/after_snapshot/reason/operator_id/operator_name/dept_id/create_time`；`action_type` 1拆单/2合单/3撤销拆单/4作废/5强制签收；原因不写 `biz_waybill.remark` | DDL L256-273、L93；方案 7.6 L1572-1611、4.1 #50 L795、9.2 L1750 |
| B7 | 两张序列表字段名/类型与方案 8.2 取号 SQL 不一致（应 `seq_date DATE` + `current_val`，删除 `seq_type`，子单序号在拆单事务内 `MAX(child_seq)+1`） | **已改**：两表均为 `seq_date DATE` 主键 + `current_val INT UNSIGNED` + `update_time`，**无 `seq_type`**；子单序号不建独立序列，拆单事务内 `MAX(child_seq)+1` | DDL L181-196；方案 8.2 L1656-1683、7.4 L1436-1466 |

---

## 2. 回改要求销项

### 2.1 要求 1：枚举与默认值按方案 4.1 / 5.x / 9.x 回改，并产出码值对照表

- **已改**：15 组枚举、全部默认值逐项对齐；产出租出 `waybill-code-value-mapping-v1.1.md`（字段、DDL 新值、方案出处、DDL 行号）。
- **证据**：码值对照表第 1-4 节；DDL L29-31、L36、L50、L67、L85、L90、L92、L129-133、L163、L204、L260。

### 2.2 要求 2：派生金额注释改为服务端计算并强制覆盖，写明公式与可负/≥0

- **已改**：`total_fee`（ΣF，不含声明价值）、`rebate_total`（现返+欠返，≥0）、`valid_freight`（可为负）、四项支付金额（按支付方式服务端计算；多笔付读客户端分摊但校验四项合计=total_fee 后覆盖）；`cash_back/owe_back` ≥0。
- **证据**：DDL L76-84；码值对照表第 5 节；方案 5.1/5.2/5.4。

### 2.3 要求 3：路由节点表采用方案 7.2 分段快照结构

- **已改**：23 列，含来源路由 `route_id/route_code`、`route_category`（1短驳/2干线）、`transfer_type`（1直达/2外转）、`from_dept_id/name`、`to_dept_id/name`、`dest_region_code/name`、`carrier_id/name`、`warehouse_area`、`mileage/transit_hours`、`dept_id`、`create_by`、`deleted`。
- **索引**：`uk_waybill_seq(waybill_id,seq)`、`idx_route(route_scope,route_id)`、`idx_dept(dept_id)`；**无 `idx_waybill_no`**。
- **证据**：DDL L125-153；方案 7.2 L1333-1389。

### 2.4 要求 4：新增 `biz_waybill_log`

- **已改**：11 列，字段与要求逐项一致；每字段中文注释；动作枚举逐项列出；拆/合/撤/作废/强制签收原因统一入此表；`biz_waybill.remark` 注释明确"不写此处"。
- **方案同步**：7.6 完整 DDL 与设计理由；7.7 逻辑外键补 3 行；10.1 数据权限补日志表行；Q28 三处、9.2、3.4.5 的"平台操作日志"全部改为 `biz_waybill_log`。
- **证据**：DDL L256-273；方案 7.6 L1572-1611、7.7 L1626-1628、10.1 L1812、3.4.5 L622、Q28 L672/L1961、9.2 L1750。

### 2.5 要求 5：客户表按简易口径收紧

| 审查要求 | 处理 | 证据 |
|---|---|---|
| 删除 `settle_type`、`credit_limit` | 已删除，表仅 12 列 | DDL L158-176 |
| 补 `create_by/update_by` | 已补，均可空 | DDL L166、L168 |
| `remark` 500 | `VARCHAR(500)` | DDL L164 |
| `customer_name` 注释改为"同机构内应用层校验，不建全局唯一索引" | 已改；仅普通索引 `idx_name` | DDL L160、L172 |
| `contact_phone` 可空 | `DEFAULT NULL` | DDL L161 |

### 2.6 要求 6：类型 / 长度 / 索引 / 默认值对齐

| 审查项 | DDL v1.1 | 证据 |
|---|---|---|
| `declared_value` 14,2 | `DECIMAL(14,2)` | L75 |
| `weight` / `volume` 12,3 | `DECIMAL(12,3)` | L64-65 |
| `related_no` 64 | `VARCHAR(64)` | L25 |
| `dest_name` 200 | `VARCHAR(200)` | L43 |
| `goods_remark` 500 | `VARCHAR(500)` | L66 |
| `dest_*_name` 64 | `VARCHAR(64)` | L38/40/42 |
| 区划编码统一 12 | `VARCHAR(12)` | L37/39/41、L138、L214-219 |
| `quantity` / `receipt_count` → `INT UNSIGNED` | 均已改 | L63、L91 |
| `child_seq` → `SMALLINT UNSIGNED` | 已改 | L30 |
| `create_by` 可空 | `DEFAULT NULL`（运单/客户/预开单/路由节点） | L94、L166、L235、L146 |
| `idx_dest` 改复合索引 | `(dest_province_code,dest_city_code,dest_district_code)` | L113 |
| 运单列表补 `(is_child,deleted,create_time)` | `idx_child_deleted_time` | L108 |
| 预开单补 `idx_submit_time` | 已补 | L243 |
| 删除路由节点冗余 `idx_waybill_no` | 已删除 | L149-152 |

### 2.7 要求 7：方案自身矛盾修正并记录

| ID | 矛盾 | 处理 | 证据 |
|---|---|---|---|
| C1 | 客户电话必填 vs 选填 | 统一为**可空**：客户表 `contact_phone DEFAULT NULL`；运单/预开单客户电话可空；方案 4.4 #4 已为"否（可空）" | DDL L161、L53、L207；方案 4.4 #4 L855、7.3 L1399 |
| C2 | 节点表命名不统一 | 全文统一为 `biz_waybill_route_node`；已清除 `biz_waybill_node` 残留（3.4.5 一处） | 方案 3.4.5 L623、7.2 L1333 |
| C3 | 方案 17.2 "四个 V13" | 改为**单 V13 合并** `V13__operation_waybill.sql`；如需拆分按 **V13–V16 顺序递增**（客户/预开单/运单/节点+日志） | 方案 17.2 L2080-2089 |
| C4 | 区划编码长度 | 全部统一 `VARCHAR(12)`，清除 `CHAR(6)` | 方案 4.1 #4-#6、4.4 #12、7.1/7.2/7.5；DDL L37-41、L138 |
| C5 | 运达网点语义 | `dest_hub` 取路由终点网点；**外转/直达终点为区划、无归属网点时允许为空**，列表显示 `—` | 方案 4.1 #58 L803、3.5.1 L686、7.1 L1184-1187；DDL L46-47 |
| C6（附带） | 隐藏列数 37 与 56−18 不符 | 统一修正为 **38**（列池 56、默认 18）；方案 3.2.2、3.5.3、4.2、N6、原型页头同步 | 方案 L427、L704、L832、L1979；原型 `index.html` L362 |
| C7（附带） | 9.2 仍建议 `void_reason` 或写 `remark` | 改为统一写 `biz_waybill_log`（`action_type=4`），不加字段、不写备注 | 方案 9.2 L1750 |

### 2.8 要求 8：数据权限豁免

- **已写入方案 17 章前置事项**：`biz_waybill_no_seq`、`biz_pre_waybill_no_seq` 无 `dept_id`，必须加入 `DataScopeTables.EXEMPT_TABLES`，并在 `data-permission-spec.md` 第 2 节登记理由。
- **证据**：方案 17.1 L2077、10.1 L1813-1815；DDL 两表表注释 L186、L196。

### 2.9 要求 9：命名与交付

| 要求 | 处理 |
|---|---|
| 修订版 DDL 另起新文件，不覆盖 v1.0 | `waybill-ddl-v1.1-pending-review.sql`（v1.0 保留） |
| 正式落地文件名 | `V13__operation_waybill.sql`（写入 DDL 头部 L8） |
| 文档升 v1.13，新建不覆盖 v1.12 | `operation-module-design-v1.13.md`（v1.12 保留） |
| 码值对照表 | `waybill-code-value-mapping-v1.1.md` |
| 审查问题销项表 | 本文件 |
| 58 项对照（含行号） | `waybill-field-mapping-v1.1.md` |
| 一页纸摘要 | `waybill-ddl-v1.1-review-summary.md` |

---

## 3. 明确不改项与理由

| ID | 事项 | 结论 | 理由 |
|---|---|---|---|
| N1 | 不建物理外键 | 保持 | 与现有 `sys_*` 表及全库约定一致；逻辑删除/数据权限下物理 FK 阻碍运维；由服务层做引用校验（方案 7.7 L1634） |
| N2 | 序列表不设自增 `id` | 保持 | 方案 8.2 取号 SQL 以 `seq_date` 为主键执行 `INSERT ... ON DUPLICATE KEY UPDATE`；加 `id` 会引入无意义第二唯一键，且不改善并发安全 |
| N3 | `biz_waybill_log` 无 `deleted/update_time` | 保持 | 审计日志只追加、不修改、不删除；保留历史是审计要求，故不设逻辑删除与更新时间 |
| N4 | 预开单 `volume` DDL 为 `NOT NULL DEFAULT 0.000`，方案 4.4 标"非必填" | 不冲突，保持 | 客户端可不申报体积，落库默认 0；"非必填"约束在应用层，DDL 不加 `CHECK`，避免客户端提交失败 |
| N5 | 运单/预开单均保留 `uk_pre_order_no`（预开单另有 `uk_pre_order_no`） | 保持 | 运单侧防止一张预开单重复生成运单；预开单侧保证单号唯一。两者不是重复索引 |
| N6 | `route_node.route_code` 为 `NOT NULL` | 保持 | 短驳/干线取路由编码，外转/直达取"区划编码_ZT/_WZ"，均可生成；置空会让打印/对账缺关键字段 |
| N7 | 预开单保留 `idx_waybill_no` | 保持 | 接单/确认揽收后按运单号反查预开单，是真实查询路径；审查仅要求删除**路由节点表**的 `idx_waybill_no` |
| N8 | `biz_waybill` 77 物理列 vs 58 逻辑字段 | 不是缺陷 | 58 项中 6 项按"编码+名称/ID+名称"成对落列（64 列），另 13 个技术/派生列；逐项见 `waybill-field-mapping-v1.1.md` |

---

## 4. 验收结果

### 4.1 临时空库实际建表

- 环境：本机 MySQL 8.0.46，临时数据目录 `/tmp/ddlcheck_v11_final`，独立 socket/端口 3399；**未连接 yunshu 库**。
- 结果：**7 张表全部创建成功**。
- 各表列数：
  - `biz_waybill` 77
  - `biz_waybill_route_node` 23
  - `biz_customer` 12
  - `biz_waybill_no_seq` 3
  - `biz_pre_waybill_no_seq` 3
  - `biz_pre_waybill` 38
  - `biz_waybill_log` 11
  - **合计 167 列**

### 4.2 `information_schema` 复核

| 检查项 | 结果 |
|---|---|
| `column_comment` 为空或 NULL 的列 | **0** |
| `data_type IN ('float','double')` 的列 | **0** |
| 枚举注释与码值对照表逐项一致 | **一致**（`status=20`、`biz_type=2`、`pay_method` 无默认、`route_scope` 1短驳/干线 2外转/直达、`action_type` 1-5） |
| 字符集/排序规则 | `utf8mb4 / utf8mb4_0900_ai_ci` |
| 物理外键 | 0 |

### 4.3 审查问题销项

- 阻断级 7 条（含序列表）→ 全部已改，见第 1 节。
- 回改要求 9 条 → 全部落地，见第 2 节。
- 明确不改 8 条 → 均有理由，见第 3 节。

### 4.4 方案 ↔ DDL 一致性再核（逐项标注出处）

| 核对项 | 方案 v1.13 出处 | DDL v1.1 出处 | 结果 |
|---|---|---|---|
| 58 项字段逐项落列 | 4.1 L744-803 | `biz_waybill` L23-99；逐项见 `waybill-field-mapping-v1.1.md` | 一致 |
| 派生金额公式与覆盖规则 | 5.1 L883-908、5.2 L910-942、5.4 L944-960 | L76-84 | 一致 |
| 状态机码值与初始状态 | 9.1 L1712-1725、9.2 L1727-1751 | L31 | 一致 |
| 路由节点分段快照结构 | 7.2 L1333-1389 | L125-153 | 一致 |
| 取号 SQL 与序列表 | 8.2 L1656-1683、7.4 L1436-1466 | L181-196 | 一致 |
| 客户表简易口径 | 7.3 L1391-1434 | L158-176 | 一致 |
| 预开单字段与索引 | 7.5 L1468-1571 | L201-251 | 一致 |
| 操作日志表与动作枚举 | 7.6 L1572-1611 | L256-273 | 一致 |
| 数据权限与豁免 | 10.1 L1804-1815、17.1 L2071-2078 | 表注释 L186/L196 | 一致 |
| 列池 56 / 默认 18 / 隐藏 38 | 3.2.2 L427、3.5.3 L704、4.2 L832、N6 L1979 | 不涉及 | 一致（原型 L362 已同步） |
| Flyway 接续 | 17.2 L2080-2089 | 头部 L6-8 | V12 之后 V13 |
| 正式文件名 | 17.2 L2083 | L8 | `V13__operation_waybill.sql` |

### 4.5 边界声明

- 未连接、未修改 `yunshu` 库；未对任何真实库执行 DDL。
- 未写业务代码、未建表（仅临时空库验证）、未提交 Git、未修改其他模块。
- v1.0 DDL、v1.12 文档、v1.0 摘要/映射均保留，未覆盖。

---

## 5. 待复审确认点

1. B6 `biz_waybill_log` 为家伟已同意方向；若复审或家伟否决，需回退为其他审计落点（当前按同意执行）。
2. 预开单 `volume` 的"非必填"与 DDL `NOT NULL DEFAULT 0` 的取舍（见 N4）。
3. 17.2 拆分方案采用 V13–V16；如团队坚持单脚本，直接用 V13 合并版即可。
4. `route_node` 是否需要在 `dept_id` 之外再加 `(waybill_id,dept_id)` 复合索引，取决于后续轨迹查询 SQL；当前 `uk_waybill_seq` 已覆盖按运单查节点的主路径。
