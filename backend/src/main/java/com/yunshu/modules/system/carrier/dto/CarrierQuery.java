package com.yunshu.modules.system.carrier.dto;

import com.yunshu.common.query.BaseListQuery;
import lombok.Data;

import java.util.List;

/** 承运商列表查询：标准列表的服务端筛选/排序/分页 */
@Data
public class CarrierQuery extends BaseListQuery {

    private String carrierCode;

    private String carrierName;

    private String contact;

    private String phone;

    private List<Long> deptId;

    private List<Boolean> status;

    private String updateByName;

    /** 修改时间起止：日期格式 yyyy-MM-dd，结束日含当日 */
    private String updateTimeStart;

    private String updateTimeEnd;

    private String remark;

    private String sortBy;

    private String sortOrder;

    private Integer pageNum;

    private Integer pageSize;
}
