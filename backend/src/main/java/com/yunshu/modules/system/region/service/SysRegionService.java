package com.yunshu.modules.system.region.service;

import com.yunshu.modules.system.region.entity.SysRegionSyncLog;
import com.yunshu.modules.system.region.vo.RegionImportCheckVO;
import com.yunshu.modules.system.region.vo.RegionImportResultVO;
import com.yunshu.modules.system.region.vo.AddressRecognizeVO;
import com.yunshu.modules.system.region.vo.RegionNodeVO;
import com.yunshu.modules.system.region.vo.RegionStatsVO;
import com.yunshu.modules.system.region.vo.RegionSyncLogPageVO;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;

public interface SysRegionService {

    List<RegionNodeVO> children(String parentCode);

    List<RegionNodeVO> search(String keyword, Integer limit);

    RegionStatsVO stats();

    /** 从详细地址识别省/市/区县，仅使用本地区划数据 */
    AddressRecognizeVO recognizeAddress(String text);

    RegionImportCheckVO precheck(MultipartFile file, String version);

    RegionImportResultVO importFile(MultipartFile file, String version, Long userId);

    RegionImportResultVO syncOnline(Long userId);

    RegionSyncLogPageVO logs(Integer pageNum, Integer pageSize);

    Map<String, Object> logDetail(Long id);

    SysRegionSyncLog latestLog();
}
