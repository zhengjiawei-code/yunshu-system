package com.yunshu.modules.transport.vo;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/** 批量配载/移出结果：成功数 + 失败清单；超限只提醒不阻断。 */
@Data
public class LoadResultVO {

    private int successCount;

    private List<Failure> failures = new ArrayList<>();

    private List<String> warnings = new ArrayList<>();

    @Data
    public static class Failure {
        private Long waybillId;
        private String waybillNo;
        private String reason;
    }
}
