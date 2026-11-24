-- =============================================================================
-- V25 待批稿（未执行，不在 Flyway 目录）
--
-- 问题（2026-11-24 实测）：
--   V22 仅修改 status 列注释，列类型仍为 TINYINT UNSIGNED（上限 255），
--   无法写入设计约定的 900=已作废；作废接口实测报
--   "Data truncation: Out of range value for column 'status' at row 1"。
--
-- 方案：
--   status 改为 SMALLINT UNSIGNED（上限 65535），注释保持 V22 的 14 码值不变。
--   现有值 10–130 不受影响；改型为扩大取值范围，MySQL 8 可原地完成。
--
-- 执行方式：家伟批准后复制到 backend/src/main/resources/db/migration/V25__waybill_status_type.sql，
--           停 8090 后启动，Flyway 版本 24 → 25。
-- =============================================================================

ALTER TABLE biz_waybill
    MODIFY COLUMN status SMALLINT UNSIGNED NOT NULL DEFAULT 20
        COMMENT '运单状态：10=待揽收（预开单/导入/接口推送），20=已揽收（默认，开单即20），30=网点装车，40=网点发车，50=分拨到车，60=分拨卸车，70=分拨装车，80=分拨发车，90=末端到车，100=到站待提，110=到站待送，120=到站待转，130=已签收，900=已作废';
