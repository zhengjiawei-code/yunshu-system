package com.yunshu.modules.operation.mapper;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Select;

/**
 * 运单号 / 预开单号并发分配（方案 8.2）。
 * 两句必须在同一事务同一连接内执行：ON DUPLICATE KEY UPDATE 用 LAST_INSERT_ID(expr)
 * 把本次分配值写入会话变量，随后 SELECT LAST_INSERT_ID() 取回。
 * 两张计数表已在 DataScopeTables 中豁免，不会被追加 dept_id 条件。
 */
public interface WaybillSeqMapper {

    @Insert("INSERT INTO biz_waybill_no_seq (seq_date, current_val) "
            + "VALUES (CURDATE(), LAST_INSERT_ID(1)) "
            + "ON DUPLICATE KEY UPDATE current_val = LAST_INSERT_ID(current_val + 1)")
    int allocateWaybillNo();

    @Select("SELECT LAST_INSERT_ID()")
    long lastWaybillNo();

    @Insert("INSERT INTO biz_pre_waybill_no_seq (seq_date, current_val) "
            + "VALUES (CURDATE(), LAST_INSERT_ID(1)) "
            + "ON DUPLICATE KEY UPDATE current_val = LAST_INSERT_ID(current_val + 1)")
    int allocatePreOrderNo();

    @Select("SELECT LAST_INSERT_ID()")
    long lastPreOrderNo();
}
