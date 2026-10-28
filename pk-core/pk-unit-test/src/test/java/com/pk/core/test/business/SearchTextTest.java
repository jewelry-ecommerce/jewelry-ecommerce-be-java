package com.pk.core.test.business;

import com.pk.core.business.service.impl.SearchText;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Chuẩn hoá từ khoá tìm kiếm: gọn khoảng trắng, chữ thường, bỏ dấu tiếng Việt, giới hạn độ dài, escape LIKE. */
class SearchTextTest {

    @Test
    void cleanTrimsCollapsesAndLowercases() {
        assertEquals("nhẫn kim cương", SearchText.clean("  Nhẫn   KIM\tcương \n"));
        assertEquals("a1", SearchText.clean("A1"));
    }

    @Test
    void cleanRejectsTooShortTooLongOrSymbolOnly() {
        assertNull(SearchText.clean(null));
        assertNull(SearchText.clean(" a "));
        assertNull(SearchText.clean("   "));
        assertNull(SearchText.clean("!!!"));
        assertNull(SearchText.clean("x".repeat(101)));
        assertEquals(100, SearchText.clean("x".repeat(100)).length());
    }

    @Test
    void keyStripsVietnameseDiacritics() {
        assertEquals("nhan kim cuong", SearchText.key("nhẫn kim cương"));
        assertEquals("dong ho", SearchText.key("đồng hồ"));
        assertEquals(SearchText.key("nhẫn"), SearchText.key("nhan"));
    }

    @Test
    void diacriticsDetectionPrefersAccentedDisplay() {
        assertTrue(SearchText.hasDiacritics("nhẫn"));
        assertFalse(SearchText.hasDiacritics("nhan"));
    }

    @Test
    void likeEscapeNeutralisesWildcards() {
        assertEquals("100!% !_ !!", SearchText.likeEscape("100% _ !"));
    }
}
