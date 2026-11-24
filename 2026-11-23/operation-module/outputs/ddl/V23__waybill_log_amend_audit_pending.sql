-- =============================================================================
-- V23 草稿（待家伟批准，未执行）
-- 目的（v1.16 方案 8.5 / 8.10）：
--   1) 改单留痕复用 biz_waybill_log：action_type 注释补 6=改单；
--   2) 为二期改单审核预留审计字段，一期只写默认值，不参与任何逻辑：
--      audit_status=0（无需审核/直接生效）、auditor_id=NULL、audit_time=NULL。
-- 说明：不新建表；企业微信对接配置本期不建表。
-- 使用方式：批准后复制到 backend/src/main/resources/db/migration/V23__waybill_log_amend_audit.sql 执行。
-- 当前库版本：21（V22 批准并执行后为 22）
-- =============================================================================

ALTER TABLE biz_waybill_log
    MODIFY COLUMN action_type TINYINT UNSIGNED NOT NULL
        COMMENT '动作类型：1=拆单，2=合单，3=撤销拆单，4=作废，5=强制签收，6=改单';

ALTER TABLE biz_waybill_log
    ADD COLUMN audit_status TINYINT UNSIGNED NOT NULL DEFAULT 0
        COMMENT '改单审核状态：0=无需审核（一期直接生效），10=待审核，20=审核通过，30=审核驳回（二期使用）' AFTER reason,
    ADD COLUMN auditor_id BIGINT UNSIGNED NULL
        COMMENT '审核人用户ID：引用sys_user.id；一期为空' AFTER audit_status,
    ADD COLUMN audit_time DATETIME NULL
        COMMENT '审核时间；一期为空' AFTER auditor_id;
