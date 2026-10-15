package com.pk.core.model.dto.response;

import java.util.Map;

/** Số đơn theo từng trạng thái (GET /admin/orders/counts) - cùng bộ lọc với danh sách, trừ status/phân trang. */
public record OrderCountsDto(long total, Map<String, Long> byStatus) {
}
