package com.yunshu.modules.operation.vo;

import lombok.Data;

/** 改单字段级差异（写入 after_snapshot.changes，并回传前端展示） */
@Data
public class WaybillFieldChangeVO {

    private String field;

    private String label;

    private String before;

    private String after;
}
