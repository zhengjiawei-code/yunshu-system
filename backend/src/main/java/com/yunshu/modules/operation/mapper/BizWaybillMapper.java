package com.yunshu.modules.operation.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.yunshu.modules.operation.entity.BizWaybill;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

public interface BizWaybillMapper extends BaseMapper<BizWaybill> {

    /** 配载/拆单前置锁定：同一运单串行，防止并发重复配载 */
    @Select("SELECT * FROM biz_waybill WHERE id = #{id} AND deleted = 0 FOR UPDATE")
    BizWaybill selectByIdForUpdate(@Param("id") Long id);

    /** 子单序号取最大值：包含逻辑删除行，避免 uk_parent_child 冲突 */
    @Select("SELECT COALESCE(MAX(child_seq), 0) FROM biz_waybill WHERE parent_id = #{parentId}")
    int selectMaxChildSeq(@Param("parentId") Long parentId);
}
