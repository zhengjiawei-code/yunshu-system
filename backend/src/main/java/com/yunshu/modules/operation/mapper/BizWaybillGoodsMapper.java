package com.yunshu.modules.operation.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.yunshu.modules.operation.entity.BizWaybillGoods;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Param;

public interface BizWaybillGoodsMapper extends BaseMapper<BizWaybillGoods> {

    /**
     * 物理删除货物行：biz_waybill_goods 的唯一键 (waybill_id, seq) 不排除逻辑删除行，
     * 改单减少行数时必须物理删除，否则后续再次使用同一序号会主键冲突。
     * 调用前运单可见性已由 selectVisible 校验；SQL 仍会经过数据权限拦截器。
     */
    @Delete("DELETE FROM biz_waybill_goods WHERE id = #{id}")
    int physicalDeleteById(@Param("id") Long id);
}
