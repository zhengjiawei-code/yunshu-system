# 装车配载 v1.3 · 数据库迁移（V27–V34）DDL 清单与风险评估

> 状态：**已于 2026-11-25 21:25 执行完成（V27–V34 全部 success=1），服务已用新 jar 启动并监听 8090**
> 批准口径：① V27 含存量回填；② 菜单方案 A（新增 1302，停用 1401）；③ 费用种子按 4.1
> 执行记录见文末第 11 节
> 依据：`outputs/transport-loading-design-v1.3.md` 第 3 章（表设计）、第 4 章（费用/毛利）、第 12 章（迁移清单）、附录 A
> 目标库：`yunshu`，MySQL **8.0.46**，字符集 `utf8mb4` / `utf8mb4_0900_ai_ci`
> 当前基线（已连库核对）：Flyway 最新 **V26**；`biz_waybill` 5 行、`sys_route` 8 行、`sys_menu` 62 行、`sys_dept` 11 行；`sys_config` 不存在；`biz_waybill` 无 `current_hub_id/current_hub_name/current_node_seq/inventory_time/load_status` 五列，也无 `idx_current_hub`、`idx_inventory_time`。
> 回滚脚本目录：`outputs/ddl-v1.3/rollback/`（**不在 classpath，不会被 Flyway 扫描**）

---

## 0. 先看结论

1. **共 8 个版本 V27–V34**：2 张现有表加列/加索引（`biz_waybill`、`sys_route`），新建 **11 张表**，种子 2 组（费用类型 16 项、`sys_config` 2 键）+ 菜单/按钮权限。
2. 现网数据量极小（现有业务表最大 62 行），**加列全部 INSTANT、加索引 INPLACE/LOCK=NONE，实测窗口 < 1 秒**；但按生产增长预估已给出公式与低峰执行要求（见第 5 节）。
3. **迁移前必须先拍板 3 件事**（否则跑完会出现空池/双入口/口径分叉）：
   - **P0-1 存量运单回填**：5 张状态 20 的运单若不回填 `current_hub_id`，上线后待配载池为空。建议 V27 同步执行幂等 UPDATE（见 3.1.3）。
   - **P0-2 菜单重复**：库里**已存在** `sys_menu.id=1401「到货卸车」`（父级 1004 到货管理，`/arrival/unload`，无前端页面）。v1.3 又要新增「运输管理 → 到站卸车」，会产生双入口。两个选项见 4.3，需家伟选 A 或 B。
   - **P0-3 费用种子口径冲突**：4.1 写「16 项 = 14 成本 + 现返/欠返」，6.8 写「16 项 = 14 成本 + 1 代收 + 1 代付」。本清单默认按 **4.1** 落种子，见 4.1。
4. 其余文档内部不一致（不阻塞 DDL，但开发前要改文档）：
   - 6.11 公式示例用 `FEE0001`，4.1 现付运输费是 `FEE0010`；
   - 6.11 候选成本项写「`profit_enabled=1`」，会把已停用的 FEE0021–0023 带入，建议改为「`enabled=1 且 profit_enabled=1`」；
   - `FEE0019/0020` 的 `source_field` 若写成 `biz_trip.*`，会被 4.1「只渲染 `biz_trip.` 开头」的规则误渲染进车次费用区，建议种子写 `biz_trip_dest.*`（本清单已按此落）。
5. **执行方式**：批准后由我写 V27–V34 到 `backend/src/main/resources/db/migration/`，**随应用启动由 Flyway 执行**，禁止手工连库跑；执行前先 `mysqldump` 全库备份。

---

## 1. 环境与基线核对

| 项 | 实测值 | 说明 |
|---|---|---|
| MySQL | 8.0.46 (macOS) | 支持 INSTANT ADD COLUMN、INPLACE/LOCK=NONE 加索引 |
| Flyway 最新 | V26 `waybill amend nullable` | 新脚本从 V27 接续 |
| 库大小 | 业务表均 ≤ 62 行 | 当前迁移几乎无锁风险 |
| `sys_config` | **不存在** | V33 新建 |
| `sys_vehicle` / `sys_driver` | 不存在 | V32 新建（方案 3.10 命名，不是 `biz_vehicle`/`biz_driver`） |
| `biz_waybill` 现有索引 | 含 `idx_parent_no`、`uk_parent_child(parent_id, child_seq)` | V27 不重复建 `idx_parent_no` |
| `sys_route.volume_weight_ratio` | 不存在 | V33 新增 |
| 数据权限豁免 | `DataScopeTables.EXEMPT_TABLES` 当前未含新表 | 迁移后必须同步代码，否则 `sys_config` 无 `dept_id` 会直接 SQL 报错（见第 7 节） |

---

## 2. 版本与执行顺序

| 顺序 | 版本 | 内容 | 依赖 |
|---|---|---|---|
| 1 | V27 | `biz_waybill` 加 5 列 + 3 索引 + 存量回填 | 无 |
| 2 | V28 | 新建 `biz_trip`、`biz_trip_dest`、`biz_trip_no_seq` | V27（配载状态列） |
| 3 | V29 | 新建 `biz_trip_waybill`、`biz_trip_waybill_fee` | V28 |
| 4 | V30 | 新建 `biz_trip_fee`、`biz_trip_log` | V28 |
| 5 | V31 | 新建 `sys_fee_type` + 16 项种子 | 无（建议在 V30 之后） |
| 6 | V32 | 新建 `sys_vehicle`、`sys_driver` | 无 |
| 7 | V33 | 新建 `sys_config` + 2 键种子；`sys_route` 加 `volume_weight_ratio` | 无 |
| 8 | V34 | 菜单 / 按钮权限种子 | V31、V33（按钮对应页面） |

> V28–V30 同批上线，避免「表在、唯一键口径不一致」；V31/V33 的种子必须先于后端接口联调。

---

## 3. 正向 DDL（全部为待审稿，未执行）

### 3.1 V27 · `biz_waybill` 加列 + 索引 + 存量回填

#### 3.1.1 加列（一条 ALTER，INSTANT）

```sql
SET NAMES utf8mb4;

ALTER TABLE `biz_waybill`
  ADD COLUMN `current_hub_id` BIGINT UNSIGNED NULL DEFAULT NULL
    COMMENT '当前库存网点ID：引用 sys_dept.id；开单时=始发网点，卸车后=到达网点；待配载池按「当前库存网点=车次始发网点」匹配',
  ADD COLUMN `current_hub_name` VARCHAR(50) NULL DEFAULT NULL
    COMMENT '当前库存网点名称快照：与 current_hub_id 同步写入，机构改名不影响历史展示',
  ADD COLUMN `current_node_seq` TINYINT UNSIGNED NOT NULL DEFAULT 0
    COMMENT '当前已完成路由节点段序号：0=仍在始发网点未发出，N=已完成第 N 段并落在该段到达网点；下一节点取 seq=N+1',
  ADD COLUMN `inventory_time` DATETIME NULL DEFAULT NULL
    COMMENT '库存落入当前网点时间：揽收完成或卸车完成时间；待配载池按此升序（FIFO）',
  ADD COLUMN `load_status` TINYINT UNSIGNED NOT NULL DEFAULT 0
    COMMENT '干线配载状态：0=待配载（含分拨中心卸车后回池），1=已配载(70)，2=已发车(80)，3=已到站(50/90)，4=已卸车终态(仅末端100/110/120)；由车次操作维护，用于池过滤与防重复配载',
  ALGORITHM=INSTANT;
```

#### 3.1.2 索引（3 条，INPLACE / LOCK=NONE）

```sql
ALTER TABLE `biz_waybill`
  ADD KEY `idx_current_hub` (`current_hub_id`, `load_status`, `status`),
  ALGORITHM=INPLACE, LOCK=NONE;

ALTER TABLE `biz_waybill`
  ADD KEY `idx_inventory_time` (`inventory_time`),
  ALGORITHM=INPLACE, LOCK=NONE;

-- 说明：现有 uk_parent_child(parent_id, child_seq) 已能命中 parent_id 前缀，
-- 本索引对「母单余额汇总」的增益有限；按方案保留，若 DBA 认为冗余可去掉（不影响功能）。
ALTER TABLE `biz_waybill`
  ADD KEY `idx_parent_deleted` (`parent_id`, `deleted`),
  ALGORITHM=INPLACE, LOCK=NONE;
```

#### 3.1.3 存量回填（P0-1，幂等，影响 5 行）

```sql
-- 仅回填「已开单但尚未进入干线配载生命周期」的运单；
-- 已有 current_hub_id 的行不重复更新，可重复执行。
UPDATE `biz_waybill`
SET `current_hub_id` = `origin_hub_id`,
    `current_hub_name` = (SELECT d.dept_name FROM `sys_dept` d WHERE d.id = `origin_hub_id`),
    `current_node_seq` = 0,
    `inventory_time` = `create_time`,
    `load_status` = 0
WHERE `current_hub_id` IS NULL
  AND `status` IN (20, 60)
  AND `deleted` = 0;
```

> 影响面：当前库 5 行（全部 status=20）；生产按同条件统计。执行前先跑 `SELECT COUNT(*)` 记录基数。
> 若家伟不希望回填历史运单，则上线后这 5 票无法进池，只能人工改单触发重算——**不建议**。

### 3.2 V28 · 车次主表 / 子车次 / 车次号流水

#### 3.2.1 `biz_trip` 车次主表

```sql
CREATE TABLE `biz_trip` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键ID，自增',
  `trip_no` VARCHAR(32) NOT NULL COMMENT '车次编号：CT+yyyyMMdd+3位日内流水，如CT20261125001；系统生成、全局唯一、创建后不可改；CT=干线（短驳预留DB）',
  `trip_name` VARCHAR(64) NOT NULL COMMENT '车次名称：手工输入，用于调度识别，允许重复',
  `trip_category` TINYINT UNSIGNED NOT NULL DEFAULT 1 COMMENT '车次类型：1=干线，2=短驳；本期只写1，短驳预留',
  `trip_status` TINYINT UNSIGNED NOT NULL DEFAULT 10 COMMENT '车次状态：10=车次计划，20=装车配载，30=车辆在途，40=车辆部分到站，50=车辆到站，60=完成卸车，90=已关闭',
  `origin_dept_id` BIGINT UNSIGNED NOT NULL COMMENT '始发网点ID：引用sys_dept.id；默认当前登录机构，可调整为有权限网点',
  `origin_dept_name` VARCHAR(50) NOT NULL COMMENT '始发网点名称快照：创建时写入，机构改名不影响历史车次',
  `plan_depart_time` DATETIME NULL DEFAULT NULL COMMENT '计划发车时间：手工录入，作为时效考核基线',
  `depart_time` DATETIME NULL DEFAULT NULL COMMENT '发车时间：点击发车时的服务器时间，实际离开始发网点时间；发车前为空',
  `plan_arrive_time` DATETIME NULL DEFAULT NULL COMMENT '计划到站时间：按线路里程/历史车速/停靠计划预估，可人工修正；无数据时按路由节点时效累加，仍无则为空',
  `actual_arrive_time` DATETIME NULL DEFAULT NULL COMMENT '实际到站时间：主车次取最后一个目的网点到站时间；各网点时间分别记录在子车次',
  `vehicle_id` BIGINT UNSIGNED NULL DEFAULT NULL COMMENT '车辆ID：引用sys_vehicle.id；快速新增时回写',
  `plate_no` VARCHAR(20) NULL DEFAULT NULL COMMENT '车牌号：手工输入或从车辆主数据选择；发车前必填',
  `vehicle_type` VARCHAR(50) NULL DEFAULT NULL COMMENT '车辆型号：选择车牌自动带出；手工录入车牌时手工选择',
  `vehicle_load` DECIMAL(12,3) NULL DEFAULT NULL COMMENT '车辆核定载重(kg)：车辆主数据带出、不可改；手工录车牌时手填；用于配载超重校验',
  `vehicle_volume` DECIMAL(12,3) NULL DEFAULT NULL COMMENT '车辆核定容积(m³)：车辆主数据带出、不可改；手工录车牌时手填；用于配载超容校验',
  `driver_id` BIGINT UNSIGNED NULL DEFAULT NULL COMMENT '司机ID：引用sys_driver.id；快速新增时回写',
  `driver_name` VARCHAR(50) NULL DEFAULT NULL COMMENT '司机姓名：手工输入或从司机主数据选择；发车前必填',
  `driver_phone` VARCHAR(20) NULL DEFAULT NULL COMMENT '司机电话：手工输入或从司机主数据选择；发车前必填',
  `total_waybill_count` INT UNSIGNED NOT NULL DEFAULT 0 COMMENT '已配载单元数：按配载明细实时汇总；整票计1，拆单每张子单计1（不重复计母单）',
  `total_quantity` INT UNSIGNED NOT NULL DEFAULT 0 COMMENT '总数量：已配载配载单元件数之和，实时汇总',
  `total_weight` DECIMAL(12,3) NOT NULL DEFAULT 0 COMMENT '总重量(kg)：已配载单元重量之和，实时汇总',
  `total_volume` DECIMAL(12,3) NOT NULL DEFAULT 0 COMMENT '总体积(m³)：已配载单元体积之和，实时汇总',
  `load_rate_weight` DECIMAL(6,2) NOT NULL DEFAULT 0 COMMENT '重量装载率(%)：总重量÷车辆核定载重×100，保留2位；无载重时为0',
  `load_rate_volume` DECIMAL(6,2) NOT NULL DEFAULT 0 COMMENT '体积装载率(%)：总体积÷车辆核定容积×100，保留2位；无容积时为0',
  `total_prepaid_freight` DECIMAL(14,2) NOT NULL DEFAULT 0 COMMENT '现付运费收入(元)：配载单元biz_waybill.prepaid_fee按票汇总；收入项，不参与分摊、不落biz_trip_fee',
  `total_collect_freight` DECIMAL(14,2) NOT NULL DEFAULT 0 COMMENT '到付运费收入(元)：配载单元biz_waybill.collect_fee按票汇总；收入项，不参与分摊',
  `total_return_freight` DECIMAL(14,2) NOT NULL DEFAULT 0 COMMENT '回付运费收入(元)：配载单元biz_waybill.return_fee按票汇总；收入项，不参与分摊',
  `total_monthly_freight` DECIMAL(14,2) NOT NULL DEFAULT 0 COMMENT '月结运费收入(元)：配载单元biz_waybill.monthly_fee按票汇总；收入项，不参与分摊',
  `total_freight` DECIMAL(14,2) NOT NULL DEFAULT 0 COMMENT '总运费收入(元)：=现付+到付+回付+月结四项之和；服务端计算，客户端传值无效',
  `total_rebate` DECIMAL(14,2) NOT NULL DEFAULT 0 COMMENT '总返款(元)：配载单元返款合计之和（取biz_waybill.rebate_total）；毛利中只扣减一次',
  `total_valid_freight` DECIMAL(14,2) NOT NULL DEFAULT 0 COMMENT '总有效运费(元)：配载单元有效运费之和（取biz_waybill.valid_freight）；仅展示，不参与毛利公式',
  `total_pickup_cost` DECIMAL(14,2) NOT NULL DEFAULT 0 COMMENT '总提货费(元)：上门提货成本合计；本期不启用，固定0',
  `total_delivery_fee` DECIMAL(14,2) NOT NULL DEFAULT 0 COMMENT '总派送费(元)：派送成本合计；本期不启用，固定0',
  `total_transfer_fee` DECIMAL(14,2) NOT NULL DEFAULT 0 COMMENT '总外转费(元)：外转成本合计；本期不启用，固定0',
  `alloc_method` TINYINT UNSIGNED NOT NULL DEFAULT 3 COMMENT '全车统一分摊方式：1=按重量占比，2=按体积占比，3=按重泡比计费重量占比；全车成本项共用；默认3；发车后锁定',
  `prepaid_transport_fee` DECIMAL(14,2) NOT NULL DEFAULT 0 COMMENT '现付运输费(元)：车次成本项，付给承运商，参与分摊与毛利',
  `return_transport_fee` DECIMAL(14,2) NOT NULL DEFAULT 0 COMMENT '回付运输费(元)：车次成本项，付给承运商，参与分摊与毛利',
  `arrival_transport_fee` DECIMAL(14,2) NOT NULL DEFAULT 0 COMMENT '到站付运输费(元)：车次成本项，付给承运商，参与分摊与毛利',
  `prepaid_oil_card_fee` DECIMAL(14,2) NOT NULL DEFAULT 0 COMMENT '现付油卡费(元)：车次成本项，参与分摊与毛利',
  `whole_weighing_fee` DECIMAL(14,2) NOT NULL DEFAULT 0 COMMENT '整车过磅费(元)：车次成本项，录入后参与分摊与毛利',
  `whole_insurance_fee` DECIMAL(14,2) NOT NULL DEFAULT 0 COMMENT '整车保险费(元)：车次成本项，录入后参与分摊与毛利',
  `bulk_load_fee` DECIMAL(14,2) NOT NULL DEFAULT 0 COMMENT '分拨装车费(元)：车次成本项，录入后参与分摊与毛利',
  `bulk_unload_fee` DECIMAL(14,2) NOT NULL DEFAULT 0 COMMENT '分拨卸车费(元)：车次成本项，录入后参与分摊与毛利',
  `bulk_other_fee` DECIMAL(14,2) NOT NULL DEFAULT 0 COMMENT '分拨其他费(元)：车次成本项，录入后参与分摊与毛利',
  `arrival_unload_fee` DECIMAL(14,2) NOT NULL DEFAULT 0 COMMENT '到站卸车费(元)：车次级汇总字段=Σ各子车次biz_trip_dest.arrival_unload_fee（仅有效）；不在车次费用录入区单独录入',
  `arrival_other_fee` DECIMAL(14,2) NOT NULL DEFAULT 0 COMMENT '到站其他费(元)：车次级汇总字段=Σ各子车次biz_trip_dest.arrival_other_fee（仅有效）；不在车次费用录入区单独录入',
  `total_cost_fee` DECIMAL(14,2) NOT NULL DEFAULT 0 COMMENT '成本费用合计(元)：已启用且类型=成本的车次费用项之和；服务端计算，客户端传值无效',
  `gross_profit` DECIMAL(14,2) NOT NULL DEFAULT 0 COMMENT '毛利(元)：=四类运费收入合计−总返款−Σ参与毛利的成本项（=Σ已配载票毛利）；服务端计算，客户端传值无效',
  `depart_by` BIGINT UNSIGNED NULL DEFAULT NULL COMMENT '发车操作人用户ID：关联sys_user.id',
  `depart_by_name` VARCHAR(50) NULL DEFAULT NULL COMMENT '发车操作人姓名快照',
  `close_reason` VARCHAR(500) NULL DEFAULT NULL COMMENT '关闭原因：关闭车次时必填',
  `close_time` DATETIME NULL DEFAULT NULL COMMENT '关闭时间：关闭动作的服务器时间',
  `close_by` BIGINT UNSIGNED NULL DEFAULT NULL COMMENT '关闭操作人用户ID',
  `close_by_name` VARCHAR(50) NULL DEFAULT NULL COMMENT '关闭操作人姓名快照',
  `remark` VARCHAR(500) NULL DEFAULT NULL COMMENT '备注：车次补充说明',
  `dept_id` BIGINT UNSIGNED NOT NULL COMMENT '归属机构ID：车次创建/调度机构；数据权限锚点（与origin_dept_id始发网点区分）',
  `create_by` BIGINT UNSIGNED NULL DEFAULT NULL COMMENT '创建人用户ID：关联sys_user.id；系统初始化数据为空',
  `create_by_name` VARCHAR(50) NOT NULL COMMENT '创建人姓名快照：创建时写入，人员改名不影响历史',
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间：插入时由数据库自动写入，后续不随更新变化',
  `update_by` BIGINT UNSIGNED NULL DEFAULT NULL COMMENT '最后修改人用户ID：关联sys_user.id',
  `update_by_name` VARCHAR(50) NULL DEFAULT NULL COMMENT '最后修改人姓名快照',
  `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '最后修改时间：插入与更新时由数据库自动维护',
  `deleted` TINYINT(1) NOT NULL DEFAULT 0 COMMENT '逻辑删除：0=正常，1=已删除；已发车车次不可删除',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_trip_no` (`trip_no`),
  KEY `idx_status_time` (`trip_status`, `plan_depart_time`),
  KEY `idx_origin` (`origin_dept_id`, `trip_status`),
  KEY `idx_plate` (`plate_no`),
  KEY `idx_create_time` (`create_time`),
  KEY `idx_dept` (`dept_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci
  COMMENT='车次主表：干线车次计划、配载、发车、到站、关闭全生命周期主数据与汇总';
```

#### 3.2.2 `biz_trip_dest` 车次目的网点（子车次）

```sql
CREATE TABLE `biz_trip_dest` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键ID，自增',
  `trip_id` BIGINT UNSIGNED NOT NULL COMMENT '主车次ID：逻辑引用biz_trip.id，不建物理外键',
  `sub_trip_no` VARCHAR(40) NOT NULL COMMENT '子车次编号：主车次号-两位序号，如CT20261125001-01；全局唯一、生成后不可改',
  `seq` TINYINT UNSIGNED NOT NULL COMMENT '目的网点顺序号：从1开始，同一车次内唯一，对应子车次编号后两位',
  `dest_dept_id` BIGINT UNSIGNED NOT NULL COMMENT '目的网点ID：引用sys_dept.id；一装多卸时多条',
  `dest_dept_name` VARCHAR(50) NOT NULL COMMENT '目的网点名称快照：创建时写入',
  `sub_status` TINYINT UNSIGNED NOT NULL DEFAULT 10 COMMENT '子车次状态：10=待发车，20=在途，30=已到站，40=完成卸车',
  `plan_arrive_time` DATETIME NULL DEFAULT NULL COMMENT '计划到站时间：按线路时效估算，可按目的网点单独调整',
  `arrive_time` DATETIME NULL DEFAULT NULL COMMENT '实际到站时间：该网点执行到站时的服务器时间',
  `arrive_by` BIGINT UNSIGNED NULL DEFAULT NULL COMMENT '到站操作人用户ID',
  `arrive_by_name` VARCHAR(50) NULL DEFAULT NULL COMMENT '到站操作人姓名快照',
  `unload_time` DATETIME NULL DEFAULT NULL COMMENT '卸车完成时间：该子车次全部运单卸完的时间',
  `unload_by` BIGINT UNSIGNED NULL DEFAULT NULL COMMENT '卸车操作人用户ID',
  `unload_by_name` VARCHAR(50) NULL DEFAULT NULL COMMENT '卸车操作人姓名快照',
  `total_waybill_count` INT UNSIGNED NOT NULL DEFAULT 0 COMMENT '本子车次配载单元数：实时汇总；子单按1计，不重复计母单',
  `total_quantity` INT UNSIGNED NOT NULL DEFAULT 0 COMMENT '本子车次总数量（件数）：配载单元件数之和',
  `total_weight` DECIMAL(12,3) NOT NULL DEFAULT 0 COMMENT '本子车次总重量(kg)：配载单元重量之和',
  `total_volume` DECIMAL(12,3) NOT NULL DEFAULT 0 COMMENT '本子车次总体积(m³)：配载单元体积之和',
  `arrival_unload_fee` DECIMAL(14,2) NOT NULL DEFAULT 0 COMMENT '到站卸车费(元)：仅干线车次的末端网点在本子车次到站/卸车时录入；=本子车次FEE0019有效合计；分拨中心与短驳固定0',
  `arrival_unload_fee_valid` TINYINT(1) NOT NULL DEFAULT 1 COMMENT '到站卸车费是否有效：1=有效（计入成本与分摊），0=已失效（取消到站/取消卸车后保留金额但不计入）；重新保存置回1',
  `arrival_other_fee` DECIMAL(14,2) NOT NULL DEFAULT 0 COMMENT '到站其他费(元)：与到站卸车费同页同范围录入；=本子车次FEE0020有效合计',
  `arrival_other_fee_valid` TINYINT(1) NOT NULL DEFAULT 1 COMMENT '到站其他费是否有效：1=有效，0=已失效（保留金额但不计入）；重新保存置回1',
  `dept_id` BIGINT UNSIGNED NOT NULL COMMENT '归属机构ID：=主车次dept_id；数据权限锚点',
  `create_by` BIGINT UNSIGNED NULL DEFAULT NULL COMMENT '创建人用户ID',
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_by` BIGINT UNSIGNED NULL DEFAULT NULL COMMENT '最后修改人用户ID',
  `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '最后修改时间',
  `deleted` TINYINT(1) NOT NULL DEFAULT 0 COMMENT '逻辑删除：0=正常，1=已删除；有配载运单时不可删除',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_trip_seq` (`trip_id`, `seq`),
  UNIQUE KEY `uk_sub_trip_no` (`sub_trip_no`),
  KEY `idx_dest_status` (`dest_dept_id`, `sub_status`),
  KEY `idx_trip` (`trip_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci
  COMMENT='车次目的网点表（子车次）：一行=一个目的网点，承载到站/卸车状态与本网点到站费用';
```

#### 3.2.3 `biz_trip_no_seq` 车次号日流水

```sql
CREATE TABLE `biz_trip_no_seq` (
  `seq_date` DATE NOT NULL COMMENT '流水日期：按服务器自然日切分，每天一行',
  `current_val` INT UNSIGNED NOT NULL DEFAULT 0 COMMENT '当日已分配的最大流水号：从1开始原子递增，用于车次号后3位',
  `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '最后分配时间：每次取号自动刷新',
  PRIMARY KEY (`seq_date`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci
  COMMENT='车次号日流水表：按自然日生成3位车次流水，需登记数据权限豁免';
```

### 3.3 V29 · 配载明细 / 费用分摊明细

#### 3.3.1 `biz_trip_waybill`

```sql
CREATE TABLE `biz_trip_waybill` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键ID，自增',
  `trip_id` BIGINT UNSIGNED NOT NULL COMMENT '主车次ID：逻辑引用biz_trip.id',
  `trip_dest_id` BIGINT UNSIGNED NOT NULL COMMENT '子车次ID：逻辑引用biz_trip_dest.id；按运单下一节点自动匹配',
  `sub_trip_no` VARCHAR(40) NOT NULL COMMENT '子车次编号快照：便于打印与对账不联表',
  `waybill_id` BIGINT UNSIGNED NOT NULL COMMENT '配载单元ID：逻辑引用biz_waybill.id；整票=母单(is_child=0)，拆单=子单(is_child=1)',
  `waybill_no` VARCHAR(32) NOT NULL COMMENT '配载单元号快照：整票=母单号，拆单=子单号（母单号-两位序号）',
  `parent_no` VARCHAR(32) NULL DEFAULT NULL COMMENT '母单号快照：整票为空；拆单时=子单所属母单号，用于右栏/打印/卸车展示母子关系',
  `is_child` TINYINT(1) NOT NULL DEFAULT 0 COMMENT '是否子单快照：0=整票，1=子单；随waybill_id一并快照，避免打印联表',
  `split_ratio` DECIMAL(9,6) NULL DEFAULT NULL COMMENT '拆单件数占比：子单件数÷母单原件数，保留6位；整票为NULL；仅展示/审计，金额以子单快照为准',
  `route_node_seq` TINYINT UNSIGNED NOT NULL COMMENT '本段路由节点序号：运单路由节点表中与目的网点匹配的seq，用于到站后回写当前节点',
  `dest_hub_id` BIGINT UNSIGNED NOT NULL COMMENT '本段到达网点ID：=运单当前路由下一节点，必须等于子车次目的网点',
  `dest_hub_name` VARCHAR(50) NOT NULL COMMENT '本段到达网点名称快照',
  `dest_name` VARCHAR(200) NULL DEFAULT NULL COMMENT '运单最终目的地快照：省+市+区县，用于运输清单展示',
  `customer_name` VARCHAR(100) NULL DEFAULT NULL COMMENT '客户名称快照',
  `receiver_name` VARCHAR(50) NULL DEFAULT NULL COMMENT '收货人姓名快照：运输清单与末端联系使用',
  `receiver_phone` VARCHAR(20) NULL DEFAULT NULL COMMENT '收货人电话快照',
  `goods_name` VARCHAR(100) NULL DEFAULT NULL COMMENT '货物名称快照',
  `quantity` INT UNSIGNED NOT NULL COMMENT '本配载单元件数：整票=运单件数；子单=拆单件数（本车份额）；用于汇总、超载校验与清单',
  `weight` DECIMAL(12,3) NOT NULL COMMENT '本配载单元重量(kg)：整票=运单重量；子单=按件数占比折算；用于超重、装载率与分摊基数',
  `volume` DECIMAL(12,3) NOT NULL COMMENT '本配载单元体积(m³)：整票=运单体积；子单=按件数占比折算；用于超容、装载率与分摊基数',
  `prepaid_fee` DECIMAL(12,2) NOT NULL DEFAULT 0 COMMENT '现付运费快照(元)：配载单元现付运费（子单=按件数拆分后金额），收入项',
  `collect_fee` DECIMAL(12,2) NOT NULL DEFAULT 0 COMMENT '到付运费快照(元)：配载单元到付运费（子单=拆分后金额），收入项',
  `return_fee` DECIMAL(12,2) NOT NULL DEFAULT 0 COMMENT '回付运费快照(元)：配载单元回付运费（子单=拆分后金额），收入项',
  `monthly_fee` DECIMAL(12,2) NOT NULL DEFAULT 0 COMMENT '月结运费快照(元)：配载单元月结运费（子单=拆分后金额），收入项',
  `total_fee` DECIMAL(12,2) NOT NULL DEFAULT 0 COMMENT '运费合计快照(元)：=现付+到付+回付+月结；服务端计算',
  `rebate_total` DECIMAL(12,2) NOT NULL DEFAULT 0 COMMENT '返款合计快照(元)：配载单元返款（子单=按件数拆分的现返+欠返），毛利中扣减一次',
  `valid_freight` DECIMAL(12,2) NOT NULL DEFAULT 0 COMMENT '有效运费快照(元)：配载单元有效运费（子单=拆分后值），仅展示',
  `allocated_fee` DECIMAL(14,2) NOT NULL DEFAULT 0 COMMENT '本票成本分摊合计(元)：本票全部成本项分摊结果之和；服务端计算，用于成本对账',
  `profit_cost` DECIMAL(14,2) NOT NULL DEFAULT 0 COMMENT '本票参与毛利的成本分摊合计(元)：仅汇总profit_enabled=1且有效的成本项；服务端计算',
  `gross_profit` DECIMAL(14,2) NOT NULL DEFAULT 0 COMMENT '本票毛利(元)：=本票四类运费合计−本票返款−profit_cost；服务端计算',
  `status_before_load` TINYINT UNSIGNED NOT NULL COMMENT '配载前单元状态：加入车次前的状态（整票20/60，子单20），移除或关闭车次时用于还原',
  `load_status` TINYINT UNSIGNED NOT NULL DEFAULT 1 COMMENT '配载状态：1=已配载，2=已发车，3=已到站，4=已卸车；按配载单元独立流转',
  `load_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '配载时间',
  `load_by` BIGINT UNSIGNED NULL DEFAULT NULL COMMENT '配载操作人用户ID',
  `load_by_name` VARCHAR(50) NULL DEFAULT NULL COMMENT '配载操作人姓名快照',
  `dept_id` BIGINT UNSIGNED NOT NULL COMMENT '归属机构ID：=主车次dept_id；数据权限锚点',
  `create_by` BIGINT UNSIGNED NULL DEFAULT NULL COMMENT '创建人用户ID',
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_by` BIGINT UNSIGNED NULL DEFAULT NULL COMMENT '最后修改人用户ID',
  `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '最后修改时间',
  `deleted` TINYINT(1) NOT NULL DEFAULT 0 COMMENT '逻辑删除：0=正常，1=已移除配载；活跃唯一性由行锁+服务层校验保证，不建唯一索引',
  PRIMARY KEY (`id`),
  KEY `idx_trip_status` (`trip_id`, `load_status`),
  KEY `idx_trip_dest` (`trip_dest_id`),
  KEY `idx_waybill` (`waybill_id`, `deleted`),
  KEY `idx_parent_no` (`parent_no`),
  KEY `idx_dept` (`dept_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci
  COMMENT='车次配载明细表：一行=一个配载单元（整票母单或拆单子单），含收入/返款/成本/毛利快照';
```

> 说明：`idx_dept` 方案未列，但数据权限按 `dept_id` 过滤，本清单补上；如不需要可删。

#### 3.3.2 `biz_trip_waybill_fee`

```sql
CREATE TABLE `biz_trip_waybill_fee` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键ID，自增',
  `trip_id` BIGINT UNSIGNED NOT NULL COMMENT '车次ID：逻辑引用biz_trip.id',
  `trip_waybill_id` BIGINT UNSIGNED NOT NULL COMMENT '配载明细ID：逻辑引用biz_trip_waybill.id',
  `waybill_id` BIGINT UNSIGNED NOT NULL COMMENT '运单ID：冗余，便于按运单查费用',
  `fee_code` VARCHAR(16) NOT NULL COMMENT '费用项编码：引用sys_fee_type.fee_code，如FEE0010',
  `fee_name` VARCHAR(64) NOT NULL COMMENT '费用项名称快照',
  `alloc_method` TINYINT UNSIGNED NOT NULL COMMENT '分摊方式快照：取车次biz_trip.alloc_method（全车统一），1=重量，2=体积，3=重泡比计费重量',
  `basis_value` DECIMAL(14,3) NOT NULL DEFAULT 0 COMMENT '本票分摊基数：按方式取重量/体积/计费重量',
  `basis_total` DECIMAL(16,3) NOT NULL DEFAULT 0 COMMENT '车次分摊基数合计：同方式下全部配载运单基数之和',
  `alloc_ratio` DECIMAL(12,8) NOT NULL DEFAULT 0 COMMENT '分摊比例：basis_value÷basis_total，保留8位',
  `amount` DECIMAL(14,2) NOT NULL DEFAULT 0 COMMENT '本票分摊金额(元)：含尾差调整，全部运单合计必须等于费用项总额',
  `dept_id` BIGINT UNSIGNED NOT NULL COMMENT '归属机构ID：=主车次dept_id；数据权限锚点',
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间：重算分摊时按配载明细+费用项覆盖更新',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_tw_fee` (`trip_waybill_id`, `fee_code`),
  KEY `idx_trip` (`trip_id`),
  KEY `idx_waybill` (`waybill_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci
  COMMENT='费用分摊明细表：每个配载单元×每个成本项一行，是逐票毛利与车次对账的唯一取数来源';
```

### 3.4 V30 · 车次费用明细 / 操作日志

#### 3.4.1 `biz_trip_fee`

```sql
CREATE TABLE `biz_trip_fee` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键ID，自增',
  `trip_id` BIGINT UNSIGNED NOT NULL COMMENT '车次ID：逻辑引用biz_trip.id',
  `trip_dest_id` BIGINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '子车次ID：0=车次级费用（全车录入区）；>0=目的网点级费用（到站卸车页录入的到站卸车费/其他费）',
  `fee_code` VARCHAR(16) NOT NULL COMMENT '费用项编码：引用sys_fee_type.fee_code，如FEE0010',
  `fee_name` VARCHAR(64) NOT NULL COMMENT '费用项名称快照',
  `fee_category` TINYINT UNSIGNED NOT NULL DEFAULT 2 COMMENT '费用类型快照：1=收入，2=成本，3=代收，4=代付；本表实际只落2=成本',
  `amount` DECIMAL(14,2) NOT NULL DEFAULT 0 COMMENT '费用金额(元)：≥0；trip_dest_id=0时为全车该项总额，>0时为该子车次录入额',
  `profit_enabled` TINYINT(1) NOT NULL DEFAULT 0 COMMENT '参与毛利快照：1=参与，0=不参与；保存时从sys_fee_type复制',
  `profit_direction` TINYINT UNSIGNED NULL DEFAULT NULL COMMENT '参与方向快照：1=收入，2=成本；profit_enabled=0时为空',
  `fee_status` TINYINT UNSIGNED NOT NULL DEFAULT 1 COMMENT '费用状态：1=有效，2=已失效（取消到站/取消卸车时置2；金额保留用于审计，不参与汇总与分摊）',
  `invalid_reason` VARCHAR(200) NULL DEFAULT NULL COMMENT '失效原因：取消到站/取消卸车时写入，如「取消卸车（子车次CT20261125001-01）」；有效时为空',
  `sort_order` INT NOT NULL DEFAULT 0 COMMENT '排序号：费用录入区展示顺序',
  `dept_id` BIGINT UNSIGNED NOT NULL COMMENT '归属机构ID：=主车次dept_id；数据权限锚点',
  `create_by` BIGINT UNSIGNED NULL DEFAULT NULL COMMENT '创建人用户ID',
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_by` BIGINT UNSIGNED NULL DEFAULT NULL COMMENT '最后修改人用户ID',
  `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '最后修改时间',
  `deleted` TINYINT(1) NOT NULL DEFAULT 0 COMMENT '逻辑删除：0=正常，1=已删除；唯一键不含deleted，删除后重录需复用原行（服务层upsert）',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_trip_fee` (`trip_id`, `trip_dest_id`, `fee_code`),
  KEY `idx_trip` (`trip_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci
  COMMENT='车次费用明细表：只存车次录入的成本项；四类运费收入不进本表';
```

#### 3.4.2 `biz_trip_log`

```sql
CREATE TABLE `biz_trip_log` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键ID，自增',
  `trip_id` BIGINT UNSIGNED NOT NULL COMMENT '车次ID：逻辑引用biz_trip.id',
  `trip_no` VARCHAR(32) NOT NULL COMMENT '车次编号快照：不联表即可展示',
  `action_type` TINYINT UNSIGNED NOT NULL COMMENT '动作类型：1=创建，2=修改，3=配载，4=移除配载，5=发车，6=取消发车，7=到站，8=完成卸车，9=关闭，10=删除，11=车辆信息变更，12=费用信息变更，13=运单改单刷新，14=运单作废解除配载，15=目的网点变更，16=取消到站，17=取消卸车',
  `dest_id` BIGINT UNSIGNED NULL DEFAULT NULL COMMENT '子车次ID：到站/卸车/取消到站/取消卸车动作记录；其他动作为空',
  `before_snapshot` JSON NULL DEFAULT NULL COMMENT '操作前快照：状态变更类必填，记录车次状态、运单集合、费用汇总等关键字段',
  `after_snapshot` JSON NULL DEFAULT NULL COMMENT '操作后快照：结构同before_snapshot',
  `reason` VARCHAR(500) NULL DEFAULT NULL COMMENT '操作原因：取消发车、取消到站、取消卸车、关闭、删除时必填',
  `operator_id` BIGINT UNSIGNED NOT NULL COMMENT '操作人用户ID',
  `operator_name` VARCHAR(50) NOT NULL COMMENT '操作人姓名快照',
  `dept_id` BIGINT UNSIGNED NOT NULL COMMENT '操作机构ID：数据权限锚点',
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '操作时间：写日志时的服务器时间',
  PRIMARY KEY (`id`),
  KEY `idx_trip_time` (`trip_id`, `create_time`),
  KEY `idx_action` (`action_type`, `create_time`),
  KEY `idx_dept` (`dept_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci
  COMMENT='车次操作日志表：配载、发车、到站、卸车、取消与费用变更的全量留痕';
```

### 3.5 V31 · `sys_fee_type` + 16 项种子

```sql
CREATE TABLE `sys_fee_type` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键ID，自增',
  `fee_code` VARCHAR(16) NOT NULL COMMENT '费用项编码：成本/代收/代付为FEE+4位流水（如FEE0010）；种子预置，生成后不可改；收入内置四项INC01-INC04为代码级虚拟行，不落本表',
  `fee_name` VARCHAR(64) NOT NULL COMMENT '费用项名称：从代码级费用项目录选择，创建后不可修改；如「现付运输费」',
  `fee_category` TINYINT UNSIGNED NOT NULL DEFAULT 2 COMMENT '费用类型：1=收入，2=成本，3=代收，4=代付；内置收入四项恒为1；新增时四类可选',
  `enabled` TINYINT(1) NOT NULL DEFAULT 1 COMMENT '是否启用：1=启用（成本项参与车次费用录入与分摊），0=停用；内置收入四项恒为启用',
  `profit_enabled` TINYINT(1) NOT NULL DEFAULT 1 COMMENT '是否参与毛利计算：1=参与，0=不参与；代收/代付强制为0；收入/成本默认1，可按项关闭',
  `profit_direction` TINYINT UNSIGNED NULL DEFAULT 2 COMMENT '参与方向：1=收入（加项），2=成本（减项）；代收/代付必须为空；收入必须为1，成本必须为2',
  `source_field` VARCHAR(64) NULL DEFAULT NULL COMMENT '取数来源标识：收入内置项对应运单字段（如prepaid_fee）；成本项对应biz_trip./biz_trip_dest.字段名；代收/代付与新建收入为空',
  `sort_order` INT NOT NULL DEFAULT 0 COMMENT '排序号：费用配置列表与车次费用录入区展示顺序，升序',
  `remark` VARCHAR(500) NULL DEFAULT NULL COMMENT '备注：业务说明、口径或停用原因',
  `dept_id` BIGINT UNSIGNED NOT NULL COMMENT '归属机构ID：固定总部ID=1；本表登记数据权限豁免（总部全局配置）',
  `create_by` BIGINT UNSIGNED NULL DEFAULT NULL COMMENT '创建人用户ID；种子为NULL',
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_by` BIGINT UNSIGNED NULL DEFAULT NULL COMMENT '最后修改人用户ID',
  `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '最后修改时间',
  `deleted` TINYINT(1) NOT NULL DEFAULT 0 COMMENT '逻辑删除：0=正常，1=已删除；配置页不做删除，仅启停',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_fee_code` (`fee_code`),
  KEY `idx_enabled_sort` (`enabled`, `sort_order`),
  KEY `idx_category_sort` (`fee_category`, `sort_order`),
  KEY `idx_name` (`fee_name`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci
  COMMENT='费用类型配置表：收入/成本/代收/代付统一配置（收入四项为代码只读行，不落库）';
```

### 3.6 V32 · 车辆 / 司机最小主数据

```sql
CREATE TABLE `sys_vehicle` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键ID，自增',
  `plate_no` VARCHAR(20) NOT NULL COMMENT '车牌号：唯一，车次选择/快速新增的唯一键',
  `vehicle_type` VARCHAR(50) NULL DEFAULT NULL COMMENT '车辆型号：文本，本期不建车型表（菜单1903后续补）',
  `vehicle_load` DECIMAL(12,3) NULL DEFAULT NULL COMMENT '核定载重(kg)：车次超重校验来源，选择后带出、不可改',
  `vehicle_volume` DECIMAL(12,3) NULL DEFAULT NULL COMMENT '核定容积(m³)：车次超容校验来源，选择后带出、不可改',
  `owner_name` VARCHAR(50) NULL DEFAULT NULL COMMENT '车主名称：自有/外协车主',
  `owner_phone` VARCHAR(20) NULL DEFAULT NULL COMMENT '车主电话',
  `status` TINYINT UNSIGNED NOT NULL DEFAULT 1 COMMENT '状态：1=启用，0=停用；停用后不在选择器出现',
  `dept_id` BIGINT UNSIGNED NOT NULL COMMENT '归属机构ID：车辆所属机构；数据权限锚点',
  `create_by` BIGINT UNSIGNED NULL DEFAULT NULL COMMENT '创建人用户ID',
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_by` BIGINT UNSIGNED NULL DEFAULT NULL COMMENT '最后修改人用户ID',
  `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '最后修改时间',
  `deleted` TINYINT(1) NOT NULL DEFAULT 0 COMMENT '逻辑删除：0=正常，1=已删除；同一车牌第二次删除会与历史删除行唯一键冲突，恢复原行即可',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_plate_no` (`plate_no`, `deleted`),
  KEY `idx_dept_status` (`dept_id`, `status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci
  COMMENT='车辆最小主数据表：车次选择与快速新增使用，完整运力管理后续建设';

CREATE TABLE `sys_driver` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键ID，自增',
  `driver_name` VARCHAR(50) NOT NULL COMMENT '司机姓名',
  `driver_phone` VARCHAR(20) NOT NULL COMMENT '司机电话：车次发车前必填项来源；不唯一，允许重名同号不同人',
  `license_no` VARCHAR(30) NULL DEFAULT NULL COMMENT '驾驶证号',
  `status` TINYINT UNSIGNED NOT NULL DEFAULT 1 COMMENT '状态：1=启用，0=停用；停用后不在选择器出现',
  `dept_id` BIGINT UNSIGNED NOT NULL COMMENT '归属机构ID：司机所属机构；数据权限锚点',
  `create_by` BIGINT UNSIGNED NULL DEFAULT NULL COMMENT '创建人用户ID',
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_by` BIGINT UNSIGNED NULL DEFAULT NULL COMMENT '最后修改人用户ID',
  `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '最后修改时间',
  `deleted` TINYINT(1) NOT NULL DEFAULT 0 COMMENT '逻辑删除：0=正常，1=已删除',
  PRIMARY KEY (`id`),
  KEY `idx_name_phone` (`driver_name`, `driver_phone`),
  KEY `idx_dept_status` (`dept_id`, `status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci
  COMMENT='司机最小主数据表：车次选择与快速新增使用';
```

### 3.7 V33 · `sys_config` + 2 键种子 + `sys_route` 加列

```sql
CREATE TABLE `sys_config` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键ID，自增',
  `config_key` VARCHAR(64) NOT NULL COMMENT '配置键：全局唯一，如fee.volume_weight_ratio、gross.formula',
  `config_value` TEXT NOT NULL COMMENT '配置值：字符串存储；数值型为数值字符串（如333），JSON型存JSON文本（如毛利公式）',
  `config_name` VARCHAR(100) NOT NULL COMMENT '配置名称：中文展示名，如「全局默认重泡比(kg/m³)」',
  `value_type` VARCHAR(16) NOT NULL DEFAULT 'string' COMMENT '值类型：string/number/bool/json',
  `remark` VARCHAR(500) NULL DEFAULT NULL COMMENT '说明：业务含义、取值范围',
  `create_by` BIGINT UNSIGNED NULL DEFAULT NULL COMMENT '创建人用户ID；种子为NULL',
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_by` BIGINT UNSIGNED NULL DEFAULT NULL COMMENT '最后修改人用户ID',
  `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '最后修改时间',
  `deleted` TINYINT(1) NOT NULL DEFAULT 0 COMMENT '逻辑删除：0=正常，1=已删除；平台配置不做删除',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_config_key` (`config_key`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci
  COMMENT='系统全局配置表：重泡比默认值与运单毛利公式等平台级配置，需登记数据权限豁免';

ALTER TABLE `sys_route`
  ADD COLUMN `volume_weight_ratio` DECIMAL(10,2) NULL DEFAULT NULL
    COMMENT '线路重泡比(kg/m³)：线路级覆盖；为空或0时回落sys_config.fee.volume_weight_ratio全局值；新建线路前端默认带出全局值',
  ALGORITHM=INSTANT;
```

### 3.8 V34 · 菜单 / 按钮权限种子

> 采用显式 ID + `ON DUPLICATE KEY UPDATE`，可重复执行；菜单 ID 段：3201+ 装车配载、3301+ 到站卸车、3401+ 系统配置。

```sql
SET NAMES utf8mb4;

-- 1) 到站卸车菜单（P0-2 待家伟选 A：新增 1302 并停用 1401；或 B：复用 1401）
INSERT INTO sys_menu (id,parent_id,menu_name,menu_type,route_path,component,redirect,perms,icon,terminal_type,sort_order,visible,status,keep_alive,affix,remark) VALUES
(1302,1003,'到站卸车',2,'/transport/loading/unload-list','transport/loading/unload-list/index',NULL,NULL,'Download','PC',20,1,1,1,0,'运输管理-到站卸车列表（v1.3）')
ON DUPLICATE KEY UPDATE
  parent_id=VALUES(parent_id),menu_name=VALUES(menu_name),menu_type=VALUES(menu_type),
  route_path=VALUES(route_path),component=VALUES(component),sort_order=VALUES(sort_order),
  visible=VALUES(visible),status=VALUES(status),remark=VALUES(remark),deleted=0;

-- 2) 车次列表/配载页按钮权限（父级 1301）
INSERT INTO sys_menu (id,parent_id,menu_name,menu_type,route_path,component,redirect,perms,icon,terminal_type,sort_order,visible,status,keep_alive,affix,remark) VALUES
(3201,1301,'创建车次',3,NULL,NULL,NULL,'transport:loading:add',NULL,'PC',10,1,1,0,0,'按钮权限：车次列表-创建车次'),
(3202,1301,'修改车次',3,NULL,NULL,NULL,'transport:loading:edit',NULL,'PC',20,1,1,0,0,'按钮权限：车次列表-修改车次'),
(3203,1301,'删除车次',3,NULL,NULL,NULL,'transport:loading:delete',NULL,'PC',30,1,1,0,0,'按钮权限：车次列表-删除车次'),
(3204,1301,'取消发车',3,NULL,NULL,NULL,'transport:loading:cancel-depart',NULL,'PC',40,1,1,0,0,'按钮权限：车次列表-取消发车'),
(3205,1301,'打印运输清单',3,NULL,NULL,NULL,'transport:loading:print',NULL,'PC',50,1,1,0,0,'按钮权限：车次列表/配载页-打印'),
(3206,1301,'装车配载',3,NULL,NULL,NULL,'transport:loading:load',NULL,'PC',60,1,1,0,0,'按钮权限：配载页-穿梭/拆单/移除'),
(3207,1301,'发车',3,NULL,NULL,NULL,'transport:loading:depart',NULL,'PC',70,1,1,0,0,'按钮权限：配载页Tab2-发车'),
(3208,1301,'关闭车次',3,NULL,NULL,NULL,'transport:loading:close',NULL,'PC',80,1,1,0,0,'按钮权限：车次列表/配载页-关闭'),
(3209,1301,'目的网点变更',3,NULL,NULL,NULL,'transport:loading:dest',NULL,'PC',90,1,1,0,0,'按钮权限：配载页-目的网点增删改'),
(3210,1301,'车次到站',3,NULL,NULL,NULL,'transport:loading:arrive',NULL,'PC',100,1,1,0,0,'按钮权限：到货卸车列表-到站'),
(3211,1301,'车次卸车',3,NULL,NULL,NULL,'transport:loading:unload',NULL,'PC',110,1,1,0,0,'按钮权限：到货卸车列表/作业页-卸车'),
(3212,1301,'取消到站',3,NULL,NULL,NULL,'transport:loading:cancel-arrive',NULL,'PC',120,1,1,0,0,'按钮权限：到货卸车列表-取消到站'),
(3213,1301,'取消卸车',3,NULL,NULL,NULL,'transport:loading:cancel-unload',NULL,'PC',130,1,1,0,0,'按钮权限：作业页已卸车Tab-取消卸车（运单级）')
ON DUPLICATE KEY UPDATE
  parent_id=VALUES(parent_id),menu_name=VALUES(menu_name),menu_type=VALUES(menu_type),
  perms=VALUES(perms),sort_order=VALUES(sort_order),status=VALUES(status),remark=VALUES(remark),deleted=0;

-- 3) 系统配置按钮权限（父级 1108 系统配置）
INSERT INTO sys_menu (id,parent_id,menu_name,menu_type,route_path,component,redirect,perms,icon,terminal_type,sort_order,visible,status,keep_alive,affix,remark) VALUES
(3401,1108,'费用类型配置查看',3,NULL,NULL,NULL,'system:config:fee-type:view',NULL,'PC',10,1,1,0,0,'按钮权限：财务配置-费用类型列表'),
(3402,1108,'费用类型配置编辑',3,NULL,NULL,NULL,'system:config:fee-type:edit',NULL,'PC',20,1,1,0,0,'按钮权限：财务配置-新增/编辑/启停'),
(3403,1108,'运单毛利公式查看',3,NULL,NULL,NULL,'system:config:gross:view',NULL,'PC',30,1,1,0,0,'按钮权限：系统设置-运单毛利计算公式-查看'),
(3404,1108,'运单毛利公式保存',3,NULL,NULL,NULL,'system:config:gross:save',NULL,'PC',40,1,1,0,0,'按钮权限：系统设置-运单毛利计算公式-保存')
ON DUPLICATE KEY UPDATE
  parent_id=VALUES(parent_id),menu_name=VALUES(menu_name),menu_type=VALUES(menu_type),
  perms=VALUES(perms),sort_order=VALUES(sort_order),status=VALUES(status),remark=VALUES(remark),deleted=0;
```

---

## 4. 种子数据明细

### 4.1 费用类型 16 项（默认按 4.1，P0-3 待确认）

```sql
SET NAMES utf8mb4;

INSERT INTO `sys_fee_type`
(`fee_code`,`fee_name`,`fee_category`,`enabled`,`profit_enabled`,`profit_direction`,`source_field`,`sort_order`,`remark`,`dept_id`) VALUES
('FEE0010','现付运输费',2,1,1,2,'biz_trip.prepaid_transport_fee',10,'车次成本项，参与分摊与毛利',1),
('FEE0011','回付运输费',2,1,1,2,'biz_trip.return_transport_fee',20,'车次成本项，参与分摊与毛利',1),
('FEE0012','到站付运输费',2,1,1,2,'biz_trip.arrival_transport_fee',30,'车次成本项，参与分摊与毛利',1),
('FEE0013','现付油卡费',2,1,1,2,'biz_trip.prepaid_oil_card_fee',40,'车次成本项，本次调整为参与毛利',1),
('FEE0014','整车过磅费',2,1,1,2,'biz_trip.whole_weighing_fee',50,'车次成本项，参与分摊与毛利',1),
('FEE0015','整车保险费',2,1,1,2,'biz_trip.whole_insurance_fee',60,'车次成本项，参与分摊与毛利',1),
('FEE0016','分拨装车费',2,1,1,2,'biz_trip.bulk_load_fee',70,'车次成本项，参与分摊与毛利',1),
('FEE0017','分拨卸车费',2,1,1,2,'biz_trip.bulk_unload_fee',80,'车次成本项，参与分摊与毛利',1),
('FEE0018','分拨其他费',2,1,1,2,'biz_trip.bulk_other_fee',90,'车次成本项，参与分摊与毛利',1),
('FEE0019','到站卸车费',2,1,1,2,'biz_trip_dest.arrival_unload_fee',100,'车次级费用，仅干线+末端网点，在到站卸车页内联录入',1),
('FEE0020','到站其他费',2,1,1,2,'biz_trip_dest.arrival_other_fee',110,'车次级费用，与到站卸车费同页同范围录入',1),
('FEE0021','提货成本',2,0,1,2,'biz_trip.total_pickup_cost',120,'本期停用，字段预留',1),
('FEE0022','派送费',2,0,1,2,'biz_trip.total_delivery_fee',130,'本期停用，字段预留',1),
('FEE0023','外转费',2,0,1,2,'biz_trip.total_transfer_fee',140,'本期停用，字段预留',1),
('FEE0024','现返',2,1,0,NULL,150,'运单级成本项（取biz_waybill.cash_back），不参与毛利、不进车次录入与分摊',1),
('FEE0025','欠返',2,1,0,NULL,160,'运单级成本项（取biz_waybill.owe_back），不参与毛利、不进车次录入与分摊',1)
ON DUPLICATE KEY UPDATE
  fee_name=VALUES(fee_name),fee_category=VALUES(fee_category),enabled=VALUES(enabled),
  profit_enabled=VALUES(profit_enabled),profit_direction=VALUES(profit_direction),
  source_field=VALUES(source_field),sort_order=VALUES(sort_order),remark=VALUES(remark),deleted=0;
```

> `FEE0019/0020` 的 `source_field` 用 `biz_trip_dest.*`（不是 `biz_trip.*`），避免被「只渲染 `biz_trip.` 开头」规则误放进车次费用录入区。
> 若家伟选 6.8 口径（14 成本 + 1 代收 + 1 代付），需另定 2 个代收/代付示例编码与名称，本清单暂不落。

### 4.2 `sys_config` 2 键

```sql
INSERT INTO `sys_config` (`config_key`,`config_value`,`config_name`,`value_type`,`remark`) VALUES
('fee.volume_weight_ratio','333','全局默认重泡比(kg/m³)','number','取值>0；线路未配置时回落此值')
ON DUPLICATE KEY UPDATE
  config_value=VALUES(config_value),config_name=VALUES(config_name),value_type=VALUES(value_type),remark=VALUES(remark),deleted=0;

INSERT INTO `sys_config` (`config_key`,`config_value`,`config_name`,`value_type`,`remark`) VALUES
('gross.formula',
 '{"version":1,"rebateDeduct":true,"items":[{"feeCode":"INC01","name":"现付运费","side":"add"},{"feeCode":"INC02","name":"到付运费","side":"add"},{"feeCode":"INC03","name":"回付运费","side":"add"},{"feeCode":"INC04","name":"月结运费","side":"add"},{"feeCode":"FEE0010","name":"现付运输费","side":"sub"},{"feeCode":"FEE0011","name":"回付运输费","side":"sub"},{"feeCode":"FEE0012","name":"到站付运输费","side":"sub"},{"feeCode":"FEE0013","name":"现付油卡费","side":"sub"},{"feeCode":"FEE0014","name":"整车过磅费","side":"sub"},{"feeCode":"FEE0015","name":"整车保险费","side":"sub"},{"feeCode":"FEE0016","name":"分拨装车费","side":"sub"},{"feeCode":"FEE0017","name":"分拨卸车费","side":"sub"},{"feeCode":"FEE0018","name":"分拨其他费","side":"sub"},{"feeCode":"FEE0019","name":"到站卸车费","side":"sub"},{"feeCode":"FEE0020","name":"到站其他费","side":"sub"}],"updatedBy":"system","updatedAt":"2026-11-25 00:00:00","history":[]}',
 '运单毛利计算公式','json','单票公式：收入加项 − 返款(rebateDeduct=true时) − 成本减项；version自增，history保留最近20版')
ON DUPLICATE KEY UPDATE
  config_value=VALUES(config_value),config_name=VALUES(config_name),value_type=VALUES(value_type),remark=VALUES(remark),deleted=0;
```

### 4.3 菜单口径（P0-2，需家伟选）

| 选项 | 做法 | 影响 |
|---|---|---|
| **A（默认写入本清单）** | 新增 `1302 到站卸车`（父级 1003），同时把既有 `1401 到货卸车` 置 `status=0, visible=0` | 符合 v1.3 菜单结构；会改 1 行既有菜单数据（可回滚） |
| **B** | 复用 `1401`：把 `parent_id` 改 1003、`route_path` 改 `/transport/loading/unload-list`、`component` 改新页面 | 不动 1003 新增；但「到货管理」下原有入口消失，且 id 与语义（到货管理）错位 |

> 我建议 **A**：运输管理下入口语义正确，旧占位菜单停用但不删，回滚容易。请家伟确认后再落 V34。

---

## 5. 影响行数 / 加锁时间 / 执行窗口

| 版本 | 对象 | 当前影响行数（实测） | 操作类型 | 预估加锁/耗时 | 建议窗口 |
|---|---|---|---|---|---|
| V27 加列 | `biz_waybill` | 5（生产按实际统计） | INSTANT ADD COLUMN | MDL 瞬间，< 0.5s | 任意（仍建议低峰） |
| V27 索引 | `biz_waybill` | 同上 | INPLACE / LOCK=NONE | 5 行 < 1s；生产按约 1M 行估 10–30s（与磁盘 IO 相关） | 低峰 |
| V27 回填 | `biz_waybill` | 5（生产同条件统计） | UPDATE（走主键/状态索引） | 5 行 < 0.1s；生产按 `status IN (20,60) AND current_hub_id IS NULL` 估，建议分批（每批 5000） | 低峰 |
| V28–V32 建表 | 11 张新表 | 0 | CREATE TABLE | < 1s/张 | 任意 |
| V31 种子 | `sys_fee_type` | 插入 16 行 | INSERT | < 0.2s | 任意 |
| V33 加列 | `sys_route` | 8 | INSTANT | < 0.5s | 任意 |
| V33 种子 | `sys_config` | 插入 2 行 | INSERT | < 0.2s | 任意 |
| V34 菜单 | `sys_menu` | 62 | INSERT 18 行 | < 0.2s | 任意 |

**生产规模估算方法**（执行前由 DBA 跑一次，写入变更单）：

```sql
SELECT COUNT(*) FROM biz_waybill WHERE deleted=0;
SELECT COUNT(*) FROM biz_waybill WHERE current_hub_id IS NULL AND status IN (20,60) AND deleted=0;
SELECT table_rows, ROUND(data_length/1024/1024,2) AS data_mb FROM information_schema.tables
WHERE table_schema='yunshu' AND table_name='biz_waybill';
```

- 加索引耗时 ≈ 表数据量 / 磁盘顺序读带宽；经验值：100 万行、单机 SSD 约 10–30 秒。
- **禁止**在业务高峰、批量开单/导出任务运行时执行 V27。

---

## 6. 回滚方案与脚本路径

> 回滚脚本为**手工执行草稿**，放在 `outputs/ddl-v1.3/rollback/`，不在 Flyway classpath，不会自动执行。
> 生产回滚前必须先做全库备份；DROP TABLE / DROP COLUMN 均为**不可逆数据丢失**。

| 版本 | 回滚脚本路径 | 回滚内容 | 数据影响 |
|---|---|---|---|
| V27 | `outputs/ddl-v1.3/rollback/V27__waybill_load_columns_rollback.sql` | 删 3 索引 + 5 列 | `load_status/current_hub_*` 等运行数据丢失；需先备份 |
| V28 | `.../V28__trip_base_rollback.sql` | DROP `biz_trip_dest`、`biz_trip`、`biz_trip_no_seq` | 车次主数据丢失 |
| V29 | `.../V29__trip_waybill_rollback.sql` | DROP `biz_trip_waybill_fee`、`biz_trip_waybill` | 配载与分摊结果丢失 |
| V30 | `.../V30__trip_fee_log_rollback.sql` | DROP `biz_trip_log`、`biz_trip_fee` | 车次费用与日志丢失 |
| V31 | `.../V31__fee_type_rollback.sql` | DROP `sys_fee_type` | 费用配置与种子丢失 |
| V32 | `.../V32__vehicle_driver_rollback.sql` | DROP `sys_driver`、`sys_vehicle` | 车辆/司机主数据丢失 |
| V33 | `.../V33__config_route_rollback.sql` | DROP `sys_config`；`sys_route` DROP COLUMN `volume_weight_ratio` | 配置丢失；线路重泡比覆盖值丢失 |
| V34 | `.../V34__menu_perm_rollback.sql` | 删 3201–3213、3301+、3401–3404、1302；恢复 1401 显示（若选 A） | 仅权限数据 |

**回滚顺序**：V34 → V33 → V32 → V31 → V30 → V29 → V28 → V27（与正向相反；先删引用方，再删被引用表）。
**Flyway 处理**：回滚后需同步删除 `flyway_schema_history` 对应版本行（或改为 `flyway repair`），否则下次启动会校验失败。该操作涉及元数据表，**必须家伟确认后由我执行**。

---

## 7. 迁移后必须同步的代码/配置（不属 DDL，但不做会出事）

1. **数据权限豁免**：`DataScopeTables.EXEMPT_TABLES` 增加
   - `biz_trip_no_seq`（无 dept_id）
   - `sys_fee_type`（总部全局，虽有 dept_id 但需跨公司可见）
   - `sys_config`（无 dept_id）
   - 否则 `sys_config` 查询会被拼接 `dept_id` 条件直接报错。
2. **枚举同步**：车次状态、子车次状态、配载状态、费用类型（1–4）、动作类型（1–17）、分摊方式（1–3）与前端字典一致。
3. **`biz_trip_fee` 写入策略**：唯一键不含 `deleted`，重算/重录必须 upsert，不能先逻辑删再插。
4. **`biz_trip_waybill_fee` 重算策略**：按 `(trip_waybill_id, fee_code)` upsert；删除配载单元时物理删除或按车次整体重建。
5. **毛利公式候选过滤**：建议 `enabled=1 AND profit_enabled=1`，避免把停用的 FEE0021–0023 带进公式。
6. **文档修订**（开发前）：6.11 的 `FEE0001` → `FEE0010`；6.8 的 16 项口径；6.11 权限标识改为英文 `system:config:gross:*` 与现有 `perms` 规范一致。

---

## 8. 迁移后验证 SQL

```sql
-- 1) 版本与结构
SELECT version, description, success FROM flyway_schema_history ORDER BY installed_rank DESC LIMIT 8;
SHOW COLUMNS FROM biz_waybill LIKE 'load_status';
SHOW COLUMNS FROM biz_waybill LIKE 'current_hub_id';
SHOW COLUMNS FROM sys_route LIKE 'volume_weight_ratio';
SHOW INDEX FROM biz_waybill WHERE Key_name IN ('idx_current_hub','idx_inventory_time','idx_parent_deleted');

-- 2) 新表齐全（应为 11 张）
SELECT COUNT(*) AS new_tables FROM information_schema.tables
WHERE table_schema='yunshu' AND table_name IN
('biz_trip','biz_trip_dest','biz_trip_waybill','biz_trip_waybill_fee','biz_trip_fee',
 'biz_trip_log','biz_trip_no_seq','sys_fee_type','sys_vehicle','sys_driver','sys_config');

-- 3) 种子
SELECT COUNT(*) AS fee_types FROM sys_fee_type WHERE deleted=0;                 -- 期望 16
SELECT config_key, value_type FROM sys_config WHERE deleted=0;                  -- 期望 2 行
SELECT COUNT(*) AS menus FROM sys_menu WHERE id IN (1302,3201,3213,3401,3404);   -- 期望 5

-- 4) 回填
SELECT COUNT(*) AS pending FROM biz_waybill
WHERE status IN (20,60) AND current_hub_id IS NULL AND deleted=0;               -- 期望 0

-- 5) 池查询执行计划（确认走 idx_current_hub）
EXPLAIN SELECT id FROM biz_waybill
WHERE current_hub_id=26 AND load_status=0 AND status IN (20,60) AND deleted=0
ORDER BY inventory_time LIMIT 20;
```

---

## 9. 风险矩阵与缓解

| # | 风险 | 等级 | 触发条件 | 缓解 |
|---|---|---|---|---|
| 1 | 生产 `biz_waybill` 大表加索引锁表 | 中高 | 百万级行、高峰执行 | INPLACE/LOCK=NONE + 低峰 + 先备份 + 分批回填 + 执行前 `SHOW PROCESSLIST` 确认无长事务 |
| 2 | 未回填 `current_hub_id` 导致待配载池为空 | 高 | 直接跑 V27 不回填 | V27 内置幂等回填；上线后跑第 8 节第 4 条校验 |
| 3 | `sys_config` 未豁免数据权限 → 接口 500 | 高 | 只跑 DDL 不改代码 | 第 7 节第 1 条列为迁移同批上线项 |
| 4 | 菜单双入口（1401 与 1302） | 中 | 未拍板直接建 1302 | P0-2 先拍板；选 A 则停用 1401 |
| 5 | 费用种子口径分叉（4.1 vs 6.8） | 中 | 未确认即落 16 项 | P0-3 先确认；本清单按 4.1 |
| 6 | 到站费用被误渲染进车次费用区 | 中 | `source_field` 写成 `biz_trip.*` | 种子用 `biz_trip_dest.*`；后端按前缀过滤并加单测 |
| 7 | 公式把停用成本项带入 / 示例编码错 | 低中 | 直接照 6.11 实现 | 候选条件 `enabled=1 AND profit_enabled=1`；文档改 `FEE0010` |
| 8 | `biz_trip_fee` 逻辑删后重录唯一键冲突 | 低 | 先软删再插入 | 服务层 upsert，禁止先删后插 |
| 9 | `sys_vehicle` 同车牌第二次删除冲突 | 低 | 反复删同一车牌 | 恢复原行（deleted 置 0），不重复插入 |
| 10 | 回滚后 Flyway 校验失败 | 中 | 手工回滚未清理历史 | 回滚脚本末尾附 `DELETE FROM flyway_schema_history WHERE version=...`（需审批后执行） |

---

## 10. 批准清单（请家伟逐项确认）

- [ ] **P0-1** V27 包含存量运单回填（`current_hub_id=origin_hub_id`，status 20/60，幂等）
- [ ] **P0-2** 菜单选 **A**（新增 1302 + 停用 1401）还是 **B**（复用 1401）
- [ ] **P0-3** 费用种子按 **4.1**（16 项：14 成本 + 现返/欠返）还是按 6.8（14 成本 + 1 代收 + 1 代付）
- [ ] V27 `idx_parent_deleted` 保留 / 去掉（现有 `uk_parent_child` 已覆盖 parent_id 前缀）
- [ ] V27 新增 `idx_dept`（`biz_trip_waybill`）是否保留
- [ ] 回滚脚本路径与 Flyway 历史清理方式
- [ ] 批准后执行顺序：先 `mysqldump` 全库备份 → 写 V27–V34 → 启动应用跑 Flyway → 跑第 8 节验证 SQL → 报告实测

> 批准前我不会创建任何 `V2x__*.sql` 到 `db/migration`，也不会连库执行任何 DDL。

---

## 11. 执行记录（2026-11-25 21:25）

| 项 | 结果 |
|---|---|
| 迁移前备份 | `backups/2026-11-25/pre-v27-yunshu.sql`（7.7 MB） |
| Flyway 版本 | V27–V34 全部 `success=1`，当前版本 **34** |
| 执行耗时 | V27–V30 首次启动完成；V31–V34 第二次启动 0.255s |
| 新表 | 11/11 齐全 |
| `biz_waybill` 列/索引 | 5 列 + `idx_current_hub`/`idx_inventory_time`/`idx_parent_deleted` 均已生效 |
| 存量回填 | `status IN (20,60) AND current_hub_id IS NULL` = **0** |
| 费用种子 | 16 项；FEE0019 source=`biz_trip_dest.*`；FEE0024/25 `profit_enabled=0`、方向/来源为空 |
| `sys_config` | 2 键，`JSON_VALID=1` |
| 菜单 | 1302 到站卸车启用；1401 到货卸车 `status=0/visible=0`；按钮 3201–3213、3401–3404 共 17 条 |
| 服务 | 新 jar 启动成功，8090 监听，`/api/v1/auth/captcha` 返回 HTTP 200 |

**中途插曲（已修复）**：V31 首次执行时 FEE0024/FEE0025 两行少写一个 `NULL`（`profit_direction` 与 `source_field` 两个空值），MySQL 报 1136；V27–V30 已提交、V31 建表已提交但插入失败。处理：确认 `sys_fee_type` 0 行后 `DROP TABLE`、删除 `flyway_schema_history` 中 V31 失败记录、修正 SQL 重新打包，二次启动 V31–V34 全部成功。最终结构无残留。

**待下一阶段**：`DataScopeTables.EXEMPT_TABLES` 增加 `biz_trip_no_seq`、`sys_fee_type`、`sys_config`（后端接口开发前必须完成）。
