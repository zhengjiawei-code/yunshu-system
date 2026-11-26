package com.yunshu.modules.transport.service;

import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.yunshu.common.exception.BizException;
import com.yunshu.modules.system.route.entity.SysRoute;
import com.yunshu.modules.system.route.mapper.SysRouteMapper;
import com.yunshu.modules.transport.dto.VolumeRatioSaveRequest;
import com.yunshu.modules.transport.entity.SysConfig;
import com.yunshu.modules.transport.mapper.SysConfigMapper;
import com.yunshu.modules.transport.vo.VolumeRatioVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * 重泡比配置（#31/#32）：全局默认存 sys_config.fee.volume_weight_ratio（默认 333），
 * 线路级存 sys_route.volume_weight_ratio；线路为空或 0 时回落全局。
 */
@Service
@RequiredArgsConstructor
public class VolumeRatioService {

    public static final String CONFIG_KEY = "fee.volume_weight_ratio";
    private static final BigDecimal DEFAULT_RATIO = new BigDecimal("333");

    private final SysConfigMapper configMapper;
    private final SysRouteMapper routeMapper;

    public VolumeRatioVO get() {
        BigDecimal global = globalRatio();
        List<SysRoute> routes = routeMapper.selectList(Wrappers.<SysRoute>lambdaQuery()
                .orderByAsc(SysRoute::getId)
                .last("LIMIT 500"));
        List<VolumeRatioVO.RouteRow> rows = new ArrayList<>();
        for (SysRoute route : routes) {
            VolumeRatioVO.RouteRow row = new VolumeRatioVO.RouteRow();
            row.setRouteId(route.getId());
            row.setRouteCode(route.getRouteCode());
            row.setRouteName(route.getRouteName());
            row.setVolumeWeightRatio(route.getVolumeWeightRatio());
            row.setEffectiveRatio(effective(route.getVolumeWeightRatio(), global));
            rows.add(row);
        }
        VolumeRatioVO vo = new VolumeRatioVO();
        vo.setGlobalRatio(global);
        vo.setRoutes(rows);
        return vo;
    }

    @Transactional(rollbackFor = Exception.class)
    public VolumeRatioVO save(VolumeRatioSaveRequest request) {
        BigDecimal global = request.getGlobalRatio();
        if (global == null || global.signum() <= 0) {
            throw new BizException(400, "全局重泡比必须大于0");
        }
        if (request.getRoutes() != null) {
            for (VolumeRatioSaveRequest.RouteItem item : request.getRoutes()) {
                if (item.getVolumeWeightRatio() != null && item.getVolumeWeightRatio().signum() <= 0) {
                    throw new BizException(400, "线路重泡比必须大于0或留空");
                }
                long exists = routeMapper.selectCount(Wrappers.<SysRoute>lambdaQuery()
                        .eq(SysRoute::getId, item.getRouteId()));
                if (exists == 0) {
                    throw new BizException(400, "线路不存在：" + item.getRouteId());
                }
                routeMapper.update(null, new UpdateWrapper<SysRoute>()
                        .eq("id", item.getRouteId())
                        .set("volume_weight_ratio", item.getVolumeWeightRatio()));
            }
        }

        SysConfig config = configMapper.selectOne(Wrappers.<SysConfig>lambdaQuery()
                .eq(SysConfig::getConfigKey, CONFIG_KEY)
                .last("LIMIT 1"));
        String value = global.stripTrailingZeros().toPlainString();
        if (config == null) {
            config = new SysConfig();
            config.setConfigKey(CONFIG_KEY);
            config.setConfigValue(value);
            config.setConfigName("全局默认重泡比(kg/m³)");
            config.setValueType("number");
            config.setRemark("取值>0；线路未配置时回落此值");
            configMapper.insert(config);
        } else {
            configMapper.update(null, new UpdateWrapper<SysConfig>()
                    .eq("id", config.getId())
                    .set("config_value", value));
        }
        return get();
    }

    public BigDecimal globalRatio() {
        SysConfig config = configMapper.selectOne(Wrappers.<SysConfig>lambdaQuery()
                .eq(SysConfig::getConfigKey, CONFIG_KEY)
                .last("LIMIT 1"));
        if (config == null || !StringUtils.hasText(config.getConfigValue())) {
            return DEFAULT_RATIO;
        }
        try {
            BigDecimal value = new BigDecimal(config.getConfigValue().trim());
            return value.signum() <= 0 ? DEFAULT_RATIO : value;
        } catch (NumberFormatException ex) {
            return DEFAULT_RATIO;
        }
    }

    private BigDecimal effective(BigDecimal routeRatio, BigDecimal global) {
        return routeRatio == null || routeRatio.signum() <= 0 ? global : routeRatio;
    }
}
