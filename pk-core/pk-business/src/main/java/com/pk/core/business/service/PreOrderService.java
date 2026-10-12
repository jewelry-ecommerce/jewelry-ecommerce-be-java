package com.pk.core.business.service;

import com.pk.core.model.dto.request.PreOrderConfigRequestDto;
import com.pk.core.model.dto.response.PreOrderConfigResponseDto;

import java.util.List;

/** Cấu hình đặt trước theo spec FE (GET/POST /admin/pre-orders). Thêm 2026-10-06. */
public interface PreOrderService {

    /** Lịch sử cấu hình, mới nhất trước (phần tử đầu = cấu hình đang hiệu lực). */
    List<PreOrderConfigResponseDto> list();

    /** Cấu hình đang hiệu lực (bản ghi mới nhất); chưa có cấu hình nào = tắt, message null. */
    PreOrderConfigResponseDto current();

    /** Đặt trước đang bật hay không (theo cấu hình mới nhất). */
    boolean isEnabled();

    /** Phạm vi của cấu hình đang hiệu lực (tắt nếu chưa có cấu hình). */
    PreOrderScope scope();

    /** Đặt trước đang bật VÀ SKU thuộc phạm vi (ALL = mọi SKU). Caller tự xét thêm "hết hàng" khi quyết định nhận đơn đặt trước. */
    boolean appliesTo(com.pk.core.model.entity.ProductSkuEntity sku);

    /** Thêm một cấu hình mới (không sửa/xoá bản cũ); adminId ghi vào created_id. */
    PreOrderConfigResponseDto create(Long adminId, PreOrderConfigRequestDto req);
}
