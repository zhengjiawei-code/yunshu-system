-- 回滚 V17：恢复送货方式原注释
ALTER TABLE biz_waybill
    MODIFY COLUMN delivery_method TINYINT UNSIGNED NULL
    COMMENT '送货方式：1=送货，2=自提，3=大车直送；码值固定；展示顺序为自提/送货/大车直送，与码值无关';
