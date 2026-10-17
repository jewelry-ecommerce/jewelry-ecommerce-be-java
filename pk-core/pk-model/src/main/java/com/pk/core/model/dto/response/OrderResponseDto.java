package com.pk.core.model.dto.response;

import com.pk.core.common.dto.UpdateDto;
import com.pk.core.model.entity.OrderEntity;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.Date;
import java.util.List;

/** extends UpdateDto (không phải BaseDto): OrderEntity chỉ ở mức UpdateEntity (bảng `orders` không
 * có deleted_id/deleted_date - không xoá mềm đơn hàng), nên gọi thẳng copyAudit(OrderEntity) kế thừa
 * từ UpdateDto thay vì BaseDto.of(...) (BaseDto.of yêu cầu tham số kiểu BaseEntity, OrderEntity không
 * phải BaseEntity). */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class OrderResponseDto extends UpdateDto {

    private Long id;
    private String code;
    private String status;
    private String paymentStatus;
    private String paymentMethod;
    private String shippingMethod;
    private String trackingCode;
    private String shipRecipientName;
    private String shipPhone;
    private String shipProvince;
    private String shipDistrict;
    private String shipWard;
    private String shipAddressLine;
    private BigDecimal subtotal;
    private BigDecimal productDiscount;
    private BigDecimal voucherDiscount;
    private BigDecimal shippingFee;
    private BigDecimal grandTotal;
    private String appliedVoucherCode;
    private String note;
    private Date placedAt;
    /** null nếu thanh toán COD (không tạo PaymentEntity) - xem OrderServiceImpl.create(). Dùng
     * paymentId này để gọi POST /storefront/payment/{paymentId}/callback. */
    private Long paymentId;
    /** Tổng tiền đã hoàn (hoàn từng phần cộng dồn). */
    private BigDecimal refundedAmount = BigDecimal.ZERO;
    private List<OrderItemResponseDto> items;
    private List<OrderTimelineResponseDto> timeline;
    /** Id khách hàng (null = đơn khách vãng lai). */
    private Long customerId;
    /** Email liên hệ: email tài khoản, hoặc email khách vãng lai. */
    private String contactEmail;
    /** Thời điểm giao thành công (mốc DELIVERED gần nhất trong timeline), null nếu chưa giao. */
    private Date deliveredAt;
    /** Giá trị thu hộ COD: bằng tổng đơn nếu thanh toán COD, ngược lại 0. */
    private BigDecimal codAmount = BigDecimal.ZERO;
    /** Id tài khoản tạo đơn (khách đặt online, hoặc nhân viên tạo hộ); null = khách vãng lai / đơn cũ. */
    private Long createdById;
    /** WEBSITE | ADMIN. */
    private String source;

    public static OrderResponseDto from(OrderEntity o, Long paymentId, List<OrderItemResponseDto> items,
                                         List<OrderTimelineResponseDto> timeline) {
        OrderResponseDto dto = new OrderResponseDto(o.getId(), o.getCode(), o.getStatus(), o.getPaymentStatus(),
                o.getPaymentMethod(), o.getShippingMethod(), o.getTrackingCode(), o.getShipRecipientName(),
                o.getShipPhone(), o.getShipProvince(), o.getShipDistrict(), o.getShipWard(),
                o.getShipAddressLine(), o.getSubtotal(), o.getProductDiscount(), o.getVoucherDiscount(),
                o.getShippingFee(), o.getGrandTotal(), o.getAppliedVoucherCode(), o.getNote(), o.getPlacedAt(),
                paymentId, o.getRefundedAmount() == null ? BigDecimal.ZERO : o.getRefundedAmount(), items, timeline,
                o.getUserId(), o.getGuestEmail(), null, BigDecimal.ZERO, o.getCreatedId(), o.getSource());
        dto.copyAudit(o);
        return dto;
    }

    // ---- FE OrderDetailResponse: field tính toán, giữ nguyên field cũ (status/tiền giữ kiểu hiện tại) ----

    /** Số tiền còn hoàn được = tổng đơn - đã hoàn. */
    public BigDecimal getRefundableAmount() {
        BigDecimal total = grandTotal == null ? BigDecimal.ZERO : grandTotal;
        return total.subtract(refundedAmount == null ? BigDecimal.ZERO : refundedAmount).max(BigDecimal.ZERO);
    }

    public String getOrderCode() {
        return code;
    }

    public String getCreatedAt() {
        Date d = placedAt != null ? placedAt : getCreatedDate();
        return d == null ? null : d.toInstant().toString();
    }

    public String getUpdatedAt() {
        return getUpdatedDate() == null ? null : getUpdatedDate().toInstant().toString();
    }

    public String getDiscountTotal() {
        BigDecimal total = (productDiscount == null ? BigDecimal.ZERO : productDiscount)
                .add(voucherDiscount == null ? BigDecimal.ZERO : voucherDiscount);
        return String.valueOf(total.longValue());
    }

    public String getTaxTotal() {
        return "0";
    }

    public Map<String, Object> getShippingAddressSnapshot() {
        // FE chỉ hiển thị firstName -> đặt HỌ TÊN ĐẦY ĐỦ vào firstName, lastName để trống.
        String full = shipRecipientName == null ? "" : shipRecipientName.trim();
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("lastName", "");
        m.put("firstName", full);
        m.put("receiverPhone", shipPhone);
        m.put("addressLine", shipAddressLine);
        m.put("wardName", shipWard);
        m.put("provinceName", shipProvince);
        return m;
    }

    public List<Map<String, Object>> getStatusHistory() {
        List<Map<String, Object>> list = new ArrayList<>();
        if (timeline != null) {
            for (OrderTimelineResponseDto t : timeline) {
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("toStatus", t.getStatus());
                m.put("title", t.getStatus());
                m.put("note", t.getNote());
                m.put("createdAt", t.getOccurredAt() == null ? null : t.getOccurredAt().toInstant().toString());
                list.add(m);
            }
        }
        return list;
    }
}
