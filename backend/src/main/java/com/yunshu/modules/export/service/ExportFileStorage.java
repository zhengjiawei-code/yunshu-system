package com.yunshu.modules.export.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * 导出文件落盘：目录取配置 yunshu.export.dir（默认 ${user.home}/.yunshu/export），按日期分桶。
 * DB 只存相对路径（如 20261126/运单列表_20261126143000_12.xlsx），下载时再拼绝对路径。
 */
@Slf4j
@Component
public class ExportFileStorage {

    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("yyyyMMdd");

    private static final DateTimeFormatter TIMESTAMP = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    private final Path baseDir;

    public ExportFileStorage(@Value("${yunshu.export.dir:}") String configuredDir) {
        String dir = configuredDir == null || configuredDir.isBlank()
                ? System.getProperty("user.home") + "/.yunshu/export"
                : configuredDir.trim();
        this.baseDir = Paths.get(dir).toAbsolutePath().normalize();
    }

    /** 本次导出目标文件 */
    public record Target(String fileName, String relativePath, Path path) {
    }

    /** 按 taskId 与当前时间生成目录/文件名，并创建目录 */
    public Target prepare(Long taskId, String baseName, LocalDateTime now) throws IOException {
        String day = now.format(DAY);
        Path dir = baseDir.resolve(day);
        Files.createDirectories(dir);
        String fileName = baseName + "_" + now.format(TIMESTAMP) + "_" + taskId + ".xlsx";
        return new Target(fileName, day + "/" + fileName, dir.resolve(fileName));
    }

    /** 相对路径 -> 绝对路径；越界或非法路径返回 null */
    public Path resolve(String relativePath) {
        if (relativePath == null || relativePath.isBlank()) {
            return null;
        }
        Path file = baseDir.resolve(relativePath).normalize();
        return file.startsWith(baseDir) ? file : null;
    }

    /** 删除文件，失败只记日志（清理/失败回滚不应影响任务状态落库） */
    public void deleteQuietly(String relativePath) {
        Path file = resolve(relativePath);
        if (file == null) {
            return;
        }
        try {
            Files.deleteIfExists(file);
        } catch (IOException ex) {
            log.warn("导出文件删除失败：{}", file, ex);
        }
    }
}
