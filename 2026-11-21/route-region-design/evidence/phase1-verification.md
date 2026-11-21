# 阶段一验收证据（V7 + 区划导入 + 机构页改造）

> 日期：2026-11-21
> 范围：仅阶段一；阶段二（V8、承运商、路由两 Tab、授权、越权验证）未开始。
> 证据目录：本文件夹（截图 `org-*.png`、`region-*.png`，DDL 与 Flyway 文本）。

## 1. 备份与迁移

| 项 | 结果 |
|---|---|
| 迁移前全库备份 | `backups/yunshu_before_v7_20261121_114301.sql`（36K，11:43:01） |
| Flyway 校验 | 7 个迁移全部 success，版本 1→7 连续，无跳号（`flyway-history.txt`） |
| V7 执行 | `V7__init_region_and_dept_area.sql` success，`installed_on=2026-11-21 11:48:14` |
| 再次启动 | `Successfully validated 7 migrations` / `Current version of schema yunshu: 7` / `Schema is up to date` |

## 2. 结构核验（`v7-show-create.txt` / `v7-verify.txt`）

- 新表：`sys_region`、`sys_region_sync_log`、`sys_dept_apply_company` 均存在，字符集 `utf8mb4`、排序规则 `utf8mb4_0900_ai_ci`。
- `sys_dept_apply_company` 唯一键 `uk_leaf_company(leaf_dept_id, company_dept_id)`、普通索引 `idx_company` 存在。
- `sys_dept` 新增 `province_code`、`city_code`、`district_code`，均为 `varchar(12) NULL`；组合索引 `idx_region(province_code,city_code,district_code)` 存在。
- 既有数据：`sys_dept` 迁移后仍为 1 行（内置总部），未丢失；新表迁移后 0 行。
- 其它 schema：`luohuo_system` 42 张表、`luohuo_install_verify` 29 张表、`yunshu` 13 张表，均与迁移前一致。

## 3. 区划导入与幂等

| 日志 | 文件 | 总数 | 新增 | 修改 | 无变化 | 待停用 | 状态 |
|---|---|---|---|---|---|---|---|
| #1 | region-third-level.json | 3429 | 3429 | 0 | 0 | 0 | success |
| #2 | region-third-level.json | 3429 | 0 | 0 | 3429 | 0 | success（幂等复跑） |
| #3 | 页面导入同文件 | 3429 | 0 | 0 | 3429 | 0 | success（页面预检→导入） |

- 数据库 `select count(*) from sys_region` = **3429**；统计接口：总数 3429 / 省 31 / 市 342 / 区县 3056。
- 在线同步默认关闭：`POST /regions/sync-online` → `{"code":400,"message":"在线同步已关闭，请使用离线 JSON 导入"}`；页面「在线更新」按钮为禁用态并带明确提示。
- 页面预检结果：文件条数 3429、新增 0、修改 0、无变化 3429、待停用 0、校验失败 0；进度条完成并提示「已写入同步日志 #3」；日志表 2→3 行。
- 截图：`region-page.png`、`region-precheck.png`、`region-imported.png`、`region-final.png`。

## 4. 机构管理页改造

### 4.1 列对照

| 改造前（13 列） | 改造后（15 列） |
|---|---|
| 机构名称、上级机构、类型、机构性质、机构编码、负责人、联系电话、是否末端、状态、创建人、创建时间、最后修改人、最后修改时间 | 原 13 列 + **所在地区**（省/市表头筛选）+ **适用公司**（公司下拉筛选） |

- 列宽自适应、拖动、排序、列设置、分页沿用 `smart-table` 标准配置；新列已加入列设置与导出。
- 页面实测表头（Playwright 读取）：`机构名称 / 上级机构 全部 / 类型 全部 / 机构性质 全部 / 机构编码 / 负责人 / 联系电话 / 所在地区 省 市 / 是否末端 全部 / 适用公司 全部 / 状态 全部 / 创建人 / 创建时间 / 最后修改人 / 最后修改时间 / 操作`。

### 4.2 筛选 / 排序 / 导出（真实浏览器）

| 用例 | 结果 |
|---|---|
| 所在地区筛选省=四川省 | 3 行 → 2 行（仅四川省机构） |
| 适用公司筛选=UI分公司C | 2 行 → 1 行（仅末端网点） |
| 重置 | 恢复 3 行 |
| 所在地区升序 | C（四川省锦江区）→ 末端网点（四川省锦江区）→ D（无地区，排最后） |
| 所在地区降序 | D → C → 末端网点（无地区反向靠前） |
| 导出 | 文件名 `机构管理_20261121122809.csv`，表头含「所在地区」「适用公司」，3 行数据，含末端网点 |

截图：`org-list.png`、`org-filtered.png`、`org-sorted.png`。

### 4.3 抽屉（三级地区 + 适用公司）

- 编辑末端网点：**省/市/区县 3 个下拉** + 「详细地址」输入框同时存在；「适用公司」多选显示。
- 取消勾选「末端网点」→「适用公司」立即消失；重新勾选 → 重新出现（仅 `is_leaf=1` 可见可编辑）。
- 新增机构默认「部门」：三级地区存在，不出现「适用公司」。
- 截图：`org-drawer-leaf.png`、`org-drawer-create.png`。

## 5. 末端网点适用公司（接口级）

| 用例 | 结果 |
|---|---|
| 总部创建末端网点未选适用公司 | 400「请选择末端网点的适用公司」 |
| 总部创建末端网点选 [分公司A, 分公司B] | 成功，详情返回 `applyCompanyIds=[5,6]`、名称以「、」连接 |
| 分公司管理员（主机构=分公司A）未选适用公司创建末端网点 | 成功，自动落库 `leaf_dept_id=9, company_dept_id=5` |
| 适用公司改为 [B] | 关系仅剩 `6`；改回 [A,B] 关系为 `5,6`（无重复行） |
| 末端标记改为否 | 适用公司关系清空；恢复末端并重选后关系恢复 |
| 适用公司传总部 id=1 | 400「适用公司必须是有效的公司机构」 |
| 分拨中心未填地区 | 400「分拨中心/网点必须选择省、市、区县」 |

## 6. 数据权限豁免登记

| 位置 | 内容 |
|---|---|
| `backend/src/main/java/com/yunshu/common/datascope/DataScopeTables.java` | 豁免表新增 `sys_region`、`sys_region_sync_log`、`sys_dept_apply_company` |
| `2026-11-21/data-permission/outputs/data-permission-spec.md` | 豁免清单同步登记；新增「2.1 豁免表可见性责任」逐表注明：区划/日志平台共享、适用公司为跨公司关系由服务层显式计算，`create_by`/`triggered_by` 由服务层显式写入 |

回归：机构树/列表在 admin 与分公司账号下均正常返回（`sys_dept` 原有豁免不变），未因新增豁免发生过滤异常。

## 7. 测试数据清理对照

| 项 | 清理前 | 清理后 |
|---|---|---|
| `sys_dept` 测试行（ZZ验收_*/ZZUI_*） | 8 行（API 5 + UI 3） | **0** |
| `sys_dept` 总行数 | 9（含总部） | **1（仅总部）** |
| 末端适用公司关系 | 5 行 | **0** |
| 测试用户 `zz_test_branch` | 1 行（含角色绑定） | **0** |

- 删前备份：`backups/phase1_testdata_backup_20261121_122142.sql`、`backups/phase1_ui_testdata_backup_20261121_122858.sql`。
- 删除方式：先接口删除（同时验证服务层联动清理），逻辑删除行再物理清除测试行；残留复查为 0。

## 8. 构建与提交

- 后端：`mvn -DskipTests package` 成功；重启后 Flyway 校验通过。
- 前端：`vue-tsc --noEmit` 0 错误；`npm run build` 成功。
- 提交：见交付报告中的 commit 哈希与 origin 一致性核对。
