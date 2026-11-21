# luohuo 账户直授实现要点 + yunshu 对齐设计

> 2026-11-20 | 依据：luohuo 源码逐行核对，非推断。
> v1.1（2026-11-20）：yunshu 已按家伟裁决移除 `portal_type` 端归属，本文第 3、8 节同步去掉 portal 维度。
> 涉及文件：
> - 表结构：`backend/database/init.sql` L156–171、`backend/database/migrate_user_perm.sql`、`backend/database/release/01_system_base.sql`
> - 模型：`backend/app/models/user_menu.py`
> - 合并逻辑：`backend/app/crud/user.py` L412–542（`get_user_perms` / `get_effective_menu_ids` / `assign_user_menus`）
> - 菜单树：`backend/app/crud/menu.py` L143–154（`get_user_menu_tree`）
> - 接口：`backend/app/api/v1/system/user.py` L253–329；`backend/app/api/v1/auth.py` L144–202
> - 鉴权：`backend/app/dependencies.py` L86–137
> - 前端：`frontend/src/components/UserPermDialog.vue`、`frontend/src/views/system/user/index.vue` L75–84 / L188–200 / L466–473

---

## 1. 存储结构（逐项核对）

luohuo 的 `sys_user_menu` 实际 DDL：

```sql
CREATE TABLE `sys_user_menu` (
    `id`          BIGINT     NOT NULL AUTO_INCREMENT COMMENT '主键',
    `user_id`     BIGINT     NOT NULL COMMENT '用户ID',
    `menu_id`     BIGINT     NOT NULL COMMENT '菜单/按钮ID',
    `grant_type`  TINYINT    NOT NULL DEFAULT 1 COMMENT '1-额外授权，0-显式禁止',
    `create_time` DATETIME   NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `create_by`   BIGINT              DEFAULT NULL COMMENT '操作人ID',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_user_menu` (`user_id`, `menu_id`),
    KEY `idx_user_id` (`user_id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT ='用户-菜单直接授权表';
```

| 问题 | 事实 |
|---|---|
| 用哪张表/字段 | `sys_user_menu`，核心字段 `user_id / menu_id / grant_type` |
| 与角色菜单表的关系 | 完全独立的第三张表；同一个 `menu_id` 既可能在 `sys_role_menu`，也可能在 `sys_user_menu`，靠合并逻辑裁决 |
| 是否有类型字段 | 有，`grant_type`：**1=额外授权，0=显式禁止**（不是菜单类型） |
| 是否有有效期 | **没有**；授权长期有效，只能手动改 |
| 是否有机构维度 | **没有**；直授是账号级、全局生效（luohuo 的角色菜单按 `user_role.org_id` 分机构，直授不分——两者不一致） |
| 主键/唯一 | 代理主键 `id` + 唯一键 `(user_id, menu_id)`；同一菜单同一用户只能有一条记录，grant/deny 互斥 |
| 审计 | 只有行级 `create_by/create_time`；覆盖保存会先删后插，**没有变更历史** |

---

## 2. 合并逻辑（不是简单并集）

`crud/user.py::get_effective_menu_ids`（L475–509）的真实顺序：

```
1) role_ids   = sys_role_menu JOIN sys_role JOIN sys_user_role
                （role.status=1 AND role.is_deleted=0，且按 user_role.org_id 限定机构）
2) granted, denied = sys_user_menu 按 grant_type 拆开（不做机构过滤）
3) denied 级联：denied 自身 + 其全部子孙（children_map 只含未删除菜单）
4) effective = (role_ids ∪ granted) − cascade(denied)
5) 祖先补全：为 effective 中每个 id 逐级补 parent_id，直到 0
6) 去重：全程用 Set，最后 sorted 返回
```

结论：

| 问题 | 事实 |
|---|---|
| 并集还是覆盖 | **并集后再减**：角色 ∪ 额外授权，再减去禁止项及其子孙 |
| 直授能否减少角色权限 | **能**。`grant_type=0` 即显式禁止，且**禁止父级会级联禁止全部子孙**（对话框原文：“禁止父级菜单会连带禁用其下全部按钮”） |
| 重复怎么处理 | Set 去重；同一 `(user_id, menu_id)` 唯一键保证库内不重复；保存时 `granted_set = granted − denied`，**禁止优先** |
| 只授权按钮会怎样 | 祖先补全会把目录/菜单补进来，保证菜单树不断链 |

`get_user_perms`（L412–434）再做一次过滤：只取 `is_deleted=0 AND status=1 AND perms 非空` 的菜单权限标识，`distinct` 去重。

---

## 3. 过滤顺序（与 status / menu_type 的关系）

luohuo 的真实顺序：

1. **角色侧**：`role.status=1 AND role.is_deleted=0`（角色禁用/删除即不参与）；
2. **菜单关系侧**：只加载 `is_deleted=0` 的菜单做父子关系与祖先补全；
3. **合并**：角色 ∪ 直授 − 禁止级联；
4. **菜单树侧**：`get_user_menu_tree` 再过滤 `is_deleted=0 AND status=1`，然后组树；
5. **权限标识侧**：同样过滤 `is_deleted=0 AND status=1 AND perms!=''`，`distinct`；
6. **menu_type**：**不过滤**，目录/菜单/按钮都参与；按钮作为树的叶子节点出现，按钮的 `perms` 直接进入权限列表；
7. **portal（端归属）**：luohuo **没有这个维度**；yunshu 也已于 2026-11-20 移除 `portal_type`，两边一致，只按 `status/deleted` 过滤。

---

## 4. 前端授权界面

- 组件：`components/UserPermDialog.vue`（620px dialog）。
- 入口：用户列表行操作 **“权限”** 按钮，`v-if="row.id !== 1"`（超管不显示），受 `system:user:assign` 控制。
- 结构：`el-tabs` 三个页签——**额外授权 / 显式禁止 / 数据范围**。
  - **额外授权树**：父子联动勾选（`check-strictly=false`）；角色已含的节点 **disabled 置灰**，右侧 tag「角色已含」，按钮节点 tag「按钮」；回显时只 `setCheckedKeys` 叶子节点，父级半选自动推导；提交时把 **checked + halfChecked** 一起送后端，保证目录/菜单可见。
  - **显式禁止树**：`check-strictly`，不做父子联动，按 `denied_ids` 精确回显；提交只送 checked。
  - **数据范围页签**：继承角色 / 1–5 档位 / 自定义机构树（本期 yunshu 不做界面，不展开）。
- 保存：先 `PUT /{user_id}/perms`，再 `PUT /{user_id}/data-scope`；成功后关闭弹窗并**只刷新用户列表**，不刷新当前登录人的会话。
- 用户列表：有派生列「直接授权」显示 `+n / -n`，并有筛选「有/无直接授权」（`has_direct_perm`）。
- 与角色授权页的差异：角色授权是“给角色勾菜单”，单向；用户权限是“角色继承结果之上做加减”，额外授权树还会把角色已含项置灰，避免重复勾。

---

## 5. 边界与校验（含发现的缺口）

luohuo 已做：

- 目标用户不存在 → 404；
- 不能配置**自己** → 400；
- 不能配置**超管 id=1** → 400（前后端都拦）；
- 目标用户必须在自己数据范围内（`ensure_in_scope`）；
- **防提权**：非超管只能授/禁“自己有效菜单范围内”的 id，超出 → 403；
- 同一 payload 中 `granted − denied`，禁止优先；
- 整体覆盖保存：先删该用户全部直授行，再按 grant/deny 插入。

luohuo 的缺口（yunshu 不照抄）：

| # | 缺口 | 影响 | yunshu 对策 |
|---|---|---|---|
| 1 | 保存时不校验 `menu_id` 是否存在/是否已删除/是否启用 | 可写入垃圾 ID；授权已禁用菜单后“看起来授权成功但实际无效” | Service 校验：菜单必须存在、未删除、启用，否则 400 |
| 2 | 禁用/删除的祖先会导致子节点在树里“升级为顶级” | 菜单树断链、侧栏出现孤儿子项 | 组树前先剪掉“祖先链不完整”的节点，不提升为根 |
| 3 | 直授是全局的，角色菜单却按机构过滤 | 多机构下语义不一致（同一账号切机构，角色权限变、直授不变） | yunshu 角色不按机构绑定，直授全局，**语义天然一致**，无需改 |
| 4 | 授权弹窗展示全部菜单（含禁用） | 可勾禁用菜单，保存后无效 | 权限树只给启用菜单；禁用菜单不出现 |
| 5 | 无变更审计（覆盖删除旧行） | 无法追溯谁在何时改了哪些授权 | 保存前后 diff 写操作日志（可直接落 `sys_oper_log`，若暂无则先记录结构化日志） |
| 6 | `granted_count` 统计含“角色已含但被重复勾选”的项 | 列表 `+n` 偏大 | 保存时剔除角色已含项，或统计时与角色菜单差集 |
| 7 | 无缓存 | 不存在缓存不一致，但每次鉴权都查库 | yunshu 若启用 30 分钟缓存，必须显式失效（见第 7 节） |
| 8 | 删除菜单时未级联清理 `sys_user_menu` | 残留关系行（查询时被 `is_deleted=0` 过滤，不影响结果） | 菜单删除时同步清理 `sys_user_menu` 与 `sys_role_menu` 的该 `menu_id`，避免脏数据 |

---

## 6. 超管与直授的关系

`dependencies.py::is_super_admin`：`user.id == 1` **或** 拥有启用角色 `role_code=admin`。

- `/auth/info`：超管直接返回**全部启用菜单 + 全部权限标识**，完全不查 `sys_user_menu`；
- 权限配置接口：超管 id=1 直接 400「超级管理员拥有全部权限，无需配置」；前端也不显示“权限”按钮；
- `require_perms` / `ensure_perm`：超管一律放行；
- 因此直授对超管**无意义**，也不会产生“超管被禁止某项权限”的情况。

yunshu 沿用该口径（id=1 或 admin 角色），并额外保护 admin 角色不可删除/禁用。

---

## 7. 缓存与刷新

- luohuo **没有任何权限缓存**（无 Redis；`require_perms` 每次查从库），直授保存后**服务端立即生效**；
- 目标用户已打开的页面不会自动变：侧栏菜单要重新调用 `/auth/info`（刷新/重新登录）才更新；
- yunshu 的 scaffold-plan 9.6 规划了 30 分钟权限缓存（`userId` 维度），这是与 luohuo 的**必要差异**：
  - 保存直授后必须删除 `auth:info:{userId}`；
  - 角色授权变更、用户禁用、菜单状态变更同样要清；
  - 建议缓存 key 统一由 `AuthCacheService.evictUser(userId)` 管理，避免漏清。

---

## 8. yunshu 对齐后的设计（逐项）

### 8.1 完全沿用

| 项 | yunshu 口径 |
|---|---|
| 表结构 | `sys_user_menu(id, user_id, menu_id, grant_type, create_time, create_by)`，唯一键 `(user_id, menu_id)`，`grant_type` 1=授权 / 0=禁止 |
| 合并公式 | `(角色菜单 ∪ 额外授权) − 禁止项及子孙`，再补祖先链；禁止优先 |
| 去重 | Set + 唯一键；保存时 `granted − denied` |
| 权限标识 | 合并后的菜单集合再过滤 `deleted=0/status=1/perms 非空`，distinct |
| menu_type | 不过滤，按钮作为叶子参与，按钮 perms 进权限列表 |
| 前端形态 | 用户列表行“权限”按钮 + 弹窗；额外授权树父子联动、角色已含置灰、只回显叶子、提交 checked+halfChecked；禁止树 `check-strictly` 精确回显 |
| 保存方式 | 整体覆盖（先删后插），禁止优先 |
| 超管 | id=1 或 admin 角色；不可配置、不显示入口、鉴权直接放行 |
| 防提权 | 非超管只能操作自己有效菜单范围内的 id |
| 自保护 | 不能配置自己、不能配置超管 |

### 8.2 因 yunshu 差异必须调整

| 项 | 调整 | 为什么必须调 |
|---|---|---|
| 禁用菜单 | 权限树不展示禁用菜单；保存校验启用状态 | luohuo 展示了但授权无效，用户会以为配置成功；yunshu 直接不给选 |
| 祖先断链 | 组树前剪掉“祖先链不完整”的节点，不提升为根 | luohuo 的 `get_user_menu_tree` 会把父级禁用/删除的子节点变成顶级节点，侧栏会冒出孤立菜单；yunshu 必须修 |
| 菜单删除 | 删除菜单时同步删除 `sys_role_menu` / `sys_user_menu` 中该菜单的关系行 | luohuo 只逻辑删菜单，关系行残留；yunshu 在菜单删除逻辑里一并清理，避免脏数据 |
| 缓存 | 保存直授后清 `auth:info:{userId}` | yunshu 有 30 分钟权限缓存，luohuo 没有；不清会出现“改了权限 30 分钟不生效” |
| 审计 | 保存前后 diff 写操作日志 | luohuo 覆盖保存丢失历史；yunshu 新系统直接补上，成本低 |
| 统计口径 | 列表“直接授权 +n/-n”剔除角色已含项 | 避免 `+n` 虚高；也避免前端置灰项被重复勾选后计数 |
| 数据范围页签 | 本期不做（字段与钩子已留，界面不开放） | 家伟已裁决本期不做数据权限界面 |

### 8.3 yunshu 最终 DDL

```sql
CREATE TABLE sys_user_menu (
  id          BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
  user_id     BIGINT UNSIGNED NOT NULL COMMENT '用户ID，引用 sys_user.id；账号级直授，全机构生效',
  menu_id     BIGINT UNSIGNED NOT NULL COMMENT '菜单/按钮ID，引用 sys_menu.id；必须存在、未删除且启用',
  grant_type  TINYINT NOT NULL DEFAULT 1 COMMENT '授权类型：1=额外授权（在角色权限之外追加），0=显式禁止（从角色权限中剔除，并级联禁止其全部子孙）',
  create_by   BIGINT UNSIGNED DEFAULT NULL COMMENT '操作人用户ID，记录是谁做的这次授权',
  create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '授权时间；整体覆盖保存时重建，不保留历史（历史走操作日志）',
  PRIMARY KEY (id),
  UNIQUE KEY uk_user_menu (user_id, menu_id),
  KEY idx_user_id (user_id),
  KEY idx_menu_id (menu_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
  COMMENT='用户-菜单直接授权表：在角色权限之上做账号级加减，禁止优先并级联子孙';
```

### 8.4 yunshu 合并实现口径（伪代码，不在本轮落地）

```
roleIds   = 启用角色(未删除)的 role_menu.menu_id
granted   = sys_user_menu where grant_type=1
denied    = sys_user_menu where grant_type=0
cascade   = denied ∪ 全部子孙(未删除菜单)
effective = (roleIds ∪ granted) − cascade
effective = 补全祖先链(effective)
tree      = effective ∩ deleted=0 ∩ status=1
perms     = tree 中 perms 非空的菜单，distinct
// 树组装前剪掉祖先链不完整的节点，禁止升级为根
```

---

## 9. 一句话总结

luohuo 的直授不是“只加不减”的白名单，而是 **角色权限之上做加减的账号级修正层**：`grant_type=1` 追加、`0` 禁止并级联子孙，禁止优先，最后补祖先链。yunshu 完全沿用这套语义与前端交互，只因为“权限缓存 + 菜单删除策略”做必要调整，并顺手修掉 luohuo 的 4 个边界缺口（菜单校验、断链提升、脏关系、无审计）。
