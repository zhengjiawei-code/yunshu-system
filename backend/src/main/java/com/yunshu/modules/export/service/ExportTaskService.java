package com.yunshu.modules.export.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yunshu.common.datascope.DataScopeContext;
import com.yunshu.common.exception.BizException;
import com.yunshu.modules.export.dto.ExportSnapshot;
import com.yunshu.modules.export.dto.ExportTaskCreateRequest;
import com.yunshu.modules.export.entity.BizExportTask;
import com.yunshu.modules.export.mapper.BizExportTaskMapper;
import com.yunshu.modules.export.provider.ExportProvider;
import com.yunshu.modules.export.provider.ExportProviderRegistry;
import com.yunshu.modules.export.vo.ExportTaskVO;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.streaming.SXSSFWorkbook;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.RejectedExecutionException;
import java.util.function.IntConsumer;

/**
 * 异步导出任务：创建校验、线程池执行、xlsx 落盘、状态/进度维护、下载与过期清理。
 * 导出任务表豁免数据权限，所有读写强制 create_by = 当前用户，并在下载时复核机构档位。
 */
@Slf4j
@Service
public class ExportTaskService {

    /** 同一用户同时进行中的任务数上限（排队 + 处理中） */
    private static final int MAX_ACTIVE_TASKS = 2;

    /** 单任务导出行数上限 */
    private static final long MAX_ROWS = 200_000L;

    /** 成功后文件保留天数 */
    private static final int EXPIRE_DAYS = 7;

    /** fail_reason 列长度上限 */
    private static final int FAIL_REASON_MAX = 500;

    /** 我的任务列表返回条数 */
    private static final int RECENT_LIMIT = 20;

    /** SXSSF 内存行窗口 */
    private static final int ROW_WINDOW = 500;

    /** 进度落库步长（行） */
    private static final int PROGRESS_STEP = 1000;

    private final BizExportTaskMapper taskMapper;

    private final ExportProviderRegistry providerRegistry;

    private final ExportFileStorage fileStorage;

    private final ObjectMapper objectMapper;

    private final ThreadPoolTaskExecutor exportExecutor;

    public ExportTaskService(BizExportTaskMapper taskMapper,
                             ExportProviderRegistry providerRegistry,
                             ExportFileStorage fileStorage,
                             ObjectMapper objectMapper,
                             @Qualifier("exportExecutor") ThreadPoolTaskExecutor exportExecutor) {
        this.taskMapper = taskMapper;
        this.providerRegistry = providerRegistry;
        this.fileStorage = fileStorage;
        this.objectMapper = objectMapper;
        this.exportExecutor = exportExecutor;
    }

    /** 下载文件信息 */
    public record DownloadFile(String fileName, Path path) {
    }

    /* ---------------- 创建 ---------------- */

    public Long create(ExportTaskCreateRequest request) {
        DataScopeContext context = DataScopeContext.require("发起导出任务");
        if (request == null || !StringUtils.hasText(request.getTaskType())) {
            throw new BizException(400, "导出类型不能为空");
        }
        ExportProvider provider = providerRegistry.require(request.getTaskType().trim());

        Long active = taskMapper.selectCount(Wrappers.<BizExportTask>lambdaQuery()
                .eq(BizExportTask::getCreateBy, context.getUserId())
                .in(BizExportTask::getStatus, BizExportTask.STATUS_QUEUED, BizExportTask.STATUS_RUNNING));
        if (active != null && active >= MAX_ACTIVE_TASKS) {
            throw new BizException(400, "最多同时执行2个导出任务，请稍后再试");
        }

        ExportSnapshot snapshot = buildSnapshot(context, request);
        // 提交时预估行数：与 write 同口径，超限直接拒绝，避免生成超大文件
        long total = provider.count(snapshot);
        if (total > MAX_ROWS) {
            throw new BizException(400, "导出数据超过20万行，请缩小筛选范围");
        }

        BizExportTask task = new BizExportTask();
        task.setTaskType(provider.taskType());
        task.setQuerySnapshot(toJson(snapshot));
        task.setStatus(BizExportTask.STATUS_QUEUED);
        task.setProcessedRows(0);
        task.setDeptId(context.getDeptId());
        task.setCreateBy(context.getUserId());
        task.setUpdateBy(context.getUserId());
        taskMapper.insert(task);

        submit(task.getId(), provider, snapshot);
        return task.getId();
    }

    private ExportSnapshot buildSnapshot(DataScopeContext context, ExportTaskCreateRequest request) {
        ExportSnapshot snapshot = new ExportSnapshot();
        snapshot.setUserId(context.getUserId());
        snapshot.setUsername(context.getUsername());
        snapshot.setDeptId(context.getDeptId());
        snapshot.setScope(context.getScope());
        snapshot.setSubtreeDeptIds(context.getSubtreeDeptIds() == null
                ? new ArrayList<>()
                : new ArrayList<>(context.getSubtreeDeptIds()));
        snapshot.setFilters(request.getFilters() == null
                ? new LinkedHashMap<>()
                : new LinkedHashMap<>(request.getFilters()));
        if (request.getSort() != null) {
            snapshot.setSortBy(request.getSort().getSortBy());
            snapshot.setSortOrder(request.getSort().getSortOrder());
        }
        snapshot.setColumns(request.getColumns() == null ? new ArrayList<>() : new ArrayList<>(request.getColumns()));
        snapshot.setPageSize(request.getPageSize());
        return snapshot;
    }

    private String toJson(ExportSnapshot snapshot) {
        try {
            return objectMapper.writeValueAsString(snapshot);
        } catch (JsonProcessingException ex) {
            throw new BizException(500, "导出参数序列化失败，请稍后重试");
        }
    }

    private void submit(Long taskId, ExportProvider provider, ExportSnapshot snapshot) {
        try {
            exportExecutor.execute(() -> runTask(taskId, provider, snapshot));
        } catch (RejectedExecutionException ex) {
            log.error("导出任务 {} 提交线程池被拒绝", taskId, ex);
            markFailed(taskId, "导出任务繁忙，请稍后再试", snapshot.getUserId());
            throw new BizException(500, "导出任务繁忙，请稍后再试");
        }
    }

    /* ---------------- 异步执行 ---------------- */

    private void runTask(Long taskId, ExportProvider provider, ExportSnapshot snapshot) {
        // 后台线程没有请求上下文，按快照恢复数据权限；结束必须清理，避免线程复用串号
        DataScopeContext.set(DataScopeContext.of(snapshot.getUserId(), snapshot.getUsername(),
                snapshot.getDeptId(), snapshot.getScope(), toDeptSet(snapshot.getSubtreeDeptIds())));
        String relativePath = null;
        try {
            markRunning(taskId, snapshot.getUserId());
            ExportFileStorage.Target target = fileStorage.prepare(taskId, provider.fileBaseName(), LocalDateTime.now());
            relativePath = target.relativePath();

            int[] processed = {0};
            int[] saved = {0};
            IntConsumer progress = count -> {
                processed[0] = count;
                if (count - saved[0] >= PROGRESS_STEP) {
                    updateProcessed(taskId, count);
                    saved[0] = count;
                }
            };
            writeWorkbook(provider, snapshot, target, progress);

            long size = Files.size(target.path());
            markSuccess(taskId, processed[0], target, size, snapshot.getUserId());
        } catch (Exception ex) {
            log.error("导出任务 {} 执行失败", taskId, ex);
            if (relativePath != null) {
                fileStorage.deleteQuietly(relativePath);
            }
            markFailed(taskId, ex.getMessage(), snapshot.getUserId());
        } finally {
            DataScopeContext.clear();
        }
    }

    private void writeWorkbook(ExportProvider provider, ExportSnapshot snapshot,
                               ExportFileStorage.Target target, IntConsumer progress) throws IOException {
        SXSSFWorkbook workbook = new SXSSFWorkbook(ROW_WINDOW);
        try {
            Sheet sheet = workbook.createSheet(provider.fileBaseName());
            XlsxRowSink sink = new XlsxRowSink(sheet);
            provider.write(snapshot, sink, progress);
            try (OutputStream out = Files.newOutputStream(target.path())) {
                workbook.write(out);
            }
        } finally {
            // 释放临时文件，避免流式写残留
            workbook.dispose();
        }
    }

    /* ---------------- 查询与下载 ---------------- */

    public ExportTaskVO get(Long id) {
        DataScopeContext context = DataScopeContext.require("查询导出任务");
        BizExportTask task = requireOwned(id, context);
        applyExpiry(task);
        return toVO(task);
    }

    public List<ExportTaskVO> recent() {
        DataScopeContext context = DataScopeContext.require("查询导出任务");
        List<BizExportTask> tasks = taskMapper.selectList(Wrappers.<BizExportTask>lambdaQuery()
                .eq(BizExportTask::getCreateBy, context.getUserId())
                .orderByDesc(BizExportTask::getId)
                .last("LIMIT " + RECENT_LIMIT));
        tasks.forEach(this::applyExpiry);
        return tasks.stream().map(this::toVO).toList();
    }

    public DownloadFile download(Long id) {
        DataScopeContext context = DataScopeContext.require("下载导出文件");
        BizExportTask task = requireOwned(id, context);
        applyExpiry(task);

        if (Objects.equals(task.getStatus(), BizExportTask.STATUS_EXPIRED)) {
            throw new BizException(410, "文件已过期，请重新导出");
        }
        if (Objects.equals(task.getStatus(), BizExportTask.STATUS_FAILED)) {
            throw new BizException(400, "导出任务失败：" + failReasonOf(task));
        }
        if (!Objects.equals(task.getStatus(), BizExportTask.STATUS_SUCCESS)) {
            throw new BizException(400, "文件尚未生成，请稍后再试");
        }
        Path path = fileStorage.resolve(task.getFilePath());
        if (path == null || !Files.isRegularFile(path)) {
            log.warn("导出任务 {} 文件缺失：{}", id, task.getFilePath());
            expireTask(task);
            throw new BizException(410, "文件已过期，请重新导出");
        }
        return new DownloadFile(task.getFileName(), path);
    }

    private BizExportTask requireOwned(Long id, DataScopeContext context) {
        BizExportTask task = id == null ? null : taskMapper.selectById(id);
        if (task == null || !Objects.equals(task.getCreateBy(), context.getUserId()) || !scopeAllows(task, context)) {
            // 不泄露任务是否存在
            throw new BizException(403, "无权访问该导出任务");
        }
        return task;
    }

    /** 下载/查看时复核当前机构档位，防止机构切换后用旧任务越权 */
    private boolean scopeAllows(BizExportTask task, DataScopeContext context) {
        Integer scope = context.getScope();
        if (scope == null) {
            return false;
        }
        return switch (scope) {
            case DataScopeContext.SCOPE_ALL -> true;
            case DataScopeContext.SCOPE_DEPT -> Objects.equals(task.getDeptId(), context.getDeptId());
            case DataScopeContext.SCOPE_SUBTREE -> context.containsDept(task.getDeptId());
            case DataScopeContext.SCOPE_SELF -> Objects.equals(task.getCreateBy(), context.getUserId());
            default -> false;
        };
    }

    /* ---------------- 过期清理 ---------------- */

    /** 每日 03:00 把已过期的成功任务置 40 并删文件；DB 记录保留便于审计 */
    @Scheduled(cron = "0 0 3 * * ?")
    public void cleanExpiredTasks() {
        DataScopeContext.runAsSystem(() -> {
            List<BizExportTask> expired = taskMapper.selectList(Wrappers.<BizExportTask>lambdaQuery()
                    .eq(BizExportTask::getStatus, BizExportTask.STATUS_SUCCESS)
                    .isNotNull(BizExportTask::getExpireTime)
                    .lt(BizExportTask::getExpireTime, LocalDateTime.now()));
            expired.forEach(this::expireTask);
            if (!expired.isEmpty()) {
                log.info("导出任务过期清理完成：{} 个", expired.size());
            }
        });
    }

    private void applyExpiry(BizExportTask task) {
        if (task == null || !Objects.equals(task.getStatus(), BizExportTask.STATUS_SUCCESS)
                || task.getExpireTime() == null) {
            return;
        }
        if (task.getExpireTime().isBefore(LocalDateTime.now())) {
            expireTask(task);
        }
    }

    private void expireTask(BizExportTask task) {
        if (task.getFilePath() != null) {
            fileStorage.deleteQuietly(task.getFilePath());
        }
        BizExportTask update = new BizExportTask();
        update.setId(task.getId());
        update.setStatus(BizExportTask.STATUS_EXPIRED);
        taskMapper.updateById(update);
        task.setStatus(BizExportTask.STATUS_EXPIRED);
    }

    /* ---------------- 状态落库 ---------------- */

    private void markRunning(Long taskId, Long userId) {
        BizExportTask update = new BizExportTask();
        update.setId(taskId);
        update.setStatus(BizExportTask.STATUS_RUNNING);
        update.setStartTime(LocalDateTime.now());
        update.setUpdateBy(userId);
        taskMapper.updateById(update);
    }

    private void updateProcessed(Long taskId, int processedRows) {
        taskMapper.update(null, Wrappers.<BizExportTask>lambdaUpdate()
                .eq(BizExportTask::getId, taskId)
                .set(BizExportTask::getProcessedRows, processedRows));
    }

    private void markSuccess(Long taskId, int totalRows, ExportFileStorage.Target target,
                             long fileSize, Long userId) {
        BizExportTask update = new BizExportTask();
        update.setId(taskId);
        update.setStatus(BizExportTask.STATUS_SUCCESS);
        update.setTotalRows(totalRows);
        update.setProcessedRows(totalRows);
        update.setFileName(target.fileName());
        update.setFilePath(target.relativePath());
        update.setFileSize(fileSize);
        update.setExpireTime(LocalDateTime.now().plusDays(EXPIRE_DAYS));
        update.setFinishTime(LocalDateTime.now());
        update.setUpdateBy(userId);
        taskMapper.updateById(update);
    }

    private void markFailed(Long taskId, String reason, Long userId) {
        BizExportTask update = new BizExportTask();
        update.setId(taskId);
        update.setStatus(BizExportTask.STATUS_FAILED);
        update.setFailReason(truncate(reason));
        update.setFinishTime(LocalDateTime.now());
        update.setUpdateBy(userId);
        taskMapper.updateById(update);
    }

    /* ---------------- 工具 ---------------- */

    private ExportTaskVO toVO(BizExportTask task) {
        ExportTaskVO vo = new ExportTaskVO();
        vo.setId(task.getId());
        vo.setTaskType(task.getTaskType());
        vo.setStatus(task.getStatus());
        vo.setStatusName(statusName(task.getStatus()));
        vo.setProcessedRows(task.getProcessedRows());
        vo.setTotalRows(task.getTotalRows());
        vo.setFileName(task.getFileName());
        vo.setFileSize(task.getFileSize());
        vo.setExpireTime(task.getExpireTime());
        vo.setFailReason(task.getFailReason());
        vo.setCreateTime(task.getCreateTime());
        return vo;
    }

    private String statusName(Integer status) {
        if (status == null) {
            return "未知";
        }
        return switch (status) {
            case BizExportTask.STATUS_QUEUED -> "排队中";
            case BizExportTask.STATUS_RUNNING -> "生成中";
            case BizExportTask.STATUS_SUCCESS -> "已完成";
            case BizExportTask.STATUS_FAILED -> "失败";
            case BizExportTask.STATUS_EXPIRED -> "已过期";
            default -> "未知";
        };
    }

    private String failReasonOf(BizExportTask task) {
        return StringUtils.hasText(task.getFailReason()) ? task.getFailReason() : "请重新导出";
    }

    private String truncate(String reason) {
        String text = StringUtils.hasText(reason) ? reason.trim() : "导出失败，请稍后重试";
        return text.length() <= FAIL_REASON_MAX ? text : text.substring(0, FAIL_REASON_MAX);
    }

    private Set<Long> toDeptSet(List<Long> deptIds) {
        if (deptIds == null || deptIds.isEmpty()) {
            return Set.of();
        }
        return new HashSet<>(deptIds);
    }
}
