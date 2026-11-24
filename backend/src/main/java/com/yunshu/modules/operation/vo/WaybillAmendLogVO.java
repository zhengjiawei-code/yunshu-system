package com.yunshu.modules.operation.vo;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/** 改单记录展示对象：日志元信息 + 字段级明细（由 after_snapshot.changes 解析，一次查询带出） */
@Data
public class WaybillAmendLogVO {

    private Long id;

    private String waybillNo;

    private String operatorName;

    private LocalDateTime createTime;

    private String reason;

    /** 变更条数，与 changes 长度一致 */
    private Integer changeCount;

    private List<WaybillFieldChangeVO> changes;
}
