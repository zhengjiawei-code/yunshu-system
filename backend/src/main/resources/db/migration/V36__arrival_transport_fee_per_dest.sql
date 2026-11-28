-- V36：到站付运输费改为按目的网点（子车次级）录入
-- 背景：到站付运输费是末端网点费用，多目的网点时必须按网点分别录入，才能与目的网点对应并只分摊到本网点运单。

-- 1) 费用项取数来源改为子车次列，前端「费用信息」按目的网点分行录入
UPDATE sys_fee_type
SET source_field = 'biz_trip_dest.arrival_transport_fee',
    update_time = NOW()
WHERE fee_code = 'FEE0012'
  AND deleted = 0;

-- 2) 历史车次级金额迁到该车次第一个目的网点，保持总额不变
--    （历史多网点金额无法回溯拆分，由首网点承接，可在页面按网点重新录入）
INSERT INTO biz_trip_fee
(trip_id, trip_dest_id, fee_code, fee_name, fee_category, amount, profit_enabled, profit_direction,
 fee_status, invalid_reason, sort_order, dept_id, create_by, update_by, deleted)
SELECT f.trip_id, d.id, f.fee_code, f.fee_name, f.fee_category, f.amount,
       f.profit_enabled, f.profit_direction,
       1, NULL, f.sort_order, f.dept_id, f.create_by, f.update_by, 0
FROM biz_trip_fee f
JOIN biz_trip_dest d
  ON d.trip_id = f.trip_id AND d.deleted = 0
JOIN (
    SELECT trip_id, MIN(seq) AS min_seq
    FROM biz_trip_dest
    WHERE deleted = 0
    GROUP BY trip_id
) m ON m.trip_id = f.trip_id AND m.min_seq = d.seq
WHERE f.fee_code = 'FEE0012'
  AND f.trip_dest_id = 0
  AND f.fee_status = 1
  AND f.deleted = 0
  AND EXISTS (
      SELECT 1 FROM sys_fee_type t
      WHERE t.fee_code = 'FEE0012'
        AND t.deleted = 0
        AND t.source_field = 'biz_trip_dest.arrival_transport_fee'
  )
ON DUPLICATE KEY UPDATE
  amount = VALUES(amount),
  fee_status = 1,
  invalid_reason = NULL,
  deleted = 0;

-- 3) 旧的车次级到站付运输费行停用，避免与新口径重复计入
UPDATE biz_trip_fee
SET fee_status = 2,
    invalid_reason = 'V36 改为按目的网点录入',
    update_time = NOW()
WHERE fee_code = 'FEE0012'
  AND trip_dest_id = 0
  AND deleted = 0
  AND EXISTS (
      SELECT 1 FROM sys_fee_type t
      WHERE t.fee_code = 'FEE0012'
        AND t.deleted = 0
        AND t.source_field = 'biz_trip_dest.arrival_transport_fee'
  );
