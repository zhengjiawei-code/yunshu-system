# 路由管理 + 区域管理 + 承运商管理 · 设计方案 v2.1（修订稿）

> 状态：**仅设计。未写业务代码、未建表、未执行任何 DDL、未修改任何现有文件。**
> 基线：v2.0。本版落地三项裁决：适用公司多选、总部授权恢复动态全量、开单直达优先；**R1 经授权代拍为方案 A**（路由/承运商/授权/适用公司全部豁免，服务层显式计算可见性）。
> 修订说明：本文件替代同版本"快照稿"。快照方案作废，`company_dept_id=0` 的"全部分公司"标记行恢复为最终设计。

---

## 0. 与 v2.0 的差异与裁决落地

| # | 裁决 | v2.1 落地 |
|---|---|---|
| 1 | 末端网点适用公司：多选 | `sys_dept_apply_company` 多对多；机构抽屉多选必填，仅 `is_leaf=1` 显示 |
| 2 | 总部"默认授权所有分公司"恢复动态方案 | 总部创建路由写入 `company_dept_id=0` 的标记行（`grant_source=3`），新增分公司自动生效，**不写快照**；取消"未授权公司数"提醒；列表用「授权范围」列展示「全部」标识 |
| 3 | 开单显示：直达优先、首条推荐、可切换 | 排序与交互按 v2.0 规则细化并固定 |
| 4 | R1：数据权限处理 | **采用方案 A**：`sys_route`、`sys_route_transfer`、`sys_carrier`、`sys_route_company`、`sys_dept_apply_company` 全部豁免行级过滤；可见性由服务层「管理范围 + 授权关系」显式计算；同时写入四条硬性要求（见第四部分） |

对 v2.0 的修正：
- 不做授权快照，不统计"未授权分公司数"，不引入新建分公司后的授权缺口提示。
- 授权表 `company_dept_id=0` 合法，表示全部分公司；数据库层不再用 `CHECK > 0` 拦截。
- 总部创建的路由在列表上必须有明确的「全部」标签，避免与"逐家授权"混淆。

---

# 第一部分 · 核查事实（只读）

## 1.1 机构与公司判定

| 项 | 事实 | 位置 |
|---|---|---|
| 机构类型 | 1=总部 2=公司 3=部门 4=分拨中心 5=网点 | `SysDept.java:29-30` |
| 末端标记 | `is_leaf` Boolean，仅网点可为 true | `SysDept.java:35-37` |
| 主机构 | `sys_user.dept_id`；额外可登录机构在 `sys_user_org` | `SysUser.java:34` |
| 超管 | `SCOPE_ALL`，保留主机构用于新增填充 | `DataScopeWebInterceptor.java:59-63` |

## 1.2 机构接口与页面

| 项 | 事实 | 位置 |
|---|---|---|
| 下拉接口 | `GET /depts/options?type=` 只按启用状态与单一类型过滤，**不做范围过滤** | `SysDeptController.java:75-81`、`SysDeptServiceImpl.java:348-356` |
| 机构列表 | 13 列，含"是否末端"，无"适用公司" | `frontend/src/views/system/org/index.vue:116-469` |
| 抽屉 | 12 项，无省市区县、无适用公司 | `DeptFormDrawer.vue:11-116` |

## 1.3 数据权限机制

- `sys_dept` 豁免（`DataScopeTables.java:13-23`），机构列表与下拉当前不受行级过滤。
- 拦截器对语句中**每一张非豁免表**逐表追加条件，join 表同样处理（`DataScopeSqlHandler.java:29-63`）。
- 无 `dept_id` 的表若不豁免，会被追加不存在的列，SQL 直接报错——授权表、适用公司表**无论 R1 如何选择都必须豁免**。
- 现行规范明确"业务表一律不得豁免"（`data-permission-spec.md:35`）；本版对路由/承运商豁免属于规范变更，必须登记并评审（见 4.4）。

## 1.4 luohuo 对照

- 机构仅 1 公司/2 部门/3 网点，无分拨中心、无 `is_leaf`（`models/organization.py:25`）。
- 无路由授权、无适用公司、无末端网点共享；本版新增内容无现成实现可抄。

## 1.5 现状空白

- yunshu 后端只有 `auth`、`system` 两模块，开单模块未建。
- 路由/承运商/区划表与页面均未建，**无历史数据需回填**；菜单 1104/1107/1109 已存在。

---

# 第二部分 · 方案

## 2.1 术语

| 术语 | 定义 |
|---|---|
| 总部 | `dept_type=1` |
| 分公司 | `dept_type=2`；多级嵌套取最近一级 |
| 末端网点 | `dept_type=5` 且 `is_leaf=1`，可被多个分公司共用 |
| 用户所属公司 | 主机构向上（含自身）最近 `dept_type=2`；主机构为总部或路径无公司 → 总部 |
| 管理范围 | 总部=全部；分公司=本公司子树 ∪ 适用本公司的末端网点 |
| 授权全部 | 授权表中 `company_dept_id=0` 的标记行，动态覆盖所有现有及后续新建分公司 |

## 2.2 关系模型

```
sys_dept
  └── sys_dept_apply_company   末端网点 ↔ 适用分公司（多对多）
sys_route / sys_route_transfer
  ├── owner_company_id         创建公司（归属 + 编辑/删除权限）
  └── sys_route_company        路由 ↔ 授权分公司（自动 / 手动 / 全部）
```

### 2.2.1 `sys_dept_apply_company`（末端网点适用分公司）

- 字段：`leaf_dept_id`、`company_dept_id`、`create_by`、`create_time`。
- 多选；`(leaf_dept_id, company_dept_id)` 唯一；仅 `is_leaf=1` 产生记录；`company_dept_id` 必须是启用的 `dept_type=2`。
- 硬删除、diff 维护（新增缺失、删除多余）。
- 决定管理范围并集与机构选择器可见性。

### 2.2.2 `sys_route_company`（路由授权分公司）

- 字段：`route_scope`（1 短驳/干线、2 外转/直达）、`route_id`、`company_dept_id`、`grant_source`、`grant_by`、`grant_time`、`remark`。
- `company_dept_id=0` 表示**全部分公司**（含后续新增），是动态标记，不展开成公司列表。
- `(route_scope, route_id, company_dept_id)` 唯一；0 号行天然只有一条。
- 授权判定：`exists(company_dept_id = 本公司 OR company_dept_id = 0)`。
- `grant_source`：1=创建自动（分公司创建/同键补授权）2=手动（总部指定公司）3=总部全部（0 号行）。

### 2.2.3 路由表新增列

`sys_route`、`sys_route_transfer` 均增加 `owner_company_id`（创建公司），用于归属展示、编辑权限与删除权限；保留 `dept_id/create_by` 作为审计（方案 A 下不参与过滤）。

## 2.3 授权流程（动态全部）

### 2.3.1 创建

```
创建请求（创建人所属公司 C）
  ├─ 按业务键查 deleted=0
  │   ├─ 不存在
  │   │    ├─ C=总部：新建路由 + 授权行(0, source=3)          // 全部分公司，动态生效
  │   │    └─ C=分公司：新建路由 + 授权行(C, source=1)
  │   └─ 已存在：复用，不覆盖公共字段
  │        ├─ C=总部：已有 0 号行则跳过；否则补 0 号行(source=3)
  │        └─ C=分公司：已有本公司行或 0 号行则跳过；否则补(C, source=1)
  └─ 返回：新建/复用、补授权结果、被忽略的差异字段提示
```

规则：
- 总部创建即"全部"，不查询、不写入当时的分公司列表；后续新建分公司自动获得授权，无需数据迁移。
- 分公司创建只授权本公司；同键复用补本公司授权。
- 同键复用的差异字段（名称/库区/里程/承运商等）不覆盖，返回提示（R8）。
- 并发：业务键应用层校验 + 事务内 `SELECT ... FOR UPDATE`；唯一冲突重试一次。

### 2.3.2 新增分公司后的表现

> 适用范围：本节仅描述**外转/直达（Tab2）**。短驳/干线（Tab1）是网点之间的物理运输段，不存在跨公司共用，**不建授权关系**，按创建公司归属可见（总部可见全部）。

| 场景 | 表现 |
|---|---|
| 总部创建的路由 | 新分公司**自动可用**（0 号行），路由列表、详情、开单匹配立即可见，无需补授权 |
| 分公司创建的路由 | 新分公司默认不可见、不可用；需总部手动/批量授权后生效 |
| 总部路由列表 | 「授权范围」列显示「全部」标签，无可点击的缺口提醒 |
| 分公司被停用 | 0 号行不受影响；停用公司自身无法登录，启用后自动恢复 |
| 分公司被删除 | 0 号行不受影响；需清理其显式授权行与适用关系（R9） |
| 停用的路由 | 不参与列表/开单匹配，恢复启用后按原授权关系生效 |

### 2.3.3 取消路由 vs 取消授权

| 动作 | 语义 | 允许条件 | 结果 |
|---|---|---|---|
| 取消路由 | 路由本体不可用，影响所有公司 | 除本公司外无其他授权行，且不存在 0 号行 | 逻辑删除路由 + 删除全部授权行 |
| 取消授权 | 本公司退出使用 | 总是允许（总部 0 号行走"取消全部授权"） | 删除对应授权行；若无任何授权行 → 路由自动 `status=0` |

拦截文案（逐字）：
> **路由被其他公司使用，不能取消，取消授权即可**

补充：
- 总部删除同样受约束；总部需先取消 0 号行或回收其他公司授权，不做强制删除。
- 分公司取消路由时若存在 0 号行 → 视为其他公司可能使用，按拦截处理。
- 总部"取消全部授权"删除 0 号行；若此时还有其他显式授权行，路由仍可用；若一行不剩 → 自动停用。

### 2.3.4 手动/批量授权

> 仅外转/直达（Tab2）提供授权；短驳/干线（Tab1）工具栏与行操作均无「授权」「批量授权」入口，授权接口对 `route_scope=1` 直接拒绝且不写任何授权行。

- 入口：外转/直达列表行操作「授权」、工具条「批量授权」；仅总部可见（R4 待确认）。
- 弹窗：路由摘要、当前授权列表（分公司/来源/授权人/时间/取消授权）、添加授权多选（启用公司、排除已有）。
- 若存在 0 号行：顶部提示「已授权全部分公司（动态，含后续新增）」；隐藏"添加授权"；保留「取消全部授权」与可选的「转为按公司授权」（转为显式时按当时公司列表写入，需二次确认，非默认路径）。
- 批量：N 条路由 × M 家公司笛卡尔积插入，已存在跳过；返回"成功 X 条，跳过 Y 条"；单次上限 50×50。
- 来源展示：创建自动 / 手动 / 总部全部，便于审计。

### 2.3.5 「同样路由」判定键（R2 待确认）

| 路由域 | 业务键 |
|---|---|
| 短驳/干线 | `route_category + origin_dept_id + dest_dept_id` |
| 外转/直达 | `apply_dept_id + region_code + transfer_type` |

## 2.4 路由列表的「授权全部」标识（仅外转/直达 Tab2）

- Tab1 短驳/干线不显示「授权范围」列，仅保留「创建公司」用于归属识别。
- Tab2 新增列「授权范围」：
  - 存在 `company_dept_id=0` → 显示主色浅底或中性灰标签「全部」（禁止亮红亮绿），Tooltip："创建时授权全部分公司，新增分公司自动生效"；
  - 否则显示「N 家」（按真实公司授权行计数）。
- 筛选「授权范围」：全部 / 部分（不再提供"未授权公司数"筛选）。
- 分公司视角：列表只显示已授权给本公司的路由（含 0 号行覆盖），「授权范围」列可隐藏，避免暴露他公司授权规模。
- 不做新建分公司后的缺口提醒，不引入待办模块。

## 2.5 管理范围接口（独立于数据权限）

```
GET /api/v1/system/depts/manage-scope?types=4,5&keyword=
```

- 总部（主机构 type=1、超管、或向上先到总部）：返回全部启用机构。
- 分公司：主机构向上最近 `dept_type=2` → 本公司子树 ∪ 适用本公司的末端网点。
- 多级公司取最近一级；链断裂/成环/主机构为空 → 返回空（fail-closed）。
- 只返回 `status=1`、`deleted=0`；`types=4,5` 不返回公司节点；节点上限 5000。
- 客户端传 `companyId` 一律忽略。
- 返回 `{scopeType, companyId, companyName, tree, sharedLeaves}`，前端分组展示"本公司机构 / 共用末端网点"。
- **服务端强制**：路由新建/编辑时，始发/到达/适用网点必须在当前管理范围内，不能只靠前端限制。

## 2.6 机构管理改造

- 抽屉新增「适用公司」：仅 `is_leaf=1` 显示，多选必填（至少 1 家）。
- 分公司管理员默认本公司（建议仅可选本公司，R5）；总部管理员无默认、必须选择。
- `is_leaf` 由 true 改 false：提示将清除适用关系，确认后删除；删除机构时同步清理关系（被路由引用则拦截）。
- 列表新增「适用公司」列（非末端显示 `-`）与筛选。
- 选择器统一走 `/depts/manage-scope`；旧 `/depts/options` 保留但标注"返回全量"的遗留风险。

## 2.7 开单侧路由匹配（裁决 3）

### 2.7.1 候选集

1. `status=1`、`deleted=0`；
2. 已授权给开单员所属公司（`company_dept_id=本公司 OR 0`）；
3. 总部开单员不做授权过滤（可用全部启用路由，R6 待确认）；
4. 始发/到达机构启用。

### 2.7.2 匹配

- 目的地是网点：授权短驳/干线图上从始发到目的地的路径拼接（最多 5 跳、无环、最多 50 条候选）。
- 目的地是区划：外转/直达按编码精确匹配，未命中逐级回退（乡级→区县→市）；同时尝试到该区划内网点的网络路径。
- 始发与目的地直连时直接作为候选。

### 2.7.3 排序与交互

排序顺序：
1. **直达路由 > 网络内完整链路 > 外转路由**；
2. 目的地匹配深度：乡级 > 区县 > 市；
3. 总时效（各段 `transit_hours` 求和，空值视为无穷大）；
4. 总里程；
5. 跳数；
6. `sort_order`、`id`。

开单页交互：
- 默认选中第一条并标记「推荐」；
- 候选 > 1 时显示"切换路由"下拉，展示编码/名称/类型/时效/里程/跳数；
- 排序键完全一致时取 `id` 最小，保证结果稳定；
- 无候选：阻断开单，文案"当前公司未授权到该目的地的路由，请联系总部授权或新增路由"。

### 2.7.4 节点契约

匹配返回有序节点（`routeScope/routeId/routeCode/category/fromDeptId/toDeptId/contact/transitHours/mileage/warehouseArea/carrierId`）；开单模块据此生成轨迹节点与路由快照；打印标签取干线节点的 `warehouse_area`。快照表由开单模块设计（R9）。

## 2.8 页面变更清单

| 页面 | 变更 |
|---|---|
| 机构管理 | 适用公司列 + 筛选 + 抽屉多选 + 关系维护 |
| 路由管理（两 Tab） | **Tab1 短驳/干线**：工具栏仅「新增路由 / 重置 / 导出 / 列设置 / 分页」，行操作仅「编辑 / 删除」，列含「创建公司」、不含「授权范围」，无任何授权入口；**Tab2 外转/直达**：新增「创建公司」「授权范围」列（「全部」标签），行操作「授权」，工具条「批量授权」 |
| 承运商管理 | 无授权概念；可见性由服务层按管理范围过滤（方案 A） |
| 区域管理 | 无变化 |
| 开单（未建） | 消费匹配接口，直达优先、首条推荐、可切换 |

---

# 第三部分 · 最终 DDL 草案（不执行，待审核）

> **V7** = 区划 + 机构地址结构化 + 末端网点适用分公司；**V8** = 承运商 + 路由两表 + 路由授权。
> 字符集/排序规则与现有表一致：`utf8mb4 / utf8mb4_0900_ai_ci`。
> 本 DDL 按 R1=方案 A 定稿；豁免属运行期配置，不改变表结构。

## V7-1 `sys_region`

```sql
CREATE TABLE `sys_region` (
  `code`        VARCHAR(12)  NOT NULL COMMENT '行政区划编码（主键）：省市县为 GB/T 2260 六位，乡级为统计局 9/12 位',
  `name`        VARCHAR(64)  NOT NULL COMMENT '区划名称（本级）',
  `parent_code` VARCHAR(12)  NOT NULL DEFAULT '0' COMMENT '上级区划编码；省级固定为 0',
  `level`       TINYINT UNSIGNED NOT NULL COMMENT '层级：1=省 2=市 3=区县 4=乡级',
  `full_name`   VARCHAR(200) NOT NULL DEFAULT '' COMMENT '全路径名称，如 广东省广州市白云区；服务端按父子链计算',
  `source`      VARCHAR(16)  NOT NULL DEFAULT 'offline' COMMENT '数据来源：offline=离线JSON online=在线同步 init=初始化',
  `version`     VARCHAR(12)  NOT NULL DEFAULT '' COMMENT '数据版本，如 2025Q4 或 YYYYMM',
  `sync_time`   DATETIME     DEFAULT NULL COMMENT '最近一次导入/同步时间',
  `status`      TINYINT(1)   NOT NULL DEFAULT 1 COMMENT '状态：1=有效 0=停用（历史区划保留，不物理删除）',
  `create_time` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`code`),
  KEY `idx_parent_code` (`parent_code`),
  KEY `idx_name` (`name`),
  KEY `idx_level` (`level`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci
  COMMENT='行政区划表：省/市/区县/乡级基础数据，平台级共享';
```

索引/约束理由：
- `code` 主键：区划编码天然唯一且稳定，直接作为业务引用键。
- `idx_parent_code`：逐级懒加载 `parent_code=? AND status=1`。
- `idx_name`：关键词搜索；`idx_level`：按层级出下拉。
- 三级 3,200 行、四级 4 万行，无需更多组合索引。

## V7-2 `sys_region_sync_log`

```sql
CREATE TABLE `sys_region_sync_log` (
  `id`              BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
  `job_id`          VARCHAR(32)  NOT NULL COMMENT '任务ID（UUID），用于幂等与报告查询',
  `trigger_type`    VARCHAR(16)  NOT NULL COMMENT '触发方式：offline=离线JSON online=在线同步',
  `triggered_by`    BIGINT UNSIGNED DEFAULT NULL COMMENT '触发人用户ID',
  `file_name`       VARCHAR(200) DEFAULT NULL COMMENT '离线导入文件名（在线同步为空）',
  `data_version`    VARCHAR(12)  DEFAULT NULL COMMENT '本次导入的数据版本',
  `started_at`      DATETIME     NOT NULL COMMENT '开始时间',
  `finished_at`     DATETIME     DEFAULT NULL COMMENT '结束时间',
  `status`          VARCHAR(16)  NOT NULL COMMENT '状态：pending/running/success/failed/partial',
  `progress`        INT          NOT NULL DEFAULT 0 COMMENT '进度百分比 0-100',
  `total_count`     INT          NOT NULL DEFAULT 0 COMMENT '文件/远端总条数',
  `added_count`     INT          NOT NULL DEFAULT 0 COMMENT '新增条数',
  `changed_count`   INT          NOT NULL DEFAULT 0 COMMENT '变更条数',
  `unchanged_count` INT          NOT NULL DEFAULT 0 COMMENT '无变化条数',
  `disabled_count`  INT          NOT NULL DEFAULT 0 COMMENT '待停用（文件中缺失）条数',
  `report_json`     MEDIUMTEXT   DEFAULT NULL COMMENT '差异明细 JSON：新增/改动/待停用/校验失败',
  `error_msg`       VARCHAR(500) DEFAULT NULL COMMENT '失败原因',
  `create_time`     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_job_id` (`job_id`),
  KEY `idx_started_at` (`started_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci
  COMMENT='区划导入/同步任务日志：记录过程、差异与可追溯报告';
```

索引/约束理由：
- `uk_job_id`：任务唯一，防重复建任务与重复写报告。
- `idx_started_at`：日志列表按开始时间倒序分页。
- 数量字段用于导入对账（总数=新增+改动+无变化+失败）。

## V7-3 `sys_dept` 增列

```sql
ALTER TABLE `sys_dept`
  ADD COLUMN `province_code` VARCHAR(12) DEFAULT NULL COMMENT '省编码，引用 sys_region.code（level=1）',
  ADD COLUMN `city_code`     VARCHAR(12) DEFAULT NULL COMMENT '市编码，引用 sys_region.code（level=2）',
  ADD COLUMN `district_code` VARCHAR(12) DEFAULT NULL COMMENT '区县编码，引用 sys_region.code（level=3）',
  ADD KEY `idx_region` (`province_code`, `city_code`, `district_code`);
```

索引/约束理由：
- 只存 code 不存名称，区划改名统一 join，避免双写漂移。
- 组合索引支撑机构按省/市筛选；最左前缀 `province_code` 可单独使用。
- 不加物理外键，与现有风格一致，允许区划导入晚于机构录入。

## V7-4 `sys_dept_apply_company`

```sql
CREATE TABLE `sys_dept_apply_company` (
  `id`              BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
  `leaf_dept_id`    BIGINT UNSIGNED NOT NULL COMMENT '末端网点ID，引用 sys_dept.id，要求 is_leaf=1',
  `company_dept_id` BIGINT UNSIGNED NOT NULL COMMENT '适用分公司ID，引用 sys_dept.id，要求 dept_type=2 且启用',
  `create_by`       BIGINT UNSIGNED DEFAULT NULL COMMENT '创建人用户ID（豁免表不触发自动填充，服务层显式写入）',
  `create_time`     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_leaf_company` (`leaf_dept_id`, `company_dept_id`),
  KEY `idx_company` (`company_dept_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci
  COMMENT='末端网点适用分公司关系表：支撑共用末端网点跨分公司可见与选择';
```

索引/约束理由：
- `uk_leaf_company`：同一网点对同一公司唯一，重复提交幂等。
- `idx_company`：管理范围并集查询"适用本公司的末端网点"。
- 关系硬删除，无软删列，避免唯一键无法重建；无更新字段。

## V8-1 `sys_carrier`

```sql
CREATE TABLE `sys_carrier` (
  `id`           BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
  `carrier_code` VARCHAR(32)  NOT NULL COMMENT '承运商编码，机构内唯一（应用层校验）',
  `carrier_name` VARCHAR(64)  NOT NULL COMMENT '承运商名称',
  `contact`      VARCHAR(32)  NOT NULL COMMENT '联系人',
  `phone`        VARCHAR(20)  NOT NULL COMMENT '联系电话',
  `status`       TINYINT(1)   NOT NULL DEFAULT 1 COMMENT '状态：1=启用 0=停用',
  `sort_order`   INT          NOT NULL DEFAULT 0 COMMENT '排序号，升序',
  `remark`       VARCHAR(500) DEFAULT NULL COMMENT '备注',
  `dept_id`      BIGINT UNSIGNED NOT NULL COMMENT '归属机构ID：可见性锚点，新增时按当前主机构写入',
  `create_by`    BIGINT UNSIGNED DEFAULT NULL COMMENT '创建人用户ID',
  `create_time`  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_by`    BIGINT UNSIGNED DEFAULT NULL COMMENT '最后修改人用户ID',
  `update_time`  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '最后修改时间',
  `deleted`      TINYINT(1)   NOT NULL DEFAULT 0 COMMENT '逻辑删除：0=未删除 1=已删除',
  PRIMARY KEY (`id`),
  KEY `idx_code` (`carrier_code`),
  KEY `idx_name` (`carrier_name`),
  KEY `idx_status` (`status`),
  KEY `idx_dept` (`dept_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci
  COMMENT='承运商表：外转/直达路由的默认承运商主数据';
```

索引/约束理由：
- 不建 DB 唯一索引：逻辑删除后同编码无法重建；应用层校验 `dept_id + carrier_code`（`deleted=0`）唯一。
- `idx_code/idx_name` 支撑筛选与路由下拉；`idx_dept` 支撑服务层可见性过滤。
- 方案 A 下本表豁免，页面可见性由服务层按管理范围显式追加 `dept_id` 条件。

## V8-2 `sys_route`（短驳/干线）

```sql
CREATE TABLE `sys_route` (
  `id`               BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
  `route_code`       VARCHAR(32)  NOT NULL COMMENT '路由编码：短驳 DB/干线 GX + 6 位流水，创建后不可改',
  `route_category`   TINYINT UNSIGNED NOT NULL COMMENT '路由类型：1=短驳 2=干线',
  `origin_dept_id`   BIGINT UNSIGNED NOT NULL COMMENT '始发网点ID，引用 sys_dept.id',
  `dest_dept_id`     BIGINT UNSIGNED NOT NULL COMMENT '到达网点ID，引用 sys_dept.id',
  `route_name`       VARCHAR(100) NOT NULL COMMENT '线路名称：干线默认“到达网点名+线路”，允许修改',
  `warehouse_area`   VARCHAR(100) DEFAULT NULL COMMENT '库区：干线必填，手工填写；打印标签直接取该值',
  `mileage`          DECIMAL(10,2) DEFAULT NULL COMMENT '线路里程（公里），2 位小数；干线必填',
  `transit_hours`    DECIMAL(10,2) DEFAULT NULL COMMENT '运输时效（小时），2 位小数；干线必填',
  `contact_name`     VARCHAR(50)  DEFAULT NULL COMMENT '线路联系人；干线必填',
  `contact_phone`    VARCHAR(20)  DEFAULT NULL COMMENT '线路联系电话；干线必填',
  `service_name`     VARCHAR(50)  DEFAULT NULL COMMENT '线路客服姓名；选填',
  `service_phone`    VARCHAR(20)  DEFAULT NULL COMMENT '线路客服电话；选填',
  `owner_company_id` BIGINT UNSIGNED NOT NULL COMMENT '创建公司ID（总部或分公司）：归属展示、编辑与删除权限',
  `status`           TINYINT(1)   NOT NULL DEFAULT 1 COMMENT '状态：1=启用 0=禁用',
  `sort_order`       INT          NOT NULL DEFAULT 0 COMMENT '排序号，升序',
  `remark`           VARCHAR(500) DEFAULT NULL COMMENT '备注',
  `dept_id`          BIGINT UNSIGNED NOT NULL COMMENT '归属机构ID（创建人主机构）：审计留痕',
  `create_by`        BIGINT UNSIGNED DEFAULT NULL COMMENT '创建人用户ID',
  `create_time`      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_by`        BIGINT UNSIGNED DEFAULT NULL COMMENT '最后修改人用户ID',
  `update_time`      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '最后修改时间',
  `deleted`          TINYINT(1)   NOT NULL DEFAULT 0 COMMENT '逻辑删除：0=未删除 1=已删除',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_route_code` (`route_code`),
  KEY `idx_business_key` (`route_category`, `origin_dept_id`, `dest_dept_id`),
  KEY `idx_owner_company` (`owner_company_id`),
  KEY `idx_category_status` (`route_category`, `status`),
  KEY `idx_origin` (`origin_dept_id`),
  KEY `idx_dest` (`dest_dept_id`),
  KEY `idx_name` (`route_name`),
  KEY `idx_dept` (`dept_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci
  COMMENT='短驳/干线路由表：网点与分拨中心之间的运输线路及线路属性';
```

索引/约束理由：
- `idx_business_key`：同键查重与补授权的主路径；不建 DB 唯一索引，避免逻辑删除后无法重建，应用层保证唯一。
- `idx_owner_company`：分公司"我创建的路由"、归属展示、编辑/删除权限判定。
- `uk_route_code`：编码永不复用，唯一索引兜底并发重号。
- `idx_category_status/idx_origin/idx_dest/idx_name/idx_dept`：列表筛选、线路反查、审计查询。
- 方案 A 下本表豁免，所有列表/详情/导出/匹配均由服务层显式追加授权条件，详见第四部分。

## V8-3 `sys_route_transfer`（外转/直达）

```sql
CREATE TABLE `sys_route_transfer` (
  `id`               BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
  `route_code`       VARCHAR(32)  NOT NULL COMMENT '路由编码：区划编码_ZT（直达）/_WZ（外转），自动生成',
  `route_name`       VARCHAR(128) NOT NULL COMMENT '路由名称：默认区划全路径，可修改',
  `transfer_type`    TINYINT UNSIGNED NOT NULL COMMENT '类型：1=直达 2=外转',
  `apply_dept_id`    BIGINT UNSIGNED NOT NULL COMMENT '适用网点ID，引用 sys_dept.id；业务字段',
  `region_code`      VARCHAR(12)  NOT NULL COMMENT '目的地行政区划编码，引用 sys_region.code',
  `region_level`     TINYINT UNSIGNED NOT NULL COMMENT '目的地粒度：2=市 3=区县 4=乡级',
  `region_full_name` VARCHAR(200) NOT NULL COMMENT '目的地全路径名称（展示用）',
  `carrier_id`       BIGINT UNSIGNED DEFAULT NULL COMMENT '默认承运商ID；仅外转可填',
  `owner_company_id` BIGINT UNSIGNED NOT NULL COMMENT '创建公司ID（总部或分公司）：归属展示、编辑与删除权限',
  `status`           TINYINT(1)   NOT NULL DEFAULT 1 COMMENT '状态：1=启用 0=停用',
  `sort_order`       INT          NOT NULL DEFAULT 0 COMMENT '排序号，升序',
  `remark`           VARCHAR(500) DEFAULT NULL COMMENT '备注',
  `dept_id`          BIGINT UNSIGNED NOT NULL COMMENT '归属机构ID（创建人主机构）：审计留痕',
  `create_by`        BIGINT UNSIGNED DEFAULT NULL COMMENT '创建人用户ID',
  `create_time`      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_by`        BIGINT UNSIGNED DEFAULT NULL COMMENT '最后修改人用户ID',
  `update_time`      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '最后修改时间',
  `deleted`          TINYINT(1)   NOT NULL DEFAULT 0 COMMENT '逻辑删除：0=未删除 1=已删除',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_route_code` (`route_code`),
  KEY `idx_business_key` (`apply_dept_id`, `region_code`, `transfer_type`),
  KEY `idx_owner_company` (`owner_company_id`),
  KEY `idx_region` (`region_code`),
  KEY `idx_type_status` (`transfer_type`, `status`),
  KEY `idx_carrier` (`carrier_id`),
  KEY `idx_dept` (`dept_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci
  COMMENT='外转/直达路由表：适用网点 + 目的地行政区划配置，支持直达与外转并存与跨公司授权';
```

索引/约束理由：
- `idx_business_key`：同键复用/补授权；`transfer_type` 入键后直达与外转可并存。
- `idx_region`：开单目的地匹配、区划变更影响面统计。
- `uk_route_code` 同上；`idx_owner_company` 支撑归属筛选与权限判定。
- 不建 DB 业务唯一索引：逻辑删除冲突，应用层保证。

## V8-4 `sys_route_company`

```sql
CREATE TABLE `sys_route_company` (
  `id`              BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
  `route_scope`     TINYINT UNSIGNED NOT NULL COMMENT '路由域：1=短驳/干线(sys_route) 2=外转/直达(sys_route_transfer)',
  `route_id`        BIGINT UNSIGNED NOT NULL COMMENT '路由ID，按 route_scope 指向对应路由表',
  `company_dept_id` BIGINT UNSIGNED NOT NULL COMMENT '被授权分公司ID，引用 sys_dept.id；0=全部分公司（动态覆盖后续新增，不展开快照）',
  `grant_source`    TINYINT UNSIGNED NOT NULL COMMENT '授权来源：1=创建自动 2=手动 3=总部全部（company_dept_id=0）',
  `grant_by`        BIGINT UNSIGNED DEFAULT NULL COMMENT '授权人用户ID',
  `grant_time`      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '授权时间',
  `remark`          VARCHAR(200) DEFAULT NULL COMMENT '备注：同键自动补授权、批量授权说明等',
  `create_time`     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_route_company` (`route_scope`, `route_id`, `company_dept_id`),
  KEY `idx_company` (`company_dept_id`),
  KEY `idx_route` (`route_scope`, `route_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci
  COMMENT='路由授权分公司表：自动/手动/全部三类来源，支撑动态全量与审计回收';
```

索引/约束理由：
- `uk_route_company`：同一路由对同一公司（含 0）只允许一条授权，重复补授权幂等。
- `idx_company`：分公司"授权给我的路由"、开单候选集（`company_dept_id IN (本公司, 0)`）。
- `idx_route`：授权弹窗、列表「授权范围」判定（是否存在 0 号行）。
- 无 `dept_id`：跨公司关系表，必须豁免数据权限，否则会被追加不存在的列导致 SQL 报错。
- 不建 `CHECK (company_dept_id > 0)`：0 是合法动态标记，由业务层约束（仅总部可写 0）。

## 版本划分

| 版本 | 内容 | 顺序理由 |
|---|---|---|
| V7 | 区划表、同步日志、机构省市区县、末端网点适用公司 | 机构与区划是路由的前置主数据 |
| V8 | 承运商、路由两表、路由授权 | 依赖 V7 的区划与机构结构 |

---

# 第四部分 · R1 方案 A 的硬性要求（必须落到实现与验证）

## 4.1 豁免清单

新增豁免 7 张（加入 `DataScopeTables.EXEMPT_TABLES`，并同步登记规范文档）：

```
sys_region
sys_region_sync_log
sys_dept_apply_company
sys_carrier
sys_route
sys_route_transfer
sys_route_company
```

豁免理由：路由与承运商跨公司共享，行级 `dept_id` 过滤与授权关系矛盾；授权表、适用公司表无 `dept_id`，不豁免会直接报错；区划为平台基础数据。

## 4.2 四条硬性要求（逐字执行）

1. **每个路由/承运商查询接口都必须显式按授权关系过滤，不允许依赖拦截器。**
2. **越权验证必须给证据**：B 公司账号查不到未授权路由、查不到他公司承运商，详情、编辑、删除、导出、开单匹配接口全部覆盖；总部账号不受限。
3. **豁免清单变更写进数据权限规范文档**（`2026-11-21/data-permission/outputs/data-permission-spec.md`），并注明"**豁免表可见性由服务层负责**"，逐表列出责任接口/服务。
4. **任一接口无法保证即停止并上报**，不允许"先上线再说"。

## 4.3 服务层可见性矩阵（实现口径）

| 接口/动作 | 总部 | 分公司 | 开单员（分公司） |
|---|---|---|---|
| 路由列表/分页 | 全部 | `owner_company_id=本公司 OR 授权(本公司) OR 授权(0)` | 仅授权（含 0） |
| 路由详情 | 全部 | 同上，否则拒绝 | 仅授权 |
| 路由新增 | 全部范围 | 机构必须在管理范围内；自动授权本公司 | — |
| 路由编辑 | 全部 | 仅 `owner_company_id=本公司` | — |
| 路由删除/取消 | 全部，但需先回收其他授权 | 仅 owner，且无其他授权行、无 0 号行 | — |
| 导出 | 与列表同一查询口径 | 同左 | — |
| 授权弹窗/批量授权 | 允许 | 拒绝 | — |
| 开单匹配 | 全部启用 | 仅授权（含 0） | 仅授权（含 0） |
| 承运商列表/下拉 | 全部 | 本公司管理范围内的 `dept_id` | 同左 |
| 承运商详情/编辑/删除 | 全部 | 仅本公司管理范围内 | — |
| 管控范围接口 | 全部机构 | 本公司子树 ∪ 适用末端网点 | — |

实现约束：
- 在 `system` 模块内提供统一的 `RouteVisibilityScope`（或等价守卫），所有路由/承运商 Mapper 查询必须显式传入公司/管理范围参数；禁止直接 `selectById` 后不做归属校验。
- 详情/编辑/删除采用"先查可见性，再执行"的两步；不可见统一返回业务错误（建议 403 语义），不泄露记录是否存在。
- 导出复用列表查询，不允许另写一套 SQL。
- 开单匹配使用同一授权判定，禁止复制条件导致口径漂移。
- 管理范围选择器与授权判定均以服务端计算为准，客户端参数不可信。

## 4.4 规范文档变更（实施时执行）

在 `data-permission-spec.md` 第 2 节追加：
- 上述 7 张表进入豁免清单；
- 新增小节「**豁免表可见性由服务层负责**」，逐表列出：责任服务、可见性规则、越权拒绝方式、对应验证用例编号；
- 注明本次变更属"跨公司共享配置类"豁免，需评审；后续新增同类表按同一模式登记。

## 4.5 停止条件

出现以下任一情况，实施立即暂停并上报，不得继续上线：
- 某个路由/承运商接口无法显式加授权条件（例如依赖 MP 通用 `selectById`/`selectPage` 且无法注入条件）；
- 导出与列表口径不一致；
- 详情接口在越权时返回了数据或可区分记录存在性的错误；
- 豁免清单未同步规范文档；
- 越权验证缺少可复核证据。

---

# 第五部分 · 对已完成模块的影响面

| 模块 | 影响 |
|---|---|
| 机构管理 | 抽屉适用公司多选（仅末端网点）、列表列与筛选、关系 diff 维护、删除/末端变更清理 |
| 机构下拉 | 新增 `/depts/manage-scope`；路由/授权/承运商必须使用；旧 `/depts/options` 保留但标注全量风险 |
| 路由管理 | 新增创建公司/授权范围列（「全部」标签）、授权与批量授权；创建时按管理范围选机构；授权表 0 号行 |
| 承运商管理 | 无授权概念；方案 A 下服务层按管理范围显式过滤，join 不再被拦截器误伤 |
| 数据权限 | 7 张表豁免；豁免表不触发 fill，`dept_id/create_by/grant_by/triggered_by` 全部服务层显式写；规范文档同步 |
| 用户模块 | 无结构变更；所属公司判定复用主机构 |
| 区划模块 | 无变更 |
| 开单模块（未建） | 消费匹配接口；路由节点快照与轨迹由开单域落地 |
| 菜单 | 不新增；不引入按钮级权限点 |

---

# 第六部分 · 待确认项（R1 已定，其余给建议）

| # | 问题 | 建议 | 理由 |
|---|---|---|---|
| R2 | 「同样路由」判定键 | 短驳/干线=`类型+始发+到达`；外转/直达=`适用网点+目的地+类型` | 外转必须带适用网点，否则不同网点误判同一条；类型入键支持直达/外转并存 |
| R3 | 分公司管理员能否手动授权给其他公司 | **不能**，手动/批量仅总部 | 跨公司授权影响他人可见性 |
| R4 | 分公司管理员能否把末端网点适用到其他公司 | **不能**，仅总部可跨公司设置 | 防止分公司扩大共用范围 |
| R5 | 总部开单员是否受授权过滤 | **不受**，可用全部启用路由 | 总部为全局角色；授权只约束分公司 |
| R6 | 总部是否保留「转为按公司授权」 | **保留为可选操作**，需二次确认 | 0 号行是动态全量；个别场景需精确控制时提供出口，但不作为默认 |
| R7 | 停用/删除分公司时授权行如何处理 | 停用保留；删除时清理其显式授权行与适用关系 | 停用可恢复；删除需防脏数据 |
| R8 | 同键复用时表单差异字段 | **不覆盖**，仅补授权并提示 | 共享路由公共字段不能被静默改写 |
| R9 | 开单路由快照表归属 | 由开单模块建，本方案只定契约 | 与运单强绑定，放开单域 |
| R10 | 机构管理列表是否按管理范围过滤 | **建议是** | 与选择器口径一致，避免"选不到但看得到" |
| R11 | 承运商页分公司可见范围 | **本公司管理范围内**（子树），总部全部 | 与机构口径一致；共享路由的承运商名由 join 展示，不受页面过滤影响 |
| R12 | 授权范围「部分」的展示 | 显示「N 家」，Tooltip 列公司名称 | 列表空间有限；详情/授权弹窗可看全量 |

---

# 第七部分 · 实施顺序与验证清单

## 7.1 实施顺序

| 阶段 | 内容 | 前置/门禁 |
|---|---|---|
| 0 | 方案评审、R1=A 备案、数据权限规范登记 | 本文档审核通过；规范文档变更条目先评审 |
| 1 | V7 迁移：区划表、同步日志、机构省市区县、适用公司表 | DDL 审核通过；备份数据库 |
| 2 | 区划离线导入 + 区域管理页 | V7 执行成功；三级数据导入幂等验证通过 |
| 3 | 机构省市区县 + 适用公司（后端 + 前端） | V7；管理范围接口可用 |
| 4 | V8 迁移：承运商、路由两表、授权表 | V7 完成；DDL 审核通过 |
| 5 | 承运商模块（CRUD + 服务层可见性） | V8；可见性守卫就绪 |
| 6 | 路由两 Tab + 授权 + 批量授权 + 「全部」标识 | V8；管理范围与授权守卫就绪 |
| 7 | 数据权限豁免配置 + 规范文档更新 + 全接口守卫接入 | 阶段 5/6 完成；**守卫未全接入不得启用模块** |
| 8 | 开单匹配接口（随开单模块排期） | 路由授权稳定；匹配契约评审通过 |
| 9 | 越权验证与回归、证据归档 | 全部用例通过 |

## 7.2 验证清单（逐项留证）

### A. 结构与迁移

- [ ] V7/V8 Flyway 执行成功，版本号连续，无重复/跳号。
- [ ] 表、列、注释、索引、唯一键与本文档一致（`SHOW CREATE TABLE` 截图/文本）。
- [ ] `sys_dept` 原数据不丢失；新增列默认 NULL。
- [ ] 回滚预案：执行前全库备份，记录备份文件与时间。

### B. 区划与机构

- [ ] 三级 JSON 导入成功，省级/市级/区县级数量与源文件一致。
- [ ] 同一文件重复导入：新增 0、修改 0、无变化=总数（幂等）。
- [ ] 父子链、`full_name` 正确；缺失编码只进"待停用"报告，不自动停用。
- [ ] 分拨/网点保存时省市区县必填；总部/公司/部门选填但完整。
- [ ] 末端网点适用公司多选保存/回显；非末端不产生关系。
- [ ] 分公司创建网点默认本公司；总部创建必须选择。
- [ ] 适用公司关系删除/新增按 diff 生效，无重复行。

### C. 管理范围

- [ ] 总部账号：返回全部网点/分拨。
- [ ] 分公司账号：返回本公司子树 + 适用本公司末端网点，且不含其他公司普通网点。
- [ ] 多级公司：只返回最近一级公司范围。
- [ ] 无公司祖先/机构链异常：返回空并记录警告。
- [ ] 客户端伪造 `companyId` 无效。
- [ ] 路由创建时提交范围外机构 → 服务端拒绝。

### D. 路由授权

- [ ] 分公司 A 创建：授权表仅 A 行，无 0 号行。
- [ ] 总部创建：授权表仅 0 号行（source=3）；列表显示「全部」标签。
- [ ] 新建分公司 B：总部路由自动对 B 可见可用，无需补数据。
- [ ] 分公司 A 与 B 创建同键路由：复用同一行，各自授权行存在，公共字段未被覆盖。
- [ ] 0 号行存在时手动授权入口隐藏/禁用；「取消全部授权」可用。
- [ ] 取消路由：存在他公司授权或 0 号行时返回逐字文案"路由被其他公司使用，不能取消，取消授权即可"。
- [ ] 取消授权：仅删本公司行；最后一行删除后路由自动停用。
- [ ] 批量授权：重复项跳过，返回成功/跳过数量正确。

### E. 越权验证（方案 A 核心，必须留证）

账号：总部 H、分公司 A、分公司 B（B 对 A 的路由无授权）。

- [ ] B 调路由列表：不含 A 独占路由；总条数与可见条数一致。
- [ ] B 调路由详情（传 A 路由 ID）：拒绝，且不泄露记录存在性。
- [ ] B 调编辑/删除 A 独占路由：拒绝，数据未变更。
- [ ] B 导出：行数与列表一致，不含 A 独占路由。
- [ ] B 开单匹配：候选不含 A 独占路由；无候选时返回约定文案。
- [ ] B 调承运商列表/下拉：不含 A 独占承运商；详情/编辑/删除同样拒绝。
- [ ] B 查看已授权共享路由：承运商名称正常显示（join 未被过滤）。
- [ ] H 调以上全部接口：不受限，结果与直接查库一致。
- [ ] 每个用例记录：请求、响应、关键 SQL、影响行数。

### F. 回归

- [ ] 用户/角色/菜单/机构四个已完成模块功能正常。
- [ ] 数据权限四档在非豁免表上行为不变；无意外 `1=0`。
- [ ] 豁免表未被追加任何条件（抓取 SQL 验证）。
- [ ] 前端 `vue-tsc` 与构建通过；后端打包通过。

### G. 证据归档

- [ ] 越权用例证据汇总为一份验证报告，附请求/响应/SQL/行数。
- [ ] 测试数据用后清理，给出清理前后清单。
- [ ] 规范文档变更与本方案一并归档。

---

# 第八部分 · 文件清单与执行声明

本轮新增：
- `2026-11-21/route-region-design/outputs/route-region-design-v2.1.md`（本文件）

保留未改：
- `route-region-design-v2.0.md`
- `route-region-design-v1.0.md`
- `route-region-design.md`（v0.1）

核查只读：luohuo 源码、yunshu 源码与迁移脚本、数据权限规范文档、数据库统计查询。

未执行：任何 DDL、建表、业务代码、现有文件修改。待 DDL 审核通过并按第 7 部分顺序实施。

---

# 第九部分 · 与 luohuo 的差异清单（固定章节）

> 总原则：页面设计参考 luohuo，但不整体照搬。luohuo 不涉及多公司，凡涉及路由授权、适用公司、创建公司、管理范围的，按 yunshu 已定方案实现，并在此逐条说明原因。

## 9.1 必要差异（保留 yunshu 口径）

| 编号 | 差异点 | luohuo | yunshu | 保留原因 |
|---|---|---|---|---|
| N1 | 权限锚点 `dept_id` | 无该字段（`org_id` 直接存适用网点） | `sys_route_transfer.dept_id` 仅作数据权限/审计锚点，`apply_dept_id` 才是业务适用网点，禁止混用 | 四表豁免数据权限，可见性由服务层显式过滤，需要稳定锚点 |
| N2 | 创建公司 `owner_company_id` | 无 | 记录创建公司，决定"本公司可覆盖公共字段""他公司只补授权"及删除权限 | 多公司下必须区分路由归属 |
| N3 | 路由授权 `sys_route_company` | 无授权概念 | 外转/直达支持总部授权、分公司自动授权、0 号行动态全量；取消授权/删除拦截按授权关系判定 | 跨公司共用同一目的地路由是真实业务 |
| N4 | 适用公司可见范围 | 仅按单网点过滤 | 分公司可见范围=本公司子树 ∪ 适用本公司的末端网点；他公司路由未授权不可见 | 末端网点可能服务多个分公司 |
| N5 | 开单侧直达优先 | 无匹配接口 | 匹配排序：直达 > 网络完整链路 > 外转，再比时效/里程/跳数/sort_order，首条推荐 | 直达成本最低，业务已确认 |
| N6 | 短驳/干线不做授权 | 无此概念 | Tab1 按创建公司归属可见，工具栏/行操作无授权入口，授权接口拒绝 `route_scope=1` | 网点间物理段不存在跨公司共用，授权是多余模型 |
| N7 | 编码唯一口径 | `区划码_ZT/_WZ`，DB 无唯一索引，不同网点同编码可共存 | 同左；唯一性=「适用网点+编码」应用层校验，DB 不建全局唯一索引 | 与 D4=B 裁决一致，避免逻辑删除后无法重建 |
| N8 | 区划级别 | 支持市/区县/乡级 | 同左；乡级数据已全量导入（41270 条），机构地址仍维持省/市/区县三级 | 目的地需要乡级，机构地址层级不因之放开 |
| N9 | 文案 | 「直送」 | 「直达」 | 家伟裁定 D2 |

## 9.2 luohuo 自身缺陷（不照抄）

| 编号 | 缺陷 | 影响 | yunshu 处理 |
|---|---|---|---|
| L1 | `sys_route.org_id` 注释写"归属机构（总部）"，实现存"适用网点" | 字段语义与注释冲突，二次开发易误用 | 拆分 `apply_dept_id`（业务）与 `dept_id`（权限锚点），注释与实现一致 |
| L2 | 仅靠应用层校验唯一，逻辑删除后同键重建依赖"已删除行不参与校验" | 若校验遗漏 `is_deleted`，会误判重复或重建失败 | 唯一校验显式排除已删除行；不建全局唯一索引 |
| L3 | 批量加入无"创建公司"区分，直接覆盖公共字段 | 他公司路由可能被误改 | 本公司路由覆盖公共字段，他公司只补授权、绝不覆盖（D5=B） |
| L4 | 不校验网点/区划有效性（停用网点、失效区划仍可加入） | 产生脏路由 | 加入/编辑校验网点在管理范围内且启用、区划存在且启用 |
| L5 | 类型切换仅改后缀，不清理承运商 | 直达行残留承运商 | 切直达自动清空承运商 |
| L6 | 单条新增（非区划库批量） | 与"区划库批量加入"双入口，编码/名称易不一致 | 只保留区划库批量加入，编辑仅限名称/承运商/状态/排序/备注 |
| L7 | 无多公司授权/越权隔离 | 无法支撑分公司场景 | 见 9.1 N1–N4，所有接口显式按授权关系过滤 |

## 9.3 用户管理（补充章节）

| 项 | luohuo | yunshu | 说明 |
|---|---|---|---|
| 状态切换 | 列表行内开关 | **列表行内开关**，与 luohuo 一致；二次确认并说明停用影响 | 早期曾讨论不提供行内开关，家伟已更正为必须提供，本记录为最终口径 |
| 停用保护 | 超管、自身不可停用 | 同左（后端双重拦截） | 与 luohuo 一致 |
| 停用后会话 | JWT 无状态，旧 token 到期前仍可用 | 服务端注销该用户全部 token，立即失效 | yunshu 更严格，属必要差异 |
| 失败回滚 | 前端提示 | 提示原因并重拉列表，以服务端状态为准 | 避免界面与数据不一致 |

## 9.4 列设置接入与筛选能力缺口清单

统一列设置已覆盖：菜单管理、机构管理、角色管理、用户管理、承运商、短驳/干线、外转/直达，以及外转/直达双栏的左侧区划库。协议一致（`ColumnDef` + `useColumnConfig` + `ColumnSetting` + `ColumnFilter`），配置按「用户 + pageKey」持久化到 `sys_column_config`，页面标识走白名单。

以下字段的筛选控件**未降级为文本**，而是标记为 `filterType: 'none'`，只参与列顺序/显隐；原因是后端缺少对应筛选参数，或该字段是复合/多值关系，塞进单值筛选会造成前后端口径不一致。后续补后端参数后，只需在 schema 打开对应筛选类型即可，不动组件。

| 页面 | 字段 | 现状 | 不提供筛选的原因 | 建议 |
|---|---|---|---|---|
| 承运商 | 排序号 | none | 列表接口无 `sortOrder` 筛选参数 | 仅作展示；如需按排序号过滤，补后端参数后再开数值区间 |
| 承运商 | 修改时间 | none | 接口无 `updateTimeStart/End` | 需要时按创建时间同款补区间参数 |
| 用户管理 | 可登录机构 | none | `sys_user_org` 多值关系，单值精确匹配会漏 | 保留抽屉维护；如要筛选需后端支持"包含任一机构"语义 |
| 机构管理 | 所在地区 | none（自定义省+市联动筛选） | 省、市是两个字段联动，不能套用通用单列筛选 | 保留页面自定义的省市联动控件，仅参与列顺序/显隐 |
| 短驳/干线 | 库区、里程、时效、联系人电话、客服电话 | none | 列表接口无对应筛选参数 | 按业务需要补参数后再开；不建议前端假筛选 |
| 短驳/干线 | 创建公司、排序号、更新时间 | none | 无筛选参数（创建公司仅展示归属） | 同上 |
| 外转/直达 | 排序、修改时间、创建公司 | none | 无筛选参数 | 同上 |
| 外转/直达 | 授权范围 | 下拉（全部/部分） | 已支持，服务层按 0 号行判定 | 保持 |
| 区划库（左栏） | 全部列 | 已支持 | 省级、市级、区县关键词、乡级关键词、层级、编码均有后端参数 | 保持 |

原则：
1. 不为凑"每列都有筛选"而把复合/多值字段降级成文本框；
2. `filterType: 'none'` 的列在列设置里明确不可选筛选类型，避免用户误配后无效果；
3. 新增筛选能力时先补后端参数与口径，再在 schema 打开，禁止只改前端。
