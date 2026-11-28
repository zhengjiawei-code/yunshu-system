package com.yunshu.modules.operation.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.yunshu.modules.operation.entity.BizWaybill;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

public interface BizWaybillMapper extends BaseMapper<BizWaybill> {

    /** 配载/拆单前置锁定：同一运单串行，防止并发重复配载与重号 */
    @Select("SELECT * FROM biz_waybill WHERE id = #{id} AND deleted = 0 FOR UPDATE")
    BizWaybill selectByIdForUpdate(@Param("id") Long id);
}
