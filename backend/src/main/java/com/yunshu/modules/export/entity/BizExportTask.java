package com.yunshu.modules.export.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 导出任务表，对应 biz_export_task（V24）。
 * 任务属用户私有，表已登记为数据权限豁免表；可见性由服务层强制 create_by = 当前用户。
 * create_time / update_time 由数据库默认值与 ON UPDATE 维护，实体不做自动填充。
 */
@Data
@TableName("biz_export_task")
public class BizExportTask {

    /** 任务状态：0 排队 / 10 处理中 / 20 成功 / 30 失败 / 40 已过期 */
    public static final int STATUS_QUEUED = 0;
    public static final int STATUS_RUNNING = 10;
    public static final int STATUS_SUCCESS = 20;
    public static final int STATUS_FAILED = 30;
    public static final int STATUS_EXPIRED = 40;

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 任务类型：waybill=运单列表，trip=车次列表 */
    private String taskType;

    /** 筛选、排序、列选择与数据权限快照（JSON） */
    private String querySnapshot;

    private Integer status;

    private Integer totalRows;

    private Integer processedRows;

    private String fileName;

    /** 文件相对路径，不存绝对路径 */
    private String filePath;

    private Long fileSize;

    private String failReason;

    private LocalDateTime expireTime;

    private Long deptId;

    private Long createBy;

    private LocalDateTime createTime;

    private LocalDateTime startTime;

    private LocalDateTime finishTime;

    private Long updateBy;

    private LocalDateTime updateTime;

    @TableLogic
    private Integer deleted;
}
