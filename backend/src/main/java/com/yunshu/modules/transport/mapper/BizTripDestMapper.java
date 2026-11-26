package com.yunshu.modules.transport.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.yunshu.modules.transport.entity.BizTripDest;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Param;

public interface BizTripDestMapper extends BaseMapper<BizTripDest> {

    /**
     * 物理删除某车次的目的网点：仅用于「无配载、无费用」的网点删除/重排，
     * 避免逻辑删除行占用 uk_trip_seq / uk_sub_trip_no 导致序号无法复用。
     */
    @Delete("DELETE FROM biz_trip_dest WHERE trip_id = #{tripId}")
    int hardDeleteByTripId(@Param("tripId") Long tripId);

    @Delete("DELETE FROM biz_trip_dest WHERE id = #{id}")
    int hardDeleteById(@Param("id") Long id);
}
