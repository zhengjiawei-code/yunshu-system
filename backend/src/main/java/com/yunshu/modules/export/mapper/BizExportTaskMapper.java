package com.yunshu.modules.export.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.yunshu.modules.export.entity.BizExportTask;

/**
 * 导出任务 Mapper：biz_export_task 已登记数据权限豁免，查询/下载由服务层强制归属校验。
 */
public interface BizExportTaskMapper extends BaseMapper<BizExportTask> {
}
