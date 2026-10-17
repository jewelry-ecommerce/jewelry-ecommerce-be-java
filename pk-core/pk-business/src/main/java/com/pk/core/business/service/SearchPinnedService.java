package com.pk.core.business.service;

import com.pk.core.model.dto.request.AdminSearchPinnedRequestDto;
import com.pk.core.model.dto.response.ProductResponseDto;
import com.pk.core.model.dto.response.SearchPinnedResponseDto;

import java.util.List;

/**
 * Ghim sản phẩm ở ô tìm kiếm: 4 ô (position 0..3) cho mỗi phạm vi (landing = keywordPattern null, hoặc một từ khoá).
 * Tạo vào ô đã có người: 409. Sửa sang ô đã có người: hai ô đổi chỗ cho nhau (FE lưu tuần tự nên đổi chỗ không bị trùng tạm thời).
 */
public interface SearchPinnedService {

    List<SearchPinnedResponseDto> list();

    SearchPinnedResponseDto create(AdminSearchPinnedRequestDto req, Long actorId);

    SearchPinnedResponseDto update(Long id, AdminSearchPinnedRequestDto req, Long actorId);

    void delete(Long id);

    /** Công khai: sản phẩm PUBLISHED đang được ghim ở landing (hiệu lực theo giờ hiện tại), theo position. */
    List<ProductResponseDto> activeLandingProducts();
}
