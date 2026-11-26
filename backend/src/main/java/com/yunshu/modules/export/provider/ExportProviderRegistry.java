package com.yunshu.modules.export.provider;

import com.yunshu.common.exception.BizException;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 导出 provider 注册表：构造器注入全部实现，按 taskType 分发；未知类型直接拒绝。
 */
@Component
public class ExportProviderRegistry {

    private final Map<String, ExportProvider> providers;

    public ExportProviderRegistry(List<ExportProvider> providerList) {
        Map<String, ExportProvider> map = new LinkedHashMap<>();
        for (ExportProvider provider : providerList) {
            if (provider == null || provider.taskType() == null) {
                continue;
            }
            ExportProvider exists = map.putIfAbsent(provider.taskType(), provider);
            if (exists != null) {
                throw new IllegalStateException("导出类型重复注册：" + provider.taskType());
            }
        }
        this.providers = Map.copyOf(map);
    }

    /** 按类型取 provider，未知类型抛业务异常 */
    public ExportProvider require(String taskType) {
        ExportProvider provider = taskType == null ? null : providers.get(taskType.trim());
        if (provider == null) {
            throw new BizException(400, "不支持的导出类型：" + taskType);
        }
        return provider;
    }
}
