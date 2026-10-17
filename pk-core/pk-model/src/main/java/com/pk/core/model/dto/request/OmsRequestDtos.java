package com.pk.core.model.dto.request;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;
import java.util.Map;

/**
 * Body các API OMS nâng cao của admin (tạo/sửa đơn, ghi chú, nhân bản, xác nhận thanh toán, đổi/trả). Mọi trường đều
 * tuỳ chọn ở mức DTO - service tự kiểm tra theo từng thao tác (tạo yêu cầu đủ trường, sửa chỉ cần trường muốn đổi).
 * Id gửi dạng số hoặc chuỗi số đều nhận được (FE dùng chuỗi). Trường lạ bị bỏ qua.
 */
public final class OmsRequestDtos {

    private OmsRequestDtos() {
    }

    /** Tạo / báo giá / sửa đơn thủ công. `items[].variationId` chính là id SKU (`skuId` cũng được nhận). */
    @Getter
    @Setter
    @NoArgsConstructor
    public static class OrderUpsert {
        private Integer contextVersion;
        private String receiverPhone;
        private String firstName;
        private String lastName;
        private Long provinceCode;
        private String provinceName;
        private Long wardCode;
        private String wardName;
        private String districtName;
        private String addressLine;
        private List<Item> items;
        private List<String> couponCodes;
        private String shippingMethod;
        private String paymentMethod;
        /** Mã báo giá nhận từ /pricing-context/quote; nếu gửi và giá đã đổi -> 409 ORDER_PRICING_CHANGED. */
        private String pricingEvaluationId;
        /** Tổng tiền (VND) FE đã hiển thị cho khách; lệch với giá hiện tại -> 409 ORDER_PRICING_CHANGED. */
        private Long expectedGrandTotalMinor;
        private String idempotencyKey;
        private String note;
        private String notePayment;
        private String contactEmail;
        private String source;
        private String purpose;
        private String orderKind;

        @Getter
        @Setter
        @NoArgsConstructor
        public static class Item {
            private Long variationId;
            private Long skuId;
            private Integer quantity;

            public Long sku() {
                return variationId != null ? variationId : skuId;
            }
        }
    }

    @Getter
    @Setter
    @NoArgsConstructor
    public static class Note {
        private String content;
        /** order (hiện cho khách/đối soát) | internal (nội bộ). Mặc định order. */
        private String type;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    public static class Duplicate {
        /** Id hoặc mã đơn nguồn. */
        private String sourceOrderId;
        private Boolean replaceSourceAfterDuplicate;
        private String cancellationReason;
        private String cancellationNote;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    public static class ConfirmPayment {
        private String note;
    }

    /** Tạo / báo giá / commit / sửa yêu cầu đổi trả. */
    @Getter
    @Setter
    @NoArgsConstructor
    public static class OrderReturn {
        /** Id hoặc mã đơn gốc. */
        private String orderId;
        /** Refund | Exchange (FE enum). */
        private String type;
        private String reason;
        private String note;
        private String status;
        private String paymentMethod;
        private String trackingCode;
        private String returnFaultParty;
        private List<ReturnItem> returnItems;
        private List<ExchangeItem> exchangeItems;
        private Map<String, Object> shippingAddress;
        private List<String> evidenceImageUrls;
        private String refundBankAccountHolder;
        private String refundBankAccountNumber;
        private String refundBankName;
        private String refundBankBranch;
        /** Tên cũ của FE cũ - ưu tiên refundBank*. */
        private String bankAccountHolder;
        private String bankAccountNumber;
        private String bankName;
        private String bankBranch;
        /** Tham chiếu báo giá khi commit (fingerprint phải còn khớp). */
        private Map<String, Object> pricingQuote;

        @Getter
        @Setter
        @NoArgsConstructor
        public static class ReturnItem {
            private Long orderItemId;
            private Integer quantity;
        }

        @Getter
        @Setter
        @NoArgsConstructor
        public static class ExchangeItem {
            private Long variationId;
            private Integer quantity;
        }
    }

    @Getter
    @Setter
    @NoArgsConstructor
    public static class Resolve {
        private String note;
    }
}
