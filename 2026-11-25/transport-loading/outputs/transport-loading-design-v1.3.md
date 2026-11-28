# 运输管理 · 装车配载功能设计方案 v1.3（两 Tab 重设计 + 拆单子单 + 到站/卸车回退 · 待确认）

> **v1.3 改动说明（2026-11-25，新建不覆盖 v1.2）**：本版改五件事，其余沿用 v1.2（含 r1–r5）。① 配载页重设计为**两个 Tab**：Tab1「装车配载」= 实际配载（顶部可编辑车次名称/计划发车时间、始发→送达网点、双栏穿梭、底部合计与下一步）；Tab2「发车」= 车辆信息 + 费用信息 + 汇总 + 执行发车，可返回 Tab1；子车次/操作日志移出 Tab，改为顶部按钮弹窗 + 列表页入口，原四个抽屉取消或合并。② 已配载列表**取消操作列**，配载页不提供改单/作废，移除配载只能左移穿梭。③ 新增**拆单装车**：复用现有子单模型（`is_child/parent_id/parent_no/child_seq`，子单号=母单号-两位序号，`MAX+1`，1..99），母单按件数拆出本车子单，余额留在母单回池；金额（运费/送货费/返款/有效运费）**按件数比例**拆分，尾差归件数份额大的一方；重量/体积按件数占比带过，拆单只录件数。④ 迁移补齐索引与风险标注。⑤ **到站/卸车补全**：列表行操作增加到站/取消到站/卸车，未到站点卸车先弹 430px 到站确认再进卸车页；卸车页未卸车 Tab 有卸车、已卸车 Tab 有取消卸车；补齐状态回退、库存回退、到站卸车费失效与日志 16/17。详见 3.3、4.5、5.2–5.7、6.2、6.4、6.7、6.10、7.1、7.4、8.4–8.6、10、11、12。
> 范围：本文件只做方案，不写业务代码、不建 Flyway 迁移、不执行任何 DDL。
>
> **v1.2 改动说明（相对 v1.1，新建不覆盖）**：按家伟 2026-11-25 拍板结论落地，不含多装多卸。核心变化：① 费用口径重构——现付/到付/回付/月结运费为**收入项**（取运单字段按票汇总，不进费用配置、不参与分摊），成本项仅车次录入的运输费/油卡等并参与分摊，删除七个旧收入项，现付油卡费改为参与毛利；② 全车共用一个分摊方式；③ 重泡比改为「全局默认 + 线路覆盖」两级；④ 配载后允许改单/作废、禁止拆合单，并联动刷新车次数据；⑤ 目的网点新增不受限、修改/删除受配载约束；⑥ D1–D14 全部落定（对照见第 11 节）。
>
> **v1.2-r1 修订（2026-11-25）**：更正费用清单——“现返、欠返”不在删除范围（删除的只有 运费/送货费/提货费/其他费/回单费/制单费/运单保费 七项）。已把现返、欠返加回费用项清单与种子：类型=成本、默认不参与毛利、方向为空、取数字段 `biz_waybill.cash_back` / `biz_waybill.owe_back`；返款仍由 `rebate_total` 统一扣减一次，防重复口径写死在 4.1，后续不得以“冗余”为由删除。
>
> **v1.2-r2 修订（2026-11-25）**：新增「到站卸车页」——末端网点到站/卸车参照 luohuo 源码（`frontend/src/views/biz/unload/`）实现，并在完成卸车时录入「到站卸车费」（仅干线 + 末端网点，对应 `arrival_unload_fee`），同事务重算分摊/成本/毛利并写日志；详见 6.10、5.6、7.4、8.6，表结构变化见 3.2/3.4，迁移影响见第 12 节。
>
> **v1.2-r3 修订（2026-11-25）**：配载页布局按家伟截图重构——**取消原 7 个 Tab，改为单页左右双栏穿梭**（左「待配载列表」/ 右「已配载列表」，中间四个穿梭按钮），双栏独立筛选与分页；顶部信息条两行（车次标识 + 保存 / 网点方向 + 模式 + 提醒）；底部左合计、右「下一步」「打印装车单」；车辆信息、费用信息、子车次、操作日志改为顶部按钮触发的抽屉/弹窗；穿梭交互与即时落库；到站卸车仍为独立页（6.10）。接口调用建议见 6.4 第⑥点，7.1 随之调整。详见 6.4。
>
> **v1.2-r4 修订（2026-11-25）**：**校验位置前移**——待配载池只返回「已通过全部资格校验、确实可配」的运单，资格条件（库存 = 车次始发网点、`load_status=0`、状态 20/60、下一节点 ∈ 目的网点集合、未被其它车次有效配载、排除末段外转）在池查询一次做足；**穿梭（右移/左移）只落库，不做任何资格校验、不返回「不符合条件」清单**，只有并发被抢、状态瞬变、车次状态不允许等真实异常才失败；超载/超容仍只提醒不阻断。约定写死在 6.4 ⑤ 与 8.4，后续实现不得把资格校验加回穿梭接口。
>
> **v1.2-r5 修订（2026-11-25）**：新增「大车直送（`delivery_method=3`）不参与末端分流」口径——① 末端网点的到站/卸车清单**查询层排除**大车直送运单，不做状态推进；② 到站卸车费的分摊范围排除大车直送，车次级成本仍按全车分摊；③ 子车次只剩大车直送运单时允许按 0 票完成卸车并写日志；④ 这批运单到末端后状态停在 80 分拨发车，由后续「直送签收」流程推进（本期占位）；⑤ 状态矩阵、卸车接口、分摊口径与边界表同步。详见 5.2/5.3/5.4/5.6、6.10、7.4、8.6、10。

> 依据：`装车配载功能及配套功能设计.docx`（运输管理-装车配载功能及配套功能设计需求）+ 家伟 2026-11-25 决策 + 2026-11-25 更正（两 Tab、取消右栏操作列、双栏列设置/表头筛选、拆单生成子单、金额按件数拆分、费用类型统一列表）。
> 现状基线：Flyway 最新版本 `V26`；`sys_menu` 已存在「运输管理 → 装车配载」菜单（id=1301，路由 `/transport/loading`），运力管理（司机/车辆/车型，id=1901/1902/1903）只有菜单、无任何表与代码。
> 子单现状（v1.3 复用，不另造）：`biz_waybill` 已有 `parent_id/parent_no/is_child/child_seq` 与 `uk_parent_child`；子单号规则为「母单号 + `-` + 两位序号」（如 `YD20261123000126-01`），`child_seq` 取当前有效子单（未作废、未逻辑删除）中最小未占用序号、范围 1..99，作废后序号回收复用；列表仅显示母单，母单带「母」徽标与「子单(n)」入口，详情页已展示母/子标识，子单清单弹窗已存在（`WaybillChildrenDialog.vue`）。本期只补「装车拆单」的服务与页面入口，不改编号规则与表关系。
> 相关现有实现：[V13__operation_waybill.sql](file:///Users/apple/Desktop/君达文件/yunshu-system/backend/src/main/resources/db/migration/V13__operation_waybill.sql)、[WaybillEnums.java](file:///Users/apple/Desktop/君达文件/yunshu-system/backend/src/main/java/com/yunshu/modules/operation/constant/WaybillEnums.java)、[WaybillService.java](file:///Users/apple/Desktop/君达文件/yunshu-system/backend/src/main/java/com/yunshu/modules/operation/service/WaybillService.java)、[WaybillChildrenVO.java](file:///Users/apple/Desktop/君达文件/yunshu-system/backend/src/main/java/com/yunshu/modules/operation/vo/WaybillChildrenVO.java)、[运单列表 index.vue](file:///Users/apple/Desktop/君达文件/yunshu-system/frontend/src/views/operation/waybill/index.vue)、[子单清单 WaybillChildrenDialog.vue](file:///Users/apple/Desktop/君达文件/yunshu-system/frontend/src/views/operation/waybill/components/WaybillChildrenDialog.vue)、[DataScopeTables.java](file:///Users/apple/Desktop/君达文件/yunshu-system/backend/src/main/java/com/yunshu/common/datascope/DataScopeTables.java)

---

## 0. 一页结论（先看这里）

1. **新增 11 张表**：车次主表、车次目的网点（子车次）、配载明细、车次费用、费用分摊明细、车次操作日志、车次号日流水、费用类型配置、车辆主数据、司机主数据、系统配置；**改造 2 张现有表**：`biz_waybill` 新增 5 列（配载/库存相关），`sys_route` 新增线路重泡比列。**拆单不新增表、不新增编号规则**，复用 `biz_waybill` 既有子单字段与 `uk_parent_child`。
2. **车次 = 主车次 + 子车次**。每个目的网点生成一条子车次（编码 `CT20261125001-01`），独立记录到站/卸车状态。
3. **配载页 = 两个 Tab**（v1.3）：Tab1「装车配载」做实际配载——顶部可编辑车次名称（创建必录）、计划发车时间、始发→送达网点，主区左待配载/右已配载双栏穿梭，底部合计 +「下一步」；Tab2「发车」录入车辆信息与费用信息、看汇总、执行发车，可返回 Tab1。子车次/操作日志改为顶部按钮弹窗与列表页入口，原四个抽屉取消或合并（车辆/费用并进 Tab2）。**两栏各自支持列设置与表头列筛选**（pageKey 独立：`transport.loading.pool` / `transport.loading.loaded`；支持单/多模式与默认查询值，配置持久化后端）。
4. **配载匹配口径**：待配载池 = `current_hub_id` = 车次始发网点 + 状态 20/60 + 路由下一节点 ∈ 车次目的网点集合 + 未被其它车次有效配载 + 排除末段外转；**可配单元 = 未拆整票（母单）或子单，母单已拆时按「母单剩余量 > 0」回池**；资格校验全部在池查询一次完成，穿梭只落库、零资格校验（见 6.4 ⑥）。
5. **拆单装车（v1.3，复用现有子单）**：仅未配载的整票可拆；勾选母单 → 拆单弹窗只录本车件数 → 生成一张子单（子单号 = 母单号-两位序号，取当前有效子单中最小未占用序号，作废后回收复用）→ 子单装本车，母单保留剩余件数与按件数占比折算的剩余重量/体积，回池等下一车；剩余为 0 时母单退出配载池、在运单列表作为汇总壳保留。**配载后禁止再拆/再合（沿用 D6）**。
6. **拆单金额（v1.3）**：运费、送货费、返款（现返/欠返）、有效运费按**子单件数 ÷ 原单件数**拆分，尾差归件数份额大的一方，保证母子合计与拆前一分不差；重量、体积同比例带过（拆单只录件数）。母单仍是客户结算与开票主体，子单金额只作车次收入/毛利/打印快照。
7. **费用分摊**：只有**车次录入的成本项**参与分摊；`FeeAllocator` 支持按重量/体积/重泡比三种维度，但**全车共用一个分摊方式**（`alloc_method`），不再每项独立；**重泡比两级**：`sys_config` 全局默认 + `sys_route.volume_weight_ratio` 线路覆盖，空/0 回落全局；分摊基数为**配载单元（子单或整票）自身的件数/重量/体积**。
8. **毛利口径（运单级）**：收入项四项 = 现付运费、到付运费、回付运费、月结运费，取配载单元对应字段按票汇总，**不参与分摊、不落 `biz_trip_fee`**；**单票毛利 = 该票收入合计 − 该票返款(rebate_total) − 该票分摊成本合计**，**车次毛利 = Σ 已配载票毛利**（不另设算法、未配载/解除配载不计入）。成本项为车次录入并分摊到本票的现付运输费、回付运输费、到站付运输费、现付油卡费、到站卸车费/其他费（参与毛利）及其他启用成本项；**现返/欠返保留**为成本项（不参与毛利、运单级取数），返款仍只按 `rebate_total` 扣一次。分摊必须闭合（逐票之和=车次该项合计，尾差最后一票吸收），`车次毛利 = Σ票毛利 = 收入−返款−成本` 对账（4.4、6.11）。费用类型配置为**收入/成本/代收/代付统一列表**：收入四项只读、种子 16 项成本可编辑，代收/代付不参与毛利（6.8）；公式可在「运单毛利计算公式」配置（6.11）。
9. **已配载列表取消操作列**（v1.3）：配载页不允许改单/作废；移除配载只能左移穿梭。配载后的改单/作废改由运单管理/运单详情入口操作，仍按 D6 同事务刷新车次数据；已配载单元禁止拆/合。
10. **目的网点新增不受限**（含发车后，新子车次直接置在途）；修改/删除某目的网点要求该网点下无配载运单。
11. **到站卸车页参照 luohuo**（见 6.10）：末端网点在**干线车次**的子车次上完成到站/卸车，并通过**信息条下方内联费用区**录入「到站卸车费」「到站其他费」，两项各落 `biz_trip_fee(trip_dest_id=子车次)`，同事务重算分摊/成本合计/毛利并写日志；短驳与分拨中心卸车不出现该费用。**卸车、打印、两项到站费用分摊全部按配载单元（子单）维度**；打印显示子单号与母单号。取消卸车时两项费用保留金额并标记失效，不静默删除（见 5.6/8.6）。
12. **大车直送不参与末端分流**：`delivery_method=3` 的运单/子单不进末端到站/卸车清单（查询排除），不分摊到站卸车费，也不做末端状态推进（停在 80，后续由直送签收流程推进）；子车次仅剩大车直送时允许 0 票完成卸车并写日志。
13. **状态规则**：子单各自独立走 20→70→80→90→100/110/120→130；母单不参与配载与发车，**余额 > 0 时存储状态保持 20/60（保证余额回池）**，余额 = 0 后写为有效子单汇总；前端另算「汇总状态」展示（取最落后一张，全部到达某阶段才推进、进度不回退；仅取消发车/移除等显式动作可回退并留日志）。详见 5.7。
14. **D1–D14 已拍板，v1.3 追加 D15（两 Tab）、D16（拆单子单/金额按件数）、D17（到站/卸车回退）**，v1.3 为开发前唯一设计基线；下一步按 v1.3 改静态原型。
15. **到站/卸车闭环（v1.3）**：`到站 / 取消到站 / 卸车` 只在**到货卸车列表**行操作提供（车次列表、子车次弹窗均不设这些入口）；**`取消卸车` 是运单级动作**，只在到站卸车作业页「已卸车」Tab 勾选运单后执行，车次/子车次列表不出现。未到站点「卸车」不拦，先弹 430px 到站确认，确认后写到站并跳转卸车页（默认「未卸车」Tab）；作业页操作条随 Tab 切换：未卸车 Tab 仅 `卸车(N)`（未选禁用，0 票大车直送除外）+「已选 N 条」，已卸车 Tab 仅 `取消卸车`（选中后可用）+「已选 N 条」。取消到站回退子车次 30→20、运单 90/50→80、大车直送不动、主车次按剩余到站情况回 30/40/50；取消卸车回退子车次 40→30、运单 100/110/120→90 或 60→50、`load_status`→3（末端 4→3、分拨回池 0→3），并把库存 `current_hub_id/current_node_seq/inventory_time` 退回上一站；两项到站费用（卸车费/其他费）保留金额并标记失效。前置条件、权限、并发与风险见 5.2/5.3/6.7/6.10/8.4/8.6。

---

## 1. 范围与边界

### 1.1 本期范围（装车配载模块）

| 能力 | 是否本期 |
|---|---|
| 创建/修改/删除/关闭车次，自动生成车次号 | 是 |
| 一装多卸，按目的网点生成子车次 | 是 |
| 待配载池查询、装车配载、移除配载、超载超容提醒（不阻断） | 是 |
| 拆单装车：未配载整票按件数拆出子单装本车，母单余额回池（v1.3，复用现有子单） | 是 |
| 配载页两 Tab（装车配载 / 发车），顶部改车次名称与计划发车时间 | 是（v1.3） |
| 车辆信息、司机信息（主数据选择 + 快速新增，D1 已定） | 是 |
| 车次成本项录入（现付/回付/到站付运输费、油卡、过磅、保险、装卸、到站费用），全车统一分摊方式，分摊到每票 | 是 |
| 发车、取消发车、到站、取消到站、卸车、取消卸车 | 是（v1.3 补全回退） |
| 到货卸车列表行操作：到站 / 取消到站 / 卸车（未到站先到站确认再进卸车页）；取消卸车为运单级，仅在作业页「已卸车」Tab 提供 | 是（v1.3） |
| 末端网点到站卸车页（参照 luohuo）+ 到站费用内联录入（到站卸车费 + 到站其他费，仅干线、仅末端网点卸车） | 是（新增，见 6.10） |
| 大车直送（`delivery_method=3`）末端排除：不进卸车清单、不分摊到站卸车费、不做末端状态推进 | 是（v1.2-r5，见 6.10） |
| 车次状态与运单状态联动 | 是 |
| 配载后改单/作废联动刷新；已配载禁止拆/合 | 是（D6；v1.3 起改单/作废入口移至运单模块，配载页不再提供） |
| 目的网点新增不受限、修改/删除受配载约束 | 是（D10） |
| 打印运输清单 | 是 |
| 费用类型配置（收入/成本/代收/代付统一列表，总部全局，D4） | 是（v1.3 合并列表） |
| 全局重泡比配置 + 线路重泡比列 | 是（D3） |
| 车次列表列配置、筛选、排序、分页、导出 | 是（复用现有列表规范与异步导出） |
| GPS 车辆轨迹、到车预报推送、异常预警 | 否，二期 |
| 总提货费、总派送费、总外转费统计 | 否，字段预留不启用 |
| 短驳车次（DB） | 否，本期仅干线（CT），表字段预留 |

### 1.2 明确不做

- 不做短驳装车、网点装车（30）/网点发车（40）状态链路。
- 不做财务核销/对账页面，只把分摊结果落库，供后续对账模块取数。
- 不做车辆/司机完整管理页面（D1 已定：只做车次内选择 + 快速新增的最小主数据）。
- 不做「按重量/体积拆单」：v1.3 拆单弹窗只录件数，重量/体积按件数占比带过；如后续实际重量与件数比例差异大，再单独放开录入（见 4.5）。
- 不做已配载运单的拆单/合单（沿用 D6）；不做配载页改单/作废入口（v1.3 起移至运单管理，见 8.5）。
- 不新增子单编号规则、不新增子单表：复用 `biz_waybill` 既有 `is_child/parent_id/parent_no/child_seq` 与 `uk_parent_child`。
- 不做拆单撤销的配载页入口：撤销拆单沿用运单模块既有规则，且要求该子单未装车。
- 不修改运单开单页、运单列表页的既有字段与布局（仅在运单详情子单清单增加配载信息列，见 8.7）。

---

## 2. 业务流程

```
① 创建车次（车次计划 10）
   车次名称（必录）/ 计划发车时间 / 始发网点(默认当前机构) / 目的网点(可多个)
   → 生成车次号 CT20261125001
   → 按目的网点生成子车次 CT20261125001-01、-02 …
        │
        ▼
② 配载页 Tab1「装车配载」（车次 20）
   顶部：车次名称、计划发车时间可编辑 → 保存；始发网点 → 送达网点（可增删）
   左「待配载」池（只含已校验可配单元）：未拆整票 / 母单余额 / 子单
        + 下一节点 ∈ 车次目的网点集合 + 未被其它车次有效配载 + 排除末段外转
   ┌ 整票直接右移 = 整票配载（不生成子单）
   └ 勾选母单「拆单装车」→ 拆单弹窗只录本车件数
         → 生成子单（母单号-两位序号）→ 子单落配载
         → 母单扣减件数并按件数占比扣减重量/体积，余额回池
   右「已配载」只读（无操作列）；移除只能左移穿梭
   底部合计 +「下一步」→ Tab2
        │
        ▼
③ 配载页 Tab2「发车」（车次仍 20）
   车辆信息：车牌/车型/核定载重/容积、司机姓名/电话
   费用信息：全车统一分摊方式 + 成本项；自动分摊到每个配载单元
   汇总：四类运费收入、返款、成本、有效运费、毛利
   →「返回装车配载」回 Tab1 /「发车」→ 车辆在途 30
   发车：配载单元运单 70→80；车次、车辆、费用锁定
   大车直送单元照常装车发车，但末端不做状态推进（见 ⑤）
        │
        ▼
④ 各子车次到站 / 卸车（末端网点走「到站卸车页」，参照 luohuo）
   子车次到站 → 子单 90 末端到车（分拨中心为 50 分拨到车）
   子车次完成卸车 → 子单 100/110/120（分拨中心为 60 分拨卸车）
   母单不参与发车；余额 > 0 时保持 20/60，余额 0 后按有效子单汇总展示（不回退，见 5.7）
   [大车直送 delivery_method=3] 不进末端到站/卸车清单，状态停在 80
   [仅干线 + 末端网点卸车] 内联录入「到站卸车费」「到站其他费」（分摊范围排除大车直送，按子单份额）
   回退（v1.3）：
   - 取消到站：子车次 30→20；运单 90/50→80；大车直送不动；主车次按剩余到站情况回 30/40/50
    - 取消卸车：子车次 40→30；运单 100/110/120→90 或 60→50；load_status→3（末端 4→3、分拨回池 0→3）；库存退回上一站
   - 取消到站/取消卸车均保留两项到站费用金额并标记失效（不静默删），重新保存按新录入值重算
        │
        ▼
⑤ 关闭（仅发车前）
   未发运运单/子单释放回待配载池，已配载单元解除配载，车次 90 已关闭
```

配载期间的运单变更（v1.3 口径）：
- 改单/作废入口：**不在配载页**，改由运单管理/运单详情操作；保存后同一事务刷新配载明细快照、分摊明细、车次汇总/装载率/毛利，并写车次日志（D6 结论不变，入口变化）。
- 已配载运单/子单：**禁止再拆、禁止再合**；拆单只发生在未配载整票上。
- 已配载子单：禁止改件数/重量/体积与任何金额（会破坏母子合计与车次分摊）；仅允许改收货人/电话，并同步配载快照。
- 作废：仅未装车单元可直接作废；已装车单元须先左移移除再作废；作废后回池（母单余额按有效子单重算）。

---

## 3. 领域模型与表设计

### 3.0 表关系

```
biz_trip (主车次)
  ├─1:N─ biz_trip_dest (子车次：目的网点 + 到站/卸车状态)
  ├─1:N─ biz_trip_waybill (配载明细，trip_dest_id 指向子车次)
  │        └─1:N─ biz_trip_waybill_fee (每票 × 每费用项分摊结果)
  ├─1:N─ biz_trip_fee (车次成本项；trip_dest_id=0 车次级 / >0 子车次级)
  └─1:N─ biz_trip_log (操作日志)
biz_trip_no_seq (车次号日流水)
sys_fee_type (费用类型配置：收入/成本/代收/代付；收入四项为代码只读行)
sys_config (全局配置：重泡比默认值等)
sys_vehicle (车辆最小主数据)
sys_driver (司机最小主数据)
biz_waybill (现有，新增 5 列)
sys_route (现有，新增线路重泡比列)
```

设计约定沿用项目既有规范：
- utf8mb4 / utf8mb4_0900_ai_ci；金额一律 `DECIMAL`，禁止 `FLOAT/DOUBLE`。
- 不建物理外键；所有业务表自带 `dept_id` 参与数据权限，平台配置表按评审豁免。
- 每张表、每个字段必须有中文 `COMMENT`，枚举逐项列出。
- 业务表统一带 `create_by/create_time/update_by/update_time/deleted`。

### 3.1 `biz_trip` 车次主表

| 字段 | 类型 | 必填 | 默认 | 中文注释（业务含义 + 取值含义） |
|---|---|---|---|---|
| id | BIGINT UNSIGNED | 是 | 自增 | 主键ID |
| trip_no | VARCHAR(32) | 是 | — | 车次编号：`CT` + `yyyyMMdd` + 3 位日内流水，如 `CT20261125001`；系统生成、全局唯一、创建后不可改；CT=干线（短驳预留 DB） |
| trip_name | VARCHAR(64) | 是 | — | 车次名称：手工输入，用于调度识别，允许重复 |
| trip_category | TINYINT UNSIGNED | 是 | 1 | 车次类型：1=干线，2=短驳；本期只写 1，短驳预留 |
| trip_status | TINYINT UNSIGNED | 是 | 10 | 车次状态：10=车次计划，20=装车配载，30=车辆在途，40=车辆部分到站，50=车辆到站，60=完成卸车，90=已关闭 |
| origin_dept_id | BIGINT UNSIGNED | 是 | — | 始发网点ID：引用 sys_dept.id；默认当前登录机构，可调整为有权限网点 |
| origin_dept_name | VARCHAR(50) | 是 | — | 始发网点名称快照：创建时写入，机构改名不影响历史车次 |
| plan_depart_time | DATETIME | 否 | NULL | 计划发车时间：手工录入，作为时效考核基线 |
| depart_time | DATETIME | 否 | NULL | 发车时间：点击发车时服务器时间，实际离开始发网点时间；发车前为空 |
| plan_arrive_time | DATETIME | 否 | NULL | 计划到站时间：按线路里程、历史平均车速及中途停靠计划自动预估生成，可人工修正；无历史车速/停靠数据时按路由节点时效累加兜底，仍无数据则允许为空 |
| actual_arrive_time | DATETIME | 否 | NULL | 实际到站时间：主车次取最后一个目的网点的到站时间；各目的网点时间分别记录在子车次 |
| vehicle_id | BIGINT UNSIGNED | 否 | NULL | 车辆ID：引用 sys_vehicle.id（D1 已定建最小主数据）；快速新增时回写 |
| plate_no | VARCHAR(20) | 否 | NULL | 车牌号：手工输入或从车辆管理选择；发车前必填 |
| vehicle_type | VARCHAR(50) | 否 | NULL | 车辆型号：选择车牌自动带出；手工录入车牌时手工选择 |
| vehicle_load | DECIMAL(12,3) | 否 | NULL | 车辆核定载重(kg)：车辆主数据带出、不可改；手工录入车牌时手填；用于配载超重校验 |
| vehicle_volume | DECIMAL(12,3) | 否 | NULL | 车辆核定容积(m³)：车辆主数据带出、不可改；手工录入车牌时手填；用于配载超容校验 |
| driver_id | BIGINT UNSIGNED | 否 | NULL | 司机ID：引用 sys_driver.id（D1 已定建最小主数据）；快速新增时回写 |
| driver_name | VARCHAR(50) | 否 | NULL | 司机姓名：手工输入或从司机管理选择；发车前必填 |
| driver_phone | VARCHAR(20) | 否 | NULL | 司机电话：手工输入或从司机管理选择；发车前必填 |
| total_waybill_count | INT UNSIGNED | 是 | 0 | 已配载**单元数**：按配载明细实时汇总；整票计 1，拆单每张子单计 1（不重复计母单） |
| total_quantity | INT UNSIGNED | 是 | 0 | 总数量：已配载运单总件数之和，实时汇总 |
| total_weight | DECIMAL(12,3) | 是 | 0 | 总重量(kg)：已配载运单重量之和，实时汇总 |
| total_volume | DECIMAL(12,3) | 是 | 0 | 总体积(m³)：已配载运单体积之和，实时汇总 |
| load_rate_weight | DECIMAL(6,2) | 是 | 0 | 重量装载率(%)：总重量 ÷ 车辆核定载重 × 100，保留 2 位；无车辆载重时为 0 |
| load_rate_volume | DECIMAL(6,2) | 是 | 0 | 体积装载率(%)：总体积 ÷ 车辆核定容积 × 100，保留 2 位；无车辆容积时为 0 |
| total_prepaid_freight | DECIMAL(14,2) | 是 | 0 | 现付运费收入(元)：配载运单 `biz_waybill.prepaid_fee` 按票汇总；收入项，不参与分摊、不落 `biz_trip_fee` |
| total_collect_freight | DECIMAL(14,2) | 是 | 0 | 到付运费收入(元)：配载运单 `biz_waybill.collect_fee` 按票汇总；收入项，不参与分摊 |
| total_return_freight | DECIMAL(14,2) | 是 | 0 | 回付运费收入(元)：配载运单 `biz_waybill.return_fee` 按票汇总；收入项，不参与分摊 |
| total_monthly_freight | DECIMAL(14,2) | 是 | 0 | 月结运费收入(元)：配载运单 `biz_waybill.monthly_fee` 按票汇总；收入项，不参与分摊，新增字段 |
| total_freight | DECIMAL(14,2) | 是 | 0 | 总运费收入(元)：= 现付 + 到付 + 回付 + 月结 四项汇总之和；服务端计算，客户端传值无效 |
| total_rebate | DECIMAL(14,2) | 是 | 0 | 总返款(元)：配载运单返款合计之和（取 biz_waybill.rebate_total）；毛利中只扣减一次 |
| total_valid_freight | DECIMAL(14,2) | 是 | 0 | 总有效运费(元)：配载运单有效运费之和（取 biz_waybill.valid_freight）；仅展示，不参与毛利公式 |
| total_pickup_cost | DECIMAL(14,2) | 是 | 0 | 总提货费(元)：上门提货成本合计；本期暂不启用，固定 0 |
| total_delivery_fee | DECIMAL(14,2) | 是 | 0 | 总派送费(元)：派送成本合计；本期暂不启用，固定 0 |
| total_transfer_fee | DECIMAL(14,2) | 是 | 0 | 总外转费(元)：外转成本合计；本期暂不启用，固定 0 |
| alloc_method | TINYINT UNSIGNED | 是 | 3 | 全车统一分摊方式：1=按运单重量占比，2=按运单体积占比，3=按重泡比计费重量占比；全车所有成本项共用此方式（D2），默认 3；发车后锁定 |
| prepaid_transport_fee | DECIMAL(14,2) | 是 | 0 | 现付运输费(元)：车次成本项，付给承运商，参与分摊与毛利 |
| return_transport_fee | DECIMAL(14,2) | 是 | 0 | 回付运输费(元)：车次成本项，付给承运商，参与分摊与毛利 |
| arrival_transport_fee | DECIMAL(14,2) | 是 | 0 | 到站付运输费(元)：车次级汇总 = Σ 各目的网点录入金额；V36 起按网点录入，参与分摊与毛利 |
| prepaid_oil_card_fee | DECIMAL(14,2) | 是 | 0 | 现付油卡费(元)：车次成本项，参与分摊与毛利（本次口径调整为参与毛利） |
| whole_weighing_fee | DECIMAL(14,2) | 是 | 0 | 整车过磅费(元)：车次费用项，录入后参与毛利 |
| whole_insurance_fee | DECIMAL(14,2) | 是 | 0 | 整车保险费(元)：车次费用项，录入后参与毛利 |
| bulk_load_fee | DECIMAL(14,2) | 是 | 0 | 分拨装车费(元)：车次费用项，录入后参与毛利 |
| bulk_unload_fee | DECIMAL(14,2) | 是 | 0 | 分拨卸车费(元)：车次费用项，录入后参与毛利 |
| bulk_other_fee | DECIMAL(14,2) | 是 | 0 | 分拨其他费(元)：车次费用项，录入后参与毛利 |
| arrival_unload_fee | DECIMAL(14,2) | 是 | 0 | 到站卸车费(元)：车次级**汇总字段**，= Σ 各子车次 `biz_trip_dest.arrival_unload_fee`（仅有效）；不再在车次费用录入区单独录入 |
| arrival_other_fee | DECIMAL(14,2) | 是 | 0 | 到站其他费(元)：车次级**汇总字段**，= Σ 各子车次 `biz_trip_dest.arrival_other_fee`（仅有效）；不再在车次费用录入区单独录入 |
| total_cost_fee | DECIMAL(14,2) | 是 | 0 | 成本费用合计(元)：上述已启用且类型=成本的车次费用项之和（服务端计算，客户端传值无效） |
| gross_profit | DECIMAL(14,2) | 是 | 0 | 毛利(元)：= 四类运费收入合计(total_freight) − 总返款(total_rebate) − Σ参与毛利的车次成本项；服务端计算，客户端传值无效 |
| depart_by | BIGINT UNSIGNED | 否 | NULL | 发车操作人用户ID |
| depart_by_name | VARCHAR(50) | 否 | NULL | 发车操作人姓名快照 |
| close_reason | VARCHAR(500) | 否 | NULL | 关闭原因：关闭车次时必填 |
| close_time | DATETIME | 否 | NULL | 关闭时间：关闭动作的服务器时间 |
| close_by | BIGINT UNSIGNED | 否 | NULL | 关闭操作人用户ID |
| close_by_name | VARCHAR(50) | 否 | NULL | 关闭操作人姓名快照 |
| remark | VARCHAR(500) | 否 | NULL | 备注：车次补充说明 |
| dept_id | BIGINT UNSIGNED | 是 | — | 归属机构ID：车次创建/调度机构；数据权限锚点（与 origin_dept_id 始发网点区分） |
| create_by | BIGINT UNSIGNED | 否 | NULL | 创建人用户ID |
| create_by_name | VARCHAR(50) | 是 | — | 创建人姓名快照 |
| create_time | DATETIME | 是 | CURRENT_TIMESTAMP | 创建时间 |
| update_by | BIGINT UNSIGNED | 否 | NULL | 最后修改人用户ID |
| update_by_name | VARCHAR(50) | 否 | NULL | 最后修改人姓名快照 |
| update_time | DATETIME | 是 | CURRENT_TIMESTAMP ON UPDATE | 最后修改时间 |
| deleted | TINYINT(1) | 是 | 0 | 逻辑删除：0=正常，1=已删除；已发车车次不可删除 |

索引：
- `UNIQUE uk_trip_no (trip_no)`
- `KEY idx_status_time (trip_status, plan_depart_time)`
- `KEY idx_origin (origin_dept_id, trip_status)`
- `KEY idx_plate (plate_no)`
- `KEY idx_create_time (create_time)`
- `KEY idx_dept (dept_id)`

### 3.2 `biz_trip_dest` 车次目的网点表（子车次）

> 设计取舍：不单独建「子车次表」，用「车次 × 目的网点」一行表达子车次；子车次编码由此表生成。这样目的网点、到站、卸车、该网点运单集合天然一一对应，避免两张表字段重复与状态不同步。

| 字段 | 类型 | 必填 | 默认 | 中文注释（业务含义 + 取值含义） |
|---|---|---|---|---|
| id | BIGINT UNSIGNED | 是 | 自增 | 主键ID |
| trip_id | BIGINT UNSIGNED | 是 | — | 主车次ID：逻辑引用 biz_trip.id |
| sub_trip_no | VARCHAR(40) | 是 | — | 子车次编号：`主车次号-两位序号`，如 `CT20261125001-01`；全局唯一、生成后不可改 |
| seq | TINYINT UNSIGNED | 是 | — | 目的网点顺序号：从 1 开始，同一车次内唯一，对应子车次编号后两位 |
| dest_dept_id | BIGINT UNSIGNED | 是 | — | 目的网点ID：引用 sys_dept.id；一装多卸时多条 |
| dest_dept_name | VARCHAR(50) | 是 | — | 目的网点名称快照 |
| sub_status | TINYINT UNSIGNED | 是 | 10 | 子车次状态：10=待发车，20=在途，30=已到站，40=完成卸车 |
| plan_arrive_time | DATETIME | 否 | NULL | 计划到站时间：按线路时效估算，可按目的网点单独调整 |
| arrive_time | DATETIME | 否 | NULL | 实际到站时间：该目的网点操作到站时的服务器时间 |
| arrive_by | BIGINT UNSIGNED | 否 | NULL | 到站操作人用户ID |
| arrive_by_name | VARCHAR(50) | 否 | NULL | 到站操作人姓名快照 |
| unload_time | DATETIME | 否 | NULL | 卸车完成时间：该子车次全部运单卸完的时间 |
| unload_by | BIGINT UNSIGNED | 否 | NULL | 卸车操作人用户ID |
| unload_by_name | VARCHAR(50) | 否 | NULL | 卸车操作人姓名快照 |
| total_waybill_count | INT UNSIGNED | 是 | 0 | 本子车次**配载单元数**：实时汇总；子单按 1 计 |
| total_quantity | INT UNSIGNED | 是 | 0 | 本子车次总数量（运单总件数） |
| total_weight | DECIMAL(12,3) | 是 | 0 | 本子车次总重量(kg) |
| total_volume | DECIMAL(12,3) | 是 | 0 | 本子车次总体积(m³) |
| arrival_unload_fee | DECIMAL(14,2) | 是 | 0 | 到站卸车费(元)：仅 `trip_category=1` 干线车次的**末端网点**在本子车次到站/完成卸车时录入；分拨中心卸车与短驳固定 0；该值为 `biz_trip_fee` 中本子车次 `FEE0019` 的**有效（`fee_status=1`）合计**，并汇总进 `biz_trip.arrival_unload_fee` |
| arrival_unload_fee_valid | TINYINT(1) | 是 | 1 | 到站卸车费是否有效：1=有效（计入成本与分摊），0=已失效（取消到站/取消卸车后**保留金额但不计入**）；重新录入并保存后置回 1 |
| arrival_other_fee | DECIMAL(14,2) | 是 | 0 | 到站其他费(元)：与到站卸车费同页同范围录入（仅干线 + 末端网点）；为 `biz_trip_fee` 中本子车次 `FEE0020` 的**有效（`fee_status=1`）合计**，并汇总进 `biz_trip.arrival_other_fee` |
| arrival_other_fee_valid | TINYINT(1) | 是 | 1 | 到站其他费是否有效：1=有效，0=已失效（取消到站/取消卸车后保留金额但不计入）；重新录入并保存后置回 1 |
| dept_id | BIGINT UNSIGNED | 是 | — | 归属机构ID：= 主车次 dept_id；数据权限锚点 |
| create_by / create_time / update_by / update_time / deleted | — | — | — | 审计字段，含义同全库约定 |

索引：
- `UNIQUE uk_trip_seq (trip_id, seq)`
- `UNIQUE uk_sub_trip_no (sub_trip_no)`
- `KEY idx_dest_status (dest_dept_id, sub_status)`
- `KEY idx_trip (trip_id)`

### 3.3 `biz_trip_waybill` 车次配载明细表

| 字段 | 类型 | 必填 | 默认 | 中文注释（业务含义 + 取值含义） |
|---|---|---|---|---|
| id | BIGINT UNSIGNED | 是 | 自增 | 主键ID |
| trip_id | BIGINT UNSIGNED | 是 | — | 主车次ID：逻辑引用 biz_trip.id |
| trip_dest_id | BIGINT UNSIGNED | 是 | — | 子车次ID：逻辑引用 biz_trip_dest.id；按运单下一节点自动匹配 |
| sub_trip_no | VARCHAR(40) | 是 | — | 子车次编号快照：便于打印与对账不联表 |
| waybill_id | BIGINT UNSIGNED | 是 | — | 配载单元ID：逻辑引用 biz_waybill.id；整票配载=母单（is_child=0），拆单配载=子单（is_child=1）；子单不可再拆 |
| waybill_no | VARCHAR(32) | 是 | — | 配载单元号快照：整票=母单号，拆单=子单号（母单号-两位序号） |
| parent_no | VARCHAR(32) | 否 | NULL | 母单号快照：整票为空；拆单时=子单所属母单号，用于右栏/打印/卸车同时展示母子关系 |
| is_child | TINYINT(1) | 是 | 0 | 是否子单快照：0=整票，1=子单；随 waybill_id 一并快照，避免打印联表 |
| split_ratio | DECIMAL(9,6) | 否 | NULL | 拆单件数占比：子单件数 ÷ 母单原件数，保留 6 位；整票为 NULL；仅用于展示/审计，金额以子单快照为准 |
| route_node_seq | TINYINT UNSIGNED | 是 | — | 本段路由节点序号：运单路由节点表中与目的网点匹配的那一段 seq，用于到站后回写当前节点 |
| dest_hub_id | BIGINT UNSIGNED | 是 | — | 本段到达网点ID：= 运单当前路由下一节点，必须等于子车次目的网点 |
| dest_hub_name | VARCHAR(50) | 是 | — | 本段到达网点名称快照 |
| dest_name | VARCHAR(200) | 否 | NULL | 运单最终目的地快照：省+市+区县，用于运输清单展示 |
| customer_name | VARCHAR(100) | 否 | NULL | 客户名称快照 |
| receiver_name | VARCHAR(50) | 否 | NULL | 收货人姓名快照：运输清单与末端联系使用 |
| receiver_phone | VARCHAR(20) | 否 | NULL | 收货人电话快照 |
| goods_name | VARCHAR(100) | 否 | NULL | 货物名称快照 |
| quantity | INT UNSIGNED | 是 | — | 本配载单元件数：整票=运单件数；子单=拆单件数（本车份额）；用于车次汇总、超载校验与清单 |
| weight | DECIMAL(12,3) | 是 | — | 本配载单元重量(kg)：整票=运单重量；子单=按件数占比折算的重量；用于超重校验、装载率与分摊基数 |
| volume | DECIMAL(12,3) | 是 | — | 本配载单元体积(m³)：整票=运单体积；子单=按件数占比折算的体积；用于超容校验、装载率与分摊基数 |
| prepaid_fee | DECIMAL(12,2) | 是 | 0 | 现付运费快照(元)：取配载单元的现付运费（子单=按件数拆分后的金额），收入项，用于分票拆分 |
| collect_fee | DECIMAL(12,2) | 是 | 0 | 到付运费快照(元)：取配载单元的到付运费（子单=拆分后金额），收入项 |
| return_fee | DECIMAL(12,2) | 是 | 0 | 回付运费快照(元)：取配载单元的回付运费（子单=拆分后金额），收入项 |
| monthly_fee | DECIMAL(12,2) | 是 | 0 | 月结运费快照(元)：取配载单元的月结运费（子单=拆分后金额），收入项 |
| total_fee | DECIMAL(12,2) | 是 | 0 | 运费合计快照(元)：= 现付 + 到付 + 回付 + 月结；服务端计算 |
| rebate_total | DECIMAL(12,2) | 是 | 0 | 返款合计快照(元)：取配载单元返款（子单=按件数拆分后的现返+欠返），毛利中扣减一次 |
| valid_freight | DECIMAL(12,2) | 是 | 0 | 有效运费快照(元)：取配载单元有效运费（子单=按件数拆分后的值），仅展示 |
| allocated_fee | DECIMAL(14,2) | 是 | 0 | 本票成本分摊合计(元)：本票全部成本项分摊结果之和，服务端计算，用于成本对账 |
| profit_cost | DECIMAL(14,2) | 是 | 0 | 本票参与毛利的成本分摊合计(元)：仅汇总 profit_enabled=1 的成本项分摊金额；服务端计算 |
| gross_profit | DECIMAL(14,2) | 是 | 0 | 本票毛利(元)：= 本票四类运费合计 − 本票返款 − profit_cost；服务端计算 |
| status_before_load | TINYINT UNSIGNED | 是 | — | 配载前单元状态：加入车次前的配载单元状态（整票 20/60；子单 20），移除或关闭车次时用于还原 |
| load_status | TINYINT UNSIGNED | 是 | 1 | 配载状态：1=已配载，2=已发车，3=已到站，4=已卸车；随车次/子车次操作联动，按配载单元独立流转 |
| load_time | DATETIME | 是 | CURRENT_TIMESTAMP | 配载时间 |
| load_by | BIGINT UNSIGNED | 否 | NULL | 配载操作人用户ID |
| load_by_name | VARCHAR(50) | 否 | NULL | 配载操作人姓名快照 |
| dept_id | BIGINT UNSIGNED | 是 | — | 归属机构ID：= 主车次 dept_id |
| create_by / create_time / update_by / update_time / deleted | — | — | — | 审计字段 |

索引与唯一性：
- `KEY idx_trip_status (trip_id, load_status)`
- `KEY idx_trip_dest (trip_dest_id)`
- `KEY idx_waybill (waybill_id, deleted)`
- `KEY idx_parent_no (parent_no)`（v1.3 新增：按母单查本车/打印母子关系）
- **活跃唯一性（D7 已定：不加 active_flag）**：同一**配载单元**（整票母单或某一张子单）同一时刻只能有一条 `deleted=0 且 load_status IN (1,2,3)` 的配载记录；**同一母单的不同子单可同时活跃在不同车次**。采用：
  1. 配载事务内 `SELECT ... FOR UPDATE` 锁配载单元行（`biz_waybill.id`）+ 条件更新 `biz_waybill.load_status=0`（更新 0 行即拒绝）；
  2. 服务层校验该单元的活跃配载记录（`waybill_id` + `load_status IN (1,2,3)` + `deleted=0`），与行锁共同防并发；不建 `active_flag` 列与唯一索引。
  3. 拆单事务锁**母单行**，校验「本车件数 ≤ 母单剩余件数」后再生成子单；同一母单并发拆分由母单行锁串行化（见 4.5/8.4）。

> **送货方式不加快照列（v1.2-r5）**：末端到站/卸车清单与「到站卸车费」分摊范围判断，实时读取 `biz_waybill.delivery_method`（3=大车直送），不在 `biz_trip_waybill` 加快照列——改单变更送货方式后即刻生效，同时不新增迁移列。

### 3.4 `biz_trip_fee` 车次费用明细表

> 只存**车次录入的成本项**（付给承运商/作业方的支出）。四类运费收入取运单字段汇总，不进本表；分摊方式统一在 `biz_trip.alloc_method`，本表不再逐项存。

| 字段 | 类型 | 必填 | 默认 | 中文注释（业务含义 + 取值含义） |
|---|---|---|---|---|
| id | BIGINT UNSIGNED | 是 | 自增 | 主键ID |
| trip_id | BIGINT UNSIGNED | 是 | — | 车次ID：逻辑引用 biz_trip.id |
| trip_dest_id | BIGINT UNSIGNED | 是 | 0 | 子车次ID：0=车次级费用（全车录入区）；>0=目的网点级费用（到站卸车页内联区录入的「到站卸车费」「到站其他费」）；逻辑引用 biz_trip_dest.id |
| fee_code | VARCHAR(16) | 是 | — | 费用项编码：引用 sys_fee_type.fee_code，如 FEE0010 |
| fee_name | VARCHAR(64) | 是 | — | 费用项名称快照 |
| fee_category | TINYINT UNSIGNED | 是 | 2 | 费用类型快照：1=收入，2=成本，3=代收，4=代付；**车次费用明细实际只落 2=成本**（收入取运单字段、代收/代付不进车次分摊） |
| amount | DECIMAL(14,2) | 是 | 0 | 费用金额(元)：≥0；`trip_dest_id=0` 时为全车该费用项总额，`trip_dest_id>0` 时为该子车次录入额 |
| profit_enabled | TINYINT(1) | 是 | 0 | 参与毛利计算快照：1=参与，0=不参与；保存时从 sys_fee_type 复制 |
| profit_direction | TINYINT UNSIGNED | 否 | NULL | 参与方向快照：1=收入，2=成本；profit_enabled=0 时为空 |
| fee_status | TINYINT UNSIGNED | 是 | 1 | 费用状态：1=有效，2=已失效（到站卸车费/到站其他费在取消到站/取消卸车时置 2；**金额保留用于审计，不参与汇总与分摊**） |
| invalid_reason | VARCHAR(200) | 否 | NULL | 失效原因：取消到站/取消卸车时写入，如「取消卸车（子车次 CT20261125001-01）」；有效时为空 |
| sort_order | INT | 是 | 0 | 排序号：费用录入区展示顺序 |
| dept_id | BIGINT UNSIGNED | 是 | — | 归属机构ID：= 主车次 dept_id |
| create_by / create_time / update_by / update_time / deleted | — | — | — | 审计字段 |

索引：
- `UNIQUE uk_trip_fee (trip_id, trip_dest_id, fee_code)`
- `KEY idx_trip (trip_id)`

### 3.5 `biz_trip_waybill_fee` 费用分摊明细表

| 字段 | 类型 | 必填 | 默认 | 中文注释（业务含义 + 取值含义） |
|---|---|---|---|---|
| id | BIGINT UNSIGNED | 是 | 自增 | 主键ID |
| trip_id | BIGINT UNSIGNED | 是 | — | 车次ID：逻辑引用 biz_trip.id |
| trip_waybill_id | BIGINT UNSIGNED | 是 | — | 配载明细ID：逻辑引用 biz_trip_waybill.id |
| waybill_id | BIGINT UNSIGNED | 是 | — | 运单ID：冗余，便于按运单查费用 |
| fee_code | VARCHAR(16) | 是 | — | 费用项编码 |
| fee_name | VARCHAR(64) | 是 | — | 费用项名称快照 |
| alloc_method | TINYINT UNSIGNED | 是 | — | 分摊方式快照：取车次 `biz_trip.alloc_method`（全车统一），1=重量，2=体积，3=重泡比计费重量 |
| basis_value | DECIMAL(14,3) | 是 | 0 | 本票分摊基数：按方式取重量/体积/计费重量 |
| basis_total | DECIMAL(16,3) | 是 | 0 | 车次分摊基数合计：同方式下全部配载运单基数之和 |
| alloc_ratio | DECIMAL(12,8) | 是 | 0 | 分摊比例：basis_value ÷ basis_total，保留 8 位 |
| amount | DECIMAL(14,2) | 是 | 0 | 本票分摊金额(元)：含尾差调整，全部运单合计必须等于费用项总额 |
| dept_id | BIGINT UNSIGNED | 是 | — | 归属机构ID：= 主车次 dept_id |
| create_time | DATETIME | 是 | CURRENT_TIMESTAMP | 创建时间 |

索引：
- `UNIQUE uk_tw_fee (trip_waybill_id, fee_code)`
- `KEY idx_trip (trip_id)`
- `KEY idx_waybill (waybill_id)`

### 3.6 `biz_trip_log` 车次操作日志表

| 字段 | 类型 | 必填 | 默认 | 中文注释（业务含义 + 取值含义） |
|---|---|---|---|---|
| id | BIGINT UNSIGNED | 是 | 自增 | 主键ID |
| trip_id | BIGINT UNSIGNED | 是 | — | 车次ID：逻辑引用 biz_trip.id |
| trip_no | VARCHAR(32) | 是 | — | 车次编号快照 |
| action_type | TINYINT UNSIGNED | 是 | — | 动作类型：1=创建，2=修改，3=配载，4=移除配载，5=发车，6=取消发车，7=到站，8=完成卸车，9=关闭，10=删除，11=车辆信息变更，12=费用信息变更，13=运单改单刷新，14=运单作废解除配载，15=目的网点变更，16=取消到站，17=取消卸车 |
| dest_id | BIGINT UNSIGNED | 否 | NULL | 子车次ID：到站/卸车动作记录；其他动作为空 |
| before_snapshot | JSON | 否 | NULL | 操作前快照：状态变更类必填，记录车次状态、运单集合、费用汇总等关键字段 |
| after_snapshot | JSON | 否 | NULL | 操作后快照：结构同 before_snapshot |
| reason | VARCHAR(500) | 否 | NULL | 操作原因：取消发车、关闭、删除时必填 |
| operator_id | BIGINT UNSIGNED | 是 | — | 操作人用户ID |
| operator_name | VARCHAR(50) | 是 | — | 操作人姓名快照 |
| dept_id | BIGINT UNSIGNED | 是 | — | 操作机构ID：数据权限锚点 |
| create_time | DATETIME | 是 | CURRENT_TIMESTAMP | 操作时间 |

索引：`KEY idx_trip_time (trip_id, create_time)`、`KEY idx_action (action_type, create_time)`、`KEY idx_dept (dept_id)`

> 到站费用日志口径（沿用现有动作类型，不新增）：录入/修改两项费用各写一条 `action_type=12 费用信息变更`（`field_name=arrival_unload_fee` / `arrival_other_fee`，记录 old_value/new_value 与 trip_dest_id）；卸车动作写 `action_type=8 完成卸车`；`action_type=7 到站` 只记状态，不含费用。二者解耦、各自记账。
>
> 回退口径（v1.3）：取消到站写 `action_type=16`，取消卸车写 `action_type=17`；二者 `dest_id`、`reason` 必填，`before_snapshot/after_snapshot` 记录子车次状态、运单状态/`load_status`、库存回退前后值；若同时触发两项到站费用失效，另各写 12（`field_name=arrival_unload_fee / arrival_other_fee`，new_value 为失效标记与原金额）。

### 3.7 `biz_trip_no_seq` 车次号日流水表

| 字段 | 类型 | 必填 | 默认 | 中文注释 |
|---|---|---|---|---|
| seq_date | DATE | 是 | — | 流水日期：按服务器自然日切分，每天一行 |
| current_val | INT UNSIGNED | 是 | 0 | 当日已分配的最大流水号：从 1 开始原子递增，用于车次号后 3 位 |
| update_time | DATETIME | 是 | CURRENT_TIMESTAMP ON UPDATE | 最后分配时间 |

主键 `PRIMARY KEY (seq_date)`；该表无 `dept_id`，需登记数据权限豁免（同 `biz_waybill_no_seq`）。

### 3.8 `sys_fee_type` 费用类型配置表（收入/成本/代收/代付 + 总部全局）

> D4 已定：**总部全局配置，不建目录表**。v1.3 起本表统一承载**收入 / 成本 / 代收 / 代付**四类费用类型；**四类运费收入（现付/到付/回付/月结）为代码级只读行（INC01–INC04），不落本表**，列表接口与配置行混排返回。费用项目录仍由后端代码维护（名称↔字段↔默认类型），`sys_fee_type` 只存可编辑的配置行。

| 字段 | 类型 | 必填 | 默认 | 中文注释（业务含义 + 取值含义） |
|---|---|---|---|---|
| id | BIGINT UNSIGNED | 是 | 自增 | 主键ID |
| fee_code | VARCHAR(16) | 是 | — | 费用项编码：成本/代收/代付新建时 `FEE` + 4 位流水，如 FEE0010；种子预置，生成后不可修改；收入内置四项固定 `INC01`–`INC04`（虚拟行，不落本表） |
| fee_name | VARCHAR(64) | 是 | — | 费用项名称：从代码级费用项目录选择，创建后不可修改；如「现付运输费」 |
| fee_category | TINYINT UNSIGNED | 是 | 2 | **费用类型：1=收入，2=成本，3=代收，4=代付**；内置收入四项恒为 1；新增时四类可选 |
| enabled | TINYINT(1) | 是 | 1 | 是否启用：1=启用（成本项参与车次费用录入与分摊），0=停用；内置收入四项恒为启用，不可停 |
| profit_enabled | TINYINT(1) | 是 | 1 | 是否参与毛利计算：1=参与，0=不参与；**3=代收、4=代付强制为 0**；收入/成本默认 1，可按项关闭（内置收入四项恒为 1） |
| profit_direction | TINYINT UNSIGNED | 否 | 2 | 参与方向：1=收入（加项），2=成本（减项）；**3/4 必须为空**；1=收入必须为 1，2=成本必须为 2 |
| source_field | VARCHAR(64) | 否 | NULL | 取数来源标识：收入内置项对应运单字段（如 prepaid_freight）、成本项对应 `biz_trip` 成本字段名（如 prepaid_transport_fee）；代收/代付与新建收入类型为空 |
| sort_order | INT | 是 | 0 | 排序号：费用配置列表与车次费用录入区展示顺序 |
| remark | VARCHAR(500) | 否 | NULL | 备注 |
| dept_id | BIGINT UNSIGNED | 是 | — | 归属机构ID：固定总部ID；本表登记数据权限豁免（D4） |
| create_by / create_time / update_by / update_time / deleted | — | — | — | 审计字段 |

索引：`UNIQUE uk_fee_code (fee_code)`、`KEY idx_enabled_sort (enabled, sort_order)`、`KEY idx_category_sort (fee_category, sort_order)`、`KEY idx_name (fee_name)`

> 说明：① 收入四项为**代码级只读行**，列表接口返回但操作列为「—」，不占 16 项种子计数；② 新建的收入类型本期只作分类与台账占位，`source_field` 为空、不参与车次费用录入与分摊（避免与四类运费收入重复计收）；③ 代收/代付不参与毛利、方向为空、不进车次分摊；④ **现返/欠返**作为运单级成本项保留在本表中（`profit_enabled=0`，只用于对账，不进车次录入区），口径见 4.1；⑤ 是否允许分公司自定义：D4 已定**总部全局**，不做分公司级配置。

### 3.9 `biz_waybill` 运单表新增字段（改造现有表）

| 字段 | 类型 | 必填 | 默认 | 中文注释（业务含义 + 取值含义） |
|---|---|---|---|---|
| current_hub_id | BIGINT UNSIGNED | 否 | NULL | 当前库存网点ID：引用 sys_dept.id；开单时=始发网点，卸车后=到达网点；待配载池按此匹配车次始发网点 |
| current_hub_name | VARCHAR(50) | 否 | NULL | 当前库存网点名称快照 |
| current_node_seq | TINYINT UNSIGNED | 是 | 0 | 当前已完成路由节点段序号：0=仍在始发网点未发出，N=已完成第 N 段并落在该段到达网点；下一节点取 `seq=N+1` |
| inventory_time | DATETIME | 否 | NULL | 库存落入当前网点时间：揽收完成或卸车完成时间；待配载池按此升序（FIFO） |
| load_status | TINYINT UNSIGNED | 是 | 0 | 干线配载状态：0=待配载（含分拨中心卸车后回池），1=已配载（70），2=已发车（80），3=已到站（50/90），4=已卸车终态（仅末端网点 100/110/120）；由车次操作维护，用于池过滤与防重复配载 |

索引：`KEY idx_current_hub (current_hub_id, load_status, status)`、`KEY idx_inventory_time (inventory_time)`、`KEY idx_parent_deleted (parent_id, deleted)`（v1.3 新增：子单汇总/母单余额计算）

> `load_status` 与 `status` 的关系：`status` 是全局运单状态，`load_status` 只描述干线车次生命周期，两者由服务层在同一事务内同步更新，禁止各自单独改。引入 `load_status` 的目的是让待配载池查询不依赖子查询（数据权限拦截器禁止子查询），同时用条件更新防止并发重复配载。

### 3.10 `sys_vehicle` / `sys_driver` 车辆 / 司机最小主数据（D1 已定：本期建）

> D1 结论：本期建最小主数据表，车次页以「选择 + 快速新增」方式使用；完整运力管理页仍归运力模块后续做。

**`sys_vehicle` 车辆主数据**

| 字段 | 类型 | 必填 | 默认 | 中文注释（业务含义 + 取值含义） |
|---|---|---|---|---|
| id | BIGINT UNSIGNED | 是 | 自增 | 主键ID |
| plate_no | VARCHAR(20) | 是 | — | 车牌号：唯一；车次选择/快速新增的唯一键 |
| vehicle_type | VARCHAR(50) | 否 | NULL | 车辆型号：文本，本期不建车型表（菜单 1903 后续补） |
| vehicle_load | DECIMAL(12,3) | 否 | NULL | 核定载重(kg)：车次超重校验来源，选择后带出、不可改 |
| vehicle_volume | DECIMAL(12,3) | 否 | NULL | 核定容积(m³)：车次超容校验来源，选择后带出、不可改 |
| owner_name | VARCHAR(50) | 否 | NULL | 车主名称：自有/外协车主 |
| owner_phone | VARCHAR(20) | 否 | NULL | 车主电话 |
| status | TINYINT UNSIGNED | 是 | 1 | 状态：1=启用，0=停用；停用后不在选择器出现 |
| dept_id | BIGINT UNSIGNED | 是 | — | 归属机构ID：车辆所属机构；数据权限锚点 |
| create_by / create_time / update_by / update_time / deleted | — | — | — | 审计字段 |

索引：`UNIQUE uk_plate_no (plate_no, deleted)`、`KEY idx_dept_status (dept_id, status)`

**`sys_driver` 司机主数据**

| 字段 | 类型 | 必填 | 默认 | 中文注释（业务含义 + 取值含义） |
|---|---|---|---|---|
| id | BIGINT UNSIGNED | 是 | 自增 | 主键ID |
| driver_name | VARCHAR(50) | 是 | — | 司机姓名 |
| driver_phone | VARCHAR(20) | 是 | — | 司机电话：车次发车前必填项来源 |
| license_no | VARCHAR(30) | 否 | NULL | 驾驶证号 |
| status | TINYINT UNSIGNED | 是 | 1 | 状态：1=启用，0=停用；停用后不在选择器出现 |
| dept_id | BIGINT UNSIGNED | 是 | — | 归属机构ID：司机所属机构；数据权限锚点 |
| create_by / create_time / update_by / update_time / deleted | — | — | — | 审计字段 |

索引：`KEY idx_name_phone (driver_name, driver_phone)`、`KEY idx_dept_status (dept_id, status)`

> 快速新增：车次页「选择车辆/司机」弹窗内提供 `+ 快速新增`，只需最少字段（车辆：车牌 + 载重 + 容积；司机：姓名 + 电话）即可落主数据并回填，减少切换页面的成本。

### 3.11 `sys_config` 系统全局配置表（D3 全局重泡比）

| 字段 | 类型 | 必填 | 默认 | 中文注释（业务含义 + 取值含义） |
|---|---|---|---|---|
| id | BIGINT UNSIGNED | 是 | 自增 | 主键ID |
| config_key | VARCHAR(64) | 是 | — | 配置键：全局唯一，如 `fee.volume_weight_ratio` |
| config_value | TEXT | 是 | — | 配置值：字符串存储；数值型为数值字符串（如 `333`），JSON 型存公式配置文本（如 `gross.formula`） |
| config_name | VARCHAR(100) | 是 | — | 配置名称：中文展示名，如「全局默认重泡比(kg/m³)」 |
| value_type | VARCHAR(16) | 是 | string | 值类型：string/number/bool/json；重泡比为 number |
| remark | VARCHAR(500) | 否 | NULL | 说明：业务含义、取值范围 |
| create_by / create_time / update_by / update_time / deleted | — | — | — | 审计字段 |

索引：`UNIQUE uk_config_key (config_key)`

> 本期落两个键：`fee.volume_weight_ratio`（全局默认重泡比，默认 333 kg/m³）与 `gross.formula`（运单毛利公式 JSON + 版本号 + 修改人/时间 + 变更历史，见 6.11）。本表为平台级配置，登记数据权限豁免。

### 3.12 `sys_route` 线路表新增重泡比列（改造现有表）

| 字段 | 类型 | 必填 | 默认 | 中文注释（业务含义 + 取值含义） |
|---|---|---|---|---|
| volume_weight_ratio | DECIMAL(10,2) | 否 | NULL | 线路重泡比(kg/m³)：D3 两级取值的线路级覆盖；为空或 0 时回落 `sys_config.fee.volume_weight_ratio` 全局值；新建线路默认继承全局值（前端带出，可改） |

索引：无需新增索引（随线路主键/既有索引取用）。

> 取数路径：某票运单的当前段路由为 `biz_waybill_route_node` 中 `seq = current_node_seq + 1` 的行；其 `route_scope/route_id` 指向 `sys_route` 时取该线路 `volume_weight_ratio`；外转路由、无 route_id 或线路系数为空/0 时回落全局值。车次不再存单一重泡比系数（原 `biz_trip.volume_weight_ratio` 删除）。

---

## 4. 费用与毛利

### 4.1 费用项清单与种子（统一列表口径，`sys_fee_type` 初始化 16 项）

> **v1.3 统一列表**：费用类型配置页把收入、成本、代收、代付放在**一张列表**。收入四项为代码级只读行（`INC01`–`INC04`，不落 `sys_fee_type`、不计入 16 项种子）；种子仍为 **16 项成本类配置**（14 项车次成本项 + 现返/欠返 2 项运单级成本项）；列表页面合计显示「共 20 项（收入 4 / 成本 16）」。

**收入侧（代码级只读行，不进 `sys_fee_type`，取运单字段按票汇总）**：

| 编码 | 收入项 | 取数字段 | 说明 |
|---|---|---|---|
| INC01 | 现付运费 | `biz_waybill.prepaid_fee` | 只读行，操作列「—」；按票汇总到 `biz_trip.total_prepaid_freight` |
| INC02 | 到付运费 | `biz_waybill.collect_fee` | 只读行；按票汇总到 `biz_trip.total_collect_freight` |
| INC03 | 回付运费 | `biz_waybill.return_fee` | 只读行；按票汇总到 `biz_trip.total_return_freight` |
| INC04 | 月结运费 | `biz_waybill.monthly_fee` | 只读行；按票汇总到 `biz_trip.total_monthly_freight` |

> 上述四项不参与分摊、不落 `biz_trip_fee`，是毛利公式的收入来源；「运费合计」= 四项之和。配置页中固定排在最前，类型=收入、参与毛利=是、方向=收入，全部只读。

**成本侧（进 `sys_fee_type`，参与车次录入与分摊）**：

| 序号 | 费用项名称 | 编码（种子） | 取数字段 | 类型 | 参与毛利 | 方向 | 状态 |
|---|---|---|---|---|---|---|---|
| 1 | 现付运输费 | FEE0010 | `biz_trip.prepaid_transport_fee` | 成本 | 是 | 成本 | 启用 |
| 2 | 回付运输费 | FEE0011 | `biz_trip.return_transport_fee` | 成本 | 是 | 成本 | 启用 |
| 3 | 到站付运输费 | FEE0012 | `biz_trip_dest.arrival_transport_fee` | 成本 | 是 | 成本 | 启用（V36 起按目的网点录入，只摊本网点运单） |
| 4 | 现付油卡费 | FEE0013 | `biz_trip.prepaid_oil_card_fee` | 成本 | 是（本次调整） | 成本 | 启用 |
| 5 | 整车过磅费 | FEE0014 | `biz_trip.whole_weighing_fee` | 成本 | 是 | 成本 | 启用 |
| 6 | 整车保险费 | FEE0015 | `biz_trip.whole_insurance_fee` | 成本 | 是 | 成本 | 启用 |
| 7 | 分拨装车费 | FEE0016 | `biz_trip.bulk_load_fee` | 成本 | 是 | 成本 | 启用 |
| 8 | 分拨卸车费 | FEE0017 | `biz_trip.bulk_unload_fee` | 成本 | 是 | 成本 | 启用 |
| 9 | 分拨其他费 | FEE0018 | `biz_trip.bulk_other_fee` | 成本 | 是 | 成本 | 启用 |
| 10 | 到站卸车费 | FEE0019 | `biz_trip.arrival_unload_fee`（车次级=Σ 子车次；明细落 `biz_trip_fee.trip_dest_id>0`） | 成本 | 是 | 成本 | 启用；**仅干线 + 末端网点，在到站卸车页内联区录入**，见 6.10 |
| 11 | 到站其他费 | FEE0020 | `biz_trip.arrival_other_fee`（车次级=Σ 子车次；明细落 `biz_trip_fee.trip_dest_id>0`） | 成本 | 是 | 成本 | 启用；**与到站卸车费同页同范围录入**，见 6.10 |
| 12 | 提货成本 | FEE0021 | `biz_trip.total_pickup_cost` | 成本 | 是 | 成本 | 停用（enabled=0） |
| 13 | 派送费 | FEE0022 | `biz_trip.total_delivery_fee` | 成本 | 是 | 成本 | 停用（enabled=0） |
| 14 | 外转费 | FEE0023 | `biz_trip.total_transfer_fee` | 成本 | 是 | 成本 | 停用（enabled=0） |
| 15 | 现返 | FEE0024 | `biz_waybill.cash_back` | 成本 | 否 | — | 启用（运单级，不进车次录入区） |
| 16 | 欠返 | FEE0025 | `biz_waybill.owe_back` | 成本 | 否 | — | 启用（运单级，不进车次录入区） |

**本次删除的旧收入项**（不再配置、不再参与任何计算）：运费、送货费、提货费、其他费、回单费、制单费、运单保费。

**现返 / 欠返的定位与防重复口径（固定口径，后续评审不得以「冗余」为由删除）**：

- 两者保留在费用项清单与种子中，类型=成本、`profit_enabled=0`、`profit_direction` 为空，取数字段仍是 `biz_waybill.cash_back` / `biz_waybill.owe_back`。
- 二者是**运单级取数**，只用于分票展示与对账，不展示在车次费用录入区、不参与车次成本分摊、不写 `biz_trip_fee`。
- 毛利中返款只按 `biz_waybill.rebate_total`（= 现返 + 欠返）**扣减一次**；现返/欠返因 `profit_enabled=0` 不参与毛利，因此不存在二次扣减，也不允许用二者替换 `rebate_total` 的扣减。
- 车次费用录入区的渲染规则：只展示 `source_field` 以 `biz_trip.` 开头的成本项；`biz_waybill.*` 来源的项（现返/欠返）不渲染输入框。

**代收 / 代付（v1.3 新增类型）**：作为费用类型可选，`profit_enabled` 强制为 0、`profit_direction` 为空、不进车次费用录入与分摊；本期只作分类配置与后续扩展，不对接代收货款字段（`biz_waybill.cod_amount` 仍独立，不参与毛利）。

**列表合计口径**：默认显示总项数；按费用类型筛选后显示筛选结果数。种子 16 项 + 收入只读 4 项 = 列表共 20 行。

### 4.2 费用分摊标准函数（跨模块复用，D2 全车统一）

统一实现 `FeeAllocator`（`common` 或 `operation` 下的无状态工具类），入参：费用总额、运单基数列表、**全车统一分摊方式**、每票重泡比系数；出参：每票金额列表。

```
计费重量_i = MAX(实际重量_i, 体积_i × 该票所属线路重泡比系数)
基数_i =
  方式 1（按重量）      → 实际重量_i
  方式 2（按体积）      → 体积_i
  方式 3（按重泡比）    → 计费重量_i

金额_i = round(总额 × 基数_i / Σ基数, 2)
尾差 = 总额 − Σ金额_i
→ 尾差加到基数最大的一票（基数并列取运单号较小者），保证 Σ金额 = 总额
```

- **D2：全车共用一个分摊方式**，存 `biz_trip.alloc_method`，所有启用成本项共用；费用页只出现一个下拉，不再逐项选择。
- 只有车次录入的**成本项**进入分摊；四类运费收入不参与分摊。
- **到站卸车费/到站其他费的分摊范围（v1.3 内联录入）**：两项都在到站卸车页内联区按**子车次**录入，只在该子车次（目的网点）下的**非大车直送配载运单**之间分摊（`delivery_method=3` 不占用末端卸车作业，排除），不摊到其他子车次；分摊方式仍取全车 `biz_trip.alloc_method`（D2 指方式统一，不是范围统一）。分摊结果写 `biz_trip_waybill_fee`，`biz_trip_dest.arrival_unload_fee/arrival_other_fee` 记本子车次有效合计；`biz_trip.arrival_unload_fee/arrival_other_fee` 只是**车次级汇总字段**（= 各子车次有效合计之和），不再有车次级录入入口。**车次级成本（运输费、油卡费等）不受影响，仍按全车全部配载运单分摊。**
- **到站付运输费（只读，v1.3 补充）**：到站卸车页内联区在「到站其他费」右侧**只读展示**「到站付运输费」，取数车次级 `biz_trip.arrival_transport_fee`（发车 Tab 录入的那笔），空值显示 `0.00`；样式为灰底只读，与两个可编辑输入框明显区分。**不参与本子车次分摊预览、不生成 `biz_trip_fee.trip_dest_id>0` 的费用行**，仍按全车分摊、归车次级成本（与车次录入的到站付运输费同一条数据，不重复计成本）。
- **内联实时预览**：前端按当前输入值调用 `FeeAllocator` 同一口径做预览（逐票可折叠），保存时后端**重新计算**并以服务端结果落库；预览只用于输入反馈，不作为最终数据来源。
- 边界规则：
  - **D14：`Σ基数 = 0` 时阻断保存**，接口返回明确错误「分摊基数合计为 0，请补充运单重量/体积后重试」，要求补数据；不再记 0 放行。
  - 金额为 0：仍生成分摊行，金额 0，便于对账完整性。
  - 所有计算用 `BigDecimal`，禁止 `double`；舍入 `HALF_UP`。
  - 函数纯计算、不查库、不依赖 Spring，便于单元测试与后续模块复用。

### 4.3 重泡比（D3 两级：全局默认 + 线路覆盖）

- 公式：`计费重量 = MAX(实际重量, 体积 × 重泡比系数)`。
- **全局默认**：`sys_config` 中 `fee.volume_weight_ratio`，种子值 `333` kg/m³，由「系统设置 → 系统配置」维护。
- **线路覆盖**：`sys_route.volume_weight_ratio`，路由管理新增一列；新建线路默认继承全局值（带出可改）；**为空或 0 时回落全局值**。
- **按票取系数**：每票取自身当前段线路（`biz_waybill_route_node` 中 `seq = current_node_seq + 1` 的 `route_scope/route_id` → `sys_route`）；外转/无路由/id 为空时回落全局值。
- 车次不再存单一重泡比系数（原 `biz_trip.volume_weight_ratio` 删除），分摊时逐票取系数计算计费重量。
- 全局值修改后只影响之后保存的分摊；已落库分摊结果不回溯。

### 4.4 运单毛利公式与口径（v1.3 改为可配置）

> **口径更正（2026-11-25，家伟明确）**：毛利以**运单为最小计算单位**，公式变量全部是单票级；车次毛利 = Σ 已配载票毛利，不另设算法、不重复扣减。原「不做脚本编辑器」的自我决定**作废**，改为「运单毛利公式配置」（系统设置 → 运单毛利计算公式，见 6.11）：结构化勾选加/减项，仍不提供自由脚本编辑器。

**单票毛利（默认公式）**

```
单票毛利 = 该票收入合计（该票现付 + 到付 + 回付 + 月结运费）
          − 该票返款（biz_waybill.rebate_total）
          − 该票分摊到的成本项合计（运输费 / 油卡费 / 到站卸车费 / 到站其他费 …）
```

- 收入固定取该运单四字段，不再走费用项配置；删除的七个旧收入项不参与。
- 返款只扣一次（`rebate_total` = 现返 + 欠返）；现返/欠返保留在费用清单但 `profit_enabled=0`，仅作运单级取数与对账，不参与毛利，避免重复扣减。
- 成本项 = 车次录入并分摊到本票的成本；`biz_trip_waybill_fee` 逐票记录分摊额，`profit_cost` 只汇总 `profit_enabled=1` 的项；已失效（`biz_trip_fee.fee_status=2`）不计入。
- 公式可配置：勾选参与的收入项（加）、参与的成本项（减），并选择是否扣减返款；每项可设加/减。配置存 `sys_config`（key=`gross.formula`，公式 JSON + 版本号 + 修改人/时间），见 6.11。
  - 「参与毛利」开关（`sys_fee_type.profit_enabled`）决定某项**默认**是否进入公式；公式页可覆盖（取消或加回），保存后以公式页为准。
  - 代收 / 代付与现返 / 欠返不进入公式（台账 / 运单级项）。

**车次毛利（只做汇总）**

```
车次毛利 = Σ 已配载票毛利
```

- 未配载 / 已解除配载 / 已失效的票不计入车次汇总。
- 车次收入合计、返款合计、成本合计分别由逐票同名字段求和得到，不重新取数。

**分摊闭合与舍入（必须成立）**

- 每个成本项按分摊维度（重量/体积/重泡比，D2 全车统一）逐票计算；**每票分摊金额之和 = 车次该成本项合计**。
- 舍入：单项分摊额 `ROUND(基数占比 × 车次该项合计, 2)`；因四舍五入产生的尾差由**最后一票吸收**（吸收顺序 = 配载明细按 `id` 升序的最后一张），保证逐票之和精确等于车次合计。
- 到站费用（FEE0019/0020）按同一规则在子车次范围内闭合（分摊范围排除大车直送）。

**对账校验口径**

```
车次毛利 = Σ 票毛利 = 车次收入合计 − 返款合计 − 车次成本合计
```

- 任一等式不成立（差额 ≠ 0）即视为**分摊异常**：列表/详情页给出异常标记；接口 `GET /transport/trip/{id}/gross-check` 返回两侧金额与差额，供排障与重算（见 7.1）。
- 配置变更只影响之后的车次计算；已落库车次毛利不回溯（需重算走手工入口，本期不做）。

**费用类型配置**

- 「系统设置 → 财务配置 → 费用类型配置」为**收入/成本/代收/代付统一列表**：收入四项只读、成本项可编辑/启停、代收/代付不参与毛利；名称与编码不可改（见 6.8）。

### 4.5 拆单金额拆分（按件数，v1.3）

**适用与时机**

- 仅**未配载的整票**（`is_child=0`、`load_status=0`、无生效子单）可拆；已配载单元禁止再拆/再合（沿用 D6，见 8.5）。
- 拆单弹窗**只录本车装载件数 `q`**；重量、体积不单独录入，按件数占比折算（1.2 已定：本期不做按重量/体积拆单）。
- 子单金额只作**车次收入、毛利、分摊、打印与内部对账**的快照；客户结算、开票、返款仍以母单为准（对 operation v1.14「子单费用固定为 0、费用挂母单」的修订，见 8.7、12）。

**符号**

| 符号 | 含义 |
|---|---|
| `Q` | 母单原始件数（`biz_waybill.quantity`，拆单期间不变） |
| `q_rem` | 本次拆前母单剩余件数 = `Q − Σ已生成有效子单件数` |
| `q` | 本车装载件数（拆出子单件数），`1 ≤ q ≤ q_rem` |
| `A` | 母单某一金额项的原值（运费合计/送货费/现返/欠返，逐项独立计算） |
| `r` | 子单件数占比 = `q / Q`，保留 6 位（仅展示/审计，金额以拆分结果为准） |

**拆分量计算**

| 项 | 子单值 | 母单余额值 | 说明 |
|---|---|---|---|
| 件数 | `q` | `q_rem − q` | 余额 = 原 − Σ子单 |
| 重量 | `ROUND(原重量 × r, 3)` | `原重量 − Σ子单重量` | 尾差留余额，避免负数 |
| 体积 | `ROUND(原体积 × r, 3)` | `原体积 − Σ子单体积` | 同上 |
| 现付/到付/回付/月结运费 | 逐项按下方尾差规则 | 逐项按下方尾差规则 | 四项各自独立拆 |
| 送货费 | 按下方尾差规则 | 按下方尾差规则 | — |
| 现返、欠返 | 按下方尾差规则 | 按下方尾差规则 | 返款合计 = 现返 + 欠返 |
| 返款合计 | `子单现返 + 子单欠返` | `余额现返 + 余额欠返` | 派生，不独立拆 |
| 有效运费 | `子单运费合计 − 子单返款合计` | `余额运费合计 − 余额返款合计` | 派生；与按比例拆一致（线性） |

**尾差规则（归件数份额大的一方）**

对每个金额项 `A` 独立计算：

- 若 `q ≥ q_rem`（本车份额大）：`子单 = A − ROUND(A × q_rem / Q, 2)`，`余额 = ROUND(A × q_rem / Q, 2)`；
- 若 `q < q_rem`（余额份额大）：`余额 = A − ROUND(A × q / Q, 2)`，`子单 = ROUND(A × q / Q, 2)`；
- 多张子单逐张按上式处理；当 `q = q_rem`（最后一批）时，子单拿走全部余额，母单余额为 0；
- 恒等式：`Σ子单该项 + 母单余额该项 = A`，母子合计与拆前一分不差；
- 金额 `DECIMAL(12,2)`，重量/体积 `DECIMAL(12,3)`；服务端计算并覆盖，前端只做预览。

**示例（件数口径）**

母单 `YD20261123000126`：100 件、1000.000 kg、10.000 m³；现付 600.00、到付 400.00（运费合计 1000.00）、送货费 100.00、现返 50.00、欠返 10.00（返款合计 60.00）、有效运费 940.00。

第一次拆 30 件（`q=30`，`q_rem=100`，`q<q_rem`，`r=0.30`）：

| 项 | 子单 | 母单余额 |
|---|---|---|
| 件数 | 30 | 70 |
| 重量(kg) | 300.000 | 700.000 |
| 体积(m³) | 3.000 | 7.000 |
| 现付/到付运费 | 180.00 / 120.00 | 420.00 / 280.00 |
| 运费合计 | 300.00 | 700.00 |
| 送货费 | 30.00 | 70.00 |
| 现返 / 欠返 | 15.00 / 3.00 | 35.00 / 7.00 |
| 返款合计 | 18.00 | 42.00 |
| 有效运费 | 282.00 | 658.00 |

第二次拆 50 件（`q=50`，`q_rem=70`，`q<q_rem`，`r=0.50`）：子单运费 500.00、送货费 50.00、现返 25.00、欠返 5.00、返款 30.00、有效运费 470.00；余额变为 20 件、运费 200.00、送货费 20.00、现返 10.00、欠返 2.00、返款 12.00、有效运费 188.00。

第三次拆 20 件（`q=q_rem=20`）：子单拿走余额全部 —— 运费 200.00、送货费 20.00、返款 12.00、有效运费 188.00；母单余额件数与金额均为 0。

校验：运费 300+500+200=1000.00；返款 18+30+12=60.00；有效运费 282+470+188=940.00；件数 30+50+20=100。✓

**尾差示例（除不尽）**

母单 3 件、运费 100.00，本车拆 1 件：`q=1 < q_rem=2`，余额份额大 → 余额 = `100 − ROUND(100×1/3,2)` = `100 − 33.33` = **66.67**，子单 = **33.33**。
若本车拆 2 件：`q=2 ≥ q_rem=1`，本车份额大 → 余额 = `ROUND(100×1/3,2)` = **33.33**，子单 = `100 − 33.33` = **66.67**。两种拆法合计均为 100.00。✓

**其他口径**

- 分摊基数为**子单自身**的件数/重量/体积（见 4.2），不取母单全量；卸车、打印、末端卸车费同样按子单维度。
- 已拆母单再次拆单时，分母始终取原始 `Q`、基数始终取原金额 `A`，不从已拆子单二次拆分，避免误差累积。
- 一期只按件数；后续若实际重量/体积与件数比例差异较大，再单独放开「按重量/体积拆单」并补字段，本期不做。
- **与毛利的关系**：拆出的子单与母单余额各自是一个配载单元，按单票毛利公式（4.4/6.11）分别计算；车次毛利只汇总**已配载**的子单/整票，母单余额未配载不计入。金额拆分与毛利口径线性一致（有效运费 = 运费合计 − 返款合计），尾差归份额大的一方后，母子毛利之和与拆前一致。

---

## 5. 状态流转

### 5.1 车次状态

| 当前 | 触发动作 | 目标 | 前置条件 | 联动 |
|---|---|---|---|---|
| — | 创建车次 | 10 车次计划 | 必填项完整；目的网点 ≥1 | 生成车次号与子车次 |
| 10 | 配载第一票运单 | 20 装车配载 | 车次计划状态即可直接配载；运单匹配目的网点且未超限 | 运单 70；`load_status=1`；同一事务内车次自动转 20，无需先手工改状态 |
| 20 | 移除全部运单 | 10 车次计划 | 无配载运单 | — |
| 20 | 发车 | 30 车辆在途 | 车辆/司机/费用完整；预计到达时间已填；至少 1 票运单 | 运单 80；`load_status=2`；子车次 20 |
| 30 | 取消发车 | 20 装车配载 | 无任何子车次已到站；填写原因 | 运单 80→70；`load_status=1`；清空发车时间；子车次 20→10 |
| 30 | 第一个子车次到站 | 40 车辆部分到站 | — | 该子车次 30；运单 50/90（大车直送保持 80）；`load_status=3`（大车直送保持 2） |
| 40 | 剩余子车次到站 | 40 保持 | — | 同上 |
| 40 | 最后一个子车次到站 | 50 车辆到站 | 全部子车次 sub_status=30/40 | 写 `actual_arrive_time` |
| 50 | 任一子车次完成卸车 | 50 保持 | 该子车次已到站 | 该子车次 40；运单 60/100/110/120（大车直送保持 80/2，不参与）；分拨中心卸车回池 `load_status=0`，末端卸车终态 `load_status=4` |
| 50 | 全部子车次完成卸车 | 60 完成卸车 | 全部子车次 sub_status=40 | 记录各网点卸车时间/操作人 |
| 40 | 取消某子车次到站，且取消后无子车次到站 | 30 车辆在途 | 该子车次 sub_status=30、未卸车、无卸车记录 | 该子车次 20；运单 90/50→80；`load_status` 3→2；大车直送不动；写日志 16 |
| 40/50 | 取消某子车次到站，但仍有子车次到站 | 40 部分到站 / 50 车辆到站（按剩余重算） | 同上 | 同上；主车次按剩余已到站子车次重算（部分=40、全部=50） |
| 60 | 取消卸车 | 50 车辆到站 | 相关单元未被下一车次配载、未被签收/核销；车次未关闭 | 子车次回 30（本子车次仍有已卸车单元则保持 40）；运单 100/110/120→90 或 60→50；`load_status`→3（末端 4→3、分拨回池 0→3）；库存退回上一站（8.4）；到站卸车费标记失效；写日志 17 |
| 10/20 | 关闭车次 | 90 已关闭 | 未发车；填写原因 | 释放全部配载关系；运单回 20/60，`load_status=0` |
| 10 | 删除车次 | 逻辑删除 | 无配载运单、未发车 | 子车次一并逻辑删除 |

> 配载/移除入口在 10、20 两个状态均可用：10 配入第一票即自动转 20；20 移除最后一票即自动退回 10。5.4 矩阵已与此一致，不存在「计划状态不能配第一票」的死锁。

### 5.2 子车次状态

`10 待发车 → 20 在途（主车次发车）→ 30 已到站 → 40 完成卸车`；回退：`30 → 20`（取消到站，仅未卸车）、`40 → 30`（取消卸车）。

- 子车次到站只能由目的网点或有权限的调度操作。
- 发车后新增的子车次（D10）直接置 `20 在途`，不回到 `10 待发车`。
- 子车次到站/卸车只更新该子车次状态及其关联运单，不影响其他子车次的在途状态（需求「各目的网点卸车作业互不干扰」）。
- 子车次支持**逐票卸车**（到站卸车页按 luohuo 勾选交互，见 6.10）；该子车次全部运单卸完自动置 `40 完成卸车`。「子车次」弹窗只提供「到站卸车页」入口，不再内置到站/卸车按钮。
- **大车直送口径（v1.2-r5）**：子车次下的运单若 `delivery_method=3 大车直送`，不进入该子车次的末端到站/卸车清单（查询排除），不参与卸车动作。判定是否可「卸车」时，只统计**非大车直送**运单；若该子车次只剩大车直送运单（可卸 0 票），**允许直接完成卸车**，按 0 票处理并写日志（`action_type=8`，备注「无末端卸车运单，大车直送不参与」）。
- 主车次 `actual_arrive_time` 取所有子车次 `arrive_time` 的最大值。
- **取消到站（v1.3）**：前置 = 子车次 `30 已到站`、`unload_time IS NULL`、本子车次**全部单元均未卸车**（`biz_trip_waybill.unload_time IS NULL`；末端 `load_status≠4`、分拨回池 `load_status≠0`）、车次未关闭。回退 = 子车次 `30→20`，运单 `90/50→80`、`load_status 3→2`（大车直送保持 80/2 不动），清空 `arrive_time/arrive_by`（原值写日志快照），主车次按剩余已到站子车次重算（取消后无其他到站：`40/50→30`；仍有到站：保持 `40/50`），若之前已录两项到站费用则同步标记失效（5.6），写日志 16。
- **取消卸车（v1.3）**：前置 = 子车次 `40 完成卸车`（或该单元已卸车）、该票**未被下一车次有效配载**、**未被签收/核销**（`status∉(130,900)`）、车次未关闭。逐票取消：运单 `100/110/120→90` 或 `60→50`；`load_status→3`（**末端网点终态 4→3；分拨中心卸车即回池为 0，取消时 0→3**，若已被下一车次配载则拒绝），库存退回上一站（8.4）；本子车次仍有已卸车单元时保持 `40`，最后一票取消后回 `30` 并清空 `unload_time/unload_by`；主车次 `60→50`；两项到站费用保留金额并标记失效（5.6）；写日志 17。

### 5.3 运单状态联动

| 车次动作 | 运单原状态 | 运单新状态 | `load_status` | 说明 |
|---|---|---|---|---|
| 配载 | 20 已揽收 / 60 分拨卸车 | 70 分拨装车 | 1 | 干线装车 |
| 发车 | 70 | 80 分拨发车 | 2 | — |
| 子车次到站（目的网点是分拨中心 dept_type=4） | 80 | 50 分拨到车 | 3 | 到站后进入卸车 |
| 子车次到站（目的网点是末端网点 dept_type=5 且 is_leaf=1） | 80 | 90 末端到车 | 3 | 到站后进入卸车 |
| 子车次卸车（分拨中心） | 50 | 60 分拨卸车 | 0（回池） | 卸车后库存落入该分拨中心，`current_hub_id`=该网点，可立即参与下一段干线配载 |
| 子车次卸车（末端网点） | 90 | 100/110/120 | 4（终态） | 按送货方式：自提=100，送货=110，外转=120；不再参与干线配载 |
| 子车次到站（末端网点，`delivery_method=3` 大车直送） | 80 | 80（不变） | 2（不变） | 不经末端分流，不写末端到车时间，不做状态推进 |
| 子车次卸车（末端网点，`delivery_method=3` 大车直送） | 80 | 80（不变） | 2（不变） | 不出现在末端卸车清单，不参与卸车；后续由「直送签收」流程推进（本期占位） |
| 取消发车 | 80 | 70 | 1 | 仅未到站 |
| 关闭车次 | 70 | 20/60（按配载前状态还原） | 0 | 需在配载明细中保存「配载前状态」用于还原 |
| 取消到站（末端网点） | 90 | 80 分拨发车 | 2 | 仅该子车次且尚未卸车；大车直送保持 80/2 不动 |
| 取消到站（分拨中心） | 50 | 80 分拨发车 | 2 | 同上 |
| 取消卸车（末端网点） | 100/110/120 | 90 末端到车 | 3 | 逐票回退；要求未被下一车次配载、未签收/核销 |
| 取消卸车（分拨中心） | 60 | 50 分拨到车 | 3 | 库存退回上一站（8.4）；要求未被下一车次配载 |

> 上表的「分拨中心 50/60、末端 90/100/110/120」映射为 D9 已确认口径；`inventory_time` 取卸车完成时间（真正可再次配载的时点）。配载明细的 `status_before_load` 字段（见 3.3）保存配载前状态，用于关闭/移除时精确还原。
>
> **大车直送（v1.2-r5）**：`delivery_method=3` 的运单在末端网点**不进入**到站/卸车状态链路，状态停在 80 分拨发车、`load_status` 保持 2；一期卸车模块不碰，后续由「直送签收」模块推进（见第 10 节）。分拨中心卸车不受此规则影响（大车直送只针对末端分流）。

### 5.4 操作 × 状态矩阵

| 操作 | 10 计划 | 20 配载 | 30 在途 | 40 部分到站 | 50 到站 | 60 完成卸车 | 90 关闭 |
|---|---|---|---|---|---|---|---|
| 修改车次 | ✓ | ✓（仅基础信息） | ✗ | ✗ | ✗ | ✗ | ✗ |
| 删除车次 | ✓（无配载） | ✗ | ✗ | ✗ | ✗ | ✗ | ✗ |
| 配载/移除 | ✓（配第一票后自动转 20） | ✓（移除最后一票自动回 10） | ✗ | ✗ | ✗ | ✗ | ✗ |
| 目的网点-新增 | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ | ✗ |
| 目的网点-修改/删除 | ✓ | ✓（仅当该网点无配载运单） | ✓（同左） | ✓（同左） | ✓（同左） | ✓（同左） | ✗ |
| 车辆信息 | ✓ | ✓ | ✗ | ✗ | ✗ | ✗ | ✗ |
| 费用信息 | ✓ | ✓ | ✗ | ✗ | ✗ | ✗ | ✗ |
| 发车 | ✗ | ✓ | ✗ | ✗ | ✗ | ✗ | ✗ |
| 取消发车 | ✗ | ✗ | ✓ | ✗ | ✗ | ✗ | ✗ |
| 到站 | ✗ | ✗ | ✓ | ✓ | ✗ | ✗ | ✗ |
| 卸车（完成卸车） | ✗ | ✗ | ✗ | ✓ | ✓ | ✗ | ✗ |
| 取消到站 | ✗ | ✗ | ✗ | ✓（该子车次已到站且未卸车） | ✓（同左） | ✗ | ✗ |
| 取消卸车（运单级） | ✗ | ✗ | ✗ | ✓（作业页「已卸车」Tab 勾选已卸运单，且未签收/未被下游配载） | ✓（同左） | ✓（主车次回 50） | ✗ |
| 到站费用录入（卸车费 + 其他费；仅干线 + 末端网点；分摊范围排除大车直送） | ✗ | ✗ | ✗ | ✓（该子车次已到站） | ✓ | ✗ | ✗ |
| 关闭车次 | ✓ | ✓ | ✗ | ✗ | ✗ | ✗ | ✗ |
| 打印运输清单 | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ |

> D10 目的网点规则：**新增不受限**（含发车后；发车后新增的子车次直接置 `sub_status=20 在途`，无需跟随发车动作）；**修改/删除**某目的网点需该网点下无配载运单（`biz_trip_waybill.trip_dest_id` 无有效记录），否则提示「该目的网点下仍有 N 票运单，请先移除配载」。修改=改目的网点/顺序/计划到站时间；删除=逻辑删除子车次。
>
> 大车直送口径（v1.2-r5）：40/50 的「到站」与「卸车（完成卸车）」只处理 `delivery_method≠3` 的运单；子车次只剩大车直送运单（可卸 0 票）时，「卸车」仍可用，按 0 票完成并写日志，不做运单状态推进。

### 5.5 配载后运单变更（D6）

| 运单动作 | 是否允许 | 联动刷新（同一事务） | 车次日志 |
|---|---|---|---|
| 改单（`WaybillService.amend`） | 允许（配载中/在途/到站均可，按运单自身 amend 规则） | 重写配载明细快照（件数/重量/体积/运费/返款/有效运费/收货人/目的地）、重算分摊明细、车次汇总与装载率、毛利 | action_type=13 运单改单刷新 |
| 作废（`WaybillService.voidWaybill`） | 允许 | 解除该票配载（配载明细置删除）、运单 `load_status=0` 回池、重算车次汇总与分摊、写 `status_before_load` 还原 | action_type=14 运单作废解除配载 |
| 拆单 / 合单 | **禁止** | 配载中的单元（`load_status ≥ 1`）直接拦截，提示先从车次移除；拆单只发生在未配载整票上（见 5.7、8.5） | — |

- 「配载明细快照」指 `biz_trip_waybill` 上冗余的运单字段；改单后必须整行覆盖，避免打印清单与实际不一致。
- 分摊重算使用当前 `biz_trip.alloc_method` 与逐票重泡比系数；D14 基数合计为 0 时改单保存阻断并提示补重量/体积。
- 超载/超容不阻断：改单后若超限，仅前端提示条 + 车次日志记录超限快照。
- **送货方式变更（v1.2-r5）**：改单若变更 `delivery_method`，按新值重算末端参与范围——变为大车直送则把该单元移出末端到站/卸车清单、删除其到站卸车费分摊行并重算本子车次分摊；若本子车次因此无可分摊单元，`arrival_unload_fee` 置 0（`biz_trip_fee` 保留 0 金额行）并写日志 12，提示「本子车次已无末端卸车单元，到站卸车费已清零」；由直送改为非直送则重新纳入分摊范围。
- **拆单单元（v1.3）**：已配载子单禁止改件数/重量/体积与金额，仅可改收货人/电话；母单余额 > 0 时同样禁止改件数与金额（见 8.5），避免破坏母子合计与车次分摊。

### 5.6 到站费用规则（到站卸车费 + 到站其他费，v1.3 内联录入）

> 两项费用同页、同范围、同生命周期：在到站卸车页信息条下方的内联区一次录入，保存后各落一条 `biz_trip_fee`（FEE0019 / FEE0020，`trip_dest_id=当前子车次`），并按本子车次非大车直送单元分摊。`biz_trip.arrival_unload_fee / arrival_other_fee` 只是**车次级汇总字段**，不再有车次级录入入口。

| 项 | 规则 |
|---|---|
| 出现条件 | `trip_category=1`（干线）且卸车网点为**末端网点**（`sys_dept.dept_type=5`）；短驳（`trip_category=2`）与分拨中心卸车（`dept_type=4`）不出现；两项费用同时出现/隐藏 |
| 录入位置 | 到站卸车页**信息条下方内联费用区**：一行内「到站卸车费」「到站其他费」两个金额输入框 + 只读「分摊方式：按本车统一分摊方式：重量/体积/重泡比」 + 实时合计；下方「逐票明细」可折叠，改值即时重算；右侧「保存费用」落库。**原「到站卸车费」按钮与弹窗删除** |
| 录入时机 | 子车次 `30 已到站` 后启用；**未到站时整个费用区禁用**（输入框、保存、折叠预览均不可操作），提示「到站后可录入到站费用」；车次关闭后只读 |
| 落库 | 保存时 upsert 两条：`fee_code=FEE0019/FEE0020`、`trip_dest_id=当前子车次`、`amount=输入值`；`biz_trip_dest.arrival_unload_fee/arrival_other_fee` 记本子车次有效合计；`biz_trip` 两字段 = Σ 各子车次有效合计 |
| 金额口径 | 各自 ≥0、两位小数；0 表示不产生该费用（保存 0 时金额置 0，仍保留费用行便于对账）；两项可同时为 0 |
| 分摊范围 | 两项费用都只在该子车次下**非大车直送**（`delivery_method≠3`）的配载单元之间分摊，按全车 `alloc_method`（见 4.2）；不摊到其他子车次；车次级成本不受影响 |
| 分摊方式 | **只读展示**「按本车统一分摊方式：X」，与 `biz_trip.alloc_method` 同源；本期不允许在此修改。若后续确认允许修改，需同步重算全车所有成本项分摊与车次汇总，不在本期内联区开放 |
| 实时预览 | 输入变化即时按 4.2 同一函数重算逐票金额与合计（折叠明细：运单号/基数/卸车费分摊/其他费分摊/小计）；前端预览仅作输入反馈，**保存以后端重算结果为准**；预览失败或 `Σ基数=0` 时展示错误并禁用保存 |
| 无可分摊单元 | 本子车次全部为大车直送（0 个可分摊单元）时整区禁用，提示「本子车次无末端卸车单元，不产生到站费用」 |
| 联动（同一事务） | 保存后重算 `biz_trip_waybill_fee`（两项各自行）、本子车次单元 `allocated_fee/profit_cost/gross_profit`、`biz_trip.total_cost_fee`、`biz_trip.gross_profit`、子车次与车次汇总；写日志 12（`field_name=arrival_unload_fee / arrival_other_fee`，old/new）；卸车动作仍写 8（见 3.6） |
| 可改窗口 | 子车次 `30 已到站` 或 `40 完成卸车`，且车次未关闭；重复保存覆盖原值并留痕 |
| 取消到站/取消卸车（v1.3） | 两项都**保留金额、标记失效**：`biz_trip_fee.fee_status=2` + `biz_trip_dest.arrival_unload_fee_valid/arrival_other_fee_valid=0`，金额原样保留（不静默删、不物理删行），汇总与分摊按 0 计；写日志 16/17 + 12。重新到站/重新卸车后整区恢复可用，**失效金额作为输入框默认值预填**（仅预填，不自动生效），用户点「保存费用」后按新值重算并置回有效 |
| 权限 | 目的网点用户可对本网点子车次操作；D5 受控放开 + 强制校验 `dest_dept_id = 当前机构`（见 9.2）；按钮权限沿用 `到站卸车` |

> **分摊闭合与毛利**：两项费用作为子车次成本项，同样遵守 4.4 的闭合规则——本子车次每票分摊之和 = 该子车次该项合计，四舍五入尾差由本子车次内 `id` 升序最后一票吸收。保存后逐票 `profit_cost`/`gross_profit` 与车次汇总同步刷新；车次毛利只汇总已配载票，按 `GET /transport/trip/{id}/gross-check` 对账（见 4.4、6.11、7.1）。

### 5.7 拆单后的状态与母单汇总（v1.3）

**子单状态**：与普通运单一致、各自独立流转：`20 已揽收 → 70 分拨装车（装本车）→ 80 分拨发车 → 90 末端到车/50 分拨到车 → 100/110/120 或 60 分拨卸车 → 130 已签收`（见 5.3）。

**母单定位**：母单只作**汇总壳 + 余额载体**，不参与配载、不写配载明细、不参与发车。

| 场景 | 母单 `status`（存储） | 说明 |
|---|---|---|
| 未拆（无子单） | 保持 20/60 | 与普通运单一致，可直接整票配载 |
| 已拆且余额 > 0 | **保持余额库存状态 20/60** | 余额仍在 `current_hub_id`，必须满足池条件；不能被汇总值推走，否则回池失效 |
| 已拆且余额 = 0 | 写为有效子单汇总状态（取最落后一张） | 已无余额，仅作汇总展示；退出配载池 |
| 全部子单 130 | 130 已签收 | 仅全部子单签收后 |
| 全部子单作废且余额 = 0 | 900 已作废 | 无有效子单、无余额时 |
| 子单作废/撤销拆单导致余额恢复 > 0 | 回到余额库存状态 20/60 | 重新进入配载池；不写状态回退日志，写拆单/作废日志 |

> 对「母单状态取子单汇总」的落地细化：**余额 > 0 时存储状态优先保证库存语义**；前端另算「汇总状态」用于展示，两者分开。否则母单一旦被汇总成 70/80，余额就再也不能回池，等于把「一票多车」做死。

**汇总状态展示（实时计算，不落库）**

- `summary_status = MIN(有效子单 status)`，即最落后一票的状态；
- 余额 > 0 时，列表状态列显示 `MIN(母单余额状态, summary_status)`，并附文案「余额 n 件在 X 网点，m 张子单在途」；
- 混合末端（部分 90、部分 100/110/120）时汇总保持 90，不提前显示已完成；
- 子单清单逐张展示自己的状态。

**不回退与日志**

- 子单状态只前进；仅以下显式动作可回退：取消发车 `80→70`、移除配载 `70→配载前（20/60）`、撤销拆单（要求该子单未装车）、取消到站 `90/50→80`、取消卸车 `100/110/120→90` 或 `60→50`。
- 母单 `summary_status` 是派生值，随子单回退自动变化，不写状态日志、不算「状态回退」，避免日志与状态混乱。
- 显式回退必须写日志：拆单/撤销拆单写 `biz_waybill_log(action_type=1/3)`，移除配载写 `biz_trip_log`，取消发车写 `biz_trip_log`，均记录 before/after 与原因。

**拆单、作废与签收**

- 子单未装车可作废；作废后从母单汇总剔除，余额重算为 `Q − Σ有效子单件数`；若余额因此恢复 > 0 且母单已退出池，母单状态回到余额库存态 20/60 并重新入池；
- 子单已装车不可作废，须先左移移除再作废；
- 母单存在任一已装车子单时禁止整票作废，须先处理子单；
- 母单强制签收（沿用运单模块规则）不回写子单状态；子单签收各自独立。

**列表 / 详情 / 配载页**

- 运单列表仍只显示母单一行：母徽标 + 「子单(n)」入口，**不新增子单行**（D11 不变）；状态列按本节展示值；
- 运单详情：头部显示母单汇总状态与余额，子单清单增加「配载车次 / 子车次 / 装车时间 / 本单元件数重量体积 / 状态 / 金额快照」（见 8.7）；
- 配载页右栏：拆单行显示**子单号**，下方浅色小字显示「母单 xxx」；整票行不显示母单号；列头可切换显示母单号列。

**跨车次**：同一母单的不同子单可同时在多辆车；母单余额可继续拆给下一车；子单不可再拆（仅两级）。

---

## 6. 页面结构与交互

### 6.1 菜单与路由

| 项 | 值 |
|---|---|
| 菜单 | 运输管理 → 装车配载（已存在，id=1301）；运输管理 → 到站卸车（新增，到货卸车列表，id 待种子） |
| 列表路由 | `/transport/loading`（车次列表） |
| 到货卸车列表路由 | `/transport/loading/unload-list`（新增，菜单直达；一行 = 一个子车次，见 6.10） |
| 详情/配载路由 | `/transport/loading/detail`（隐藏路由，带 `?id=`，与运单详情页同风格） |
| 到站卸车作业路由 | `/transport/loading/unload`（隐藏路由，带 `?tripId=&destId=`，从到货卸车列表进入，见 6.10） |
| 费用类型配置 | 系统设置 → 系统配置页新增「财务配置」Tab → 费用类型配置（见 6.8） |
| 终端 | PC；到站/卸车后续可放宽到 APP，本期只做 PC |

### 6.2 列表页（单列表页）

**工具条**（严格按需求给定的 5 个按钮）：
`创建车次`、`修改车次`、`删除车次`、`取消发车`、`打印运输清单`
筛选条件以列表上方筛选区呈现（车次编号/名称、车次状态、始发网点、目的网点 + 查询/重置）；右侧：`刷新`、`列设置`、`导出`

- 「修改车次」「删除车次」「取消发车」「打印运输清单」为**选中行后可用**，未选中置灰；不满足状态时点击给出明确提示。
- **到货卸车列表为独立菜单页**（运输管理 → 到站卸车，见 6.10）：到站/取消到站/卸车/取消卸车**只在到货卸车列表提供**，车次列表不设这些入口，避免与卸车作业混淆；两者共用同一套到站确认与状态回退逻辑。
- 行操作列（本页只做配载与发车，与原型一致）：`配载/详情`、`发车`（10/20）、`关闭`（10/20）、`取消发车`（30 且无子车次到站）。
  - 不满足状态的行操作置灰并 hover 提示原因；`关闭`/`取消发车` 仅对应状态可用。
  - `取消发车` 走 6.6 口径（仅车辆在途且无任何子车次到站），弹窗必填原因。

**默认可见列（20 列）**：

| # | 列名 | 字段 | 宽度 | 固定 | 排序 | 表头筛选 | 合计 |
|---|---|---|---|---|---|---|---|
| — | 序号 | — | 60 | left | 否 | 无 | 否 |
| 1 | 车次编号 | trip_no | 170 | left | 是 | text 多模式（默认模糊） | 否 |
| 2 | 车次状态 | trip_status | 100 | — | 是 | select 多选 | 否 |
| 3 | 车次名称 | trip_name | 140 | — | 是 | text 单模式 | 否 |
| 4 | 始发网点 | origin_dept_name | 130 | — | 是 | select 单选 | 否 |
| 5 | 目的网点 | dest_dept_names | 200 | — | 是 | select 多选 | 否 |
| 6 | 计划发车时间 | plan_depart_time | 160 | — | 是 | daterange | 否 |
| 7 | 发车时间 | depart_time | 160 | — | 是 | daterange | 否 |
| 8 | 计划到站时间 | plan_arrive_time | 160 | — | 是 | daterange | 否 |
| 9 | 实际到站时间 | actual_arrive_time | 160 | — | 是 | daterange | 否 |
| 10 | 车牌号 | plate_no | 110 | — | 是 | text 多模式 | 否 |
| 11 | 司机 | driver_name | 90 | — | 是 | text 单模式 | 否 |
| 12 | 车辆型号 | vehicle_type | 110 | — | 是 | select 单选 | 否 |
| 13 | 总数量 | total_quantity | 80 | — | 是 | numberrange | 是 |
| 14 | 总重量(kg) | total_weight | 100 | — | 是 | numberrange | 是 |
| 15 | 总体积(m³) | total_volume | 100 | — | 是 | numberrange | 是 |
| 16 | 装载率 | load_rate | 130 | — | 是 | numberrange（按重量率） | 否 |
| 17 | 总运费 | total_freight | 110 | — | 是 | numberrange | 是 |
| 18 | 总有效运费 | total_valid_freight | 120 | — | 是 | numberrange | 是 |
| 19 | 毛利 | gross_profit | 110 | — | 是 | numberrange | 是 |
| 20 | 创建时间 | create_time | 170 | — | 是 | daterange | 否 |
| — | 操作 | — | 132 | right | 否 | 无 | 否 |

> 行操作列 v1.3 收窄为 **132px**（原 240px）：只保留 `配载/详情`、`发车`、`关闭`、`取消发车`，不再承载到站/卸车四类动作（已移至到货卸车列表，见 6.10）。

**隐藏列池**（可在列设置勾选）：司机电话、车辆载重、车辆容积、重量装载率、体积装载率、现付运费收入、到付运费收入、回付运费收入、月结运费收入、总返款、全车分摊方式、现付运输费、回付运输费、到站付运输费、现付油卡费、整车过磅费、整车保险费、分拨装车费、分拨卸车费、分拨其他费、到站卸车费、到站其他费、总成本费用、总提货费、总派送费、总外转费、创建人、更新时间、更新人、关闭原因、关闭时间、关闭人、备注。

**列表规范**：
- 严格复用 `SmartTable.vue` 与列配置（pageKey：`transport.loading`）。
- 表头 61px、筛选控件 28px（既有全局契约）。
- 列名不换行，列宽自适应 + 最小列宽，最大 320。
- 合计行按默认列中标注的「是」列汇总。
- 排序走服务端；日期区间结束日含当日 23:59:59；数值区间落 SQL。
- 1280/1100 无整页横向滚动，操作列固定右侧。

### 6.3 创建 / 修改车次（抽屉，720px）

分区：
1. **基础信息**：车次名称（必填）、计划发车时间（必填）、始发网点（默认当前机构，可调整为有权限网点）、目的网点（多选，至少 1 个，去重）。
2. **车辆信息**（可后补）：车牌号（从 `sys_vehicle` 选择，或 `+ 快速新增` 后回填）、车辆型号、核定载重、核定容积、司机姓名/电话（从 `sys_driver` 选择或快速新增）。
3. **备注**。

交互规则：
- 保存后生成车次号与子车次，进入「车次计划」。
- 目的网点：**新增不受限**（本抽屉、详情页均可；发车后新增的子车次直接置在途），**修改/删除**需该网点下无配载运单（D10）。
- 修改时若已有配载单元，始发网点不可改（避免库存归属错乱），仅允许改名称/计划发车/备注。
- 车辆载重/容积从车辆主数据带出时只读；`+ 快速新增` 弹窗内可填，保存后回填并只读。
- 抽屉底部：`取消`、`保存`；保存成功后**直接跳转该车次的装车配载页（Tab1）**，并提示「车次已创建，已进入装车配载」，不再弹二次询问。

### 6.4 车次配载页（两 Tab，`/transport/loading/detail?id=`）

> **v1.3 布局重设计（取代 v1.2-r3 单页双栏）**：配载页拆为**两个 Tab**——Tab1「装车配载」做实际配载，Tab2「发车」做车辆/费用录入、汇总与发车；两 Tab 可来回切换。子车次、操作日志从抽屉改为**顶部按钮弹窗/抽屉**；车辆信息、费用信息不再单独开抽屉，直接内联到 Tab2。原四个抽屉（车辆、费用、子车次、日志）相应取消或合并。到站/卸车仍为独立页（6.10）。

**整体结构**

| 层 | Tab1「装车配载」 | Tab2「发车」 |
|---|---|---|
| 顶部 | 车次编号/状态、车次名称（可编辑）、计划发车时间（可编辑）、`保存`/`子车次`/`操作日志`/`打印装车单` | 同一 Tab 条（装车配载 ⇄ 发车）；车次信息在 Tab1，Tab2 不再重复车次标识区 |
| 主区 | 始发→送达网点、全车分摊方式；左「待配载」/ 中间穿梭按钮 / 右「已配载」双栏 | 车辆信息 + 费用信息（成本项/收入项）+ 逐票分摊预览 + 汇总 |
| 底部 | 合计（票数/件数/重量/体积）+ `下一步：发车`、`打印装车单` | `返回装车配载`、`保存费用`、`发车` |

**Tab 规则**

- 默认进入 Tab1；Tab1「下一步」= 先保存顶部编辑，再切到 Tab2，**不再直接弹发车确认**（发车动作只在 Tab2）。
- Tab2 可随时「返回装车配载」回 Tab1；两 Tab 共享同一车次与同一份配载数据，任一 Tab 保存后刷新另一 Tab。
- Tab2 标签显示状态徽标：未发车显示「待发车」，已发车显示「已发车」；发车校验（车牌号、司机姓名必填）在点「发车」时执行。
- 车次 `status ≥ 30`（已发车）后：Tab1 双栏与顶部编辑全部只读、穿梭按钮禁用；Tab2 车辆/费用只读，仅保留查看与打印。

**页面分区（Tab1 自上而下三层 + 覆盖层）**

| 层 | 内容 |
|---|---|
| ① 顶部信息条 | 车次名称 + 计划发车时间 + 始发→送达网点 + `子车次`/`操作日志` + `保存`；下方提醒位 |
| ② 主区 | 左「待配载列表」/ 中间四个穿梭按钮 / 右「已配载列表」；两栏各自列设置与表头列筛选 |
| ③ 底部合计栏 | 左：合计票数/件数/重量/体积；右：`下一步`、`打印装车单` |
| ④ 覆盖层 | 子车次抽屉、操作日志抽屉、列设置面板；拆单弹窗见 ②-3（本轮原型已实现） |

#### ① Tab1 顶部信息条

第一行：
- `车次编号`（`trip_no`，只读）+ 状态 tag（如「装车配载」）
- `车次名称`（`trip_name`，**必填，创建时录入，本页可编辑**；≤64 字，保存时非空校验）
- `干线/短驳`标志（`trip_category`，本期恒为干线）
- `计划发车时间`（`plan_depart_time`，车次 10/20 可编辑，发车后只读）
- 右侧按钮区：`子车次`、`操作日志`（次要按钮，打开弹窗/抽屉）+ `保存`（主按钮）+ `打印装车单`

第二行：
- `始发网点`（只读，`origin_dept_name`，有配载后不可改）→ `送达网点`（可编辑：单网点直接下拉；多网点显示「广州分拨中心 等 3 个」，点击打开「子车次」弹窗维护）
- `提醒文案位`（红色/橙色提示，按场景显示）：
  - 车次名称/计划发车时间改过未保存：「修改后点击上方保存才能生效」
  - 送达网点改过未保存：「修改送达网点后点击保存才生效，需重新配载/校验」
  - 超载超容：「已超载 X kg / 超容 X m³（不影响继续配载）」
  - 存在已拆余额：「本车有 N 张子单，母单仍有 M 件在 X 网点待配」
  - 发车后：「已发车，配载与车辆费用只读；运单改单/作废请到运单管理操作」

`保存` 的作用：只提交**顶部编辑**（车次名称、计划发车时间、送达网点）；成功后刷新页面与 Tab2 汇总。**穿梭与拆单产生的配载变更不依赖此按钮**（见 ⑦）。**全车分摊方式唯一修改入口在 Tab2 费用信息区**（与 `biz_trip.alloc_method` 同源）；Tab1 顶部只读展示当前方式，不放下拉、不出现「在此修改」入口。

#### ② Tab1 主区：左右双栏穿梭 + 双栏列设置/表头筛选

| 区域 | 内容 |
|---|---|
| 左栏「待配载列表」 | 待配载池（口径见 8.4）；**池内均为已通过资格校验的可配单元**（未拆整票 / 母单余额 / 子单），默认按库存落入时间升序（FIFO） |
| 中列 | 竖排四个按钮：`全部右移 ≫`、`选中右移 >`、`全选左移 ≪`、`选中左移 <`；按钮列宽 56px，两栏各占剩余宽度约 48% |
| 右栏「已配载列表」 | 本车次已配载单元（子单/整票）；默认按子车次（目的网点）分组，组头显示子车次号 + 目的网点 + 组内小计；**只读，无操作列** |

**两栏各自的栏头工具区**（每栏右上角，互不影响）：

- `拆单装车`（仅左栏）：勾选 1 行且该行为可拆单元时可用；点击打开拆单弹窗（见本节 ②-3）。
- `列设置`（两栏各一个）：打开 `ColumnSetting`，维护本栏列的显隐、顺序、排序、筛选形态、筛选模式与默认查询值。
- `筛选`：表头列筛选（见本节 ②-2），非独立筛选条；清空条件只影响本栏。
- `刷新`：只刷新本栏。

按钮语义与禁用：
- 右移 = 配载（加入本车次）；左移 = 移除配载（回池）；**穿梭只落库，不做资格校验**（池已过滤，见 ⑥）。
- 左栏无数据 → 右移禁用；右栏无数据 → 左移禁用；车次状态 ∉ {10, 20} → 四个按钮与拆单装车全部禁用（只读查看）。
- `全部右移` 作用于左栏**当前筛选结果的全部行**（跨页），执行前二次确认「将把当前筛选的 N 个可配单元全部配载，是否继续？」；`全选左移` 同理，二次确认「将把本车次已配载的 N 个单元全部移除」。
- **已拆母单余额行可直接右移**（v1.3-r2 修订）：按剩余件数/重量/体积生成子单落库，与本车同母单子单自动合并；合并后正好是整单则整体更新为母单、作废并取消子单。未拆整票与子单可直接右移。

**②-1 双栏列设置与表头列筛选（v1.3 新增，两栏都要）**

| 项 | 约定 |
|---|---|
| 组件与能力 | 两栏均复用 `SmartTable.vue` + `ColumnSetting.vue`，表头筛选、列显隐/顺序/宽度/排序、列设置面板全部复用现有能力 |
| pageKey | 左栏 `transport.loading.pool`，右栏 `transport.loading.loaded`；**两个独立 pageKey，配置互不影响** |
| 列配置内容 | 列显隐与顺序、列宽、排序、筛选形态（text/select/date/numberrange）、筛选模式（text 单/多模式 `single` / `multi`、select 单选/多选）、默认查询值；结构与现有列配置 v2 一致（见 `2026-11-21/column-filter-modes/outputs/column-filter-modes-design-v1.0.md`） |
| 持久化 | 持久化到后端 `sys_column_config`，按「当前用户 + pageKey」隔离；接口 `GET/PUT/DELETE /api/v1/system/column-configs/{pageKey}`；**禁止用 localStorage 代替**，保证换设备一致 |
| 默认查询值 | 文本多模式默认值 `{ mode, value }`；下拉多选默认值为数组；进入页面先读列配置 → `applyDefaults()` 灌入查询表单 → 再首次查列表；日期/区间列不设默认值（沿用既有约定） |
| 单/多模式 | 文本列可配 `single`/`multi`；`multi` 的实际匹配方式为表头运行时选择（`like/exact/ne/batch` 等），通过 `filterModes` JSON 传给后端，如 `{"waybillNo":"batch","customerName":"exact"}` |
| 查询参数 | 两栏查询接口均带 `filterModes`（业务列模式 JSON）与 `columns`（当前可见列 key，服务端据此裁剪返回/合计/导出）；日期区间继续用 `start/end`、数值区间用 `min/max`，不放进 `filterModes` |
| 独立性 | 两栏各自保存筛选值、模式、排序、`page/size`、勾选状态与列配置；任一栏操作成功后仅刷新本栏与底部合计，另一栏筛选与勾选不丢 |
| 安全 | `filterModes` 只作用于业务列条件，不得绕过数据权限；服务端仍按 8.4/9 强制条件过滤 |
| 原型要求 | 两栏各放「列设置」入口与表头筛选控件；切页/刷新后各自配置保留；用假数据演示 pool 与 loaded 配置互不影响（如左栏按客户筛选、右栏按子车次筛选同时生效） |

**②-2 表头筛选项**

| 栏 | 表头可筛选列 |
|---|---|
| 左（待配载） | 运单号、运单类型、状态、库存落入时间区间、下单时间区间、客户名称、到达网点（= 当前路由下一节点）、收货人、目的站、件数/重量/体积区间 |
| 右（已配载） | 运单号（子单号/整票号）、母单号、状态、客户名称、到达网点（= 子车次目的网点）、收货人、目的站、子车次、件数/重量/体积区间 |

双栏独立分页：各自 `page/size`（默认 20，可选 20/50/100），各自保留筛选与勾选状态，互不影响；任一栏操作成功后仅刷新受影响栏与底部合计。

列定义（表头 61px、筛选控件 28px、列名不换行、列宽自适应且最大 320、固定列不参与横向滚动，按 `SmartTable` 规范；两栏都可列设置，以下为默认可见列）：

左栏「待配载列表」：

| # | 列名 | 字段 | 宽度 | 固定 | 说明 |
|---|---|---|---|---|---|
| — | 勾选 | — | 44 | left | 多选；拆单只允许单选 |
| — | 序号 | — | 56 | left | — |
| 1 | 运单号 | waybillNo | 160 | left | 整票=母单号；子单=子单号；点击进入运单详情页 `/operation/waybill/:id`，本页不再自建只读详情弹窗 |
| 2 | 运单类型 | unitType | 96 | — | 徽标：整票 / 母单余额 / 子单 |
| 3 | 母单号 | parentNo | 150 | — | 仅子单与已拆母单余额显示，整票为「—」 |
| 4 | 客户名称 | customerName | 150 | — | — |
| 5 | 目的站 | destStationName | 120 | — | 运单目的地 |
| 6 | 到达网点 | nextDeptName | 140 | — | = 当前路由下一节点，须 ∈ 车次目的网点集合 |
| 7 | 收货人 | receiverName | 100 | — | — |
| 8 | 剩余件数 | remainingQuantity | 90 | — | 右对齐；整票=原件数，余额/子单=剩余量 |
| 9 | 剩余重量(kg) | remainingWeight | 100 | — | 右对齐；子单按件数折算后的本单量 |
| 10 | 剩余体积(m³) | remainingVolume | 100 | — | 右对齐 |
| 11 | 运费合计 | totalFee | 100 | — | 可配单元对应金额（子单=拆分后），右对齐 |
| 12 | 库存落入时间 | inventoryTime | 160 | — | 默认排序字段（升序） |
| 13 | 状态 | status | 90 | — | 单元状态 tag（余额行显示母单余额状态） |

> 池列默认展示「剩余件数/重量/体积」；未拆整票时剩余=原始量。原始量可在列设置里追加「原件数/原重量/原体积」隐藏列，hover 剩余量时浮层显示「原件数 − 已配 n 件」。

右栏「已配载列表」（**只读，无操作列**）：

| # | 列名 | 字段 | 宽度 | 固定 | 说明 |
|---|---|---|---|---|---|
| — | 勾选 | — | 44 | left | 多选 |
| — | 序号 | — | 56 | left | — |
| 1 | 运单号 | waybillNo | 160 | left | 整票=母单号；拆单=**子单号**（如 `YD20261123000126-01`） |
| 2 | 母单号 | parentNo | 150 | — | 仅子单显示；整票为「—」 |
| 3 | 运单类型 | unitType | 96 | — | 徽标：整票 / 子单 |
| 4 | 子车次/目的网点 | tripDestNo + destDeptName | 170 | — | 分组列，组内可折叠 |
| 5 | 客户名称 | customerName | 150 | — | — |
| 6 | 收货人 | receiverName | 100 | — | — |
| 7 | 本单元件数 | quantity | 90 | — | 子单=本车份额件数 |
| 8 | 本单元重量(kg) | weight | 100 | — | 子单=按件数折算 |
| 9 | 本单元体积(m³) | volume | 100 | — | 子单=按件数折算 |
| 10 | 运费合计 | totalFee | 100 | — | 子单=拆分后金额 |
| 11 | 库存落入时间 | inventoryTime | 160 | — | 配载时的快照时间 |
| 12 | 状态 | status | 90 | — | 单元状态 tag |

> 右栏**取消操作列**：不提供改单/作废/移除入口。移除只能「左移」穿梭；改单/作废改由运单管理/运单详情操作（见 8.5）。子单行点击运单号打开子单详情，hover 母单号可浮层显示母单摘要。

双栏均复用 `SmartTable.vue`；列配置 pageKey 分别为 `transport.loading.pool`、`transport.loading.loaded`，列设置、筛选模式与默认查询值各自记忆、互不影响。

**②-3 拆单弹窗（左栏「拆单装车」触发）**

| 项 | 约定 |
|---|---|
| 触发 | 左栏勾选 1 行 → `拆单装车`（原型另在每行提供「拆单」快捷入口，效果相同）；可拆条件：`is_child=0`、`load_status=0`、余额件数 > 0、车次 10/20；不满足置灰并给原因（件数=1 时禁用） |
| 弹窗头部 | 母单号、客户名称、目的站、到达网点、原件数/重量/体积、剩余件数/重量/体积、母单金额（运费合计/送货费/返款/有效运费） |
| 输入 | **只录「本车装载件数」**（正整数，`1 ≤ q ≤ 剩余件数`）；提供「全部余额装车」快捷按钮，一键填入剩余件数 |
| 自动预览（只读） | 子单件数 `q`、按件数折算的重量/体积、拆分后运费（四项分项）、送货费、返款、有效运费；母单剩余件数/重量/体积与剩余金额；预览算法与 4.5 一致，服务端 `split-preview` 返回为准 |
| 子单号 | 自动生成「母单号-两位序号」，取当前有效子单中最小未占用序号，作废后回收复用；弹窗内提示按最新可用号生成（实际序号由服务端分配），确认后回填并在右栏标注「子单」 |
| 操作 | `取消`、`确认拆单并装车`；确认调 8a 接口，同事务生成子单 + 落配载；成功后关闭弹窗，左栏该母单余额减少或消失，右栏新增子单行，底部合计刷新 |
| 失败 | 保留弹窗与输入，显示真实原因（余额不足/并发被抢/车次状态不允许/子单序号已达 99） |
| 禁止 | 本页不提供「合并子单」「撤销拆单」入口；撤销拆单走运单模块，且要求子单未装车（见 5.7、8.5） |

#### ③ Tab1 底部合计栏

- 左：`合计 N 个单元 / X 件 / Y kg / Z m³`（取右栏**全量**汇总，不受分页影响；按配载单元计，子单只计本单量，不重复计母单）；右栏有勾选时追加 `已选 n 个单元`；超载/超容时在合计后追加橙色提示。
- 右：`下一步`、`打印装车单`。
  - `下一步`：车次 10/20 可用；点击先保存顶部编辑，再切到 **Tab2「发车」**；30 及以后置灰。
  - `打印装车单`：调 `GET /transport/trip/{id}/print`，按子车次分组输出本车次全部配载单元；拆单行同时打印**子单号 + 母单号 + 本单元件数/重量/体积**，整票行只打印母单号；原「打印运输清单」为同一接口，入口文案统一为「打印装车单」。

#### ④ 覆盖层（v1.3 精简为两个入口 + 列设置）

| 入口 | 形态 | 内容 | 说明 |
|---|---|---|---|
| 子车次 | 弹窗 720px | 每个目的网点一行：子车次号、目的网点、票数/件数/重量/体积、状态、计划/实际到站、卸车时间、操作（仅 `到站卸车页`，到站/取消到站/卸车/取消卸车统一在到货卸车列表与作业页操作）；工具条 `新增目的网点`（任意状态可用，发车后新子车次置在途）、`修改`、`删除`（有配载单元时置灰并提示先移除） | 替代原 Tab5；顶部按钮触发 |
| 操作日志 | 抽屉 560px | 时间、动作、操作人、原因、关键快照摘要 | 替代原 Tab6；顶部按钮触发 |
| 列设置 | 抽屉/面板 | 两栏共用 `ColumnSetting`，按 pageKey 打开；列显隐、顺序、筛选模式、默认查询值 | 每栏右上角 `列设置` 触发 |

- **车辆信息、费用信息不再单独开抽屉**：已内联到 Tab2（见 ⑤），原 Tab3/Tab4 抽屉取消。
- 原 Tab7「轨迹」占位取消，不设入口；GPS 仍按第 10 节不做。
- 弹窗/抽屉打开期间主区穿梭与拆单按钮禁用，避免同一车次并发改配载与分摊。
- `关闭车次`、`取消发车`不放在本页（底部只留两个主操作），保留在列表页工具条/行操作，避免误触。

#### ⑤ Tab2「发车」页

**顶部**：与 Tab1 共用同一 Tab 条（装车配载 ⇄ 发车）；车次编号/名称/计划发车/网点仅在 Tab1 展示，Tab2 不重复车次标识区。

**主区（三个纵向区块，同页内联，不再开抽屉）**

| 区块 | 内容 | 可编辑性 |
|---|---|---|
| A 车辆信息 | 车牌（选择/快速新增）、车型、核定载重(kg)、核定容积(m³)、司机姓名、司机电话 | 车次 10/20 可编辑；发车后只读 |
| B 费用信息 | **费用矩阵录入**（列=费用项，行=费用归属：首行为**始发站（标注「发站」）**，录全部车次成本项；其后每个目的网点一行，标注「到站」，只录「到站付运输费」，其余格子不可录入）；「到站付运输费」列紧跟「回付运输费」；收入四项 + **收入合计**/成本合计/实时毛利以小字彩条列在表下方（收入合计主题色、成本橙、毛利绿/红）；**逐单元分摊预览收进右上角「分摊预览」抽屉**；全车统一分摊方式仍为唯一修改入口（`alloc_method`），`Σ基数=0` 阻断保存（D14） | 车次 10/20 可编辑；发车后只读 |
| C 汇总 | 票数/件数/重量/体积、重量与体积装载率、超限提示（超载/超容只提醒） | 只读，实时刷新 |

**底部**：`返回装车配载`（回 Tab1）、`保存费用`（只落费用并重算分摊）、`发车`（主按钮）。

- `发车` 可点击条件：至少 1 个配载单元、车牌/司机完整、费用已保存、D14 满足；不满足时置灰并给出缺口清单。
- 点击 `发车` 弹确认框（预计到达时间必填，默认按线路时效估算），确认后执行 6.5；成功后停留 Tab2 显示已发车只读态，Tab1 同时转为只读。
- 费用保存与发车解耦：`保存费用` 只落费用并重算分摊；车辆信息在 `发车` 时一并落库；`发车` 才推进状态。
- 拆单子单与整票单元同在汇总与分摊范围内，按各自本单元量计算（不重复计母单）。

#### ⑥ 校验位置约定：池查询一次做足，穿梭零资格校验

> **约定（实现时必须遵守）**：左栏待配载池里的每一个单元，都必须是「已通过全部资格校验、确实可配」的配载单元。资格校验只在**池查询阶段做一次**；**穿梭（右移/左移）只做落库，不做任何资格校验，也不返回「不符合条件」清单**。后续实现不得把资格判断加回穿梭接口。

**配载单元定义（v1.3）**：池中一行 = 一个可配单元，共三类：
1. **未拆整票**：`is_child=0`、无生效子单、`load_status=0`；右移即整票配载。
2. **母单余额**：`is_child=0`、剩余件数 > 0（已拆）；**可直接右移**：按剩余件数/重量/体积生成子单落库，与本车同母单子单自动合并；合并后正好是整单时整体回母单、作废并取消子单。
3. **子单**：`is_child=1`、`load_status=0`（上一段卸车后回池）；右移即配载该子单，不可再拆。

**池查询（接口 6）的过滤条件，一次做足：**

| # | 条件 | 说明 |
|---|---|---|
| 1 | `current_hub_id = 车次始发网点`（`trip.origin_dept_id`） | 库存必须在车次始发网点 |
| 2 | `load_status = 0` | 单元未被任何车次占用；母单余额本身永远为 0 |
| 3 | `status IN (20, 60)` | 单元状态为「已揽收 / 分拨卸车」；已拆母单余额保持 20/60（见 5.7） |
| 4 | 剩余量 > 0：`remaining_qty = quantity − Σ有效子单件数 > 0`；重量/体积同口径 | 已部分配载的母单仍可回池；剩余为 0 的母单不出现 |
| 5 | 路由下一节点（`seq = current_node_seq + 1` 的 `to_dept_id`）∈ 本车次目的网点集合 | 多目的网点任一命中即可 |
| 6 | 未被其它车次有效配载（`biz_trip_waybill` 活跃记录 + 车次未关闭） | 同一母单的不同子单互不影响 |
| 7 | 排除末段外转（无 `to_dept_id`） | 外转走外转流程，不进池 |
| 8 | 数据权限：仅车次始发网点（D5 受控放开） | 不跨机构取库存 |

池接口只返回同时满足以上条件的单元，并返回 `unitType`、`parentNo`、`remainingQuantity/Weight/Volume`；左栏拿到的就是可配集合，前端不再做资格筛选。

**穿梭/拆单阶段的处理：**

| 场景 | 处理 |
|---|---|
| 正常右移 / 左移（整票、子单） | 只落库 + 返回最新汇总，**不校验资格** |
| 母单余额点右移 | 按剩余量生成子单并直接配载，与本车同母单子单合并；合并后为整单则回母单（作废子单） |
| 拆单装车（8a） | 锁母单行 → 重算剩余 → 校验 `1 ≤ q ≤ 剩余件数` → 生成子单并落配载；同一母单并发拆单由母单行锁串行化，只有一个请求拿到同一份额 |
| 并发被抢（池查询后被别的车次配走） | 条件更新影响行数为 0 → 该单元失败，原因「已被其它车次配载」 |
| 状态在这几秒内被改（作废/改单导致下一节点变化等） | 条件更新失败 → 返回真实失败原因，前端高亮该单元并提示刷新左栏 |
| 车次状态已不允许操作（非 10/20） | 失败原因「车次当前状态不可配载」 |
| 超载 / 超容 | **只提醒不阻断**：右移/拆单前按右栏合计预计算，弹确认「本次加入后超载 X kg / 超容 X m³，是否继续？」，继续即落库并写超限日志 |
| 移除限制 | 车次 30 起不允许移除（左移按钮禁用）；改单/作废不在本页，按 8.5 在运单模块处理 |

- 失败只针对真实异常（并发、状态瞬变、车次状态不允许、余额不足）；「下一节点不匹配 / 不在始发网点库存 / 末段外转 / 已被配载 / 剩余为 0」在池阶段已被过滤，正常情况下穿梭不会命中。

#### ⑦ 接口调用顺序建议（即时 vs 暂存）

**建议：穿梭与拆单即时调用接口，不采用「前端暂存 + 点保存统一提交」。**

右移调用顺序：
1. 前端按钮置 loading，收集选中 `waybillIds[]`（整票母单 id 或子单 id）；
2. 调 `POST /transport/trip/{id}/waybills`（接口 8，批量）；
3. 服务端单事务批量落库（条件更新 `load_status=0` + 状态符合 + 车次状态 10/20）；**不做资格校验**，仅在并发被抢、状态瞬变、车次状态不允许时返回对应单元的失败原因；已拆母单余额按剩余量生成子单并合并，合并为整单时整体回母单；
4. 成功后返回：右栏新增行、左栏移除/余额更新行、车次汇总（单元数/件数/重量/体积/装载率/四类收入/成本/毛利）、超限提示；
5. 前端用返回值就地更新双栏与底部合计，失败保持原状并高亮失败单元。

左移 / 全选左移：同上，调批量移除 `DELETE /transport/trip/{id}/waybills`（接口 9，body 传 `waybillIds[]`）。

拆单装车调用顺序：
1. 左栏勾选母单 → 打开拆单弹窗 → 输入件数（或用「全部余额装车」）；
2. 前端可先调 `GET /transport/trip/{id}/waybills/{waybillId}/split-preview`（接口 8b）拿服务端预览；
3. 确认后调 `POST /transport/trip/{id}/waybills/split-load`（接口 8a）：同事务锁母单 → 校验余额 → 生成子单 → 落配载 → 刷新分摊/汇总；
4. 返回子单号、拆分金额、母单剩余量、车次汇总；前端关闭弹窗，左栏余额减少/消失，右栏新增子单行；
5. 失败保留弹窗与输入，按真实原因提示（余额不足/并发/车次状态/子单序号 99 上限）。

理由：
- 与 D6「改单/作废同事务刷新车次汇总、分摊、装载率、毛利」口径一致；即时落库保证右栏列表、底部合计、费用分摊、毛利任何时刻都与库一致。
- 暂存方案的代价：切页/关窗即丢、多人同时配载易覆盖、左栏可能包含已被别车抢走的单元且到保存才暴露、发车前置校验要额外维护一套内存态。
- 即时方案每次网络往返的代价，用**批量提交 + 单事务**摊薄，无需新增暂存草稿表与冲突合并逻辑。

因此 Tab1 顶部 `保存` 只承担「车次名称/计划发车时间/送达网点」落库，与穿梭、拆单解耦。

#### ⑧ 待确认点（需家伟拍板）

1. 全车分摊方式已按 D2 移到 Tab2 费用区，本页不再保留「模式选择」；如仍需在 Tab1 顶部常驻展示，请告知，我加只读回显。
2. 已拆母单余额支持直接右移（v1.3-r2 修订）：按剩余件数/重量/体积生成子单落库，与已配载列表同母单子单直接合并；合并后正好是整单时整体更新为母单、作废并取消子单。原「余额不可右移、只能拆单装车」的限制取消。

### 6.5 发车

- 入口：**Tab2「发车」按钮**（不再从 Tab1「下一步」直接触发；Tab1 下一步只负责保存顶部编辑并切到 Tab2）。
- 确认弹窗：预计到达时间（必填，默认按线路时效估算）、提示「发车后配载、车辆信息与成本费用将锁定；运单改单/作废请到运单管理操作并自动刷新车次数据」。
- 校验：至少 1 个配载单元（整票或子单）、车辆/司机信息完整、费用已保存、D14 分摊基数合计 > 0。
- 确认后：车次 30、全部配载单元（整票与子单）80、子车次 20、写发车时间/操作人/日志；页面停留 Tab2 只读态，Tab1 同步只读。
- 取消发车仍在列表页工具条/行操作（5.4 矩阵），不在配载页提供；取消后 80→70、`load_status=1`、子车次回 10。

### 6.6 取消发车

- 仅「车辆在途」且无任何子车次到站可用。
- 弹窗必填原因；确认后回滚车次 20、运单 70、`load_status=1`、清空发车时间、子车次回 10，写日志。
- 二次确认文案写明「取消发车将把全部运单退回装车配载状态」。

### 6.7 到站 / 取消到站 / 卸车 / 取消卸车

**到站**
- 入口：到货卸车列表行操作 `到站`、配载页顶部「子车次」弹窗行内 `到站`、到站卸车页工具条 `到站`；车次列表不提供。
- 弹窗（430px，对齐 luohuo 到车确认形态）：`车次号`（子车次号）、`车次名称`、`到站时间`（默认当前，可改）、提示「确认后写入子车次及本车运单的实际到站时间，运单由已发车更新为到站」；页脚 `取消` / `确认到站`。
- 确认后：写 `arrive_time/arrive_by`，子车次 `30 已到站`，按 5.3 推进运单（大车直送不动）；写日志 7。多目的网点先选子车次。

**取消到站**
- 入口：到货卸车列表行操作 `取消到站`、配载页顶部「子车次」弹窗行内 `取消到站`；车次列表不提供。
- 前置：子车次 `30 已到站` 且本子车次全部单元均未卸车（`biz_trip_waybill.unload_time IS NULL`；末端 `load_status≠4`、分拨回池 `load_status≠0`）；车次未关闭。
- 弹窗（430px）：`子车次号`/`目的网点`/`取消原因`（必填）；提示「取消到站将回退运单到分拨发车，主车次按剩余到站情况回退；本子车次尚未卸车，操作可逆」。
- 确认后：按 5.2/5.3 回退（子车次 30→20、运单 90/50→80、`load_status 3→2`，大车直送不动；主车次 30/40/50 按剩余重算），两项到站费用（卸车费/其他费）标记失效、金额保留；写日志 16。若已卸车则拒绝并提示先「取消卸车」。

**卸车**
- 入口：到货卸车列表行操作 `查看`（只读）/`卸车` 进入作业页，切「已卸车」Tab 勾选运单后点 `取消卸车`；车次列表、子车次弹窗均不提供。
- **未到站时点「卸车」不拦截**：先弹 430px 到站确认框（同「到站」），确认后写入到站并跳转卸车页，默认停在「未卸车」Tab；已到站则直接跳转。主车次 60（全部卸完）从列表进入时默认「已卸车」Tab，便于取消卸车。
- 卸车页按勾选逐票卸车（或可卸 0 票的子车次直接完成，见 6.10）；确认后写 `unload_time/unload_by`，按 5.3 推进运单；全部子车次完成后主车次 60；写日志 8。
- 末端网点卸车后按送货方式落到 100/110/120，便于提货/派送模块接续。

**取消卸车**
- 入口：到站卸车作业页「已卸车」Tab 勾选已卸运单后点 `取消卸车`（运单级，唯一入口）；车次列表、到货卸车列表、子车次弹窗均不提供。
- 前置：该单元**已卸车**（末端 `load_status=4`；分拨回池 `load_status=0` 且 `biz_trip_waybill.unload_time` 非空）、子车次 `40 完成卸车`；**未被下一车次有效配载**、**未被签收/核销**（`status∉(130,900)`）；车次未关闭。
- 弹窗（430px，对齐 luohuo 取消卸车）：`车次号`/`车次名称` → `本次取消卸车运单`（勾选清单，只读）→ `取消原因`（必填）→ 提示「取消卸车将回退运单到到站状态，并把库存退回本站；若该票已被下一车次配载或已签收，将不允许取消」；页脚 `取消` / `确认取消卸车`。
- 确认后：按 5.2/5.3 回退（运单 100/110/120→90 或 60→50、`load_status→3`（末端 4→3、分拨回池 0→3）；子车次 40→30，仍有已卸车单元则保持 40；主车次 60→50）；库存退回上一站（8.4）；两项到站费用（卸车费/其他费）标记失效、金额保留（5.6）；写日志 17。

### 6.8 费用类型配置页（系统设置 → 财务配置，统一列表）

- 入口：`/system/config` 页面 Tabs：`基础配置`（占位）/`财务配置`；财务配置内为「费用类型配置」列表。
- **一张列表混排四类**（收入 / 成本 / 代收 / 代付），不再按收入/成本分区块。
- **列顺序（固定默认）**：排序、费用编码、费用名称、费用类型、取数来源、是否参与毛利、启用、备注、操作。
- **收入四项只读行**：现付运费 `INC01`、到付运费 `INC02`、回付运费 `INC03`、月结运费 `INC04` 与成本/代收/代付**混排在同一张表**（按 sort_order），费用类型=收入、取数来源=对应运单字段、参与毛利=是、启用=是，操作列显示「—」，不可编辑/停用/删除。
- **其余 16 项可配置项（14 项成本 + 2 项代收/代付示例）**：可编辑（名称/编码只读，可改启用、参与毛利、排序、备注）、可启停；不做删除（保留历史）。
- **头部筛选**：按「费用类型」筛选（单选/多选，`feeCategory`），可叠加费用编码/名称关键字、启用状态；头部右侧显示**总项数**（默认 20），并显示分类型计数（收入 4 / 成本 14 / 代收 1 / 代付 1）。
- **列设置**：沿用 `ColumnSetting`，pageKey=`transport.feeType`，与列表筛选模式/默认值一并持久化后端。
- **新增费用类型**：
  - 弹窗字段：费用类型（单选四类，必选）、费用名称（从「未配置的费用项目录」选择或输入）、启用、参与毛利、排序、备注；
  - 类型=代收/代付：参与毛利强制为「否」且置灰，方向为空，取数来源为空；
  - 类型=收入：方向固定「收入」；类型=成本：方向固定「成本」；方向不单独开放编辑；
  - 类型选定后**创建后不可修改**（避免方向/毛利与已产生数据冲突）；
  - 编码：成本/代收/代付自动生成 `FEE`+4 位流水；收入新建项本期生成 `INC`+2 位流水（内置四项占用 01–04，新建从 05 起），但新建收入类型不参与车次费用录入与分摊。
- **编辑**：费用名称、费用编码只读；可改启用、参与毛利（代收/代付不可改，恒为否）、排序、备注。
- **取数来源列**：收入内置项显示「运单.现付运费」等；成本项显示「车次.现付运输费」等；代收/代付与新建收入类型显示「—」。
- 权限：总部财务/管理员可见（按钮权限控制）。

### 6.9 全局重泡比配置 + 路由管理重泡比列（D3）

- **系统设置 → 系统配置 → 基础配置** 新增一项：`全局默认重泡比(kg/m³)`，读写 `sys_config.fee.volume_weight_ratio`，默认 333，保存校验 >0。
- **路由管理**列表/编辑新增一列 `重泡比(kg/m³)`，对应 `sys_route.volume_weight_ratio`：
  - 新建线路时默认带出全局值，可改；
  - 允许清空或填 0，表示继承全局；
  - 列表显示：为空/0 时展示「继承全局(333)」灰字，实际值随全局变化。
- 分摊时逐票取所属线路系数（见 4.3），不在车次上再维护单一系数。

### 6.10 到站卸车页（末端网点，参照 luohuo）

> 设计原则：**不凭印象美化，按 luohuo 源码的字段顺序、分区、按钮位置与交互 1:1 对齐**；差异只在两处——① 新增**到站费用内联录入区**（到站卸车费 + 到站其他费，仅干线 + 末端网点）；② 补全「取消到站 / 取消卸车」（luohuo 仅有取消卸车，我方补齐前置校验、库存回退与费用失效；见本节第 10/13 点）。
>
> **v1.3 拆单口径**：到站/卸车清单以**配载单元**为行；拆单行显示子单号并在其下标注母单号，状态推进、卸车费分摊、打印均按子单维度；整票行仍按母单。清单合计按单元求和，不重复计母单。

**参照源码（luohuo 项目，`/Users/apple/Desktop/君达文件/luohuo-system`）**

| 参照点 | 源码文件 | 行号 |
|---|---|---|
| 到货卸车列表页：操作列（状态 1/2/5 显示「卸车」，否则「详情」） | `frontend/src/views/biz/unload/index.vue` | L92–112 |
| 确认到车弹窗（宽 430px；车次号/车次名称/到车时间 + 提示；页脚「取消 / 确认到车并进入卸车」） | 同上 | L116–152 |
| 卸车作业页面包屑（返回 + 标题） | `frontend/src/views/biz/unload/unload.vue` | L5–9 |
| 车次信息条（车次号+状态 tag、车次名称、到车时间、落货客户、重量、体积、件数、规则行；单行 kv 横排） | 同上 | L11–29 |
| 三个 Tab（全部/未卸车/已卸车，带数量角标；默认「未卸车」） | 同上 | L31–42、L218 |
| 操作条（左：卸车主按钮带选中数 + 「已选 N 条」；右：重置筛选/导出/列设置/分页） | 同上 | L44–70 |
| 运单表格（勾选列 50px、序号 70px、其余动态列、行内运单号链接/状态 tag/金额格式化） | 同上 | L85–154（勾选 L96–102、序号 L103） |
| 卸车确认（选中后二次确认，列出运单号，warning 型） | 同上 | L328–341 |
| 取消卸车（已卸车 Tab 专用） | 同上 | L348–360 |
| 运单列定义与顺序（运单号/运单状态/路由类型/送货方式/目的地/货物名称/数量/重量/体积/运费/送货费/代收运费/回单/等通知/收货人/收货电话/开单时间/备注；隐藏列提货费/其他费/代收货款） | `frontend/src/views/biz/unload/unloadColumns.js` | L76–98 |
| 卸车后运单状态映射（直送+送货上门/自提、外转三类目标状态） | `backend/app/crud/unload.py` | L43–57 |
| 执行卸车事务（仅 2/3 可卸；写 unload_time；全部卸完→车次 4，未卸完→5） | 同上 | L491–544 |
| 卸车费录入弹窗（计价单位/单价/实时预览/合计、页脚「取消 / 确定计价」）——**仅作金额输入与实时预览的形态参照；v1.3 已改为页面内联，不再弹窗** | `design/finance-design.md` | §8.1 L548–570 |

**我方页面结构与交互（落地口径）**

**页面一：到货卸车列表页（v1.3 新增，菜单「运输管理 → 到站卸车」直达，路由 `/transport/loading/unload-list`）**

- 一行 = 一个子车次（目的网点），数据来源 `biz_trip_dest` 联 `biz_trip`；运单数/件数/重量只统计非大车直送单元。
- 默认列：车次号、子车次号、车次名称、目的网点、子车次状态、到车时间、卸车时间、运单数、件数、重量(kg)、操作。
- 行操作按「车次状态 + 子车次状态」**写死**，不满足的行操作**隐藏**（不置灰）：

  | 车次状态 | 子车次状态 | 行操作 | 说明 |
  |---|---|---|---|
  | 10 计划 / 20 装车配载 | 任意 | `查看` | 只读作业页；**末端网点不能到站、不能卸车**（未发车不会产生到站时间） |
  | 30 车辆在途 | 10 未到站 | `查看`、`到站`、`卸车` | 点 `卸车` 先弹 430px 到站确认，确认后写到站并进入作业页 |
  | 30/40/50 | 30 已到站未卸完 | `查看`、`卸车`、`取消到站` | 部分卸车也可取消到站，弹窗提示「已卸 N 票将一并回退」 |
  | 40/50/60 | 40 已卸车 | `查看` | **取消卸车是运单级动作**，列表不提供；只能在作业页「已卸车」Tab 勾选运单后执行 |
  | 90 已关闭 | 任意 | `查看` | 只读，不提供任何状态动作 |

> 行操作列 v1.3 收窄为 **150px**（原 168px）：去掉「取消卸车」后仅保留 `查看` / `到站` / `卸车` / `取消到站`。

- **不设独立「作业页」按钮**：进入作业页只能由 `查看`（只读态）/`卸车`（可操作态）带入；`到站` 是列表行内的状态动作，确认后留在列表。
- **只读态（`查看`）**：信息条与运单表格可见、Tabs 可切换；费用区整区禁用、无 `保存费用`；无 `卸车` / `取消卸车` 按钮、无勾选列、无「已选 N 条」。只读态与可操作态使用同一路由与同一份数据，仅按 `readonly` 标志裁剪操作。
- **取消卸车仅运单级**：车次/子车次列表（含 P1 车次列表、P3 到货卸车列表、子车次弹窗）一律不出现「取消卸车」；只能在到站卸车作业页「已卸车」Tab 勾选已卸运单后点「取消卸车」。到站/取消到站也只保留在 P3 到货卸车列表行操作，作业页不再提供。
- 筛选：车次号/子车次号/车次名称关键字、子车次状态、目的网点；分页与排序服务端。
- 权限与作业页一致：按 `dest_dept_id = 当前机构` 过滤，非本站子车次不可见/不可操作。

**页面二：到站卸车作业页（`/transport/loading/unload?tripId=&destId=`）**

1. **入口**：到货卸车列表行操作 `查看`（只读态）/`卸车`；`到站` 为列表行状态动作，确认后不跳转。车次配载页顶部「子车次」弹窗仅提供「到站卸车页」入口。车次列表（P1）不提供到站/卸车入口；列表不设独立「作业页」按钮。未到站时从列表点 `卸车`，先弹到站确认，确认后进入本页并默认「未卸车」Tab。
2. **面包屑**：`返回到货卸车`（回到到货卸车列表页）+ 标题「到站卸车」——与 luohuo L5–9 一致。
3. **车次信息条**（单行 kv，顺序对齐 luohuo L11–29，按我方字段替换「落货客户」为「目的网点」）：
   `车次号`（带子车次状态 tag）→ `车次名称` → `到车时间` → `目的网点` → `重量` → `体积` → `件数` → `到站卸车费`（仅干线+末端网点显示，其余隐藏）→ `到站其他费`（同上）→ `到站付运输费`（按本目的网点取数、只读，空值 0.00；V36 起在发车 Tab 按网点录入）→ 规则行。
   `重量`/`体积`/`件数` 均为**非大车直送**运单的合计（大车直送不占用末端卸车作业）。
   规则行文案（对齐 luohuo L28 的「直送/外转 → 状态」句式，按我方 D9 + r5 映射）：`卸车规则：自提 → 到站待提 ｜ 送货上门 → 到站待送 ｜ 外转 → 到站待转 ｜ 大车直送 → 不参与末端卸车，保持分拨发车`。
4. **Tabs**：`全部 / 未卸车 / 已卸车`，带数量；默认「未卸车」（同 luohuo L218 `activeTab='pending'`）。三个 Tab 的数据与计数均**不含大车直送运单**。操作条按钮**随 Tab 切换**（对齐 luohuo，不再固定四个）：
   - **未卸车 Tab**：只保留一个 `卸车(N)` 主按钮（N = 勾选的非大车直送未卸运单数，未选禁用；本子车次只剩大车直送 0 票时显示 `卸车(0)` 仍可用，按 0 票完成）+ `已选 N 条`；
   - **已卸车 Tab**：只保留 `取消卸车`（勾选已卸运单后可用）+ `已选 N 条`；
   - **全部 Tab**：`卸车(N)` + `取消卸车` + `已选 N 条`（未卸车、已卸车分别计数）。
   - 作业页**不再提供** `到站` / `取消到站`（只保留在到货卸车列表行操作）；点列表 `卸车` 仍先弹到站确认再进作业页。
5. **操作条**（左右分区与按钮顺序同 luohuo L44–70）：
   - 左：按当前 Tab 渲染上述按钮；`已选 N 条` 与按钮同区。
   - 右：`重置筛选`、`导出`、`列设置`、分页（不随 Tab 变化）。
   - **只读态**（列表 `查看` 进入）：不渲染左侧动作按钮与「已选 N 条」，运单表格无勾选列，费用区禁用且无保存按钮；其余结构与可操作态一致。
   - **不再有「到站卸车费」按钮**：费用录入改为信息条下方的内联费用区（见第 9 点）。
6. **运单表格**：列顺序按 luohuo L76–98 裁剪为我方字段——`运单号`、`运单状态`、`路由类型`、`送货方式`、`目的地`、`货物名称`、`数量`、`重量(kg)`、`体积(m³)`、`运费合计`（四类收入之和）、`收货人`、`收货电话`、`开单时间`、`备注`；隐藏列池保留金额明细。勾选列 50px、序号 70px，与 luohuo L96–103 一致。`送货方式` 列保留 luohuo 列位，但本页查询已排除大车直送，实际只会出现 1 送货 / 2 自提（外转由路由类型体现，不在该列）。
7. **确认到站弹窗**（复用 luohuo L116–152 形态）：宽 430px，字段 `子车次号`、`车次名称`、`目的网点`、`到站时间`（默认当前，可改），提示「确认后写入车次及本车运单的实际到站时间，运单状态由已发车更新为末端到车」；页脚 `取消` / `确认到站`。确认后子车次 `30 已到站`；**仅非大车直送运单** → 90 末端到车（大车直送保持 80/2 不变）；写日志 7。从列表行 `卸车` 触发时，确认后直接跳转本页并默认「未卸车」Tab。
8. **卸车确认弹窗**（对齐 luohuo L328–341 的勾选清单二次确认）：
   - 内容：`车次号/车次名称`（只读）→ `本次卸车运单`（清单，只读；不含大车直送）→ `到站卸车费` / `到站其他费`（只读展示已保存金额，未录显示「未录入」，失效显示「已失效（¥x）」，仅干线 + 末端网点显示）→ 页脚 `取消` / `确认卸车`。
   - 可卸 0 票（子车次只剩大车直送）：弹窗仍可确认，文案「本子车次无末端卸车运单（大车直送不参与），是否直接完成卸车？」，确认后子车次置 40，运单状态不变，写日志 8（备注「0 票卸车」）。
   - 短驳车次、分拨中心卸车：不显示费用行，弹窗退化为纯确认。
   - 确认后同一事务：非大车直送运单按 D9 映射、子车次全部卸完自动 `40 完成卸车`、写日志 8。
9. **到站费用内联录入区（v1.3，替代原弹窗）**：位于车次信息条下方、Tabs 与操作条上方的一行式卡片，仅干线 + 末端网点渲染；未到站时整区禁用（灰底 + 「未到站，整区禁用」标签）。
   - **布局**：左侧「到站卸车费(元)」输入框、「到站其他费(元)」输入框（均 ≥0、两位小数，默认取已保存值，未录为 0）；紧接其后为**只读**「到站付运输费(元)」灰底展示（取 `biz_trip.arrival_transport_fee`，发车 Tab 录入，空值显示 0.00，不参与本子车次分摊预览）；中间只读文案「分摊方式：按本车统一分摊方式：重泡比/重量/体积」（与 `biz_trip.alloc_method` 同源）；右侧实时「合计 ¥X」与 `保存费用` 主按钮；下方「逐票明细」折叠面板（默认收起，展开显示运单号/基数/卸车费分摊/其他费分摊/小计）。
   - **到站付运输费只读口径**：按**本目的网点**取 `biz_trip_fee(trip_dest_id>0, FEE0012)` 金额，在卸车页仅展示、不可编辑（改录走发车 Tab 费用信息）；该笔属子车次级，只分摊到本网点运单，不计入本区内联「合计」，视觉上与两个可编辑金额输入框明显区分（灰底、无输入光标）。
   - **实时重算**：两个输入框 `oninput` 即按 4.2 `FeeAllocator` 同一口径重算逐票与合计（防抖 300ms，可本地算或调 `GET .../arrival-fees/preview`）；`Σ基数=0` 时显示错误并禁用保存。
   - **保存**：调 `PUT .../arrival-fees`（`unloadFee`、`otherFee` 一起提交）；同一事务 upsert `FEE0019/FEE0020` 两行、重算分摊/成本/毛利/汇总，返回服务端结果并刷新信息条与预览；写日志 12（两项各记 old/new）。
   - **取消后重新录入**：取消到站/取消卸车后两项费用为失效态，金额仍保留；重新进入本页时把失效金额作为**输入框默认值预填**，并在区域上方提示「原到站费用已失效，当前金额为预填默认值，保存后重新生效」；不点保存不改变任何数据。
   - **无分摊单元**：本子车次全部为大车直送时整区禁用，提示「本子车次无末端卸车单元，不产生到站费用」。
   - **分摊方式本期只读**：若后续允许在内联区修改，需同步重算全车所有成本项分摊与车次汇总，本期不开放。
10. **与 luohuo 的差异说明（v1.3）**：luohuo 有「取消卸车」（已卸车 Tab，回退运单到已到车）；我方**本期支持**，并额外补齐「取消到站」、前置校验（未被下游配载、未签收/核销）、库存回退与到站费用失效；费用录入由弹窗改为**页面内联**（到站卸车费 + 到站其他费）。状态矩阵、接口、权限与风险见 5.2/5.3/6.7/7.1/8.4/8.6。
11. **权限**：页面按钮 `运输管理:装车配载:到站卸车`（迁移种子）及 `运输管理:装车配载:取消到站` / `...:取消卸车`；后端按 D5 受控放开 + 强制 `dest_dept_id = 当前机构` 校验（或车次调度机构），非本站用户不可见/不可操作。
12. **大车直送排除（v1.2-r5，查询层实现）**：列表、Tab 计数、信息条合计、可卸判断、费用分摊预览**全部在后端查询排除 `biz_waybill.delivery_method = 3`**，不是前端隐藏；前端不提供「显示大车直送」开关。这批运单在末端无任何操作入口，状态停在 80 分拨发车，由后续「直送签收」模块推进（见第 10 节）。
13. **取消卸车 / 取消到站弹窗（v1.3）**：
   - 取消卸车（430px，形态对齐 luohuo L348–360）：`子车次号` → `已选卸运单`（N 票，只读，取「已卸车」Tab 勾选集）→ `取消原因`（必填）→ 提示「取消后运单回退到到站状态，库存退回本站；已被下一车次配载或已签收的运单会被拒绝」；页脚 `取消` / `确认取消卸车`。确认后按 5.2/5.3 回退并调 `POST .../cancel-unload`。
   - 取消到站（430px）：`子车次号`/`目的网点` → `取消原因`（必填）→ 提示「已到站未全部卸车可取消；若已有卸车运单将一并回退到分拨发车（已卸 N 票），库存退回上一站」；确认后调 `POST .../cancel-arrive`，子车次 30→20、本子车次非大车直送运单全部回退 80 且清空卸车标记。
   - 两者均要求当前用户机构 = 子车次 `dest_dept_id`（或车次调度机构），失败返回明确原因；成功后刷新信息条、Tab 计数与列表。

---

### 6.11 运单毛利计算公式配置（系统设置，v1.3 新增）

> 口径：毛利以**运单（配载单元）**为最小计算单位；本页配置的是**单票公式**，车次毛利不另设算法，只做 Σ 已配载票毛利（见 4.4）。原 4.4「不做脚本编辑器」的自我决定**作废**：本页提供结构化勾选（加/减项），仍不开放自由脚本编辑器。

**入口与权限**

- 菜单：系统设置 → 运单毛利公式；页面标题「运单毛利计算公式」，路由 `/system/config` 下子页。
- 权限：总部财务/管理员（按钮权限 `系统设置:毛利公式:查看` / `系统设置:毛利公式:保存`）；总部全局配置，不做分公司级，登记数据权限豁免（同 3.11）。

**页面结构（自上而下）**

1. **说明条**：单票为最小单位、车次毛利=Σ已配载票、公式变更只影响之后的车次。
2. **收入项表**：列=勾选、费用编码、费用名称（`INC01` 现付运费 / `INC02` 到付运费 / `INC03` 回付运费 / `INC04` 月结运费，只读项）、加/减切换、单票样例值。默认全部勾选、方向为「加」。
3. **返款行**：勾选「扣减返款（`rebate_total`）」；勾选时作为减项，取消则不扣返款；默认勾选。
4. **成本项表**：列=勾选、费用编码、费用名称、加/减切换、单票样例值；候选 = `sys_fee_type` 中 `profit_enabled=1` 的成本项（含到站卸车费 `FEE0019`、到站其他费 `FEE0020`）。默认按「参与毛利」开关带入、方向为「减」。
5. **实时公式文本**（原型 `#gross-formula`）：按当前勾选与方向拼接，如「现付运费 + 到付运费 + 回付运费 + 月结运费 − 返款合计 − 现付运输费 − 现付油卡费 − …」；勾选变化即时刷新。
6. **单票试算**：用一张样例运单（取样例车次已配载票中一票）**逐项列出**——该票每个收入项、该票返款（`rebate_total`）、该票每个分摊成本项，再给合计与该票毛利 = 收入 − 返款 − 成本；**只算单票，不在此页算车次**。原型样例按重量比例演示分摊，实际按车次 `alloc_method`（4.2）。
7. **车次汇总对账（只读演示）**：样例车次 Σ已配载票毛利 vs（车次收入合计 − 返款合计 − 车次成本合计），展示「对账差额」与「校验通过 / 分摊异常」标签，用来说明车次毛利只是汇总。
8. **配置变更日志**：版本号、修改人、修改时间、变更摘要（本次增删/加减的项）；保留最近 20 版。

**默认值**

- 单票：该票收入合计 − 该票返款 − 该票分摊成本合计（收入四项全加、返款扣减、成本项按参与毛利开关全减）。
- 车次：Σ 已配载票毛利；未配载 / 已解除配载 / 已失效的票不计入。

**与「参与毛利」开关的关系**

- `sys_fee_type.profit_enabled` 决定成本项**默认**是否进入候选表并预勾选；公式页可覆盖（取消勾选=不计入；把未勾选项加回）。
- 收入项恒为「加」、返款恒为「减」，不提供反向，避免口径错乱；每项仅成本项可在加/减间切换。
- 保存后以公式页为准，后续车次/到站费用计算按新公式；`profit_enabled` 只影响默认带入，不回改已保存公式。

**校验与边界**

- 至少勾选 1 个收入项，否则 400「至少保留一个收入项」。
- 收入项与成本项全部未勾选（公式为空）→ 400「公式不能为空」。
- 试算样例该票收入合计 ≤ 0 → 拒绝保存并提示「样例收入为 0，无法试算」（除零保护）。
- 分摊成本不闭合（4.4 等式差额 ≠ 0）→ 页面标「分摊异常」，允许保存公式但提示先修分摊；不得把异常结果作为对账依据。
- 减项之和大于收入 → 单票毛利为负，允许（红色提示，不阻断）。
- 配置变更只影响之后保存的车次/到站费用；已落库车次毛利不回溯（重算入口本期不做）。

**接口**

- `GET /api/v1/system/config/gross-formula`：读公式配置 + 收入/成本候选 + 变更日志。
- `PUT /api/v1/system/config/gross-formula`：保存公式 JSON（`version` 自增、写 `update_by/update_time`、向 history 头部追加摘要）。
- `POST /api/v1/system/config/gross-formula/trial`：传公式草稿 + 样例运单 id，返回单票试算与车次对账结果（不落库）。
- 车次侧对账：`GET /api/v1/transport/trip/{id}/gross-check`（见 7.1），返回 `tripGross`、`sumTicketGross`、`incomeTotal − rebateTotal − costTotal` 与 `diff`。

**存储（`sys_config`）**

- key=`gross.formula`，`value_type=json`，`config_value` 存 JSON 文本：

```json
{
  "version": 3,
  "rebateDeduct": true,
  "items": [
    { "feeCode": "INC01", "name": "现付运费", "side": "add" },
    { "feeCode": "INC02", "name": "到付运费", "side": "add" },
    { "feeCode": "FEE0001", "name": "现付运输费", "side": "sub" },
    { "feeCode": "FEE0019", "name": "到站卸车费", "side": "sub" }
  ],
  "updatedBy": "陈敏",
  "updatedAt": "2026-11-25 10:40:00",
  "history": [
    { "version": 2, "updatedBy": "陈敏", "updatedAt": "2026-11-20 09:12:00", "summary": "增加到站卸车费为减项" }
  ]
}
```

- 保存时 `version + 1`、更新 `update_by/update_time`、history 头部插入本次摘要并截断到 20 条；页面「配置变更日志」直接读 `history`。

**原型与截图**

- 原型页面：`系统设置 → 运单毛利公式`（`Views.grossFormula`），含单票试算、车次对账、变更日志。
- 截图：`outputs/screenshots-v1.3/1280-运单毛利公式.png`、`1100-运单毛利公式.png`；两档无横向溢出、console 0。

---

## 7. 接口清单

统一前缀 `/api/v1`，返回 `ApiResult<T>`，分页返回 `PageResult<T>`（含 `summary` 合计）。

### 7.1 车次

| # | 方法 | 路径 | 说明 | 关键入参 | 关键出参 |
|---|---|---|---|---|---|
| 1 | GET | `/transport/trip/page` | 车次分页列表 | tripNo、status[]、originDeptId、destDeptId、plateNo、driverName、planDepartStart/End、departStart/End、planArriveStart/End、actualArriveStart/End、createStart/End、filterModes、sort、page、size | 列表 + summary |
| 2 | GET | `/transport/trip/{id}` | 车次详情（含子车次/费用/汇总） | id | TripDetailVO |
| 3 | POST | `/transport/trip` | 创建车次 | tripName、planDepartTime、originDeptId、destDeptIds[]、vehicle、driver、remark | id、tripNo、subTrips |
| 4 | PUT | `/transport/trip/{id}` | 修改车次 | 同上（状态受限） | — |
| 5 | DELETE | `/transport/trip/{id}` | 逻辑删除车次 | id | — |
| 6 | GET | `/transport/trip/pool` | 待配载池（左栏；**资格校验在此一次做足**）。返回**可配单元**三类：未拆整票、母单余额（剩余件数>0）、子单；条件：`current_hub_id=trip.origin_dept_id`、`load_status=0`、`status IN (20,60)`、剩余量>0、下一节点 ∈ 目的网点集合、未被其它车次有效配载、排除末段外转。支持两栏列设置/表头筛选：`filterModes`（业务列模式 JSON）与 `columns`（当前可见列 key） | tripId、waybillNo、unitType（1整票/2母单余额/3子单）、status[]、customerName、receiverName、destStation、nextDeptId、inventoryStart/End、createStart/End、quantityMin/Max、weightMin/Max、volumeMin/Max、filterModes、columns、sort、page、size | 池列表（含 unitType、parentNo、remainingQuantity/Weight/Volume、originalQuantity/Weight/Volume、splitCount、childCount）+ summary |
| 7 | GET | `/transport/trip/{id}/waybills` | 已配载单元（右栏，按子车次分组，独立筛选/分页）；返回整票与子单，含母子关系；支持 `filterModes` 与 `columns` | id、waybillNo、parentNo、unitType、status[]、customerName、receiverName、destStation、destId、filterModes、columns、sort、page、size | 分组列表（含 parentNo、isChild、splitRatio、本单元 quantity/weight/volume、拆分金额）+ 汇总 |
| 8 | POST | `/transport/trip/{id}/waybills` | 批量装车配载（右移即调用；**只落库、不做资格校验**；`waybillIds` 为配载单元 id：整票母单或子单；**已拆母单余额按剩余量自动生成子单并与本车同母单子单合并，合并为整单时回母单**；条件更新防并发） | waybillIds[] | 成功数、失败清单（id + 真实异常原因）、超限提示、最新汇总 |
| 8a | POST | `/transport/trip/{id}/waybills/split-load` | **拆单装车（v1.3 新增，编号 8a 不打乱既有引用）**：同事务锁母单行 → 校验 `1 ≤ quantity ≤ 剩余件数` → 按现有规则生成子单（取当前有效子单中最小未占用序号，作废后回收复用）→ 按 4.5 拆分金额/重量/体积 → 子单落配载 → 刷新分摊与汇总 | parentWaybillId、quantity、tripDestId?（默认按下一节点匹配） | childWaybillId、childWaybillNo、splitRatio、childAmounts{prepaid/collect/return/monthly/totalFee/deliveryFee/cashBack/oweBack/rebateTotal/validFreight}、childWeight/Volume、parentRemaining{quantity/weight/volume/amount}、最新汇总 |
| 8b | GET | `/transport/trip/{id}/waybills/{waybillId}/split-preview` | 拆单预览（不落库）：按件数计算子单与余额金额、重量、体积；算法与 4.5 一致 | quantity | 同 8a 的预览部分 |
| 9 | DELETE | `/transport/trip/{id}/waybills` | 批量移除配载（左移/全选左移；单票也走此接口；**只落库、不做资格校验**） | id、waybillIds[] | 成功数、失败清单、最新汇总 |
| 10 | PUT | `/transport/trip/{id}/vehicle` | 保存车辆/司机信息 | vehicleId、driverId、plateNo、vehicleType、vehicleLoad、vehicleVolume、driverName、driverPhone | — |
| 11 | PUT | `/transport/trip/{id}/fees` | 保存成本项 + 全车分摊方式 | allocMethod（车次级，1/2/3）、items[{feeCode, amount}] | 汇总、分摊结果 |
| 12 | GET | `/transport/trip/{id}/fees/preview` | 费用分摊预览 | allocMethod、items[]（未保存也可预览） | 每配载单元每项金额、基数、系数、汇总 |
| 13 | POST | `/transport/trip/{id}/depart` | 发车 | planArriveTime | — |
| 14 | POST | `/transport/trip/{id}/cancel-depart` | 取消发车 | reason | — |
| 15 | POST | `/transport/trip/dest/{destId}/arrive` | 子车次到站 | destId | — |
| 16 | POST | `/transport/trip/dest/{destId}/unload` | 子车次完成卸车 | destId | — |
| 15a | POST | `/transport/trip/{tripId}/dests/{destId}/cancel-arrive` | 取消到站（仅未卸车；运单 90/50→80、`load_status 3→2`，主车次按剩余重算；两项到站费用（卸车费/其他费）标记失效、金额保留） | reason | 子车次状态、运单更新数、主车次状态 |
| 16a | POST | `/transport/trip/{tripId}/dests/{destId}/cancel-unload` | 取消卸车（逐票；校验未被下游配载、未签收/核销；库存回退；费用标记失效） | waybillIds[]、reason | 运单状态、子车次状态、库存回退结果、主车次状态、费用失效结果 |
| 17 | POST | `/transport/trip/{id}/close` | 关闭车次 | reason | — |
| 18 | GET | `/transport/trip/{id}/print` | 打印运输清单数据（打印装车单同一接口）；拆单行输出**子单号 + 母单号 + 本单元件数/重量/体积** | id、subTripId?（按子车次打印可选） | 表头 + 单元清单（含 parentNo/isChild）+ 合计 |
| 19 | GET | `/transport/trip/{id}/logs` | 操作日志 | id | 日志列表 |
| 20 | GET | `/transport/trip/options` | 下拉选项（网点/车辆/司机/车型） | type、keyword | options[] |
| 21 | POST | `/transport/trip/{id}/dests` | 新增目的网点（D10，任意状态可用，发车后新子车次置在途） | destDeptId、planArriveTime? | 新子车次 |
| 22 | PUT | `/transport/trip/{id}/dests/{destId}` | 修改目的网点/顺序/计划到站时间（该网点无配载运单） | destDeptId?、seq?、planArriveTime? | — |
| 23 | DELETE | `/transport/trip/{id}/dests/{destId}` | 删除目的网点（该网点无配载运单） | destId | — |
| 24 | POST | `/transport/vehicle/quick` | 车辆快速新增（D1） | plateNo、vehicleLoad、vehicleVolume、vehicleType? | vehicleId |
| 25 | POST | `/transport/driver/quick` | 司机快速新增（D1） | driverName、driverPhone、licenseNo? | driverId |
| 37 | GET | `/transport/trip/{id}/gross-check` | 车次毛利对账校验：返回车次毛利、Σ票毛利、收入−返款−成本三值，`diff≠0` 标分摊异常（4.4、6.11） | id | tripGross、sumTicketGross、incomeTotal、rebateTotal、costTotal、diff、balanced |

### 7.2 费用类型配置

| # | 方法 | 路径 | 说明 | 请求参数 | 返回 |
|---|---|---|---|---|---|
| 26 | GET | `/system/fee-type/page` | 费用类型分页统一列表：**收入 4 项只读虚拟行 + `sys_fee_type` 配置行（成本/代收/代付及新建收入）**；支持 `feeCategory[]`（单选/多选）筛选，返回总项数与分类型计数 | feeCategory[]、feeCode、feeName、enabled、filterModes、columns、sort、page、size | rows（含 readonly 标记、取数来源、方向）、total、counts{income,cost,collect,payable} |
| 27 | GET | `/system/fee-type/catalog` | 未配置的费用项目录（新增下拉），按 `feeCategory` 返回可选名称/编码/默认方向 | feeCategory | list |
| 28 | POST | `/system/fee-type` | 新增费用类型（服务端生成 `FEE` 编码；新建收入生成 `INC` 编码）。校验：代收/代付 → `profitEnabled=false`、方向为空；收入 → 方向=收入；成本 → 方向=成本 | feeCategory、feeName、enabled、profitEnabled、sortOrder、remark | id、feeCode |
| 29 | PUT | `/system/fee-type/{id}` | 编辑：名称/编码/类型不可改；代收/代付的参与毛利恒为否 | enabled、profitEnabled、sortOrder、remark | — |
| 30 | PUT | `/system/fee-type/{id}/status` | 启用/停用（内置收入四项为虚拟只读行，不提供该接口） | enabled | — |

### 7.3 系统配置（全局重泡比）

| # | 方法 | 路径 | 说明 |
|---|---|---|---|
| 31 | GET | `/system/config/volume-weight-ratio` | 读取全局默认重泡比 |
| 32 | PUT | `/system/config/volume-weight-ratio` | 保存全局默认重泡比（>0 校验） |
| 38 | GET | `/system/config/gross-formula` | 读取运单毛利公式配置（`sys_config.gross.formula`）+ 收入/成本候选 + 变更日志 history |
| 39 | PUT | `/system/config/gross-formula` | 保存运单毛利公式（版本号自增、写修改人/时间、history 追加，保留 20 版）；校验至少 1 个收入项、公式非空 |
| 40 | POST | `/system/config/gross-formula/trial` | 公式试算（不落库）：传公式草稿 + 样例运单 id，返回单票逐项试算与车次汇总对账差额 |

### 7.4 到站卸车页（末端网点）

| # | 方法 | 路径 | 说明 | 关键入参 | 关键出参 |
|---|---|---|---|---|---|
| 33 | GET | `/transport/trip/{tripId}/dests/{destId}/unload` | 到站卸车页初始化：车次信息条 + Tab 计数 + 运单列表 + 内联费用区数据；**查询层排除 `delivery_method=3 大车直送`** | tab(all/pending/done)、筛选、sort、page、size | trip、dest、counts、list、summary、feeEditable、allocMethod、arrivalUnloadFee、arrivalOtherFee、feeInvalid、canCancelArrive、canCancelUnload |
| 34 | POST | `/transport/trip/{tripId}/dests/{destId}/arrive` | 确认到站（仅推进非大车直送运单） | arriveTime | 子车次状态、运单更新数 |
| 35 | POST | `/transport/trip/{tripId}/dests/{destId}/unload` | 卸车（逐票勾选或批量全卸；`waybillIds` 为空时按 0 票完成子车次） | waybillIds[] | 运单状态、子车次状态、汇总 |
| 36 | PUT | `/transport/trip/{tripId}/dests/{destId}/arrival-fees` | 内联保存到站卸车费 + 到站其他费（不改状态；两项一起提交，分摊范围排除大车直送） | unloadFee、otherFee | 两项金额、分摊结果、汇总、毛利 |
| 36a | GET | `/transport/trip/{tripId}/dests/{destId}/arrival-fees/preview` | 内联实时预览（同一 `FeeAllocator`，仅供输入反馈） | unloadFee、otherFee | 逐票分摊行、合计、错误码（Σ基数=0） |

> 取消到站 / 取消卸车：复用 7.1 的 **15a / 16a**（`POST /transport/trip/{tripId}/dests/{destId}/cancel-arrive`、`.../cancel-unload`），到货卸车列表与卸车页为同一接口。接口 33 的返回见上表 `canCancelArrive`、`canCancelUnload`、`feeInvalid`。

- 33 的 `feeEditable`：`trip_category=1`、`dest_dept_id.sys_dept.dept_type=5`，且**本子车次存在至少 1 票 `delivery_method≠3` 的配载运单**才为 true；否则内联费用区禁用。未到站（`sub_status=20`）时同样返回 `feeEditable=false`（前端整区禁用）。
- 33 的 `feeInvalid`：两项费用各自是否失效（来自 `biz_trip_dest.arrival_unload_fee_valid/arrival_other_fee_valid`）；失效时仍返回原金额用于前端预填。
- 34/35/33 均按 `delivery_method≠3` 过滤：大车直送运单不返回、不推进状态、不计入 Tab 计数与信息条合计。
- 35 允许 `waybillIds` 为空并完成子车次（子车次只剩大车直送时，按 0 票卸车，写日志 8 备注「0 票卸车」）；非空时服务端强校验每票属于本子车次且非大车直送。
- 36 服务端强校验 `feeEditable`，非干线或非末端传入金额直接 400；本子车次无非大车直送运单时返回 400「本子车次无末端卸车运单，不产生到站费用」。分摊基数只取非大车直送运单，`Σ基数=0` 按 D14 阻断。`unloadFee`/`otherFee` 可同时为 0。
- 36 同一事务内：upsert `biz_trip_fee(FEE0019, trip_dest_id)` 与 `(FEE0020, trip_dest_id)`、刷新 `biz_trip_dest.arrival_unload_fee/arrival_other_fee` 及有效性、`biz_trip.arrival_unload_fee/arrival_other_fee`、本子车次分摊明细（两项各自行）、成本合计、毛利；写日志 12（两项各记 old/new）。保存后两项均置为有效（`fee_status=1`、`*_valid=1`）。
- 36a 为只读预览，不落库、不写日志；与 36 复用同一 `FeeAllocator` 与同一基数过滤，避免前后端口径不一致。
- 35 与 36 解耦：卸车不写费用、写日志 8；费用不改状态、写日志 12。

### 7.5 复用现有接口

- 网点下拉：沿用 `/operation/dept-options` 或 `UserOrgService` 的管理范围。
- 运单改单 / 作废：沿用 `PUT /operation/waybills/{id}/amend`、`POST /operation/waybills/{id}/void`，由 `WaybillService` 内部回调车次刷新（见 8.5），不新增车次侧接口。
- 列表导出：沿用 `/export/tasks`（异步导出），pageKey=`transport.loading`。
- 列配置：沿用现有 `ColumnConfigController`，`GET/PUT/DELETE /api/v1/system/column-configs/{pageKey}`；pageKey：列表页 `transport.loading`、左栏 `transport.loading.pool`、右栏 `transport.loading.loaded`、费用类型配置 `transport.feeType`；按「当前用户 + pageKey」隔离，**禁止用 localStorage 代替**。
- 双栏筛选模式：沿用 `2026-11-21/column-filter-modes/outputs/column-filter-modes-design-v1.0.md` 的列配置 v2 与 `filterModes` JSON 契约；默认查询值随列配置持久化，进入页面先读配置再 `applyDefaults()`，再首次查询。
- 操作日志审计：新表 `biz_trip_log`，不复用 `biz_waybill_log`（动作类型不同，避免污染运单日志）。

---

## 8. 与现有模块的衔接点

### 8.1 可复用的现有能力

| 现有能力 | 复用方式 |
|---|---|
| `sys_dept` 机构树 | 始发/目的网点、分拨中心/末端判断（`dept_type`、`is_leaf`） |
| `UserOrgService` | 当前机构、管理范围网点下拉、越权校验 |
| `sys_route` + `sys_route_transfer` | 创建车次时估算计划到站时间；目的网点合法性提示 |
| `biz_waybill_route_node` | 取运单下一节点（`seq = current_node_seq + 1` 的 `to_dept_id`） |
| `biz_waybill` 金额字段 | 四类运费收入（prepaid_fee/collect_fee/return_fee/monthly_fee）、返款（rebate_total）、有效运费的取数来源 |
| `WaybillEnums.Status` | 运单状态码唯一来源，禁止在车次模块另写数字 |
| `SmartTable` + 列配置 + 异步导出 | 列表页能力全部复用 |
| `DataScopeContext` | 车次/配载表默认按 `dept_id` 过滤；池查询需受控放开（D5） |
| `sys_menu` 1301 | 菜单已存在，只需补按钮权限 |

### 8.2 必须新增的字段/表

| 对象 | 变更 | 原因 |
|---|---|---|
| `biz_waybill` | +5 列（current_hub_id、current_hub_name、current_node_seq、inventory_time、load_status） | 待配载池定位、下一节点匹配、防重复配载 |
| `sys_route` | +1 列 `volume_weight_ratio`（线路重泡比，空/0 回落全局） | D3 线路级重泡比覆盖 |
| 11 张新表 | 见第 3 节 | 车次、子车次、配载、车次费用、分摊、日志、流水、费用配置（成本项）、车辆、司机、系统配置 |
| 配载明细保存 `status_before_load` | 放 `biz_trip_waybill` | 关闭/移除/作废时精确还原运单状态 |

### 8.3 对现有模块的行为影响

1. **运单改单/作废/拆单/合单（D6 已定）**：配载后**允许改单、允许作废**，**禁止拆单/合单**。改单/作废在 `WaybillService.amend` / `voidWaybill` 内触发车次联动刷新（同事务），拆合单入口对 `load_status ≥ 1` 的运单直接拦截。详见 8.5。
2. **运单开单**：保存时需初始化 `current_hub_id=origin_hub_id`、`current_hub_name`、`current_node_seq=0`、`inventory_time=create_time`、`load_status=0`。这是开单服务的小改动，需列入开发范围。
3. **运单列表**：暂不增加「配载状态」列，避免影响已验收页面；如需要可在隐藏列池追加（D11）。
4. **上游卸车模块**：未来卸车完成时需把 `current_hub_id` 更新为卸车网点、`current_node_seq` 推进、`inventory_time` 更新、`load_status=0`；本期该模块未建，需在接口层预留统一方法 `WaybillInventoryService.moveToHub(...)`，避免各模块各写一套。
5. **数据权限**：新增业务表默认按 `dept_id` 过滤；`sys_fee_type`、`sys_config`、`biz_trip_no_seq` 需登记豁免（评审后）。
6. **菜单按钮权限**：需为 1301 增加按钮权限（创建/修改/删除/取消发车/打印/配载/发车/到站/卸车/关闭），走菜单管理维护或迁移脚本（D12）。

### 8.4 库存、可配单元与下一节点的统一口径

- 「库存可配」= `biz_waybill.current_hub_id = 车次始发网点`（`trip.origin_dept_id`，创建时默认当前机构），不再用「开单网点」判断。
- 「当前路由下一节点」= `biz_waybill_route_node` 中 `seq = current_node_seq + 1` 的 `to_dept_id`；没有该节点说明已到末段或路由缺失，不可配载。
- 外转/直达末段没有 `to_dept_id`（终点是区划）时，不可作为干线车次目的网点，池中直接排除（不走穿梭时提示）。
- **卸车完成后**（不是到站）：`current_hub_id = 子车次目的网点`、`current_node_seq = 配载明细.route_node_seq`、`inventory_time = unload_time`（D9 已定取卸车完成时间）；**大车直送（`delivery_method=3`）不执行这三项推进**，库存仍视为随车，状态停在 80，由直送签收流程处理。
- **取消到站不回退库存**：到站阶段库存尚未推进，只回退状态；若发现该票库存已推进到目的网点（异常数据）则拒绝并提示走「取消卸车」。
- **取消卸车回退库存（v1.3）**：`current_hub_id` 退回上一站 = 配载明细所属车次的始发网点（`biz_trip.origin_dept_id`，即该段起点）；`current_node_seq` 退回上一节点 = `配载明细.route_node_seq − 1`（若结果为 0，取始发网点对应的路由节点 seq，通常为 1）；`inventory_time` 恢复为 `配载明细.load_time`（本次配载时间，即上一站落入/可再次配载时点）。回退值**只从配载明细与车次始发网点取，不信任前端传值**。
- **取消卸车并发保护**：事务内先 `SELECT ... FOR UPDATE` 锁子车次行与本次取消的运单行，再做条件更新 `WHERE current_hub_id = dest_dept_id AND current_node_seq = route_node_seq AND load_status IN (4,0) AND status IN (100,110,120,60)`；更新 0 行即回滚并返回 409「库存或状态已变化，请刷新后重试」。下一车次配载同样锁运单行，保证「取消卸车」与「下一车次配载」串行；若该票已有活跃下游配载记录，直接拒绝。

**可配单元（v1.3）**

| 类型 | 判定 | 可配方式 |
|---|---|---|
| 未拆整票 | `is_child=0`、无有效子单、余额=原件数 | 直接右移，整票配载（`waybill_id`=母单） |
| 母单余额 | `is_child=0`、剩余件数 `>0` 且 `<原件数` | 直接右移：按剩余量生成子单并与本车同母单子单合并，合并为整单时整体回母单 |
| 子单 | `is_child=1`、`status IN (20,60)`、`load_status=0` | 直接右移，配载该子单；不可再拆 |

- 剩余量：`remaining_qty = quantity − Σ(有效子单 quantity)`；`remaining_weight/volume` 同口径；余额为 0 的母单退出池，但运单列表保留为汇总壳（5.7）。
- 子单号沿用现有规则：母单号 + `-` + 两位序号，`child_seq` 取当前有效子单中最小未占用序号，范围 1..99，作废后回收复用；达到 99 时拒绝继续拆单并提示走新运单或合单。作废子单保留原运单号后缀，只把内部 `child_seq` 置空以释放唯一占位。

**待配载池查询条件（一次性做足，等价于「可配资格」）**

1. `current_hub_id = trip.origin_dept_id`；
2. `load_status = 0`（母单余额本身恒为 0）；
3. 单元 `status IN (20, 60)`；已拆母单余额保持 20/60；
4. 剩余量 > 0（母单按 `quantity − Σ有效子单` 计算；整票=原件数；子单=本单量）；
5. 下一节点 ∈ 本车次目的网点集合（子查询/内存集合过滤，按数据权限实现方式定）；
6. 未被其它车次有效配载（活跃 `biz_trip_waybill` 且车次未关闭；同一母单的不同子单互不影响）；
7. 排除末段外转（无 `to_dept_id`）；
8. 数据权限：仅车次始发网点（D5 受控放开）。

> **约定**：以上条件就是「可配资格」的唯一定义，全部在池查询一次完成；**穿梭接口只落库，不做任何资格校验、不返回「不符合条件」清单**（详见 6.4 ⑥）。后续实现不得把资格判断加回穿梭阶段。母单余额由配载接口按剩余量自动生成子单，属落库实现细节，不是资格校验。

**防超配与并发**

- 配载（整票/子单）：事务内 `SELECT ... FOR UPDATE` 锁配载单元行（`biz_waybill.id`）+ 条件更新 `load_status=0`；更新 0 行即拒绝。
- 拆单（8a）：事务内锁**母单行**，重算剩余件数，校验 `1 ≤ q ≤ 剩余`，再生成子单；同一母单并发拆单由母单行锁串行化，**同一份额只可能被一个车次拿到**。
- 子单生成与配载明细写入同一事务；任一步失败整体回滚，不允许出现「子单已生成但未配载」的孤儿数据。
- 车次/子车次状态推进使用状态条件更新（`WHERE trip_status=?` / `sub_status=?`）防重复提交。


### 8.5 改单 / 作废 / 拆单联动流程（D6）

> **入口变化（v1.3）**：改单、作废、拆单/合单全部**不在配载页**提供。配载页右栏只读、无操作列；移除配载只能左移穿梭。改单/作废走运单管理或运单详情；拆单装车走配载页「拆单装车」（只创建子单并装本车），撤销拆单/合并走运单模块。

**改单（`WaybillService.amend`）**

1. 校验运单存在且非作废；配载中（`load_status ≥ 1`）不做拦截。
2. 按运单自身规则更新运单与路由节点。
3. 若存在活跃配载记录（`biz_trip_waybill.load_status IN (1,2,3)`），在同一事务内：
   - 覆盖配载明细快照：件数、重量、体积、运费合计、返款、有效运费、收货人、电话、目的地、货名；
   - 若重量/体积变化 → 重算超重/超容（只提醒，写日志）；
   - 重跑 `FeeAllocator`（当前 `biz_trip.alloc_method` + 逐票重泡比）刷新 `biz_trip_waybill_fee`、`allocated_fee`、`profit_cost`、`gross_profit`；
   - 重算车次汇总（单元数/件数/重量/体积/装载率/四类运费收入/返款/成本/毛利）；
   - 若 `delivery_method` 变化（大车直送 ↔ 非直送）→ 按 5.5/5.6 重算末端参与范围与本子车次两项到站费用分摊（含无可分摊单元时费用清零）；
   - 写 `biz_trip_log`（action_type=13，before/after 快照）。
4. 若改单改动路由下一节点导致与原子车次目的网点不匹配 → 本次不允许保存，提示「改单后下一节点 X 不在本车次目的网点，请先调整车次目的网点或移除该单元」。
5. **子单限制**：已配载子单禁止改件数/重量/体积与任何金额（会破坏母单余额与 4.5 拆分守恒、车次分摊）；仅允许改收货人/电话，并同步配载快照。**母单已存在有效子单且余额 > 0 时**，同样禁止改件数/金额（须先撤销未装车子单或走运单模块专门流程）；未拆整票仍按 D6 正常改单。

**作废（`WaybillService.voidWaybill`）**

1. 原有作废校验不变（900 拒绝重复作废）。
2. 若存在活跃配载记录，在同一事务内：
   - 逻辑删除该配载明细与分摊明细；
   - 运单置作废态（90），`load_status=0`；因待配载池只取状态 20/60，作废单不会回到池中；
   - 重算车次汇总、装载率、毛利；
   - 写 `biz_trip_log`（action_type=14）。
3. 若该单元是某子车次最后一个，不删除子车次（目的网点保留，符合 D10 新增/删除分离）。
4. **拆单相关**：子单未装车可作废，作废后母单余额重算；子单已装车须先左移移除；母单存在任一已装车子单时禁止整票作废。

**配载页刷新（v1.3 两 Tab）**

改单/作废若发生在配载页打开期间：右栏就地更新该单元快照（改单）或移除该行（作废），重拉底部合计与 Tab2 费用区/分摊预览；左栏按新筛选条件重查，不整页 reload，不丢失双栏筛选、勾选与列配置状态。Tab2 已打开时同步刷新汇总与毛利。

**拆单（v1.3 装车拆单，复用现有子单）**

- **时机**：仅未配载整票可拆；已配载单元（`load_status ≥ 1`）禁止再拆或再合（沿用 D6），提示「该单元已配载，请先移除后再拆/合单」。
- **服务步骤（8a，单事务）**：
  1. `SELECT ... FOR UPDATE` 锁母单行，校验 `is_child=0`、无活跃配载、`load_status=0`、车次 10/20；
  2. 重算剩余件数/重量/体积；校验 `1 ≤ 本车件数 ≤ 剩余件数`；
  3. 生成子单：复制母单基础信息与路由，`parent_id/parent_no/is_child=1/child_seq=最小未占用序号`，子单号=母单号-两位序号；**`pre_order_no` 置空**（受 `uk_pre_order_no` 唯一键限制，不得复制）；
  4. 按 4.5 计算子单金额、重量、体积；母单件数/金额不直接改写，余额由 `quantity − Σ子单` 实时得出；
  5. 写配载明细（`waybill_id`=子单）并推进子单 70、`load_status=1`，随后刷新车次汇总与分摊；
  6. 写 `biz_waybill_log(action_type=1)` 与 `biz_trip_log`，记录母子号、件数占比、金额拆分结果。
- **合单/撤销拆单**：本期不在配载页提供；沿用运单模块既有规则（要求相关子单均未装车），合单后余额与母单金额按原值恢复，不在本模块另写一套。
- **状态**：见 5.7（子单独立流转、母单余额保持 20/60、余额 0 后退出池）。

### 8.6 到站费用联动（到站卸车费 + 到站其他费，末端网点）

1. 入口：到站卸车页**内联费用区**的「保存费用」→ `PUT .../arrival-fees`（`unloadFee`、`otherFee` 一起提交）；输入变化时调 `GET .../arrival-fees/preview` 做只读预览。与卸车接口 `POST .../unload` 解耦；原 `arrival-unload-fee` 接口与按钮/弹窗删除。
2. 前置校验：车次 `trip_category=1`；`dest_dept_id` 为末端网点；当前用户机构 = `dest_dept_id`；子车次 `sub_status IN (30,40)`；车次未关闭；两项金额各自 ≥0、两位小数；**本子车次存在至少 1 票 `delivery_method≠3` 的配载运单**（否则 400，整区禁用）。
3. 同一事务内：
   - 按 `(trip_id, trip_dest_id, fee_code)` upsert 两条：`FEE0019`（到站卸车费）、`FEE0020`（到站其他费），写入金额并置 `fee_status=1`；金额为 0 时同样 upsert 为 0，保留费用行；
   - 重算该子车次配载单元的 `biz_trip_waybill_fee`：**两项费用各自生成分摊行**，范围仅本子车次、排除 `delivery_method=3` 大车直送，方式取 `biz_trip.alloc_method`，基数取各单元本单元量；刷新 `allocated_fee/profit_cost/gross_profit`；大车直送单元不生成两项费用的分摊行；
   - 汇总 `biz_trip_dest.arrival_unload_fee/arrival_other_fee` 及有效性，`biz_trip.arrival_unload_fee/arrival_other_fee`（**车次级 = Σ 各子车次有效合计**）、`biz_trip.total_cost_fee`、`biz_trip.gross_profit`、车次与子车次装载汇总；
   - 写 `biz_trip_log(12)` 两条：`field_name=arrival_unload_fee`、`field_name=arrival_other_fee`，各记 old/new 与 trip_dest_id。
4. 卸车动作 `POST .../unload` 单独写 `biz_trip_log(8)`；失败回滚：费用或状态任一失败，整事务回滚，不出现「卸了车没记账」或「记了账没卸车」。卸车查询与状态推进均排除大车直送，子车次只剩大车直送时允许 0 票完成（写日志 8）。
5. **取消到站/取消卸车（v1.3）**：两项费用都**不删除**，置 `biz_trip_fee.fee_status=2` + `invalid_reason`、`biz_trip_dest.arrival_unload_fee_valid=0` 与 `arrival_other_fee_valid=0`，金额保留；同一事务重算两个 dest 汇总（失效不计）、`biz_trip.arrival_unload_fee/arrival_other_fee`、`total_cost_fee`、`gross_profit` 与各单元 `allocated_fee/profit_cost`（关联失效费用的分摊行不参与汇总）；写日志 12 + 16/17。重新进入卸车页时，失效金额作为**输入框默认值预填**（不自动生效）；用户点「保存费用」后按新值 upsert 两条并置 `fee_status=1`、两个 `*_valid=1` 重算。
6. **到站付运输费按网点只读（v1.3/V36）**：卸车页内联区在两项可编辑费用后只读展示**本目的网点**的到站付运输费（发车 Tab 费用信息按网点录入，落 `biz_trip_fee.trip_dest_id>0`）；本页不提供编辑入口；该笔属子车次级成本，只分摊到本网点运单，毛利汇总只计一次，不得与本页两项费用重复计入。
7. **毛利联动与对账（v1.3）**：保存两项费用后，刷新本子车次每个配载单元的 `profit_cost/gross_profit` 与 `biz_trip.gross_profit`；车次毛利只汇总已配载票（Σ票毛利），且必须满足 `车次毛利 = Σ票毛利 = 收入合计 − 返款合计 − 成本合计`。保存后调用 `GET /transport/trip/{id}/gross-check`，`diff≠0` 视为分摊异常并在卸车页/车次详情提示（口径见 4.4、6.11）。

### 8.7 运单列表 / 详情展示建议（拆单）

**建议结论：列表不新增默认列，详情补配载信息；配载池必须显示剩余量。**

| 位置 | 建议 | 理由 |
|---|---|---|
| 运单列表 | 维持「只显示母单」：母徽标 + 「子单(n)」入口，不新增子单行、不新增默认列（D11 不变） | 避免同一票在列表重复出现，件数/金额/合计被重复统计 |
| 运单列表隐藏列 | 可追加「运输进度（汇总状态）」「余额件数」两个隐藏列，默认不显示 | 调度需要时打开；不打扰已验收页面 |
| 运单列表状态列 | 显示 5.7 的展示值：余额 > 0 时取 `MIN(余额状态, 子单最落后状态)`，hover 显示「余额 n 件在 X 网点，m 张子单在途」 | 一张单可能同时在多个节点，单一状态码无法表达，需文案补充 |
| 运单详情 | 头部展示母单汇总状态 + 余额件数/重量/体积；子单清单增列「配载车次 / 子车次 / 装车时间 / 本单元件数/重量/体积 / 状态 / 运费快照」 | 详情是唯一能完整看清母子关系与拆分结果的页面 |
| 运单详情子单行 | 点击子单号进入子单详情（独立只读页/抽屉）；子单详情展示母单号与拆分占比 | 便于客服按子单查在途位置 |
| 配载池 | 必须显示运单类型徽标 + **剩余件数/重量/体积**；子单与母单余额显示母单号 | 拆单后池里同时存在整票、余额、子单，不标类型无法判断能否装 |
| 打印装车单 | 拆单行打印子单号 + 母单号 + 本单元量；合计按单元求和 | 司机/网点按子单卸货，必须能对上母子关系 |
| 运单导出 | 仍按母单导出一行；不导出子单行 | 与列表、合计口径一致，避免财务重复 |

**明确不建议**：把子单做成运单列表的独立行；给母单新增「已配份额/剩余份额」默认列（信息重复且列已很多，放隐藏列或详情即可）。

---

## 9. 数据权限与安全

1. **车次主表/配载/费用/日志**：默认按 `dept_id`（车次创建/调度机构）过滤。
2. **目的网点可见性（D5 已批准）**：目的网点用户需看到本站车次与子车次。批准范围为**两个专用查询**：车次列表/详情、待配载池；实现方式 `DataScopeContext.runAsSystem` + 强制显式条件（车次：「始发 ∈ 范围 OR 目的 ∈ 范围」；池：`current_hub_id = 车次始发网点` + `load_status=0` + 状态 20/60 + 下一节点 ∈ 目的网点集合 + 未被其它车次有效配载 + 排除末段外转），并做接口级归属校验。其他查询仍按 `dept_id` 过滤，禁止无条件下全表。
3. **待配载池**：按 D5 批准的受控放开执行，强制 `current_hub_id = 车次始发网点` 且 `load_status=0`，并叠加 8.4 其余条件（状态、剩余量>0、下一节点、未被其它车次有效配载、排除外转）；**可配单元含未拆整票、母单余额、子单**；禁止无条件下全表查询。接口必须校验 `trip.originDeptId == 当前机构`，防止跨机构偷看库存。**资格校验在此一次完成，配载/移除接口不再重复**。
4. **越权写**：配载/移除/发车/到站/卸车/拆单必须校验车次归属或目的网点归属，不能只靠前端隐藏按钮；拆单额外校验母单 `dept_id` 与当前机构数据权限一致。
5. **到站卸车页（D5 延伸）**：目的网点用户对本站子车次执行到站/卸车/保存到站费用（卸车费 + 其他费），查询按受控放开（强制 `dest_dept_id = 当前机构`），写操作校验「当前用户机构 = 子车次 dest_dept_id」；非本站返回 403。到站费用额外校验干线 + 末端网点，分摊范围排除大车直送、按子单维度。
5a. **取消到站/取消卸车（v1.3）**：仅**本子车次目的网点的操作人**或**车次创建机构的调度**可操作；后端校验「当前用户机构 = 子车次 `dest_dept_id`」或「当前用户机构 = 车次 `dept_id` 且具备调度角色」，并校验按钮权限 `运输管理:装车配载:取消到站` / `运输管理:装车配载:取消卸车`；原因必填并写日志 16/17。前端按同一条件显示/置灰，不能只靠隐藏按钮。
6. **`sys_fee_type` / `sys_config`（D4）**：总部全局配置，登记数据权限豁免，读写权限由按钮控制；不做分公司级配置。
7. **`sys_vehicle` / `sys_driver`（D1）**：按 `dept_id` 过滤；车次选择器只能看到本机构或有权限机构的启用记录；快速新增落在当前机构。
8. **金额安全**：所有金额、比例、装载率、拆单拆分结果服务端重算并覆盖；客户端传值仅作参考。
9. **并发安全**：取车次号用原子流水；配载用单元行锁 + 条件更新；**拆单用母单行锁 + 剩余量校验，同一份额只允许一个车次拿到**；发车/到站/卸车用状态条件更新（`WHERE trip_status=?`）防止重复提交。

---

## 10. 边界与暂不实现

| 项 | 处理 |
|---|---|
| GPS 轨迹 | 配载页不设轨迹 Tab/地图入口；发车只记录时间/操作人，不写轨迹表 |
| 配载页 Tab 形态 | v1.3 起改为**两个 Tab**：Tab1 装车配载（双栏穿梭 + 双栏列设置/表头筛选）、Tab2 发车（车辆 + 费用内联 + 汇总 + 发车）；子车次/日志为顶部弹窗/抽屉，原四个抽屉取消或合并 |
| 配载资格校验位置 | v1.2-r4 起全部前移到待配载池查询一次完成；穿梭只落库、零资格校验，后续不得加回；母单余额直配按剩余量生成子单并合并，属落库实现细节 |
| 拆单 | 只对未配载整票；复用现有子单模型（最多两级、每单 99 张），不新增编号规则；金额按件数拆、尾差归份额大的一方；已配载禁止拆/合（D6、4.5、5.7） |
| 母单余额 | 可直接右移配载：按剩余量生成子单并与本车同母单子单合并，合并为整单时整体回母单并作废子单；剩余 0 退出配载池、运单列表保留为汇总壳 |
| 子单金额 | 只作车次收入/毛利/分摊/打印快照；客户结算、开票、返款仍以母单为准（修订 operation v1.14「子单费用=0」旧约定） |
| 双栏列设置/表头筛选 | 两栏独立 pageKey（`transport.loading.pool` / `transport.loading.loaded`），支持单/多模式与默认查询值，配置持久化后端，禁止 localStorage |
| 运单列表展示 | 不新增默认列、不加子单行；详情子单清单增配载信息；配载池必须显示剩余量（8.7） |
| 关联车次 | 不做（家伟已拍板）：页面不设展示位、不落库 |
| 到车预报推送 | 不实现；预留「发车后事件」扩展点，后续接消息模块 |
| 计划到站时间的历史车速因子 | 一期无历史平均车速数据源，按 `sys_route` 各段 `transit_hours` 累加 + 人工修正；历史车速模型二期接入 |
| 总提货费/总派送费/总外转费 | 字段保留、固定 0、列表可隐藏展示 |
| 短驳车次 | 表字段预留，页面不提供 |
| 车辆/司机完整管理页 | 本期不做；仅车次内「选择 + 快速新增」最小主数据（D1） |
| 费用项目录维护 | 不建目录表；收入四项为代码级只读行（INC01–INC04），成本/代收/代付类型存 `sys_fee_type`（D4，见 6.8） |
| 运单毛利公式 | 毛利以**运单**为最小单位，车次毛利只做 Σ 已配载票；公式为结构化勾选（收入加项、成本减项、返款可开关），**不开放脚本编辑器**；配置存 `sys_config.gross.formula`，只影响之后的车次，已落库不回溯（4.4、6.11） |
| 分摊异常 | `车次毛利 = Σ票毛利 = 收入合计 − 返款合计 − 成本合计` 任一不等即标「分摊异常」并提示，不阻断业务单据；对账接口见 7.1（37） |
| 毛利公式试算样例 | 用固定样例运单/样例车次演示，不取真实业务数据；样例收入为 0 时拒绝保存公式（除零保护） |
| 配载后运单改单/作废 | 允许，但入口在运单管理/详情，不在配载页；同一事务刷新车次数据；取消右栏操作列；禁止拆/合（D6） |
| 超载/超容 | 只提醒不阻断，确认后可继续配载 |
| 分摊基数合计为 0 | 阻断保存，提示补重量/体积（D14） |
| 取消卸车 / 取消到站 | **本期支持**（D17）：取消卸车回退到站状态并退回库存，要求未被下游配载、未签收/核销；取消到站仅未卸车可用；两项到站费用（卸车费/其他费）保留金额标记失效。见 5.2/5.3/6.7/6.10/8.4/8.6 |
| 到站费用（卸车费/其他费）适用范围 | 仅干线 + 末端网点卸车、在信息条下方内联区录入；短驳、分拨中心卸车不出现 |
| 大车直送（`delivery_method=3`） | 查询层排除：不进末端到站/卸车清单、不推进末端状态、不分摊两项到站费用；状态停在 80 分拨发车，后续由「直送签收」模块推进（本期占位，见 5.3） |
| 打印模板自定义 | 本期固定模板，不支持自定义列 |

---

## 11. 决策落点对照（D1–D17 已全部拍板）

| 编号 | 决策结论 | 落在本文档章节 |
|---|---|---|
| 费用口径 | 现付/到付/回付/月结运费为**收入项**，取 `biz_waybill` 四字段按票汇总，不参与分摊、不落 `biz_trip_fee`；成本项为车次录入的现付/回付/到站付运输费 + 现付油卡费（参与毛利）；删除旧七项收入项；**现返/欠返保留**（成本、不参与毛利、运单级取数，返款仍按 `rebate_total` 扣一次）；费用类型配置统一四类列表 | 0（第 5/8 条）、3.1（四类收入汇总列）、3.3、3.4、3.8、4.1、4.4、6.4 ⑤（Tab2 费用区）、6.8、7.2 |
| 到站卸车页 + 到站费用（卸车费/其他费） | 末端网点按 luohuo 页面设计执行到站/卸车；两项费用在信息条下方内联区一次录入（不再弹窗），各落 `biz_trip_fee(trip_dest_id)`，同事务重算并写日志 | 1.1、2（⑤）、3.2、3.4、3.6、4.1、4.2、5.4、5.6、6.10、7.4、8.6、9.5、10、12（V28/V30/V34） |
| 大车直送末端排除（v1.2-r5） | `delivery_method=3` 不进末端卸车清单（查询排除）、不做末端状态推进（停在 80）、不分摊两项到站费用；子车次仅剩大车直送时允许 0 票完成卸车并写日志；后续由直送签收模块推进 | 0（第 10 条）、1.1、4.2、5.2、5.3、5.4、5.6、6.10（第 3/4/5/7/8/9/12 点）、7.4（33–36）、8.6、10 |
| 配载页布局（v1.3） | 两个 Tab：Tab1 装车配载（双栏穿梭 + 双栏列设置/表头筛选）、Tab2 发车（车辆/费用内联 + 汇总 + 发车）；子车次/日志为顶部弹窗/抽屉；穿梭与拆单只落库；到站卸车仍独立页 | 6.4（全节）、6.5、6.7、7.1（6–9、8a/8b）、7.5、8.4、8.5、10 |
| 配载资格校验位置（v1.2-r4） | 资格条件全部在待配载池查询一次做足，池只返回可配单元；穿梭（右移/左移）只落库，零资格校验，仅并发/状态瞬变/车次状态异常才失败；超载超容只提醒不阻断 | 6.4 ⑥、7.1（6/8/9）、8.4、9.2/9.3、10 |
| **D1** 车辆/司机主数据 | 本期建 `sys_vehicle` / `sys_driver` 最小主数据，车次内「选择 + 快速新增」；完整管理页后续 | 3.10、6.3、6.4 ⑤（Tab2）、7.1（24/25）、8.2、9.7、12（V32） |
| **D2** 分摊方式 | **全车共用一个分摊方式**，存 `biz_trip.alloc_method`，费用区只一个下拉（v1.3 位于 Tab2 费用区） | 3.1、3.4、3.5、4.2、6.4 ⑤、7.1 |
| **D3** 重泡比 | 两级：`sys_config` 全局默认 + `sys_route.volume_weight_ratio` 线路覆盖；空/0 回落全局；按票所属线路取系数，车次不存单一系数 | 3.11、3.12、4.3、6.9、12（V33） |
| **D4** 费用类型配置 | 总部全局、不建目录表；**收入/成本/代收/代付统一一张列表**，收入四项为只读虚拟行，16 项种子为可编辑成本项 | 3.8、4.1、6.8、7.2、9.6 |
| **D5** 数据权限 | 批准受控放开，仅「车次列表/详情」与「待配载池」两个专用查询，强制显式条件 | 9.1、9.2、9.3 |
| **D6** 配载后运单变更 | 允许改单、允许作废，禁止拆合单；同事务刷新配载快照/分摊/汇总/装载率/毛利并写车次日志；作废自动解除配载回池；超载超容只提醒；**v1.3 起入口移至运单管理，配载页右栏无操作列** | 2、5.7、6.4 ②、8.3、8.5 |
| **D7** 活跃唯一 | 不加 `active_flag`：行锁 + 条件更新 + 服务层校验；**按配载单元唯一，同一母单不同子单可同时活跃在不同车次；拆单锁母单行** | 3.3（索引与唯一性）、8.4 |
| **D8** 列表行操作 | v1.3：车次列表行操作=配载/详情、发车、关闭、取消发车；到货卸车列表行操作=查看、到站、卸车、取消到站（按状态隐藏，见 6.10 规则表）；取消卸车为运单级不入列表；无独立作业页按钮 | 6.2 |
| **D9** 状态映射 | 按建议：分拨中心 50/60、末端 90/100/110/120；`inventory_time` 取卸车完成时间；**子单独立流转，母单余额保持余额库存态** | 5.3、5.7、8.4 |
| **D10** 目的网点 | 新增不受限（含发车后，新子车次置在途）；修改/删除需该网点无配载单元 | 5.4、5.5、6.3、6.4 ④、7.1（21–23） |
| **D11** 运单列表 | 本期不加「配载状态」默认列；列表仍只显示母单，详情子单清单增配载信息（8.7） | 6.2、8.3、8.7 |
| **D12** 按钮权限 | 出 Flyway 迁移种子（与 V11 一致） | 12（V34） |
| **D13** 关闭车次 | 仅发车前（10/20）可关闭 | 5.4 |
| **D14** 分摊基数为 0 | 阻断保存并提示补重量/体积 | 4.2、6.4 ⑤ |
| **D15** 配载页两 Tab + 双栏列设置/筛选 | Tab1 装车配载 / Tab2 发车，可来回切换；两栏独立 pageKey、支持单/多模式与默认查询值、配置持久化后端；子车次/日志为顶部入口，四抽屉取消或合并 | 6.4、6.5、7.1（6/7）、7.5、10 |
| **D16** 拆单装车（子单 + 按件数金额拆分） | 仅未配载整票可拆；复用现有子单模型与编号；金额按件数比例、尾差归份额大的一方；重量/体积按件数折算；母单余额回池、剩余 0 退出池；分摊/卸车/打印/末端卸车费按子单 | 3.3、4.5、5.7、6.4 ②、7.1（8a/8b）、8.4、8.5、8.7、12 |
| **D17** 到站/卸车回退 | 到货卸车列表行操作提供到站/取消到站/卸车（取消卸车为运单级，仅在作业页「已卸车」Tab 勾选后执行）；未到站点卸车先弹到站确认再进卸车页；作业页操作条随 Tab 切换（未卸车仅「卸车(N)」、已卸车仅「取消卸车」）；取消到站/取消卸车按 5.2/5.3 回退，库存按 8.4 退回上一站，费用保留金额标记失效；权限=本站操作人或调度 | 5.2、5.3、5.4、6.2、6.7、6.10、7.1（15a/16a）、7.4、8.4、8.6、9.5a、10 |

> 与旧编号的对应：v1.1 第 11 节的 D1–D14 编号保持不变，v1.3 追加 D15（两 Tab + 双栏列设置/筛选）、D16（拆单子单 + 金额按件数）与 D17（到站/卸车回退）；本节只把「待拍板」替换为「结论 + 落点」。仍属实现细节、未单列决策的点（超载提醒的具体样式、改单后路由不匹配的提示文案）按上述章节的建议口径执行。

---

## 12. 数据库迁移清单（仅清单，未执行、未建文件）

> 以下全部待第 2 步原型确认、第 3 步开发批准后才产出并执行；当前 Flyway 最新为 `V26`，新脚本从 `V27` 接续。

| 版本 | 内容 | 风险 |
|---|---|---|
| V27 | `biz_waybill` 新增 5 列；索引在原 `idx_current_hub (current_hub_id, load_status, status)`、`idx_inventory_time` 基础上，**v1.3 仅追加 `idx_parent_deleted (parent_id, deleted)`**（子单汇总/余额计算）。**拆单不新增份额字段**，剩余量实时计算 | 中高：改现有大表；加列可 INSTANT，但**新增索引需 online DDL（ALGORITHM=INPLACE, LOCK=NONE）**，需低峰执行并评估锁表时间 |
| V28 | 新建 `biz_trip`、`biz_trip_dest`、`biz_trip_no_seq`（`biz_trip` 含 `alloc_method`、四类运费收入汇总列、成本项字段，不含 `volume_weight_ratio`；`biz_trip_dest` 含 `arrival_unload_fee`/`arrival_other_fee` 两项到站费用快照与 `arrival_unload_fee_valid`/`arrival_other_fee_valid` 有效性标记） | 低：全新表 |
| V29 | 新建 `biz_trip_waybill`、`biz_trip_waybill_fee`（`biz_trip_waybill` 含 `parent_no/is_child/split_ratio` 与 `idx_parent_no`；`waybill_id` 为配载单元 ID） | 低：全新表；`parent_no` 为冗余快照，打印分组用 |
| V30 | 新建 `biz_trip_fee`（含 `trip_dest_id`，0=车次级、>0=子车次；`fee_status`/`invalid_reason`；唯一键 `(trip_id, trip_dest_id, fee_code)`）、`biz_trip_log`（动作类型含 13/14/15/16/17） | 低 |
| V31 | 新建 `sys_fee_type` + **统一列表种子 16 项**（14 项车次成本项 + 现返/欠返 2 项运单级成本项；七项旧收入项不落库；**收入四项 INC01–INC04 为代码只读行，不占种子**）；索引含 `idx_category_sort (fee_category, sort_order)` | 低；风险：`fee_category` 由「仅 2=成本」扩为 1–4，需同步校验与前端枚举，旧数据仍为 2 |
| V32 | 新建 `sys_vehicle`、`sys_driver`（D1） | 低 |
| V33 | 新建 `sys_config` + 种子两键：`fee.volume_weight_ratio=333`（全局重泡比）、`gross.formula`（运单毛利公式 JSON，version=1，默认收入四项加、成本项按参与毛利减、返款扣减）；`sys_route` 新增 `volume_weight_ratio` 列 | 中：`sys_route` 为现有表，加可空列，INSTANT；`config_value` 用 TEXT 存 JSON |
| V34 | 装车配载按钮权限种子（D12），含「到站卸车」「取消到站」「取消卸车」按钮 | 低：只动 1301 子权限 |
| — | 数据权限豁免登记：`biz_trip_no_seq`、`sys_fee_type`、`sys_config` | 需评审，改代码常量 |

**拆单（v1.3）迁移与接口影响**

1. **不新增表、不新增份额字段**：复用 `biz_waybill` 的 `parent_id/parent_no/is_child/child_seq`；剩余件数/重量/体积 = 原件数 − Σ有效子单，实时计算；`biz_trip_waybill` 只加 `parent_no/is_child/split_ratio` 快照列（新表，低风险）。
2. **子单号流水**：沿用母单下「当前有效子单最小未占用序号」，作废后回收复用；上限 99，达到后拆单接口拒绝并提示「子单已达 99 张，请走合单或新建运单」。
3. **唯一键冲突**：V13 的 `uk_pre_order_no` 不允许子单复制母单预订单号，拆单时子单 `pre_order_no` 必须置空；否则插入失败。
4. **旧契约冲突（高风险）**：operation v1.14 约定「子单费用固定为 0、费用挂母单」；v1.3 改为子单携带按件数拆分的金额快照。**客户结算/开票/返款仍挂母单**，但运单模块文档、子单创建逻辑、打印与对账取数需同步修订，否则两套口径并存。
5. **母单状态语义变更（中风险）**：余额 > 0 时母单 `status` 保持 20/60（不再是「纯汇总壳」存储值），汇总状态改为前端/查询实时计算；运单列表/详情的状态展示与统计需同步改，避免把母单当成普通运单统计在途。
6. **跨模块事务（中高风险）**：拆单涉及 operation（生成子单/写日志）与 transport（配载/分摊/汇总）。必须同事务或明确补偿，**禁止出现「子单已生成但未配载」的孤儿数据**；建议由 transport 服务编排、调用 operation 的拆单方法并共用事务。
7. **数据权限**：子单 `dept_id` 继承母单；配载池/拆单接口按车次始发网点数据权限校验，不允许跨机构拆单。
8. **打印/扫码（后续）**：子单标签与扫码装车需运营侧配合，本期只保证打印清单显示子单号与母单号，不改标签模板。
9. **存量数据**：现有 `biz_waybill` 子单数据（若有）需校验 `child_seq` 唯一与 `parent_id` 有效；上线前跑一次一致性核对，不做数据回填。

**到站卸车页/到站费用（卸车费 + 其他费）的迁移影响说明**：不新增表；V28 在 `biz_trip_dest` 加 2 个金额列（`arrival_unload_fee`、`arrival_other_fee`，默认 0）与 2 个有效性列（`arrival_unload_fee_valid`、`arrival_other_fee_valid`，默认 1），V30 在 `biz_trip_fee` 加 `trip_dest_id`（默认 0）、`fee_status`（默认 1）、`invalid_reason`，并把唯一键扩为 `(trip_id, trip_dest_id, fee_code)`（两项费用各占一行，靠 `fee_code` 区分），V34 增加 3 个按钮权限（到站卸车、取消到站、取消卸车）。均为新增列/新增行，不回改既有数据；V30 与 V28 同批上线，避免唯一键与子车次引用不一致。

**到站/卸车回退（v1.3）迁移影响**：不新增表；V28 增 `arrival_unload_fee_valid`/`arrival_other_fee_valid`，V30 增 `biz_trip_fee.fee_status/invalid_reason` 并在日志枚举补 16/17（枚举为代码常量，无需改表）；无存量回填。风险：① 取消卸车需回退库存，依赖 `biz_trip_waybill.route_node_seq` 与车次始发网点正确，上线前需核对配载明细；② 与下一车次配载、签收核销存在并发，必须锁运单行 + 条件更新，否则可能双写；③ 已签收运单误取消会破坏对账，前置校验必须硬拦截；④ 取消后两项费用失效但金额保留，报表与对账取数需按 `fee_status=1` 过滤，避免重复计成本。

**大车直送排除（v1.2-r5）无迁移影响**：`biz_waybill.delivery_method` 已在 V13 存在（1=送货，2=自提，3=大车直送），本次只改查询与分摊条件，不新增表/列、不改索引，V27–V34 清单不变。

所有脚本必须先出待审稿、通过临时空库语法校验、经家伟批准后才执行；执行走应用启动 Flyway，禁止手工连库跑 SQL。

---

## 13. 下一步

1. 家伟审阅 **v1.3**（不覆盖 v1.2）；本轮重点核对：
   - 6.4 两 Tab 结构、右栏无操作列、双栏列设置/表头筛选（独立 pageKey + 默认查询值持久化）；
   - 4.5 拆单金额**按件数**拆分、尾差规则与示例；
   - 5.7 母单余额状态与汇总展示、不回退规则；
   - 7.1 接口 8a/8b、8.4 可配单元与防超配、8.7 运单列表/详情展示建议；
   - 6.8 费用类型统一列表（收入 4 只读 + 成本 16 可编辑、四类新增规则）；
   - 6.11 运单毛利公式配置（单票公式、加/减项勾选、单票试算、车次 Σ 对账、sys_config 存储与变更日志、校验边界）；
   - 4.4/5.6/8.6 分摊闭合（尾差最后一票吸收）与 `gross-check` 对账口径；
   - 6.2/6.7/6.10 到站、取消到站、卸车、取消卸车（未到站先确认再进卸车页；作业页工具条固定四项操作）；
   - 5.2/5.3/5.4/8.4/8.6 状态回退、库存回退、两项到站费用（卸车费/其他费）失效与日志 16/17。
2. 原型已完成 v1.3 静态版（`prototype/index.html` + `assets/*.js`）：Tab1 双栏 + 双栏列设置/表头筛选 + **拆单装车弹窗（已实现，含件数=1 禁用）**、Tab2 车辆/费用内联（**全车分摊方式唯一入口**）+ 汇总 + 发车、顶部子车次/日志弹窗；到货卸车列表独立菜单页（到站/取消到站/卸车/取消卸车，未发车仅「查看」只读态）；卸车页未卸车/已卸车 Tab + 内联费用区（含只读到站付运输费）；费用类型统一列表；**运单毛利计算公式页（单票试算 + 车次 Σ 对账 + 变更日志）**。实测结论见附录 A.3。
3. 原型确认后再进入开发；数据库变更按第 12 节 V27–V34 逐版本报批执行。
4. 未确认前不写业务代码、不建 Flyway 文件、不执行任何 DDL。

---

## 附录 A · 页面清单（与静态原型一一对应）

> 原型目录：`2026-11-25/transport-loading/prototype/index.html`（v1.3，纯 HTML/CSS/JS，file:// 可直接打开）。「新增」= v1.3 新做页面/弹窗；「改造」= v1.2 已有、v1.3 调整。

### A.1 页面

| # | 页面名 | 路径 / 路由 | 入口 | 覆盖章节 | 本次 |
|---|---|---|---|---|---|
| P1 | 车次列表（装车配载） | `/transport/loading` | 菜单：运输管理 → 装车配载 | 6.2 | 改造（行操作收窄：仅配载/详情、发车、关闭、取消发车） |
| P2 | 车次配载页（Tab1 装车配载 / Tab2 发车） | `/transport/loading/detail?id=` | P1 行「配载/详情」；创建车次保存后自动跳转 | 6.4 / 6.5 | 改造（两 Tab 新增） |
| P3 | 到货卸车列表 | `/transport/loading/unload-list` | 菜单：运输管理 → 到站卸车 | 6.10 | 新增（行操作按状态：查看 / 到站 / 卸车 / 取消到站；无独立作业页按钮；取消卸车不在此页） |
| P4 | 到站卸车作业页（含内联费用区与只读态） | `/transport/loading/unload?tripId=&destId=` | P3 行「查看」（只读）/「到站」「卸车」 | 6.10 | 改造（内联费用区 + 只读态新增；未卸车 Tab 仅「卸车(N)」、已卸车 Tab 仅「取消卸车」） |
| P5 | 费用类型配置 | `/system/config` → 财务配置 Tab | 菜单：系统设置 → 财务配置 | 6.8 | 改造（统一列表） |
| P6 | 系统配置 · 全局重泡比 | `/system/config` → 基础配置 | 菜单：系统设置 → 系统配置 | 6.9 | 改造（既有页面，本原型未复刻） |
| P7 | 路由管理 · 重泡比列 | `/system/route` | 菜单：系统设置 → 路由管理 | 6.9 | 改造（既有页面，本原型未复刻） |
| P8 | 运单列表 / 运单详情（子单配载信息） | 既有运单模块路由 | 运单管理菜单 | 8.7 | 改造（仅建议，非本模块页面） |
| P9 | 运单毛利计算公式 | `/system/config` → 运单毛利计算公式 | 菜单：系统设置 → 运单毛利公式 | 6.11 / 4.4 | 新增（单票试算 + 车次对账 + 变更日志） |

### A.2 抽屉 / 弹窗 / 内联区

| # | 名称 | 形态 | 触发入口 | 覆盖章节 | 本次 |
|---|---|---|---|---|---|
| D1 | 创建 / 修改车次 | 抽屉 720px | P1「创建车次/修改车次」 | 6.3 | 改造（保存后跳 P2） |
| D2 | 拆单装车 | 弹窗 560px | P2 Tab1 待配载行「拆单」/ 勾选 1 行后「拆单装车」 | 4.5 / 6.4 | 新增（本轮原型已实现；件数=1 禁用） |
| D3 | 子车次列表 | 弹窗 | P2 顶部「子车次」 | 6.4 | 改造 |
| D4 | 操作日志 | 弹窗/抽屉 | P2 顶部「操作日志」 | 6.4 | 改造 |
| D5 | 发车确认 | 弹窗 520px | P2 Tab2「发车」；P1 行「发车」 | 6.5 | 改造 |
| D6 | 取消发车 | 弹窗 + 原因 | P1 工具条「取消发车」 | 6.6 | 改造 |
| D7 | 到站确认 | 弹窗 430px | P3 行「到站」「卸车」、P4 工具条 | 6.7 / 6.10 | 改造 |
| D8 | 选择子车次（多目的网点） | 弹窗 | 多目的网点执行到站/取消到站/卸车 | 6.2 / 6.7 | 改造（原型 P4 已带子车次；独立选择弹窗待开发） |
| D9 | 取消到站 | 弹窗 430px + 原因 | P3 行「取消到站」（P1 不提供） | 6.7 | 新增 |
| D10 | 卸车确认 | 弹窗（勾选清单） | P4 未卸车 Tab「卸车(N)」 | 6.7 / 6.10 | 改造 |
| D11 | 取消卸车 | 弹窗 430px + 原因 | P3 行「取消卸车」、P4 工具条 | 6.7 / 6.10 | 新增/改造 |
| D12 | 到站费用内联区（卸车费/其他费/只读到站付运输费） | 页面内联卡片 | P4 信息条下方 | 6.10 / 4.2 / 8.6 | 新增（替代原弹窗） |
| D13 | 费用类型新增 / 编辑 | 弹窗 | P5「新增费用类型」/行「编辑」 | 6.8 | 改造 |
| D14 | 列设置 | 抽屉/弹窗 | P1 列表、P2 双栏（P3 列表待开发） | 6.2 / 6.4 | 改造（双栏独立 pageKey） |
| D15 | 运单子单清单（含配载信息） | 既有弹窗 | 运单列表「子单(n)」 | 8.7 | 改造（建议） |

### A.3 原型自测结论（2026-11-25 实测）

- 环境：macOS + 系统 Chrome 158（Playwright 驱动），`file://` 直开原型；视口 1280×900 与 1100×900。
- 全链路（1280，22 张）：P1 车次列表 → 创建车次（目的网点=长沙分拨中心/株洲网点）→ 保存后自动进入 P2 Tab1 → 勾选 2 票穿梭右移 → 改车次名称并保存 →「下一步」切 Tab2（无弹窗）→ 录车辆/费用（含到站付运输费 300）→ 发车确认 → P3 到货卸车列表 → 行「卸车」（未到站先弹到站确认）→ 确认后进入 P4 作业页 → 断言只读「到站付运输费=300.00」→ 录入 860/120 实时合计 ¥980.00 → 保存 → 完成卸车 → 已卸车 Tab → 取消卸车（填原因）→ 子车次回退 30、运单回未卸车、两项费用保留并失效。
- 补充场景（本轮新增截图）：P1 行操作仅「配载/详情、发车、关闭、取消发车」；P3 行操作仅「查看/到站/卸车/取消到站」（无取消卸车）；作业页未卸车 Tab 仅「卸车(N)」、已卸车 Tab 仅「取消卸车」；P3 未发车行仅「查看」并进只读作业页；Tab1 拆单弹窗（560）→ 确认后子单进已配载、母单余额回池；费用类型统一列表（四类混排、收入只读、代收/代付参与毛利强制否）；运单毛利公式页（单票试算 收入 ¥1,760.00 − 返款 ¥52.80 − 分摊成本 ¥1,381.29 = ¥325.91；车次 Σ票毛利 ¥1,894.00 与 收入 ¥10,200.00 − 返款 ¥306.00 − 成本 ¥8,000.00 对账差额 ¥0.00）。
- 视口与溢出：两档共 45 张截图（1280 31 张 / 1100 14 张），逐页 `scrollWidth - clientWidth` 全部为 **0**；到货卸车列表操作列右侧固定，1100 下仍可直接点「查看/到站/卸车/取消到站」；作业页 `1280-P4-未卸车Tab.png`（仅「卸车(0)」+ 已选）、`1280-P4-已卸车Tab.png`（仅「取消卸车」+ 勾选后可用）；费用类型统一列表（1280/1100）、运单毛利公式页（1280/1100）、拆单弹窗与拆后双栏（1280）、1100 拆单/拆后均无横向溢出。
- 控制台断言：`console.error` **0 条**，`pageerror` **0 条**，脚本业务断言 0 失败。
- 截图目录：`outputs/screenshots-v1.3/`；步骤、断言与逐张溢出数值见 `prototype/prototype-test-report.md`。
