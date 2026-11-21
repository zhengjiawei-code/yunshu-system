# -*- coding: utf-8 -*-
"""重新生成模块树（已按 2026-11-20 家伟确认项更新）：
- 新增端归属：预开单=CLIENT（客户自助入口），其余=ADMIN（公司内部端）
- 客户管理/客户管理 → 客户档案
- Q2/Q3/Q5/Q7 写为已定结论
"""
import json, os
W = "/Users/apple/Desktop/君达文件/yunshu-system/2026-11-20/project-scaffold/work"

# (id, 一级目录, icon, [(id, 名称, 路由, 组件, 终端, 文档行, 端归属, 备注)])
data = [
 (1001,"系统管理","Setting",[
  (1101,"机构管理","/system/org","system/org/index","PC","R01","ADMIN","集团架构+机构授权（能否发货/到货、现付未付开单、到付未付签收）"),
  (1102,"用户管理","/system/user","system/user/index","PC","R02","ADMIN","同 luohuo；账户直接功能授权，一账户多机构"),
  (1103,"角色管理","/system/role","system/role/index","PC","R03","ADMIN","同 luohuo"),
  (1104,"路由管理","/system/route","system/route/index","PC","R04","ADMIN","干线路由/短驳路由/转出直达路由，多 Tab"),
  (1105,"菜单管理","/system/menu","system/menu/index","PC","R05","ADMIN","多级目录自定义维护；树形表例外，不分页/合计/导出"),
  (1106,"字典管理","/system/dict","system/dict/index","PC","R06","ADMIN","同 luohuo"),
  (1107,"承运商管理","/system/carrier","system/carrier/index","PC","R07","ADMIN","同 luohuo"),
  (1108,"系统配置","/system/config","system/config/index","PC","R08","ADMIN","改单/删单/打印等配置，多 Tab"),
  (1109,"区划管理","/system/region","system/region/index","PC","R09","ADMIN","同 luohuo"),
 ]),
 (1002,"运营管理","Tickets",[
  (1201,"预开单","/operation/pre-create","operation/preCreate/index","PC","R10","CLIENT","客户自助预开单入口（客户端），公司内部端菜单树不展示；客户只能提交/查看自己的预开单，不做揽收"),
  (1202,"开单揽收","/operation/create","operation/create/index","PC","R11","ADMIN","公司内部端。Tab1「接单」：接预开单→核对→确认揽收→转正式运单；Tab2「自开单」：开单员直接开单"),
  (1203,"运单导入","/operation/import","operation/import/index","PC","R12","ADMIN",""),
  (1204,"运单管理","/operation/waybill","operation/waybill/index","PC","R13","ADMIN",""),
  (1205,"删单明细","/operation/deleted","operation/deleted/index","PC","R14","ADMIN",""),
 ]),
 (1003,"运输管理","Van",[
  (1301,"装车配载","/transport/loading","transport/loading/index","PC,APP","R15","ADMIN","多点装车/多点卸车/拆零配载（一单多车）；本期只做 PC"),
 ]),
 (1004,"到货管理","Download",[
  (1401,"到货卸车","/arrival/unload","arrival/unload/index","PC,APP","R16","ADMIN","同 luohuo；本期只做 PC"),
 ]),
 (1005,"提货管理","TakeawayBox",[
  (1501,"客户提货","/pickup/customer","pickup/customer/index","PC,APP","R17","ADMIN","同 luohuo；本期只做 PC"),
 ]),
 (1006,"送货管理","Location",[
  (1601,"货物派送","/delivery/dispatch","delivery/dispatch/index","PC","R18","ADMIN","同 luohuo"),
 ]),
 (1007,"外转管理","Switch",[
  (1701,"货物转出","/transfer/out","transfer/out/index","PC","R19","ADMIN","同 luohuo"),
 ]),
 (1008,"签收管理","Finished",[
  (1801,"运单签收","/sign/waybill","sign/waybill/index","PC,APP","R20","ADMIN","同 luohuo；本期只做 PC"),
 ]),
 (1009,"运力管理","Van",[
  (1901,"司机管理","/capacity/driver","capacity/driver/index","PC","R21","ADMIN","司机基础信息维护"),
  (1902,"车辆管理","/capacity/vehicle","capacity/vehicle/index","PC","R22","ADMIN","车辆基础信息维护"),
  (1903,"车型管理","/capacity/vehicle-type","capacity/vehicleType/index","PC","R23","ADMIN","车型、车长维护"),
 ]),
 (1010,"客户管理","User",[
  (2001,"客户档案","/customer/archive","customer/archive/index","PC","R24","ADMIN","发货客户基础信息维护（原名与一级同名，已改为“客户档案”）"),
 ]),
 (1011,"异常管理","Warning",[
  (2101,"异常上报","/exception/report","exception/report/index","PC","R25","ADMIN",""),
  (2102,"异常处理","/exception/handle","exception/handle/index","PC","R26","ADMIN",""),
 ]),
 (1012,"回单管理","Document",[
  (2201,"回单寄出","/receipt/send","receipt/send/index","PC","R27","ADMIN","末端网点回寄回单"),
  (2202,"回单接收","/receipt/receive","receipt/receive/index","PC","R28","ADMIN","公司接收末端网点回寄的回单"),
  (2203,"回单发放","/receipt/issue","receipt/issue/index","PC","R29","ADMIN","公司向发货客户发放回单"),
 ]),
 (1013,"财务管理","Money",[
  (2301,"核销管理","/finance/verification","finance/verification/index","PC","R30","ADMIN","运单费用+车次费用核销，参考 luohuo"),
  (2302,"网点对账","/finance/branch-recon","finance/branchRecon/index","PC","R31","ADMIN","与末端网点对账结算，参考 luohuo"),
  (2303,"承运商对账","/finance/carrier-recon","finance/carrierRecon/index","PC","R32","ADMIN","与外转承运商对账结算，参考 luohuo"),
  (2304,"费用录入","/finance/fee-entry","finance/feeEntry/index","PC","R33","ADMIN","末端送货费、中转费快速录入"),
  (2305,"账单中心","/finance/bill-center","finance/billCenter/index","PC","R34","ADMIN","同 luohuo"),
  (2306,"收支方式","/finance/pay-method","finance/payMethod/index","PC","R35","ADMIN","同 luohuo"),
  (2307,"科目管理","/finance/fee-subject","finance/feeSubject/index","PC","R36","ADMIN","同 luohuo"),
  (2308,"账户管理","/finance/account","finance/account/index","PC","R37","ADMIN","网点预充值、提现、余额管理"),
 ]),
 (1014,"审核管理","DocumentChecked",[
  (2401,"删单审批","/audit/delete","audit/delete/index","PC","R38","ADMIN",""),
  (2402,"改单审批","/audit/modify","audit/modify/index","PC","R39","ADMIN",""),
  (2403,"取消签收审批","/audit/cancel-sign","audit/cancelSign/index","PC","R40","ADMIN",""),
 ]),
]

SLUG = {"系统管理":"system","运营管理":"operation","运输管理":"transport","到货管理":"arrival",
        "提货管理":"pickup","送货管理":"delivery","外转管理":"transfer","签收管理":"sign",
        "运力管理":"capacity","客户管理":"customer","异常管理":"exception","回单管理":"receipt",
        "财务管理":"finance","审核管理":"audit"}

# JSON
tree=[{"id":d[0],"name":d[1],"type":1,"icon":d[2],"path":"/"+SLUG[d[1]],"portal":"ADMIN",
       "children":[{"id":m[0],"name":m[1],"type":2,"path":m[2],"component":m[3],"terminal":m[4],
                   "source":m[5],"portal":m[6],"remark":m[7]} for m in d[3]]} for d in data]
open(os.path.join(W,"module-tree.json"),"w",encoding="utf-8").write(json.dumps(tree,ensure_ascii=False,indent=2))

# Markdown
lines=[]
lines.append("# 运输管理系统功能框架 · 模块树清单（v1.1）\n")
lines.append("> 来源：`运输管理系统功能框架.docx`（仅 1 张 4 列表格，40 个功能行，无正文段落）")
lines.append("> 解析口径：文档“一级”映射为目录（menu_type=1），“二级”映射为菜单（menu_type=2）；终端类型空白的按 PC 处理。")
lines.append("> 版本：v1.1（2026-11-20，按家伟确认更新：新增端归属、客户档案改名、D1-D5/Q3/Q5/Q7 定稿）。")
lines.append("> 统计：**14 个一级目录 + 40 个菜单 = 54 个节点**。公司内部端可见 **13 个目录 + 39 个菜单**；客户端仅 1 个入口（预开单）。\n")
lines.append("## 1. 完整模块树\n")
lines.append("| 层级 | 名称 | 类型 | 建议路由 | 建议组件（src/views/） | 终端 | 端归属 | 文档行 | 备注 |")
lines.append("|---|---|---|---|---|---|---|---|---|")
for d in data:
    lines.append(f"| L1 | **{d[1]}** | 目录 | /{SLUG[d[1]]} | Layout | PC | ADMIN | — | 一级模块 |")
    for m in d[3]:
        note = m[7] if m[7] else "—"
        lines.append(f"| L2 | {m[1]} | 菜单 | {m[2]} | {m[3]}.vue | {m[4]} | {m[6]} | {m[5]} | {note} |")

lines.append("\n## 2. 目录 / 菜单角色划分\n")
lines.append("- **目录（14）**：系统管理、运营管理、运输管理、到货管理、提货管理、送货管理、外转管理、签收管理、运力管理、客户管理、异常管理、回单管理、财务管理、审核管理。")
lines.append("- **菜单（40）**：文档二级全部为具体页面，无更深层级的功能点。")
lines.append("- 文档中的“多 Tab”（路由管理、系统配置、开单揽收）属于**页面内 Tab**，不拆成菜单层级。")
lines.append("- 文档没有出现三级功能点；但菜单模型必须支持任意层级（见《工程框架方案》第 5 节）。")
lines.append("- 首页 `/dashboard` 固定为 affix 首页，**不占菜单表**（D1 已定）。\n")

lines.append("## 3. 端归属（Q5 已定）\n")
lines.append("| 端 | 归属 | 说明 |")
lines.append("|---|---|---|")
lines.append("| 客户端（CLIENT） | 预开单 | 客户通过系统自助预开单；只能提交/查看自己的预开单，不做揽收与改单 |")
lines.append("| 公司内部端（ADMIN） | 其余 39 个菜单 | 含开单揽收：Tab「接单」处理客户预开单并确认揽收，Tab「自开单」由开单员直接开单 |")
lines.append("")
lines.append("约定：`portal_type = CLIENT` 的节点只在客户端菜单树下发，公司内部端菜单树**不展示**预开单；两端共用同一套菜单表，用字段区分，不复制菜单。\n")

lines.append("## 4. 终端分布（D3/Q7 已定：本期只做 PC）\n")
lines.append("| 终端 | 数量 | 页面 | 本期 |")
lines.append("|---|---|---|---|")
lines.append("| PC/APP | 4 | 装车配载、到货卸车、客户提货、运单签收 | 只做 PC，APP 字段保留不建端 |")
lines.append("| PC（文档明确） | 9 | 系统管理 9 个页面 | 做 |")
lines.append("| PC（文档空白，按 PC 处理） | 27 | 运营 5 + 送货 1 + 外转 1 + 运力 3 + 客户 1 + 异常 2 + 回单 3 + 财务 8 + 审核 3 | 做 |")

lines.append("\n## 5. 文档歧义 · 已定结论\n")
lines.append("| 编号 | 问题 | 结论（2026-11-20 家伟确认） |")
lines.append("|---|---|---|")
lines.append("| Q1/D5 | 一级模块只有 1 个二级页面时是否保留目录层 | **保留目录层**，便于后续扩展 |")
lines.append("| Q2/D3/Q7 | 终端类型空白、APP 是否本期建设 | **本期只做 PC**；APP 端不做，`terminal_type` 字段预留 |")
lines.append("| Q3 | 客户管理/客户管理同名 | 二级菜单显示名改为 **客户档案**，路由 `/customer/archive` |")
lines.append("| Q4 | 路由管理、系统配置的多 Tab | 一处单页面 + 内部 `el-tabs`，不拆菜单 |")
lines.append("| Q5 | 预开单与开单揽收边界 | **预开单=客户端自助入口；开单揽收=公司内部端**。开单揽收 Tab：接单（接预开单→核对→确认揽收→转正式运单）+ 自开单。两菜单不合并 |")
lines.append("| Q6 | “同 luohuo”页面 | 复用 luohuo 页面结构与交互，字段按运输业务重新核对 |")
lines.append("| D1 | 首页 | `/dashboard` 固定 affix 首页，**不占菜单表** |")
lines.append("| D2 | 按钮权限维护 | **保留** `menu_type=3`，菜单行可“新增按钮” |")
lines.append("| D4 | 菜单管理列表规范 | 树形表例外：**不做分页/合计/导出**，只保留筛选 + 刷新 |")
open(os.path.join(W,"module-tree.md"),"w",encoding="utf-8").write("\n".join(lines))

# SQL seed
sql=["-- 运输管理系统菜单种子（设计稿，勿直接执行）","-- 生成依据：module-tree.json；ID 段：目录 1001-1014，菜单 11xx-24xx",
     "-- portal_type: ADMIN=公司内部端，CLIENT=客户端（仅预开单）",""]
cols="(id,parent_id,menu_name,menu_type,route_path,component,perms,icon,portal_type,terminal_type,sort_order,visible,status,keep_alive,affix,remark)"
for d in data:
    sql.append(f"INSERT INTO sys_menu {cols} VALUES ({d[0]},0,'{d[1]}',1,'/{SLUG[d[1]]}',NULL,NULL,'{d[2]}','ADMIN','PC',{(data.index(d)+1)*10},1,1,0,0,NULL);")
    for i,m in enumerate(d[3],1):
        sql.append(f"INSERT INTO sys_menu {cols} VALUES ({m[0]},{d[0]},'{m[1]}',2,'{m[2]}','{m[3]}',NULL,NULL,'{m[6]}','{m[4]}',{i*10},1,1,1,0,NULL);")
open(os.path.join(W,"menu-seed.sql"),"w",encoding="utf-8").write("\n".join(sql))
print("ok", len(tree), sum(len(x[3]) for x in data))
