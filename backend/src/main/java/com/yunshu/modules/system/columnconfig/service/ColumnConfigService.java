package com.yunshu.modules.system.columnconfig.service;

import com.yunshu.modules.system.columnconfig.vo.ColumnConfigVO;

public interface ColumnConfigService {

    /** 读取当前用户在某页面的列配置；不存在时 customized=false */
    ColumnConfigVO get(Long userId, String pageKey);

    /** 保存（新增或覆盖）当前用户在某页面的列配置 */
    ColumnConfigVO save(Long userId, String pageKey, String configJson);

    /** 恢复默认：物理删除当前用户在该页面的配置行 */
    void reset(Long userId, String pageKey);
}
