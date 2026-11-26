package com.yunshu.modules.transport.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.yunshu.modules.transport.entity.BizTripNoSeq;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Select;

/**
 * 车次号并发分配。两句必须在同一事务同一连接内执行：
 * ON DUPLICATE KEY UPDATE 用 LAST_INSERT_ID(expr) 记录本次值，随后取回。
 */
public interface BizTripNoSeqMapper extends BaseMapper<BizTripNoSeq> {

    @Insert("INSERT INTO biz_trip_no_seq (seq_date, current_val) "
            + "VALUES (CURDATE(), LAST_INSERT_ID(1)) "
            + "ON DUPLICATE KEY UPDATE current_val = LAST_INSERT_ID(current_val + 1)")
    int allocate();

    @Select("SELECT LAST_INSERT_ID()")
    long lastValue();
}
