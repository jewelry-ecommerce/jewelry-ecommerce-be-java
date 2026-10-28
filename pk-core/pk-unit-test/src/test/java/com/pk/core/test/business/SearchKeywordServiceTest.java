package com.pk.core.test.business;

import com.pk.core.business.repository.SearchHistoryRepo;
import com.pk.core.business.repository.SearchKeywordRepo;
import com.pk.core.business.service.CategoryService;
import com.pk.core.business.service.impl.SearchKeywordServiceImpl;
import com.pk.core.model.dto.response.CategoryResponseDto;
import com.pk.core.model.dto.response.SearchSuggestionResponseDto;
import com.pk.core.model.entity.SearchHistoryEntity;
import com.pk.core.model.entity.SearchKeywordEntity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/** Ghi nhận từ khoá (khách/tài khoản), trending, gợi ý (danh mục + từ khoá, không phân biệt dấu) và xoá lịch sử. */
@ExtendWith(MockitoExtension.class)
class SearchKeywordServiceTest {

    @Mock SearchKeywordRepo keywords;
    @Mock SearchHistoryRepo history;
    @Mock CategoryService categories;
    SearchKeywordServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new SearchKeywordServiceImpl(keywords, history, categories);
    }

    private static SearchKeywordEntity kw(String keyword, String key) {
        SearchKeywordEntity e = new SearchKeywordEntity();
        e.setKeyword(keyword);
        e.setKeywordKey(key);
        return e;
    }

    private static CategoryResponseDto cat(String name, String slug) {
        CategoryResponseDto c = new CategoryResponseDto();
        c.setName(name);
        c.setSlug(slug);
        return c;
    }

    @Test
    void guestSearchBumpsKeywordButWritesNoHistory() {
        when(keywords.bump("nhan kim cuong")).thenReturn(1);
        service.record(null, "  Nhẫn  Kim Cương ");
        verify(keywords).bump("nhan kim cuong");
        // Có dấu -> dạng hiển thị được cập nhật cho đẹp ("nhan" trước đó thành "nhẫn").
        verify(keywords).rename("nhan kim cuong", "nhẫn kim cương");
        verify(keywords, never()).create(any());
        verifyNoInteractions(history);
    }

    @Test
    void newKeywordIsCreatedAndAccountHistoryIsTouchedCreatedAndTrimmed() {
        when(keywords.bump(anyString())).thenReturn(0);
        when(history.touch(eq(5L), anyString(), anyString())).thenReturn(0);
        service.record(5L, "Charm");

        ArgumentCaptor<SearchKeywordEntity> k = ArgumentCaptor.forClass(SearchKeywordEntity.class);
        verify(keywords).create(k.capture());
        assertEquals("charm", k.getValue().getKeyword());
        assertEquals("charm", k.getValue().getKeywordKey());
        ArgumentCaptor<SearchHistoryEntity> h = ArgumentCaptor.forClass(SearchHistoryEntity.class);
        verify(history).create(h.capture());
        assertEquals(5L, h.getValue().getUserId());
        assertEquals("charm", h.getValue().getKeyword());
        verify(history).trimTo(5L, 20);
    }

    @Test
    void existingHistoryRowIsOnlyTouched() {
        when(keywords.bump(anyString())).thenReturn(1);
        when(history.touch(eq(5L), anyString(), anyString())).thenReturn(1);
        service.record(5L, "charm");
        verify(keywords, never()).rename(anyString(), anyString());
        verify(history, never()).create(any());
        verify(history).trimTo(5L, 20);
    }

    @Test
    void uselessKeywordsAreIgnored() {
        service.record(5L, "a");
        service.record(5L, "  ");
        service.record(5L, "!!");
        service.record(5L, null);
        verifyNoInteractions(keywords, history);
    }

    @Test
    void trendingClampsLimitAndReturnsKeywords() {
        when(keywords.trending(any(), eq(10))).thenReturn(List.of(kw("charm", "charm"), kw("dây chuyền", "day chuyen")));
        assertEquals(List.of("charm", "dây chuyền"), service.trending(null));
        service.trending(500);
        verify(keywords).trending(any(), eq(20));
        service.trending(-3);
        verify(keywords, org.mockito.Mockito.times(2)).trending(any(), eq(10));
    }

    @Test
    void suggestionsMatchCategoriesAndKeywordsWithoutDiacritics() {
        when(categories.findAllActive()).thenReturn(List.of(cat("Nhẫn", "nhan"), cat("Dây chuyền", "day-chuyen")));
        when(keywords.suggest("%nhan%", 8)).thenReturn(List.of(kw("nhẫn", "nhan"), kw("nhẫn kim cương", "nhan kim cuong")));

        List<SearchSuggestionResponseDto> out = service.suggestions("Nhan");

        assertEquals(2, out.size());
        assertEquals(new SearchSuggestionResponseDto("Nhẫn", "category", "nhan"), out.get(0));
        // "nhẫn" trùng tên danh mục nên không lặp lại thành từ khoá.
        assertEquals(new SearchSuggestionResponseDto("nhẫn kim cương", "keyword", null), out.get(1));
    }

    @Test
    void suggestionsForShortOrBlankQueryAreEmptyWithoutQuerying() {
        assertTrue(service.suggestions("n").isEmpty());
        assertTrue(service.suggestions(null).isEmpty());
        verifyNoInteractions(keywords, categories);
    }

    @Test
    void suggestionsEscapeLikeWildcards() {
        when(categories.findAllActive()).thenReturn(List.of());
        when(keywords.suggest(anyString(), anyInt())).thenReturn(List.of());
        service.suggestions("100%_");
        verify(keywords).suggest("%100!%!_%", 8);
    }

    @Test
    void historyOperationsAreScopedToTheAccountAndAccentInsensitive() {
        service.removeHistoryKeyword(5L, "Nhẫn");
        verify(history).removeOne(5L, "nhan");
        service.removeHistoryKeyword(5L, "x");
        verify(history, never()).removeOne(eq(5L), eq("x"));
        service.clearHistory(5L);
        verify(history).clearAll(5L);
        when(history.recent(anyLong(), anyInt())).thenReturn(List.of());
        service.history(5L, 100);
        verify(history).recent(5L, 20);
    }
}
