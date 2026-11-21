# 用户管理 · 方案与字段清单（v0.1，参考 luohuo + yunshu 已定裁决）

> 依据：luohuo `backend/app/api/v1/system/user.py`、`crud/user.py`、`models/user*.py`、`schemas/user.py`、`frontend/src/views/system/user/index.vue`、`components/UserPermDialog.vue` 逐项核对；yunshu `system-management-design.md` 2.4–2.9 / 3.1–3.3 已定口径。
> 状态：**方案与 V6 已产出，未执行建表、未改业务代码**；等家伟确认后按 V4/V5 同流程执行。

---

## 1. 与 luohuo 的必要差异（其余沿用）

| # | 项 | luohuo | yunshu |
|---|---|---|---|
| U1 | 主机构字段 | `org_id`，可空 | `dept_id`；内部员工必填、客户可空（家伟裁定） |
| U2 | 用户类型 | 无 | `user_type` 1=内部员工 2=客户 |
| U3 | 数据范围 | `data_scope` NULL=继承角色，可配置 1–5 | `sys_user.data_scope` 默认 4，四档 1/3/4/5；客户不适用；**不做“继承角色”**（角色无数据范围） |
| U4 | 用户-角色 | 带 `org_id` 限定机构 | 不带 `org_id`，角色全机构通用 |
| U5 | 可登录机构 | `sys_user_org_auth` + `is_default` | `sys_user_org` 只存额外机构；默认机构固定为主机构，不设 is_default |
| U6 | 自定义数据范围 | `sys_user_org`（多机构） | 本期不做，2 档预留未实现，不建表 |
| U7 | 分页 | 10（10/20/50） | 20（20/50/100），与机构/角色一致 |
| U8 | 密码哈希 | bcrypt | BCrypt（引入 `spring-security-crypto`，不启用 Security 过滤链） |
| U9 | 登录态 | JWT | 本期先用服务端 token 会话（内存 + 8 小时过期），不做 JWT |

---

## 2. 列表（13 列，服务端筛选/排序/分页/导出/列设置/合计）

| # | 列 | 最小宽 | 对齐 | 筛选 | 排序 | 导出 |
|---|---|---|---|---|---|---|
| 1 | 序号 | 80 | 居中 | — | 否 | 否 |
| 2 | 用户名 | 130 | 左 | 文本 136（username） | 是 | 是 |
| 3 | 真实姓名 | 120 | 左 | 文本 136（realName） | 是 | 是 |
| 4 | 手机号 | 130 | 左 | 文本 136（phone） | 是 | 是 |
| 5 | 邮箱 | 160 | 左 | 文本 136（email） | 是 | 是 |
| 6 | 所属机构 | 140 | 左 | 机构树（deptId） | 是 | 是 |
| 7 | 可登录机构 | 160 | 左 | — | 否 | 是 |
| 8 | 角色 | 150 | 左 | 多选（roleId） | 否 | 是 |
| 9 | 直接授权 | 130 | 居中 | 有/无（hasDirectPerm） | 否 | 是 |
| 10 | 状态 | 100 | 居中 | 启用/禁用 | 是 | 是 |
| 11 | 最后修改人 | 120 | 左 | 文本 136（updateByName） | 是 | 是 |
| 12 | 最后修改时间 | 200 | 居中 | 日期区间 200 | 是 | 是 |
| 13 | 操作 | 260 | 居中 | — | 否 | 否 |

- 操作：权限 / 编辑 / 重置密码 / 删除；`id=1`（admin）的「权限」「删除」置灰，重置密码保留（与 luohuo 一致）。
- 「直接授权」显示 `+n / -n`；统计口径剔除“角色已含但被重复勾选”的项（修复 luohuo 计数虚高）。
- 默认排序 `id asc`；工具条顺序：重置 → 导出 → 列设置 → 分页。
- 可登录机构列 = 主机构 + `sys_user_org` 额外机构拼接；姓名类筛选依赖同一张表，始终可用。

---

## 3. 新增/编辑抽屉（三 Tab，家伟已确认）

### Tab 1 基础信息

| 字段 | 控件 | 必填 | 校验 | 编辑态 |
|---|---|---|---|---|
| 用户名 `username` | 输入框 | 是 | `^\w{2,20}$`，deleted=0 唯一 | **只读** |
| 密码 `password` | 密码框 | 新增必填 | 6–20 字符；确认密码一致 | 不出现（走重置密码） |
| 真实姓名 `realName` | 输入框 | 是 | 2–20 字符 | 可改 |
| 手机号 `phone` | 输入框 | 否 | `1[3-9]\d{9}` | 可改 |
| 邮箱 `email` | 输入框 | 否 | 标准邮箱格式，≤100 | 可改 |
| 用户类型 `userType` | 单选 | 是 | 1 内部员工 / 2 客户，默认 1 | 可改 |
| 所属机构 `deptId` | 机构树单选 | 内部员工必填；客户可空 | 机构须启用；客户选择「无」 | 可改 |
| 可登录机构 `authOrgIds` | 机构树多选 | 否 | 默认 = 所属机构；仅内部员工 | 可改 |
| 状态 `status` | 单选 | 是 | 启用/禁用，默认启用；admin 不可禁用 | 可改 |
| 备注 `remark` | 文本域 | 否 | ≤500 | 可改 |

- 客户账号：`deptId` 可空、可登录机构固定为“仅本账号”（不展示多选），**不得为填字段虚构机构**。
- 修改用户类型时，若由客户改内部员工，必须补选 `deptId` 才能保存。

### Tab 2 角色

- 角色多选下拉（数据源 `GET /roles/options`，只返回启用角色）；展示 `角色名称（编码）`。
- 内部员工**至少 1 个角色**；客户账号允许 0 个（客户权限由客户域规则决定）。
- 保存为整体覆盖 `sys_user_role`。

### Tab 3 数据权限

- 下拉四项：1 全部 / 3 本机构 / 4 本机构及以下 / 5 仅本人；默认 4；2 自定义机构不出现，后端收到 2 直接拒绝。
- 客户账号：Tab 显示「客户账号不适用数据范围」并禁用下拉，不提交 `dataScope`。
- 关联校验：3/4 要求 `deptId` 非空；5 不要求；admin 用户不受数据范围约束（展示值仅存档）。
- `yunshu.data-scope.enabled=false` 保持不变，本期不启用任何过滤。

---

## 4. 权限配置弹窗（账号直授，对齐 luohuo）

- 入口：列表行「权限」，`id=1` 不显示；不能配置自己（服务端拦截）。
- 两个页签：
  - **额外授权**：启用菜单树（禁用菜单不出现）；角色已含节点置灰并标「角色已含」；父子联动；回显只 `setCheckedKeys(叶子)`；提交 `checked + halfChecked`。
  - **显式禁止**：`check-strictly` 不联动；按 `denied_ids` 精确回显；提交只送 checked；禁止父级会级联禁止全部子孙（后端计算）。
- 保存：`PUT /users/{id}/perms`，整体覆盖；`granted − denied` 禁止优先；菜单必须存在且启用，否则 400。
- 超管（id=1 或 admin 角色）不可配置，后端返回 400「超级管理员拥有全部权限，无需配置」。
- 列表直接授权计数：额外授权数 / 禁止数（级联子孙不计入统计，只统计直接行）。

---

## 5. 保护与校验

| 规则 | 行为 |
|---|---|
| admin 删除/禁用 | 拒绝「超级管理员不允许删除/禁用」 |
| 删除自己 | 拒绝「不能删除当前登录账号」 |
| 删除方式 | 逻辑删除 `deleted=1`，同时物理清理 `sys_user_role`、`sys_user_org`、`sys_user_menu` |
| 用户名唯一 | Service 校验（deleted=0），冲突「用户名已存在」 |
| 手机号/邮箱 | 只做格式校验，不做唯一（与 luohuo 一致） |
| 重置密码 | 固定重置为 `123456`，BCrypt 落库，提示「密码已重置为 123456」 |
| 角色/机构引用 | 角色必须启用；机构必须存在且启用 |
| 删除角色/机构/菜单 | 角色删除前查 `sys_user_role`（已实现）；机构删除前查 `sys_user.dept_id` 与 `sys_user_org.org_id`；菜单删除时清理 `sys_role_menu` / `sys_user_menu` 对应行 |

---

## 6. 登录与 `/auth/info` 改造（本期必做，替换占位实现）

- `POST /api/v1/auth/login`：按 `username` 查 `deleted=0`；BCrypt 校验；`status=1`；内部员工校验 `dept_id` 及其祖先链全部启用；签发服务端 token（内存会话，8 小时）。
- `GET /api/v1/auth/info`：按 `Authorization: Bearer <token>` 解析用户；无 token/过期 → HTTP 401。
  - 普通用户：`roles` = 启用角色编码；`menus`/`perms` 按「角色菜单 ∪ 额外授权 − 禁止级联 → 补祖先链 → 过滤启用」计算；树组装剪掉祖先断链节点，不提升为根。
  - 超管（id=1 或含 admin 角色）：返回全部启用菜单与全部按钮 perms。
  - `user` 返回 `id/username/realName/deptId/deptName/authOrgs[]`。
- 本期**不做**每个接口的按钮级鉴权拦截（`@RequirePerm`），仅登录态与数据下发真实化；接口越权拦截列为下一阶段，避免与数据权限开关混在一起。

---

## 7. 接口清单

| 方法 | 路径 | 说明 |
|---|---|---|
| GET | `/api/v1/system/users/page` | 分页 + 筛选 + 排序 |
| GET | `/api/v1/system/users/{id}` | 详情（含角色、可登录机构、数据范围） |
| POST | `/api/v1/system/users` | 新增（含角色、机构、数据范围） |
| PUT | `/api/v1/system/users/{id}` | 编辑（用户名/密码不可改） |
| DELETE | `/api/v1/system/users/{id}` | 逻辑删除 + 清理关联 |
| PUT | `/api/v1/system/users/{id}/status` | 启用/禁用 |
| PUT | `/api/v1/system/users/{id}/reset-password` | 重置为 123456 |
| GET | `/api/v1/system/users/{id}/perms` | 回显：角色已含 / 额外授权 / 禁止 |
| PUT | `/api/v1/system/users/{id}/perms` | 整体覆盖直授 |
| GET | `/api/v1/system/roles/options` | **新增**：启用角色下拉 |

---

## 8. V6 DDL（已产出，暂存未执行）

脚本：`V6__init_sys_user.sql`（同目录），4 张表 + admin 种子：

- `sys_user`：16 字段（含 `user_type`、`data_scope`）；`idx_username/idx_dept/idx_status`；`dept_id` 可空（内部员工由 Service 强制必填）。
- `sys_user_role`：`PRIMARY KEY(user_id, role_id)` + `idx_role`；无 `org_id`。
- `sys_user_org`：`PRIMARY KEY(user_id, org_id)` + `idx_org`；只存额外机构。
- `sys_user_menu`：`id` 主键 + `uk_user_menu` + 两个索引；`grant_type` 1/0。
- 种子：`admin / 123456`（BCrypt `$2a$10$ao1cv30...`，已用 bcrypt 校验 `true`），`dept_id=1`，`user_type=1`，`data_scope=4`；绑定角色 id=1。

---

## 9. 待确认（建议项）

| # | 问题 | 建议 |
|---|---|---|
| Q1 | 本期是否连账户直授弹窗一起做（`sys_user_menu`） | **做**，设计 2.7 与对照文档已定 |
| Q2 | 是否本期替换登录与 `/auth/info`（真实账号密码） | **做**，否则用户模块无法登录验证；接口级越权拦截留给下一阶段 |
| Q3 | 可登录机构是否本期做 | **做**，字段少且已设计；客户账号不展示 |
| Q4 | 重置密码固定 123456 | **沿用** luohuo；首次登录强制改密本期不做 |
| Q5 | 列表是否 13 列全上 | **全上**，与 luohuo 一致；后续可列设置隐藏 |
