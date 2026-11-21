# 数据权限接入规范（v1.0）

> 裁决：2026-11-21 家伟确定采用**方案乙·黑名单式**——默认对所有表生效，仅显式豁免平台表。
> 开关：`yunshu.data-scope.enabled: true`（`backend/src/main/resources/application.yml`）。
> 实现：`backend/src/main/java/com/yunshu/common/datascope/`。

---

## 1. 生效范围与挂载方式

| 项 | 结论 |
|---|---|
| 拦截器 | MyBatis-Plus `DataPermissionInterceptor`（子类 `DataScopeInterceptor` 增加 SQL 形态校验） |
| 注册 | `MybatisPlusConfig#mybatisPlusInterceptor`：数据权限在前、分页在后 |
| 数据源 | 项目使用 dynamic-datasource 的路由数据源 + 单一 `SqlSessionFactory`，因此 `master`（写）与 `slave`（读）**同时生效**，无需分别注册 |
| 作用对象 | 所有 `SELECT / UPDATE / DELETE`（INSERT 不拦截，由写入填充负责归属） |
| 默认策略 | 非豁免表一律追加行级条件；豁免表不追加任何条件 |

## 2. 平台表豁免清单（变更需评审）

```
sys_menu
sys_role
sys_role_menu
sys_user
sys_user_role
sys_user_org
sys_user_menu
sys_dept
sys_region
sys_region_sync_log
sys_dept_apply_company
sys_carrier
sys_route
sys_route_transfer
sys_route_company
flyway_schema_history
```

另外，数据库系统 schema 自动豁免：`information_schema`、`mysql`、`performance_schema`、`sys`（元数据查询）。

**豁免申请方式**：新增平台配置表时，在 `DataScopeTables.EXEMPT_TABLES` 增加表名，并在本文件同步登记，提交说明写清"平台配置类，不含业务数据"。

**2026-11-21 路由/区域方案补充（家伟裁定）**：跨公司共享的配置类表可申请豁免，但必须同时满足：
1. 表本身不含需按机构隔离的业务数据（如区划、机构关系、路由授权）；
2. 在下方「豁免表可见性责任」逐表登记服务层过滤规则；
3. 越权验证覆盖列表、详情、编辑、删除、导出、匹配等入口，并留证据。

### 2.1 豁免表可见性责任（服务层负责，不允许依赖拦截器）

| 表 | 定位 | 可见性责任 |
|---|---|---|
| `sys_region` | 区划基础数据 | 平台共享，所有登录人可读；导入/同步仅总部 |
| `sys_region_sync_log` | 区划任务日志 | 平台共享，日志列表按需展示；`triggered_by` 由服务层显式写入 |
| `sys_dept_apply_company` | 末端网点适用分公司 | 跨公司关系数据；机构选择器与管理范围按登录人所属公司显式计算，`create_by` 由服务层显式写入 |
| `sys_carrier` | 承运商主数据 | **豁免表可见性由服务层负责**：`SysCarrierServiceImpl` 按管理范围（总部全部；分公司=本公司子树 ∪ 适用本公司末端网点）显式过滤 `dept_id`；详情/编辑/删除先做可见性校验，失败返回 403 文案且不泄露记录是否存在；被路由引用禁止删除 |
| `sys_route` | 短驳/干线共享路由 | **豁免表可见性由服务层负责**：`SysRouteServiceImpl` 列表/详情/导出统一按 `owner_company_id=本公司 OR 授权(本公司) OR 授权(0)` 过滤；编辑仅 owner；删除需无其他授权；禁止裸 `selectById` |
| `sys_route_transfer` | 外转/直达共享路由 | **豁免表可见性由服务层负责**：同 `sys_route`，开单匹配另按授权关系过滤，`apply_dept_id` 只作业务字段、`dept_id` 仅审计锚点 |
| `sys_route_company` | 路由授权关系 | **豁免表可见性由服务层负责**：授权读取/写入仅总部（取消授权允许分公司删本公司行）；`grant_by` 由服务层显式写入；无 `dept_id`，不豁免会被追加不存在列 |

后续新增同类表（路由、承运商、路由授权）在同一小节登记后方可加入豁免清单。

## 3. 四档规则

锚点：**当前机构 `currentOrgId`（会话）→ 无则回落用户主机构 `sys_user.dept_id`**。

- 当前机构默认取用户主机构；切换后立即生效，档位语义不变。
- 切换范围仅限「主机构 ∪ `sys_user_org`」，越界由服务端拒绝，客户端不得传任意机构 id。
- 当前机构被收回授权/停用后，请求按主机构回落，避免旧会话越权。
- 退出登录、切换用户、会话失效时当前机构随会话一并销毁。
- 菜单与按钮权限不随机构变化（角色不绑机构），只有数据范围变化。

| 档位 | 含义 | 追加条件 |
|---|---|---|
| 1 | 全部数据 | 无 |
| 3 | 本机构 | `dept_id = 当前机构（缺省主机构）` |
| 4 | 本机构及以下（默认） | `dept_id IN (当前机构 + 全部子孙机构)` |
| 5 | 仅本人 | `create_by = 当前用户ID` |
| 2 | 自定义机构（预留未实现） | 用户模块已拒绝写入；历史脏数据按 5 处理 |

超管（`id=1` 或含 `admin` 角色）始终按档位 1 处理。客户账号（`user_type=2`）不适用数据范围，按档位 5（最严格）处理，后续由客户域规则接管。

## 4. 上下文与 fail-closed

- `DataScopeContext` 为 ThreadLocal，由 `DataScopeWebInterceptor` 在 `preHandle` 解析 `Authorization: Bearer` 后写入，`afterCompletion` 清理；线程复用不会串号。
- **缺上下文不默认放行**：拦截器对非豁免表追加 `1 = 0` 并记录 WARN。
- 异步/定时任务需要访问业务数据时，必须显式声明系统上下文：

```java
DataScopeContext.runAsSystem(() -> businessService.rebuildStatistics());
```

- `runAsSystem` 不代表有主机构：系统上下文新增业务数据时若 `dept_id` 为空会直接报错，须显式赋值。

## 5. 写侧规则

### 5.1 新增自动填充

`DataScopeMetaObjectHandler` 对非豁免表生效：`dept_id` 取上下文当前机构（缺省主机构），`create_by/update_by` 取当前用户；缺上下文或锚点为空直接抛错。

**实体必须声明填充注解，否则 MyBatis-Plus 不会触发填充**：

```java
@TableField(fill = FieldFill.INSERT)
private Long deptId;

@TableField(fill = FieldFill.INSERT)
private Long createBy;

@TableField(fill = FieldFill.INSERT_UPDATE)
private Long updateBy;
```

### 5.2 编辑/删除/详情越权校验

不能依赖"更新 0 行"静默通过。服务层在操作前显式调用：

```java
dataScopeGuard.assertWritable(entity.getDeptId(), entity.getCreateBy()); // 编辑/删除
dataScopeGuard.assertReadable(entity.getDeptId(), entity.getCreateBy()); // 详情
```

不满足范围时抛出 `BizException(403, "无权操作其他机构或其他人的数据")`。配合 SQL 拦截形成双保险：查询不可见 + 写入显式拒绝。

## 6. SQL 结构处理策略（宁可报错，不可放行）

| 场景 | 策略 |
|---|---|
| 单表 SELECT / UPDATE / DELETE | 正常追加条件 |
| JOIN | 对每个非豁免表分别追加该表别名的条件；豁免表（如 `sys_dept`）只做关联不加条件 |
| 分页 count | `PaginationInnerInterceptor` 在数据权限之后，count SQL 使用同一条件，口径一致 |
| UNION / 集合查询 | 直接拒绝：`数据权限：不支持 UNION/集合查询，已拒绝执行` |
| FROM / JOIN 子查询 | 直接拒绝 |
| WHERE / HAVING 子查询（含 EXISTS、IN 子查询） | 直接拒绝 |
| 多表 UPDATE / DELETE | 直接拒绝 |
| SQL 解析失败 | 直接拒绝（`DataScopeException`） |
| 非豁免表缺 `dept_id` / `create_by` | 生成的 SQL 执行报错（fail-closed），接入时必须补列 |
| 子树超过 2000 个机构 | 抛错，要求调整数据范围或分批查询 |

`DataScopeException` 由全局异常处理统一转成 `code=403`，不会静默返回空结果。

## 7. 导出、统计、聚合与缓存

- 导出、统计、聚合查询**必须复用列表同一套查询条件**（同一 mapper 方法或同一 wrapper），禁止另写无条件 SQL。
- 若为性能引入缓存，缓存 key 必须包含：`租户/用户ID + 数据范围档位 + 主机构ID + 机构子树版本`；机构或用户范围变更时必须失效对应 key。
- 现有 `PermissionService` 缓存按 `userId` 维度，属于功能权限，不参与数据过滤。

## 8. 新模块接入检查清单

1. 业务表必须包含 `dept_id`（归属机构）与 `create_by`（归属人）两列，并建索引。
2. 实体必须给 `deptId/createBy/updateBy` 加 `@TableField(fill = ...)`。
3. 表名不得加入豁免清单（豁免仅限平台配置表）。
4. 禁止在业务查询中使用 UNION、FROM/JOIN 子查询、WHERE 子查询；确需复杂统计，改为多步查询后在内存聚合，或在 SQL 中显式 `runAsSystem` 并单独评审。
5. 列表、详情、导出、统计必须同条件（同一 wrapper）。
6. 编辑/删除/详情前必须调用 `DataScopeGuard` 做显式越权校验。
7. 新增接口不得自行拼接 `dept_id` 条件，避免与拦截器重复。
8. 异步/定时任务访问业务数据必须 `DataScopeContext.runAsSystem`，并在代码注释说明原因。
9. 新增平台配置表需走豁免申请（见第 2 节）。
10. 上线前用探针表验证四档、JOIN、分页 count、导出与越权拒绝。

## 9. 本期验证结论（2026-11-21）

- 临时探针表 `e2e_datascope_probe`（含 `dept_id/create_by`）实测 26 项全部通过：四档过滤、JOIN、分页 count、COUNT、导出、无登录态 fail-closed、UNION 拒绝、新增自动填充、跨机构编辑/删除拒绝。
- 探针表验证后已 `DROP`，建表前后表清单与行数完全一致；未写入任何业务数据，未触碰 `luohuo_system` / `luohuo_install_verify`。
- 平台页面接口基线（机构树、机构分页、角色分页、角色下拉、菜单树、用户分页、admin 直授回显）在启用前后**逐项一致**；机构/角色/菜单/用户页面 UI 冒烟通过。

## 10. 已知限制

- 子查询与 UNION 全部拒绝，复杂报表需拆查询或单独评审。
- 客户域数据规则未接入，当前客户账号按仅本人处理。
- 机构切换只改数据范围锚点（当前机构→缺省主机构），不改菜单/按钮权限；切换后前端重建页面缓存，但标签页保留。
- 当前机构仅限「主机构 ∪ sys_user_org」；授权被收回或机构停用时按主机构回落。
