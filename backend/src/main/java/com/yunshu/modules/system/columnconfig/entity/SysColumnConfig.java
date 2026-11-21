package com.yunshu.modules.system.columnconfig.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 列表列配置，对应 sys_column_config；唯一键 user_id + page_key。
 * 平台配置表，已登记数据权限豁免（按用户隔离由服务层保证）。
 */
@Data
@TableName("sys_column_config")
public class SysColumnConfig {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;

    /** 页面标识，如 system:user */
    private String pageKey;

    /** 列配置 JSON 字符串 */
    private String configJson;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
