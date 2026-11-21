package com.yunshu.modules.system.region.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.yunshu.modules.system.region.entity.SysRegion;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;

import java.util.List;

public interface SysRegionMapper extends BaseMapper<SysRegion> {

    /**
     * 按 code 幂等写入：存在则更新名称/父级/层级/全路径/来源/版本/同步时间/状态。
     * 区划表已豁免数据权限，不会追加 dept_id 条件。
     */
    @Insert("<script>"
            + "INSERT INTO sys_region (code, name, parent_code, level, full_name, source, version, sync_time, status) VALUES "
            + "<foreach collection='list' item='item' separator=','>"
            + "(#{item.code}, #{item.name}, #{item.parentCode}, #{item.level}, #{item.fullName}, "
            + "#{item.source}, #{item.version}, #{item.syncTime}, #{item.status})"
            + "</foreach>"
            + " ON DUPLICATE KEY UPDATE "
            + "name = VALUES(name), parent_code = VALUES(parent_code), level = VALUES(level), "
            + "full_name = VALUES(full_name), source = VALUES(source), version = VALUES(version), "
            + "sync_time = VALUES(sync_time), status = VALUES(status)"
            + "</script>")
    int upsertBatch(@Param("list") List<SysRegion> list);
}
