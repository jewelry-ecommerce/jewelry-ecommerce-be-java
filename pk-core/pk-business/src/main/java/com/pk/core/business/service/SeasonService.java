package com.pk.core.business.service;

import com.pk.core.common.web.PageResponse;
import com.pk.core.model.dto.request.AdminSeasonRequestDto;
import com.pk.core.model.dto.response.SeasonResponseDto;

/** Mùa (admin catalog). */
public interface SeasonService {
    PageResponse<SeasonResponseDto> list(String search, Boolean isActive, int page, int take);

    SeasonResponseDto detail(Long id);

    SeasonResponseDto create(AdminSeasonRequestDto req);

    SeasonResponseDto update(Long id, AdminSeasonRequestDto req);

    /** Xoá mềm; 409 SEASON_IN_USE nếu còn sản phẩm đang gắn. */
    void delete(Long id);
}
