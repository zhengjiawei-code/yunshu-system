package com.yunshu.modules.system.route.dto;

import com.yunshu.common.query.BaseListQuery;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

/** 短驳/干线路由列表查询 */
@Data
public class RouteQuery extends BaseListQuery {

    private String routeCode;

    private String routeName;

    /** 路由类型：1短驳 2干线；支持多选 */
    private List<Integer> routeCategory;

    /** 始发网点；支持多选 */
    private List<Long> originDeptId;

    /** 到达网点；支持多选 */
    private List<Long> destDeptId;

    /** 状态；支持多选 */
    private List<Boolean> status;

    /** ALL=授权全部；PARTIAL=按公司授权 */
    private String grantScope;

    private String warehouseArea;

    private BigDecimal mileageMin;

    private BigDecimal mileageMax;

    private BigDecimal transitHoursMin;

    private BigDecimal transitHoursMax;

    private String contactName;

    private String contactPhone;

    private String serviceName;

    private String servicePhone;

    /** 创建公司 ID，多选时逗号分隔 */
    private String ownerCompanyIds;

    /** 更新时间起止：yyyy-MM-dd，结束日含当日 */
    private String updateTimeStart;

    private String updateTimeEnd;

    private String sortBy;

    private String sortOrder;

    private Integer pageNum;

    private Integer pageSize;
}
