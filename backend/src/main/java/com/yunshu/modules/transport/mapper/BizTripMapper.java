package com.yunshu.modules.transport.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.yunshu.modules.transport.entity.BizTrip;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

public interface BizTripMapper extends BaseMapper<BizTrip> {

    /** 发车/取消发车/关闭前置锁行：同一车次串行，防并发状态跳变 */
    @Select("SELECT * FROM biz_trip WHERE id = #{id} AND deleted = 0 FOR UPDATE")
    BizTrip selectByIdForUpdate(@Param("id") Long id);
}
