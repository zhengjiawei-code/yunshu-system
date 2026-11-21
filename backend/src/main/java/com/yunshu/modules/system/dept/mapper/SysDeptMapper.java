package com.yunshu.modules.system.dept.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.yunshu.modules.system.dept.entity.SysDept;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.Collection;
import java.util.List;
import java.util.Map;

public interface SysDeptMapper extends BaseMapper<SysDept> {

    /** 判断表是否已存在：sys_user / sys_user_org 属于后续模块，未建表时跳过引用校验 */
    @Select("SELECT COUNT(*) FROM information_schema.tables "
            + "WHERE table_schema = DATABASE() AND table_name = #{tableName}")
    long countTable(@Param("tableName") String tableName);

    @Select("SELECT COUNT(*) FROM sys_user WHERE deleted = 0 AND dept_id = #{deptId}")
    long countUserRef(@Param("deptId") Long deptId);

    @Select("SELECT COUNT(*) FROM sys_user_org WHERE org_id = #{deptId}")
    long countUserOrgRef(@Param("deptId") Long deptId);

    /** 批量解析用户名；仅在 sys_user 表存在时调用 */
    @Select("<script>SELECT id AS id, real_name AS realName FROM sys_user WHERE id IN "
            + "<foreach collection='ids' item='id' open='(' separator=',' close=')'>#{id}</foreach>"
            + "</script>")
    List<Map<String, Object>> selectUserNames(@Param("ids") Collection<Long> ids);

    /** 按真实姓名模糊匹配用户ID，供“修改人”列表筛选使用 */
    @Select("SELECT id FROM sys_user WHERE deleted = 0 AND real_name LIKE CONCAT('%', #{name}, '%')")
    List<Long> selectUserIdsByName(@Param("name") String name);
}
