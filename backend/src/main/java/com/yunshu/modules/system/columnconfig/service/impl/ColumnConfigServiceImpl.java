package com.yunshu.modules.system.columnconfig.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yunshu.common.exception.BizException;
import com.yunshu.modules.system.columnconfig.entity.SysColumnConfig;
import com.yunshu.modules.system.columnconfig.mapper.SysColumnConfigMapper;
import com.yunshu.modules.system.columnconfig.service.ColumnConfigService;
import com.yunshu.modules.system.columnconfig.vo.ColumnConfigVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.Set;

/**
 * 列配置服务：只读写当前登录用户自己的配置，页面标识白名单校验。
 * 表 sys_column_config 已登记数据权限豁免，可见性由这里的 userId 条件负责。
 */
@Service
@RequiredArgsConstructor
public class ColumnConfigServiceImpl implements ColumnConfigService {

    /** 已接入统一列设置的页面标识；新增页面必须在此登记，避免脏 key 写库 */
    static final Set<String> PAGE_KEYS = Set.of(
            "system:menu",
            "system:user",
            "system:org",
            "system:role",
            "system:carrier",
            "system:route",
            "system:route-transfer",
            "system:route-library",
            "operation:waybill",
            "transport.loading",
            "transport.loading.pool",
            "transport.loading.loaded",
            "transport.feeType"
    );

    private static final int MAX_CONFIG_LENGTH = 64 * 1024;

    private final SysColumnConfigMapper mapper;
    private final ObjectMapper objectMapper;

    @Override
    public ColumnConfigVO get(Long userId, String pageKey) {
        String key = normalizePageKey(pageKey);
        SysColumnConfig row = mapper.selectOne(Wrappers.<SysColumnConfig>lambdaQuery()
                .eq(SysColumnConfig::getUserId, requireUser(userId))
                .eq(SysColumnConfig::getPageKey, key));
        ColumnConfigVO vo = new ColumnConfigVO();
        vo.setPageKey(key);
        vo.setCustomized(row != null);
        if (row != null) {
            vo.setConfig(row.getConfigJson());
            vo.setUpdateTime(row.getUpdateTime());
        }
        return vo;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ColumnConfigVO save(Long userId, String pageKey, String configJson) {
        String key = normalizePageKey(pageKey);
        Long uid = requireUser(userId);
        String json = validateConfig(configJson);

        SysColumnConfig row = mapper.selectOne(Wrappers.<SysColumnConfig>lambdaQuery()
                .eq(SysColumnConfig::getUserId, uid)
                .eq(SysColumnConfig::getPageKey, key));
        if (row == null) {
            row = new SysColumnConfig();
            row.setUserId(uid);
            row.setPageKey(key);
            row.setConfigJson(json);
            mapper.insert(row);
        } else {
            row.setConfigJson(json);
            mapper.updateById(row);
        }
        return get(uid, key);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void reset(Long userId, String pageKey) {
        String key = normalizePageKey(pageKey);
        mapper.delete(Wrappers.<SysColumnConfig>lambdaQuery()
                .eq(SysColumnConfig::getUserId, requireUser(userId))
                .eq(SysColumnConfig::getPageKey, key));
    }

    private Long requireUser(Long userId) {
        if (userId == null) {
            throw new BizException(401, "登录已失效，请重新登录");
        }
        return userId;
    }

    private String normalizePageKey(String pageKey) {
        if (!StringUtils.hasText(pageKey)) {
            throw new BizException("页面标识不能为空");
        }
        String key = pageKey.trim();
        if (!PAGE_KEYS.contains(key)) {
            throw new BizException("未登记的页面标识：" + key);
        }
        return key;
    }

    private String validateConfig(String configJson) {
        if (!StringUtils.hasText(configJson)) {
            throw new BizException("列配置不能为空");
        }
        String json = configJson.trim();
        if (json.length() > MAX_CONFIG_LENGTH) {
            throw new BizException("列配置过大，单页上限 64KB");
        }
        try {
            objectMapper.readTree(json);
        } catch (Exception ex) {
            throw new BizException("列配置不是合法 JSON");
        }
        return json;
    }
}
