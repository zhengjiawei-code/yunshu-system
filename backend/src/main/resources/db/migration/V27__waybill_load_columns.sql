-- ============================================================================
-- V27 · 运单干线配载基础列 + 索引 + 存量回填
-- 对应方案：transport-loading-design-v1.3.md 3.9 / 第 12 节 V27
-- 说明：加列使用 INSTANT；索引使用 INPLACE/LOCK=NONE；回填幂等，可重复执行
-- ============================================================================
SET NAMES utf8mb4;

ALTER TABLE `biz_waybill`
  ADD COLUMN `current_hub_id` BIGINT UNSIGNED NULL DEFAULT NULL
    COMMENT '当前库存网点ID：引用 sys_dept.id；开单时=始发网点，卸车后=到达网点；待配载池按「当前库存网点=车次始发网点」匹配',
  ADD COLUMN `current_hub_name` VARCHAR(50) NULL DEFAULT NULL
    COMMENT '当前库存网点名称快照：与 current_hub_id 同步写入，机构改名不影响历史展示',
  ADD COLUMN `current_node_seq` TINYINT UNSIGNED NOT NULL DEFAULT 0
    COMMENT '当前已完成路由节点段序号：0=仍在始发网点未发出，N=已完成第 N 段并落在该段到达网点；下一节点取 seq=N+1',
  ADD COLUMN `inventory_time` DATETIME NULL DEFAULT NULL
    COMMENT '库存落入当前网点时间：揽收完成或卸车完成时间；待配载池按此升序（FIFO）',
  ADD COLUMN `load_status` TINYINT UNSIGNED NOT NULL DEFAULT 0
    COMMENT '干线配载状态：0=待配载（含分拨中心卸车后回池），1=已配载(70)，2=已发车(80)，3=已到站(50/90)，4=已卸车终态(仅末端100/110/120)；由车次操作维护，用于池过滤与防重复配载',
  ALGORITHM=INSTANT;

-- 存量回填：仅处理已开单但尚未进入干线配载生命周期的运单；已有 current_hub_id 的行不重复更新
UPDATE `biz_waybill`
SET `current_hub_id` = `origin_hub_id`,
    `current_hub_name` = (SELECT d.dept_name FROM `sys_dept` d WHERE d.id = `origin_hub_id`),
    `current_node_seq` = 0,
    `inventory_time` = `create_time`,
    `load_status` = 0
WHERE `current_hub_id` IS NULL
  AND `status` IN (20, 60)
  AND `deleted` = 0;

ALTER TABLE `biz_waybill`
  ADD KEY `idx_current_hub` (`current_hub_id`, `load_status`, `status`),
  ALGORITHM=INPLACE, LOCK=NONE;

ALTER TABLE `biz_waybill`
  ADD KEY `idx_inventory_time` (`inventory_time`),
  ALGORITHM=INPLACE, LOCK=NONE;

ALTER TABLE `biz_waybill`
  ADD KEY `idx_parent_deleted` (`parent_id`, `deleted`),
  ALGORITHM=INPLACE, LOCK=NONE;
