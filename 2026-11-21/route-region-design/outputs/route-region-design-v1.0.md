# 路由管理 + 区域管理 + 承运商管理 · 设计方案终稿 v1.0

> 状态：**仅设计。未写业务代码、未建表、未执行任何 DDL、未修改任何现有文件。**
> 基线：v0.1（2026-11-21）。本版按家伟 7 条裁决收敛，所有结论以本版为准。
> 核查依据：luohuo 源码逐项核对（文件与行号见下）+ yunshu 现有库结构/代码/菜单只读核查。

---

## 0. 版本差异（v0.1 → v1.0）

| # | 家伟裁决 | v1.0 落地方式 | 对 v0.1 的改动 |
|---|---|---|---|
| 1 | Q1 承运商：参考 luohuo 增加承运商管理 | 新增 `sys_carrier` 表 + 独立页面（菜单 1107 已有），字段与交互对齐 luohuo | v0.1 的 Q1 待确认项关闭，从"可选"变为"必做" |
| 2 | Q2 始发/到达网点：下拉可选且必填 | 两者均必填，下拉仅可选启用状态的网点/分拨中心 | 关闭 Q2；唯一性校验可依赖非空业务键 |
| 3 | Q3 时效单位小时、里程单位 km | 里程 `DECIMAL(10,2)` km；时效 `DECIMAL(10,2)` 小时，均 2 位小数 | 明确字段类型与精度 |
| 4 | Q9 省市区县必填范围 | 分拨中心、网点（dept_type=4/5）必填；总部/公司/部门选填 | 关闭 Q9，写入抽屉与后端校验 |
| 5 | Q10 区划放 MySQL，基础搭扎实 | `sys_region`（code 主键 / parent_code 索引 / level）+ `sys_region_sync_log` + 离线 JSON 幂等导入；先三级 | 关闭 Q10；导入方式由"建议"升为"主路径" |
| 6 | 路由不存到达省/市快照 | 路由表**删除** `arrival_province/city_*` 四列；机构表只存 3 个区划 code；列表/筛选/导出按到达网点 join 机构与区划实时取 | 删除 v0.1 中 4 个冗余列；取消"按机构重算"入口 |
| 7 | 区域页：离线导入为主，在线同步可选且需降级 | 页面主操作=离线 JSON 导入；在线更新作为次级按钮，默认关闭，失败即提示改走离线，不写半成品 | 重写区域页方案 |

其它结构性修正（v0.1 未暴露的问题）：
- **数据权限锚点与业务网点分离**：外转/直达表的"适用网点"是业务字段，不能复用 `dept_id`。v1.0 拆为 `apply_dept_id`（适用网点）+ `dept_id`（数据权限锚点，由 fill 自动填充）。
- **机构表不冗余区划名称**：仅存 `province_code/city_code/district_code`，名称统一 join `sys_region`，避免双写漂移（与"不做快照"的裁决同源）。

---

# 第一部分 · 核查事实（只读）

## 1.1 luohuo 路由管理（外转/直达）

| 项 | 事实 | 位置 |
|---|---|---|
| 表名 | `sys_route`，直送/外转同一张表 | `backend/app/models/route.py:11-39`、`backend/database/migrate_route_module.sql:75-98` |
| 字段 | `id, route_code, route_name, route_type(1直送/2外转), region_code, region_level(2市/3区县/4乡级), region_full_name, carrier_id, status, sort_order, remark, org_id, is_deleted, create_by, create_time, update_by, update_time` | 同上 |
| 索引 | `idx_region(region_code)`、`idx_type(route_type)`、`idx_carrier(carrier_id)`、`idx_status(status)`；目的地唯一由应用层 upsert 保证，**不加 DB 唯一索引** | `migrate_route_module.sql:93-98` |
| 编码 | 自动生成 `区划编码_ZT`（直送）/`区划编码_WZ`（外转） | `backend/app/crud/route.py:23-30` |
| 名称 | 默认区划全路径，可修改 | `crud/route.py:267-327`、`schemas/route.py:45-52` |
| 唯一规则 | 「适用网点 + 目的地」唯一；已存在则更新类型/编码/名称/层级/承运商 | `crud/route.py:267-327` |
| 承运商 | 仅外转可挂；切直送自动清空；支持批量挂承运商 | `api/v1/system/route.py:253-302, 345-352` |
| 权限 | 仅总部可维护 | `api/v1/system/route.py:55-56` |
| 页面 | 11 列、表头内嵌筛选、新增/编辑弹窗 | `frontend/src/views/system/route/index.vue:361-415, 273-308` |

## 1.2 luohuo 区域管理

| 项 | 事实 | 位置 |
|---|---|---|
| 表 | `sys_region`，主键 `code`；`name, parent_code, level(1省/2市/3区县/4乡级), full_name, source, version, sync_time` | `backend/app/models/region.py:10-38`、`migrate_route_module.sql:13-26` |
| 索引 | `idx_parent(parent_code)`、`idx_name(name)`、`idx_level(level)` | 同上 |
| 数据源 | 在线 CDN `modood/Administrative-divisions-of-China` 的 provinces/cities/areas/streets.json | `backend/app/config.py:57-64` |
| 页面 | **同步控制台**：版本卡 + 在线更新 + 进度 + 同步日志 + 差异报告四 Tab，不做区划增删改 | `frontend/src/views/system/region/index.vue:1-160` |
| 日志表 | `sys_region_sync_log(job_id,status,progress,新增/改动/待删/路由迁移,report_json)` | `migrate_route_module.sql:31-47` |
| 接口 | `/regions/children` 懒加载、`/regions/search`、`/regions/sync` 异步、`/sync/{job}/report` | `api/v1/system/region.py:280-479` |

## 1.3 luohuo 承运商（本版新增核查）

| 项 | 事实 | 位置 |
|---|---|---|
| 表 | `sys_carrier：id, carrier_code(32), carrier_name(64), contact(32), phone(20), status, sort_order, remark(500), org_id, is_deleted, create_by/create_time/update_by/update_time` | `backend/database/migrate_route_module.sql:52-70`、`backend/app/models/carrier.py` |
| 索引 | `idx_name(carrier_name)`、`idx_status(status)`；**无唯一索引** | `migrate_route_module.sql:68-70` |
| 校验 | 编码 2–32、名称 2–64、联系人 1–32、电话 1–20，联系人与电话**必填**；编码重复未做后端校验 | `backend/app/schemas/carrier.py:14-30`、`crud/carrier.py:234-241` |
| 归属 | `org_id` 必填，默认取顶栏当前机构；数据范围按 org_id 过滤 | `schemas/carrier.py:24-27`、`crud/carrier.py:19-27` |
| 页面 | 10 列（编码/名称/联系人/电话/归属机构/状态/排序/修改人/修改时间/备注）+ 序号 + 操作；表头内嵌筛选；新增/编辑弹窗 560px；操作=编辑/停用启用/删除 | `frontend/src/views/system/carrier/index.vue:20-110` |
| 下拉 | `CarrierOption(id, carrier_code, carrier_name, org_id, org_name)` 供路由选择 | `schemas/carrier.py:88-99` |

## 1.4 luohuo 仓库/库区

- 全量检索 `库区/warehouse/storage_area/库房`：业务代码与模型**均不存在**，仅备份 SQL 命中字符串。
- 结论：luohuo 无同类概念。yunshu 按裁决用路由表内文本字段；未来升级主数据的迁移成本见第三部分第 1 条。

## 1.5 yunshu 现状（只读）

| 项 | 事实 |
|---|---|
| `sys_dept` | 24 列，仅 `address varchar(200)`，无省/市/区县；`dept_type`：1 总部 / 2 公司 / 3 部门 / 4 分拨中心 / 5 网点（`SysDept.java:29-30`） |
| 机构接口 | `GET /api/v1/system/depts/options?type=` 已存在，目前只支持单一类型（`SysDeptController.java:75-81`） |
| 机构数据 | **全库仅 1 条：总部（id=1，address 为 NULL）**，历史回填量极小 |
| 菜单 | 1104 路由管理、1107 承运商管理、1109 区划管理均已存在；表与页面均未建 |
| 承运商/区划 | 无 `sys_carrier`、无 `sys_region`、无数据 |
| 数据权限 | 黑名单式；豁免清单 9 张（`DataScopeTables.java:13-23`）；`DataScopeMetaObjectHandler` 对豁免表直接跳过填充（`DataScopeMetaObjectHandler.java:25-27,45-47`） |

---

# 第二部分 · 方案终稿

## 2.1 表拆分结论与两个 Tab 职责

**结论：分两张表，统一页面两个 Tab。**

| Tab | 表 | 职责 |
|---|---|---|
| 路由管理 | `sys_route` | 短驳/干线：网点↔网点、网点↔分拨、分拨↔分拨；含库区、里程、时效、联系人、客服 |
| 外转/直达 | `sys_route_transfer` | 适用网点 + 目的地行政区划配置的直达/外转路由与默认承运商，对齐 luohuo |

理由：
1. 字段集互斥：短驳/干线有始发/到达机构、库区、里程、时效、四类联系人；外转/直达有目的地区划、粒度、承运商。合表将产生 10+ 长期 NULL 列。
2. 唯一性口径不同：前者「类型+始发+到达」，后者「适用网点+目的地」。
3. 后端拆表对前端无感，两个 Tab 各自独立演进。

承运商独立成表 `sys_carrier`，被外转/直达以 `carrier_id` 逻辑引用。

## 2.2 承运商管理（对齐 luohuo）

**定位**：网点可维护的业务主数据；参与数据权限过滤（`dept_id` = 归属机构）。

页面：菜单 1107 已存在，直接补 `system/carrier/index`。

### 列表列（12 列）

| # | 列 | 最小宽 | 对齐 | 筛选 | 排序 | 导出 |
|---|---|---|---|---|---|---|
| 1 | 序号 | 80 | 居中 | — | 否 | 否 |
| 2 | 承运商编码 | 140 | 左 | 文本 | 是 | 是 |
| 3 | 承运商名称 | 160 | 左 | 文本 | 是 | 是 |
| 4 | 联系人 | 120 | 左 | 文本 | 是 | 是 |
| 5 | 联系电话 | 140 | 左 | 文本 | 是 | 是 |
| 6 | 归属机构 | 140 | 左 | 机构树 | 是 | 是 |
| 7 | 状态 | 100 | 居中 | 启用/停用 | 是 | 是 |
| 8 | 排序 | 110 | 居中 | 数值 | 是 | 是 |
| 9 | 修改人 | 120 | 左 | 文本 | 是 | 是 |
| 10 | 修改时间 | 200 | 居中 | 日期区间 | 是 | 是 |
| 11 | 备注 | 150 | 左 | 文本 | 是 | 是 |
| 12 | 操作 | 200 | 居中 | — | 否 | 否 |

操作：编辑 / 停用·启用 / 删除（逻辑删除，被外转路由引用时拦截，见 R7）。

### 抽屉字段

| 字段 | 控件 | 必填 | 长度/校验 | 联动/默认 |
|---|---|---|---|---|
| 承运商编码 | 输入 | 是 | 2–32，字母数字中划线，机构内唯一（应用层） | 编辑可改 |
| 承运商名称 | 输入 | 是 | 2–64 | — |
| 联系人 | 输入 | 是 | 1–32 | — |
| 联系电话 | 输入 | 是 | 1–20；手机/座机格式 | — |
| 归属机构 | 只读 | 是 | 取当前顶栏机构 | 新增自动写入 `dept_id`；编辑不可改 |
| 排序 | 数字 | 否 | 0–9999，默认 0 | — |
| 状态 | 单选 | 是 | 启用/停用，默认启用 | — |
| 备注 | 文本域 | 否 | ≤500 | — |

## 2.3 区划数据与区域管理页

### 2.3.1 数据来源与体量

- 三级（省/市/区县）约 **3,200 行**：34 省级 + 333 地级 + 2,843 县级。
- 二级（乡级/街道）约 **40,000+ 行**，二期补充，表结构与导入逻辑不变。
- 编码：省/市/区县用 GB/T 2260 六位（如 440000 / 440100 / 440111）；乡级用统计局 9/12 位，`VARCHAR(12)` 兼容。
- 数据放 MySQL，不塞 Flyway；初始数据以离线 JSON 导入。

### 2.3.2 离线 JSON 导入（主路径）

格式（嵌套或扁平均可，入库统一扁平）：

```json
[
  { "code": "440000", "name": "广东省", "parentCode": "0", "level": 1 },
  { "code": "440100", "name": "广州市", "parentCode": "440000", "level": 2 },
  { "code": "440111", "name": "白云区", "parentCode": "440100", "level": 3 }
]
```

流程：
1. 选择本地 JSON（≤10MB，三级约 300KB）→ 上传。
2. **预检**（不落库）：结构校验、编码/层级/父子链校验、统计新增/改动/无变化/待停用，错误行返回前 20 条。
3. 确认导入：按 `code` 幂等 UPSERT（存在则更新 name/parent_code/level/source/version/sync_time，不存在则插入），**同一文件重复导入 added=0、changed=0**。
4. `full_name` 由服务端按 parent 链计算，不信任文件值。
5. 分批 500 条提交，任务记录 `partial/failed`，重跑幂等。
6. 文件缺失的既有编码：不自动停用，只进报告"待停用"，人工确认后置 `status=0`；被路由/机构引用时禁止停用并显示引用数。

### 2.3.3 在线同步（可选，需降级）

- 默认关闭，开关 `yunshu.region.online-sync.enabled=false`；仅手动点击触发，不做启动同步与定时任务。
- 连接超时 5s / 读取 10s，失败即任务置 `failed`，提示"当前无外网或数据源不可达，请改用离线 JSON 导入"，**不写半成品数据**。
- 成功后走与离线导入相同的 UPSERT 与报告链路。

### 2.3.4 区域管理页面结构

| 区块 | 字段/元素 |
|---|---|
| 版本信息卡 | 当前版本、数据来源（offline/online）、最近同步时间、区划总数、三级/四级分布、状态 |
| 导入区（主） | 选择文件、格式说明下载、预检按钮、预检结果（新增/改动/无变化/待停用 + 错误行）、确认导入、进度条、结果报告 |
| 在线更新（次） | "检测远端版本"、"在线更新"；不可用时置灰并给出降级提示 |
| 同步日志表 | 任务ID、触发方式、触发人、文件名、开始时间、结束时间、状态、进度、新增、改动、无变化、待停用、报告、操作 |
| 报告弹窗 | 新增 / 改动 / 待停用 / 校验失败 四个 Tab，支持导出 |
| 区划查询 | 省→市→区县逐级树 + 关键词搜索，供机构/路由选择器复用 |
| 权限 | 仅总部可导入/同步/停用 |

页面不做区划手工增删改（编码被路由与机构引用，手工改会破坏引用）。

## 2.4 机构省市区县方案

- `sys_dept` 新增 3 列：`province_code/city_code/district_code`（均 `VARCHAR(12)`，引用 `sys_region.code`）。
- **不冗余名称**：列表、路由、导出统一 join `sys_region` 取名，避免区划改名后双写漂移。
- `address` 保留为详细地址（街道门牌），与区划字段并存。
- 必填：`dept_type=4/5`（分拨中心、网点）必填三级；1/2/3 选填但须完整（选了省必须选市、区县）且父子链匹配。
- 联动：省→市→区县三级下拉，仅 `status=1`；上级变更清空下级。
- 历史回填：现有仅总部 1 条且 address 为空，无需脚本回填；上线后由机构抽屉人工补录。
- 机构下拉接口扩展：`/depts/options` 增加 `types=4,5` 多类型参数，返回项补 `provinceCode/cityCode/districtCode + 三个 name`（服务端 join 后返回），供路由抽屉直接回显到达省/市。

## 2.5 Tab「路由管理」（短驳/干线）列表列（19 列）

| # | 列 | 最小宽 | 对齐 | 筛选 | 排序 | 导出 |
|---|---|---|---|---|---|---|
| 1 | 序号 | 80 | 居中 | — | 否 | 否 |
| 2 | 路由编码 | 140 | 左 | 文本 | 是 | 是 |
| 3 | 路由类型 | 100 | 居中 | 短驳/干线 | 是 | 是 |
| 4 | 始发网点 | 150 | 左 | 机构（4/5） | 是 | 是 |
| 5 | 到达网点 | 150 | 左 | 机构（4/5） | 是 | 是 |
| 6 | 线路名称 | 180 | 左 | 文本 | 是 | 是 |
| 7 | 到达省 | 100 | 左 | 省下拉 | 是（按 code） | 是 |
| 8 | 到达市 | 100 | 左 | 市下拉（随省联动） | 是（按 code） | 是 |
| 9 | 库区 | 120 | 左 | 文本 | 否 | 是 |
| 10 | 线路里程(km) | 110 | 右 | 数值区间 | 是 | 是 |
| 11 | 运输时效(h) | 110 | 右 | 数值区间 | 是 | 是 |
| 12 | 线路联系人 | 110 | 左 | 文本 | 否 | 是 |
| 13 | 联系电话 | 130 | 左 | 文本 | 否 | 是 |
| 14 | 线路客服 | 110 | 左 | 文本 | 否 | 是 |
| 15 | 客服电话 | 130 | 左 | 文本 | 否 | 是 |
| 16 | 状态 | 90 | 居中 | 启用/禁用 | 是 | 是 |
| 17 | 最后修改人 | 110 | 左 | 文本 | 是 | 是 |
| 18 | 最后修改时间 | 190 | 居中 | 日期区间 | 是 | 是 |
| 19 | 操作 | 200 | 居中 | — | 否 | 否 |

说明：到达省/市不落路由表，列表 SQL `sys_route r JOIN sys_dept d ON r.dest_dept_id=d.id LEFT JOIN sys_region p ON d.province_code=p.code LEFT JOIN sys_region c ON d.city_code=c.code`；筛选按 `d.province_code/d.city_code`；导出同口径。

## 2.6 Tab「路由管理」抽屉字段

| 字段 | 控件 | 必填 | 长度/校验 | 联动/默认 |
|---|---|---|---|---|
| 路由类型 `routeCategory` | 单选 | 是 | 1 短驳 / 2 干线，默认短驳 | 切换重算名称默认值与干线必填项 |
| 始发网点 `originDeptId` | 机构下拉（dept_type 4/5 且启用） | 是 | 不能与到达网点相同 | — |
| 到达网点 `destDeptId` | 机构下拉（4/5 且启用） | 是 | 同上 | 变更即刷新到达省/市 |
| 到达省/市 | 只读 | 自动 | 取所选机构 join 结果 | 机构无区划时提示"请先补全机构省市区县" |
| 线路名称 `routeName` | 输入 | 是 | 2–100 | 干线：未手工改过时自动生成「到达网点名+线路」；短驳默认「始发-到达 短驳」 |
| 库区 `warehouseArea` | 输入 | 干线必填，短驳选填 | ≤100 | 手工填写，不联表；打印标签直接取该值 |
| 线路里程 `mileage` | 数字 | 干线必填，短驳选填 | 0.01–999,999.99，2 位小数 | 单位 km |
| 运输时效 `transitHours` | 数字 | 干线必填，短驳选填 | 0.01–9,999.99，2 位小数 | 单位小时（跨天用 48 表示） |
| 线路联系人 | 输入 | 干线必填 | ≤50 | — |
| 线路联系电话 | 输入 | 干线必填 | ≤20；手机/座机 | — |
| 线路客服 | 输入 | 否 | ≤50 | — |
| 线路客服电话 | 输入 | 否 | ≤20；手机/座机 | — |
| 状态 | 单选 | 是 | 启用/禁用，默认启用 | — |
| 排序 | 数字 | 否 | 0–9999，默认 0 | — |
| 备注 | 文本域 | 否 | ≤500 | — |

## 2.7 Tab「外转/直达」列表列（13 列）与抽屉

### 列表

| # | 列 | 最小宽 | 对齐 | 筛选 | 排序 | 导出 |
|---|---|---|---|---|---|---|
| 1 | 序号 | 80 | 居中 | — | 否 | 否 |
| 2 | 路由编码 | 150 | 左 | 文本 | 是 | 是 |
| 3 | 路由名称 | 170 | 左 | 文本 | 是 | 是 |
| 4 | 目的地全路径 | 200 | 左 | 文本 | 否 | 是 |
| 5 | 级别 | 110 | 居中 | 市/区县/乡级 | 是 | 是 |
| 6 | 类型 | 100 | 居中 | 直达/外转 | 是 | 是 |
| 7 | 适用网点 | 140 | 左 | 机构（4/5） | 是 | 是 |
| 8 | 默认承运商 | 150 | 左 | 承运商下拉 | 是 | 是 |
| 9 | 状态 | 90 | 居中 | 启用/停用 | 是 | 是 |
| 10 | 排序 | 100 | 居中 | 数值 | 是 | 是 |
| 11 | 最后修改人 | 110 | 左 | 文本 | 是 | 是 |
| 12 | 最后修改时间 | 190 | 居中 | 日期区间 | 是 | 是 |
| 13 | 操作 | 160 | 居中 | — | 否 | 否 |

### 抽屉

| 字段 | 控件 | 必填 | 长度/校验 | 联动/默认 |
|---|---|---|---|---|
| 适用网点 `applyDeptId` | 机构下拉（4/5） | 是 | 新增可选、编辑只读 | 决定数据归属展示 |
| 目的地 `regionCode` | 区划级联/搜索选择 | 是 | 仅 status=1；层级由选择深度决定 | 选中后回填全路径与 level |
| 路由编码 | 只读 | 自动 | `区划编码_ZT`（直达）/`_WZ`（外转） | 类型切换时重算后缀 |
| 路由名称 `routeName` | 输入 | 是 | 2–128 | 默认区划全路径；未手工改过时随目的地刷新 |
| 路由类型 `transferType` | 单选 | 是 | 1 直达 / 2 外转，默认外转 | 切直达时清空承运商并置灰 |
| 默认承运商 `carrierId` | 承运商下拉（启用） | 外转必填 | — | 仅外转可填 |
| 状态 | 单选 | 是 | 启用/停用，默认启用 | — |
| 排序 | 数字 | 否 | 0–9999，默认 0 | — |
| 备注 | 文本域 | 否 | ≤500 | — |

## 2.8 名称生成、唯一性与编码规则

- **线路名称生成时机**（干线）：① 到达网点选择/变更时，若用户未手工编辑过（dirty=false）立即生成「到达网点名+线路」；② 保存时名称为空兜底生成一次。
- **名称去重**：不做全局唯一；重名仅前端提示不阻断。真正唯一性在业务键。
- **唯一性**：
  - `sys_route`：「路由类型 + 始发网点 + 到达网点」在 `deleted=0` 内唯一，**应用层校验**，DB 不建唯一索引。
  - `sys_route_transfer`：「适用网点 + 目的地编码」在 `deleted=0` 内唯一（与 luohuo 一致，同一目的地只能二选一：直达或外转），应用层校验。
  - 理由：均存在逻辑删除，DB 唯一索引会导致删除后无法重建；luohuo 同样只用应用层保证。
- **路由编码**：干线 `GX` + 6 位流水，短驳 `DB` + 6 位流水；外转/直达 `区划编码_ZT/_WZ`。创建后不可修改；`uk_route_code` 兜底，冲突重试一次。

## 2.9 字段类型与精度（Q3）

| 字段 | 类型 | 范围（前端校验） | 理由 |
|---|---|---|---|
| `mileage` | `DECIMAL(10,2)` | 0.01–999,999.99 km | 2 位小数满足公里精度；整型留足余量，避免后期扩容 |
| `transit_hours` | `DECIMAL(10,2)` | 0.01–9,999.99 小时 | 小时制统一口径；0.5 小时等小数可表达；跨天用 48/72 |
| `warehouse_area` | `VARCHAR(100)` | ≤100 | 手工文本，打印标签直接取值 |
| 联系方式 | `VARCHAR(20)` | 手机/座机 | 与 luohuo 一致 |

---

# 第三部分 · 最终 DDL 草案（不执行）

> 版本规划：**V7** = 区划表 + 同步日志 + 机构增列；**V8** = 承运商 + 路由两表。
> 字符集/排序规则与现有表一致：`utf8mb4 / utf8mb4_0900_ai_ci`。

## V7-1 `sys_region`

```sql
CREATE TABLE `sys_region` (
  `code`        VARCHAR(12)  NOT NULL COMMENT '行政区划编码（主键）：省市县为 GB/T 2260 六位，乡级为统计局 9/12 位',
  `name`        VARCHAR(64)  NOT NULL COMMENT '区划名称（本级）',
  `parent_code` VARCHAR(12)  NOT NULL DEFAULT '0' COMMENT '上级区划编码；省级固定为 0',
  `level`       TINYINT UNSIGNED NOT NULL COMMENT '层级：1=省 2=市 3=区县 4=乡级',
  `full_name`   VARCHAR(200) NOT NULL DEFAULT '' COMMENT '全路径名称，如 广东省广州市白云区；服务端按父子链计算',
  `source`      VARCHAR(16)  NOT NULL DEFAULT 'offline' COMMENT '数据来源：offline=离线JSON online=在线同步 init=初始化',
  `version`     VARCHAR(12)  NOT NULL DEFAULT '' COMMENT '数据版本，如 2025Q4 或 YYYYMM',
  `sync_time`   DATETIME     DEFAULT NULL COMMENT '最近一次导入/同步时间',
  `status`      TINYINT(1)   NOT NULL DEFAULT 1 COMMENT '状态：1=有效 0=停用（历史区划保留，不物理删除）',
  `create_time` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`code`),
  KEY `idx_parent_code` (`parent_code`),
  KEY `idx_name` (`name`),
  KEY `idx_level` (`level`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci
  COMMENT='行政区划表：省/市/区县/乡级基础数据，平台级共享';
```

索引/约束理由：
- `code` 主键：区划编码天然唯一且稳定，直接作为业务外键，避免自增 id 与编码双主键。
- `idx_parent_code`：逐级懒加载 `WHERE parent_code=? AND status=1` 的必需索引。
- `idx_name`：关键词搜索。
- `idx_level`：机构/路由按层级出下拉（如只取省级）。
- 不建唯一约束之外的组合索引：三级仅 3,200 行、四级 4 万行，单表扫描成本可接受。

## V7-2 `sys_region_sync_log`

```sql
CREATE TABLE `sys_region_sync_log` (
  `id`                   BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
  `job_id`               VARCHAR(32)  NOT NULL COMMENT '任务ID（UUID），用于幂等与报告查询',
  `trigger_type`         VARCHAR(16)  NOT NULL COMMENT '触发方式：offline=离线JSON online=在线同步',
  `triggered_by`         BIGINT UNSIGNED DEFAULT NULL COMMENT '触发人用户ID',
  `file_name`            VARCHAR(200) DEFAULT NULL COMMENT '离线导入文件名（在线同步为空）',
  `data_version`         VARCHAR(12)  DEFAULT NULL COMMENT '本次导入的数据版本',
  `started_at`           DATETIME     NOT NULL COMMENT '开始时间',
  `finished_at`          DATETIME     DEFAULT NULL COMMENT '结束时间',
  `status`               VARCHAR(16)  NOT NULL COMMENT '状态：pending/running/success/failed/partial',
  `progress`             INT          NOT NULL DEFAULT 0 COMMENT '进度百分比 0-100',
  `total_count`          INT          NOT NULL DEFAULT 0 COMMENT '文件/远端总条数',
  `added_count`          INT          NOT NULL DEFAULT 0 COMMENT '新增条数',
  `changed_count`        INT          NOT NULL DEFAULT 0 COMMENT '变更条数',
  `unchanged_count`      INT          NOT NULL DEFAULT 0 COMMENT '无变化条数',
  `disabled_count`       INT          NOT NULL DEFAULT 0 COMMENT '待停用（文件中缺失）条数',
  `report_json`          MEDIUMTEXT   DEFAULT NULL COMMENT '差异明细 JSON：新增/改动/待停用/校验失败',
  `error_msg`            VARCHAR(500) DEFAULT NULL COMMENT '失败原因',
  `create_time`          DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_job_id` (`job_id`),
  KEY `idx_started_at` (`started_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci
  COMMENT='区划导入/同步任务日志：记录过程、差异与可追溯报告';
```

索引/约束理由：
- `uk_job_id`：任务 ID 唯一，防重复创建与重复写报告。
- `idx_started_at`：日志列表默认按开始时间倒序分页。
- `total_count/unchanged_count/disabled_count` 是对 luohuo 日志字段的补齐，用于完整对账（导入条数必须自洽）。

## V7-3 `sys_dept` 增列

```sql
ALTER TABLE `sys_dept`
  ADD COLUMN `province_code` VARCHAR(12) DEFAULT NULL COMMENT '省编码，引用 sys_region.code（level=1）',
  ADD COLUMN `city_code`     VARCHAR(12) DEFAULT NULL COMMENT '市编码，引用 sys_region.code（level=2）',
  ADD COLUMN `district_code` VARCHAR(12) DEFAULT NULL COMMENT '区县编码，引用 sys_region.code（level=3）',
  ADD KEY `idx_region` (`province_code`, `city_code`, `district_code`);
```

索引/约束理由：
- 只存 code 不存名称：区划改名时无需双写同步，展示统一 join。
- `idx_region` 组合索引：支撑机构按省/市筛选与"某省下所有网点"的反查；最左前缀 `province_code` 也可单独用。
- 不加物理外键：与现有代码风格一致（逻辑引用），且区划导入允许用户态晚于机构录入。

## V8-1 `sys_carrier`

```sql
CREATE TABLE `sys_carrier` (
  `id`           BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
  `carrier_code` VARCHAR(32)  NOT NULL COMMENT '承运商编码，机构内唯一（应用层校验）',
  `carrier_name` VARCHAR(64)  NOT NULL COMMENT '承运商名称',
  `contact`      VARCHAR(32)  NOT NULL COMMENT '联系人',
  `phone`        VARCHAR(20)  NOT NULL COMMENT '联系电话',
  `status`       TINYINT(1)   NOT NULL DEFAULT 1 COMMENT '状态：1=启用 0=停用',
  `sort_order`   INT          NOT NULL DEFAULT 0 COMMENT '排序号，升序',
  `remark`       VARCHAR(500) DEFAULT NULL COMMENT '备注',
  `dept_id`      BIGINT UNSIGNED NOT NULL COMMENT '归属机构ID：数据权限锚点，新增自动填充',
  `create_by`    BIGINT UNSIGNED DEFAULT NULL COMMENT '创建人用户ID',
  `create_time`  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_by`    BIGINT UNSIGNED DEFAULT NULL COMMENT '最后修改人用户ID',
  `update_time`  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '最后修改时间',
  `deleted`      TINYINT(1)   NOT NULL DEFAULT 0 COMMENT '逻辑删除：0=未删除 1=已删除',
  PRIMARY KEY (`id`),
  KEY `idx_code` (`carrier_code`),
  KEY `idx_name` (`carrier_name`),
  KEY `idx_status` (`status`),
  KEY `idx_dept` (`dept_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci
  COMMENT='承运商表：外转/直达路由的默认承运商主数据';
```

索引/约束理由：
- 不建 DB 唯一索引：逻辑删除后同编码无法重建；luohuo 同样只用应用层保证。应用层校验「`dept_id + carrier_code` 且 `deleted=0`」唯一。
- `idx_code/idx_name`：列表筛选与路由端下拉搜索。
- `idx_dept`：数据权限过滤与机构下拉。

## V8-2 `sys_route`（短驳/干线）

```sql
CREATE TABLE `sys_route` (
  `id`              BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
  `route_code`      VARCHAR(32)  NOT NULL COMMENT '路由编码：短驳 DB/干线 GX + 6 位流水，创建后不可改',
  `route_category`  TINYINT UNSIGNED NOT NULL COMMENT '路由类型：1=短驳 2=干线',
  `origin_dept_id`  BIGINT UNSIGNED NOT NULL COMMENT '始发网点ID，引用 sys_dept.id；仅网点/分拨中心',
  `dest_dept_id`    BIGINT UNSIGNED NOT NULL COMMENT '到达网点ID，引用 sys_dept.id；仅网点/分拨中心',
  `route_name`      VARCHAR(100) NOT NULL COMMENT '线路名称：干线默认“到达网点名+线路”，允许修改',
  `warehouse_area`  VARCHAR(100) DEFAULT NULL COMMENT '库区：干线必填，手工填写；打印标签直接取该值',
  `mileage`         DECIMAL(10,2) DEFAULT NULL COMMENT '线路里程（公里），2 位小数；干线必填',
  `transit_hours`   DECIMAL(10,2) DEFAULT NULL COMMENT '运输时效（小时），2 位小数；干线必填',
  `contact_name`    VARCHAR(50)  DEFAULT NULL COMMENT '线路联系人；干线必填',
  `contact_phone`   VARCHAR(20)  DEFAULT NULL COMMENT '线路联系电话；干线必填',
  `service_name`    VARCHAR(50)  DEFAULT NULL COMMENT '线路客服姓名；选填',
  `service_phone`   VARCHAR(20)  DEFAULT NULL COMMENT '线路客服电话；选填',
  `status`          TINYINT(1)   NOT NULL DEFAULT 1 COMMENT '状态：1=启用 0=禁用',
  `sort_order`      INT          NOT NULL DEFAULT 0 COMMENT '排序号，升序',
  `remark`          VARCHAR(500) DEFAULT NULL COMMENT '备注',
  `dept_id`         BIGINT UNSIGNED NOT NULL COMMENT '数据权限归属机构ID，新增时自动填充',
  `create_by`       BIGINT UNSIGNED DEFAULT NULL COMMENT '创建人用户ID',
  `create_time`     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_by`       BIGINT UNSIGNED DEFAULT NULL COMMENT '最后修改人用户ID',
  `update_time`     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '最后修改时间',
  `deleted`         TINYINT(1)   NOT NULL DEFAULT 0 COMMENT '逻辑删除：0=未删除 1=已删除',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_route_code` (`route_code`),
  KEY `idx_category_status` (`route_category`, `status`),
  KEY `idx_origin` (`origin_dept_id`),
  KEY `idx_dest` (`dest_dept_id`),
  KEY `idx_name` (`route_name`),
  KEY `idx_dept` (`dept_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci
  COMMENT='短驳/干线路由表：网点与分拨中心之间的运输线路及线路属性';
```

索引/约束理由：
- `uk_route_code`：编码由流水生成、永不复用，唯一索引安全且能兜底并发重号。
- `idx_category_status`：Tab 默认"按类型 + 启用状态"过滤。
- `idx_origin/idx_dest`：始发/到达网点筛选与"某网点所有线路"查询。
- `idx_name`：名称模糊查询。
- `idx_dept`：数据权限过滤。
- 业务唯一「类型+始发+到达」不建 DB 唯一索引（逻辑删除冲突），改应用层校验。
- **不含到达省/市列**：按裁决由 `dest_dept_id` join 实时取；`idx_dest` 同时支撑该 join。

## V8-3 `sys_route_transfer`（外转/直达）

```sql
CREATE TABLE `sys_route_transfer` (
  `id`               BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
  `route_code`       VARCHAR(32)  NOT NULL COMMENT '路由编码：区划编码_ZT（直达）/_WZ（外转），自动生成',
  `route_name`       VARCHAR(128) NOT NULL COMMENT '路由名称：默认区划全路径，可修改',
  `transfer_type`    TINYINT UNSIGNED NOT NULL COMMENT '类型：1=直达 2=外转',
  `apply_dept_id`    BIGINT UNSIGNED NOT NULL COMMENT '适用网点ID，引用 sys_dept.id；业务字段',
  `region_code`      VARCHAR(12)  NOT NULL COMMENT '目的地行政区划编码，引用 sys_region.code',
  `region_level`     TINYINT UNSIGNED NOT NULL COMMENT '目的地粒度：2=市 3=区县 4=乡级',
  `region_full_name` VARCHAR(200) NOT NULL COMMENT '目的地全路径名称（导入时固定，展示用）',
  `carrier_id`       BIGINT UNSIGNED DEFAULT NULL COMMENT '默认承运商ID；仅外转可填',
  `status`           TINYINT(1)   NOT NULL DEFAULT 1 COMMENT '状态：1=启用 0=停用',
  `sort_order`       INT          NOT NULL DEFAULT 0 COMMENT '排序号，升序',
  `remark`           VARCHAR(500) DEFAULT NULL COMMENT '备注',
  `dept_id`          BIGINT UNSIGNED NOT NULL COMMENT '数据权限归属机构ID，新增时自动填充',
  `create_by`        BIGINT UNSIGNED DEFAULT NULL COMMENT '创建人用户ID',
  `create_time`      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_by`        BIGINT UNSIGNED DEFAULT NULL COMMENT '最后修改人用户ID',
  `update_time`      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '最后修改时间',
  `deleted`          TINYINT(1)   NOT NULL DEFAULT 0 COMMENT '逻辑删除：0=未删除 1=已删除',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_route_code` (`route_code`),
  KEY `idx_apply_dept` (`apply_dept_id`),
  KEY `idx_region` (`region_code`),
  KEY `idx_type_status` (`transfer_type`, `status`),
  KEY `idx_carrier` (`carrier_id`),
  KEY `idx_dept` (`dept_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci
  COMMENT='外转/直达路由表：适用网点 + 目的地行政区划配置，对齐 luohuo';
```

索引/约束理由：
- `uk_route_code`：编码由区划+后缀生成，唯一兜底。
- `idx_apply_dept`：按适用网点筛选/展示。
- `idx_region`：按目的地查询、区划变更影响面统计。
- `idx_type_status`：Tab 内类型与启停筛选。
- `idx_carrier`：承运商删除前的引用检查。
- `idx_dept`：数据权限。
- 「适用网点+目的地」唯一由应用层保证，DB 不建唯一索引（同上，逻辑删除冲突）。
- `apply_dept_id` 与 `dept_id` 分离：前者是"这条路由给哪个网点用"，后者是"数据权限锚点"，复用会导致网点维护他网点路由时权限错乱。

## 区划初始数据导入

- 不写入 Flyway；上线后由区域管理页导入三级 JSON（约 3,200 行）。
- 导入顺序：省→市→区县（或先全量插入再补 parent 链；实现时按 level 升序分批即可）。
- 幂等：`INSERT ... ON DUPLICATE KEY UPDATE`，按 `code` 更新，重复导入不新增。
- 乡级二期：同一套表与导入逻辑，文件加 level=4 记录即可，无需改表。

---

# 第四部分 · 数据权限衔接

| 表 | 定位 | 是否豁免 | dept_id / create_by 处理 |
|---|---|---|---|
| `sys_region` | 平台基础数据，全机构共享 | **豁免**（加入 `EXEMPT_TABLES`） | 不带 `dept_id`；仅 `create_time/update_time` 审计（同 `sys_menu` 模式） |
| `sys_region_sync_log` | 平台任务日志 | **豁免** | 不带 `dept_id`；`triggered_by` 记录触发人 |
| `sys_carrier` | 业务主数据（网点可维护） | **不豁免** | `dept_id` NOT NULL + `create_by/update_by`，实体加 `@TableField(fill=...)` |
| `sys_route` | 业务配置 | **不豁免** | `dept_id` NOT NULL + `create_by/update_by` + fill |
| `sys_route_transfer` | 业务配置 | **不豁免** | `dept_id`（权限锚点）+ `create_by/update_by` + fill；适用网点另存 `apply_dept_id` |

关键点：
1. **豁免表不会触发填充**：`DataScopeMetaObjectHandler.isExempt` 直接 return（`DataScopeMetaObjectHandler.java:25-27,45-47`）。区划导入任务写日志时，`triggered_by` 必须由服务层显式赋值。
2. 被豁免的两张表加入 `DataScopeTables.EXEMPT_TABLES` 属**代码变更**，本轮不做；实施时需评审（清单注释要求任何变更需评审）。
3. 路由/承运商的 `dept_id` 由 fill 取当前账号主机构；无主机构时 fill 抛 `DataScopeException`（fail-closed），与现有规范一致。
4. 读侧：路由/承运商列表、导出、统计全部走同一拦截器，导出不得绕过；区划不受过滤。
5. 跨机构可见性：总部可看全部；网点只看本机构及授权范围，符合黑名单模式默认行为。

---

# 第五部分 · 对现有模块的影响面

| 层 | 影响 |
|---|---|
| 数据库 | V7：新增 `sys_region`、`sys_region_sync_log`，`sys_dept` 加 3 列 + 1 索引；V8：新增 `sys_carrier`、`sys_route`、`sys_route_transfer` |
| 后端-机构 | `SysDept` 加 3 字段；`DeptSaveRequest/DeptVO/DeptQuery` 扩展；保存校验（4/5 必填、父子链合法）；列表/导出 join 区划；`/depts/options` 支持 `types=4,5` 并返回区划 code+name |
| 后端-新模块 | 区划（查询/树/导入/日志）、承运商 CRUD、路由 CRUD（两套）+ 编码生成 + 名称/唯一校验 |
| 前端-机构页 | 抽屉地址区改为"省/市/区县三级联动 + 详细地址"（`DeptFormDrawer.vue` 现为单行文本）；列表新增「所在地区」列（建议合并省市区县，最小宽 180）；筛选新增省/市；导出列同步；列设置增加开关 |
| 前端-新页面 | 路由管理（两 Tab）、承运商管理、区域管理（同步控制台）；新增区划级联选择器组件，供机构/路由复用 |
| 菜单 | 1104/1107/1109 已存在，无需新增菜单；不新增按钮权限点（沿用现状） |
| 数据权限 | 豁免清单加 2 张平台表；3 张业务表按规范带 `dept_id/create_by` |
| 既有测试 | 机构相关用例需补省市区县必填与筛选；数据权限探针不需新增表（现有拦截器行为不变） |

---

# 第六部分 · 剩余待确认项（每条给建议）

| # | 问题 | 建议 | 理由 |
|---|---|---|---|
| R1 | 机构表是否额外冗余省市区县名称 | **不冗余**，统一 join `sys_region` | 与"不做快照"同源；避免区划改名后双写漂移；3,200 行 join 成本可忽略 |
| R2 | 离线 JSON 是否接受嵌套结构 | **嵌套/扁平都接受**，服务端统一扁平化并按父子链算 `full_name` | 兼容 luohuo 数据源（嵌套），同时便于手工整理 |
| R3 | 文件中缺失的既有区划如何处理 | **只报告"待停用"，不自动停用**；被机构/路由引用时禁止停用 | 自动停用会让历史单据显示空白；需人工确认 |
| R4 | 在线同步是否保留 | **保留为次级按钮，默认关闭**；失败提示改走离线，不写半成品 | 满足无外网环境的稳定交付；不引入定时任务 |
| R5 | 路由编码生成方式 | **事务内取同前缀最大流水 +1，`uk_route_code` 兜底重试一次** | 当前数据量与并发低，不为编码单独建序列表 |
| R6 | 短驳路由是否也必填里程/时效/联系人 | **选填**；干线必填 | 短驳距离短、时效意义弱，强制必填会增加录入负担 |
| R7 | 承运商被外转路由引用时能否删除/停用 | **禁止删除**；停用需提示"引用 N 条外转路由"并二次确认 | 删除会导致路由承运商悬空；停用后历史路由仍可展示 |
| R8 | 区域导入/同步/停用的操作权限 | **仅总部** | 与 luohuo 一致；区划变更影响全平台 |

---

# 第七部分 · 文件清单与执行声明

本轮新增：
- `2026-11-21/route-region-design/outputs/route-region-design-v1.0.md`（本文件）

保留未改：
- `2026-11-21/route-region-design/outputs/route-region-design.md`（v0.1，历史留档）

已核查未改动：luohuo 源码（只读）、yunshu 源码与迁移脚本（只读）、数据库（仅 SELECT 统计）。

未执行：任何 DDL、建表、业务代码编写、现有文件修改。
