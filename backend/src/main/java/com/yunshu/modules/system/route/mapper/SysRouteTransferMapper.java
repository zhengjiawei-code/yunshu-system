package com.yunshu.modules.system.route.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.yunshu.modules.system.route.entity.SysRouteTransfer;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

public interface SysRouteTransferMapper extends BaseMapper<SysRouteTransfer> {

    @Select("SELECT COUNT(*) FROM sys_route_transfer WHERE route_code = #{code}")
    long countCodeAll(@Param("code") String code);

    @Select("SELECT COUNT(*) FROM sys_route_transfer WHERE deleted = 0 AND carrier_id = #{carrierId}")
    long countCarrierRef(@Param("carrierId") Long carrierId);

    /** 适用网点内编码是否被未删除行占用（D4=B：唯一口径为 适用网点+编码） */
    @Select("SELECT COUNT(*) FROM sys_route_transfer WHERE deleted = 0 "
            + "AND apply_dept_id = #{applyDeptId} AND route_code = #{code}")
    long countCodeInDept(@Param("applyDeptId") Long applyDeptId, @Param("code") String code);

    /** 查找同键逻辑删除行：批量加入时恢复而非新增 */
    @Select("SELECT * FROM sys_route_transfer WHERE deleted = 1 AND apply_dept_id = #{applyDeptId} "
            + "AND region_code = #{regionCode} AND transfer_type = #{type} ORDER BY id DESC LIMIT 1")
    SysRouteTransfer selectDeletedByKey(@Param("applyDeptId") Long applyDeptId,
                                        @Param("regionCode") String regionCode,
                                        @Param("type") Integer type);

    /** 恢复逻辑删除行并写入默认公共字段 */
    @Update("UPDATE sys_route_transfer SET deleted = 0, route_code = #{routeCode}, route_name = #{routeName}, "
            + "region_level = #{regionLevel}, region_full_name = #{regionFullName}, carrier_id = #{carrierId}, "
            + "owner_company_id = #{ownerCompanyId}, status = 1, sort_order = #{sortOrder}, remark = NULL, "
            + "dept_id = #{deptId}, update_by = #{userId}, update_time = NOW() WHERE id = #{id}")
    int restoreById(@Param("id") Long id, @Param("routeCode") String routeCode,
                    @Param("routeName") String routeName, @Param("regionLevel") Integer regionLevel,
                    @Param("regionFullName") String regionFullName, @Param("carrierId") Long carrierId,
                    @Param("ownerCompanyId") Long ownerCompanyId, @Param("sortOrder") Integer sortOrder,
                    @Param("deptId") Long deptId, @Param("userId") Long userId);
}
