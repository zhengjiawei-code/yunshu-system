-- 改单允许清空字段（家伟要求：改单页暂时不做必填）。
-- 被清空的字段按「显式置空」落 NULL，因此把改单可写列由 NOT NULL 放宽为可空。
-- 说明：开单保存仍由服务端校验必填，表中的非空语义只在改单链路被放宽。
ALTER TABLE `biz_waybill`
  MODIFY COLUMN `sender_name` VARCHAR(50) NULL DEFAULT NULL COMMENT '发货人姓名：开单必填；改单可清空为 NULL',
  MODIFY COLUMN `sender_phone` VARCHAR(20) NULL DEFAULT NULL COMMENT '发货人电话：开单必填；改单可清空为 NULL',
  MODIFY COLUMN `sender_address` VARCHAR(200) NULL DEFAULT NULL COMMENT '发货详细地址：开单必填；改单可清空为 NULL',
  MODIFY COLUMN `receiver_name` VARCHAR(50) NULL DEFAULT NULL COMMENT '收货人姓名：开单必填；改单可清空为 NULL',
  MODIFY COLUMN `receiver_phone` VARCHAR(20) NULL DEFAULT NULL COMMENT '收货人电话：开单必填；改单可清空为 NULL',
  MODIFY COLUMN `receiver_address` VARCHAR(200) NULL DEFAULT NULL COMMENT '收货详细地址：开单必填；改单可清空为 NULL',
  MODIFY COLUMN `pay_method` TINYINT UNSIGNED NULL DEFAULT NULL COMMENT '支付方式：1=现付，2=到付，3=月结，4=回付，5=多笔付；开单必填，改单可清空为 NULL',
  MODIFY COLUMN `freight_fee` DECIMAL(12,2) NULL DEFAULT NULL COMMENT '运费(元)：开单默认 0；改单可清空为 NULL（参与合计时按 0 处理）',
  MODIFY COLUMN `delivery_fee` DECIMAL(12,2) NULL DEFAULT NULL COMMENT '送货费(元)：改单可清空为 NULL（参与合计时按 0 处理）',
  MODIFY COLUMN `pickup_fee` DECIMAL(12,2) NULL DEFAULT NULL COMMENT '提货费(元)：改单可清空为 NULL（参与合计时按 0 处理）',
  MODIFY COLUMN `other_fee` DECIMAL(12,2) NULL DEFAULT NULL COMMENT '其他费(元)：改单可清空为 NULL（参与合计时按 0 处理）',
  MODIFY COLUMN `receipt_fee` DECIMAL(12,2) NULL DEFAULT NULL COMMENT '回单费(元)：改单可清空为 NULL（参与合计时按 0 处理）',
  MODIFY COLUMN `make_bill_fee` DECIMAL(12,2) NULL DEFAULT NULL COMMENT '制单费(元)：改单可清空为 NULL（参与合计时按 0 处理）',
  MODIFY COLUMN `insurance_fee` DECIMAL(12,2) NULL DEFAULT NULL COMMENT '保费(元)：改单可清空为 NULL（参与合计时按 0 处理）',
  MODIFY COLUMN `cash_back` DECIMAL(12,2) NULL DEFAULT NULL COMMENT '现返(元)：改单可清空为 NULL（参与返款合计时按 0 处理）',
  MODIFY COLUMN `owe_back` DECIMAL(12,2) NULL DEFAULT NULL COMMENT '欠返(元)：改单可清空为 NULL（参与返款合计时按 0 处理）',
  MODIFY COLUMN `delivery_method` TINYINT UNSIGNED NULL DEFAULT NULL COMMENT '送货方式：1=送货，2=自提，3=大车直送；开单必填，改单可清空为 NULL',
  MODIFY COLUMN `cod_amount` DECIMAL(12,2) NULL DEFAULT NULL COMMENT '代收货款(元)：改单可清空为 NULL（按 0 处理）';
