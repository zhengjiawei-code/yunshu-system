package com.yunshu.modules.system.carrier.dto;

import lombok.Data;

/** 承运商列表查询：标准列表的服务端筛选/排序/分页 */
@Data
public class CarrierQuery {

    private String carrierCode;

    private String carrierName;

    private String contact;

    private String phone;

    private Long deptId;

    private Boolean status;

    private String updateByName;

    private String remark;

    private String sortBy;

    private String sortOrder;

    private Integer pageNum;

    private Integer pageSize;
}
