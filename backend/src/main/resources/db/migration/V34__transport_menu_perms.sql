-- ============================================================================
-- V34 · 菜单 / 按钮权限种子（方案 A：新增 1302 到站卸车，停用既有 1401 占位）
-- 对应方案：transport-loading-design-v1.3.md 6.1 / 6.2 / 6.10 / 6.11 / 第 12 节 V34
-- 说明：显式 ID + ON DUPLICATE KEY UPDATE，可重复执行
-- ============================================================================
SET NAMES utf8mb4;

-- 1) 运输管理 → 到站卸车（新增菜单）
INSERT INTO sys_menu (id,parent_id,menu_name,menu_type,route_path,component,redirect,perms,icon,terminal_type,sort_order,visible,status,keep_alive,affix,remark) VALUES
(1302,1003,'到站卸车',2,'/transport/loading/unload-list','transport/loading/unload-list/index',NULL,NULL,'Download','PC',20,1,1,1,0,'运输管理-到站卸车列表（v1.3）')
ON DUPLICATE KEY UPDATE
  parent_id=VALUES(parent_id),menu_name=VALUES(menu_name),menu_type=VALUES(menu_type),
  route_path=VALUES(route_path),component=VALUES(component),sort_order=VALUES(sort_order),
  visible=VALUES(visible),status=VALUES(status),remark=VALUES(remark),deleted=0;

-- 2) 停用既有「到货管理 → 到货卸车」占位菜单，避免与 1302 双入口（方案 A）
UPDATE `sys_menu`
SET `status` = 0, `visible` = 0, `remark` = CONCAT(IFNULL(`remark`, ''), '；v1.3 起由运输管理-到站卸车(1302)承接，本菜单停用')
WHERE `id` = 1401 AND `deleted` = 0;

-- 3) 装车配载页按钮权限（父级 1301）
INSERT INTO sys_menu (id,parent_id,menu_name,menu_type,route_path,component,redirect,perms,icon,terminal_type,sort_order,visible,status,keep_alive,affix,remark) VALUES
(3201,1301,'创建车次',3,NULL,NULL,NULL,'transport:loading:add',NULL,'PC',10,1,1,0,0,'按钮权限：车次列表-创建车次'),
(3202,1301,'修改车次',3,NULL,NULL,NULL,'transport:loading:edit',NULL,'PC',20,1,1,0,0,'按钮权限：车次列表-修改车次'),
(3203,1301,'删除车次',3,NULL,NULL,NULL,'transport:loading:delete',NULL,'PC',30,1,1,0,0,'按钮权限：车次列表-删除车次'),
(3204,1301,'取消发车',3,NULL,NULL,NULL,'transport:loading:cancel-depart',NULL,'PC',40,1,1,0,0,'按钮权限：车次列表-取消发车'),
(3205,1301,'打印运输清单',3,NULL,NULL,NULL,'transport:loading:print',NULL,'PC',50,1,1,0,0,'按钮权限：车次列表/配载页-打印'),
(3206,1301,'装车配载',3,NULL,NULL,NULL,'transport:loading:load',NULL,'PC',60,1,1,0,0,'按钮权限：配载页-穿梭/拆单/移除'),
(3207,1301,'发车',3,NULL,NULL,NULL,'transport:loading:depart',NULL,'PC',70,1,1,0,0,'按钮权限：配载页Tab2-发车'),
(3208,1301,'关闭车次',3,NULL,NULL,NULL,'transport:loading:close',NULL,'PC',80,1,1,0,0,'按钮权限：车次列表/配载页-关闭'),
(3209,1301,'目的网点变更',3,NULL,NULL,NULL,'transport:loading:dest',NULL,'PC',90,1,1,0,0,'按钮权限：配载页-目的网点增删改'),
(3210,1301,'车次到站',3,NULL,NULL,NULL,'transport:loading:arrive',NULL,'PC',100,1,1,0,0,'按钮权限：到货卸车列表-到站'),
(3211,1301,'车次卸车',3,NULL,NULL,NULL,'transport:loading:unload',NULL,'PC',110,1,1,0,0,'按钮权限：到货卸车列表/作业页-卸车'),
(3212,1301,'取消到站',3,NULL,NULL,NULL,'transport:loading:cancel-arrive',NULL,'PC',120,1,1,0,0,'按钮权限：到货卸车列表-取消到站'),
(3213,1301,'取消卸车',3,NULL,NULL,NULL,'transport:loading:cancel-unload',NULL,'PC',130,1,1,0,0,'按钮权限：作业页已卸车Tab-取消卸车（运单级）')
ON DUPLICATE KEY UPDATE
  parent_id=VALUES(parent_id),menu_name=VALUES(menu_name),menu_type=VALUES(menu_type),
  perms=VALUES(perms),sort_order=VALUES(sort_order),status=VALUES(status),remark=VALUES(remark),deleted=0;

-- 4) 系统配置按钮权限（父级 1108 系统配置）
INSERT INTO sys_menu (id,parent_id,menu_name,menu_type,route_path,component,redirect,perms,icon,terminal_type,sort_order,visible,status,keep_alive,affix,remark) VALUES
(3401,1108,'费用类型配置查看',3,NULL,NULL,NULL,'system:config:fee-type:view',NULL,'PC',10,1,1,0,0,'按钮权限：财务配置-费用类型列表'),
(3402,1108,'费用类型配置编辑',3,NULL,NULL,NULL,'system:config:fee-type:edit',NULL,'PC',20,1,1,0,0,'按钮权限：财务配置-新增/编辑/启停'),
(3403,1108,'运单毛利公式查看',3,NULL,NULL,NULL,'system:config:gross:view',NULL,'PC',30,1,1,0,0,'按钮权限：系统设置-运单毛利计算公式-查看'),
(3404,1108,'运单毛利公式保存',3,NULL,NULL,NULL,'system:config:gross:save',NULL,'PC',40,1,1,0,0,'按钮权限：系统设置-运单毛利计算公式-保存')
ON DUPLICATE KEY UPDATE
  parent_id=VALUES(parent_id),menu_name=VALUES(menu_name),menu_type=VALUES(menu_type),
  perms=VALUES(perms),sort_order=VALUES(sort_order),status=VALUES(status),remark=VALUES(remark),deleted=0;
