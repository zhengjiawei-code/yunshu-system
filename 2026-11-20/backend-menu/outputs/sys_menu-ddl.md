# sys_menu 建表说明（待家伟审核）

> 配套文件：`V1__init_sys_menu.sql`
> 依据：`2026-11-20/project-scaffold/outputs/scaffold-plan.md` 第 9 节；`menu-management-fields.md` 第 5 节。
> **本轮未执行任何 SQL**：没有连库、没有建表、没有写数据。MySQL 客户端虽已安装（8.0.46），但没有连接信息，不做猜测。

---

## 1. 这张表是什么

`sys_menu` 是**菜单权限表**，服务于「系统管理 → 菜单管理」模块，同时被侧栏渲染、动态路由生成、按钮级鉴权三处消费。

一棵树、三种节点：

| menu_type | 含义 | 有路由 | 有组件 | 有权限标识 | 子节点 |
|---|---|---|---|---|---|
| 1 | 目录：只做分组 | 是（路径段） | 否 | 否 | 目录 / 菜单 |
| 2 | 菜单：对应一个页面 | 是（绝对路径） | 是 | 选填 | 仅按钮 |
| 3 | 按钮：页面内操作点 | 否 | 否 | 必填 | 无 |

表级 COMMENT 已写清「给哪个模块用、三类节点分别是什么、被哪些能力消费」，不是只写“菜单表”。

---

## 2. 逐字段说明

| 字段 | 类型 / 可空 / 默认 | COMMENT 原文 | 为什么这么写 |
|---|---|---|---|
| id | BIGINT UNSIGNED / 否 / 自增 | 节点主键ID，自增；被 sys_role_menu.menu_id 与 sys_user_menu.menu_id 引用 | 菜单 ID 会进入关系表与前端路由 name，用无符号 BIGINT 留足空间；注释点明被谁引用，避免后续改类型 |
| parent_id | BIGINT UNSIGNED / 否 / 0 | 上级节点ID；0=顶级节点；目录的父级只能是0或目录，菜单的父级必须是目录，按钮的父级必须是菜单 | 用 0 代替 NULL 表示顶级，便于 `WHERE parent_id=0` 查询；注释把三类节点的挂载规则写死，和已确认口径一致 |
| menu_name | VARCHAR(50) / 否 / 无 | 节点显示名称；目录/菜单展示在侧栏、面包屑与标签页，按钮展示在角色授权树；同一父节点下不可重名 | 50 字够用且可控；注释说明三类节点的展示位置，避免被当成纯技术字段 |
| menu_type | TINYINT UNSIGNED / 否 / 1 | 节点类型：1=目录（仅分组，不对应页面）2=菜单（对应一个前端页面组件并生成路由）3=按钮（页面内操作权限点，不生成路由） | 明确三个取值的业务含义与是否生成路由，不是“菜单类型”这种同义反复；默认 1 与建目录的主流程一致 |
| route_path | VARCHAR(200) / 是 / NULL | 前端路由地址：目录存路径段（如 /system），菜单存绝对路径（如 /system/user），按钮为NULL；非空时全局唯一 | 目录与菜单的存法不同，必须在注释里说清，否则联调必错；唯一性由 Service 保证（见第 4 节） |
| component | VARCHAR(200) / 是 / NULL | 前端组件路径，相对 src/views 且不含 .vue 后缀（如 system/user/index）；仅菜单填写，目录与按钮为NULL | 约定“相对路径 + 不带后缀”，前端可直接拼 `../views/${component}.vue`；注释限制只有菜单可填 |
| redirect | VARCHAR(200) / 是 / NULL | 目录默认跳转地址，通常指向第一个子菜单；留空由前端自动选择；仅目录使用，菜单与按钮为NULL | 说明“留空”是正常用法而非缺数据，避免实施时被当成必填 |
| perms | VARCHAR(100) / 是 / NULL | 权限标识，格式为 模块:实体:动作（如 system:user:add）；按钮必填且全局唯一，菜单选填，目录为NULL | 把格式、谁必填、唯一范围写进注释；权限标识是鉴权 Key，写不清会导致重复授权 |
| icon | VARCHAR(64) / 是 / NULL | Element Plus 图标组件名（如 Setting）；目录与菜单必填，按钮为NULL | 存组件名而不是图片地址，前端走白名单渲染；注释点明来源，防止有人塞 URL |
| portal_type | VARCHAR(20) / 否 / ADMIN | 端归属：ADMIN=公司内部端（内部员工使用），CLIENT=客户端（发货客户自助使用）；决定菜单下发给哪个端 | 取值含义与使用方写清楚；这是 Q5 预开单/开单揽收边界在数据层的落点 |
| terminal_type | VARCHAR(20) / 否 / PC | 适用终端：PC=电脑浏览器，APP=移动端，PC,APP=两端通用；本期只建设PC端，其余取值仅作预留 | 解释三个取值，并注明本期只做 PC，避免误以为 APP 已支持 |
| sort_order | INT / 否 / 0 | 同级排序号，升序排列（数值越小越靠前）；同一父节点下建议按10递增，便于后续插入 | 说明排序方向与“按 10 递增”的实施约定，避免同级插一条就要全表改号 |
| visible | TINYINT(1) / 否 / 1 | 侧栏是否显示：1=显示，0=隐藏；隐藏后不在侧栏出现，但已授权用户仍可通过URL直接访问 | 明确“隐藏≠禁用”，这两个状态最容易被混用 |
| status | TINYINT(1) / 否 / 1 | 启用状态：1=启用（正常下发路由并参与权限校验），0=禁用（不生成路由，按钮鉴权直接拒绝） | 写清禁用后的实际后果（不下发、不鉴权），而不是只写“状态” |
| keep_alive | TINYINT(1) / 否 / 1 | 前端页面是否缓存：1=缓存（切走再返回保留筛选与滚动位置），0=不缓存；仅菜单类型有效 | 说明缓存的具体表现，并限定仅菜单有效 |
| affix | TINYINT(1) / 否 / 0 | 标签页是否固定：1=固定标签且不可关闭，0=可关闭；仅菜单类型有效 | 说明固定的是标签页，不是菜单，避免误解 |
| remark | VARCHAR(500) / 是 / NULL | 备注，记录该节点的业务用途、负责人或变更原因，便于运维排查 | 说明备注该写什么，而不是一句“备注” |
| create_by | BIGINT UNSIGNED / 是 / NULL | 创建人用户ID，关联 sys_user.id；系统初始化数据为NULL | 说明关联对象与 NULL 的合理场景（初始化数据无操作人） |
| create_time | DATETIME / 否 / CURRENT_TIMESTAMP | 创建时间，插入时自动生成，后续更新不变 | 明确自动生成、不随更新变化，避免业务层重复赋值 |
| update_by | BIGINT UNSIGNED / 是 / NULL | 最后修改人用户ID，关联 sys_user.id；系统初始化数据为NULL | 同 create_by |
| update_time | DATETIME / 否 / CURRENT_TIMESTAMP ON UPDATE | 最后修改时间，插入时生成，更新时自动刷新 | 说明由数据库自动刷新，前端“最后修改时间”列直接取此字段 |
| deleted | TINYINT(1) / 否 / 0 | 逻辑删除标记：0=未删除，1=已删除；查询必须带 deleted=0，删除前需校验子节点与角色/用户引用 | 说明查询与删除的前置条件，防止漏过滤导致菜单“复活” |

---

## 3. 索引设计

| 索引 | 字段 | 为什么建 |
|---|---|---|
| PRIMARY | id | 主键；关系表按 menu_id 回查 |
| idx_parent_sort | (parent_id, sort_order) | 菜单树按父节点分组、按排序号取子节点，是最高频查询 |
| idx_type_status | (menu_type, status) | 菜单管理按类型筛选、只取启用节点下发路由 |
| idx_portal | (portal_type, status) | 登录后按端归属 + 状态过滤菜单树 |
| idx_perms | (perms) | 按钮权限标识校验与授权查询 |

没有建 `(parent_id, menu_name)` 唯一索引：软删除后同名重建会冲突，改为 Service 层校验“同级名称唯一、全局路径唯一、按钮权限标识全局唯一”。

---

## 4. 唯一性为什么放在应用层

本表使用 `deleted` 逻辑删除。如果加 `UNIQUE(route_path)`，删除后的旧行仍占用路径，新建同名路由会失败；加 `UNIQUE(route_path, deleted)` 也只能容纳一条已删除记录。

因此：

- 同级名称唯一、路由全局唯一、按钮 perms 全局唯一 → 由 Service 在 `deleted=0` 范围内校验；
- 数据库只保留普通索引保证查询性能；
- 并发下的最终兜底可在后续加“应用层锁 + 二次校验”，本期不做。

---

## 5. 与前端、接口的对应

- 前端字段为 camelCase（`menuName` / `routePath` / `portalType` / `terminalType` / `keepAlive`），后端实体按驼峰映射，Jackson 统一输出。
- 管理页 12 列与表字段对应：`menu_name`、`menu_type`、`route_path`、`component`、`perms`、`portal_type`、`terminal_type`、`sort_order`、`visible`、`status`、`update_time`、操作列。
- `redirect` 为目录默认跳转，管理页目录表单有、菜单表单无。
- 接口契约仍按方案 10.7 的 6 个方法：树查询、详情、新增、修改、删除、排序。

---

## 6. 本轮明确没做的事

- 没有执行任何 `CREATE TABLE` / `INSERT` / `ALTER`。
- 没有连接任何数据库（生产、测试、本地都没有）。
- 没有写入菜单种子数据；种子 SQL 仍停留在 `project-scaffold/work/menu-seed.sql` 设计稿，需另行确认后执行。
- 没有创建 `sys_user` / `sys_role` / `sys_role_menu` 等表（不在本轮菜单表范围）。

---

## 7. 需要家伟提供 / 拍板

| # | 事项 | 说明 |
|---|---|---|
| ① | 数据库连接信息 | **已授权建库建表**，但本机 root 无密码连接被拒（ERROR 1045）。需要本地 MySQL 的账号与密码，以及确认库名 `yunshu` |
| ② | 建表方式 | 已定：Flyway，V1 随应用启动执行 |
| ③ | 本机 JDK 17 与 Maven | 已授权安装 `openjdk@17` 与 `maven`（Homebrew），只装 brew 目录，不动系统默认 Java |
| ④ | 菜单种子数据 | 已定：V2 迁移写入 14 目录 + 40 菜单 |

---

## 8. 审核检查清单

- [ ] 表级 COMMENT 是否说清用途与模块
- [ ] 22 个字段是否每个都有业务含义 + 取值含义
- [ ] `menu_type` / `portal_type` / `terminal_type` / `status` / `visible` 的取值是否与前端一致
- [ ] 索引是否够用且不过度
- [ ] 是否同意唯一性放在应用层
- [ ] 数据库账号与密码
- [ ] 数据库连接信息与建表方式
- [ ] JDK / Maven 安装授权
