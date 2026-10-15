package com.pk.core.business.service;

import com.pk.core.common.web.PageResponse;
import com.pk.core.model.dto.request.AdminBrandRequestDto;
import com.pk.core.model.dto.response.BrandResponseDto;

/** Thương hiệu (admin catalog). */
public interface BrandService {
    PageResponse<BrandResponseDto> list(String search, String status, int page, int take);

    BrandResponseDto detail(Long id);

    BrandResponseDto create(AdminBrandRequestDto req);

    BrandResponseDto update(Long id, AdminBrandRequestDto req);

    /** Xoá mềm; 409 BRAND_IN_USE nếu còn sản phẩm đang gắn. */
    void delete(Long id);
}
