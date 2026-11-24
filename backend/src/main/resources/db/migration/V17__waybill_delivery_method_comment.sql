-- V17：送货方式口径收敛为「1=送货，2=自提」，3=大车直送停用（历史兼容）
-- 仅修改列注释，无数据、无结构变化
ALTER TABLE biz_waybill
    MODIFY COLUMN delivery_method TINYINT UNSIGNED NULL
    COMMENT '送货方式：1=送货，2=自提；3=大车直送已停用（历史兼容）';
