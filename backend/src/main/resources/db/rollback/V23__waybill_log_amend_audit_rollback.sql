-- V23 回滚草稿（待批准，未执行）
ALTER TABLE biz_waybill_log
    DROP COLUMN audit_time,
    DROP COLUMN auditor_id,
    DROP COLUMN audit_status;

ALTER TABLE biz_waybill_log
    MODIFY COLUMN action_type TINYINT UNSIGNED NOT NULL
        COMMENT '动作类型：1=拆单，2=合单，3=撤销拆单，4=作废，5=强制签收';
