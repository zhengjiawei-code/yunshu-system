# 去掉“端归属”维度 · 变更影响清单（V3 已执行）

> 2026-11-20 | 家伟裁决：不再在菜单上区分内部端/客户端，客户功能改为“建客户账号 + 授菜单”。
> **2026-11-20 已获批准并执行 V3**：备份表 54 行 → 停后端 → Flyway 执行 → 新代码启动；本文保留影响与回滚说明。

## 1. 变更结论

| 项 | 变更前 | 变更后 |
|---|---|---|
| 菜单维度 | `portal_type`：ADMIN / CLIENT | **删除**，菜单不再分端 |
| 权限过滤 | 角色/直授 + status + deleted + portal | 角色/直授 + status + deleted |
| 客户功能 | 客户端菜单靠 portal 下发 | 客户账号 + 角色/直授菜单 |
| 预开单 | CLIENT，内部端不显示 | 普通菜单，全员可见（是否授予客户由账号权限决定） |
| 缓存 key（规划） | `auth:info:{userId}:{portal}` | `auth:info:{userId}` |
| 菜单列表列 | 12 列（含端归属） | 11 列 |
| 菜单筛选 | 4 个（名称/类型/端归属/状态） | 3 个（名称/类型/状态） |
| 菜单抽屉 | 含“端归属”单选 | 不含 |
| 级联规则 | 目录改端归属 → 级联子孙 + 确认弹窗 | **作废** |

## 2. 数据库影响

### 2.1 变更对象

| 对象 | 类型 | 定义 | 处理 |
|---|---|---|---|
| `sys_menu.portal_type` | 列 | `varchar(20) NOT NULL DEFAULT 'ADMIN'` | `DROP COLUMN` |
| `sys_menu.idx_portal` | 索引 | `(portal_type, status)` | `DROP INDEX` |
| `sys_menu_portal_backup_20261120` | 新增表 | `menu_id, portal_type, backed_up_at` | 回滚备份，执行后保留 |

### 2.2 已有数据处理

- 现有 54 条菜单：53 条 `ADMIN`、1 条 `CLIENT`（id=1201 预开单）；
- **不删任何菜单行、不改名称/路由/权限**；仅把 `portal_type` 的值备份后随列一起删除；
- 删除后“预开单”自然成为普通菜单，符合“通过客户账号授权控制”的新口径；
- 种子文件 V1/V2 保持不动（历史迁移不可改），V3 在其后收口；新库按 V1→V2→V3 顺序执行同样得到无端归属的 54 条。

### 2.3 是否可回滚

- **可回滚，但有窗口限制**：依赖 `sys_menu_portal_backup_20261120`；备份表被删除后无法还原 ADMIN/CLIENT 区分；
- 回滚脚本：`rollback_V3__restore_sys_menu_portal.sql`（加字段 → 回填 → 加索引）；
- MySQL DDL 隐式提交，V3 本身不能靠事务回滚；
- 回滚不修改 `flyway_schema_history`，需人工登记，避免后续误判版本状态。

### 2.4 执行顺序（确认后）

1. 停止后端（8080），确认无写入；
2. 把 `V3__drop_sys_menu_portal.sql` 复制到 `backend/src/main/resources/db/migration/`；
3. 启动新后端（已不含 portalType 代码），Flyway 自动执行 V3；
4. 校验：`flyway_schema_history` 出现 version=3、`sys_menu` 无 `portal_type`、tree 接口 54 节点；
5. 若失败：按回滚脚本恢复，并回退代码。

> 必须先停后端：旧代码查询 `portal_type`，列删除后会直接 `Unknown column` 报错。

### 2.5 实际执行记录（2026-11-20 16:37）

| 步骤 | 结果 |
|---|---|
| 备份表 | `sys_menu_portal_backup_20261120` 创建并写入 **54 行**（53 ADMIN / 1 CLIENT） |
| 停后端 | 已停止 PID 31637 |
| 迁移 | 放入 `db/migration/V3__drop_sys_menu_portal.sql`，重新打包启动 |
| Flyway | 日志：`Migrating schema to version "3 - drop sys menu portal"` → `Successfully applied 1 migration` |
| 版本表 | version=3，success=1，execution_time=166ms |
| 表结构 | `portal_type` 列不存在、`idx_portal` 索引不存在 |

## 3. 代码影响（本轮已改完，编译通过）

### 3.1 后端

| 文件 | 改动 |
|---|---|
| `SysMenu.java` | 删除 `portalType` 字段 |
| `MenuSaveRequest.java` | 删除 `portalType` 与 `@NotBlank` |
| `MenuVO.java` | 删除 `portalType` |
| `SysMenuService.java` | `routeTree()/buttonPerms()` 去掉 portal 参数 |
| `SysMenuServiceImpl.java` | 去掉 portal 过滤、校验、赋值、`cascadePortal()` 级联 |
| `AuthController.java` | 调用改为无参，`/auth/info` 返回全部启用菜单与权限 |

### 3.2 前端

| 文件 | 改动 |
|---|---|
| `types/menu.ts` | 删除 `PortalType` 与 `portalType` |
| `api/system/menu.ts` | 载荷、mock 创建/更新去 portal，删除级联逻辑 |
| `views/system/menu/index.vue` | 删除端归属列、筛选项、筛选状态与条件 |
| `views/system/menu/components/MenuFormDrawer.vue` | 删除端归属表单项、默认值、规则、回显、级联确认 |
| `views/system/menu/components/ButtonFormDrawer.vue` | 按钮不再继承 parent.portalType |
| `router/menuToRoute.ts` | 路由 meta 去掉 portalType |
| `views/_placeholder/index.vue` | 去掉端归属展示 |
| `mock/menus.ts` | 40 个菜单统一无 portal；预开单并入运营管理；删除 clientMenus |
| `mock/menuManage.ts` | 删除 PRE_CREATE 特殊合并与 portal 赋值 |

### 3.3 验证

- 后端 `mvn compile` / `package`：通过；
- 前端 `npm run build`（含 vue-tsc）：通过；
- 后端已用新代码重启（带 `YUNSHU_DB_PASSWORD`），实测：
  - `GET /api/v1/system/menus/tree` → 54 节点（14 目录 + 40 菜单），响应中 **无 `portalType` 字段**；
  - `GET /api/v1/auth/info` → 54 个菜单，**含预开单 id=1201**（已作为普通菜单下发）；
  - `POST /api/v1/system/menus` 不传 `portalType` → 200，id=2419 落库，DB 旧列取默认 `ADMIN`；
  - 测试用 2419 已软删并物理清除，存活数恢复 54；
- 数据库 `portal_type` 列与索引已删除，备份表保留 54 行；
- 前端实测：菜单管理 54 行、页面无“端归属”文字、筛选仅 3 项、新增目录抽屉无该字段；筛选“预开单”命中 1 行。

## 4. 文档同步

| 文档 | 处理 |
|---|---|
| `system-management-design.md` | 权限过滤/缓存 key/对照表去掉 portal |
| `luohuo-user-menu-alignment.md` | 端归属相关“必要调整”删除，改为无端设计 |
| `module-tree.md` | 菜单表删除“端归属”列，预开单改为普通菜单 |
| `menu-management-fields.md` | 列表 11 列、筛选 3 项、抽屉删字段、级联规则作废、API 去 portal |
| `scaffold-plan.md`（额外） | 同步删除端归属维度，避免与四份文档冲突 |

## 5. 必须重新决定的点（不自行拍板）

1. **客户账号如何识别**（已裁决）
   - 家伟裁决：`sys_user` 增加 `user_type`（1=内部员工，2=客户），**仅标识用途，不参与任何权限、过滤、校验与菜单下发逻辑**；
   - 客户能看到什么仍完全由角色/账户直授决定。
2. **客户登录入口**
   - 客户与内部员工是否共用同一个登录页/域名？
   - 建议：共用登录页；登录后菜单仍完全由账号授权决定，`user_type` 只用于展示与运营识别，不参与菜单下发。
3. **客户默认角色**
   - 新建客户账号时是否自动绑定“客户”角色（只含预开单等客户功能）？
   - 建议：建 `customer` 内置角色，创建客户账号时默认绑定，仍可用直授增减。
4. **菜单可见性默认值**
   - 角色为空的新账号：应“无任何菜单”还是“只读工作台”？建议无菜单，仅工作台固定首页。
5. **数据隔离**
   - 客户只能看自己的运单，这属于数据权限，不是菜单权限；本期不做，但应在客户模块立项时一并设计（`customer_id` 归属）。
6. **原 `portal_type` 的历史含义**
   - 确认没有其他系统/报表依赖 `ADMIN/CLIENT` 字段；当前仅本库使用，已确认无外部依赖。

## 6. 待确认清单（连同前 11 条一起勾）

- A. V3 执行：**已批准并完成**（见 2.5）。
- B. 回滚方案：**已批准**（依赖备份表，DDL 不可事务回滚）。
- C. `sys_user.user_type`：**已裁决**，1 内部 / 2 客户，仅标识。
- D. 客户默认角色：是否默认绑定内置 `customer` 角色，待客户模块立项时确认。
- E. 客户与内部员工是否共用登录页：待客户模块立项时确认。
- F. 其他系统依赖 `portal_type`：已确认无（仅本库使用）。
