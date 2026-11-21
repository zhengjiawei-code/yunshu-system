package com.yunshu.modules.system.route.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.yunshu.modules.system.route.entity.SysRoute;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

public interface SysRouteMapper extends BaseMapper<SysRoute> {

    /** 编码全表唯一校验（含逻辑删除，唯一索引不区分 deleted） */
    @Select("SELECT COUNT(*) FROM sys_route WHERE route_code = #{code}")
    long countCodeAll(@Param("code") String code);

    @Select("SELECT MAX(route_code) FROM sys_route WHERE route_code LIKE CONCAT(#{prefix}, '%')")
    String maxCode(@Param("prefix") String prefix);
}
