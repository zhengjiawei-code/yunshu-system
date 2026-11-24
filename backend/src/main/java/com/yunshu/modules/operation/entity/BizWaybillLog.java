package com.yunshu.modules.operation.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 运单操作日志，对应 biz_waybill_log。
 * 所有动作统一挂母单（子单相关操作也记母单 ID），子单明细放在快照 children[]。
 * JSON 列按字符串读写，由服务层保证结构契约（方案 8.5 / 13）。
 */
@Data
@TableName("biz_waybill_log")
public class BizWaybillLog {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long waybillId;

    private String waybillNo;

    /** 1=拆单 2=合单 3=撤销拆单 4=作废 5=强制签收 6=改单 */
    private Integer actionType;

    private String beforeSnapshot;

    private String afterSnapshot;

    private String reason;

    /** 0=无需审核（一期直接生效），10/20/30 为二期预留 */
    private Integer auditStatus;

    private Long auditorId;

    private LocalDateTime auditTime;

    private Long operatorId;

    private String operatorName;

    private Long deptId;

    private LocalDateTime createTime;
}
