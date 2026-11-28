-- 回滚 V36：到站付运输费恢复为车次级录入
-- 注意：按网点录入的金额无法自动合并回单一车次级金额，需人工确认后处理；此处只恢复配置与旧行状态。

UPDATE biz_trip_fee
SET fee_status = 1,
    invalid_reason = NULL
WHERE fee_code = 'FEE0012'
  AND trip_dest_id = 0
  AND deleted = 0;

DELETE FROM biz_trip_fee
WHERE fee_code = 'FEE0012'
  AND trip_dest_id > 0
  AND deleted = 0;

UPDATE sys_fee_type
SET source_field = 'biz_trip.arrival_transport_fee'
WHERE fee_code = 'FEE0012'
  AND deleted = 0;
