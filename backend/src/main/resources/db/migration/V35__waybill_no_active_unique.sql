-- V35：子单号作废回收复用，母单号永久占号
-- 背景：子单作废后需要把 -NN 序号释放给后续拆单复用；但母单号是历史追溯主键，作废后也不得被新记录占用。
-- 方案：新增 STORED 生成列 active_waybill_no，只对「母单行 + 有效子单行」写入 waybill_no，作废/删除子单写 NULL；
--       唯一索引建在生成列上，同一运单号允许多条作废子单墓碑行，但有效行仍全局唯一。
-- 注意：原 uk_waybill_no 必须删除，否则作废子单保留原号会阻塞复用。

ALTER TABLE biz_waybill
    ADD COLUMN active_waybill_no VARCHAR(32)
        GENERATED ALWAYS AS (
            CASE
                -- 母单：无论状态一律占号，永不释放
                WHEN is_child = 0 OR parent_id IS NULL THEN waybill_no
                -- 子单：仅有效行占号；作废(900)或逻辑删除后释放
                WHEN deleted = 0 AND status <> 900 THEN waybill_no
                ELSE NULL
            END
        ) STORED;

ALTER TABLE biz_waybill
    ADD UNIQUE KEY uk_active_waybill_no (active_waybill_no);

ALTER TABLE biz_waybill
    DROP INDEX uk_waybill_no;
