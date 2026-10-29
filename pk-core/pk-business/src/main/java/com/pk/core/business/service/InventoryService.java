package com.pk.core.business.service;

import com.pk.core.common.web.PageResponse;
import com.pk.core.model.dto.request.AdminStockAdjustmentRequestDto;
import com.pk.core.model.dto.response.InventoryMovementResponseDto;
import com.pk.core.model.dto.response.StockLevelResponseDto;

import java.util.List;

/** Admin mục M spec (document/09-tong-hop-api-fe.md) - "Tồn kho". Quyết định thiết kế (chốt trong
 * quá trình làm, xem RULE-CODE.md mục Inventory admin): KHÔNG tạo bảng `warehouses` mới - tái dùng
 * thẳng `product_skus.on_hand`/`reserved` có sẵn (V1__init.sql), coi hệ thống có ĐÚNG 1 kho ngầm định
 * duy nhất (`warehouseId` = hằng "MAIN", chỉ mang tính trang trí/tương thích FE, KHÔNG lọc/phân biệt
 * kho thật). Giải quyết gọn mâu thuẫn "spec có warehouseId nhưng DB không multi-warehouse" đã ghi
 * nhận trước đó mà không cần đổi schema. */
public interface InventoryService {

    /** GET /admin/inventory/stock-levels. `keyword` lọc theo skuCode/tên (tương tự ProductSkuRepo.
     * searchByKeyword) - spec chỉ nêu filter `warehouseId` nhưng filter đó decorative (xem trên) nên
     * KHÔNG cần tham số warehouseId ở đây; Rest vẫn nhận query param warehouseId cho khớp spec nhưng
     * bỏ qua giá trị, không truyền xuống tầng service. */
    List<StockLevelResponseDto> stockLevels(String keyword);

    /**
     * POST /admin/inventory/adjustments - 400 STOCK_ADJUSTMENT_INVALID (delta 0 / sai dấu theo reason), 422 NEGATIVE_STOCK |
     * STOCK_OVERFLOW | STOCK_BELOW_RESERVED, 409 STOCK_CONFLICT khi thua race. Mỗi lần thành công ghi 1 dòng sổ kho cùng
     * transaction. idempotencyKey (tuỳ chọn, 8-100 ký tự): cùng người làm + cùng khoá + cùng nội dung thì KHÔNG cộng lần nữa
     * (trả tồn hiện tại của SKU); cùng khoá khác nội dung -> 409 IDEMPOTENCY_KEY_REUSED.
     */
    StockLevelResponseDto adjust(AdminStockAdjustmentRequestDto req, Long actorId, String idempotencyKey);

    /**
     * GET /admin/inventory/movements - sổ kho, mới nhất trước. Lọc tuỳ chọn theo skuId, reason, khoảng thời gian
     * [from, to) (ISO-8601: ngày `2026-10-28`, hoặc ngày giờ có/không offset; `to` dạng ngày là ngày đó trọn ngày). page 1-based.
     */
    PageResponse<InventoryMovementResponseDto> movements(Long skuId, String reason, String from, String to, int page, int take);

    /** GET /admin/inventory/movements/{id} - 404 nếu không có. */
    InventoryMovementResponseDto movement(Long id);
}
