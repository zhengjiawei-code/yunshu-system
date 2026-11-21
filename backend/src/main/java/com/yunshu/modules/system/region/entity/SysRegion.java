package com.yunshu.modules.system.region.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 行政区划实体，对应 sys_region；code 为业务主键。
 */
@Data
@TableName("sys_region")
public class SysRegion {

    @TableId(type = IdType.INPUT)
    private String code;

    private String name;

    private String parentCode;

    /** 层级：1=省 2=市 3=区县 4=乡级 */
    private Integer level;

    private String fullName;

    /** offline / online / init */
    private String source;

    private String version;

    private LocalDateTime syncTime;

    /** 1=有效 0=停用 */
    private Integer status;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
