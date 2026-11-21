# 路由管理 + 区域管理 + 承运商管理 · 设计方案 v2.0

> 状态：**仅设计。未写业务代码、未建表、未执行任何 DDL、未修改任何现有文件。**
> 基线：v1.0（2026-11-21）。本版新增「路由授权分公司」「末端网点适用分公司」「管理范围选择器」「开单侧路由匹配」四块业务规则。
> 核查依据：luohuo 源码 + yunshu 现有代码/库结构（只读），文件与行号见下。

---

## 0. 与 v1.0 的差异

| # | 新增规则 | v2.0 落地 |
|---|---|---|
| 1 | 路由在开单时使用，输入目的地自动拼接路由，数据按节点流转 | 新增「开单侧路由匹配」设计：授权候选集 + 图路径拼接 + 排序 + 多候选处理 + 节点契约 |
| 2 | 选择网点受登录人范围限制 | 新增独立「管理范围」接口，不复用数据权限四档；总部全部、分公司=本公司子树 ∪ 适用本公司的末端网点 |
| 3 | 总部/分公司管理员维护路由 | 管理范围判定：用户主机构向上最近 `dept_type=2`；主机构为总部或无分公司祖先 → 总部 |
| 4 | 末端网点跨分公司共用 | 新增 `sys_dept_apply_company` 关系表；管理范围做并集 |
| 5 | 机构管理增加「适用公司」 | 仅末端网点（`is_leaf=1`）显示并必填；分公司创建默认本公司，总部创建必须选 |
| 6 | 路由授权分公司（创建自动 / 他公司同键补授权 / 取消拦截 / 手动与批量） | 新增 `sys_route_company` 授权表；路由表加 `owner_company_id`；定义取消路由与取消授权的区别与文案 |
| 7 | 家伟建议 a–d | 全部采纳并写入：独立管理范围接口、适用公司多选、总部全部用动态标记、直达优先规则 |

对 v1.0 的修正：
- v1.0「外转/直达唯一键=适用网点+目的地（类型二选一）」→ v2.0 改为 **适用网点 + 目的地 + 类型**，允许直达与外转并存，开单侧直达优先（对应建议 d）。
- v1.0「路由两表不豁免数据权限」→ 本版发现跨公司共享会与行级过滤冲突，改为**待确认 R1**（推荐豁免，见第四部分），不自行拍板。

---

# 第一部分 · 核查事实（只读）

## 1.1 机构模型与「公司」判定基础

| 项 | 事实 | 位置 |
|---|---|---|
| 机构类型 | 1=总部 2=公司 3=部门 4=分拨中心 5=网点 | `SysDept.java:29-30` |
| 末端标记 | `is_leaf` Boolean，注释"仅网点可为 true" | `SysDept.java:35-37` |
| 主机构 | `sys_user.dept_id`；可登录机构另存 `sys_user_org` | `SysUser.java:34`、`SysUserOrg.java:12` |
| 用户数据范围 | `sys_user.data_scope`：1 全部 / 3 本机构 / 4 本机构及以下 / 5 仅本人 | `DataScopeWebInterceptor.java:66-82` |
| 超管 | `SCOPE_ALL`，但保留主机构用于新增填充 | `DataScopeWebInterceptor.java:59-63` |

## 1.2 现有机构接口与页面

| 项 | 事实 | 位置 |
|---|---|---|
| 下拉接口 | `GET /api/v1/system/depts/options?type=`，当前**只按启用状态与单一类型过滤，不做任何范围过滤** | `SysDeptController.java:75-81`、`SysDeptServiceImpl.java:348-356` |
| 机构列表 | 13 列（名称/上级/类型/性质/编码/负责人/电话/是否末端/状态/创建人/创建时间/修改人/修改时间/操作） | `frontend/src/views/system/org/index.vue:116-469` |
| 抽屉 | 上级/类型/性质/是否末端/名称/编码/负责人/电话/机构地址/排序/状态/备注；无省市区县、无适用公司 | `DeptFormDrawer.vue:11-116` |

## 1.3 数据权限的边界事实

- `sys_dept` 在豁免清单内，因此**机构列表与机构下拉当前不受数据权限过滤**（`DataScopeTables.java:13-23`）。
- 拦截器对语句中**每一张非豁免表**逐表追加条件，包括 join 进来的表（`DataScopeSqlHandler.java:29-63`、`columnPrefix` 取别名）。
- 结论：跨公司共享一旦引入，若路由/承运商仍参与行级过滤，`join` 会被追加对方公司的 `dept_id` 条件，导致共享数据被隐藏。这不是配置问题，是机制决定的，必须显式决策（见第四部分 R1）。

## 1.4 luohuo 对照

| 项 | 事实 |
|---|---|
| 机构类型 | 仅 1 公司 / 2 部门 / 3 网点，无分拨中心、无 `is_leaf`（`models/organization.py:25`） |
| 路由授权 | **无**路由授权分公司、无适用公司、无末端网点共享概念；路由仅按 `org_id` 归属 |
| 结论 | 本版新增的授权/适用公司/管理范围均为 yunshu 独有需求，luohuo 无现成实现可抄，需按本方案自建 |

## 1.5 现状空白

- yunshu 当前仅 `auth`、`system` 两个后端模块（`modules/` 下只有这两目录），**开单模块尚未建设**。
- 路由、承运商、区划表与页面均未建，因此本版所有结构变更**无历史数据需要回填**。
- 菜单 1104 / 1107 / 1109 已存在，无需新增菜单。

---

# 第二部分 · 方案

## 2.1 术语与角色

| 术语 | 定义 |
|---|---|
| 总部 | `dept_type=1` 的机构（当前 id=1） |
| 分公司 | `dept_type=2` 的机构；允许多级嵌套，取"最近"的一级 |
| 末端网点 | `dept_type=5` 且 `is_leaf=1`；可被多个分公司共用 |
| 用户所属公司 | 从用户主机构向上（含自身）最近的 `dept_type=2`；主机构为总部或路径上无公司节点 → 总部 |
| 管理范围 | 总部=全部机构；分公司=本公司子树 ∪ 适用本公司的末端网点 |
| 路由授权 | 某条路由被授权给某分公司使用；未授权的公司不可在开单时匹配 |

## 2.2 关系模型总览

```
sys_dept (机构)
  ├── sys_dept_apply_company   末端网点 ↔ 适用分公司（多对多）
sys_route / sys_route_transfer (路由)
  ├── sys_route_company        路由 ↔ 授权分公司（多对多，含来源/授权人/时间）
  └── owner_company_id         路由创建公司（归属审计 + 删除权限）
```

### 2.2.1 末端网点适用分公司 `sys_dept_apply_company`

| 字段 | 说明 |
|---|---|
| `leaf_dept_id` | 末端网点 ID，要求 `is_leaf=1` |
| `company_dept_id` | 适用分公司 ID，要求 `dept_type=2` 且启用 |
| `create_by/create_time` | 审计；豁免表不触发 fill，服务层显式写入 |

规则：
- 多选；同一网点对同一公司唯一（唯一键）。
- 仅末端网点维护；非末端网点不产生记录。
- 关系为硬删除（diff 维护：新增缺失、删除多余），不做逻辑删除。
- 适用范围直接决定管理范围并集与机构选择器可见性。

### 2.2.2 路由授权分公司 `sys_route_company`

| 字段 | 说明 |
|---|---|
| `route_scope` | 1=短驳/干线（`sys_route`）2=外转/直达（`sys_route_transfer`） |
| `route_id` | 路由 ID，按 `route_scope` 指向对应表 |
| `company_dept_id` | 被授权分公司 ID；**0=全部分公司**（动态覆盖后续新建公司） |
| `grant_source` | 1=创建自动 2=手动 3=总部全部 |
| `grant_by / grant_time` | 授权人与授权时间，用于审计与回收 |
| `remark` | 如"同键路由自动补授权""全部授权转显式快照" |

规则：
- `(route_scope, route_id, company_dept_id)` 唯一。
- "总部全部"用 `company_dept_id=0` 的标记行表达，**不写入公司列表快照**；新增公司自动获得授权（对应建议 c）。
- 查询授权：`company_dept_id = 本公司 OR company_dept_id = 0`。
- 授权行硬删除；回收即删行，保留审计可通过操作日志补足（若需要，二期再加授权变更日志表）。

### 2.2.3 路由表新增列

`sys_route` 与 `sys_route_transfer` 均新增：
- `owner_company_id`：创建公司 ID（总部或分公司），用于归属展示与删除权限判定；不参与授权判定。
- 不新增 `grant_all_companies` 列：全部授权由 `sys_route_company` 的 0 号行表达，避免两处状态。

## 2.3 管理范围接口（独立于数据权限）

### 2.3.1 接口

```
GET /api/v1/system/depts/manage-scope?types=4,5&keyword=
```

返回：
```
{
  "scopeType": "HQ" | "COMPANY",
  "companyId": 1002,
  "companyName": "广州分公司",
  "tree": [ ... ],            // 本公司子树（或总部全树），含 children
  "sharedLeaves": [ ... ]     // 适用本公司的末端网点（单列分组，便于前端区分）
}
```

### 2.3.2 判定规则

1. 取当前登录用户主机构 `dept_id`。
2. 主机构为 `dept_type=1`，或超管 → **总部范围**：返回全部启用机构（按 `types` 过滤）。
3. 否则从主机构向上逐级查 `parent_id`：
   - 遇到第一个 `dept_type=2` → 该公司；
   - 若先到达 `dept_type=1`（路径上无公司节点）→ 按总部处理；
   - 机构链断裂、成环、主机构为空 → **返回空并记警告**（fail-closed，不猜测）。
4. 分公司范围 = `subtree(company)` ∪ `{leaf | is_leaf=1, status=1, exists sys_dept_apply_company(leaf, company)}`。
5. 去重（按 id）；`types` 过滤后返回；`types=4,5` 时不返回公司节点自身。
6. 只返回 `status=1`、`deleted=0`；节点数超过 5000 直接报错（防超大子树）。

### 2.3.3 边界

| 场景 | 结果 |
|---|---|
| 总部管理员 | 全部网点/分拨中心 |
| 分公司管理员（两级公司嵌套） | 只取最近一级公司的子树，看不到父公司其他分支 |
| 主机构是网点/分拨/部门 | 向上找最近公司，再按该公司取范围 |
| 末端网点挂在其他分公司下 | 因适用关系并集，仍可见、可选 |
| 分公司看不到未授权给它的普通网点 | 符合"数据权限隔离"预期 |
| 客户端传 companyId 试图越权 | 服务端忽略该参数，始终按登录人计算 |

### 2.3.4 与数据权限的关系

- **管理范围接口只回答"选择器里能选哪些机构"**，不参与数据行过滤。
- 数据权限继续负责业务数据行可见性；二者不互相替代。
- 但路由/承运商因跨公司共享，行级过滤语义失效，处理方式见第四部分 R1。

## 2.4 机构管理改造

### 2.4.1 抽屉新增「适用公司」

| 字段 | 控件 | 显示条件 | 必填 | 规则 |
|---|---|---|---|---|
| 适用公司 | 多选下拉（仅 `dept_type=2` 且启用） | 仅 `is_leaf=1` 时显示 | 是（至少 1 家） | 分公司管理员默认本公司；总部管理员无默认、必须选择 |

- 分公司管理员创建网点：默认勾选本公司；按建议仅允许本公司（跨公司设置由总部完成，见 R5）。
- 总部管理员：不预选，必须选择 1 家及以上。
- 编辑已有末端网点：回显已有适用公司。
- `is_leaf` 由 true 改为 false：隐藏字段；保存时提示"将清除适用公司关系"，确认后删除关系（见 R8）。
- 机构删除：先删关系；若该机构被路由引用则拦截（沿用现有引用校验思路）。

### 2.4.2 列表

- 新增列「适用公司」：末端网点显示公司名称，多个用逗号分隔（超 2 家显示"等 N 家"）；非末端显示 `-`。
- 新增筛选「适用公司」：下拉选择分公司，仅命中末端网点关系。
- 列设置中可开关。

### 2.4.3 选择器

- 路由/授权等需要"按管理范围选机构"的地方，**必须使用新接口** `/depts/manage-scope`。
- 现有 `/depts/options` 保持不动（其他页面在用），但其"返回全部机构"的现状需在文档中标注为遗留风险，后续统一收敛。

## 2.5 路由授权完整流程

### 2.5.1 创建（含同键复用）

前置：创建人所属公司 = `C`；业务键见 2.5.5。

```
创建请求
  ├─ 按业务键查 deleted=0
  │   ├─ 不存在 → 新建路由
  │   │            owner_company_id=C
  │   │            C=总部 → 授权行(0, source=3)
  │   │            C=分公司 → 授权行(C, source=1)
  │   └─ 已存在 → 复用该路由，不覆盖任何公共字段
  │                C=总部 → 补授权行(0, source=3)
  │                C=分公司 → 若已授权则跳过；否则补授权行(C, source=1, remark=同键路由自动补授权)
  └─ 返回：路由ID + 是否新建 + 是否补授权 + 被忽略的差异字段提示
```

约束：
- 同键复用时，若提交的表单与现有路由存在字段差异（名称/库区/里程/承运商等），**不覆盖**，返回提示"路由已存在，已为您授权；差异字段未生效，如需修改请联系创建公司或总部"（R10）。
- 并发：业务键应用层校验 + 事务内 `SELECT ... FOR UPDATE`；唯一冲突时重试一次。

### 2.5.2 授权关系判定

```
is_authorized(route, company) =
    exists sys_route_company(route_scope, route_id, company)
 or exists sys_route_company(route_scope, route_id, 0)
```

- 总部创建：插入 0 号行，所有分公司（含以后新建）自动可用。
- 分公司创建：插入本公司行。
- 总部手动授权：插入指定公司行，来源=2。

### 2.5.3 取消路由 vs 取消授权

| 动作 | 语义 | 影响范围 | 允许条件 | 结果 |
|---|---|---|---|---|
| 取消路由（停用/删除） | 路由本体不可用 | 所有公司 | 除本公司外无其他授权（含 0 号行） | 逻辑删除路由 + 清除全部授权行 |
| 取消授权 | 本公司退出使用 | 仅本公司 | 总是允许（总部 0 号行走"取消全部授权"） | 删除本公司授权行；若已无任何授权行 → 路由置 `status=0` |

拦截文案（逐字）：
> **路由被其他公司使用，不能取消，取消授权即可**

补充规则：
- 总部删除同样受此约束；总部需先"取消全部授权"或逐家回收，不能直接强删（本版不做强制删除）。
- 分公司取消路由时若仅本公司授权 → 允许，等价于删除；若存在 0 号行 → 视为其他公司可能使用，按拦截处理。
- 删除路由不物理删，只 `deleted=1`；授权行同步删除。

### 2.5.4 手动授权弹窗与批量授权

入口：
- 路由列表行操作「授权」（总部可见）。
- 路由列表勾选多条 → 工具条「批量授权」（总部可见）。

弹窗结构：
1. 路由摘要：编码 / 名称 / 类型 / 创建公司 / 状态。
2. 当前授权列表：分公司、来源（创建自动/手动/总部全部）、授权人、授权时间、操作（取消授权）。
3. 若存在 0 号行：顶部横幅「已授权全部分公司（含后续新增）」；隐藏"添加授权"；提供「转为按公司授权」按钮。
4. 添加授权：多选分公司（`dept_type=2`、启用、排除已授权）→ 确定。
5. 取消授权：二次确认；若删除后无任何授权行 → 同时提示"路由将自动停用"。

批量授权：
- 选择 N 条路由 + M 家公司 → 按笛卡尔积插入，已存在跳过；返回"成功 X 条，跳过 Y 条"。
- 单次上限建议 50 条路由 × 50 家公司（防止误操作）。

权限：仅总部可手动授权/回收（建议，见 R4）；分公司仅有创建自动授权。

### 2.5.5 「同样路由」判定键

| 路由域 | 业务键（应用层唯一，`deleted=0`） |
|---|---|
| 短驳/干线 `sys_route` | `route_category + origin_dept_id + dest_dept_id` |
| 外转/直达 `sys_route_transfer` | `apply_dept_id + region_code + transfer_type` |

说明：
- 短驳/干线的始发/到达就是网点本身，不存在独立"适用网点"。
- 外转/直达包含 `apply_dept_id`：不同适用网点的线路不视为同一条；只有共用末端网点等场景才会同键，从而触发跨公司自动补授权。
- v2.0 起 `transfer_type` 进入业务键，允许同一公司同一目的地同时存在直达与外转，开单侧按直达优先选择（建议 d）。

## 2.6 开单侧路由匹配

### 2.6.1 输入与候选集

输入：始发网点、目的地（网点 ID 或区划编码）。
过滤：
1. `status=1`、`deleted=0`；
2. 已授权给开单员所属公司（`company_dept_id=本公司 OR 0`）；
3. 总部开单员：不做授权过滤，视为可使用全部启用路由（见 R6）；
4. 网点/分拨节点均需启用。

### 2.6.2 匹配规则

1. **目的地是网点**：在授权短驳/干线构成的图上，从始发网点到目的地网点枚举路径。
   - 最大 5 条边、最多 50 条候选、禁止环路。
   - 边方向：始发→到达（单向）。
2. **目的地是区划**：
   - 先按区划层级匹配外转/直达：精确编码 → 上级（乡级→区县→市）逐级回退；
   - 同时尝试网络路径到"该区划内的网点"；
   - 两类候选合并排序。
3. **单段直达**：始发网点与目的地网点之间存在直连短驳/干线时，直接作为候选，无需拼接。

### 2.6.3 排序与多候选

排序（从高到低）：
1. 路由形态：直达路由 > 网络内完整链路 > 外转路由；
2. 目的地匹配深度：乡级 > 区县 > 市；
3. 总运输时效（链路各段 `transit_hours` 求和，空值视为无穷大）；
4. 总里程；
5. 跳数（少者优先）；
6. `sort_order` 升序、`id` 升序。

多候选处理：
- 返回 `matched[]`，第一条标记 `recommended=true`；
- 开单页默认选中推荐项；候选 > 1 时展示"切换路由"下拉，显示编码/名称/时效/里程/跳数；
- 若排序键完全一致，取 `id` 最小，保证结果稳定；
- 无候选：阻断开单，文案"当前公司未授权到该目的地的路由，请联系总部授权或新增路由"。

### 2.6.4 节点数据流

匹配结果返回有序节点：
```
routeNodes: [
  { seq, routeScope, routeId, routeCode, category, fromDeptId, toDeptId,
    contactName, contactPhone, transitHours, mileage, warehouseArea, carrierId }
]
```
- 开单模块按节点生成运输轨迹节点（网点→分拨→分拨→到达网点/外转交接）；
- 打印标签取**干线节点**的 `warehouse_area` 值；
- 开单时需将节点快照落库（表由开单模块设计，本方案只定义契约，见 R11）。

## 2.7 页面变更清单

| 页面 | 变更 |
|---|---|
| 机构管理 | 抽屉加「适用公司」（仅末端网点）；列表加列与筛选；删除/末端变更时维护关系 |
| 路由管理（两 Tab） | 列表加「创建公司」「授权分公司」列；行操作加「授权」；工具条加「批量授权」；抽屉创建时按管理范围选机构 |
| 承运商管理 | 无授权概念；可见性按 R1 决策执行 |
| 区域管理 | 无变化（v1.0 方案） |

---

# 第三部分 · DDL 草案（不执行）

> 版本规划：**V7** = 区划 + 机构地址 + 末端网点适用分公司；**V8** = 承运商 + 路由两表 + 路由授权。

## V7-1 `sys_region`、V7-2 `sys_region_sync_log`、V7-3 `sys_dept` 增列

沿用 v1.0 终稿，DDL 与中文注释不变（见 `route-region-design-v1.0.md` 第三部分）。V7 追加：

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
  COMMENT='末端网点适用分公司关系表：支撑共用末端网点的跨分公司可见与选择';
```

索引/约束理由：
- `uk_leaf_company`：同一网点对同一公司只能有一条适用关系，重复提交幂等。
- `idx_company`：管理范围并集查询"适用本公司的末端网点"走该索引。
- 关系表硬删除、无软删列：多对多关系用 diff 维护，软删会让唯一键无法重建。
- 无 `update_*`：授权关系只增删不改。

## V8-1 `sys_carrier`

沿用 v1.0 终稿 DDL，字段不变；是否豁免见第四部分 R1。

## V8-2 `sys_route`（短驳/干线）

在 v1.0 基础上增加 `owner_company_id`，其余不变：

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
  `owner_company_id` BIGINT UNSIGNED NOT NULL COMMENT '创建公司ID（总部或分公司），用于归属展示与删除权限，不参与授权判定',
  `status`           TINYINT(1)   NOT NULL DEFAULT 1 COMMENT '状态：1=启用 0=禁用',
  `sort_order`       INT          NOT NULL DEFAULT 0 COMMENT '排序号，升序',
  `remark`           VARCHAR(500) DEFAULT NULL COMMENT '备注',
  `dept_id`          BIGINT UNSIGNED NOT NULL COMMENT '归属机构ID（创建人主机构）：审计与数据权限兜底',
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
- `idx_business_key`：同键查重（创建复用/补授权）的主查询路径；不建 DB 唯一索引，避免逻辑删除后无法重建，由应用层保证。
- `idx_owner_company`：分公司"我创建的路由"过滤与归属展示。
- `uk_route_code`：编码永不复用，唯一索引兜底并发重号。
- 其余索引同 v1.0。

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
  `owner_company_id` BIGINT UNSIGNED NOT NULL COMMENT '创建公司ID（总部或分公司），用于归属展示与删除权限',
  `status`           TINYINT(1)   NOT NULL DEFAULT 1 COMMENT '状态：1=启用 0=停用',
  `sort_order`       INT          NOT NULL DEFAULT 0 COMMENT '排序号，升序',
  `remark`           VARCHAR(500) DEFAULT NULL COMMENT '备注',
  `dept_id`          BIGINT UNSIGNED NOT NULL COMMENT '归属机构ID（创建人主机构）：审计与数据权限兜底',
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
  COMMENT='外转/直达路由表：适用网点 + 目的地行政区划配置，对齐 luohuo 并支持跨公司授权';
```

索引/约束理由：
- `idx_business_key`：同键复用/补授权查询；`transfer_type` 进入键后，直达与外转可并存。
- `idx_region`：开单按目的地匹配与区划变更影响面。
- 其余同 v1.0。

## V8-4 `sys_route_company`

```sql
CREATE TABLE `sys_route_company` (
  `id`              BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
  `route_scope`     TINYINT UNSIGNED NOT NULL COMMENT '路由域：1=短驳/干线(sys_route) 2=外转/直达(sys_route_transfer)',
  `route_id`        BIGINT UNSIGNED NOT NULL COMMENT '路由ID，按 route_scope 指向对应路由表',
  `company_dept_id` BIGINT UNSIGNED NOT NULL COMMENT '被授权分公司ID；0=全部分公司（动态覆盖后续新建公司，不写快照）',
  `grant_source`    TINYINT UNSIGNED NOT NULL COMMENT '授权来源：1=创建自动 2=手动 3=总部全部',
  `grant_by`        BIGINT UNSIGNED DEFAULT NULL COMMENT '授权人用户ID',
  `grant_time`      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '授权时间',
  `remark`          VARCHAR(200) DEFAULT NULL COMMENT '备注：同键自动补授权/全部授权转显式快照等',
  `create_time`     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_route_company` (`route_scope`, `route_id`, `company_dept_id`),
  KEY `idx_company` (`company_dept_id`),
  KEY `idx_route` (`route_scope`, `route_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci
  COMMENT='路由授权分公司表：记录创建自动/手动/总部全部三类授权来源，支撑审计与回收';
```

索引/约束理由：
- `uk_route_company`：防止重复授权；0 号行天然唯一。
- `idx_company`：分公司"授权给我的路由"查询、开单候选集。
- `idx_route`：授权弹窗与批量授权按路由查当前授权。
- 无 `dept_id`：跨公司关系表，带 `dept_id` 会被行级过滤导致授权关系查不全，因此必须豁免（见第四部分）。

---

# 第四部分 · 数据权限衔接与冲突（需拍板）

## 4.1 事实与冲突

路由改为跨公司共享后，`sys_route.dept_id = 创建人主机构` 与"授权给其他公司"天然矛盾：
- 分公司 B 被授权使用 A 创建的路由，但列表若按 `dept_id` 过滤，B 看不到；
- 列表 join `sys_carrier` 时，拦截器会对承运商表追加 `carrier.dept_id IN (B 子树)`，导致 A 的承运商名显示为空；
- 拦截器不允许 UNION，无法用"我创建的 ∪ 授权给我的"单条 SQL 绕过。

## 4.2 两个方案

| 方案 | 内容 | 优点 | 缺点 |
|---|---|---|---|
| **A（推荐）** | `sys_route`、`sys_route_transfer`、`sys_carrier`、`sys_route_company`、`sys_dept_apply_company` 全部豁免；路由/承运商可见性由服务层「管理范围 + 授权关系」显式控制 | 与跨公司共享语义一致；join 不被误过滤；开单匹配简单可靠 | 突破"业务表必须参与数据权限"的既有规范，豁免清单从 9 张扩到 14 张；可见性靠代码与测试保证 |
| **B（保守）** | 路由/承运商继续参与数据权限；列表拆"我创建的"与"授权给我的"两次查询在内存合并；路由表冗余 `carrier_name` 快照 | 不扩大豁免面 | 复杂、易漏；分页/排序/导出失真；承运商改名不同步；开单匹配需绕过拦截器 |

建议：**A**。理由：授权关系本身已经承担了可见性职责，再用 `dept_id` 过滤会形成双重口径且互相矛盾；且 `sys_dept` 早已豁免，机构类配置表走豁免有先例。

## 4.3 若采纳 A 的填充规则

| 表 | 豁免 | dept_id | create_by / 审计 |
|---|---|---|---|
| `sys_region` | 是 | 无 | `create_time/update_time` |
| `sys_region_sync_log` | 是 | 无 | `triggered_by` |
| `sys_dept_apply_company` | 是 | 无（用 `leaf_dept_id/company_dept_id`） | `create_by` 服务层显式写 |
| `sys_route_company` | 是 | 无（用 `company_dept_id`） | `grant_by/grant_time` 服务层显式写 |
| `sys_route` | 是 | 保留 `dept_id`，创建时服务层写入创建人主机构 | `create_by/update_by` 服务层显式写（豁免表不触发 fill） |
| `sys_route_transfer` | 是 | 同上 | 同上 |
| `sys_carrier` | 是 | 保留 `dept_id`，承运商页服务层按管理范围过滤 | 同上 |

注意：**豁免表不会触发 `DataScopeMetaObjectHandler`**（`DataScopeMetaObjectHandler.java:25-27,45-47`），所有审计字段必须在服务层显式赋值，不能依赖 fill。

## 4.4 若采纳 B 的额外工作

- 路由列表拆两次查询并内存合并分页（排序、总数、导出全部要重做）；
- `sys_route`/`sys_route_transfer` 增加 `carrier_name` 快照列，承运商改名需批量刷新；
- 开单匹配需 `DataScopeContext.runAsSystem` 包裹后显式按公司过滤，存在误用风险。

---

# 第五部分 · 对已完成模块的影响面

| 模块 | 影响 |
|---|---|
| 机构管理 | 抽屉新增「适用公司」（仅末端网点，多选必填）；列表新增列与筛选；保存时 diff 维护关系；删除/末端变更清理关系；创建时默认值按角色区分 |
| 机构下拉 | 新增 `/depts/manage-scope`；路由/授权必须使用；旧 `/depts/options` 不动但标注遗留风险 |
| 路由管理 | 两 Tab 增加「创建公司」「授权分公司」列；行操作「授权」、工具条「批量授权」；创建时按管理范围选机构；同键复用与补授权提示 |
| 承运商管理 | 无授权；可见性按 R1 决策；若走 A，页面过滤由服务层显式实现 |
| 数据权限 | 豁免清单需增加 5 张表（region、sync_log、apply_company、route_company、route/transfer/carrier 中的路由与承运商，具体见 R1），属代码变更需评审；适配"豁免表不 fill" |
| 用户模块 | 无结构变更；"所属公司"判定复用主机构，不改用户表 |
| 区划模块 | 无变更 |
| 开单模块（未建） | 需实现匹配接口消费、路由节点快照落库、轨迹节点生成；本方案只定契约 |
| 菜单 | 不新增菜单；不引入按钮级权限点（沿用现状） |

---

# 第六部分 · 待确认项（每条给建议与理由）

| # | 问题 | 建议 | 理由 |
|---|---|---|---|
| R1 | 跨公司共享后，路由/承运商是否豁免数据权限 | **方案 A：豁免**，可见性由管理范围+授权关系显式控制 | 行级 `dept_id` 过滤与跨公司授权天然矛盾；join 会被逐表追加条件导致共享数据隐藏；UNION 又被拦截器禁止 |
| R2 | 「同样路由」判定键 | 短驳/干线=`类型+始发+到达`；外转/直达=`适用网点+目的地+类型` | 始发/到达是线路本体的端点；外转必须带适用网点，否则不同网点会被误判为同一条；类型入键以支持直达/外转并存 |
| R3 | 「总部全部授权」落地方式 | `sys_route_company` 写 `company_dept_id=0` 的标记行，`grant_source=3`，动态生效 | 单表单一事实来源；新增公司自动覆盖；授权来源枚举完整；避免路由表再加一个状态列 |
| R4 | 分公司管理员能否手动授权给其他公司 | **不能**，手动授权仅总部 | 跨公司授权影响他人可见性，属平台级操作；分公司只保留创建自动授权 |
| R5 | 分公司管理员能否把末端网点适用到其他公司 | **不能**，仅总部可跨公司设置；分公司默认且只能选本公司 | 防止分公司随意扩大共用范围，影响其他公司选择器 |
| R6 | 总部开单员是否受授权过滤 | **不受**，总部可用全部启用路由 | 总部是全局角色；授权关系只约束分公司 |
| R7 | 开单多候选排序：直达 > 网络链路 > 外转，是否成立 | **成立**；同形态再比时效、里程、跳数 | 直达时效最稳；网络链路次之；外转兜底；与建议 d 的"直达优先"一致 |
| R8 | 目的地区划层级回退 | 精确编码 → 区县 → 市逐级回退 | 区县无路由时用市级配置是行业惯例；再往上不做省级（路由最小粒度=市） |
| R9 | 总部"取消全部授权"是否快照现有公司 | **默认不快照**；弹窗提供"保留当前公司为显式授权"选项 | 不快照最干净；但全部取消会让所有分公司立即失去路由，需二次确认并允许渐进收敛 |
| R10 | 同键复用时表单差异字段如何处理 | **不覆盖**，仅补授权并提示差异未生效 | 共享路由的公共字段不能被他公司静默改写；修改走创建公司或总部 |
| R11 | 开单路由节点快照表归属 | 由开单模块建 `biz_waybill_route`（或等价表），本方案只定契约 | 快照字段与运单强绑定，放在开单域更合理；本方案不越界建表 |
| R12 | 机构管理列表是否也按管理范围过滤 | **建议是**（分公司只看本公司子树+适用末端网点） | 与路由选择器口径一致，避免"选不到但看得到"的割裂；但属行为变更，需确认 |

---

# 第七部分 · 文件清单与执行声明

本轮新增：
- `2026-11-21/route-region-design/outputs/route-region-design-v2.0.md`（本文件）

保留未改：
- `route-region-design-v1.0.md`（上一版终稿）
- `route-region-design.md`（v0.1）

核查只读：luohuo 源码、yunshu 后端/前端源码、`sys_dept` 数据（SELECT 统计）。

未执行：任何 DDL、建表、业务代码、现有文件修改。
