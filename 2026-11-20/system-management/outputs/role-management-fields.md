# 角色管理 · 方案与字段清单（v0.2，对齐 luohuo + 数据权限解耦裁决）

> 依据：luohuo-system 角色管理实际代码（后端 `app/api/v1/system/role.py`、`app/crud/role.py`、`app/models/role.py`、`database/release/01_system_base.sql`；前端 `frontend/src/views/system/role/index.vue`）；yunshu `system-management-design.md` 2.3/2.5/2.6/3.2/3.5 已定结论。
> 原则：**完全按 luohuo 的角色管理来**，只列必须不同的项；方案确认后 V5 已执行，角色后端与前端已按本文实现。
> v0.2（2026-11-20 家伟最终裁决）：**角色与数据权限彻底解耦**，`sys_role` 去掉 `data_scope`，按 11 字段设计；数据范围改挂 `sys_user.data_scope`，配置入口在用户管理。详见 `data-permission-assessment.md`。
> 状态：**已确认并执行**（V5 于 2026-11-20 18:41 由 Flyway 执行，version=5、success=1）；角色后端与前端已开发并联调通过。

---

## 0. luohuo 角色管理事实基线（核查结论）

| 项 | luohuo 实际实现 |
|---|---|
| 表 | `sys_role`：id、role_name、role_code、role_desc、data_scope、status、is_deleted、create_time、create_by、update_time、update_by、remark；唯一键仅 `uk_role_code` |
| 关联表 | `sys_role_menu(role_id, menu_id)` 唯一键；`sys_user_role(user_id, role_id, org_id)`；`sys_role_org`（自定义数据范围）；无 `sys_role_dept` |
| 接口 | `GET /roles` 分页、`GET /roles/all`、`GET /roles/{id}`、`POST /roles`、`PUT /roles/{id}`、`DELETE /roles/{id}`、`PUT /roles/{id}/status`、`GET/PUT /roles/{id}/menus`、`GET/PUT /roles/{id}/data-scope` |
| 新增校验 | 仅校验 `role_code` 唯一（「角色编码已存在」）；**未校验角色名称唯一**；名称 2–20、编码 2–20；data_scope 1–5 默认 4；data_scope=2 时必须选机构 |
| 编辑 | 编码只读（RoleUpdate 无 role_code）；名称/描述/状态/data_scope/备注可改；admin 角色：状态必须启用（「超级管理员角色不允许禁用」），data_scope 强制沿用原值 |
| 删除 | 逻辑删除；admin 拒绝（「超级管理员角色不允许删除」）；有用户关联拒绝（「该角色已分配给用户，请先解除用户关联后再删除」）；删除时物理清 `sys_user_role / sys_role_menu / sys_role_org` |
| 菜单授权 | 整体覆盖（先删后插）；回显只 `setCheckedKeys` 叶子，父级由联动推导；提交 `checked + halfChecked`；admin 拒绝（「超级管理员角色不允许修改权限」） |
| 数据范围 | 编辑弹窗内 radio（1 全部 / 2 自定义机构 / 3 本机构 / 4 本机构及以下 / 5 仅本人），默认 4；选 2 出现机构树（多选、check-strictly）；保存走独立 `/data-scope` 接口 |
| 超管判定 | 角色编码 `role_code = "admin"` |
| portal_type | `sys_menu` 无该字段，菜单树接口无该参数，授权树本身无端过滤 |

---

## 1. 列表列清单（沿用 luohuo + yunshu 列表基座）

| # | 列名 | 最小宽 | 对齐 | 筛选类型 | 可排序 | 可导出 |
|---|---|---|---|---|---|---|
| 1 | 序号 | 80 | 居中 | — | 否 | 否 |
| 2 | 角色名称 | 150 | 左 | 文本 136（`roleName`） | 是 | 是 |
| 3 | 角色编码 | 150 | 左 | 文本 136（`roleCode`） | 是 | 是 |
| 4 | 角色描述 | 170 | 左 | 文本 136（`roleDesc`） | 是 | 是 |
| 5 | 最后修改人 | 120 | 左 | 文本 136（`updateByName`） | 是 | 是 |
| 6 | 最后修改时间 | 200 | 居中 | 日期区间 200（`updateTimeStart/End`） | 是 | 是 |
| 7 | 操作 | 200 | 居中 | — | 否 | 否 |

- 操作列：编辑 / 权限分配 / 删除（与 luohuo 一致）。
- 角色**不含任何数据权限语义**：无数据范围列、无数据范围筛选、无自定义机构配置。
- 最后修改人由 `sys_user` 关联解析；用户表未建时显示 `—`，筛选条件整体忽略（与机构管理同一降级策略）。
- 默认排序 `id asc`（luohuo `default_order=(Role.id,)`）。
- 排序、筛选均走服务端；分页默认 **20**（20/50/100），工具条顺序：重置 → 导出 → 列设置 → 分页。

### 1.1 筛选参数表

| 筛选项 | 控件 | 参数 | 匹配 |
|---|---|---|---|
| 角色名称 | 文本 | `roleName` | 包含 |
| 角色编码 | 文本 | `roleCode` | 包含 |
| 角色描述 | 文本 | `roleDesc` | 包含 |
| 最后修改人 | 文本 | `updateByName` | 包含（依赖 sys_user） |
| 最后修改时间 | 日期区间 | `updateTimeStart` / `updateTimeEnd` | 闭区间 |

> luohuo 还有 `filter_modes`（文本匹配模式切换）。yunshu 公共列表基座暂不提供该交互，统一“包含”匹配，见待确认 Q5。

---

## 2. 新增/编辑抽屉字段清单

> 容器按 yunshu 统一 `PageDrawer`；字段、校验、默认值与 luohuo 保持一致。

| # | 字段 | 控件 | 必填 | 校验 | 默认值 | 编辑态 |
|---|---|---|---|---|---|---|
| 1 | 角色名称 `roleName` | 输入框 | 是 | 2–20 字符 | 空 | 可改 |
| 2 | 角色编码 `roleCode` | 输入框 | 是 | `^\w{2,20}$`（字母/数字/下划线） | 空 | **只读禁用** |
| 3 | 角色描述 `roleDesc` | 文本域 2 行 | 否 | ≤200 | 空 | 可改 |
| 4 | 状态 `status` | 单选 | 是 | 1 启用 / 0 禁用 | 启用 | 可改（admin 禁用选项无效，后端拒绝） |
| 5 | 备注 `remark` | 文本域 2 行 | 否 | ≤500 | 空 | 可改 |
| — | 数据范围 `dataScope` | — | — | — | 固定 1（全部） | **不出现在界面**（差异 D2/D3） |

---

## 3. 权限分配（独立弹窗，沿用 luohuo）

| 项 | 规则 |
|---|---|
| 入口 | 列表操作列「权限分配」 |
| 数据源 | 全量菜单树（含目录、菜单、按钮；含禁用/隐藏节点），不按端过滤 |
| 父子联动 | `el-tree` 默认联动（`check-strictly=false`） |
| 回显 | 只 `setCheckedKeys(叶子)`，父级勾选/半选由子节点推导 |
| 提交 | `getCheckedKeys() + getHalfCheckedKeys()` 一起提交，保证目录/菜单可见 |
| 保存 | `PUT /roles/{id}/menus`，`menu_ids` 全量覆盖 |
| admin | 后端拒绝修改（「超级管理员角色不允许修改权限」）；前端入口是否置灰见 Q4 |

---

## 4. 保护与删除规则

| 规则 | 行为 / 文案 |
|---|---|
| admin 删除 | 拒绝「超级管理员角色不允许删除」 |
| admin 禁用 | 拒绝「超级管理员角色不允许禁用」 |
| admin 改权限 | 拒绝「超级管理员角色不允许修改权限」 |
| admin 名称/描述/备注 | 可改（与 luohuo 一致） |
| 有用户关联 | 拒绝「该角色已分配给用户，请先解除用户关联后再删除」；用户表未建时跳过该检查（同机构管理降级），建表后自动生效 |
| 删除方式 | 逻辑删除 `deleted=1`，同时物理清理 `sys_role_menu`（yunshu 无 `sys_role_org`） |
| 编码唯一 | `deleted=0` 范围唯一，冲突「角色编码已存在」 |
| 名称唯一 | **yunshu 建议全局唯一**（luohuo 未校验），见 Q3 |

---

## 5. 与 luohuo 的差异表（只列必须不同）

| # | 项 | luohuo | yunshu | 原因 |
|---|---|---|---|---|
| D1 | `sys_user_role.org_id` | 有，角色可按机构生效 | **无** | yunshu 已定“角色是功能角色、全机构通用”；机构级差异用 `sys_dept` 四个业务开关表达，塞进角色会与开关语义冲突 |
| D2 | 角色 `data_scope` 字段 | 有（默认 4） | **无（11 字段）** | 最终裁决：角色只控制菜单/按钮，数据范围挂用户；同一角色不同人需要不同范围时不复制角色 |
| D3 | `sys_role_org` 与数据范围配置 | 有表、有机构树、独立 data-scope 接口 | **全部没有** | 数据范围配置入口移到用户管理，角色侧不保留任何数据范围载体 |
| D4 | 数据权限列/筛选 | 列表有“数据权限”列与下拉筛选 | **不展示、不筛选** | 角色已无该字段，不能也不应展示 |
| D5 | 分页默认 | 10（10/20/50） | **20（20/50/100）** | yunshu 列表标准统一 20 |
| D6 | 角色名称唯一 | 未校验（仅 DB `uk_role_code`） | **全局唯一（Service 校验 + 唯一索引）** | yunshu 设计 2.3 明确“全局不可重名”；同名角色在用户分配下拉里无法区分 |
| D7 | 删除级联 | 清 `user_role / role_menu / role_org` | 清 `role_menu`（`user_role` 已被拦截） | yunshu 无 `role_org`；有用户关联时删除已被拦截，不会真的级联删用户关系 |
| D8 | 用户引用删除拦截 | 直接查 `sys_user_role` | 用户表未建时**跳过**，建表后自动生效 | 过渡期无 `sys_user`，不能因此阻断角色维护 |
| D9 | 菜单授权树 | 全量菜单，无端过滤 | **同左** | yunshu V3 已删 `portal_type`，luohuo 当前也无该字段，无需简化；注意不要从 yunshu V1 旧代码把 `portal_type` 带回来 |
| D10 | 角色与机构的关系 | 可通过 `user_role.org_id` 限定 | **无任何绑定** | 同 D1；`sys_user_org` 是“可登录机构范围”，不是角色绑定；机构开关也不与角色挂钩 |

> 其余（字段命名与长度、列表操作列、抽屉字段与校验、编码只读、admin 三项保护、授权树回显/提交方式、逻辑删除）**全部沿用**。

---

## 6. V5 DDL（已执行：`V5__init_sys_role.sql`）

> 脚本位置：`2026-11-20/system-management/outputs/V5__init_sys_role.sql`；已复制到 `backend/src/main/resources/db/migration/` 并由 Flyway 执行（按 V4 同样流程）。

### 6.1 `sys_role`（11 字段）

`id, role_name, role_code, role_desc, status, remark, create_by, create_time, update_by, update_time, deleted`

- 约束：`PRIMARY(id)`、`UNIQUE uk_role_code(role_code)`、`UNIQUE uk_role_name(role_name)`（待 Q3 确认）、`KEY idx_status(status)`。
- **无 `data_scope`**（与数据权限解耦）。
- 种子：`id=1, role_name=超级管理员, role_code=admin, status=1`。

### 6.2 `sys_role_menu`（5 字段）

`id, role_id, menu_id, create_time`；`UNIQUE uk_role_menu(role_id, menu_id)`、`KEY idx_role_id`、`KEY idx_menu_id`。

### 6.3 种子授权

admin 角色授权全量菜单（含按钮），与 luohuo seed 一致；新增角色默认无菜单。

---

## 7. 待确认项（请逐条勾）

| # | 问题 | 建议 |
|---|---|---|
| Q1 | 列表是否保留“序号”列（luohuo 有，yunshu 机构页没有） | **保留**，完全按 luohuo |
| Q2 | 数据权限是否确定与角色完全无关 | **已裁决：是**，角色 11 字段，不含 data_scope |
| Q3 | 角色名称是否做全局唯一 | **做**（Service + `uk_role_name`），与设计 2.3 一致 |
| Q4 | admin 角色前端是否禁用「权限分配/删除/禁用」入口 | **禁用或隐藏**，避免用户点了才报错（luohuo 只靠后端拦） |
| Q5 | 文本筛选是否需要 luohuo 的匹配模式（精确/前缀/包含） | **不需要**，统一“包含”，与机构管理一致 |
| Q6 | 是否保留“最后修改人”筛选（依赖 sys_user） | **保留**，用户表建好前忽略条件 |
| Q7 | 菜单授权树是否只显示启用菜单 | **全量**（含禁用/隐藏/按钮），与 luohuo 一致；禁用菜单由菜单管理控制 |
| Q8 | 是否需要“分配用户”入口 | **不需要**，用户模块未建，luohuo 也无此入口 |
| Q9 | 岗位/多角色上限等额外规则 | **不加**，luohuo 无限制 |
| Q10 | 最大列宽 240 / 320 | 沿用当前 320，待家伟统一规范后再改 |
