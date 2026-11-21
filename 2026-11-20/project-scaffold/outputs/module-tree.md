# 运输管理系统功能框架 · 模块树清单（v1.2）

> 来源：`运输管理系统功能框架.docx`（仅 1 张 4 列表格，40 个功能行，无正文段落）
> 解析口径：文档“一级”映射为目录（menu_type=1），“二级”映射为菜单（menu_type=2）；终端类型空白的按 PC 处理。
> 版本：v1.2（2026-11-20，按家伟裁决移除“端归属”维度；客户功能改为账号+菜单授权）。
> 统计：**14 个一级目录 + 40 个菜单 = 54 个节点**，不再按端拆分；预开单为普通菜单。

## 1. 完整模块树

| 层级 | 名称 | 类型 | 建议路由 | 建议组件（src/views/） | 终端 | 文档行 | 备注 |
|---|---|---|---|---|---|---|---|
| L1 | **系统管理** | 目录 | /system | Layout | PC | — | 一级模块 |
| L2 | 机构管理 | 菜单 | /system/org | system/org/index.vue | PC | R01 | 集团架构+机构授权（能否发货/到货、现付未付开单、到付未付签收） |
| L2 | 用户管理 | 菜单 | /system/user | system/user/index.vue | PC | R02 | 同 luohuo；账户直接功能授权，一账户多机构 |
| L2 | 角色管理 | 菜单 | /system/role | system/role/index.vue | PC | R03 | 同 luohuo |
| L2 | 路由管理 | 菜单 | /system/route | system/route/index.vue | PC | R04 | 干线路由/短驳路由/转出直达路由，多 Tab |
| L2 | 菜单管理 | 菜单 | /system/menu | system/menu/index.vue | PC | R05 | 多级目录自定义维护；树形表例外，不分页/合计/导出 |
| L2 | 字典管理 | 菜单 | /system/dict | system/dict/index.vue | PC | R06 | 同 luohuo |
| L2 | 承运商管理 | 菜单 | /system/carrier | system/carrier/index.vue | PC | R07 | 同 luohuo |
| L2 | 系统配置 | 菜单 | /system/config | system/config/index.vue | PC | R08 | 改单/删单/打印等配置，多 Tab |
| L2 | 区划管理 | 菜单 | /system/region | system/region/index.vue | PC | R09 | 同 luohuo |
| L1 | **运营管理** | 目录 | /operation | Layout | PC | — | 一级模块 |
| L2 | 预开单 | 菜单 | /operation/pre-create | operation/preCreate/index.vue | PC | R10 | 客户自助预开单入口；通过给客户账号授予该菜单实现，客户只能提交/查看自己的预开单，不做揽收 |
| L2 | 开单揽收 | 菜单 | /operation/create | operation/create/index.vue | PC | R11 | 公司内部职责。Tab1「接单」：接预开单→核对→确认揽收→转正式运单；Tab2「自开单」：开单员直接开单 |
| L2 | 运单导入 | 菜单 | /operation/import | operation/import/index.vue | PC | R12 | — |
| L2 | 运单管理 | 菜单 | /operation/waybill | operation/waybill/index.vue | PC | R13 | — |
| L2 | 删单明细 | 菜单 | /operation/deleted | operation/deleted/index.vue | PC | R14 | — |
| L1 | **运输管理** | 目录 | /transport | Layout | PC | — | 一级模块 |
| L2 | 装车配载 | 菜单 | /transport/loading | transport/loading/index.vue | PC,APP | R15 | 多点装车/多点卸车/拆零配载（一单多车）；本期只做 PC |
| L1 | **到货管理** | 目录 | /arrival | Layout | PC | — | 一级模块 |
| L2 | 到货卸车 | 菜单 | /arrival/unload | arrival/unload/index.vue | PC,APP | R16 | 同 luohuo；本期只做 PC |
| L1 | **提货管理** | 目录 | /pickup | Layout | PC | — | 一级模块 |
| L2 | 客户提货 | 菜单 | /pickup/customer | pickup/customer/index.vue | PC,APP | R17 | 同 luohuo；本期只做 PC |
| L1 | **送货管理** | 目录 | /delivery | Layout | PC | — | 一级模块 |
| L2 | 货物派送 | 菜单 | /delivery/dispatch | delivery/dispatch/index.vue | PC | R18 | 同 luohuo |
| L1 | **外转管理** | 目录 | /transfer | Layout | PC | — | 一级模块 |
| L2 | 货物转出 | 菜单 | /transfer/out | transfer/out/index.vue | PC | R19 | 同 luohuo |
| L1 | **签收管理** | 目录 | /sign | Layout | PC | — | 一级模块 |
| L2 | 运单签收 | 菜单 | /sign/waybill | sign/waybill/index.vue | PC,APP | R20 | 同 luohuo；本期只做 PC |
| L1 | **运力管理** | 目录 | /capacity | Layout | PC | — | 一级模块 |
| L2 | 司机管理 | 菜单 | /capacity/driver | capacity/driver/index.vue | PC | R21 | 司机基础信息维护 |
| L2 | 车辆管理 | 菜单 | /capacity/vehicle | capacity/vehicle/index.vue | PC | R22 | 车辆基础信息维护 |
| L2 | 车型管理 | 菜单 | /capacity/vehicle-type | capacity/vehicleType/index.vue | PC | R23 | 车型、车长维护 |
| L1 | **客户管理** | 目录 | /customer | Layout | PC | — | 一级模块 |
| L2 | 客户档案 | 菜单 | /customer/archive | customer/archive/index.vue | PC | R24 | 发货客户基础信息维护（原名与一级同名，已改为“客户档案”） |
| L1 | **异常管理** | 目录 | /exception | Layout | PC | — | 一级模块 |
| L2 | 异常上报 | 菜单 | /exception/report | exception/report/index.vue | PC | R25 | — |
| L2 | 异常处理 | 菜单 | /exception/handle | exception/handle/index.vue | PC | R26 | — |
| L1 | **回单管理** | 目录 | /receipt | Layout | PC | — | 一级模块 |
| L2 | 回单寄出 | 菜单 | /receipt/send | receipt/send/index.vue | PC | R27 | 末端网点回寄回单 |
| L2 | 回单接收 | 菜单 | /receipt/receive | receipt/receive/index.vue | PC | R28 | 公司接收末端网点回寄的回单 |
| L2 | 回单发放 | 菜单 | /receipt/issue | receipt/issue/index.vue | PC | R29 | 公司向发货客户发放回单 |
| L1 | **财务管理** | 目录 | /finance | Layout | PC | — | 一级模块 |
| L2 | 核销管理 | 菜单 | /finance/verification | finance/verification/index.vue | PC | R30 | 运单费用+车次费用核销，参考 luohuo |
| L2 | 网点对账 | 菜单 | /finance/branch-recon | finance/branchRecon/index.vue | PC | R31 | 与末端网点对账结算，参考 luohuo |
| L2 | 承运商对账 | 菜单 | /finance/carrier-recon | finance/carrierRecon/index.vue | PC | R32 | 与外转承运商对账结算，参考 luohuo |
| L2 | 费用录入 | 菜单 | /finance/fee-entry | finance/feeEntry/index.vue | PC | R33 | 末端送货费、中转费快速录入 |
| L2 | 账单中心 | 菜单 | /finance/bill-center | finance/billCenter/index.vue | PC | R34 | 同 luohuo |
| L2 | 收支方式 | 菜单 | /finance/pay-method | finance/payMethod/index.vue | PC | R35 | 同 luohuo |
| L2 | 科目管理 | 菜单 | /finance/fee-subject | finance/feeSubject/index.vue | PC | R36 | 同 luohuo |
| L2 | 账户管理 | 菜单 | /finance/account | finance/account/index.vue | PC | R37 | 网点预充值、提现、余额管理 |
| L1 | **审核管理** | 目录 | /audit | Layout | PC | — | 一级模块 |
| L2 | 删单审批 | 菜单 | /audit/delete | audit/delete/index.vue | PC | R38 | — |
| L2 | 改单审批 | 菜单 | /audit/modify | audit/modify/index.vue | PC | R39 | — |
| L2 | 取消签收审批 | 菜单 | /audit/cancel-sign | audit/cancelSign/index.vue | PC | R40 | — |

## 2. 目录 / 菜单角色划分

- **目录（14）**：系统管理、运营管理、运输管理、到货管理、提货管理、送货管理、外转管理、签收管理、运力管理、客户管理、异常管理、回单管理、财务管理、审核管理。
- **菜单（40）**：文档二级全部为具体页面，无更深层级的功能点。
- 文档中的“多 Tab”（路由管理、系统配置、开单揽收）属于**页面内 Tab**，不拆成菜单层级。
- 文档没有出现三级功能点；但菜单模型必须支持任意层级（见《工程框架方案》第 5 节）。
- 首页 `/dashboard` 固定为 affix 首页，**不占菜单表**（D1 已定）。

## 3. 端归属（2026-11-20 已移除）

家伟裁决：不再在菜单上区分内部端/客户端，`sys_menu.portal_type` 字段与索引删除。给客户使用哪个功能，改为**单独创建客户账号并授予相应菜单**；预开单作为普通菜单存在，由账号权限决定是否可见。

- 客户账号与内部账号的区分方式（如 `sys_user.user_type`）待家伟确认，见 `2026-11-20/menu-portal-removal/outputs/portal-removal-impact.md` 第 5 节；
- 菜单权限链路只按 `status/deleted` 过滤，不再有 `portal` 维度。

## 4. 终端分布（D3/Q7 已定：本期只做 PC）

| 终端 | 数量 | 页面 | 本期 |
|---|---|---|---|
| PC/APP | 4 | 装车配载、到货卸车、客户提货、运单签收 | 只做 PC，APP 字段保留不建端 |
| PC（文档明确） | 9 | 系统管理 9 个页面 | 做 |
| PC（文档空白，按 PC 处理） | 27 | 运营 5 + 送货 1 + 外转 1 + 运力 3 + 客户 1 + 异常 2 + 回单 3 + 财务 8 + 审核 3 | 做 |

## 5. 文档歧义 · 已定结论

| 编号 | 问题 | 结论（2026-11-20 家伟确认） |
|---|---|---|
| Q1/D5 | 一级模块只有 1 个二级页面时是否保留目录层 | **保留目录层**，便于后续扩展 |
| Q2/D3/Q7 | 终端类型空白、APP 是否本期建设 | **本期只做 PC**；APP 端不做，`terminal_type` 字段预留 |
| Q3 | 客户管理/客户管理同名 | 二级菜单显示名改为 **客户档案**，路由 `/customer/archive` |
| Q4 | 路由管理、系统配置的多 Tab | 一处单页面 + 内部 `el-tabs`，不拆菜单 |
| Q5 | 预开单与开单揽收边界 | **预开单=客户自助入口；开单揽收=公司内部职责**（不再用端归属表达，改为账号+菜单授权）。开单揽收 Tab：接单（接预开单→核对→确认揽收→转正式运单）+ 自开单。两菜单不合并 |
| Q6 | “同 luohuo”页面 | 复用 luohuo 页面结构与交互，字段按运输业务重新核对 |
| D1 | 首页 | `/dashboard` 固定 affix 首页，**不占菜单表** |
| D2 | 按钮权限维护 | **保留** `menu_type=3`，菜单行可“新增按钮” |
| D4 | 菜单管理列表规范 | 树形表例外：**不做分页/合计/导出**，只保留筛选 + 刷新 |