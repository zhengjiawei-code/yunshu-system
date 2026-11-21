package com.yunshu.modules.system.route.vo;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/** 批量加入/批量操作的汇总结果 */
@Data
public class TransferBatchResultVO {

    private int total;

    /** 新建 */
    private int created;

    /** 恢复逻辑删除 */
    private int restored;

    /** 本公司已有的路由，覆盖公共字段 */
    private int overwritten;

    /** 他公司路由，仅补授权不覆盖 */
    private int granted;

    /** 跳过（已可用/冲突/越权） */
    private int skipped;

    private String message;

    private List<String> details = new ArrayList<>();

    public void addDetail(String detail) {
        this.details.add(detail);
    }
}
