package com.pk.core.business.service;

import com.pk.core.common.web.PageResponse;
import com.pk.core.model.dto.request.AdminOrderStatusRequestDto;
import com.pk.core.model.dto.request.CreateOrderRequestDto;
import com.pk.core.model.dto.response.OrderResponseDto;

import java.math.BigDecimal;
import java.util.List;

public interface OrderService {

    /** Tạo đơn từ danh sách dòng hàng client gửi kèm (không có cart phía server - xem
     * document/09-tong-hop-api-fe.md mục D). Giữ chỗ tồn kho (reserved) cho từng SKU, áp voucher nếu
     * có, snapshot địa chỉ giao hàng, ghi timeline PENDING đầu tiên. CHƯA tính phí ship theo phương
     * thức/khu vực (để 0) và CHƯA áp khuyến mãi tự động theo sản phẩm (productDiscount để 0, domain
     * Promotion chưa làm) - cố ý để dành đợt sau. */
    OrderResponseDto create(Long userId, CreateOrderRequestDto req);

    /** Như {@link #create(Long, CreateOrderRequestDto)} nhưng có `Idempotency-Key` (null/rỗng = không chống trùng).
     * Cùng khoá + cùng nội dung -> trả lại đúng đơn đã tạo (không giữ chỗ tồn/dùng voucher thêm); cùng khoá + nội dung khác -> 409
     * IDEMPOTENCY_KEY_REUSED; khoá sai định dạng -> 400 IDEMPOTENCY_KEY_INVALID. Khoá hiệu lực 24 giờ, tính theo từng người đặt. */
    OrderResponseDto create(Long userId, CreateOrderRequestDto req, String idempotencyKey);

    OrderResponseDto createGuest(com.pk.core.model.dto.request.GuestOrderRequestDto req, String guestCartId, String idempotencyKey);

    /** Đặt hàng không cần đăng nhập (địa chỉ gửi kèm). `guestCartId` (nếu có) để dọn các dòng đã mua khỏi giỏ khách. */
    OrderResponseDto createGuest(com.pk.core.model.dto.request.GuestOrderRequestDto req, String guestCartId);

    /** Tra cứu đơn của khách vãng lai bằng mã đơn + SĐT giao hàng; sai SĐT trả 404 như không có đơn. */
    OrderResponseDto findGuestOrder(String code, String phone);

    PageResponse<OrderResponseDto> listMine(Long userId, int page, int take);

    OrderResponseDto findByCodeForUser(Long userId, String code);

    /** Khách tự huỷ đơn (chỉ khi còn PENDING/CONFIRMED) - nhả tồn kho đã giữ chỗ, ghi timeline CANCELLED. */
    OrderResponseDto cancel(Long userId, String code, String reason);

    // ---------- Admin (mục L spec, KHÔNG lọc theo userId - IDOR không áp dụng cho admin) ----------

    /** GET /admin/orders - search/status/paymentStatus lọc ở SQL (OrderRepo.searchAdmin), fromDate/
     * toDate (định dạng yyyy-MM-dd, có thể null/rỗng) lọc bằng Java trên placedAt sau khi query. */
    PageResponse<OrderResponseDto> searchForAdmin(String search, String status, String paymentStatus,
                                                   String fromDate, String toDate, int page, int take);

    /** Danh sách đơn admin với đầy đủ bộ lọc/sắp xếp (xem {@link AdminOrderQuery}). */
    PageResponse<OrderResponseDto> searchForAdmin(AdminOrderQuery query);

    /** GET /admin/orders/counts - số đơn theo trạng thái, cùng bộ lọc với danh sách (trừ status/page/take). */
    com.pk.core.model.dto.response.OrderCountsDto countsForAdmin(AdminOrderQuery query);

    /** GET /admin/orders/{orderCode} - không lọc theo userId (khác findByCodeForUser). */
    OrderResponseDto findByCodeForAdmin(String code);

    /** PATCH /admin/orders/{orderCode}/status - validate qua OrderEntity.canTransitionTo(), sai luồng
     * ném BusinessException.conflict(ErrorCode.INVALID_STATUS_TRANSITION) (409). */
    OrderResponseDto updateStatus(String code, AdminOrderStatusRequestDto request);

    /** POST /admin/orders/{orderCode}/cancel - rộng hơn cancel() của khách (xem
     * OrderEntity.isCancellableByAdmin()), nhả tồn kho toàn bộ dòng hàng, ghi timeline CANCELLED. */
    OrderResponseDto cancelByAdmin(String code, String reason);

    /** POST /admin/orders/{orderCode}/return - chỉ khi đơn DELIVERED (OrderEntity.isReturnable()),
     * nhả tồn kho CÁC DÒNG trong lineIds (không phải toàn đơn), chuyển status RETURNED. */
    OrderResponseDto returnOrder(String code, com.pk.core.model.dto.request.AdminOrderReturnRequestDto request);

    /** POST /admin/orders/{orderCode}/refund - chỉ khi đơn paymentStatus=PAID, chuyển paymentStatus
     * sang REFUNDED, ghi amount/reason vào timeline (xem AdminOrderRefundRequestDto javadoc). */
    OrderResponseDto refund(String code, BigDecimal amount, String reason);
}
