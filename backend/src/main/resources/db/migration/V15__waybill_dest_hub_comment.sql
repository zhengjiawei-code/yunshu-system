-- V15：运达网点派生规则注释修订（仅修改列注释，不改结构、不改数据）
-- 规则：末段目标本身是有归属机构的网点(dept_type=5且启用) -> 取该网点；
--       末段仅到区划、无归属网点 -> 回落为始发网点(origin_hub)；
--       任何情况下不得取路由中间分拨中心或与始发无关的节点。
ALTER TABLE `biz_waybill`
  MODIFY COLUMN `dest_hub_id` BIGINT UNSIGNED NULL DEFAULT NULL
    COMMENT '运达网点ID：引用sys_dept.id；末段目标本身是有归属机构的网点(dept_type=5且启用)时取该网点；末段仅到区划、无归属网点时回落为始发网点(=origin_hub_id)；不得取路由中间节点；只读派生，前端不可编辑（Q29）',
  MODIFY COLUMN `dest_hub_name` VARCHAR(50) NULL DEFAULT NULL
    COMMENT '运达网点名称快照：随dest_hub_id写入；末段无归属网点时回落为始发网点名称，不再为空';
