package com.yunshu.modules.operation.vo;

import lombok.Data;

import java.util.List;

/** 客户自动匹配结果：matched=false 时候选为空数组，前端留空、不拦截保存 */
@Data
public class CustomerMatchVO {

    private Boolean matched;

    private List<Item> candidates;

    @Data
    public static class Item {

        private Long id;

        private String customerName;

        private String contactPhone;

        private String address;
    }
}
