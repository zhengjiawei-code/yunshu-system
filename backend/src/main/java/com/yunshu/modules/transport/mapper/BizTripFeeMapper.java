package com.yunshu.modules.transport.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.yunshu.modules.transport.entity.BizTripFee;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;

import java.math.BigDecimal;

public interface BizTripFeeMapper extends BaseMapper<BizTripFee> {

    /**
     * 车次级费用 upsert：唯一键 (trip_id, trip_dest_id, fee_code) 不含 deleted，
     * 逻辑删除过的行必须复用，故走 ON DUPLICATE KEY UPDATE 恢复 deleted=0。
     */
    @Insert("""
            INSERT INTO biz_trip_fee
            (trip_id, trip_dest_id, fee_code, fee_name, fee_category, amount, profit_enabled, profit_direction,
             fee_status, invalid_reason, sort_order, dept_id, create_by, update_by, deleted)
            VALUES (#{tripId}, 0, #{feeCode}, #{feeName}, 2, #{amount}, #{profitEnabled}, #{profitDirection},
                    1, NULL, #{sortOrder}, #{deptId}, #{operatorId}, #{operatorId}, 0)
            ON DUPLICATE KEY UPDATE
              fee_name = VALUES(fee_name),
              amount = VALUES(amount),
              profit_enabled = VALUES(profit_enabled),
              profit_direction = VALUES(profit_direction),
              fee_status = 1,
              invalid_reason = NULL,
              sort_order = VALUES(sort_order),
              update_by = VALUES(update_by),
              deleted = 0
            """)
    int upsertTripLevel(@Param("tripId") Long tripId,
                        @Param("feeCode") String feeCode,
                        @Param("feeName") String feeName,
                        @Param("amount") BigDecimal amount,
                        @Param("profitEnabled") Boolean profitEnabled,
                        @Param("profitDirection") Integer profitDirection,
                        @Param("sortOrder") Integer sortOrder,
                        @Param("deptId") Long deptId,
                        @Param("operatorId") Long operatorId);
}
