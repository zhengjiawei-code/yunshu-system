-- V18：送货方式口径恢复三项，大车直送不受线路服务能力限制
-- 仅修改列注释，无数据、无结构变化
ALTER TABLE biz_waybill
    MODIFY COLUMN delivery_method TINYINT UNSIGNED NULL
    COMMENT '送货方式：1=送货，2=自提，3=大车直送（不受线路服务能力限制）';
