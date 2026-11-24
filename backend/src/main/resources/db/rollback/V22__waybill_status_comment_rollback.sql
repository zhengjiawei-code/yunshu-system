-- V22 回滚草稿（待批准，未执行）：恢复 v1.16 之前的运单状态注释
ALTER TABLE biz_waybill
    MODIFY COLUMN status TINYINT UNSIGNED NOT NULL DEFAULT 20
        COMMENT '运单状态：10=待揽收，20=已揽收（默认，开单即20），25=已拆单（母单），30=已装车，40=运输中，50=已到达，55=部分到达，60=已签收（含Q28强制签收），70=异常，90=已作废';
