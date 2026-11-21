package com.yunshu.modules.system.route.vo;

import lombok.Data;

import java.util.List;

@Data
public class BatchGrantResultVO {

    private Integer success;

    private Integer skipped;

    private String message;
}
