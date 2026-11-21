package com.yunshu.modules.system.carrier.vo;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class CarrierVO {

    private Long id;

    private String carrierCode;

    private String carrierName;

    private String contact;

    private String phone;

    private Long deptId;

    private String deptName;

    private Boolean status;

    private Integer sortOrder;

    private String remark;

    private Long createBy;

    private String createByName;

    private LocalDateTime createTime;

    private Long updateBy;

    private String updateByName;

    private LocalDateTime updateTime;
}
