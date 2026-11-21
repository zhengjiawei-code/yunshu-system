# 路由管理 + 区域管理 · 核查与设计方案（v0.1）

> 状态：**仅设计，未写业务代码、未建表、未执行任何 DDL、未修改现有文件**。
> 依据：luohuo 源码逐项核对（路径与行号见下）+ yunshu 现有库结构与代码事实。
> 补充裁决（2026-11-21）：库区为手工填写文本，不做主数据表，打印标签直接取该字段值。

---

# 第一部分 · 核查事实

## 1. luohuo 路由管理（外转/直达）

| 项 | 事实 | 位置 |
|---|---|---|
| 表名 | `sys_route`（直送/外转同一张表） | `backend/app/models/route.py:11-39`、`backend/database/migrate_route_module.sql:75-98` |
| 字段 | `id, route_code, route_name, route_type(1直送/2外转), region_code, region_level(2市/3区县/4乡级), region_full_name, carrier_id, status, sort_order, remark, org_id, is_deleted, create_by, create_time, update_by, update_time` | 同上 |
| 索引 | `idx_region(region_code)`、`idx_type(route_type)`、`idx_carrier(carrier_id)`、`idx_status(status)`；目的地唯一由应用层 upsert 保证 | `migrate_route_module.sql:93-98` |
| 路由编码 | 自动生成：`区划编码_ZT`（直送）/`区划编码_WZ`（外转） | `backend/app/crud/route.py:23-30` |
| 名称 | 默认 `区划全路径`，可改（更新接口允许 `route_name`） | `crud/route.py:267-327`、`schemas/route.py:45-52` |
| 唯一规则 | 「适用网点 + 目的地」唯一；已存在时更新类型/编码/名称/层级/承运商 | `crud/route.py:267-327` |
| 承运商 | 仅外转可挂，直送不允许；切直送自动清空；批量挂承运商 | `api/v1/system/route.py:253-302, 345-352` |
| 权限 | 仅总部可维护路由 | `api/v1/system/route.py:55-56` |
| 页面列（11 列） | 路由编码 140 / 路由名称 170 / 目的地 180 / 级别 120 / 类型 110 / 承运商 150 / 状态 100 / 排序 110 / 修改人 120 / 修改时间 200 / 操作 130 | `frontend/src/views/system/route/index.vue:361-372` |
| 页面筛选 | 编码、名称、目的地（文本）；级别/类型/承运商/状态（下拉）；排序（数字）；修改人（文本）；修改时间（日期区间） | 同文件 `389-415` |
| 抽屉字段 | 适用网点（只读回显）、目的地（只读）、路由编码、路由名称、路由类型、默认承运商（仅外转）、状态、排序、备注 | 同文件 `273-308` |
| 与区划关系 | 外键逻辑关联 `sys_region.code`；区划编码变更时同步迁移路由编码/名称 | `crud/region.py:168-242` |

## 2. luohuo 区域管理

| 项 | 事实 | 位置 |
|---|---|---|
| 表名 | `sys_region`，主键 `code` | `backend/app/models/region.py:10-38`、`migrate_route_module.sql:13-26` |
| 字段 | `code, name, parent_code, level(1省/2市/3区县/4乡级), full_name, source(init/online/file), version(YYYYMM), sync_time` | 同上 |
| 索引 | `idx_parent`、`idx_name`、`idx_level` | 同上 |
| 数据来源 | 在线 CDN（`modood/Administrative-divisions-of-China`）：`provinces/cities/areas/streets.json`，可配置镜像 | `backend/app/config.py:57-64`、`api/v1/system/region.py:47-119` |
| 页面结构 | **同步控制台**：版本信息卡（当前版本/来源/最近同步/远端版本/区划总数/状态）+「在线更新区划」+ 实时进度 + 同步日志表 + 报告弹窗（新增/改动/编码迁移/待删除四个 Tab） | `frontend/src/views/system/region/index.vue:1-160` |
| 接口 | `/regions/children` 逐级懒加载、`/regions/search` 关键词搜索（带 `has_route`）、`/regions/sync` 异步同步、`/sync/{job}/report` | `api/v1/system/region.py:280-479` |
| 同步日志表 | `sys_region_sync_log`（job_id/status/progress/新增/改动/待删/路由迁移/报告 JSON） | `migrate_route_module.sql:31-47` |
| CRUD | luohuo 的区域页面**不做手工增删改**，以在线同步为主 | 同上 |

## 3. luohuo 仓库/库区核查

- 在 luohuo 的 `backend/`、`frontend/` 全量检索 `库区/warehouse/storage_area/库房`：**仅备份 SQL 文件命中**，业务代码与模型中**不存在仓库/库区主数据表或字段**。
- 结论：luohuo 无同类概念；yunshu 按家伟裁决采用**路由表内文本字段**，不做主数据表。
- **未来升级成本（若要做主数据）**：需新增 `sys_warehouse_area(id, code, name, warehouse_id, dept_id, status...)`，在路由表增加 `warehouse_area_id` 并以名称做一次性匹配回填；列表/抽屉改下拉；打印模板改为取名称快照。属中等成本，且需处理同名库区。当前文本方案不阻塞升级。

## 4. yunshu 现状事实

| 项 | 事实 |
|---|---|
| `sys_dept` | 24 列：`id/parent_id/dept_name/dept_code/dept_type/dept_nature/is_leaf/allow_ship/allow_arrival/allow_prepaid_unpaid/allow_collect_unpaid_sign/leader/phone/address/sort_order/status/remark/create_by/create_time/update_by/update_time/deleted`；**只有 `address varchar(200)`，无省/市/区县字段** |
| 机构页面 | 列表 13 列（无地址列），12 个筛选项；抽屉含「机构地址」单行文本（`DeptFormDrawer.vue:91-93`）；有导出、无导入 |
| 菜单 | 已存在：`1104 路由管理 /system/route → system/route/index`、`1107 承运商管理 /system/carrier → system/carrier/index`、`1109 区划管理 /system/region → system/region/index`；**页面与表均未建** |
| 承运商 | **无 `sys_carrier` 表、无页面、无接口**（仅有菜单占位） |
| 区划 | **无 `sys_region` 表、无数据** |
| 数据权限 | 黑名单式已启用；豁免表 9 张（含 `sys_dept`）；新业务表需 `dept_id/create_by` + `fill` 注解 |

---

# 第二部分 · 设计方案

## a. 表拆分结论与两个 Tab 的职责边界

**结论：分两张表，统一页面两个 Tab。**

| Tab | 表 | 职责 |
|---|---|---|
| 路由管理（短驳/干线） | `sys_route` | 网点↔网点、网点↔分拨、分拨↔分拨的运输线路；含里程、时效、联系人、客服、库区 |
| 外转/直达 | `sys_route_transfer` | 按「适用网点 + 目的地行政区划」配置的直送/外转路由与默认承运商，完全对齐 luohuo `sys_route` |

理由：
1. 字段集差异大：短驳/干线有始发/到达网点、里程、时效、四类联系人、库区；外转/直达有目的地区划、粒度、承运商。合表会产生十余个长期 NULL 字段与互相矛盾的约束。
2. 唯一性口径不同：短驳/干线按「类型+始发+到达」；外转/直达按「适用网点+目的地区划」。
3. 页面本来就是两个 Tab，后端拆表对前端无感，后续独立演进成本最低。
4. 命名沿用 luohuo 的 `route_type` 语义会与短驳/干线冲突，故外转表用 `transfer_type`（1=直达，2=外转）。

## b. 路由管理页面

### b1. Tab「路由管理」（短驳/干线）列表列（19 列）

| # | 列 | 最小宽 | 对齐 | 筛选 | 排序 | 导出 |
|---|---|---|---|---|---|---|
| 1 | 序号 | 80 | 居中 | — | 否 | 否 |
| 2 | 路由编码 | 140 | 左 | 文本 | 是 | 是 |
| 3 | 路由类型 | 100 | 居中 | 下拉（短驳/干线） | 是 | 是 |
| 4 | 始发网点 | 150 | 左 | 机构下拉（网点/分拨） | 是 | 是 |
| 5 | 到达网点 | 150 | 左 | 机构下拉 | 是 | 是 |
| 6 | 线路名称 | 180 | 左 | 文本 | 是 | 是 |
| 7 | 到达省 | 100 | 左 | 省下拉 | 是 | 是 |
| 8 | 到达市 | 100 | 左 | 市下拉（随省联动） | 是 | 是 |
| 9 | 库区 | 120 | 左 | 文本 | 否 | 是 |
| 10 | 线路里程(km) | 110 | 右 | 数值区间 | 是 | 是 |
| 11 | 运输时效(h) | 110 | 右 | 数值区间 | 是 | 是 |
| 12 | 线路联系人 | 110 | 左 | 文本 | 否 | 是 |
| 13 | 联系电话 | 130 | 左 | 文本 | 否 | 是 |
| 14 | 线路客服 | 110 | 左 | 文本 | 否 | 是 |
| 15 | 客服电话 | 130 | 左 | 文本 | 否 | 是 |
| 16 | 状态 | 90 | 居中 | 启用/禁用 | 是 | 是 |
| 17 | 最后修改人 | 110 | 左 | 文本 | 是 | 是 |
| 18 | 最后修改时间 | 190 | 居中 | 日期区间 | 是 | 是 |
| 19 | 操作 | 200 | 居中 | — | 否 | 否 |

默认排序 `id asc`；工具条：新增 | 重置 / 导出 / 列设置 / 分页；分页 20/50/100。

### b2. Tab「路由管理」抽屉字段

| 字段 | 控件 | 必填 | 长度/校验 | 联动/默认 |
|---|---|---|---|---|
| 路由类型 `routeCategory` | 单选 | 是 | 1 短驳 / 2 干线，默认 1 | 切换时重算线路名称与必填项 |
| 始发网点 `originDeptId` | 机构树/下拉 | 是（待确认 Q2） | 仅 `dept_type` 4/5 且启用 | — |
| 到达网点 `destDeptId` | 机构树/下拉 | 是（待确认 Q2） | 仅 `dept_type` 4/5 且启用 | 选择后自动回填到达省/市 |
| 线路名称 `routeName` | 输入 | 是 | 2–100 字符 | 干线：未手工改过时自动生成「到达网点名+线路」；短驳：默认「始发-到达 短驳」 |
| 到达省/市 | 只读展示 | 自动 | 取到达网点机构省/市 | 到达网点变更即刷新 |
| 库区 `warehouseArea` | 输入 | 干线必填 | ≤100 字符 | 手工填写；打印标签直接取值 |
| 线路里程 `mileage` | 数字输入 | 干线必填 | 0.01–99999.99，2 位小数 | 单位 km |
| 运输时效 `transitHours` | 数字输入 | 干线必填 | 0.01–9999.99，2 位小数 | 单位小时（待确认 Q3） |
| 线路联系人 | 输入 | 干线必填 | ≤50 | — |
| 线路联系电话 | 输入 | 干线必填 | 手机或座机（`1[3-9]\d{9}` / `0\d{2,3}-?\d{7,8}`） | — |
| 线路客服 | 输入 | 否 | ≤50 | — |
| 线路客服电话 | 输入 | 否 | 同上 | — |
| 状态 | 单选 | 是 | 启用/禁用，默认启用 | — |
| 排序 | 数字 | 否 | 0–9999，默认 0 | — |
| 备注 | 文本域 | 否 | ≤500 | — |

**自动生成线路名称的时机**：① 到达网点选择/变更时，若用户未手工编辑过线路名称（dirty 标记为 false），立即生成「到达网点名 + 线路」；② 保存时若名称为空，再兜底生成一次。
**去重规则**：线路名称**不做全局唯一**（同目的地不同始发可同名）；名称重复时前端提示「已存在同名线路，是否继续保存」，不阻断。真正的唯一性在「路由类型 + 始发网点 + 到达网点」（deleted=0），保存时后端校验并提示重复。

### b3. Tab「外转/直达」列表列（12 列，对齐 luohuo）

序号 80 / 路由编码 140 / 路由名称 170 / 目的地全路径 180 / 级别 110 / 类型 100（直达/外转） / 适用网点 140 / 默认承运商 150 / 状态 90 / 排序 100 / 最后修改人 110 / 最后修改时间 190 / 操作 160。
筛选：编码/名称/目的地（文本），级别/类型/承运商/状态（下拉），修改时间（区间）。
抽屉：适用网点（新建时选、编辑只读）、目的地（区划搜索选择，显示全路径）、路由编码（自动生成只读）、路由名称（默认全路径，可改）、类型（直达/外转，默认外转）、默认承运商（仅外转可填）、状态、排序、备注。

## c. 区域管理页面

完全对齐 luohuo 的「同步控制台」定位（不做手工增删改）：

1. **版本信息卡**：当前版本、数据来源（init/online/file）、最近同步时间、远程最新版本、区划总数、状态。
2. **操作区**：「在线更新区划」（异步任务 + 进度条 + 轮询）、「刷新」。
3. **同步日志表**：任务ID、开始/结束时间、状态、新增/改动/待删/迁移数量、查看报告。
4. **报告弹窗**四个 Tab：新增、改动、编码迁移（含受影响路由数）、待删除。
5. 另提供「区划查询」抽屉/树：省→市→区县→乡级逐级懒加载 + 关键词搜索（供路由/机构选择器复用）。

字段即 `sys_region` 全部字段（见 DDL），页面只读展示，不允许手工编辑区划编码与层级。

## d. DDL 草案（本草案不执行）

> 版本规划：**V7** = 区划表 + 机构地址结构化；**V8** = 路由两张表（如确认承运商简表，一并放 V8）。

### V7-1 `sys_region`

```sql
CREATE TABLE `sys_region` (
  `code`        VARCHAR(12)  NOT NULL COMMENT '行政区划编码：省/市/区县采用 GB/T 2260，乡级采用统计局 9/12 位编码',
  `name`        VARCHAR(64)  NOT NULL COMMENT '区划名称（本级）',
  `parent_code` VARCHAR(12)  NOT NULL DEFAULT '0' COMMENT '上级区划编码；0=顶级（省级的上级）',
  `level`       TINYINT UNSIGNED NOT NULL COMMENT '层级：1=省 2=市 3=区县 4=乡级（乡镇/街道）',
  `full_name`   VARCHAR(200) NOT NULL DEFAULT '' COMMENT '全路径名称，如 广东省广州市白云区；用于路由名称默认值与展示',
  `source`      VARCHAR(32)  NOT NULL DEFAULT 'init' COMMENT '数据来源：init=首次导入 online=在线更新 file=离线文件',
  `version`     VARCHAR(12)  NOT NULL DEFAULT '' COMMENT '数据版本 YYYYMM；在线更新后刷新',
  `sync_time`   DATETIME     DEFAULT NULL COMMENT '最近一次同步时间',
  `status`      TINYINT(1)   NOT NULL DEFAULT 1 COMMENT '状态：1=有效 0=停用；历史区划保留不物理删除',
  `create_time` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`code`),
  KEY `idx_parent` (`parent_code`),
  KEY `idx_name` (`name`),
  KEY `idx_level` (`level`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci
  COMMENT='行政区划表：省/市/区县/乡级四级基础数据，平台级共享，不参与数据权限过滤';
```

### V7-2 `sys_region_sync_log`（与 luohuo 对齐）

```sql
CREATE TABLE `sys_region_sync_log` (
  `id`                   BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
  `job_id`               VARCHAR(32)  NOT NULL COMMENT '同步任务ID',
  `triggered_by`         BIGINT UNSIGNED DEFAULT NULL COMMENT '触发人用户ID',
  `started_at`           DATETIME     NOT NULL COMMENT '开始时间',
  `finished_at`          DATETIME     DEFAULT NULL COMMENT '结束时间',
  `status`               VARCHAR(16)  NOT NULL COMMENT '任务状态：pending/running/done/failed',
  `progress`             INT          NOT NULL DEFAULT 0 COMMENT '进度 0-100',
  `added_count`          INT          NOT NULL DEFAULT 0 COMMENT '新增区划数',
  `changed_count`        INT          NOT NULL DEFAULT 0 COMMENT '名称/路径变更数',
  `deleted_count`        INT          NOT NULL DEFAULT 0 COMMENT '待人工确认的删除数',
  `route_migrated_count` INT          NOT NULL DEFAULT 0 COMMENT '编码变更引发的路由迁移条数',
  `report_json`          MEDIUMTEXT   DEFAULT NULL COMMENT '差异明细 JSON',
  `error_msg`            VARCHAR(500) DEFAULT NULL COMMENT '失败原因',
  PRIMARY KEY (`id`),
  KEY `idx_job_id` (`job_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci
  COMMENT='区划同步任务日志：记录在线/离线更新过程与差异报告';
```

### V7-3 `sys_dept` 变更（新增省/市/区县）

```sql
ALTER TABLE `sys_dept`
  ADD COLUMN `province_code` VARCHAR(12) DEFAULT NULL COMMENT '省编码，引用 sys_region.code（level=1）',
  ADD COLUMN `province_name` VARCHAR(50) DEFAULT NULL COMMENT '省名称（冗余，列表与打印直接展示）',
  ADD COLUMN `city_code`     VARCHAR(12) DEFAULT NULL COMMENT '市编码，引用 sys_region.code（level=2）',
  ADD COLUMN `city_name`     VARCHAR(50) DEFAULT NULL COMMENT '市名称（冗余）',
  ADD COLUMN `district_code` VARCHAR(12) DEFAULT NULL COMMENT '区县编码，引用 sys_region.code（level=3）',
  ADD COLUMN `district_name` VARCHAR(50) DEFAULT NULL COMMENT '区县名称（冗余）',
  ADD KEY `idx_region` (`province_code`, `city_code`, `district_code`);
```

### V8-1 `sys_route`（短驳/干线）

```sql
CREATE TABLE `sys_route` (
  `id`                   BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
  `route_code`           VARCHAR(32)  NOT NULL COMMENT '路由编码：DB/GX + 6 位数字，创建后不可修改',
  `route_category`       TINYINT UNSIGNED NOT NULL COMMENT '路由类型：1=短驳 2=干线',
  `origin_dept_id`       BIGINT UNSIGNED DEFAULT NULL COMMENT '始发网点ID，引用 sys_dept.id；仅网点/分拨中心',
  `dest_dept_id`         BIGINT UNSIGNED DEFAULT NULL COMMENT '到达网点ID，引用 sys_dept.id；仅网点/分拨中心',
  `route_name`           VARCHAR(100) NOT NULL COMMENT '线路名称：干线默认“到达网点名+线路”，允许修改',
  `arrival_province_code` VARCHAR(12) DEFAULT NULL COMMENT '到达省编码：取到达网点机构省，冗余存储',
  `arrival_province_name` VARCHAR(50) DEFAULT NULL COMMENT '到达省名称',
  `arrival_city_code`    VARCHAR(12)  DEFAULT NULL COMMENT '到达市编码：取到达网点机构市',
  `arrival_city_name`    VARCHAR(50)  DEFAULT NULL COMMENT '到达市名称',
  `warehouse_area`       VARCHAR(100) DEFAULT NULL COMMENT '库区：干线必填，手工填写；打印标签直接取该值',
  `mileage`              DECIMAL(10,2) DEFAULT NULL COMMENT '线路里程（公里）；干线必填',
  `transit_hours`        DECIMAL(8,2)  DEFAULT NULL COMMENT '运输时效（小时）；干线必填',
  `contact_name`         VARCHAR(50)  DEFAULT NULL COMMENT '线路联系人；干线必填',
  `contact_phone`        VARCHAR(20)  DEFAULT NULL COMMENT '线路联系电话；干线必填',
  `service_name`         VARCHAR(50)  DEFAULT NULL COMMENT '线路客服姓名；选填',
  `service_phone`        VARCHAR(20)  DEFAULT NULL COMMENT '线路客服电话；选填',
  `status`               TINYINT(1)   NOT NULL DEFAULT 1 COMMENT '状态：1=启用 0=禁用',
  `sort_order`           INT          NOT NULL DEFAULT 0 COMMENT '排序号，升序',
  `remark`               VARCHAR(500) DEFAULT NULL COMMENT '备注',
  `dept_id`              BIGINT UNSIGNED NOT NULL COMMENT '归属机构ID：数据权限锚点，新增时自动填充',
  `create_by`            BIGINT UNSIGNED DEFAULT NULL COMMENT '创建人用户ID',
  `create_time`          DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_by`            BIGINT UNSIGNED DEFAULT NULL COMMENT '最后修改人用户ID',
  `update_time`          DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '最后修改时间',
  `deleted`              TINYINT(1)   NOT NULL DEFAULT 0 COMMENT '逻辑删除：0=未删除 1=已删除',
  PRIMARY KEY (`id`),
  KEY `idx_code` (`route_code`),
  KEY `idx_category_status` (`route_category`, `status`),
  KEY `idx_origin` (`origin_dept_id`),
  KEY `idx_dest` (`dest_dept_id`),
  KEY `idx_dept` (`dept_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci
  COMMENT='短驳/干线路由表：网点与分拨中心之间的运输线路及线路属性';
```

### V8-2 `sys_route_transfer`（外转/直达，对齐 luohuo）

```sql
CREATE TABLE `sys_route_transfer` (
  `id`               BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
  `route_code`       VARCHAR(32)  NOT NULL COMMENT '路由编码：区划编码_ZT（直达）/_WZ（外转），自动生成',
  `route_name`       VARCHAR(128) NOT NULL COMMENT '路由名称：默认区划全路径，可修改',
  `transfer_type`    TINYINT UNSIGNED NOT NULL COMMENT '类型：1=直达 2=外转，默认 2',
  `region_code`      VARCHAR(12)  NOT NULL COMMENT '目的地行政区划编码，引用 sys_region.code',
  `region_level`     TINYINT UNSIGNED NOT NULL COMMENT '目的地粒度：2=市 3=区县 4=乡级',
  `region_full_name` VARCHAR(200) NOT NULL COMMENT '目的地全路径名称（冗余）',
  `carrier_id`       BIGINT UNSIGNED DEFAULT NULL COMMENT '默认承运商ID；仅外转可填；需承运商主数据（见待确认 Q1）',
  `status`           TINYINT(1)   NOT NULL DEFAULT 1 COMMENT '状态：1=启用 0=停用',
  `sort_order`       INT          NOT NULL DEFAULT 0 COMMENT '排序号',
  `remark`           VARCHAR(500) DEFAULT NULL COMMENT '备注',
  `dept_id`          BIGINT UNSIGNED NOT NULL COMMENT '适用网点机构ID：路由按网点配置，同时作为数据权限锚点',
  `create_by`        BIGINT UNSIGNED DEFAULT NULL COMMENT '创建人用户ID',
  `create_time`      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_by`        BIGINT UNSIGNED DEFAULT NULL COMMENT '最后修改人用户ID',
  `update_time`      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '最后修改时间',
  `deleted`          TINYINT(1)   NOT NULL DEFAULT 0 COMMENT '逻辑删除：0=未删除 1=已删除',
  PRIMARY KEY (`id`),
  KEY `idx_region` (`region_code`),
  KEY `idx_type` (`transfer_type`),
  KEY `idx_carrier` (`carrier_id`),
  KEY `idx_dept` (`dept_id`),
  KEY `idx_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci
  COMMENT='外转/直达路由表：适用网点 + 目的地行政区划配置，完全对齐 luohuo sys_route';
```

### V8-3 `sys_carrier`（若家伟确认补承运商简表）

照 luohuo：`id, carrier_code, carrier_name, contact, phone, status, sort_order, remark, dept_id, create_by, create_time, update_by, update_time, deleted` + `idx_name/idx_status/idx_dept`。

### 区划数据导入

- 3 级（省/市/区县）约 **3,200 行**；含乡级约 **44,000 行**（34 省 / 340+ 市 / 2,900+ 区县 / 40,000+ 乡镇街道）。
- 不建议塞进 Flyway：以离线 JSON（或在线同步任务）一次性导入，`version=YYYYMM`，可重复执行按 `code` upsert。
- 来源：民政部/国家统计局区划代码，或 `modood/Administrative-divisions-of-China` 数据包（luohuo 同源）。

## e. 与数据权限的衔接

| 表 | 定位 | 是否豁免 | dept_id / create_by |
|---|---|---|---|
| `sys_region` | 平台基础数据（全机构共享） | **建议豁免**（加入豁免清单） | 建议只保留 `create_by` 审计；若严格按"新表必带"，可加 `dept_id` 固定为总部并在服务层显式赋值（豁免表不会触发 fill） |
| `sys_region_sync_log` | 平台任务日志 | **豁免** | 以 `triggered_by` 记录触发人 |
| `sys_route` | 业务配置（总部/网点维护线路） | **不豁免** | `dept_id` 归属（总部或始发网点）、`create_by`，实体加 `@TableField(fill=...)` |
| `sys_route_transfer` | 业务配置（按网点配置） | **不豁免** | `dept_id` = 适用网点，`create_by`，加 fill 注解 |
| `sys_carrier` | 业务主数据（若建） | **不豁免** | `dept_id` 归属、`create_by`，加 fill 注解 |

注意：`DataScopeMetaObjectHandler` 对豁免表直接跳过填充，因此区域表的 `dept_id` 必须由服务层显式写入（见 Q7）。

## f. 需要家伟确认的待确认项

| # | 问题 | 建议 | 理由 |
|---|---|---|---|
| Q1 | 外转/直达依赖承运商，但 yunshu 只有菜单、无 `sys_carrier` 表与页面 | **同轮补简版承运商表+页面**（字段照 luohuo） | 否则外转路由的默认承运商无法选择，"完全按 luohuo"落不了地 |
| Q2 | 「始发网点、到达网点可选」是"可选填"还是"可从下拉选择" | 理解为**下拉可选值**，且两者**必填** | 无始发/到达的线路无业务意义；若确实允许为空，唯一性校验需特殊处理 |
| Q3 | 运输时效单位：小时还是天 | **小时（DECIMAL 8,2）** | 短驳按小时更精确；干线跨天可用 48 小时表示，展示时可折算 |
| Q4 | 干线必填范围：库区/里程/时效/联系人/联系电话，客服是否必填 | 库区、里程、时效、联系人、联系电话**必填**；客服与客服电话**选填** | 客服常与联系人同一人，强制必填会产生重复录入 |
| Q5 | 到达省/市是否只读自动带出，能否手工覆盖 | **只读自动带出，不允许覆盖** | 需求明确"取值来自到达网点机构的地址"，手工改会造成与机构不一致 |
| Q6 | 区域管理是否需要手工维护区划（增删改） | **不做手工增删改**，只做在线更新/离线导入 + 查询 | 完全按 luohuo；手工改区划编码会破坏路由与机构引用 |
| Q7 | 区域表是否严格带 `dept_id` 并配 fill | **加 `create_by` 审计、加 `dept_id`（固定总部）由服务层显式赋值，但豁免过滤** | 区划是全机构共享基础数据，纳入过滤会导致网点看不到省市区县 |
| Q8 | 路由名称重复是否允许 | **允许同名，仅提示不阻断**；唯一性放在「类型+始发+到达」 | 不同始发到同一目的地可能同名，强制唯一会阻碍录入 |
| Q9 | 机构省/市/区县必填范围 | **分拨中心与网点必填**；总部/公司/部门选填 | 路由与打印强依赖分拨/网点的地区信息；总部无实际作业地址 |
| Q10 | 区划数据是否含乡级（4 级） | **先导入 3 级**，乡级按需二期补充 | 3 级 3,200 行足够路由与机构使用；乡级 4 万行增加维护与同步成本 |
| Q11 | 路由菜单已存在但无按钮权限点 | 沿用现状（yunshu 未做按钮级鉴权），不新增按钮种子 | 避免引入与现有权限体系不一致的占位数据 |
| Q12 | 迁移版本拆分 | **V7 区划 + 机构地址；V8 路由（+承运商）** | DDL 体积与回滚边界清晰；区划导入独立于业务表 |

---

# 第三部分 · 冲突与缺口（不自行拍板）

1. **库区无主数据（已裁决）**：按文本字段实现；升级主数据时需新增表、路由表加 `warehouse_area_id` 并按名称回填，打印模板改为取名称快照。当前方案不阻塞。
2. **承运商缺失 vs 外转路由**：luohuo 外转路由强依赖 `sys_carrier`；yunshu 只有菜单占位。若本轮不建承运商，外转 Tab 的「默认承运商」只能留空或改文本，与"完全按 luohuo"不符 → 见 Q1。
3. **省市区县 vs 现有 address**：建议 `province/city/district` 六字段（code+name）结构化，`address` 保留为详细地址（街道门牌）；历史数据当前仅总部 1 条，采用人工补录，不做文本解析回填（解析准确率与维护成本不可控）。
4. **到达省/市 vs 到达网点变更**：路由保存的是快照；若机构后续改地址，历史路由不会自动更新。建议保存时快照 + 提供"按机构重算"维护入口（待确认是否本期做）。
5. **区域管理页面定位**：luohuo 是"同步控制台"而非 CRUD。若家伟期望"区域管理=手工维护区划"，与 luohuo 实现不一致 → 见 Q6。
6. **"所有机构信息务必识别省市区县"**：总部/公司/部门是否需要强制？本文建议仅分拨/网点必填（Q9），否则现有总部数据与后续录入都会被卡住。

---

# 第四部分 · 对现有页面的影响面（供排期参考，本轮不动）

| 层 | 影响 |
|---|---|
| 数据库 | `sys_dept` 新增 6 列 + 1 索引；新增 `sys_region`、`sys_region_sync_log`（V7）；新增 `sys_route`、`sys_route_transfer`（V8） |
| 后端 | 机构：Entity/VO/SaveRequest/Query/Service（省市区联动校验、按省市筛选、导出字段）；新增区划模块（查询/树/同步）；新增路由两模块 |
| 前端机构页 | 抽屉加省/市/区县三级联动（替换现有单行地址为"区划 + 详细地址"）；列表新增「省/市/区县」列（建议合并一列「所在地区」，最小宽 180）；筛选新增省/市；导出列同步；列设置新增开关 |
| 前端其他 | 新增路由页（两 Tab）、区域页（同步控制台）；区划选择器组件供机构/路由复用 |
| 数据权限 | 路由/承运商表需 `dept_id/create_by` + fill；区域表豁免 |
| 菜单 | 1104/1107/1109 已存在，仅需补前端页面与后端接口，无需新增菜单 |
