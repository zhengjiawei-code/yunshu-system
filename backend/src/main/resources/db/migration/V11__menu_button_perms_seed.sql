-- =====================================================================
-- 运输管理系统 · 按钮级权限种子（Flyway V11，随应用启动执行）
-- 内容：用户管理、菜单管理两个页面的按钮节点，共 8 条
-- 口径：sys_menu 表设计「权限标识格式为 模块:实体:动作，按钮必填且全局唯一」
--       与前端菜单管理种子按钮（mock/menuManage.ts）保持一致
-- 说明：仅补权限数据，不改变权限校验逻辑；重复执行按主键更新，保持幂等
-- =====================================================================

SET NAMES utf8mb4;

INSERT INTO sys_menu (id,parent_id,menu_name,menu_type,route_path,component,redirect,perms,icon,terminal_type,sort_order,visible,status,keep_alive,affix,remark) VALUES
(3101,1102,'新增用户',3,NULL,NULL,NULL,'system:user:add',NULL,'PC',10,1,1,0,0,'按钮权限：用户管理-新增'),
(3102,1102,'编辑用户',3,NULL,NULL,NULL,'system:user:edit',NULL,'PC',20,1,1,0,0,'按钮权限：用户管理-编辑'),
(3103,1102,'删除用户',3,NULL,NULL,NULL,'system:user:delete',NULL,'PC',30,1,1,0,0,'按钮权限：用户管理-删除'),
(3104,1105,'新增目录',3,NULL,NULL,NULL,'system:menu:dir:add',NULL,'PC',10,1,1,0,0,'按钮权限：菜单管理-新增目录'),
(3105,1105,'新增菜单',3,NULL,NULL,NULL,'system:menu:add',NULL,'PC',20,1,1,0,0,'按钮权限：菜单管理-新增菜单'),
(3106,1105,'新增按钮',3,NULL,NULL,NULL,'system:menu:btn:add',NULL,'PC',30,1,1,0,0,'按钮权限：菜单管理-新增按钮'),
(3107,1105,'编辑菜单',3,NULL,NULL,NULL,'system:menu:edit',NULL,'PC',40,1,1,0,0,'按钮权限：菜单管理-编辑'),
(3108,1105,'删除菜单',3,NULL,NULL,NULL,'system:menu:delete',NULL,'PC',50,1,1,0,0,'按钮权限：菜单管理-删除')
ON DUPLICATE KEY UPDATE
  parent_id = VALUES(parent_id),
  menu_name = VALUES(menu_name),
  menu_type = VALUES(menu_type),
  perms = VALUES(perms),
  sort_order = VALUES(sort_order),
  status = VALUES(status),
  deleted = 0;
