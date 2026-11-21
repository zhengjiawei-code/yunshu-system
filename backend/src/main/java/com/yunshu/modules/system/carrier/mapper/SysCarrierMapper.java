package com.yunshu.modules.system.carrier.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.yunshu.modules.system.carrier.entity.SysCarrier;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

public interface SysCarrierMapper extends BaseMapper<SysCarrier> {

    /** 被外转/直达路由引用次数（逻辑删除的路由不计） */
    @Select("SELECT COUNT(*) FROM sys_route_transfer WHERE deleted = 0 AND carrier_id = #{carrierId}")
    long countRouteRef(@Param("carrierId") Long carrierId);
}
