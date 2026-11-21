# 数据权限（data_scope）luohuo 核查与 yunshu 方案

> 依据：luohuo `backend/app/utils/data_scope.py`、各 CRUD 调用点、`app/services/export_service.py`、`app/api/v1/system/org.py`、`database/release/01_system_base.sql`。
> 2026-11-20 家伟最终裁决：**数据权限与角色彻底解耦**；`sys_role` 去掉 `data_scope`（11 字段）；数据范围挂 `sys_user.data_scope`；配置入口在用户管理；过滤由开关控制、默认关闭。
> 本文只做方案与评估，**未改代码、未改表**；V5 待确认后再出。

---

## 1. 五档位定义（源码原文）

`app/utils/data_scope.py` L27–L39：

| 值 | 常量 | 源码标签 | 实际行为（`_add_rule` L168–L190） |
|---|---|---|---|
| 1 | `SCOPE_ALL` | 全部数据 | `scope.is_all = True`，查询不加条件 |
| 2 | `SCOPE_CUSTOM` | 自定义机构 | `scope.org_ids |= role_org 中该角色配置的机构ID`；**只含所选机构，不含子孙** |
| 3 | `SCOPE_ORG` | 本机构 | `scope.org_ids.add(anchor_org_id)`，只含**当前锚定机构本身**，不含子孙 |
| 4 | `SCOPE_ORG_SUB` | 本机构及以下 | `descendant_map[anchor_org_id]`，**含自身 + 全部子孙**（`get_descendant_org_ids` 注释“含自身”，L78–L91） |
| 5 | `SCOPE_SELF` | 仅本人 | `scope.allow_self = True`，按 `create_by = 当前用户` 判定（用户表传 `self_field=User.id`） |

**默认值：4（本机构及以下）**，三处一致：
- 建表 `sys_role.data_scope TINYINT NOT NULL DEFAULT 4`（`01_system_base.sql` L38）；
- 请求体 `RoleCreate.data_scope = Field(default=4)`（`schemas/role.py` L17）；
- 前端 `form.data_scope: 4`（`role/index.vue` L295）。

**4 档口径确认：就是“本机构及以下”，不是“本机构”**，并且包含本机构自身。

### 1.1 角色与机构如何结合

- 锚定机构 `anchor_org_id = user.current_org_id or user.org_id`（L201）——即 **token 里的当前切换机构，缺省回落到用户所属主机构**，不是你登录时可选的机构列表（`sys_user_org_auth`）。
- `sys_user_role.org_id` 限定角色只在某机构生效：`org_id IS NULL` 的角色全机构通用；非空时只在 `org_id = anchor_org_id` 时参与（L227–L230）。
- 多角色：**并集**（`scope.org_ids |= ...`）。
- 用户级覆盖优先：`sys_user.data_scope` 非空时直接用用户配置，不再看角色（L209–L216）。yunshu 设计里 `sys_user` 没有该字段。
- 超管：`user.id == 1` 或持有启用且未删除的 `admin` 角色 → `is_all=True`（L204）。
- 无任何范围时：`query.filter(false())` 返回空集，**不退化全量**（L262–264）。

---

## 2. 实际作用范围（重要）

**机制是手工拼条件，不是全局 SQL 改写、也不是拦截器。**

| 能力 | 是否覆盖 | 证据 |
|---|---|---|
| 列表查询 | 覆盖，但需各模块显式调用 `apply_data_scope(query, Model, scope)` | 运单、派送、签收、客户、承运商、对账、资金台账、用户、仪表盘等 30+ 处调用 |
| 列表总数 | 覆盖（`query.count()` 在加范围之后） | 各 `list_*` CRUD |
| 详情读取 | 覆盖，调 `ensure_in_scope(row, scope)` → 403「无权访问该数据」 | `customer_recon.py` L1718/L1728 等 |
| 编辑/删除 | 覆盖，写入前 `ensure_in_scope` / `ensure_org_in_scope` | `system/org.py` L196/L227 等 |
| 导出 | 覆盖，但不是自动：`export_service` 每个模块手工声明 `extra: {"scope": ...}`，导出复用列表函数 | `export_service.py` L303–L360 |
| 统计/合计 | 部分覆盖：仪表盘、资金台账显式加范围；列表合计依赖列表查询本身；**没有统一钩子** | `dashboard.py` L106/L149、`finance_ledger.py` L1386 |
| 树接口 | **仅机构树**用 `visible_org_ids` 过滤并补祖先；**菜单树完全不过滤**（菜单是全局资源） | `system/org.py` L54/L83；`system/menu` 无 scope 调用 |
| 角色/菜单管理 | **不过滤**（`export_service` 中 `system.role` / `system.menu` / `system.route` 的 `extra=None`） | `export_service.py` L311–L341 |
| 用户列表 | 覆盖，且 `self_field=User.id`（“仅本人”= 看自己账号） | `crud/user.py` L239 |

结论：luohuo 的数据权限是**逐接口接入**的白名单式实现，漏接的接口就是不过滤；不存在“一加拦截器全站生效”。

---

## 3. yunshu 方案（按最终裁决）

### 3.1 存储方案：`sys_user.data_scope` 单字段 + 按需关联

**结论：主字段用 `sys_user.data_scope`（TINYINT，默认 4）；`2=自定义机构` 的多机构明细另放关联表，不在用户表存 JSON。**

| 方案 | 评价 |
|---|---|
| 单字段 `sys_user.data_scope` | **采用**。1/3/4/5 四档只靠该字段即可判定；与 `dept_id` 对齐，查询零 join |
| 单字段存 JSON 机构数组 | 否决。无法索引、无法约束机构有效性、后续统计/校验都要在应用层解析 |
| 独立 `sys_user_data_scope` 表 | 否决。每个用户一行、绝大多数只有档位，纯冗余 |
| 自定义机构关联表 `sys_user_scope_dept(user_id, dept_id)` | **仅当 2 档启用时新增**；不与现有 `sys_user_org` 混用（后者语义是“可登录机构范围”） |

> 本期若只做 4 档，不需要任何关联表；字段先建，成本接近零。

### 3.2 “本机构”锚点：本期固定 `sys_user.dept_id`

- yunshu 没有 `current_org_id`，本期**不引入机构切换**：3 档= `dept_id` 本身，4 档= `dept_id + 全部子孙`。
- 将来若做“顶栏切换机构”，锚点改为 token 中的当前机构，缺省回落 `dept_id`；解析函数只改一处，不影响调用方。
- 不采用“可登录机构列表”作为锚点：那是登录范围（`sys_user_org`），不是数据归属。

### 3.3 档位开放范围：四档可选，2 档预留未实现

| 档位 | 本期 | 判定依据 |
|---|---|---|
| 1 全部 | **开放** | 不加条件；管理员/跨机构岗位常用 |
| 2 自定义机构 | **预留未实现** | 需要 `sys_user_scope_dept` 与机构选择器；本期不建表、不暴露 |
| 3 本机构 | **开放** | `dept_id = 用户主机构`，不含子孙 |
| 4 本机构及以下 | **开放（默认）** | `dept_id + 全部子孙` |
| 5 仅本人 | **开放** | `create_by = 当前用户`，不依赖机构树 |

> 下拉四项：全部 / 本机构 / 本机构及以下 / 仅本人；默认 4。四档均单字段可判定，不需要关联查询。2 档在文档与枚举中保留值，界面不出现，后端收到 2 直接拒绝（「自定义机构范围暂未开放」）。

### 3.3.1 切换档位时的关联校验

| 档位 | 前置校验 | 不满足时 |
|---|---|---|
| 1 全部 | 无 | — |
| 3 本机构 | `user.dept_id` 必须非空 | 拒绝：「该用户未设置所属机构，不能选择“本机构”」 |
| 4 本机构及以下 | `user.dept_id` 必须非空 | 同上；默认值落在 4，因此**新增用户时 `dept_id` 必填**，否则无法保存 |
| 5 仅本人 | `dept_id` 可为空；需 `sys_user.id` 与业务表 `create_by` 可对齐 | 新增用户保存时不校验 dept_id，但列表查询会按 `create_by` 过滤 |
| 2 自定义机构 | 本期不开放 | 后端拒绝 |

补充：
- admin 用户（id=1 或持有 admin 角色）不受数据范围约束，其 `data_scope` 仅作展示，改档位不影响其可见数据。
- 用户从 3/4 改为 5 时，`dept_id` 保留不清空（用于组织展示与后续切回）。
- 从 5 改为 3/4 且 `dept_id` 为空时，保存直接拒绝，不允许落一个“永远查不到数据”的配置。
- **客户账号（`user_type=2`）不适用数据范围**（家伟裁定）：`dept_id` 可空，数据权限 Tab 不展示/不可选，后端不以客户账号的 `data_scope` 作为过滤依据；客户可见数据由客户域自身规则决定，**不得为了填字段而虚构机构**。`data_scope` 默认 4 只对内部员工（`user_type=1`）生效。

### 3.4 默认值 4 如何保证不漏

1. **DB 默认值**：`data_scope TINYINT UNSIGNED NOT NULL DEFAULT 4`，任何直接 INSERT 都落到 4。
2. **实体默认值**：`SysUser.dataScope = 4` 初始值，MP 插入非空字段。
3. **新增用户接口显式赋值**：`user.setDataScope(req.getDataScope() == null ? 4 : req.getDataScope())`，不依赖前端传值。
4. **DDL 之后补一条校验 SQL**：`SELECT count(*) FROM sys_user WHERE data_scope IS NULL OR data_scope NOT IN (1,2,3,4,5)` 必须为 0。
5. **接口层校验**：只允许 1/3/4/5；2 直接拒绝（「自定义机构范围暂未开放」），其它值 400；客户账号（`user_type=2`）不接受 `data_scope` 作为过滤依据。
6. 三层任意一层生效都不会出现 NULL；DB 默认值是最后兜底。

### 3.5 配置入口：用户编辑抽屉的独立 Tab「数据权限」

- **采用**：用户新增/编辑抽屉内新增第三个 Tab（基础信息 / 角色 / 数据权限），与机构抽屉的「授权」Tab 同一交互范式。
- 不采用列表操作列“数据授权”按钮：数据范围是用户属性，不是独立高频操作；放抽屉里与角色分配同屏，保存一次完成。
- Tab 内容：数据范围下拉，四项（全部 / 本机构 / 本机构及以下 / 仅本人），默认“本机构及以下”；说明文案“默认本机构及以下，锚点为所属主机构；仅本人按创建人判定”；保存走用户更新接口的 `dataScope` 字段，不单独开接口。
- 权限：仅拥有 `system:user:edit` 的人可见；非超管修改他人范围时校验“新范围不得超出自己的范围”（本期开关关闭，可先不校验）。

### 3.6 本期是否启用过滤：**默认关闭**

- `yunshu.data-scope.enabled=false` 为默认值；关闭时 handler 恒返回 `null`，SQL 与现在完全一致。
- 字段、实体、用户抽屉 Tab、默认值、校验**可以上线**，不影响任何现有查询。
- 启用过滤需要的前置件：
  1. `sys_user` 建表并接入登录态（当前用户上下文）；
  2. `DataScopeContext` + `DataScopeFilter` + `DataPermissionHandler` 三个组件；
  3. 业务表白名单与 `dept_id` 列映射（当前只有 `sys_dept`，且它用 `id` 过滤）；
  4. 写操作的 `MetaObjectHandler`（自动填 `dept_id`）与 `ensureInScope`；
  5. 机构树/上级选择器的可见范围裁剪；
  6. SQL 回归集（分页 count、join、exists、group by、union）；
  7. 若引入 ES，需在 DSL 同步条件（当前无 ES）。

---

## 4. 如果启用：影响哪些接口

### 4.1 前置依赖
1. `sys_user` 建表（`sys_role` 与角色管理同期建；V4 只有 `sys_dept`）。
2. 用户范围解析：`admin`（id=1 或 admin 角色）直通；普通用户 `dept_id + data_scope`；无范围返回空集（不能退化为全量）。
3. 本期不引入 `current_org_id`，锚点固定 `dept_id`。

### 4.2 拦截器方案需要的组件
| 组件 | 作用 |
|---|---|
| `@DataScope` 注解 | 标注需要过滤的方法/表，声明机构字段名（业务表 `dept_id`，`sys_dept` 用 `id`） |
| `DataPermissionHandler` | 拼 `dept_id IN (...)` / `id IN (...)`；开关关闭、admin、注解缺失时返回 null |
| ThreadLocal 上下文 | 请求进入时解析一次范围，避免每个 mapper 重复查用户 |
| 表白名单/黑名单 | `sys_menu / sys_role / sys_role_menu / sys_user / sys_user_role / sys_user_menu / flyway_schema_history` 等平台表**必须排除** |
| 缓存 | 按 `userId` 缓存可见机构集合，用户范围/机构变更后失效 |

### 4.3 影响哪些已有接口

| 接口 | 影响 | 说明 |
|---|---|---|
| `GET /system/depts/tree` | **要改** | 按可见机构裁剪，并补祖先保证树可展开（同 luohuo `visible_org_ids`） |
| `GET /system/depts/page` | **要改** | `sys_dept` 没有 `dept_id`，过滤字段是 **`id`**，不能套通用 `dept_id` 规则 |
| `GET /system/depts/options` | **要改** | 上级机构选择器必须裁剪，否则可把机构挂到无权限父级 |
| `GET /system/depts/{id}` | **要改** | 详情越权 403 |
| `POST/PUT/DELETE /system/depts` | **要改** | 新增校验 `parentId` 在范围内；编辑/删除校验目标机构 |
| `PUT /system/depts/sort` | **要改** | 同级排序需校验父级在范围内 |
| `GET /system/menus/**` | **不改** | 菜单是全局资源，平台配置类接口不挂数据权限 |
| `POST/PUT/DELETE /system/menus` | 不改 | 功能权限由按钮 perms 控制 |
| `GET /system/roles/**` | **不改** | 角色已与数据权限解耦，属平台配置；只用功能权限控制 |
| `GET /system/users/**` | **不改** | 用户管理本身是平台配置；用户列表不应因被管理者范围而隐藏 |
| `GET /auth/info` | 不改 | 菜单下发属于功能权限，与数据范围无关 |
| 未来业务表（运单/对账等） | 全部要接 | 每张表需有 `dept_id`，并逐个确认注解/白名单 |

> 机构管理（`sys_dept`）是唯一既属“平台配置”又承载数据归属的特殊对象：它的增删改查**本期随开关一起生效**；若启用，必须裁剪树与上级选择器，否则管理员可以绕过范围把机构挂到越权父级。

### 4.4 导出 / 统计 / ES
- **导出**：yunshu 当前是前端拿 `pageSize=10000` 再生成 CSV，只要列表接口过滤了，导出自动跟随，无需额外改造；若将来改服务端导出，必须复用同一查询。
- **统计/合计**：列表合计取服务端 `total`，拦截器生效后自动正确；自定义 `SUM/COUNT` 聚合 SQL 需要显式注解，MP 的 count 可被拦截，手写 XML 聚合不会自动加条件。
- **ES**：yunshu 当前**没有 ES**（后端无依赖、无索引代码）。将来若引入，SQL 拦截器完全无效，必须在 ES 查询 DSL 里单独注入 `dept_id terms`，并保证机构变更后索引同步。

### 4.5 工作量级别
- **仅字段 + 配置入口 + 骨架（开关关闭）**：**小到中等**。用户表加字段、抽屉 Tab、handler 恒 null、文档与规范。
- **启用后只覆盖机构/用户域**：**中等**。6 个机构接口裁剪、树与选择器、缓存与失效、回归测试。
- **全业务域接入（每张业务表 + 导出 + 统计）**：**偏大**。风险不在拦截器本身，而在“漏接即越权”与“多数据源/手写 SQL 不生效”的核查成本。
- 建议节奏：本期只落字段与入口；启用时先机构/用户域灰度，再逐业务模块接入。

---

## 5. 待确认（家伟）

1. ~~四档是否先不暴露~~ → **已裁决：开放 1/3/4/5，2 预留未实现**。
2. ~~是否暂不建关联表~~ → **已裁决：暂不建 `sys_user_scope_dept`**。
3. ~~用户抽屉三个 Tab~~ → **已裁决：确认**。
4. ~~开关名与默认值~~ → **已裁决：`yunshu.data-scope.enabled=false`**。
5. ~~平台配置类接口排除是否写入规范~~ → **已裁决：写入（前后端规范已更新）**。
6. 剩余待确认：`sys_user.dept_id` 是否设为**必填**（因默认 4 档依赖主机构）；建议必填。
