# yunshu-system 工程框架方案（v1.1 已确认）

> 依据：`运输管理系统功能框架.docx` + `luohuo-frontend-spec.md`（v1.1 定稿）+ 家伟补充需求“菜单可自定义多级目录，区分创建目录和菜单”。
> 版本：v1.1（2026-11-20，D1-D5 / Q3 / Q5 / Q7 已经家伟确认，结论直接写入正文，见第 13 节）。
> 边界：**不建库、不写库、不写业务逻辑**；纯前端骨架（令牌 / 布局 / 静态路由 / 请求层 / 菜单树渲染）已获准开工，不含业务页面。
> 配套文件：`outputs/module-tree.md`（54 个节点清单）、`work/menu-seed.sql`（菜单种子，仅设计稿）、`work/module-tree.json`（机器可读）。

---

## 1. 目标与边界

### 1.1 本轮目标

1. 把 docx 的 14 个一级模块、40 个功能页整理成可落库的菜单树。
2. 确定前后端工程结构、路由与权限模型、请求层、布局与菜单渲染方式、公共组件位置。
3. 设计菜单数据模型（支持任意层级目录、区分目录/菜单、权限标识、排序、显隐）。
4. 设计菜单管理页的交互，明确“新增目录”和“新增菜单”两类表单的字段差异。

### 1.2 本轮不做

- 不创建数据库、不执行 DDL、不写入任何菜单种子。
- 不写业务页面代码、不接后端真实接口（登录与菜单使用本地 mock）。
- 后端工程不初始化——等菜单管理页与权限模型确认后再动。

### 1.3 本轮已获准开工（纯前端骨架）

1. 样式令牌与全局样式（沿用 luohuo 的墨蓝·素笺体系）。
2. `BasicLayout`（顶栏 + 标签栏 + 侧栏 + 内容区）。
3. 静态路由（`/login`、`/404`、`/dashboard` affix 首页）。
4. 请求层封装（`request.ts` / `download.ts`，后端未就绪时只做接口契约）。
5. 菜单树渲染（递归 `SidebarItem`，mock 菜单驱动，支持任意层级）。
6. 每个菜单对应一个占位页，保证点击可跳转、标签页可开合、不出现白屏。

---

## 2. 技术栈与版本锁定

采用**精确版本**（不带 `^`），沿用 luohuo 已实测的组合，避免 Element Plus 与表格基建的兼容风险。

### 2.1 前端

| 依赖 | 锁定版本 | 说明 |
|---|---|---|
| node | 20.20.x | `.nvmrc` 固定 20.20.2，`engines` 声明 `>=20.19 <21` |
| vue | 3.5.44 | |
| vue-router | 4.6.4 | |
| pinia | 2.3.1 | |
| pinia-plugin-persistedstate | 3.2.1 | |
| element-plus | 2.14.7 | SmartTable 依赖其内部 table store，必须锁死 |
| @element-plus/icons-vue | 2.3.1 | |
| axios | 1.20.0 | |
| sortablejs | 1.15.7 | 菜单同级拖拽排序（可选） |
| vite | 5.4.21 | |
| @vitejs/plugin-vue | 5.2.4 | |
| sass | 1.106.1 | |
| typescript | 5.6.3 | |
| vue-tsc | 2.1.10 | 构建时类型检查 |

### 2.2 后端

- Java 17、Spring Boot 3.3.x、MyBatis-Plus 3.5.x、MySQL 8.0、Redis 7。
- 鉴权：JWT（无状态）+ Redis 存 token 黑名单与菜单缓存。
- 接口前缀统一 `/api/v1`，前端 `baseURL=/api`。

---

## 3. 前端工程结构

```
frontend/
├─ src/
│  ├─ api/                      # 一个业务页一个文件，按模块分目录
│  │  ├─ auth.ts
│  │  ├─ system/{menu,user,role,org,dict,carrier,config,region,route}.ts
│  │  ├─ operation/{preCreate,create,import,waybill,deleted}.ts
│  │  └─ ...
│  ├─ assets/                   # 图片、svg
│  ├─ components/
│  │  ├─ base/                  # 与业务无关，可直接移植
│  │  │  ├─ SmartTable.vue
│  │  │  ├─ ListPage.vue
│  │  │  ├─ TablePager.vue
│  │  │  ├─ HeaderFilter.vue
│  │  │  ├─ ColumnRenderer.vue
│  │  │  ├─ ExportButton.vue
│  │  │  ├─ TableColumnSetting.vue
│  │  │  ├─ StatusTag.vue
│  │  │  ├─ ReconStatusTag.vue
│  │  │  ├─ PageDrawer.vue       # 统一抽屉壳：640/900/1120
│  │  │  └─ IconPicker.vue
│  │  └─ business/               # 跨页面业务组件
│  │     ├─ OrgTreeSelect.vue
│  │     ├─ MenuTreeSelect.vue
│  │     ├─ WaybillPicker.vue
│  │     └─ CustomerSelect.vue
│  ├─ composables/
│  │  ├─ useListTable.ts
│  │  ├─ useTableColumns.ts
│  │  ├─ usePageRefresh.ts
│  │  └─ useTableHeight.ts
│  ├─ directives/
│  │  ├─ hasPerm.ts
│  │  └─ index.ts
│  ├─ layouts/
│  │  ├─ BasicLayout.vue
│  │  └─ components/{AppHeader,AppSidebar,SidebarItem,TagsView,Breadcrumb}.vue
│  ├─ router/
│  │  ├─ index.ts               # createRouter + 静态路由
│  │  ├─ guard.ts               # 登录态、动态路由、标题
│  │  └─ menuToRoute.ts         # 菜单树 → 扁平路由
│  ├─ stores/
│  │  ├─ user.ts                # token/userInfo/perms/menus
│  │  ├─ tags.ts                # 多标签页
│  │  ├─ refresh.ts             # 页面刷新总线
│  │  └─ app.ts                 # 侧栏折叠、主题
│  ├─ styles/
│  │  ├─ variables.css          # 墨蓝·素笺设计令牌
│  │  ├─ common.css             # Element Plus 覆盖
│  │  └─ index.scss
│  ├─ types/
│  │  ├─ api.d.ts               # ApiResult/PageResult
│  │  ├─ menu.ts                # MenuNode/MenuType
│  │  └─ table.ts               # TableColumn
│  ├─ utils/
│  │  ├─ request.ts
│  │  ├─ download.ts
│  │  ├─ colWidth.ts
│  │  ├─ tableFilter.ts
│  │  ├─ tableSort.ts
│  │  ├─ amountColor.ts
│  │  └─ tree.ts
│  ├─ views/                    # 目录与菜单组件路径一一对应
│  │  ├─ dashboard/index.vue
│  │  ├─ login/index.vue
│  │  ├─ error/404.vue
│  │  ├─ system/{org,user,role,route,menu,dict,carrier,config,region}/index.vue
│  │  ├─ operation/{preCreate,create,import,waybill,deleted}/index.vue
│  │  ├─ transport/loading/index.vue
│  │  ├─ arrival/unload/index.vue
│  │  ├─ pickup/customer/index.vue
│  │  ├─ delivery/dispatch/index.vue
│  │  ├─ transfer/out/index.vue
│  │  ├─ sign/waybill/index.vue
│  │  ├─ capacity/{driver,vehicle,vehicleType}/index.vue
│  │  ├─ customer/archive/index.vue
│  │  ├─ exception/{report,handle}/index.vue
│  │  ├─ receipt/{send,receive,issue}/index.vue
│  │  ├─ finance/{verification,branchRecon,carrierRecon,feeEntry,billCenter,payMethod,feeSubject,account}/index.vue
│  │  └─ audit/{delete,modify,cancelSign}/index.vue
│  ├─ App.vue
│  └─ main.ts
├─ .env.development            # VITE_API_BASE=/api
├─ .env.production
├─ index.html
├─ package.json
├─ tsconfig.json / tsconfig.node.json
├─ vite.config.ts
└─ .eslintrc.cjs / .prettierrc
```

约定：

- `views` 下的相对路径 = 菜单表 `component` 字段，例如 `system/user/index` → `src/views/system/user/index.vue`。
- 页面禁止跨模块引用；跨模块能力放 `components/business` 或 `composables`。
- 所有颜色/字号/间距只用 `variables.css` 令牌，禁止写死色值（规范 1.2）。

---

## 4. 后端工程结构（按域分包）

```
backend/
├─ src/main/java/com/yunshu/
│  ├─ YunshuApplication.java
│  ├─ common/
│  │  ├─ result/{ApiResult,PageResult,ResultCode}.java
│  │  ├─ exception/{BizException,GlobalExceptionHandler}.java
│  │  ├─ base/{BaseEntity,PageQuery}.java
│  │  └─ util/{TreeUtils,BeanUtils}.java
│  ├─ config/{MybatisPlusConfig,RedisConfig,WebMvcConfig,JacksonConfig,DataSourceConfig,EsConfig}.java
│  ├─ security/
│  │  ├─ JwtTokenProvider.java
│  │  ├─ JwtAuthenticationFilter.java
│  │  ├─ LoginUser.java
│  │  └─ PermissionService.java
│  └─ modules/
│     ├─ auth/{controller,service,dto,vo}
│     └─ system/
│        ├─ menu/{controller,service,mapper,entity,dto,vo}
│        ├─ user/role/org/dict/carrier/config/region/route/...
│     └─ operation/...（按模块）
├─ src/main/resources/
│  ├─ application.yml / application-dev.yml / application-prod.yml
│  ├─ mapper/system/SysMenuMapper.xml
│  └─ db/migration/            # Flyway，V1__init.sql、V2__menu_seed.sql
└─ pom.xml
```

- Controller 只做参数校验与编排；业务在 Service；禁止 Controller 直接写 SQL。
- 所有列表接口统一返回 `PageResult<T>`，合计行 `summary` 与 `data/total` 同级（规范 3.6）。
- 排序参数统一 `sort_by` + `sort_order`，字段白名单校验（规范 3.5）。

---

## 4A. 数据访问架构：读写分离与 ES（后端，架构预留）

> 与 luohuo 保持一致：**初期读写指向同一个库，后期只改配置即可切主从，不改代码**；ES 本期不实施，只预留位置与降级策略。

### 4A.1 读写路由方案

- 采用**动态数据源 + 路由数据源**：`dynamic-datasource-spring-boot-starter`（或自研 `AbstractRoutingDataSource`），数据源命名固定为 `master` / `slave`。
- **默认走 `slave`（读）**；写操作显式 `@DS("master")`，或用 AOP 拦截 `insert/update/delete/save/remove` 方法名前缀自动切主。
- **事务内强制主库**：`@Transactional` 生效时，路由键强制为 `master`（TransactionSynchronization 绑定连接后不再切换），避免“事务里读从库”造成读写不一致。
- 初期 `master` 与 `slave` 的 url/账号指向**同一个 MySQL 实例**，只改 `application-prod.yml` 即可切换真实主从；代码零改动。
- 从库不承载写流量：`slave` 账号只授 SELECT 权限，物理上兜底。

### 4A.2 注解边界（必须标注）

| 场景 | 标注 | 原因 |
|---|---|---|
| 新增/修改/删除/审核/核销等写操作 Service | `@DS("master")` + `@Transactional` | 写与事务必须主库 |
| 写后立即回查（返回刚保存的详情、生成单号后查询） | `@DS("master")` | 防止主从延迟读到旧数据 |
| 普通列表查询、详情查询、下拉数据 | 不标注，走默认 `slave` | 接受短暂延迟 |
| 登录鉴权、权限/菜单加载 | `@DS("master")` | 登录后立刻要用权限，不能读到旧授权 |
| 报表/合计/导出 | 默认 `slave` | 读多写少，允许延迟 |

禁止：在 `@Transactional` 方法内部调用另一个标了 `@DS("slave")` 的方法（连接已绑定，注解不生效，容易误判）。同一事务内所有读写均走主库。

### 4A.3 读一致性规则

1. **写后立即回查走主库**：任何“保存成功后返回详情/跳详情页”的接口，查询阶段必须 `@DS("master")`。
2. **列表页接受短暂延迟**：主从延迟通常在毫秒~百毫秒级，列表、报表、导出允许最终一致；页面上不提示“数据可能有延迟”。
3. **状态流转校验走主库**：拦截重复开单、重复揽收、重复核销这类“先查后写”的校验，查询必须主库，否则并发下会漏判。
4. **跨表强一致场景**（运单 + 费用 + 账户余额）统一在主库事务内完成，不拆到从库读。
5. 客户端“我的预开单”列表允许走从库；提交后的结果页回查走主库。

### 4A.4 ES 定位与降级（本期不实施）

- **MySQL 是唯一写入源**，ES 只做查询侧，不承接任何写请求。
- 同步链路：`MySQL binlog → canal（或同类 CDC）→ ES`，异步、可重放；不同步业务直接写 ES。
- 触发时机：运单量级达到列表/多条件检索性能瓶颈时再上，本期只在架构上留位。
- **降级策略**：ES 不可用或同步延迟超阈值时，查询自动回落 MySQL（同一 Service 内 `try ES → catch → MySQL`）；**写操作永不依赖 ES**，开单、揽收、签收、核销在 ES 故障时不受影响。
- 代码预留：查询接口抽象为 `WaybillQueryService`，实现分 `MysqlWaybillQuery` / `EsWaybillQuery`，用配置开关 `search.engine=mysql|es` 切换，默认 `mysql`。

### 4A.5 配置项与配置文件结构

```yaml
# application.yml
spring:
  datasource:
    dynamic:
      primary: slave            # 默认读从库
      strict: true              # 找不到数据源直接报错，不静默回落
      datasource:
        master:
          url: jdbc:mysql://${DB_MASTER_HOST}:3306/yunshu?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai
          username: ${DB_MASTER_USER}
          password: ${DB_MASTER_PWD}
          driver-class-name: com.mysql.cj.jdbc.Driver
          hikari: { maximum-pool-size: 20, minimum-idle: 5 }
        slave:
          url: jdbc:mysql://${DB_SLAVE_HOST}:3306/yunshu?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai
          username: ${DB_SLAVE_USER}       # 只读账号
          password: ${DB_SLAVE_PWD}
          driver-class-name: com.mysql.cj.jdbc.Driver
          hikari: { maximum-pool-size: 20, minimum-idle: 5 }

yunshu:
  search:
    engine: mysql               # mysql | es，本期固定 mysql
    es:
      enabled: false            # 本期关闭
      uris: http://${ES_HOST}:9200
      index: yunshu_waybill
      sync: canal               # canal | logstash
      degrade-on-error: true    # ES 异常回落 MySQL
```

- 初期：`DB_MASTER_HOST` 与 `DB_SLAVE_HOST` 配同一个地址即可，无需改代码。
- 后期：只改环境变量与 `search.enabled`，不动业务代码。
- 配置只允许在 `application-{env}.yml` 出现一次，业务代码禁止硬编码数据源名以外的连接信息。

---

## 5. 路由与权限模型

### 5.1 静态路由

| 路径 | 组件 | 说明 |
|---|---|---|
| `/login` | `login/index.vue` | 白名单 |
| `/404` | `error/404.vue` | |
| `/` → `/dashboard` | `BasicLayout` + `dashboard/index.vue` | 固定 affix 首页，不占菜单表（D1 已定） |
| `/:pathMatch(.*)*` | `error/404.vue` | 不用 redirect，保留原始 fullPath 以便动态路由加载后重匹配 |

### 5.2 动态路由生成

登录后 `GET /api/v1/auth/info` 返回：

```json
{
  "user": { "id": 1, "username": "admin", "orgId": 1, "orgName": "总部" },
  "roles": ["admin"],
  "perms": ["system:user:list", "system:user:add"],
  "menus": [ { "id": 1001, "menuName": "系统管理", "menuType": 1, "path": "/system", "children": [] } ]
}
```

生成规则：

1. 只取 `menu_type` 为 1/2、`status=1` 的节点；`menu_type=3` 只贡献 `perms`，不生成路由。
2. **目录与菜单都生成扁平路由**，全部挂在 `BasicLayout` 的 `children` 下，`path` 直接使用表里的绝对路径。原因：菜单支持任意层级，扁平化可避免多层 `<router-view>` 嵌套和 keep-alive 失效。
3. 目录不生成可访问页面：`redirect` 指向第一个可见子菜单；无子菜单则跳过。
4. 菜单节点的 `component` 通过 `import.meta.glob('@/views/**/*.vue')` 映射；找不到组件时注册 404 并在控制台报错，禁止静默白屏。
5. 路由 `name = Menu_${id}`（稳定、可被标签页与 keep-alive 复用），`meta` 写入 `title / icon / affix / keepAlive / terminal / menuId / parents`。
6. `parents` 存祖先名称数组，供面包屑使用，避免菜单树与路由树两套结构互相依赖。
7. 侧边栏直接渲染菜单树，不读路由表——菜单顺序、显隐、层级完全由后端数据决定。

### 5.3 守卫流程

```
beforeEach:
  1. 无 token → 白名单放行，否则 /login?redirect=fullPath
  2. 有 token 且访问 /login → /
  3. menusLoaded=false → fetchAuthInfo()
       → 生成并 addRoute 动态路由
       → 重建 tags
       → next({ path, query, hash, replace:true })
  4. 已加载 → 设置 document.title = `${meta.title} - 运输管理系统`
```

### 5.4 权限模型

- **角色授权**：`sys_role_menu` 存角色可见的目录/菜单/按钮 ID。
- **账户直接授权**：文档要求“按账户直接做功能授权、一账户关联多个机构”，因此增加 `sys_user_menu`（用户直授菜单/按钮）与 `sys_user_org`（多机构）。
- **有效权限** = 用户直授 ∪ 所有角色授权；`perms` 去重后下发。
- 按钮级：`v-hasPerm="'system:user:add'"`；无权限时 `display:none`，不移除 DOM（避免 vnode 失配）。
- 超级管理员：`user.id===1` 或角色含 `admin`，跳过校验。
- 菜单可见性由后端菜单树决定，前端不做角色硬编码。

### 5.5 终端维度（端归属已移除）

> 2026-11-20 家伟裁决：不再区分内部端/客户端，`portal_type` 字段与索引删除；客户功能改为“建客户账号 + 授予菜单”。本节只保留终端维度。

| 维度 | 字段 | 取值 | 作用 |
|---|---|---|---|
| 终端 | `terminal_type` | `PC` / `APP` / `PC,APP` | 决定同一套菜单在哪种载体可用 |

规则：

1. `/auth/info` 只按 `terminal_type` 过滤（本期 `PC`）与 `status=1/deleted=0`。
2. **预开单（`/operation/pre-create`）是普通菜单**，不再标记客户端；客户账号通过角色/账户直授获得该菜单。
3. 本期只建 PC 端（D3/Q7），APP 只保留 `terminal_type` 字段，不建端、不做适配层。
4. 客户与内部员工共用同一套登录与路由生成逻辑，功能差异全部由账号授权决定。
5. 客户账号与内部账号用 `sys_user.user_type` 标识（1 内部 / 2 客户），**仅标识用途，不参与任何权限、过滤、校验与菜单下发**。

---

## 6. 请求层

`utils/request.ts`：

- axios 实例：`baseURL: import.meta.env.VITE_API_BASE`、15s 超时、JSON。
- 请求拦截：注入 `Authorization: Bearer <token>`；参数归一化——
  - 数组 → 逗号分隔；
  - `'' / undefined / null` 剔除；
  - 散落的 `xxx_mode` 汇总成 `filter_modes` JSON（与 luohuo 查询协议一致）。
- 响应拦截：业务 `code===200` 返回 `data`；否则统一 toast 并 reject `{ code, message, data, __toasted }`；401 清空登录态并跳登录；403 优先展示后端 message。
- 类型：`request<T>(config): Promise<T>`、`ApiResult<T>`、`PageResult<T>`。
- 下载：`utils/download.ts` 独立 axios（blob、120s），解析 `Content-Disposition`、`x-export-rows`，blob 内 JSON 错误转为提示。
- 禁止页面直接 `axios`；禁止在组件里手写错误 toast（统一拦截器）。

---

## 7. 布局与菜单渲染

严格按规范 2.1/2.2：

| 区域 | 规格 |
|---|---|
| 顶栏 | 56px；左侧 logo 60px；标签栏 32px 嵌在顶栏内，与右侧快速搜索同一行 |
| 侧栏 | 208px，折叠 56px；深色 `#1a2432`，激活左侧 2px `#9db3cd` 指示条 |
| 页面 | 内边距 16px；内容区 `flex:1; min-height:0`，列表页不出现整页滚动 |
| 菜单项 | 高 40px、字号 14px、图标 16px + 文字间距 10px；折叠时只显示图标并弹层 |
| 标签页 | key=`route.fullPath`，首页 affix 不可关；关闭后优先右邻，其次左邻，兜底首页 |
| 面包屑 | 由 `route.meta.parents` + 当前标题生成，目录可点击跳第一个子菜单 |

菜单渲染：

- `AppSidebar` 读取 `userStore.menus`（树），`SidebarItem` **递归渲染任意层级**。
- 目录节点只做展开/收起，不跳转；叶子菜单点击 `router.push(path)`。
- 图标：后端存 Element Plus 图标组件名，前端通过 `iconMap` 白名单渲染；找不到时用 `Menu` 兜底并在菜单管理页保存时校验（规范 1.6 图标必填、必须是真实存在的图标名）。
- 3 级及以上缩进每级 16px；超过 4 级时给家伟预警（建议限制深度）。

---

## 7A. 预开单与开单揽收的职责边界（Q5 已定）

家伟口径：**预开单给客户用，客户可以自助预开单；公司开单员在开单揽收的“接单”Tab 接单、核对、确认揽收；开单揽收同时是开单员自己开单的入口。** 两个菜单分属不同端，不合并、不混在一起。

### 7A.1 预开单（`/operation/pre-create`，客户账号授权可见）

| 项 | 内容 |
|---|---|
| 使用者 | 发货客户（客户端账号） |
| 页面职责 | 提交预开单、查看自己预开单的受理状态、在未揽收前修改或撤回 |
| 不做 | 不确认揽收、不生成正式运单、不改运费、不查看其他客户数据 |
| 状态可见 | 待受理 → 已受理（被揽收）→ 已转正式运单 / 已撤回 |
| 列表 | 客户端只有一个页面，不做 Tab；工具条 = 新增预开单 / 筛选 / 刷新 |

### 7A.2 开单揽收（`/operation/create`，内部账号使用）

| 项 | 内容 |
|---|---|
| 使用者 | 公司开单员 / 网点操作员 |
| 页面形态 | 单页面 + 两个 Tab（`el-tabs`），不拆成两个菜单 |
| Tab1「接单」 | 列出客户端提交的预开单；开单员核对货物与收发货信息 → 补全计费信息 → **确认揽收** → 转正式运单；可退回/驳回（写明原因，回到客户端“待受理”） |
| Tab2「自开单」 | 开单员直接开单（无预开单来源），走同一套开单表单与校验 |
| 共用 | 运单号生成规则、计费规则、客户/线路选择器、打印能力两 Tab 共用，不各写一份 |
| 不做 | 不承载客户端自助入口；客户端入口只在预开单 |

### 7A.3 数据流

```
客户（客户端）预开单 ──► 待受理
                          │
                    开单员【接单】Tab 核对
                          ├─ 驳回 ──► 回到客户端（可见驳回原因）
                          └─ 确认揽收 ──► 生成正式运单 ──► 运单管理
开单员【自开单】Tab ─────────────────────► 生成正式运单 ──► 运单管理
```

> 实现约束：预开单与正式运单是两张表/两个状态域，预开单不直接产生应收费用；费用在确认揽收、生成正式运单时写入，避免客户端提交的数据进入财务口径。

---

## 8. 公共组件放置

| 组件/能力 | 位置 | 说明 |
|---|---|---|
| 表格基座 | `components/base/SmartTable.vue` | 全局替换 `ElTable`，注册顺序必须在 `app.use(ElementPlus)` 之后 |
| 列表页壳 | `components/base/ListPage.vue` | 工具条顺序、结构列、空态、分页 |
| 分页 | `components/base/TablePager.vue` | 20/50/100，工具条右侧 |
| 表头筛选 | `components/base/HeaderFilter.vue` | 文本/下拉/机构/日期/区间；单号多模式 |
| 列渲染 | `components/base/ColumnRenderer.vue` | 按 type 渲染，金额两位小数 + 千分位 |
| 状态标签 | `components/base/StatusTag.vue`、`ReconStatusTag.vue` | 禁止页面自写 el-tag |
| 抽屉壳 | `components/base/PageDrawer.vue` | 统一 640/900/1120 宽度与 padding 20/gap 12 |
| 图标选择 | `components/base/IconPicker.vue` | 搜索 + 预览，菜单表单专用 |
| 业务选择器 | `components/business/*` | 机构树、菜单树、运单、客户等跨页复用 |
| 列表组合式 | `composables/useListTable.ts` 等 | 一份列声明驱动五项能力 |
| 权限指令 | `directives/hasPerm.ts` | 全局注册 |

---

## 9. 菜单数据模型设计

### 9.1 设计原则

1. **一棵树、三种节点**：`menu_type` 1=目录、2=菜单、3=按钮；用 `parent_id` 表达任意层级。
2. **目录与菜单的硬边界**：目录不能有组件/权限标识；菜单必须有组件；按钮必须有权限标识且不能有子节点。
3. **路径与组件解耦**：目录存路径段，菜单存绝对路径；组件只存 `views` 下相对路径，不存 `../`。
4. **排序与显隐分离**：`sort_order` 控制同级顺序；`visible` 控制侧栏是否展示；`status` 控制是否启用（禁用后不下发路由、不参与鉴权）。
5. **软删除**：`deleted` 逻辑删除，删除前校验子节点与角色引用。

### 9.2 表结构（设计稿，不执行）

```sql
CREATE TABLE sys_menu (
  id            BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '菜单ID',
  parent_id     BIGINT UNSIGNED NOT NULL DEFAULT 0      COMMENT '父节点ID，0=根',
  menu_name     VARCHAR(50)  NOT NULL                   COMMENT '菜单名称',
  menu_type     TINYINT      NOT NULL DEFAULT 1         COMMENT '1目录 2菜单 3按钮',
  route_path    VARCHAR(200) DEFAULT NULL               COMMENT '目录=路径段；菜单=绝对路径',
  component     VARCHAR(200) DEFAULT NULL               COMMENT 'views 下组件路径；目录为 NULL',
  redirect      VARCHAR(200) DEFAULT NULL               COMMENT '目录默认跳转，可空',
  perms         VARCHAR(100) DEFAULT NULL               COMMENT '权限标识，如 system:user:add',
  icon          VARCHAR(64)  DEFAULT NULL               COMMENT 'Element Plus 图标组件名',
  terminal_type VARCHAR(20)  NOT NULL DEFAULT 'PC'      COMMENT '终端：PC / APP / PC,APP（本期只做 PC）',
  sort_order    INT          NOT NULL DEFAULT 0         COMMENT '同级升序',
  visible       TINYINT      NOT NULL DEFAULT 1         COMMENT '1显示 0隐藏（隐藏仍可访问）',
  status        TINYINT      NOT NULL DEFAULT 1         COMMENT '1启用 0禁用',
  keep_alive    TINYINT      NOT NULL DEFAULT 1         COMMENT '菜单是否缓存',
  affix         TINYINT      NOT NULL DEFAULT 0         COMMENT '是否固定标签页',
  external_url  VARCHAR(500) DEFAULT NULL               COMMENT '外链地址，可空',
  remark        VARCHAR(500) DEFAULT NULL,
  create_by     BIGINT       DEFAULT NULL,
  create_time   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  update_by     BIGINT       DEFAULT NULL,
  update_time   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  deleted       TINYINT      NOT NULL DEFAULT 0,
  PRIMARY KEY (id),
  KEY idx_parent_sort (parent_id, sort_order),
  KEY idx_type_status (menu_type, status),
  KEY idx_perms (perms)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='菜单表';
```

> 不在 `(parent_id, menu_name)` 上加唯一索引：软删除后同名重建会冲突；改为 Service 层“同级名称唯一、全局路径唯一”校验。

### 9.3 关系表

```sql
CREATE TABLE sys_role_menu (
  role_id BIGINT UNSIGNED NOT NULL,
  menu_id BIGINT UNSIGNED NOT NULL,
  PRIMARY KEY (role_id, menu_id),
  KEY idx_menu (menu_id)
) COMMENT='角色-菜单';

CREATE TABLE sys_user_menu (
  user_id BIGINT UNSIGNED NOT NULL,
  menu_id BIGINT UNSIGNED NOT NULL,
  PRIMARY KEY (user_id, menu_id),
  KEY idx_menu (menu_id)
) COMMENT='用户直授菜单/按钮';

CREATE TABLE sys_user_org (
  user_id BIGINT UNSIGNED NOT NULL,
  org_id  BIGINT UNSIGNED NOT NULL,
  is_default TINYINT NOT NULL DEFAULT 0,
  PRIMARY KEY (user_id, org_id),
  KEY idx_org (org_id)
) COMMENT='用户-多机构';
```

### 9.4 字段与业务规则的对应

| 字段 | 目录（1） | 菜单（2） | 按钮（3） |
|---|---|---|---|
| parent_id | 0 或目录 | 只能目录 | 只能菜单 |
| route_path | 必填，路径段（`/system`） | 必填，绝对路径（`/system/user`） | 禁止 |
| component | 禁止 | 必填（`system/user/index`） | 禁止 |
| perms | 禁止 | 选填（页面级，如 `system:user:list`） | 必填 |
| icon | 必填 | 必填 | 禁止 |
| terminal_type | 继承/PC | 必填，本期固定 PC | 继承父级 |
| keep_alive / affix | 禁止 | 可选 | 禁止 |
| 子节点 | 目录/菜单 | 仅按钮 | 禁止 |

> 2026-11-20 确认：菜单上级**只能选目录**（原 9.4 写的“目录/菜单”作废），与 9.1、10.5 对齐；端归属已于同日整体移除。其余确认口径见 10.5 校验规则。

### 9.5 图标与路由组件的对应

- 图标：后端只存组件名（如 `Setting`），前端 `iconMap` 白名单渲染；保存时用同一份名单校验，避免 `Truck` 这类不存在的图标（规范 1.6）。
- 组件：`component` 必须是 `src/views` 下真实存在的相对路径；菜单管理保存时后端不做文件系统校验（后端不知道前端产物），由前端在提交前用 `import.meta.glob` 的 key 列表校验并给出候选下拉。
- 路由路径规范：全小写、中划线分词；目录段不带 `views`；菜单路径以目录段开头；禁止 `..`、`//`、尾部 `/`。

### 9.6 缓存与失效

- `GET /auth/info` 的菜单树按 `userId + terminal` 缓存 Redis，TTL 30 分钟。
- 菜单/角色/用户授权任一写操作后，删除相关缓存键；菜单变更额外广播前端刷新提示（可选）。

---

## 10. 菜单管理页交互设计

### 10.1 页面形态

- **单页树形表格**（不用左树右表）：菜单是一棵数据集，树表更直观，且与“多级目录”心智一致。
- 表格 `row-key=id`、`tree-props={children}`、默认展开一级。
- 页面遵循规范 2.4.1：工具条 → 表格 → 无分页/合计/导出（树形配置表例外，见 D4）。

### 10.2 工具条

左侧业务按钮：

- **新增目录**（主按钮）：打开抽屉，类型固定为“目录”。
- **新增菜单**（次按钮）：打开抽屉，类型固定为“菜单”。
- 展开全部 / 收起全部。

右侧工具组（规范顺序）：

- 重置筛选 → 刷新（树表不提供导出/列设置/分页，见 D4）。

> 家伟的核心诉求“区分创建目录和菜单”就落在两个独立入口上；不在表单里用类型单选让用户自己切，避免误建。

### 10.3 列表列

| 列 | 最小宽 | 说明 |
|---|---|---|
| 菜单名称 | 240 | 树列，带图标；目录加粗 |
| 类型 | 90 | StatusTag：目录=info、菜单=primary、按钮=warning |
| 路由地址 | 200 | 目录显示路径段，菜单显示绝对路径 |
| 组件路径 | 200 | 目录/按钮显示 `—` |
| 权限标识 | 180 | 无则 `—` |
| 终端 | 90 | PC / APP / PC,APP |
| 排序 | 80 | 右对齐 |
| 显示 | 80 | 显示/隐藏 |
| 状态 | 80 | 启用/禁用 |
| 最后修改时间 | 170 | 规范列 |
| 操作 | 240 | 固定右侧 |

字段名不换行、列宽按规范 3.2 自适应，操作列固定 240px。

> 2026-11-20 修正：操作列 220→240（4 个动作实测约 200px）；端归属列已整体移除。全部列宽由 `SmartTable` 按 `max(表头宽, 当前页最宽内容 + 内边距)` 自适应，不低于表中最小宽，超过 240 截断并走表格级 tooltip。

> 2026-11-20 确认：**按钮以菜单子行的形式显示在树表**，默认折叠、不撑开树；展开菜单后才可见。表内顺序固定 `sort_order asc, id asc`，不提供点击列头排序。

### 10.4 行操作

| 当前节点 | 可用操作 |
|---|---|
| 目录 | 新增子目录、新增子菜单、编辑、删除 |
| 菜单 | 新增按钮、编辑、删除 |
| 按钮 | 编辑、删除 |

- “新增子菜单”在目录行上出现，自动带入 `parent_id`。
- “新增按钮”仅菜单行出现，用于维护 `system:user:add` 这类权限点；如果家伟希望菜单管理只管目录/菜单，可隐藏该入口（D2）。

### 10.5 新增/编辑抽屉（640px）

统一用 `PageDrawer`：body padding 20、区块 gap 12、底部按钮右对齐。

**两类表单的字段差异**

| 字段 | 新增目录 | 新增菜单 | 说明 |
|---|---|---|---|
| 上级节点 | 树选择（可为空=顶级） | 树选择（只能选目录） | 编辑时禁止选自己或自己的后代 |
| 类型 | 只读“目录” | 只读“菜单” | 入口决定类型 |
| 菜单名称 | 必填，2-20 字 | 必填，2-20 字 | 同级唯一 |
| 路由地址 | 必填，路径段，如 `/system`；**有子节点时禁止修改** | 必填，绝对路径，如 `/system/user` | 全局唯一 |
| 组件路径 | 不显示 | 必填，下拉选择（来自 views 实际文件） | 只存相对路径 |
| 权限标识 | 不显示 | 选填，如 `system:user:list` | 格式 `模块:实体:动作` |
| 图标 | 必填，图标选择器 | 必填，图标选择器 | 白名单校验 |
| 终端类型 | 不显示（默认 PC） | 必填，本期固定 PC | APP 字段保留，暂不建端 |
| 是否缓存 | 不显示 | 开关，默认开 | keep-alive |
| 固定标签 | 不显示 | 开关，默认关 | affix |
| 默认跳转 | 选填，第一个子菜单 | 不显示 | redirect |
| 排序 | 必填，0-9999，默认同级末位+10 | 同左 | |
| 显示 | 开关，默认显示 | 同左 | 隐藏仍可访问 |
| 状态 | 启用/禁用 | 同左 | 禁用不下发路由 |
| 备注 | 选填 500 字 | 同左 | |

**校验规则**

1. 同级名称唯一（同 parent_id 下，排除自身）。
2. 路由路径全局唯一；菜单路径必须以父目录路径开头；目录一旦有子节点，禁止再改路径。
3. 目录不能填组件/权限标识；菜单必须有组件；按钮必须有权限标识。
4. 菜单只能挂在目录下（不能挂菜单）；按钮只能挂在菜单下；禁止把节点挂到自己的后代下（环检测）。
5. 深度上限 4 级（只算目录+菜单，按钮层不计），超出提示“菜单层级过深”。
6. 图标必须在白名单内；组件文件不存在时**警告但允许保存**（先建菜单后做页面）。
7. 按钮权限标识**全局唯一**；菜单级权限标识不强制唯一。
8. 编辑时目录与菜单允许更换父级，按钮不允许。
9. 删除：有子节点直接拒绝；被角色/用户引用时二次确认并展示引用数量，确认后级联删除关系。

### 10.6 排序

- 一期：表单里的“排序号”控制同级顺序（升序），保存后刷新树。
- 二期可选：同级拖拽（sortablejs），调用 `PUT /v1/system/menus/sort` 提交完整有序 ID 列表，后端事务内批量更新。

### 10.7 接口清单

| 方法 | 路径 | 说明 |
|---|---|---|
| GET | `/api/v1/system/menus/tree` | 全量树（管理页，含禁用/隐藏） |
| GET | `/api/v1/system/menus/{id}` | 详情 |
| POST | `/api/v1/system/menus` | 新增目录/菜单/按钮 |
| PUT | `/api/v1/system/menus/{id}` | 编辑 |
| DELETE | `/api/v1/system/menus/{id}` | 删除（校验子节点/引用） |
| PUT | `/api/v1/system/menus/sort` | 同级排序（可选） |
| GET | `/api/v1/auth/info` | 当前用户信息 + 菜单树 + 权限 |

---

## 11. 初始化菜单

- 种子文件：`work/menu-seed.sql`（设计稿，**未执行**）。
- 结构：14 个目录（ID 1001-1014）+ 40 个菜单（ID 1101-2403），与 `module-tree.md` 一一对应。
- 端归属：已移除；54 条菜单全部为普通菜单，预开单通过客户账号授权可见。
- 顺序：按 docx 原始行顺序；同级 `sort_order` 以 10 递增，便于后续插入。
- 路由管理、系统配置、开单揽收的多 Tab 不拆菜单，页面内部用 `el-tabs` 实现。
- 建立“客户档案”时菜单名直接写 `客户档案`，不再保留同名的“客户管理/客户管理”（Q3 已定）。
- 首页 `/dashboard` 为静态路由，不写入菜单表（D1 已定）。

---

## 12. 建议落地顺序（确认后执行）

1. **纯前端骨架**（不含业务、不连库）：工程初始化、令牌与全局样式、BasicLayout、静态路由、请求封装、登录页壳、404、菜单树渲染组件。
2. **后端骨架**：Spring Boot 工程、统一返回/异常、JWT、`/auth/info` mock 或真实现、`sys_menu` 迁移脚本（**执行前再次确认**）。
3. **菜单管理页**：树表 + 目录/菜单两个抽屉 + 校验 + 排序。
4. **登录与权限打通**：动态路由、按钮权限、多机构切换。
5. **业务模块**：按模块逐个开发，每完成一个经家伟确认后再下一个（沿用既有协作规则）。

---

## 13. 风险与待确认

### 13.1 已确认事项（2026-11-20 家伟裁决）

| 编号 | 事项 | 结论 |
|---|---|---|
| D1 | 首页/看板 | **保留 `/dashboard` 为固定 affix 首页，不占菜单表** |
| D2 | 按钮权限维护 | **保留** `menu_type=3`，菜单行可“新增按钮” |
| D3 / Q7 | APP 端 | **本期只做 PC**，APP 不建端；`terminal_type` 字段预留 |
| D4 | 菜单管理豁免列表规范 | **豁免分页/合计/导出**，只留筛选 + 刷新；规范 3.1 记为“树形表例外” |
| D5 / Q1 | 一级单页模块 | **保留目录层** |
| Q2 | 终端空白 | 默认 PC（与 D3 一致） |
| Q3 | 客户管理同名 | 二级菜单显示名改为 **客户档案** |
| Q5 | 预开单与开单揽收 | **预开单=客户自助入口；开单揽收=公司内部职责**（接单 Tab + 自开单 Tab），不合并；不再用 `portal_type` 区分，改为账号+菜单授权 |

### 13.1A 仍待确认（不阻塞骨架）

| 编号 | 事项 | 建议 |
|---|---|---|
| D6 | 菜单深度上限 | 4 级；超出需单独评估 |
| D7 | 是否复用 luohuo 的菜单/权限接口契约 | 字段名按本方案，前端 adapter 兼容 luohuo 命名 |

### 13.2 技术风险

1. **SmartTable 依赖 Element Plus 内部 table store**：必须锁 2.14.7；升级前回归表格拖拽、列宽、合计、选择列。
2. **全局 `ElTable` 覆盖顺序**：`app.use(ElementPlus)` 之后再 `app.component('ElTable', SmartTable)`，TS 化时不要改成局部 import。
3. **动态路由与 404 的竞态**：必须用 `next({ ...to, replace:true })` 重新匹配，不能 `next()` 直接放行。
4. **多级菜单与 keep-alive**：采用扁平路由 + `meta.parents`，避免嵌套 router-view 导致缓存失效。
5. **菜单缓存一致性**：菜单/角色/授权写操作后必须失效 Redis 缓存，否则前端看到的还是旧树。
6. **路径与组件漂移**：组件文件改名后菜单表不会自动更新；建议在 CI 加一条校验脚本，比对 `views` glob 与菜单 `component`。
7. **读写分离的误用**：`@DS` 在同类内部自调用时因 AOP 不生效而失效，必须跨 Bean 调用；事务内再切从库同样无效。代码评审需检查“读注解是否真的生效”。
8. **ES 同步延迟**：binlog → canal → ES 为异步链路，任何“写后立刻搜”的场景必须回查 MySQL 主库；ES 只作为列表/多条件检索加速，不参与业务判断。

### 13.3 文档本身的歧义

已全部收口，见 `module-tree.md` 第 5 节“文档歧义 · 已定结论”。
