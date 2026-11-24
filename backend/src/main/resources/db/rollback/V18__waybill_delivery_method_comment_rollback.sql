-- 回滚 V18：恢复 V17 的送货方式注释
ALTER TABLE biz_waybill
    MODIFY COLUMN delivery_method TINYINT UNSIGNED NULL
    COMMENT '送货方式：1=送货，2=自提；3=大车直送已停用（历史兼容）';
