package com.yunshu.modules.system.route.service;

import com.yunshu.modules.system.route.dto.BatchGrantRequest;
import com.yunshu.modules.system.route.dto.RouteQuery;
import com.yunshu.modules.system.route.dto.RouteSaveRequest;
import com.yunshu.modules.system.route.dto.TransferBatchCarrierRequest;
import com.yunshu.modules.system.route.dto.TransferBatchJoinRequest;
import com.yunshu.modules.system.route.dto.TransferBatchStatusRequest;
import com.yunshu.modules.system.route.dto.TransferBatchTypeRequest;
import com.yunshu.modules.system.route.dto.TransferEditRequest;
import com.yunshu.modules.system.route.dto.TransferQuery;
import com.yunshu.modules.system.route.vo.BatchGrantResultVO;
import com.yunshu.modules.system.route.vo.RegionLibraryPageVO;
import com.yunshu.modules.system.route.vo.RouteGrantVO;
import com.yunshu.modules.system.route.vo.RouteMatchVO;
import com.yunshu.modules.system.route.vo.RoutePageVO;
import com.yunshu.modules.system.route.vo.RouteSaveResultVO;
import com.yunshu.modules.system.route.vo.RouteTransferVO;
import com.yunshu.modules.system.route.vo.RouteVO;
import com.yunshu.modules.system.route.vo.TransferBatchResultVO;
import com.yunshu.modules.system.route.vo.TransferPageVO;

import java.util.List;

public interface SysRouteService {

    RoutePageVO page(RouteQuery query);

    RouteVO detail(Long id);

    RouteSaveResultVO create(RouteSaveRequest request);

    void update(Long id, RouteSaveRequest request);

    void delete(Long id);

    TransferPageVO transferPage(TransferQuery query);

    RouteTransferVO transferDetail(Long id);

    /** 区划库：按配置网点给出可加入的市/区县/乡级区划及配置状态 */
    RegionLibraryPageVO regionLibrary(Long applyDeptId, String keyword, List<Integer> levelList,
                                      List<Boolean> configuredList, String parentCode,
                                      String provinceCode, String cityCode,
                                      String districtKeyword, String townKeyword,
                                      String districtCode, String townCode,
                                      String nameKeyword,
                                      String filterModes, String sortBy, String sortOrder,
                                      long pageNum, long pageSize);

    /** 从区划库批量加入（默认外转）：新建/恢复；本公司已有覆盖公共字段，他公司只补授权 */
    TransferBatchResultVO batchJoin(TransferBatchJoinRequest request);

    void updateTransfer(Long id, TransferEditRequest request);

    void deleteTransfer(Long id);

    TransferBatchResultVO switchTransferType(TransferBatchTypeRequest request);

    TransferBatchResultVO batchCarrier(TransferBatchCarrierRequest request);

    TransferBatchResultVO batchStatus(TransferBatchStatusRequest request);

    List<RouteGrantVO> grants(Integer routeScope, Long routeId);

    int grant(Integer routeScope, Long routeId, List<Long> companyDeptIds);

    void revoke(Integer routeScope, Long routeId, Long companyDeptId);

    BatchGrantResultVO batchGrant(BatchGrantRequest request);

    RouteMatchVO match(String destRegionCode, Long destDeptId, Long originDeptId, Long applyDeptId);
}
