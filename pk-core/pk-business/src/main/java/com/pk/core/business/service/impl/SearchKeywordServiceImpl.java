package com.pk.core.business.service.impl;

import com.pk.core.business.repository.SearchHistoryRepo;
import com.pk.core.business.repository.SearchKeywordRepo;
import com.pk.core.business.service.CategoryService;
import com.pk.core.business.service.SearchKeywordService;
import com.pk.core.model.dto.response.CategoryResponseDto;
import com.pk.core.model.dto.response.SearchHistoryItemResponseDto;
import com.pk.core.model.dto.response.SearchSuggestionResponseDto;
import com.pk.core.model.entity.SearchHistoryEntity;
import com.pk.core.model.entity.SearchKeywordEntity;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

@Service
@RequiredArgsConstructor
public class SearchKeywordServiceImpl implements SearchKeywordService {

    static final int DEFAULT_LIMIT = 10;
    static final int MAX_LIMIT = 20;
    static final int HISTORY_KEEP = 20;
    static final int TRENDING_DAYS = 30;
    static final int MAX_CATEGORY_SUGGESTIONS = 3;
    static final int MAX_SUGGESTIONS = 8;

    private final SearchKeywordRepo keywords;
    private final SearchHistoryRepo history;
    private final CategoryService categories;

    @Transactional
    @Override
    public void record(Long userId, String rawKeyword) {
        String kw = SearchText.clean(rawKeyword);
        if (kw == null) {
            return;
        }
        String key = SearchText.key(kw);
        if (keywords.bump(key) == 0) {
            SearchKeywordEntity e = new SearchKeywordEntity();
            e.setKeyword(kw);
            e.setKeywordKey(key);
            keywords.create(e);
        } else if (SearchText.hasDiacritics(kw)) {
            keywords.rename(key, kw);
        }
        if (userId != null) {
            if (history.touch(userId, key, kw) == 0) {
                SearchHistoryEntity h = new SearchHistoryEntity();
                h.setUserId(userId);
                h.setKeyword(kw);
                h.setKeywordKey(key);
                history.create(h);
            }
            history.trimTo(userId, HISTORY_KEEP);
        }
    }

    @Transactional(readOnly = true)
    @Override
    public List<String> trending(Integer limit) {
        Date since = new Date(System.currentTimeMillis() - TRENDING_DAYS * 24L * 3600_000L);
        return keywords.trending(since, clampLimit(limit)).stream().map(SearchKeywordEntity::getKeyword).toList();
    }

    @Transactional(readOnly = true)
    @Override
    public List<SearchSuggestionResponseDto> suggestions(String q) {
        String cleaned = SearchText.clean(q);
        if (cleaned == null) {
            return List.of();
        }
        String key = SearchText.key(cleaned);
        List<SearchSuggestionResponseDto> out = new ArrayList<>();
        for (CategoryResponseDto c : categories.findAllActive()) {
            if (out.size() >= MAX_CATEGORY_SUGGESTIONS) {
                break;
            }
            if (c.getName() != null && c.getSlug() != null && SearchText.key(c.getName().toLowerCase()).contains(key)) {
                out.add(new SearchSuggestionResponseDto(c.getName(), "category", c.getSlug()));
            }
        }
        List<SearchKeywordEntity> found = keywords.suggest("%" + SearchText.likeEscape(key) + "%", MAX_SUGGESTIONS);
        for (SearchKeywordEntity k : found) {
            if (out.size() >= MAX_SUGGESTIONS) {
                break;
            }
            boolean sameAsCategory = out.stream().anyMatch(s -> "category".equals(s.type())
                    && SearchText.key(s.text().toLowerCase()).equals(k.getKeywordKey()));
            if (!sameAsCategory) {
                out.add(new SearchSuggestionResponseDto(k.getKeyword(), "keyword", null));
            }
        }
        return out;
    }

    @Transactional(readOnly = true)
    @Override
    public List<SearchHistoryItemResponseDto> history(Long userId, Integer limit) {
        return history.recent(userId, clampLimit(limit)).stream().map(SearchHistoryItemResponseDto::from).toList();
    }

    @Transactional
    @Override
    public void clearHistory(Long userId) {
        history.clearAll(userId);
    }

    @Transactional
    @Override
    public void removeHistoryKeyword(Long userId, String keyword) {
        String cleaned = SearchText.clean(keyword);
        if (cleaned != null) {
            history.removeOne(userId, SearchText.key(cleaned));
        }
    }

    private static int clampLimit(Integer limit) {
        return limit == null || limit <= 0 ? DEFAULT_LIMIT : Math.min(limit, MAX_LIMIT);
    }
}
