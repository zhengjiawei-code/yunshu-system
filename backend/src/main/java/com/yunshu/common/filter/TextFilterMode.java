package com.yunshu.common.filter;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * 文本列筛选模式：与前端 {@code utils/textFilter.ts} 保持同一口径。
 *
 * <p>第一段只提供解析与匹配能力，不改变任何页面现有查询条件。</p>
 */
public enum TextFilterMode {

    /** 模糊包含 */
    LIKE,
    /** 等于 */
    EXACT,
    /** 不等于（SQL 口径：null / 空值不命中） */
    NE,
    /** 左匹配：值以关键字开头 */
    LEFT,
    /** 右匹配：值以关键字结尾 */
    RIGHT,
    /** 批量：多个值精确匹配命中任意一个 */
    BATCH;

    /** 批量查询单列上限，超过直接报错，不静默截断 */
    public static final int BATCH_LIMIT = 500;

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final String BATCH_SPLIT_REGEX = "[,，;；、\\s]+";

    /**
     * 解析前端传入的 filterModes JSON。
     * 空值、非法 JSON 返回空 Map；未知模式按 LIKE 处理。
     */
    public static Map<String, TextFilterMode> parse(String json) {
        if (json == null || json.isBlank()) {
            return Collections.emptyMap();
        }
        try {
            Map<String, String> raw = MAPPER.readValue(json, new TypeReference<Map<String, String>>() {
            });
            Map<String, TextFilterMode> result = new HashMap<>();
            raw.forEach((field, mode) -> {
                if (field == null || field.isBlank()) {
                    return;
                }
                result.put(field, parseMode(mode));
            });
            return result;
        } catch (Exception ex) {
            return Collections.emptyMap();
        }
    }

    /** 单个模式值解析；未知或空值回退 LIKE。 */
    public static TextFilterMode parseMode(String value) {
        if (value == null || value.isBlank()) {
            return LIKE;
        }
        try {
            return valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            return LIKE;
        }
    }

    /** 取某字段的模式，未配置时默认 LIKE。 */
    public static TextFilterMode modeOf(Map<String, TextFilterMode> modes, String field) {
        if (modes == null || field == null) {
            return LIKE;
        }
        return modes.getOrDefault(field, LIKE);
    }

    /**
     * 判断原始值是否命中关键字。
     *
     * <p>关键字为空不产生条件；空值在 LIKE / EXACT / LEFT / RIGHT / BATCH 下不命中，
     * 在 NE 下按 SQL 口径也不命中。批量超过 {@link #BATCH_LIMIT} 抛
     * {@link IllegalArgumentException}。</p>
     */
    public static boolean match(String raw, String keyword, TextFilterMode mode) {
        if (keyword == null || keyword.trim().isEmpty()) {
            return true;
        }
        TextFilterMode effective = mode == null ? LIKE : mode;
        String expected = keyword.trim();
        if (effective == BATCH) {
            return matchBatch(raw, expected);
        }
        if (raw == null) {
            return false;
        }
        String actual = raw;
        return switch (effective) {
            case EXACT -> actual.equalsIgnoreCase(expected);
            case NE -> !actual.trim().isEmpty() && !actual.equalsIgnoreCase(expected);
            case LEFT -> actual.regionMatches(true, 0, expected, 0, expected.length());
            case RIGHT -> actual.toLowerCase(Locale.ROOT).endsWith(expected.toLowerCase(Locale.ROOT));
            case LIKE -> actual.toLowerCase(Locale.ROOT).contains(expected.toLowerCase(Locale.ROOT));
            default -> false;
        };
    }

    /**
     * 拆分批量关键字：按 ,，;；、空白/换行 拆分，去重并忽略大小写。
     * 供需要把条件落到 SQL 的页面复用；超过 {@link #BATCH_LIMIT} 直接抛错。
     */
    public static List<String> batchValues(String keyword) {
        if (keyword == null || keyword.trim().isEmpty()) {
            return List.of();
        }
        Set<String> values = new LinkedHashSet<>();
        for (String part : keyword.split(BATCH_SPLIT_REGEX)) {
            String value = part.trim();
            if (!value.isEmpty()) {
                values.add(value.toLowerCase(Locale.ROOT));
            }
        }
        if (values.size() > BATCH_LIMIT) {
            throw new IllegalArgumentException("批量查询最多 " + BATCH_LIMIT + " 个值");
        }
        return new ArrayList<>(values);
    }

    private static boolean matchBatch(String raw, String keyword) {
        Set<String> values = new LinkedHashSet<>(batchValues(keyword));
        if (values.isEmpty()) {
            return true;
        }
        if (raw == null) {
            return false;
        }
        return values.contains(raw.toLowerCase(Locale.ROOT));
    }
}
