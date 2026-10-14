package com.pk.core.business.service;

import com.pk.core.model.dto.request.AdminAttributeRequestDto;
import com.pk.core.model.dto.response.ProductAttributeResponseDto;

import java.util.List;

/** Thuộc tính sản phẩm cấu hình được (Admin Catalog mục J: GET/POST/PUT /admin/catalog/attributes). */
public interface ProductAttributeService {

    List<ProductAttributeResponseDto> findAll();

    ProductAttributeResponseDto create(AdminAttributeRequestDto req);

    ProductAttributeResponseDto update(Long attributeId, AdminAttributeRequestDto req);

    /** Xoá thuộc tính - DELETE /admin/catalog/attributes/{attributeId}. 409 ATTRIBUTE_IN_USE nếu còn SKU gán. */
    void delete(Long attributeId);
}
