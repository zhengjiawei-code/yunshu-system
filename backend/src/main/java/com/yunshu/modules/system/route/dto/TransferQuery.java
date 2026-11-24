package com.yunshu.modules.system.route.dto;

import com.yunshu.common.query.BaseListQuery;
import lombok.Data;

import java.util.List;

/** 外转/直达路由列表查询 */
@Data
public class TransferQuery extends BaseListQuery {

    private String routeCode;

    private String routeName;

    /** 类型：1直达 2外转；支持多选 */
    private List<Integer> transferType;

    /** 配置网点 */
    private Long applyDeptId;

    /** 目的地编码/名称关键字，支持多模式匹配 */
    private String regionCode;

    /** 别名关键字，支持多模式匹配 */
    private String alias;

    /** 目的地级别：2=市 3=区县 4=乡级；支持多选 */
    private List<Integer> regionLevel;

    /** 承运商；支持多选 */
    private List<Long> carrierId;

    /** 状态；支持多选 */
    private List<Boolean> status;

    /** 授权范围：ALL / PARTIAL；支持多选 */
    private List<String> grantScope;

    /** 提供服务：PICKUP 仅自提 / DELIVERY 仅送货 / BOTH 自提+送货；支持多选 */
    private List<String> serviceType;

    /** 修改人姓名，支持多模式匹配 */
    private String updateByName;

    /** 创建公司 ID，多选时逗号分隔 */
    private String ownerCompanyIds;

    /** 修改时间起止：yyyy-MM-dd，结束日含当日 */
    private String updateTimeStart;

    private String updateTimeEnd;

    /** 目的地四级区划编码筛选，按 regionCode 前缀匹配 */
    private String destProvinceCode;

    private String destCityCode;

    private String destDistrictCode;

    private String destTownCode;

    private String sortBy;

    private String sortOrder;

    private Integer pageNum;

    private Integer pageSize;
}
