package com.yunshu.modules.system.region.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yunshu.common.exception.BizException;
import com.yunshu.modules.system.region.entity.SysRegion;
import com.yunshu.modules.system.region.entity.SysRegionSyncLog;
import com.yunshu.modules.system.region.mapper.SysRegionMapper;
import com.yunshu.modules.system.region.mapper.SysRegionSyncLogMapper;
import com.yunshu.modules.system.region.service.SysRegionService;
import com.yunshu.modules.system.region.vo.RegionBriefVO;
import com.yunshu.modules.system.region.vo.RegionImportCheckVO;
import com.yunshu.modules.system.region.vo.RegionImportResultVO;
import com.yunshu.modules.system.region.vo.RegionNodeVO;
import com.yunshu.modules.system.region.vo.RegionStatsVO;
import com.yunshu.modules.system.region.vo.RegionSyncLogPageVO;
import com.yunshu.modules.system.region.vo.RegionSyncLogVO;
import com.yunshu.modules.system.user.entity.SysUser;
import com.yunshu.modules.system.user.mapper.SysUserMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.locks.ReentrantLock;
import java.util.stream.Collectors;

/**
 * 区划数据服务：离线 JSON 幂等导入为主，在线同步默认关闭。
 * 区划表与同步日志均已豁免数据权限，查询不会被追加 dept_id 条件。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SysRegionServiceImpl implements SysRegionService {

    private static final int MAX_SAMPLE = 200;
    private static final int BATCH_SIZE = 500;
    private static final int MAX_ERROR_SAMPLE = 100;

    private final SysRegionMapper regionMapper;
    private final SysRegionSyncLogMapper syncLogMapper;
    private final SysUserMapper userMapper;
    private final ObjectMapper objectMapper;
    private final ReentrantLock importLock = new ReentrantLock();

    @Value("${yunshu.region.online-sync-enabled:false}")
    private boolean onlineSyncEnabled;

    @Value("${yunshu.region.online-data-url:https://raw.githubusercontent.com/modood/Administrative-divisions-of-China/master/dist/pca-code.json}")
    private String onlineDataUrl;

    @Override
    @com.baomidou.dynamic.datasource.annotation.DS("slave")
    public List<RegionNodeVO> children(String parentCode) {
        String parent = StringUtils.hasText(parentCode) ? parentCode.trim() : "0";
        List<SysRegion> all = regionMapper.selectList(Wrappers.<SysRegion>lambdaQuery()
                .eq(SysRegion::getStatus, 1));
        Set<String> parents = all.stream().map(SysRegion::getParentCode).collect(Collectors.toSet());
        return all.stream()
                .filter(item -> Objects.equals(item.getParentCode(), parent))
                .sorted(Comparator.comparing(SysRegion::getCode))
                .map(item -> {
                    RegionNodeVO vo = new RegionNodeVO();
                    vo.setCode(item.getCode());
                    vo.setName(item.getName());
                    vo.setLevel(item.getLevel());
                    vo.setHasChildren(parents.contains(item.getCode()));
                    return vo;
                })
                .toList();
    }

    @Override
    @com.baomidou.dynamic.datasource.annotation.DS("slave")
    public List<RegionNodeVO> search(String keyword, Integer limit) {
        if (!StringUtils.hasText(keyword)) {
            return List.of();
        }
        String text = keyword.trim();
        int size = limit == null || limit < 1 ? 20 : Math.min(limit, 100);
        return regionMapper.selectList(Wrappers.<SysRegion>lambdaQuery()
                        .eq(SysRegion::getStatus, 1)
                        .like(SysRegion::getName, text))
                .stream()
                .sorted(Comparator.comparing(SysRegion::getLevel, Comparator.nullsLast(Integer::compareTo))
                        .thenComparing(SysRegion::getCode))
                .limit(size)
                .map(item -> {
                    RegionNodeVO vo = new RegionNodeVO();
                    vo.setCode(item.getCode());
                    vo.setName(item.getName());
                    vo.setLevel(item.getLevel());
                    vo.setHasChildren(false);
                    return vo;
                })
                .toList();
    }

    @Override
    @com.baomidou.dynamic.datasource.annotation.DS("slave")
    public RegionStatsVO stats() {
        List<SysRegion> all = regionMapper.selectList(null);
        RegionStatsVO vo = new RegionStatsVO();
        vo.setTotal(all.size());
        vo.setProvinceCount((int) all.stream().filter(item -> Objects.equals(item.getLevel(), 1)).count());
        vo.setCityCount((int) all.stream().filter(item -> Objects.equals(item.getLevel(), 2)).count());
        vo.setDistrictCount((int) all.stream().filter(item -> Objects.equals(item.getLevel(), 3)).count());
        vo.setOnlineSyncEnabled(onlineSyncEnabled);
        all.stream()
                .filter(item -> item.getSyncTime() != null)
                .max(Comparator.comparing(SysRegion::getSyncTime))
                .ifPresent(item -> {
                    vo.setLastSyncTime(item.getSyncTime());
                    vo.setVersion(item.getVersion());
                    vo.setSource(item.getSource());
                });
        return vo;
    }

    @Override
    @com.baomidou.dynamic.datasource.annotation.DS("slave")
    public RegionImportCheckVO precheck(MultipartFile file, String version) {
        Parsed parsed = parseFile(file);
        Map<String, SysRegion> existing = loadExistingMap();
        return compare(parsed, existing);
    }

    @Override
    @com.baomidou.dynamic.datasource.annotation.DS("master")
    public RegionImportResultVO importFile(MultipartFile file, String version, Long userId) {
        String fileName = file == null ? null : file.getOriginalFilename();
        try {
            Parsed parsed = parseFile(file);
            return executeImport(parsed, fileName, version, "offline", userId);
        } catch (BizException e) {
            throw e;
        } catch (Exception e) {
            log.error("区划离线导入失败", e);
            throw new BizException("区划导入失败：" + e.getMessage());
        }
    }

    @Override
    @com.baomidou.dynamic.datasource.annotation.DS("master")
    public RegionImportResultVO syncOnline(Long userId) {
        if (!onlineSyncEnabled) {
            throw new BizException("在线同步已关闭，请使用离线 JSON 导入");
        }
        HttpClient client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .build();
        HttpRequest request = HttpRequest.newBuilder(URI.create(onlineDataUrl))
                .timeout(Duration.ofSeconds(10))
                .GET()
                .build();
        byte[] body;
        try {
            HttpResponse<byte[]> response = client.send(request, HttpResponse.BodyHandlers.ofByteArray());
            if (response.statusCode() != 200) {
                throw new BizException("在线同步失败：数据源返回 HTTP " + response.statusCode()
                        + "，请改用离线 JSON 导入");
            }
            body = response.body();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new BizException("在线同步被中断，请改用离线 JSON 导入");
        } catch (IOException e) {
            throw new BizException("在线同步失败：当前无法访问外网数据源，请改用离线 JSON 导入（"
                    + e.getMessage() + "）");
        }
        try {
            Parsed parsed = parseBytes(body);
            String version = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
            return executeImport(parsed, null, version, "online", userId);
        } catch (BizException e) {
            throw e;
        } catch (Exception e) {
            log.error("区划在线同步失败", e);
            throw new BizException("在线同步失败：" + e.getMessage() + "，请改用离线 JSON 导入");
        }
    }

    @Override
    @com.baomidou.dynamic.datasource.annotation.DS("slave")
    public RegionSyncLogPageVO logs(Integer pageNum, Integer pageSize) {
        int current = pageNum == null || pageNum < 1 ? 1 : pageNum;
        int size = pageSize == null || pageSize < 1 ? 20 : Math.min(pageSize, 100);
        Page<SysRegionSyncLog> page = syncLogMapper.selectPage(new Page<>(current, size),
                Wrappers.<SysRegionSyncLog>lambdaQuery()
                        .orderByDesc(SysRegionSyncLog::getStartedAt)
                        .orderByDesc(SysRegionSyncLog::getId));
        Map<Long, String> names = userNames(page.getRecords().stream()
                .map(SysRegionSyncLog::getTriggeredBy)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet()));
        RegionSyncLogPageVO vo = new RegionSyncLogPageVO();
        vo.setTotal(page.getTotal());
        vo.setRecords(page.getRecords().stream().map(item -> {
            RegionSyncLogVO row = new RegionSyncLogVO();
            row.setId(item.getId());
            row.setJobId(item.getJobId());
            row.setTriggerType(item.getTriggerType());
            row.setTriggeredBy(item.getTriggeredBy());
            row.setTriggeredByName(item.getTriggeredBy() == null ? null : names.get(item.getTriggeredBy()));
            row.setFileName(item.getFileName());
            row.setDataVersion(item.getDataVersion());
            row.setStartedAt(item.getStartedAt());
            row.setFinishedAt(item.getFinishedAt());
            row.setStatus(item.getStatus());
            row.setProgress(item.getProgress());
            row.setTotalCount(item.getTotalCount());
            row.setAddedCount(item.getAddedCount());
            row.setChangedCount(item.getChangedCount());
            row.setUnchangedCount(item.getUnchangedCount());
            row.setDisabledCount(item.getDisabledCount());
            row.setErrorMsg(item.getErrorMsg());
            return row;
        }).toList());
        return vo;
    }

    @Override
    @com.baomidou.dynamic.datasource.annotation.DS("slave")
    public Map<String, Object> logDetail(Long id) {
        SysRegionSyncLog log = syncLogMapper.selectById(id);
        if (log == null) {
            throw new BizException("同步日志不存在");
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("id", log.getId());
        result.put("jobId", log.getJobId());
        result.put("triggerType", log.getTriggerType());
        result.put("triggeredBy", log.getTriggeredBy());
        result.put("fileName", log.getFileName());
        result.put("dataVersion", log.getDataVersion());
        result.put("startedAt", log.getStartedAt());
        result.put("finishedAt", log.getFinishedAt());
        result.put("status", log.getStatus());
        result.put("progress", log.getProgress());
        result.put("totalCount", log.getTotalCount());
        result.put("addedCount", log.getAddedCount());
        result.put("changedCount", log.getChangedCount());
        result.put("unchangedCount", log.getUnchangedCount());
        result.put("disabledCount", log.getDisabledCount());
        result.put("errorMsg", log.getErrorMsg());
        if (StringUtils.hasText(log.getReportJson())) {
            try {
                result.put("report", objectMapper.readValue(log.getReportJson(), Object.class));
            } catch (Exception e) {
                result.put("report", log.getReportJson());
            }
        }
        return result;
    }

    @Override
    @com.baomidou.dynamic.datasource.annotation.DS("slave")
    public SysRegionSyncLog latestLog() {
        Page<SysRegionSyncLog> page = syncLogMapper.selectPage(new Page<>(1, 1),
                Wrappers.<SysRegionSyncLog>lambdaQuery()
                        .orderByDesc(SysRegionSyncLog::getStartedAt)
                        .orderByDesc(SysRegionSyncLog::getId));
        return page.getRecords().isEmpty() ? null : page.getRecords().get(0);
    }

    /* ------------------------------------------------------------------ */

    private RegionImportResultVO executeImport(Parsed parsed, String fileName, String version,
                                               String triggerType, Long userId) {
        if (!importLock.tryLock()) {
            throw new BizException("已有区划导入任务正在执行，请稍后再试");
        }
        try {
            if (parsed.invalidCount > 0) {
                throw new BizException("文件校验未通过，共 " + parsed.invalidCount + " 条错误："
                        + String.join("；", parsed.errors.subList(0, Math.min(3, parsed.errors.size()))));
            }
            String dataVersion = StringUtils.hasText(version) ? version.trim()
                    : LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
            Map<String, SysRegion> existing = loadExistingMap();
            RegionImportCheckVO check = compare(parsed, existing);

            SysRegionSyncLog logRow = new SysRegionSyncLog();
            logRow.setJobId(UUID.randomUUID().toString().replace("-", ""));
            logRow.setTriggerType(triggerType);
            logRow.setTriggeredBy(userId);
            logRow.setFileName(fileName);
            logRow.setDataVersion(dataVersion);
            logRow.setStartedAt(LocalDateTime.now());
            logRow.setStatus("running");
            logRow.setProgress(10);
            logRow.setTotalCount(check.getTotal());
            logRow.setAddedCount(check.getAdded());
            logRow.setChangedCount(check.getChanged());
            logRow.setUnchangedCount(check.getUnchanged());
            logRow.setDisabledCount(check.getDisabled());
            syncLogMapper.insert(logRow);

            int done = 0;
            try {
                List<SysRegion> rows = parsed.items.stream()
                        .map(item -> toEntity(item, triggerType, dataVersion))
                        .toList();
                for (int i = 0; i < rows.size(); i += BATCH_SIZE) {
                    List<SysRegion> batch = rows.subList(i, Math.min(i + BATCH_SIZE, rows.size()));
                    regionMapper.upsertBatch(batch);
                    done += batch.size();
                    logRow.setProgress(Math.min(95, 10 + (int) (85.0 * done / Math.max(1, rows.size()))));
                    syncLogMapper.updateById(logRow);
                }
                logRow.setStatus("success");
                logRow.setProgress(100);
                logRow.setFinishedAt(LocalDateTime.now());
                logRow.setReportJson(buildReport(check));
                syncLogMapper.updateById(logRow);

                RegionImportResultVO result = new RegionImportResultVO();
                copyCheck(check, result);
                result.setLogId(logRow.getId());
                result.setJobId(logRow.getJobId());
                result.setStatus("success");
                result.setMessage("导入完成");
                return result;
            } catch (Exception e) {
                logRow.setStatus("partial");
                logRow.setProgress(Math.min(95, 10 + (int) (85.0 * done / Math.max(1, parsed.items.size()))));
                logRow.setFinishedAt(LocalDateTime.now());
                logRow.setErrorMsg(limit(e.getMessage(), 500));
                logRow.setReportJson(buildReport(check));
                syncLogMapper.updateById(logRow);
                throw new BizException("导入中断：已完成 " + done + " 条，可重新导入（按 code 幂等）。原因："
                        + e.getMessage());
            }
        } finally {
            importLock.unlock();
        }
    }

    private Parsed parseFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BizException("请选择区划 JSON 文件");
        }
        try {
            return parseBytes(file.getBytes());
        } catch (IOException e) {
            throw new BizException("读取文件失败：" + e.getMessage());
        }
    }

    private Parsed parseBytes(byte[] bytes) throws IOException {
        JsonNode root = objectMapper.readTree(bytes);
        List<RawItem> rawItems = new ArrayList<>();
        Parsed parsed = new Parsed();
        if (root.isArray()) {
            for (JsonNode node : root) {
                if (node.has("children")) {
                    walkNested(node, "0", 1, null, rawItems, parsed);
                } else {
                    addFlat(node, rawItems, parsed);
                }
            }
        } else if (root.isObject()) {
            if (root.has("children")) {
                walkNested(root, "0", 1, null, rawItems, parsed);
            } else {
                addFlat(root, rawItems, parsed);
            }
        } else {
            parsed.error("文件根节点必须是数组或对象");
        }
        normalizeAndValidate(rawItems, parsed);
        return parsed;
    }

    private void walkNested(JsonNode node, String parentCode, int level, String parentName,
                            List<RawItem> out, Parsed parsed) {
        String code = text(node, "code");
        String name = text(node, "name");
        if (!StringUtils.hasText(code) || !StringUtils.hasText(name)) {
            parsed.error("第 " + (out.size() + 1) + " 条缺少 code 或 name");
            return;
        }
        String normalized = normalizeCode(code);
        if (level == 2 && "市辖区".equals(name) && StringUtils.hasText(parentName)) {
            name = parentName;
        }
        out.add(new RawItem(normalized, name, parentCode, level));
        JsonNode children = node.get("children");
        if (children != null && children.isArray()) {
            for (JsonNode child : children) {
                walkNested(child, normalized, level + 1, name, out, parsed);
            }
        }
    }

    private void addFlat(JsonNode node, List<RawItem> out, Parsed parsed) {
        String code = text(node, "code");
        String name = text(node, "name");
        if (!StringUtils.hasText(code) || !StringUtils.hasText(name)) {
            parsed.error("第 " + (out.size() + 1) + " 条缺少 code 或 name");
            return;
        }
        String rawCode = code.trim();
        int level = node.hasNonNull("level") ? node.get("level").asInt() : inferLevel(rawCode);
        String parentCode = text(node, "parentCode");
        if (!StringUtils.hasText(parentCode)) {
            parentCode = level == 1 ? "0" : "";
        }
        out.add(new RawItem(normalizeCode(rawCode), name.trim(), normalizeCode(parentCode), level));
    }

    private void normalizeAndValidate(List<RawItem> rawItems, Parsed parsed) {
        Set<String> seen = new HashSet<>();
        Map<String, SysRegion> existing = loadExistingMap();
        Map<String, RawItem> byCode = new LinkedHashMap<>();
        for (RawItem item : rawItems) {
            if (!seen.add(item.code)) {
                parsed.error("编码重复：" + item.code);
                continue;
            }
            if (item.level < 1 || item.level > 4) {
                parsed.error("层级非法：" + item.code + " level=" + item.level);
                continue;
            }
            if (item.level == 1) {
                item.parentCode = "0";
            } else if (!StringUtils.hasText(item.parentCode) || "0".equals(item.parentCode)) {
                parsed.error("缺少上级编码：" + item.code);
                continue;
            }
            byCode.put(item.code, item);
        }

        List<RawItem> valid = new ArrayList<>();
        for (RawItem item : byCode.values()) {
            if (item.level == 2 && "市辖区".equals(item.name)) {
                RawItem parent = byCode.get(item.parentCode);
                if (parent != null) {
                    item.name = parent.name;
                } else if (existing.containsKey(item.parentCode)) {
                    item.name = existing.get(item.parentCode).getName();
                }
            }
            if (item.level > 1) {
                RawItem parent = byCode.get(item.parentCode);
                Integer parentLevel = parent == null
                        ? (existing.containsKey(item.parentCode) ? existing.get(item.parentCode).getLevel() : null)
                        : parent.level;
                if (parentLevel == null) {
                    parsed.error("上级区划不存在：" + item.code + " -> " + item.parentCode);
                    continue;
                }
                if (parentLevel != item.level - 1) {
                    parsed.error("层级不匹配：" + item.code + " 的上级层级为 " + parentLevel);
                    continue;
                }
            }
            valid.add(item);
        }

        Map<String, RawItem> merged = new LinkedHashMap<>(byCode);
        Map<String, String> nameMap = new HashMap<>();
        existing.forEach((code, region) -> nameMap.put(code, region.getName()));
        merged.forEach((code, item) -> nameMap.put(code, item.name));
        Map<String, String> parentMap = new HashMap<>();
        existing.forEach((code, region) -> parentMap.put(code, region.getParentCode()));
        merged.forEach((code, item) -> parentMap.put(code, item.parentCode));

        for (RawItem item : valid) {
            String fullName = fullName(item.code, nameMap, parentMap, new HashSet<>());
            if (fullName == null) {
                parsed.error("无法计算全路径：" + item.code);
                continue;
            }
            item.fullName = fullName;
            parsed.items.add(item);
        }
    }

    private RegionImportCheckVO compare(Parsed parsed, Map<String, SysRegion> existing) {
        RegionImportCheckVO check = new RegionImportCheckVO();
        check.setTotal(parsed.items.size());
        List<RegionBriefVO> added = new ArrayList<>();
        List<RegionBriefVO> changed = new ArrayList<>();
        int unchanged = 0;
        Set<String> fileCodes = new HashSet<>();
        Set<Integer> fileLevels = new HashSet<>();
        for (RawItem item : parsed.items) {
            fileCodes.add(item.code);
            fileLevels.add(item.level);
            SysRegion old = existing.get(item.code);
            if (old == null) {
                added.add(brief(item));
            } else if (!Objects.equals(old.getName(), item.name)
                    || !Objects.equals(old.getParentCode(), item.parentCode)
                    || !Objects.equals(old.getLevel(), item.level)
                    || !Objects.equals(old.getFullName(), item.fullName)) {
                changed.add(brief(item));
            } else {
                unchanged++;
            }
        }
        List<RegionBriefVO> disabled = existing.values().stream()
                .filter(item -> Objects.equals(item.getStatus(), 1))
                .filter(item -> fileLevels.contains(item.getLevel()))
                .filter(item -> !fileCodes.contains(item.getCode()))
                .sorted(Comparator.comparing(SysRegion::getCode))
                .map(item -> {
                    RegionBriefVO brief = new RegionBriefVO();
                    brief.setCode(item.getCode());
                    brief.setName(item.getName());
                    brief.setFullName(item.getFullName());
                    brief.setLevel(item.getLevel());
                    return brief;
                })
                .toList();

        check.setAdded(added.size());
        check.setChanged(changed.size());
        check.setUnchanged(unchanged);
        check.setDisabled(disabled.size());
        check.setInvalid(parsed.invalidCount);
        check.setErrors(new ArrayList<>(parsed.errors));
        check.setAddedList(limit(added));
        check.setChangedList(limit(changed));
        check.setDisabledList(limit(disabled));
        check.setTruncated(added.size() > MAX_SAMPLE || changed.size() > MAX_SAMPLE
                || disabled.size() > MAX_SAMPLE || parsed.errors.size() > MAX_SAMPLE);
        return check;
    }

    private Map<String, SysRegion> loadExistingMap() {
        Map<String, SysRegion> map = new HashMap<>();
        regionMapper.selectList(null).forEach(item -> map.put(item.getCode(), item));
        return map;
    }

    private SysRegion toEntity(RawItem item, String source, String version) {
        SysRegion region = new SysRegion();
        region.setCode(item.code);
        region.setName(item.name);
        region.setParentCode(item.parentCode);
        region.setLevel(item.level);
        region.setFullName(item.fullName);
        region.setSource(source);
        region.setVersion(version);
        region.setSyncTime(LocalDateTime.now());
        region.setStatus(1);
        return region;
    }

    private String fullName(String code, Map<String, String> names, Map<String, String> parents,
                            Set<String> visiting) {
        String name = names.get(code);
        if (name == null || !visiting.add(code)) {
            return null;
        }
        try {
            String parent = parents.get(code);
            if (parent == null || "0".equals(parent)) {
                return name;
            }
            String parentFull = fullName(parent, names, parents, visiting);
            if (parentFull == null) {
                return null;
            }
            String parentName = names.get(parent);
            return Objects.equals(parentName, name) ? parentFull : parentFull + name;
        } finally {
            visiting.remove(code);
        }
    }

    private String buildReport(RegionImportCheckVO check) {
        Map<String, Object> report = new LinkedHashMap<>();
        report.put("added", check.getAddedList());
        report.put("changed", check.getChangedList());
        report.put("disabled", check.getDisabledList());
        report.put("errors", check.getErrors());
        report.put("truncated", check.getTruncated());
        try {
            return objectMapper.writeValueAsString(report);
        } catch (Exception e) {
            return "{}";
        }
    }

    private void copyCheck(RegionImportCheckVO source, RegionImportCheckVO target) {
        target.setTotal(source.getTotal());
        target.setAdded(source.getAdded());
        target.setChanged(source.getChanged());
        target.setUnchanged(source.getUnchanged());
        target.setDisabled(source.getDisabled());
        target.setInvalid(source.getInvalid());
        target.setErrors(source.getErrors());
        target.setAddedList(source.getAddedList());
        target.setChangedList(source.getChangedList());
        target.setDisabledList(source.getDisabledList());
        target.setTruncated(source.getTruncated());
    }

    private RegionBriefVO brief(RawItem item) {
        RegionBriefVO vo = new RegionBriefVO();
        vo.setCode(item.code);
        vo.setName(item.name);
        vo.setFullName(item.fullName);
        vo.setLevel(item.level);
        return vo;
    }

    private List<RegionBriefVO> limit(List<RegionBriefVO> list) {
        return list.size() <= MAX_SAMPLE ? list : new ArrayList<>(list.subList(0, MAX_SAMPLE));
    }

    private Map<Long, String> userNames(Set<Long> ids) {
        if (ids.isEmpty()) {
            return new HashMap<>();
        }
        Map<Long, String> names = new HashMap<>();
        userMapper.selectBatchIds(ids).forEach(user -> names.put(user.getId(), user.getRealName()));
        return names;
    }

    private String text(JsonNode node, String field) {
        JsonNode value = node.get(field);
        return value == null || value.isNull() ? null : value.asText();
    }

    private int inferLevel(String code) {
        int length = code == null ? 0 : code.replaceAll("\\D", "").length();
        return switch (length) {
            case 2 -> 1;
            case 4 -> 2;
            case 6 -> 3;
            default -> 0;
        };
    }

    private String normalizeCode(String raw) {
        if (!StringUtils.hasText(raw)) {
            return "";
        }
        String code = raw.trim();
        if ("0".equals(code)) {
            return "0";
        }
        if (!code.matches("\\d{2,12}")) {
            return code;
        }
        return switch (code.length()) {
            case 2 -> code + "0000";
            case 4 -> code + "00";
            default -> code;
        };
    }

    private String limit(String text, int max) {
        if (text == null) {
            return null;
        }
        return text.length() <= max ? text : text.substring(0, max);
    }

    private static final class RawItem {
        private final String code;
        private String name;
        private String parentCode;
        private final Integer level;
        private String fullName;

        private RawItem(String code, String name, String parentCode, Integer level) {
            this.code = code;
            this.name = name;
            this.parentCode = parentCode;
            this.level = level;
        }
    }

    private static final class Parsed {
        private final List<RawItem> items = new ArrayList<>();
        private final List<String> errors = new ArrayList<>();
        private int invalidCount;

        private void error(String message) {
            invalidCount++;
            if (errors.size() < MAX_ERROR_SAMPLE) {
                errors.add(message);
            }
        }
    }
}
