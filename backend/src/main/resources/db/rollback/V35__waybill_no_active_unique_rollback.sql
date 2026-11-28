-- V35 回滚：恢复 waybill_no 全局唯一
-- 警告：若已发生子单号回收复用，库内会存在多条相同 waybill_no 的墓碑行，直接恢复唯一索引会失败。
--       回滚前需先把重复的作废子单号改写成墓碑号（如 -01#V126），或清理重复行。

ALTER TABLE biz_waybill DROP INDEX uk_active_waybill_no;
ALTER TABLE biz_waybill ADD UNIQUE KEY uk_waybill_no (waybill_no);
ALTER TABLE biz_waybill DROP COLUMN active_waybill_no;
