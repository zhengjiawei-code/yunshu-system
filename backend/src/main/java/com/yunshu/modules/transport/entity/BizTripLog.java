package com.yunshu.modules.transport.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 车次操作日志，对应 biz_trip_log（V30）。
 * 只新增不修改，快照列存 JSON 文本。
 */
@Data
@TableName("biz_trip_log")
public class BizTripLog {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long tripId;

    private String tripNo;

    /** 1创建 2修改 3配载 4移除 5发车 6取消发车 7到站 8卸车 9关闭 10删除 11车辆 12费用 13改单 14作废 15网点 16取消到站 17取消卸车 */
    private Integer actionType;

    private Long destId;

    private String beforeSnapshot;

    private String afterSnapshot;

    private String reason;

    private Long operatorId;

    private String operatorName;

    private Long deptId;

    private LocalDateTime createTime;
}
