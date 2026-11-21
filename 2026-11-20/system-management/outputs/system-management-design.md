# 系统管理模块群 · 整体设计（机构 / 角色 / 用户）

> 版本：v1.4（2026-11-20，最终裁决：五类机构、线路归路由、user_type 仅标识）
> 依据：`scaffold-plan.md` 5.4 / 9.3、`module-tree.md` R01–R03、`menu-management-fields.md` 已确认口径、luohuo 现有实现。
> **本轮方案阶段**：不建机构/角色/用户表、不执行这三个模块的 DDL、不写业务代码；端归属移除的代码改动与 V3 执行记录见 `../menu-portal-removal/outputs/portal-removal-impact.md`。
> 表名按家伟指定使用 `sys_dept`（机构），字段按新表统一命名为 `dept_*`；与 luohuo 的 `sys_org/org_*` 差异见第 6 节。

---

## 1. 结论摘要

| 议题 | 结论 |
|---|---|
| 机构表 | `sys_dept`，单表树，`parent_id`，最多 **5 级** |
| 机构业务授权 | 机构表内置 4 个开关：能否发货、能否到货、现付未付开单、到付未付签收；放在机构抽屉的**「授权」Tab**，不混在基础信息表单 |
| 用户与机构 | 一个用户 **1 个主机构**（`dept_id`）+ **0..N 个额外可登录机构**（`sys_user_org`） |
| 角色与机构 | 本期角色是**功能角色**，不按机构绑定 |
| 功能权限链路 | 用户 → 角色 → 菜单/按钮，再并上账户直授（授权/禁止），`perms` 去重下发 |
| 数据权限 | **挂在用户上（`sys_user.data_scope`，默认 4=本机构及以下），与角色彻底解耦**；角色只管菜单/按钮；配置入口在用户管理；过滤由开关控制、默认关闭；落法与坑见 3.5 |
| 超级管理员 | `sys_user.id=1` 或角色编码 `admin`，跳过功能权限校验 |
| 开发顺序 | 机构 → 角色 → 用户（用户模块包含登录与 `/auth/info` 改造） |
| 列表形态 | 机构为树形表，**沿用树形配置表例外**：不分页、不做合计、不做导出、不做列设置 |

---

## 2. 数据模型

### 2.1 关系图

```
sys_dept (机构树, parent_id 自关联)
   │ 1
   ├──< sys_user.dept_id          （主机构，1 用户 1 主机构）
   ├──< sys_user_org.org_id       （额外可登录机构，多对多）
   │
sys_user ──< sys_user_role >── sys_role ──< sys_role_menu >── sys_menu
   └────────< sys_user_menu >───────────────────────────────┘
```

- 一个用户可拥有多个角色；一个角色可分配给多个用户。
- 一个角色可授权多个菜单/按钮；一个菜单/按钮可被多个角色授权。
- 一个用户可被直接授权菜单/按钮（账户级直授），与角色授权取并集。

### 2.2 sys_dept（机构）

```sql
CREATE TABLE sys_dept (
  id                      BIGINT UNSIGNED NOT NULL AUTO_INCREMENT
    COMMENT '机构主键ID，自增；被 sys_user.dept_id 与 sys_user_org.org_id 引用',
  parent_id               BIGINT UNSIGNED NOT NULL DEFAULT 0
    COMMENT '上级机构ID；0=顶级机构；只能挂顶级或本机构的上级，禁止形成环',
  dept_name               VARCHAR(50)  NOT NULL
    COMMENT '机构名称；展示在机构树、用户所属机构、单据机构字段；同一上级下不可重名',
  dept_code               VARCHAR(50)  NOT NULL
    COMMENT '机构编码；全局唯一（deleted=0 范围内由 Service 校验）；创建后不可修改，供对接与导入使用',
  dept_type               TINYINT UNSIGNED NOT NULL DEFAULT 3
    COMMENT '机构类型：1=总部 2=公司 3=部门 4=分拨中心 5=网点；决定允许的父子关系（见 4.2 矩阵）；仅 1 可作为树根',
  allow_ship              TINYINT(1) NOT NULL DEFAULT 1
    COMMENT '能否发货：1=允许该机构作为发货机构开单；0=禁止；开单模块保存时校验',
  allow_arrival           TINYINT(1) NOT NULL DEFAULT 1
    COMMENT '能否到货：1=允许该机构作为到货机构；0=禁止；到货卸车模块校验',
  allow_prepaid_unpaid    TINYINT(1) NOT NULL DEFAULT 0
    COMMENT '现付未付开单：1=客户现付运费未结清时允许开单；0=必须先收款；开单模块校验',
  allow_collect_unpaid_sign TINYINT(1) NOT NULL DEFAULT 0
    COMMENT '到付未付签收：1=到付运费未结清时允许签收；0=必须先结清；签收模块校验',
  leader                  VARCHAR(50)  DEFAULT NULL
    COMMENT '负责人姓名；仅展示与联系，不参与鉴权',
  phone                   VARCHAR(20)  DEFAULT NULL
    COMMENT '机构联系电话；手机或座机，仅展示与联系',
  address                 VARCHAR(200) DEFAULT NULL
    COMMENT '机构详细地址；用于单据展示与打印',
  sort_order              INT          NOT NULL DEFAULT 0
    COMMENT '同级排序号，升序；建议按 10 递增，便于中间插入',
  status                  TINYINT(1)   NOT NULL DEFAULT 1
    COMMENT '状态：1=启用（可被分配用户、可作为业务机构）；0=禁用（不可新分配用户，机构链上用户登录被拦截）',
  remark                  VARCHAR(500) DEFAULT NULL
    COMMENT '备注；记录变更原因或业务说明，不参与业务计算',
  create_by               BIGINT UNSIGNED DEFAULT NULL
    COMMENT '创建人用户ID，关联 sys_user.id；初始化数据为 NULL',
  create_time             DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP
    COMMENT '创建时间；插入时自动生成，后续更新不变',
  update_by               BIGINT UNSIGNED DEFAULT NULL
    COMMENT '最后修改人用户ID，关联 sys_user.id；初始化数据为 NULL',
  update_time             DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
    COMMENT '最后修改时间；更新时由数据库自动刷新，列表直接展示',
  deleted                 TINYINT(1)   NOT NULL DEFAULT 0
    COMMENT '逻辑删除：0=未删除，1=已删除；查询必须带 deleted=0；删除前校验子机构与用户引用',
  PRIMARY KEY (id),
  KEY idx_parent_sort (parent_id, sort_order),
  KEY idx_code (dept_code),
  KEY idx_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
  COMMENT='机构表：集团-分公司-网点-部门的树形组织架构，同时承载机构级业务授权开关';
```

**为什么有 4 个业务开关**：`module-tree.md` R01 明确“机构授权（能否发货/到货、现付未付开单、到付未付签收）”。它们是**机构级业务能力**，不是功能权限，不能塞进 `sys_role_menu`，否则同一角色在不同机构无法区分能力。

**为什么没有唯一索引 `dept_code`**：与 `sys_menu.route_path` 同理，逻辑删除后旧行仍占用编码；改为 Service 在 `deleted=0` 范围内校验“编码全局唯一、同级名称唯一”。

### 2.3 sys_role（角色）

```sql
CREATE TABLE sys_role (
  id          BIGINT UNSIGNED NOT NULL AUTO_INCREMENT
    COMMENT '角色主键ID；被 sys_role_menu.role_id 与 sys_user_role.role_id 引用',
  role_name   VARCHAR(50) NOT NULL
    COMMENT '角色名称；展示在角色列表与用户分配角色；全局不可重名',
  role_code   VARCHAR(50) NOT NULL
    COMMENT '角色编码；全局唯一；内置 admin 为超级管理员，不可删除、不可禁用、不可改权限',
  role_desc   VARCHAR(200) DEFAULT NULL
    COMMENT '角色描述；说明该角色的职责范围，仅展示',
  status      TINYINT(1)  NOT NULL DEFAULT 1
    COMMENT '状态：1=启用（参与权限计算）；0=禁用（该角色授权不下发）',
  remark      VARCHAR(500) DEFAULT NULL
    COMMENT '备注；记录变更原因，不参与权限计算',
  create_by   BIGINT UNSIGNED DEFAULT NULL COMMENT '创建人用户ID；初始化数据为 NULL',
  create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  update_by   BIGINT UNSIGNED DEFAULT NULL COMMENT '最后修改人用户ID',
  update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '最后修改时间',
  deleted     TINYINT(1) NOT NULL DEFAULT 0
    COMMENT '逻辑删除：0=未删除，1=已删除；已分配给用户的角色不允许删除',
  PRIMARY KEY (id),
  KEY idx_code (role_code),
  KEY idx_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
  COMMENT='角色表：功能权限的授权载体；角色与机构无关，本期不按机构区分';
```

**角色与数据权限彻底解耦（2026-11-20 家伟最终裁决）**：`sys_role` **不建 `data_scope`**，角色只控制菜单与按钮可见性，不含任何数据范围语义；数据范围改挂 `sys_user.data_scope`，配置入口在用户管理。理由：同一角色在不同机构/不同人身上往往需要不同数据范围，挂在角色上会迫使“为每个人复制角色”；挂在用户上语义单一，且与 `sys_user.dept_id` 天然对齐。

**为什么保留 `data_scope` 字段但不启用**：多机构隔离是确定需求，字段现在建成本接近零；默认值取 `4=本机构及以下`，与用户主机构语义一致。过滤由 `yunshu.data-scope.enabled` 开关控制，本期默认关闭（见 3.5）。

### 2.4 sys_user（用户）

```sql
CREATE TABLE sys_user (
  id          BIGINT UNSIGNED NOT NULL AUTO_INCREMENT
    COMMENT '用户主键ID；被所有业务表的 create_by/update_by 引用；id=1 为内置超级管理员',
  username    VARCHAR(50) NOT NULL
    COMMENT '登录账号；全局唯一；2-20 位字母/数字/下划线；创建后不可修改',
  password    VARCHAR(100) NOT NULL
    COMMENT '密码；BCrypt 密文，禁止存明文；重置密码走独立接口，不回显',
  real_name   VARCHAR(50) NOT NULL
    COMMENT '真实姓名；展示在操作人、单据经办人、审批人；2-20 个字符',
  phone       VARCHAR(20) DEFAULT NULL
    COMMENT '手机号；用于联系与后续找回密码，本期仅展示与唯一性提示',
  email       VARCHAR(100) DEFAULT NULL
    COMMENT '邮箱；用于联系与后续通知，本期仅展示',
  dept_id     BIGINT UNSIGNED DEFAULT NULL
    COMMENT '所属主机构ID；内部员工(user_type=1)必填，客户账号(user_type=2)可为空；决定数据归属与默认登录机构；禁用机构不可分配；引用 sys_dept.id',
  status      TINYINT(1) NOT NULL DEFAULT 1
    COMMENT '状态：1=启用（可登录）；0=禁用（禁止登录，已登录会话由后端拦截）',
  user_type   TINYINT NOT NULL DEFAULT 1
    COMMENT '用户类型：1=内部员工 2=客户；不参与功能权限下发；客户账号不适用数据范围过滤',
  data_scope  TINYINT UNSIGNED NOT NULL DEFAULT 4
    COMMENT '数据范围：1=全部 3=本机构 4=本机构及以下 5=仅本人（2=自定义机构预留未实现）；默认 4；仅内部员工适用，客户账号不参与过滤；过滤由 yunshu.data-scope.enabled 控制，本期默认关闭；与角色无关',
  remark      VARCHAR(500) DEFAULT NULL
    COMMENT '备注；记录岗位、职责或变更原因',
  create_by   BIGINT UNSIGNED DEFAULT NULL COMMENT '创建人用户ID；初始化数据为 NULL',
  create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  update_by   BIGINT UNSIGNED DEFAULT NULL COMMENT '最后修改人用户ID',
  update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '最后修改时间',
  deleted     TINYINT(1) NOT NULL DEFAULT 0
    COMMENT '逻辑删除：0=未删除，1=已删除；删除前需解除角色、机构直授关系',
  PRIMARY KEY (id),
  KEY idx_username (username),
  KEY idx_dept (dept_id),
  KEY idx_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
  COMMENT='用户表：登录账号与人员信息；通过 sys_user_role 获得功能权限，通过 sys_user_org 获得多机构登录范围';
```

**`dept_id` 按用户类型区分（2026-11-20 家伟裁定）**：内部员工（`user_type=1`）**必填**——运输业务的单据、对账、审批都要落到机构，没有主机构的用户无法表达数据归属，内置 admin 归属“总部”（`sys_dept.id=1`）；客户账号（`user_type=2`）**可空**——客户不是组织内成员，客户可见数据由客户域自身规则决定，**不得为了填字段而虚构机构**。

**`user_type` 只做标识**：1=内部员工、2=客户，默认 1。它**不参与任何功能权限、菜单下发或数据范围过滤**，客户能看到什么完全由角色/账户直授和客户域规则决定；保留该字段只为后续客户账号列表、报表统计、运营分析能区分账号来源。

**`data_scope` 挂在用户上**：默认 `4=本机构及以下`，锚点为 `dept_id`（主机构）；个别用户可在用户管理里单独调整。角色不含数据范围，避免“同角色不同人需要不同范围”时复制角色。字段与配置入口本期可上线，**过滤由开关控制、默认关闭**；`data_scope` **对客户账号不适用**（客户不参与数据范围过滤）。

### 2.5 sys_user_role（用户-角色）

```sql
CREATE TABLE sys_user_role (
  user_id     BIGINT UNSIGNED NOT NULL COMMENT '用户ID，引用 sys_user.id',
  role_id     BIGINT UNSIGNED NOT NULL COMMENT '角色ID，引用 sys_role.id',
  create_by   BIGINT UNSIGNED DEFAULT NULL COMMENT '分配操作人用户ID；初始化数据为 NULL',
  create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '分配时间',
  PRIMARY KEY (user_id, role_id),
  KEY idx_role (role_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
  COMMENT='用户-角色关联表：一个用户可多角色、一个角色可多用户；解除分配为物理删除';
```

**与 luohuo 的差异**：luohuo 的 `sys_user_role` 带 `org_id`，用于“同一角色只在某机构生效”。yunshu 本期角色是功能角色、全机构通用，因此不带 `org_id`；需要在某机构禁用某功能时，用机构业务开关或后续数据权限解决。

### 2.6 sys_role_menu（角色-菜单）

```sql
CREATE TABLE sys_role_menu (
  role_id     BIGINT UNSIGNED NOT NULL COMMENT '角色ID，引用 sys_role.id',
  menu_id     BIGINT UNSIGNED NOT NULL COMMENT '菜单/按钮ID，引用 sys_menu.id；目录与菜单控制可见性，按钮控制功能权限',
  create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '授权时间',
  PRIMARY KEY (role_id, menu_id),
  KEY idx_menu (menu_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
  COMMENT='角色-菜单关联表：存角色可见的目录/菜单/按钮ID；保存时按全量覆盖，解除授权为物理删除';
```

### 2.7 sys_user_menu（账户直授菜单/按钮，口径对齐 luohuo）

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

**与 luohuo 完全一致**：代理主键 + `(user_id, menu_id)` 唯一键 + `grant_type`（1 授权 / 0 禁止）。

**直授不是“只加不减”**：`grant_type=0` 会从角色权限中剔除该节点及其全部子孙，禁止优先；同一 payload 中 `granted − denied`。合并公式与前端交互详见 [`luohuo-user-menu-alignment.md`](./luohuo-user-menu-alignment.md)。

**为什么没有 `effect` 之外的字段**：luohuo 没有有效期、没有机构维度；本设计同样不引入，避免自创一套。直授变更即时生效，历史通过操作日志追溯。

### 2.8 sys_user_org（额外可登录机构）

```sql
CREATE TABLE sys_user_org (
  user_id     BIGINT UNSIGNED NOT NULL COMMENT '用户ID，引用 sys_user.id',
  org_id      BIGINT UNSIGNED NOT NULL COMMENT '额外可登录机构ID，引用 sys_dept.id；不包含主机构 dept_id',
  create_by   BIGINT UNSIGNED DEFAULT NULL COMMENT '授权操作人用户ID',
  create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '授权时间',
  PRIMARY KEY (user_id, org_id),
  KEY idx_org (org_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
  COMMENT='用户-额外可登录机构表：主机构由 sys_user.dept_id 表达，此处只存额外机构；登录后可在授权机构间切换业务上下文';
```

**语义说明（与 luohuo 完全不同）**：luohuo 的 `sys_user_org` 是“用户数据范围=自定义机构”的载体；yunshu 本期不做数据权限，本表表示**可登录/可操作哪些机构**。两者不能混用。

**不设 `is_default`**：默认登录机构固定等于主机构 `dept_id`，无需冗余标记；如家伟需要“默认机构可不同于主机构”，再加 `is_default`。

### 2.9 索引与唯一约束策略

| 表 | 唯一性 | 实现位置 | 原因 |
|---|---|---|---|
| sys_dept | `dept_code` 全局唯一 | Service（deleted=0） | 逻辑删除后旧编码不允许复用，DB 唯一索引会卡重建 |
| sys_dept | `(parent_id, dept_name)` 同级唯一 | Service（deleted=0） | 同上 |
| sys_role | `role_code` 全局唯一 | Service（deleted=0） | 同上 |
| sys_role | `role_name` 全局唯一 | Service（deleted=0） | 同上 |
| sys_user | `username` 全局唯一 | Service（deleted=0） | 同上 |
| 关联表 | 组合主键天然唯一 | DB 主键 | 解除关系直接物理删除，无软删 |

### 2.10 审计字段统一

- 业务表统一 `create_by / create_time / update_by / update_time / deleted`，与 `sys_menu` 完全一致；
- 关联表只保留 `create_by / create_time`，不做软删；
- `update_time` 由数据库自动刷新，前端“最后修改时间”直接展示，格式 `YYYY-MM-DD HH:mm:ss`（与菜单管理已确认口径一致）。

---

## 3. 权限模型

### 3.1 功能权限链路

```
登录（username + password）
  → 校验 sys_user.status=1
  → 校验主机构及祖先链 sys_dept.status=1
  → 签发 token（携带 userId）
请求鉴权
  → 解析 userId
  → 查询有效角色：sys_user_role JOIN sys_role（status=1 AND deleted=0）
  → 查询授权菜单：(sys_role_menu ∪ sys_user_menu 授权) − sys_user_menu 禁止及其子孙
  → 补全祖先链，过滤已删除/禁用菜单
  → 下发菜单树 + 按钮 perms
```

### 3.2 有效权限计算（对齐 luohuo）

```
roleIds   = 启用角色（未删除）的 sys_role_menu.menu_id
granted   = sys_user_menu where grant_type=1
denied    = sys_user_menu where grant_type=0
cascade   = denied ∪ denied 的全部子孙（未删除菜单）
effective = (roleIds ∪ granted) − cascade
effective = 逐级补全祖先链
tree      = effective ∩ deleted=0 ∩ status=1
perms     = tree 中 perms 非空的记录，distinct
```

- **禁止优先且级联**：禁止一个目录/菜单，其下全部按钮一并失效；
- **祖先补全**：只授按钮时，目录与菜单自动补进来，保证侧栏与面包屑不断链；
- **去重**：全程 Set；保存时 `granted − denied`，唯一键兜底；
- **按钮权限**：`menu_type=3` 的 `perms` 进入权限列表；菜单/目录上非空的 `perms` 同样纳入（页面级鉴权）；
- **树组装**：先剪掉“祖先链不完整”的节点，**不允许把子节点提升为根**（修复 luohuo 的断链问题，见对照文档第 5 节）；
- 排序仍为 `sort_order asc, id asc`，与菜单管理、侧栏一致。

### 3.3 登录与 `/auth/info`

现有 `AuthController` 是占位实现（任意账号密码、固定 admin、ADMIN 全量菜单）。用户模块落地时替换为：

```json
{
  "user": {
    "id": 1,
    "username": "admin",
    "realName": "管理员",
    "deptId": 1,
    "deptName": "总部",
    "authOrgs": [
      { "id": 1, "deptName": "总部", "isPrimary": true }
    ]
  },
  "roles": ["admin"],
  "perms": ["system:user:add", "system:dept:add"],
  "menus": [ ... ]
}
```

- `authOrgs` = 主机构 + `sys_user_org` 中的额外机构，按机构名排序；
- 前端顶栏的机构切换只切换“业务上下文”，不重新计算功能权限（角色全机构通用）；
- 超级管理员：`user.id=1` 或角色含 `admin`，返回全量启用菜单与全部按钮 perms，跳过功能权限校验；
- 登录态缓存：`userId` 维度的菜单与权限缓存 30 分钟，角色/用户/菜单/授权任一写操作后失效（沿用 scaffold-plan 9.6；端归属已移除，key 不再带 portal）。

### 3.4 按钮级鉴权

- 前端 `v-hasPerm="'system:dept:add'"`，无权限时 `display:none`，不移除 DOM；
- 后端每个写接口挂 `@RequirePerm("system:dept:add")`（或等价拦截），不能只靠前端隐藏；
- 按钮权限标识沿用菜单管理规则：`模块:实体:动作`，全局唯一。

### 3.5 数据权限：本期只留字段与钩子（v1.1 调整，采纳家伟折中方案）

**最终结论（2026-11-20 家伟裁决，替代旧方案）**：

1. **角色与数据权限彻底解耦**：`sys_role` 不含 `data_scope`，角色只回答“能不能看到/点击某个菜单、按钮”。
2. **数据范围挂在用户上**：`sys_user.data_scope`，默认 `4=本机构及以下`，个别用户可单独覆盖；锚点为 `dept_id`。
3. **配置入口在用户管理**，不在角色管理。
4. **字段与配置入口本期可上线，过滤由 `yunshu.data-scope.enabled` 开关控制，默认关闭**；关闭时 SQL 与现在完全一致。
5. **平台配置类接口不受数据权限影响**：`sys_role`、`sys_menu`、`sys_user`（用户管理本身）、`sys_dept`（机构管理本身）、登录与 `/auth/info` 永不挂数据过滤；数据权限只作用于业务数据表。

**为什么改**：运输业务确定是多机构共用，运单、账单、对账迟早要按机构隔离。挂在角色上会遇到“同一角色、不同人需要不同范围”，只能复制角色；挂在用户上与 `dept_id` 天然对齐。现在留字段与钩子接近零成本，避免将来列表/详情/导出/统计全局返工。

**边界要说清**：钩子只能零侵入解决**读**（列表/详情/分页 count/导出/统计）。**写**（新增/编辑/删除）仍需要 `dept_id` 自动填充与越权校验，不能只靠 SQL 改写——这是启用时必须补的一小块，不是“只填 handler”就能全覆盖。

#### 3.5.1 落点：MyBatis-Plus `DataPermissionInterceptor`

- 版本：MP 3.5.7 已内置 `DataPermissionInterceptor` + `DataPermissionHandler / MultiDataPermissionHandler`，基于 JSqlParser 改写 SQL；
- 注册在 `MybatisPlusConfig`，与分页插件同级；handler 入参含表名、别名、原 where、`mappedStatementId`；
- 当前 handler 恒返回 `null` → **生成的 SQL 与现在完全一致**；未来按 `DataScopeContext` 返回 `dept_id = ?` / `dept_id IN (...)` / `create_by = ?`；
- 覆盖：MP Wrapper、XML 动态 SQL、分页 count、复用同一 Mapper 的导出查询；
- 不覆盖：JdbcTemplate/原生 JDBC、ES 查询（将来 ES 侧在 query DSL 里拼同一套条件）。

方案对比：

| 方案 | 业务侵入 | 覆盖面 | 结论 |
|---|---|---|---|
| MP `DataPermissionInterceptor` | 零 | 所有 Mapper SQL（含 XML） | **推荐** |
| 自定义 `InnerInterceptor`（仿 TenantLine） | 零 | 同上，可控性更高 | 备选（IN 子树等复杂表达式 MP Handler 不够用时） |
| Service 模板方法/BaseService 手动拼 | 每个查询都要改 | 漏 XML、漏自定义 SQL | 否决（就是全局返工） |
| AOP 包 Mapper 方法 | 看不到最终 SQL | 无法安全注入 WHERE | 否决 |

本期只搭骨架（不写规则、不写业务代码）：

| 组件 | 职责 | 本期状态 |
|---|---|---|
| `YunshuDataPermissionHandler`（MultiDataPermissionHandler） | 按表白名单产出过滤表达式 | `return null`（不过滤） |
| `DataScopeContext` | ThreadLocal 保存当前请求的数据范围 | 按 `sys_user.data_scope` 解析；开关关闭时固定 `ALL` |
| `DataScopeFilter`（OncePerRequestFilter） | 请求入口解析登录态并写入 Context | 登录改造后接入；开关关闭时不解析，固定 ALL |
| `DataScopeProperties`（`yunshu.data-scope`） | 业务表白名单 + 机构列映射 + 开关 | 白名单为空；`enabled=false` 可关闭拦截器 |
| `@DataScopeIgnore` | 配置类/登录/字典/菜单等无需过滤的逃生口 | 预留，与 MP `@InterceptorIgnore` 二选一（以 3.5.7 实际支持为准） |
| `MetaObjectHandler` | 新增时填 `dept_id/create_by` | 用户模块落地后接 |
| `DataScopeGuard.ensureInScope` | 详情/编辑/删除的越权校验 | 用户模块落地后接 |

#### 3.5.2 常见坑（启用前必须逐项解决）

| 坑 | 说明与对策 |
|---|---|
| Join | 只过滤“归属表”；客户/承运商/字典等维度表不能加 `dept_id`，否则报错或误杀。靠白名单 + 别名映射，别按列名猜 |
| 子查询 / UNION / CTE | JSqlParser 支持程度不一；建立 SQL 回归集（分页 count、join、exists、union、group by）逐条断言 |
| 统计 / 报表 | 条件必须在聚合前；跨机构统计只能给 `data_scope=1` 的用户；缓存 key 必须带 scope |
| 平台配置接口 | 角色、菜单、用户管理、机构管理本身**必须排除**，否则管理员会把自己锁死；靠表白名单，不靠“表里有没有 dept_id”猜 |
| 导出 | 必须复用同一 Mapper；禁止单独拼 SQL/JdbcTemplate，否则绕过钩子 |
| 写操作 | 拦截器只改 where，不会填 `dept_id`，也不会把“越权更新 0 行”变成明确报错；需要 MetaObjectHandler + ensureInScope |
| 异步 / 定时任务 | ThreadLocal 不传递；定时任务显式设 `ALL`，业务接口缺上下文时 **fail-closed**，避免越权 |
| 解析失败 | `scope≠ALL` 时 fail-closed 并告警，不允许静默放行 |
| 性能 | 每条 SQL 多一次 JSqlParser 解析；本期可用 `enabled=false` 关闭，先量测再决定常开 |
| ES | binlog 同步链路要在查询 DSL 同步同一条件，不能只做 MySQL 侧 |

#### 3.5.3 将来启用步骤（按此执行即不返工）

1. 用户管理开放 `data_scope`：**1 全部 / 3 本机构 / 4 本机构及以下 / 5 仅本人**四档，默认 4；`2=自定义机构` 预留未实现（不建 `sys_user_scope_dept`，后端拒绝）；
2. `DataScopeContext` 从登录态解析当前用户范围（admin=ALL）；
3. handler 按白名单表返回条件；
4. 业务表补 `dept_id`，接 `MetaObjectHandler` 与 `ensureInScope`；
5. 打开 `yunshu.data-scope.enabled`，按模块灰度，用 SQL 回归集验证。**开关开启属于单独评审项，需家伟明确批准，不得随用户管理上线顺带打开。**

> 本期字段默认 `4=本机构及以下`：开关关闭时行为等同“全部”，将来启用时再按用户收紧，不会出现“默认全部”导致的安全感错觉。

### 3.6 机构业务开关与功能权限的区别

| 维度 | 功能权限 | 机构业务授权 |
|---|---|---|
| 载体 | 角色 / 账户直授 → 菜单、按钮 | `sys_dept` 的 4 个开关 |
| 回答的问题 | “这个账号能不能看到/点击这个功能” | “这个机构能不能做这类业务” |
| 生效范围 | 账号 | 机构及其下所有账号 |
| 典型场景 | 没有 `system:user:add` 看不到新增按钮 | 某网点不允许“现付未付开单” |

两者**同时校验**：例如开单保存时，既要有 `operation:create:add` 按钮权限，又要当前机构 `allow_ship=1`。

---

## 4. 机构树规则

### 4.1 层级与上限

- **树根唯一**：`sys_dept.id=1` 为内置“总部”，`dept_type=1`，`parent_id=0`；全库只能有一个总部；
- 除总部外，所有机构必须有上级；**不提供“新增顶级机构”入口**，顶部“新增机构”默认挂在总部下；
- 最多 **5 级**（含总部）：总部 → 公司 → 分拨中心/网点 → 网点/部门 → 部门；
- 采用 `parent_id` 单表树，不存 `ancestors` 路径字段；
- 校验：新增/移动时计算“目标父级深度 + 自身子树高度”，超过 5 级拒绝；
- 移动机构（改 `parent_id`）允许，但需做环检测（不能移到自身或后代下）；
- 总部不可移动、不可删除、不可禁用、类型不可改。

### 4.2 五类机构与父子关系

**类型编码与顺序**（按家伟 2026-11-20 最终裁决，线路已移除）：

| 编码 | 类型 | 说明 | 典型层级 |
|---|---|---|---|
| 1 | 总部 | 全公司唯一根节点，内置 `id=1` | L1 |
| 2 | 公司 | 法人/分公司/子公司 | L2–L3 |
| 3 | 部门 | 职能或操作部门 | L3–L5 |
| 4 | 分拨中心 | 中转/分拨场地 | L3–L4 |
| 5 | 网点 | 收派网点/营业部 | L4–L5 |

**推荐的父子允许矩阵**（✓ 允许，— 禁止）：

| 父 \ 子 | 1 总部 | 2 公司 | 3 部门 | 4 分拨中心 | 5 网点 |
|---|---|---|---|---|---|
| 1 总部 | — | ✓ | ✓ | ✓ | — |
| 2 公司 | — | ✓（子公司） | ✓ | ✓ | ✓ |
| 3 部门 | — | — | ✓（子部门） | — | — |
| 4 分拨中心 | — | — | ✓ | — | ✓ |
| 5 网点 | — | — | ✓ | — | — |

规则说明：

1. **不限制层级会导致的树结构问题**：部门下继续挂网点、公司下任意挂多级公司、网点下再挂分拨中心等，会让“组织树”同时承担经营单元与操作单元，导致用户归属、数据隔离、机构选择器的语义混乱；限制父子关系是为了保证树的可解释性。
2. **部门是“功能容器”**：只能挂在总部/公司/分拨中心/网点下，且只允许继续挂子部门；不允许部门下直接挂网点/分拨中心。
3. **类型可改，但要重新校验**：编辑机构时允许改类型，但必须同时满足“父 → 新类型”与“新类型 → 所有现有子类型”两段矩阵；任一不满足则拒绝，并提示冲突的子节点。
4. 类型默认值：上级是总部 → 默认“公司”；其他上级 → 默认“部门”；用户可改，但受矩阵约束。
5. **线路不在机构树中**：线路由“路由管理”定义（目的地区划/承运商/外转规则），业务表按路由引用，不再作为 `sys_dept` 节点。

### 4.3 用户与机构的归属

- **主机构**：`sys_user.dept_id`，必填，一名用户只有一个；决定数据归属与默认登录机构；
- **额外可登录机构**：`sys_user_org`，0..N 个；不包含主机构；
- 用户可归属任意类型机构（总部/公司/部门/分拨中心/网点）；
- 切换机构只改变业务操作上下文（例如开单机构、对账机构），**不改变功能权限**；
- 分配用户时，主机构不可选禁用机构；额外可登录机构只允许选启用机构；
- 角色不按机构绑定，所有可登录机构共用同一套角色；
- 业务选择器按场景过滤类型（例如“发货机构”只列允许发货的类型），机构管理页本身不过滤。

### 4.4 机构状态对登录的影响

- 用户登录时校验：`sys_user.status=1` 且 **主机构及其所有祖先** `status=1`；
- 任一祖先被禁用，该机构及子孙下的用户都无法登录（树形语义自然生效）；
- 已在线的会话：后端鉴权时按需校验机构状态（本期可在登录时校验，接口级拦截放到权限拦截器统一做）；
- 顶级“总部”不允许禁用。

### 4.5 删除与禁用规则

| 操作 | 规则 |
|---|---|
| 删除机构 | 有子机构 → 拒绝；有用户（`dept_id` 或 `sys_user_org` 引用）→ 拒绝；是内置总部（`id=1`）→ 拒绝；通过后逻辑删除 |
| 禁用机构 | 不能禁用内置总部；已有用户不受影响（可继续使用），但不可再分配给新用户；机构链上用户下次登录被拦截 |
| 修改父级 | 不能选自身或后代；移动后总深度不能超过 5；必须满足 4.2 的父子矩阵 |
| 修改编码 | 不允许（创建后只读），避免对接与历史数据断链 |

---

## 5. 开发顺序与依赖

| 阶段 | 模块 | 依赖 | 交付内容 |
|---|---|---|---|
| 1 | 机构管理 | 仅依赖菜单管理的按钮权限点 | DDL `sys_dept` + 种子“总部”（id=1）；后端 tree/detail/create/update/delete/options；前端 `/system/org` 树表页 + 抽屉「基础信息 / 授权」两个 Tab |
| 2 | 角色管理 | 依赖阶段 1 的机构 options；依赖现有菜单树接口做授权 | DDL `sys_role`（含 `data_scope`，默认 1）/ `sys_role_menu` + 内置 admin 角色并授权全量菜单；后端角色 CRUD + 权限分配；**搭好 `DataPermissionInterceptor` 骨架（handler 恒 null，SQL 不变）**；前端 `/system/role` 列表 + 菜单授权树 |
| 3 | 用户管理 | 依赖阶段 1（主机构/多机构）与阶段 2（分配角色） | DDL `sys_user / sys_user_role / sys_user_menu / sys_user_org` + 内置 admin 用户；后端用户 CRUD + 重置密码 + 分配角色；登录与 `/auth/info` 改造；**`DataScopeFilter` 接入登录态（仍固定 ALL）**；前端 `/system/user` 列表 |

约束：
- 每个阶段开工前先出字段清单，家伟确认后再写代码；
- 每个阶段的 DDL 单独出、单独确认，执行前报风险；
- 前一阶段验收通过后再进入下一阶段（单模块逐个开发）。

---

## 6. 与 luohuo 的口径对照

### 6.1 沿用

| 项 | 口径 |
|---|---|
| 机构树 | 单表 `parent_id`、`sort_order` 同级排序、状态 1 启用 / 0 禁用；类型扩展为 6 类（见 6.2） |
| 机构删除 | 有子机构拒绝；有用户引用拒绝；顶级机构不可删 |
| 角色 | `role_code` 全局唯一；内置 `admin` 不可删/不可禁用/不可改权限；删除前校验是否已分配给用户 |
| 角色授权 | 角色-菜单多对多，授权弹窗为菜单树多选，全量覆盖保存 |
| 用户 | `username` 唯一且创建后不可改；密码 BCrypt；启用/禁用；重置密码独立操作；删除前解除关联 |
| 多机构 | 通过用户-机构关联表表达，登录后可切换机构 |
| 超管 | `id=1` 或 `role_code=admin` 跳过权限校验 |

### 6.2 不同（及原因）

| 项 | luohuo | yunshu 本期 | 原因 |
|---|---|---|---|
| 机构表名/字段 | `sys_org` / `org_name`、`org_code`、`org_type` | `sys_dept` / `dept_name`、`dept_code`、`dept_type` | 家伟指定 `sys_dept`；字段随表名统一 |
| 机构业务授权 | 无 | 新增 4 个开关（发货/到货/现付未付开单/到付未付签收） | `module-tree.md` R01 的硬性业务要求 |
| 机构类型 | 3 类（公司/部门/网点） | 5 类：总部/公司/部门/分拨中心/网点（线路已移除，归路由管理） | 家伟 2026-11-20 最终裁决；用父子矩阵约束，避免树结构失控 |
| 机构层级 | 不限制 | 最多 5 级 | 防止误建无限层级，与菜单 4 级上限同一治理思路 |
| 用户-机构关联语义 | `sys_user_org` = 自定义数据范围机构 | `sys_user_org` = 额外可登录机构 | 本期不做数据权限，不能复用 luohuo 语义 |
| 角色-机构 | `sys_user_role.org_id` 限定角色生效机构 | 不绑定，角色全机构通用 | 本期角色只承担功能权限，绑定机构会放大复杂度 |
| 数据权限 | 已实现 `data_scope` 1–5 + 角色/用户自定义机构 | 只留 `sys_role.data_scope`（默认 1=全部）+ MP `DataPermissionInterceptor` 钩子（恒不过滤），不做界面与规则 | 多机构隔离是确定需求，先留钩子避免将来改所有查询与导出（见 3.5） |
| 账户直授 | 支持 grant + deny，覆盖式保存 | 支持 grant + deny，覆盖式保存（完全沿用） | 直授是账号级修正层，不是只加不减；luohuo 已实现且交互成熟 |
| 用户数据覆盖 | `sys_user.data_scope` | 本期不建，只在角色上留字段 | 本期不做用户级覆盖，避免无消费者的字段；将来需要时加可空列 |
| 软删字段 | `is_deleted` | `deleted` | 与 `sys_menu` 统一 |
| 唯一约束 | 编码类字段 DB 唯一索引 | Service 层（deleted=0） | 逻辑删除后编码可复用，避免迁移冲突 |
| 关联表结构 | 代理主键 + 唯一索引 | `sys_user_role / sys_role_menu` 用组合主键；`sys_user_menu` 按 luohuo 用代理主键 + `(user_id, menu_id)` 唯一键 | `sys_user_menu` 有 `grant_type`，且需与 luohuo 完全对齐；其余关系表无需单行主键 |
| 菜单维度 | 无端归属/终端过滤 | 只有 `terminal_type`（端归属已移除） | yunshu 不再区分内部端/客户端，客户功能靠账号+菜单授权 |

### 6.3 前端交互沿用

- 机构页：树表、行内“新增子机构/编辑/删除”、抽屉表单、上级机构树选择（`check-strictly`、不含自身）；
- 角色页：列表 + 新增/编辑抽屉 + “权限分配”弹窗（菜单树多选）；
- 用户页：列表 + 新增/编辑抽屉（所属机构树选、可登录机构多选树、角色多选）+ 重置密码；
- 与 yunshu 规范的差异：抽屉统一 640px（luohuo 用 dialog 520/560）、底部按钮右对齐、列表列宽自适应、禁止整页滚动——以 yunshu 前端规范为准。

---

## 7. 确认记录与新增待确认

### 7.1 已确认（2026-11-20 家伟裁决）

| # | 事项 | 结论 |
|---|---|---|
| 1 | 机构表名与字段：`sys_dept` + `dept_*` | 通过 |
| 2 | 机构层级上限 5 级 | 通过 |
| 3 | 机构类型 | **改为 5 类：1 总部 / 2 公司 / 3 部门 / 4 分拨中心 / 5 网点**；线路移除，归路由管理；总部唯一根，见 4.1/4.2 |
| 4 | 4 个业务开关默认值（发货开、到货开、现付未付开关、到付未付签收关） | 通过；**移到机构抽屉「授权」Tab**，不在基础信息表单 |
| 5 | 业务开关不继承，逐机构独立 | 通过 |
| 6 | 主机构 `dept_id` + 额外可登录机构，不设 `is_default` | 通过 |
| 7 | 数据权限：`sys_role.data_scope`（默认 1=全部）+ 查询钩子，不做界面与规则 | 通过 |
| 8 | 角色不按机构绑定 | 通过 |
| 9 | 账户直授按 luohuo：`grant_type` 1 授权 / 0 禁止，禁止级联子孙 | 通过（文档已对齐） |
| 10 | 机构链任一祖先禁用 → 其下用户不可登录 | 通过 |
| 11 | 内置总部 `sys_dept.id=1`、admin 角色、admin 用户 id=1 三者保护 | 通过 |
| 12 | `sys_user.user_type`（1 内部 / 2 客户） | 通过；**仅标识用途，不参与任何权限/过滤/校验/菜单下发** |

### 7.2 仍需确认

| # | 事项 | 建议 |
|---|---|---|
| C | 去掉线路后的父子允许矩阵（见 4.2） | 按矩阵执行，违反则拒绝保存 |
| I | 机构管理字段清单（列表 10 列、抽屉两 Tab、校验规则） | 见 `org-management-fields.md`，逐项确认后再写代码 |

已关闭项：A（五类编码 1–5）按最终裁决固定；B（总部唯一根）通过；D（类型可改两段校验）通过；E（线路真身方案）**作废**；F（授权 Tab）通过；G（V3 删列）已执行并通过；H（`user_type`）按“仅标识”通过。

---

## 8. 本轮状态说明

- 方案与文档：已按家伟最终裁决更新；**没有建机构/角色/用户表，没有写这三个模块的业务代码**；
- 端归属移除：V3 已由 Flyway 执行（version=3），`portal_type` 与 `idx_portal` 已删除；备份表保留 54 行；
- 机构管理：只出字段清单，等家伟逐项确认后再写代码；
- 角色、用户：字段清单尚未出（等机构管理确认后逐个出）。
