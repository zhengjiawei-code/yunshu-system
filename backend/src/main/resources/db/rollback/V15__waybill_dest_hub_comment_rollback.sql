-- V15 回滚：恢复运达网点列注释（仅注释）
ALTER TABLE `biz_waybill`
  MODIFY COLUMN `dest_hub_id` BIGINT UNSIGNED NULL DEFAULT NULL
    COMMENT '运达网点ID：引用sys_dept.id；取所选路由终点网点，与目的地区县对应；外转/直达路由终点为区划、无归属网点时允许为空；只读派生，前端不可编辑（Q29）',
  MODIFY COLUMN `dest_hub_name` VARCHAR(50) NULL DEFAULT NULL
    COMMENT '运达网点名称快照：随dest_hub_id写入；无归属网点时为空，列表显示—';
