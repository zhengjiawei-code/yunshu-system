package com.yunshu.common.filter;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TextFilterModeTest {

    @Test
    void parseNullBlankAndInvalidReturnsEmpty() {
        assertTrue(TextFilterMode.parse(null).isEmpty());
        assertTrue(TextFilterMode.parse("  ").isEmpty());
        assertTrue(TextFilterMode.parse("not-json").isEmpty());
    }

    @Test
    void parseUnknownModeFallsBackToLike() {
        Map<String, TextFilterMode> modes = TextFilterMode.parse(
                "{\"username\":\"exact\",\"status\":\"unknown\",\"\":\"batch\"}");

        assertEquals(TextFilterMode.EXACT, modes.get("username"));
        assertEquals(TextFilterMode.LIKE, modes.get("status"));
        assertFalse(modes.containsKey(""));
    }

    @Test
    void modeOfDefaultsToLike() {
        assertEquals(TextFilterMode.LIKE, TextFilterMode.modeOf(Map.of(), "username"));
        assertEquals(TextFilterMode.LIKE, TextFilterMode.modeOf(null, "username"));
        assertEquals(TextFilterMode.LEFT,
                TextFilterMode.modeOf(Map.of("username", TextFilterMode.LEFT), "username"));
    }

    @Test
    void blankKeywordNeverFilters() {
        for (TextFilterMode mode : TextFilterMode.values()) {
            assertTrue(TextFilterMode.match(null, "  ", mode), mode.name());
            assertTrue(TextFilterMode.match("abc", null, mode), mode.name());
        }
    }

    @Test
    void likeIsCaseInsensitive() {
        assertTrue(TextFilterMode.match("ZhangSan", "zhangsan", TextFilterMode.LIKE));
        assertTrue(TextFilterMode.match("ZhangSan", "ANG", TextFilterMode.LIKE));
        assertFalse(TextFilterMode.match(null, "zhang", TextFilterMode.LIKE));
    }

    @Test
    void exactIsCaseInsensitive() {
        assertTrue(TextFilterMode.match("A001", "a001", TextFilterMode.EXACT));
        assertFalse(TextFilterMode.match("A001", "A00", TextFilterMode.EXACT));
        assertFalse(TextFilterMode.match(null, "A001", TextFilterMode.EXACT));
    }

    @Test
    void notEqualsFollowsSqlNullSemantics() {
        assertTrue(TextFilterMode.match("A002", "A001", TextFilterMode.NE));
        assertFalse(TextFilterMode.match("a001", "A001", TextFilterMode.NE));
        assertFalse(TextFilterMode.match(null, "A001", TextFilterMode.NE));
        assertFalse(TextFilterMode.match("", "A001", TextFilterMode.NE));
        assertFalse(TextFilterMode.match("   ", "A001", TextFilterMode.NE));
    }

    @Test
    void leftAndRightMatch() {
        assertTrue(TextFilterMode.match("HelloWorld", "hello", TextFilterMode.LEFT));
        assertFalse(TextFilterMode.match("HelloWorld", "world", TextFilterMode.LEFT));
        assertTrue(TextFilterMode.match("HelloWorld", "WORLD", TextFilterMode.RIGHT));
        assertFalse(TextFilterMode.match("HelloWorld", "hello", TextFilterMode.RIGHT));
    }

    @Test
    void keywordIsTrimmed() {
        assertTrue(TextFilterMode.match("abc", "  abc  ", TextFilterMode.EXACT));
        assertTrue(TextFilterMode.match("xabcx", "  abc  ", TextFilterMode.LIKE));
    }

    @Test
    void batchSupportsAllSeparators() {
        String keyword = "A1，A2;A3；A4、A5 A6\nA7";

        assertTrue(TextFilterMode.match("a5", keyword, TextFilterMode.BATCH));
        assertTrue(TextFilterMode.match("A7", keyword, TextFilterMode.BATCH));
        assertFalse(TextFilterMode.match("A8", keyword, TextFilterMode.BATCH));
        assertFalse(TextFilterMode.match(null, keyword, TextFilterMode.BATCH));
    }

    @Test
    void batchOnlySeparatorsIsNoCondition() {
        assertTrue(TextFilterMode.match(null, " , ; 、 ", TextFilterMode.BATCH));
    }

    @Test
    void batchDeduplicatesBeforeLimitCheck() {
        StringBuilder keyword = new StringBuilder();
        for (int i = 1; i <= TextFilterMode.BATCH_LIMIT; i++) {
            if (i > 1) {
                keyword.append(',');
            }
            keyword.append('v').append(i);
        }
        keyword.append(",V1");

        assertTrue(TextFilterMode.match("v1", keyword.toString(), TextFilterMode.BATCH));
    }

    @Test
    void batchOverLimitThrows() {
        StringBuilder keyword = new StringBuilder();
        for (int i = 1; i <= TextFilterMode.BATCH_LIMIT + 1; i++) {
            if (i > 1) {
                keyword.append(',');
            }
            keyword.append('v').append(i);
        }

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> TextFilterMode.match("v1", keyword.toString(), TextFilterMode.BATCH));
        assertTrue(ex.getMessage().contains("500"));
    }
}
