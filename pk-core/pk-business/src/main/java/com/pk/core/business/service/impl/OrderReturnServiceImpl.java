package com.pk.core.business.service.impl;

import com.pk.core.business.repository.OrderItemRepo;
import com.pk.core.business.repository.OrderRepo;
import com.pk.core.business.repository.OrderReturnHistoryRepo;
import com.pk.core.business.repository.OrderReturnItemRepo;
import com.pk.core.business.repository.OrderReturnRepo;
import com.pk.core.business.repository.ProductRepo;
import com.pk.core.business.repository.ProductSkuRepo;
import com.pk.core.business.repository.UserRepo;
import com.pk.core.business.service.OrderOmsService;
import com.pk.core.business.service.OrderReturnService;
import com.pk.core.business.service.OrderService;
import com.pk.core.business.service.PromotionPricingService;
import com.pk.core.common.exception.BusinessException;
import com.pk.core.common.exception.ErrorCode;
import com.pk.core.common.exception.ResourceNotFoundException;
import com.pk.core.model.dto.request.AdminOrderReturnRequestDto;
import com.pk.core.model.dto.request.OmsRequestDtos;
import com.pk.core.model.dto.response.OrderResponseDto;
import com.pk.core.model.entity.OrderEntity;
import com.pk.core.model.entity.OrderItemEntity;
import com.pk.core.model.entity.OrderReturnEntity;
import com.pk.core.model.entity.OrderReturnHistoryEntity;
import com.pk.core.model.entity.OrderReturnItemEntity;
import com.pk.core.model.entity.ProductEntity;
import com.pk.core.model.entity.ProductSkuEntity;
import com.pk.core.model.entity.UserEntity;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;

@Service
@RequiredArgsConstructor
public class OrderReturnServiceImpl implements OrderReturnService {

    static final String PENDING = "Pending";
    static final String APPROVED = "Approved";
    static final String COMPLETED = "Completed";
    static final String REJECTED = "Rejected";
    static final String CANCELLED = "Cancelled";
    private static final String REFUND = "Refund";
    private static final String EXCHANGE = "Exchange";
    private static final String KIND_RETURN = "RETURN";
    private static final String KIND_EXCHANGE = "EXCHANGE";
    private static final List<String> STATUSES = List.of(PENDING, APPROVED, COMPLETED, REJECTED, CANCELLED);
    private static final Set<String> TYPES = Set.of(REFUND, EXCHANGE);
    private static final Set<String> OBJECTIVE_REASONS = Set.of("WrongOrderDelivered", "EmptyBoxDelivered",
            "MissingItemsDelivered", "WrongProductDelivered", "DefectiveProduct", "ProductNotAsDescribed");
    private static final String SUBJECTIVE_REASON = "ChangedMind";

    private final OrderReturnRepo returns;
    private final OrderReturnItemRepo returnItems;
    private final OrderReturnHistoryRepo history;
    private final OrderRepo orders;
    private final OrderItemRepo orderItems;
    private final ProductSkuRepo skus;
    private final ProductRepo products;
    private final UserRepo users;
    private final PromotionPricingService promotionPricing;
    private final OrderService orderService;
    private final OrderOmsService oms;

    // ============================== kế hoạch / tính tiền ==============================

    private record Line(Long orderItemId, Long skuId, Long productId, String productName, String variationName,
                        String skuCode, String image, BigDecimal unitPrice, int quantity, BigDecimal lineTotal) {
    }

    private record Plan(OrderEntity order, String type, String reason, List<Line> returnLines,
                        List<Line> exchangeLines, BigDecimal returnTotal, BigDecimal exchangeTotal) {
        BigDecimal difference() {
            return exchangeTotal.subtract(returnTotal);
        }
    }

    private static BusinessException bad(String message) {
        return BusinessException.badRequest(ErrorCode.VALIDATION_FAILED, message);
    }

    private static BusinessException conflict(String message, String from, String to) {
        return BusinessException.conflict(ErrorCode.INVALID_STATUS_TRANSITION, message, from, to);
    }

    private OrderEntity findOrder(String key) {
        String k = key == null ? "" : key.trim();
        OrderEntity o = k.isEmpty() ? null : orders.findByCode(k);
        if (o == null && k.matches("\\d{1,18}")) {
            o = orders.findOne(Long.parseLong(k));
        }
        if (o == null) {
            throw new ResourceNotFoundException("Đơn hàng", "Order", key);
        }
        return o;
    }

    private OrderReturnEntity find(String key) {
        String k = key == null ? "" : key.trim();
        OrderReturnEntity r = k.isEmpty() ? null : returns.findByCode(k);
        if (r == null && k.matches("\\d{1,18}")) {
            r = returns.findOne(Long.parseLong(k));
        }
        if (r == null) {
            throw new ResourceNotFoundException("Yêu cầu đổi trả", "OrderReturn", key);
        }
        return r;
    }

    private static String normType(String type) {
        for (String t : TYPES) {
            if (t.equalsIgnoreCase(type == null ? "" : type.trim())) {
                return t;
            }
        }
        throw bad("type chỉ nhận Refund | Exchange");
    }

    private static String normReason(String reason) {
        String r = reason == null ? "" : reason.trim();
        for (String x : OBJECTIVE_REASONS) {
            if (x.equalsIgnoreCase(r)) {
                return x;
            }
        }
        if (SUBJECTIVE_REASON.equalsIgnoreCase(r)) {
            return SUBJECTIVE_REASON;
        }
        throw bad("reason không hợp lệ: " + reason);
    }

    private static BigDecimal round(BigDecimal v) {
        return v.setScale(0, RoundingMode.HALF_UP);
    }

    private boolean sellable(ProductSkuEntity sku) {
        if (sku == null || !ProductSkuEntity.PUBLISHED.equals(sku.getStatus())) {
            return false;
        }
        ProductEntity product = products.findOne(sku.getProductId());
        return product != null && product.isSellable();
    }

    /** Số lượng đã nằm trong các yêu cầu Pending khác của cùng đơn (theo orderItemId). */
    private Map<Long, Integer> pendingQty(Long orderId, Long excludeReturnId) {
        Map<Long, Integer> m = new LinkedHashMap<>();
        for (OrderReturnEntity r : returns.findByOrderId(orderId)) {
            if (!PENDING.equals(r.getStatus()) || r.getId().equals(excludeReturnId)) {
                continue;
            }
            for (OrderReturnItemEntity it : returnItems.findByReturnId(r.getId())) {
                if (KIND_RETURN.equals(it.getKind()) && it.getOrderItemId() != null) {
                    m.merge(it.getOrderItemId(), it.getQuantity(), Integer::sum);
                }
            }
        }
        return m;
    }

    private Plan plan(OmsRequestDtos.OrderReturn req, Long excludeReturnId) {
        if (req == null) {
            throw bad("Thiếu nội dung yêu cầu");
        }
        OrderEntity order = findOrder(req.getOrderId());
        if (!OrderEntity.DELIVERED.equals(order.getStatus())) {
            throw conflict("Chỉ đổi/trả được đơn đã giao (hiện tại: " + order.getStatus() + ")", order.getStatus(),
                    OrderEntity.RETURNED);
        }
        String type = normType(req.getType());
        String reason = normReason(req.getReason());
        if (req.getReturnItems() == null || req.getReturnItems().isEmpty()) {
            throw bad("Cần ít nhất một sản phẩm trả");
        }
        Map<Long, OrderItemEntity> byId = new LinkedHashMap<>();
        for (OrderItemEntity it : orderItems.findByOrderId(order.getId())) {
            byId.put(it.getId(), it);
        }
        BigDecimal ratio = BigDecimal.ONE;
        if (order.getSubtotal() != null && order.getSubtotal().signum() > 0) {
            BigDecimal paid = order.getSubtotal().subtract(order.getProductDiscount()).subtract(order.getVoucherDiscount());
            ratio = paid.divide(order.getSubtotal(), 10, RoundingMode.HALF_UP);
        }
        Map<Long, Integer> wanted = new LinkedHashMap<>();
        for (OmsRequestDtos.OrderReturn.ReturnItem ri : req.getReturnItems()) {
            if (ri.getOrderItemId() == null || ri.getQuantity() == null || ri.getQuantity() < 1) {
                throw bad("Mỗi dòng trả cần orderItemId và quantity >= 1");
            }
            wanted.merge(ri.getOrderItemId(), ri.getQuantity(), Integer::sum);
        }
        Map<Long, Integer> pending = pendingQty(order.getId(), excludeReturnId);
        List<Line> returnLines = new ArrayList<>();
        BigDecimal returnTotal = BigDecimal.ZERO;
        for (Map.Entry<Long, Integer> e : wanted.entrySet()) {
            OrderItemEntity it = byId.get(e.getKey());
            if (it == null) {
                throw bad("Dòng hàng " + e.getKey() + " không thuộc đơn " + order.getCode());
            }
            int remaining = it.getQuantity() - it.getReturnedQuantity() - pending.getOrDefault(it.getId(), 0);
            if (e.getValue() > remaining) {
                throw bad("Dòng " + it.getId() + " chỉ còn đổi/trả được " + Math.max(remaining, 0) + " sản phẩm");
            }
            BigDecimal lineTotal = round(it.getUnitPrice().multiply(BigDecimal.valueOf(e.getValue())).multiply(ratio));
            BigDecimal netUnit = round(lineTotal.divide(BigDecimal.valueOf(e.getValue()), 2, RoundingMode.HALF_UP));
            returnLines.add(new Line(it.getId(), it.getSkuId(), it.getProductId(), it.getName(),
                    it.getName(), it.getSkuCode(), it.getImageUrl(), netUnit, e.getValue(), lineTotal));
            returnTotal = returnTotal.add(lineTotal);
        }
        List<Line> exchangeLines = new ArrayList<>();
        BigDecimal exchangeTotal = BigDecimal.ZERO;
        boolean hasExchange = req.getExchangeItems() != null && !req.getExchangeItems().isEmpty();
        if (REFUND.equals(type) && hasExchange) {
            throw bad("Yêu cầu hoàn tiền không có sản phẩm đổi");
        }
        if (EXCHANGE.equals(type)) {
            if (!hasExchange) {
                throw bad("Yêu cầu đổi hàng cần ít nhất một sản phẩm đổi");
            }
            Map<Long, Integer> ex = new LinkedHashMap<>();
            for (OmsRequestDtos.OrderReturn.ExchangeItem xi : req.getExchangeItems()) {
                if (xi.getVariationId() == null || xi.getQuantity() == null || xi.getQuantity() < 1) {
                    throw bad("Mỗi dòng đổi cần variationId và quantity >= 1");
                }
                ex.merge(xi.getVariationId(), xi.getQuantity(), Integer::sum);
            }
            for (Map.Entry<Long, Integer> e : ex.entrySet()) {
                ProductSkuEntity sku = skus.findOne(e.getKey());
                if (!sellable(sku)) {
                    throw bad("Sản phẩm đổi " + e.getKey() + " không còn bán");
                }
                BigDecimal unit = sku.effectivePrice().subtract(promotionPricing.unitDiscount(sku.getProductId(), sku.effectivePrice()));
                BigDecimal lineTotal = unit.multiply(BigDecimal.valueOf(e.getValue()));
                ProductEntity product = products.findOne(sku.getProductId());
                exchangeLines.add(new Line(null, sku.getId(), sku.getProductId(),
                        product != null ? product.getName() : sku.getName(), sku.getName(), sku.getSkuCode(),
                        product != null ? product.getThumbnailUrl() : null, unit, e.getValue(), lineTotal));
                exchangeTotal = exchangeTotal.add(lineTotal);
            }
        }
        return new Plan(order, type, reason, returnLines, exchangeLines, returnTotal, exchangeTotal);
    }

    private static String sha256(String text) {
        try {
            byte[] d = java.security.MessageDigest.getInstance("SHA-256")
                    .digest(text.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            return java.util.HexFormat.of().formatHex(d);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private static String fingerprint(Plan p) {
        StringBuilder sb = new StringBuilder(p.order().getId() + "|" + p.type() + "|");
        p.returnLines().forEach(l -> sb.append("R").append(l.orderItemId()).append(':').append(l.quantity()).append(':')
                .append(l.lineTotal().longValue()).append(';'));
        p.exchangeLines().forEach(l -> sb.append("E").append(l.skuId()).append(':').append(l.quantity()).append(':')
                .append(l.lineTotal().longValue()).append(';'));
        return sha256(sb.toString());
    }

    // ============================== quote / commit / create ==============================

    @Transactional(readOnly = true)
    @Override
    public Map<String, Object> quote(OmsRequestDtos.OrderReturn req) {
        Plan p = plan(req, null);
        String fp = fingerprint(p);
        BigDecimal topUp = p.difference().max(BigDecimal.ZERO);
        Map<String, Object> shippingRef = new LinkedHashMap<>();
        shippingRef.put("declaredValueFeeMinor", 0);
        shippingRef.put("shippingFeeMinor", 0);
        shippingRef.put("totalFeeMinor", 0);
        shippingRef.put("customerPayableShippingMinor", 0);
        Map<String, Object> ref = new LinkedHashMap<>();
        ref.put("pricingEvaluationId", "RQ-" + fp.substring(0, 24));
        ref.put("fingerprint", fp);
        ref.put("exchangeMerchandiseTotalMinor", p.exchangeTotal().longValue());
        ref.put("shipping", shippingRef);
        Map<String, Object> product = new LinkedHashMap<>();
        product.put("returnedMerchandiseSettlementMinor", p.returnTotal().longValue());
        product.put("exchangeMerchandiseTotalMinor", p.exchangeTotal().longValue());
        product.put("merchandiseTopUpMinor", topUp.longValue());
        Map<String, Object> shipping = new LinkedHashMap<>();
        shipping.put("returnInboundShippingFeeMinor", 0);
        shipping.put("exchangeOutboundShippingFeeMinor", 0);
        shipping.put("exchangeOutboundTotalFeeMinor", 0);
        shipping.put("customerPayableShippingMinor", 0);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("success", true);
        out.put("quoteReference", ref);
        out.put("product", product);
        out.put("shipping", shipping);
        return out;
    }

    @Transactional
    @Override
    public Map<String, Object> commit(OmsRequestDtos.OrderReturn req, OrderOmsService.Actor actor) {
        if (req == null || req.getPricingQuote() == null || req.getPricingQuote().get("fingerprint") == null) {
            throw bad("Thiếu pricingQuote.fingerprint - hãy gọi báo giá trước");
        }
        Plan p = plan(req, null);
        if (!fingerprint(p).equals(String.valueOf(req.getPricingQuote().get("fingerprint")))) {
            throw BusinessException.conflict("ORDER_RETURN_QUOTE_STALE",
                    "Báo giá đổi trả đã thay đổi, vui lòng báo giá lại");
        }
        OrderReturnEntity r = persist(req, p, actor);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("success", true);
        out.put("orderReturnCode", r.getCode());
        out.put("shipmentQueued", false);
        return out;
    }

    @Transactional
    @Override
    public Map<String, Object> create(OmsRequestDtos.OrderReturn req, OrderOmsService.Actor actor) {
        Plan p = plan(req, null);
        return detailMap(persist(req, p, actor));
    }

    private OrderReturnEntity persist(OmsRequestDtos.OrderReturn req, Plan p, OrderOmsService.Actor actor) {
        OrderReturnEntity r = new OrderReturnEntity();
        r.setCode("RT" + System.currentTimeMillis() + ThreadLocalRandom.current().nextInt(100, 999));
        r.setOrderId(p.order().getId());
        r.setType(p.type());
        r.setReason(p.reason());
        r.setReasonCategory(SUBJECTIVE_REASON.equals(p.reason()) ? "Subjective" : "Objective");
        r.setStatus(PENDING);
        r.setReturnTotal(p.returnTotal());
        r.setExchangeTotal(p.exchangeTotal());
        r.setPriceDifference(p.difference());
        r.setTotalRefundAmount(BigDecimal.ZERO);
        r.setReturnFaultParty(req.getReturnFaultParty() != null && !req.getReturnFaultParty().isBlank()
                ? req.getReturnFaultParty().trim() : (SUBJECTIVE_REASON.equals(p.reason()) ? "Customer" : "Retailer"));
        r.setPaymentMethod(req.getPaymentMethod() != null && !req.getPaymentMethod().isBlank()
                ? req.getPaymentMethod().trim() : p.order().getPaymentMethod());
        applyCommon(r, req);
        r.setCreatedId(actor == null ? null : actor.id());
        returns.create(r);
        persistItems(r.getId(), p, actor);
        addHistory(r.getId(), null, PENDING, actor, req.getNote());
        oms.audit(p.order().getId(), "oms", "RETURN_REQUEST_CREATED", "RETURN",
                "Tạo yêu cầu " + (EXCHANGE.equals(p.type()) ? "đổi hàng " : "hoàn tiền ") + r.getCode(), req.getNote(), actor,
                List.of(change("returnTotal", null, p.returnTotal().longValue())));
        return r;
    }

    private static void applyCommon(OrderReturnEntity r, OmsRequestDtos.OrderReturn req) {
        if (req.getNote() != null) {
            r.setNote(req.getNote().isBlank() ? null : req.getNote().trim());
        }
        if (req.getTrackingCode() != null) {
            r.setTrackingCode(req.getTrackingCode().isBlank() ? null : req.getTrackingCode().trim());
        }
        if (req.getEvidenceImageUrls() != null) {
            r.setEvidenceUrls(PromoJson.write(req.getEvidenceImageUrls()));
        }
        if (req.getShippingAddress() != null) {
            r.setShippingAddress(PromoJson.write(req.getShippingAddress()));
        }
        String holder = first(req.getRefundBankAccountHolder(), req.getBankAccountHolder());
        String number = first(req.getRefundBankAccountNumber(), req.getBankAccountNumber());
        String bank = first(req.getRefundBankName(), req.getBankName());
        String branch = first(req.getRefundBankBranch(), req.getBankBranch());
        if (holder != null) {
            r.setRefundBankHolder(holder);
        }
        if (number != null) {
            r.setRefundBankNumber(number);
        }
        if (bank != null) {
            r.setRefundBankName(bank);
        }
        if (branch != null) {
            r.setRefundBankBranch(branch);
        }
    }

    private static String first(String a, String b) {
        if (a != null && !a.isBlank()) {
            return a.trim();
        }
        return b != null && !b.isBlank() ? b.trim() : null;
    }

    private void persistItems(Long returnId, Plan p, OrderOmsService.Actor actor) {
        for (Line l : p.returnLines()) {
            returnItems.create(item(returnId, KIND_RETURN, l, actor));
        }
        for (Line l : p.exchangeLines()) {
            returnItems.create(item(returnId, KIND_EXCHANGE, l, actor));
        }
    }

    private static OrderReturnItemEntity item(Long returnId, String kind, Line l, OrderOmsService.Actor actor) {
        OrderReturnItemEntity e = new OrderReturnItemEntity();
        e.setReturnId(returnId);
        e.setKind(kind);
        e.setOrderItemId(l.orderItemId());
        e.setSkuId(l.skuId());
        e.setProductId(l.productId());
        e.setProductName(l.productName());
        e.setVariationName(l.variationName());
        e.setSkuCode(l.skuCode());
        e.setImageUrl(l.image());
        e.setUnitPrice(l.unitPrice());
        e.setQuantity(l.quantity());
        e.setLineTotal(l.lineTotal());
        e.setCreatedId(actor == null ? null : actor.id());
        return e;
    }

    private void addHistory(Long returnId, String from, String to, OrderOmsService.Actor actor, String note) {
        OrderReturnHistoryEntity h = new OrderReturnHistoryEntity();
        h.setReturnId(returnId);
        h.setFromStatus(from);
        h.setToStatus(to);
        h.setChangedById(actor == null ? null : actor.id());
        h.setChangedByType("employee");
        h.setNote(note == null || note.isBlank() ? null : note.trim());
        h.setCreatedId(actor == null ? null : actor.id());
        history.create(h);
    }

    private static Map<String, Object> change(String field, Object oldValue, Object newValue) {
        Map<String, Object> c = new LinkedHashMap<>();
        c.put("field", field);
        c.put("oldValue", oldValue);
        c.put("newValue", newValue);
        return c;
    }

    // ============================== đọc ==============================

    private String nameOf(Long userId) {
        if (userId == null) {
            return null;
        }
        UserEntity u = users.findOne(userId);
        if (u == null) {
            return null;
        }
        return u.getFullName() != null && !u.getFullName().isBlank() ? u.getFullName() : u.getPhone();
    }

    private static String m(BigDecimal v) {
        return String.valueOf((v == null ? BigDecimal.ZERO : v).longValue());
    }

    private Map<String, Object> itemMap(OrderReturnEntity r, OrderReturnItemEntity i) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", String.valueOf(i.getId()));
        m.put("createdAt", PromoJson.iso(i.getCreatedDate()));
        m.put("updatedAt", PromoJson.iso(i.getCreatedDate()));
        m.put("orderReturnId", String.valueOf(r.getId()));
        m.put("orderItemId", i.getOrderItemId() == null ? null : String.valueOf(i.getOrderItemId()));
        m.put("variationId", String.valueOf(i.getSkuId()));
        m.put("productId", i.getProductId() == null ? null : String.valueOf(i.getProductId()));
        m.put("productName", i.getProductName());
        m.put("variationName", i.getVariationName());
        m.put("skuCode", i.getSkuCode());
        m.put("image", i.getImageUrl());
        m.put("unitPrice", m(i.getUnitPrice()));
        m.put("salePrice", m(i.getUnitPrice()));
        m.put("quantity", i.getQuantity());
        m.put("lineTotal", m(i.getLineTotal()));
        return m;
    }

    private Map<String, Object> listMap(OrderReturnEntity r, OrderEntity o) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", String.valueOf(r.getId()));
        m.put("createdAt", PromoJson.iso(r.getCreatedDate()));
        m.put("updatedAt", PromoJson.iso(r.getUpdatedDate() != null ? r.getUpdatedDate() : r.getCreatedDate()));
        m.put("tenantCode", "default");
        m.put("returnCode", r.getCode());
        m.put("originalOrderId", String.valueOf(r.getOrderId()));
        m.put("originalOrderCode", o == null ? null : o.getCode());
        m.put("orderId", String.valueOf(r.getOrderId()));
        m.put("orderCode", o == null ? null : o.getCode());
        m.put("customerId", o == null || o.getUserId() == null ? null : String.valueOf(o.getUserId()));
        m.put("customerName", o == null ? null : o.getShipRecipientName());
        m.put("customerPhone", o == null ? null : o.getShipPhone());
        m.put("firstName", o == null ? null : o.getShipRecipientName());
        m.put("lastName", "");
        m.put("receiverPhone", o == null ? null : o.getShipPhone());
        m.put("source", r.getSource());
        m.put("status", r.getStatus());
        m.put("type", r.getType());
        m.put("reason", r.getReason());
        m.put("reasonCategory", r.getReasonCategory());
        m.put("returnFaultParty", r.getReturnFaultParty());
        m.put("originalOrderAmountReceived", o == null ? null : m(o.getGrandTotal()));
        m.put("note", r.getNote());
        m.put("returnTotal", m(r.getReturnTotal()));
        m.put("exchangeTotal", m(r.getExchangeTotal()));
        m.put("priceDifference", m(r.getPriceDifference()));
        m.put("exchangeMerchandiseTopUpAmount", m(r.getPriceDifference().max(BigDecimal.ZERO)));
        m.put("totalRefundAmount", m(r.getTotalRefundAmount()));
        m.put("paymentMethod", r.getPaymentMethod());
        m.put("evidenceImageUrls", r.getEvidenceUrls() == null ? List.of()
                : PromoJson.strings(readList(r.getEvidenceUrls())));
        m.put("shippingAddressSnapshot", r.getShippingAddress() == null ? null : PromoJson.readMap(r.getShippingAddress()));
        m.put("trackingCode", r.getTrackingCode());
        m.put("exchangeFulfillmentOrderId", r.getExchangeOrderId() == null ? null : String.valueOf(r.getExchangeOrderId()));
        m.put("originalOrderTrackingCode", o == null ? null : o.getTrackingCode());
        m.put("resolvedById", r.getResolvedById() == null ? null : String.valueOf(r.getResolvedById()));
        m.put("resolvedByName", r.getResolvedByName());
        m.put("resolvedAt", PromoJson.iso(r.getResolvedAt()));
        m.put("resolvedNote", r.getResolvedNote());
        m.put("reasonReject", r.getRejectReason());
        m.put("refundBankAccountHolder", r.getRefundBankHolder());
        m.put("refundBankAccountNumber", r.getRefundBankNumber());
        m.put("refundBankName", r.getRefundBankName());
        m.put("refundBankBranch", r.getRefundBankBranch());
        m.put("bankAccountHolder", r.getRefundBankHolder());
        m.put("bankAccountNumber", r.getRefundBankNumber());
        m.put("bankName", r.getRefundBankName());
        m.put("bankBranch", r.getRefundBankBranch());
        Map<String, Object> by = new LinkedHashMap<>();
        by.put("id", r.getCreatedId() == null ? null : String.valueOf(r.getCreatedId()));
        by.put("userType", "employee");
        by.put("displayName", nameOf(r.getCreatedId()));
        m.put("createdByUser", r.getCreatedId() == null ? null : by);
        List<Map<String, Object>> ret = new ArrayList<>();
        List<Map<String, Object>> exc = new ArrayList<>();
        for (OrderReturnItemEntity i : returnItems.findByReturnId(r.getId())) {
            (KIND_RETURN.equals(i.getKind()) ? ret : exc).add(itemMap(r, i));
        }
        m.put("returnItems", ret);
        m.put("exchangeItems", exc);
        return m;
    }

    private static List<?> readList(String json) {
        try {
            return new com.fasterxml.jackson.databind.ObjectMapper().readValue(json, List.class);
        } catch (Exception e) {
            return List.of();
        }
    }

    private Map<String, Object> detailMap(OrderReturnEntity r) {
        OrderEntity o = orders.findOne(r.getOrderId());
        Map<String, Object> m = listMap(r, o);
        m.put("shippingMethod", null);
        m.put("returnShippingFee", 0);
        List<Map<String, Object>> hs = new ArrayList<>();
        for (OrderReturnHistoryEntity h : history.findByReturnId(r.getId())) {
            Map<String, Object> x = new LinkedHashMap<>();
            x.put("id", String.valueOf(h.getId()));
            x.put("orderReturnId", String.valueOf(r.getId()));
            x.put("fromStatus", h.getFromStatus());
            x.put("toStatus", h.getToStatus());
            x.put("changedById", h.getChangedById() == null ? null : String.valueOf(h.getChangedById()));
            x.put("changedByType", h.getChangedByType());
            x.put("note", h.getNote());
            x.put("createdAt", PromoJson.iso(h.getCreatedDate()));
            hs.add(x);
        }
        m.put("orderReturnStatusHistory", hs);
        return m;
    }

    @Transactional(readOnly = true)
    @Override
    public Map<String, Object> detail(String idOrCode) {
        return detailMap(find(idOrCode));
    }

    private static int intOf(Object o, int def) {
        try {
            return o == null ? def : Integer.parseInt(String.valueOf(o).trim());
        } catch (NumberFormatException e) {
            return def;
        }
    }

    private static String low(Object o) {
        return o == null ? "" : String.valueOf(o).trim().toLowerCase(Locale.ROOT);
    }

    @Transactional(readOnly = true)
    @Override
    public Map<String, Object> list(Map<String, Object> f) {
        Map<String, Object> filter = f == null ? new LinkedHashMap<>() : f;
        int page = Math.max(intOf(filter.get("page"), 1), 1);
        int take = Math.min(Math.max(intOf(filter.get("take"), 20), 1), 200);
        String search = low(filter.get("search"));
        String status = low(filter.get("status"));
        String type = low(filter.get("type"));
        String reason = low(filter.get("reason"));
        String category = low(filter.get("reasonCategory"));
        Date from = PromoJson.date(filter.get("startDate"), "startDate");
        Date to = PromoJson.date(filter.get("endDate"), "endDate");
        if (to != null && String.valueOf(filter.get("endDate")).trim().length() <= 10) {
            to = new Date(to.getTime() + 24L * 3600 * 1000 - 1);
        }
        Long orderFilter = null;
        String orderKey = filter.get("orderId") == null ? "" : String.valueOf(filter.get("orderId")).trim();
        if (!orderKey.isEmpty()) {
            try {
                orderFilter = findOrder(orderKey).getId();
            } catch (ResourceNotFoundException ex) {
                orderFilter = -1L;
            }
        }
        Map<Long, OrderEntity> orderCache = new LinkedHashMap<>();
        List<OrderReturnEntity> base = new ArrayList<>();
        for (OrderReturnEntity r : returns.findAll(Sort.unsorted())) {
            OrderEntity o = orderCache.computeIfAbsent(r.getOrderId(), orders::findOne);
            if (orderFilter != null && !orderFilter.equals(r.getOrderId())) {
                continue;
            }
            if (!type.isEmpty() && !type.equals(low(r.getType()))) {
                continue;
            }
            if (!reason.isEmpty() && !reason.equals(low(r.getReason()))) {
                continue;
            }
            if (!category.isEmpty() && !category.equals(low(r.getReasonCategory()))) {
                continue;
            }
            if (from != null && r.getCreatedDate().before(from) || to != null && r.getCreatedDate().after(to)) {
                continue;
            }
            if (!search.isEmpty()) {
                String hay = (low(r.getCode()) + " " + (o == null ? "" : low(o.getCode()) + " " + low(o.getShipPhone())
                        + " " + low(o.getShipRecipientName())));
                if (!hay.contains(search)) {
                    continue;
                }
            }
            base.add(r);
        }
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("ALL", base.size());
        for (String s : STATUSES) {
            summary.put(s, base.stream().filter(r -> s.equals(r.getStatus())).count());
        }
        for (String t : TYPES) {
            summary.put(t, base.stream().filter(r -> t.equals(r.getType())).count());
        }
        List<OrderReturnEntity> filtered = new ArrayList<>();
        for (OrderReturnEntity r : base) {
            if (status.isEmpty() || status.equals("all") || status.equals(low(r.getStatus()))) {
                filtered.add(r);
            }
        }
        String orderBy = low(filter.get("orderBy"));
        Comparator<OrderReturnEntity> cmp = switch (orderBy) {
            case "returncode" -> Comparator.comparing(OrderReturnEntity::getCode);
            case "pricedifference" -> Comparator.comparing(OrderReturnEntity::getPriceDifference);
            case "updatedat" -> Comparator.comparing(
                    (OrderReturnEntity r) -> r.getUpdatedDate() != null ? r.getUpdatedDate() : r.getCreatedDate());
            default -> Comparator.comparing(OrderReturnEntity::getCreatedDate).thenComparing(OrderReturnEntity::getId);
        };
        filtered.sort("asc".equals(low(filter.get("orderType"))) ? cmp : cmp.reversed());
        int fromIdx = Math.min((page - 1) * take, filtered.size());
        int toIdx = Math.min(fromIdx + take, filtered.size());
        List<Map<String, Object>> list = new ArrayList<>();
        for (OrderReturnEntity r : filtered.subList(fromIdx, toIdx)) {
            list.add(listMap(r, orderCache.get(r.getOrderId())));
        }
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("list", list);
        out.put("total", filtered.size());
        out.put("page", page);
        out.put("take", take);
        out.put("summary", summary);
        return out;
    }

    // ============================== sửa / duyệt / từ chối ==============================

    private void requirePending(OrderReturnEntity r, String action) {
        if (!PENDING.equals(r.getStatus())) {
            throw conflict("Chỉ " + action + " được yêu cầu đang Pending (hiện tại: " + r.getStatus() + ")",
                    r.getStatus(), action);
        }
    }

    @Transactional
    @Override
    public Map<String, Object> update(String idOrCode, OmsRequestDtos.OrderReturn req, OrderOmsService.Actor actor) {
        OrderReturnEntity r = find(idOrCode);
        requirePending(r, "sửa");
        if (req.getStatus() != null && !req.getStatus().isBlank()) {
            if (!CANCELLED.equalsIgnoreCase(req.getStatus().trim()) && !PENDING.equalsIgnoreCase(req.getStatus().trim())) {
                throw bad("Chỉ đổi trạng thái sang Cancelled; dùng approve/reject cho các trạng thái khác");
            }
        }
        if (req.getReturnItems() != null || req.getExchangeItems() != null || req.getType() != null
                || req.getReason() != null) {
            OmsRequestDtos.OrderReturn merged = new OmsRequestDtos.OrderReturn();
            merged.setOrderId(String.valueOf(r.getOrderId()));
            merged.setType(req.getType() != null ? req.getType() : r.getType());
            merged.setReason(req.getReason() != null ? req.getReason() : r.getReason());
            List<OrderReturnItemEntity> cur = returnItems.findByReturnId(r.getId());
            if (req.getReturnItems() != null) {
                merged.setReturnItems(req.getReturnItems());
            } else {
                List<OmsRequestDtos.OrderReturn.ReturnItem> ri = new ArrayList<>();
                for (OrderReturnItemEntity i : cur) {
                    if (KIND_RETURN.equals(i.getKind())) {
                        OmsRequestDtos.OrderReturn.ReturnItem x = new OmsRequestDtos.OrderReturn.ReturnItem();
                        x.setOrderItemId(i.getOrderItemId());
                        x.setQuantity(i.getQuantity());
                        ri.add(x);
                    }
                }
                merged.setReturnItems(ri);
            }
            if (req.getExchangeItems() != null) {
                merged.setExchangeItems(req.getExchangeItems());
            } else {
                List<OmsRequestDtos.OrderReturn.ExchangeItem> ei = new ArrayList<>();
                for (OrderReturnItemEntity i : cur) {
                    if (KIND_EXCHANGE.equals(i.getKind())) {
                        OmsRequestDtos.OrderReturn.ExchangeItem x = new OmsRequestDtos.OrderReturn.ExchangeItem();
                        x.setVariationId(i.getSkuId());
                        x.setQuantity(i.getQuantity());
                        ei.add(x);
                    }
                }
                merged.setExchangeItems(ei);
            }
            Plan p = plan(merged, r.getId());
            for (OrderReturnItemEntity i : cur) {
                returnItems.delete(i);
            }
            persistItems(r.getId(), p, actor);
            r.setType(p.type());
            r.setReason(p.reason());
            r.setReasonCategory(SUBJECTIVE_REASON.equals(p.reason()) ? "Subjective" : "Objective");
            r.setReturnTotal(p.returnTotal());
            r.setExchangeTotal(p.exchangeTotal());
            r.setPriceDifference(p.difference());
        }
        applyCommon(r, req);
        if (req.getPaymentMethod() != null && !req.getPaymentMethod().isBlank()) {
            r.setPaymentMethod(req.getPaymentMethod().trim());
        }
        if (req.getReturnFaultParty() != null && !req.getReturnFaultParty().isBlank()) {
            r.setReturnFaultParty(req.getReturnFaultParty().trim());
        }
        r.touch();
        r.setUpdatedId(actor == null ? null : actor.id());
        boolean cancel = req.getStatus() != null && CANCELLED.equalsIgnoreCase(req.getStatus().trim());
        if (cancel) {
            r.setStatus(CANCELLED);
            addHistory(r.getId(), PENDING, CANCELLED, actor, req.getNote());
        }
        returns.update(r);
        oms.audit(r.getOrderId(), "oms", cancel ? "RETURN_REQUEST_CANCELLED" : "RETURN_REQUEST_UPDATED", "RETURN",
                (cancel ? "Huỷ" : "Cập nhật") + " yêu cầu đổi trả " + r.getCode(), req.getNote(), actor, null);
        return detailMap(r);
    }

    @Transactional
    @Override
    public Map<String, Object> approve(String idOrCode, String note, OrderOmsService.Actor actor) {
        OrderReturnEntity r = find(idOrCode);
        requirePending(r, "duyệt");
        OrderEntity order = orders.findOne(r.getOrderId());
        List<OrderReturnItemEntity> items = returnItems.findByReturnId(r.getId());

        // 1. Trả hàng về kho + cập nhật đơn gốc (dùng đúng luồng returnOrder: CAS số lượng, nhập kho, RETURNED khi trả hết).
        AdminOrderReturnRequestDto dto = new AdminOrderReturnRequestDto();
        List<AdminOrderReturnRequestDto.Line> lines = new ArrayList<>();
        Map<Long, Integer> exchangeQty = new LinkedHashMap<>();
        for (OrderReturnItemEntity i : items) {
            if (KIND_RETURN.equals(i.getKind())) {
                lines.add(new AdminOrderReturnRequestDto.Line(i.getOrderItemId(), i.getQuantity()));
            } else {
                exchangeQty.merge(i.getSkuId(), i.getQuantity(), Integer::sum);
            }
        }
        dto.setLines(lines);
        orderService.returnOrder(order.getCode(), dto);

        // 2. Đổi hàng: tạo đơn thay thế, giá trị hàng đã trả được trừ vào đơn mới.
        BigDecimal refundAmount = r.getReturnTotal();
        if (EXCHANGE.equals(r.getType())) {
            OrderResponseDto ex = orderService.createExchangeOrder(order.getCode(), exchangeQty, r.getReturnTotal(),
                    "Đơn đổi hàng từ yêu cầu " + r.getCode() + " (đơn gốc " + order.getCode() + ")",
                    actor == null ? null : actor.id());
            r.setExchangeOrderId(ex.getId());
            refundAmount = r.getReturnTotal().subtract(r.getExchangeTotal()).max(BigDecimal.ZERO);
        }

        // 3. Hoàn tiền (chỉ khi đơn đã thu tiền; không vượt số còn hoàn được).
        String resolvedNote = note == null || note.isBlank() ? null : note.trim();
        BigDecimal refunded = BigDecimal.ZERO;
        String extra = "";
        if (refundAmount.signum() > 0) {
            OrderEntity fresh = orders.findOne(order.getId());
            if (OrderEntity.PAID.equals(fresh.getPaymentStatus())) {
                BigDecimal refundable = fresh.getGrandTotal().subtract(fresh.getRefundedAmount());
                refunded = refundAmount.min(refundable);
                if (refunded.signum() > 0) {
                    orderService.refund(order.getCode(), refunded, "Hoàn tiền theo yêu cầu đổi trả " + r.getCode());
                    oms.audit(order.getId(), "order_refund", "REFUND", "REFUND", "Hoàn tiền " + refunded.longValue(),
                            "Theo yêu cầu đổi trả " + r.getCode(), actor,
                            List.of(change("refundedAmount", fresh.getRefundedAmount().longValue(),
                                    fresh.getRefundedAmount().add(refunded).longValue())));
                }
            } else {
                extra = " (đơn chưa thanh toán nên không hoàn tiền)";
            }
        }
        r.setTotalRefundAmount(refunded);
        r.setStatus(COMPLETED);
        r.setResolvedById(actor == null ? null : actor.id());
        r.setResolvedByName(actor == null ? null : nameOf(actor.id()));
        r.setResolvedAt(new Date());
        r.setResolvedNote(resolvedNote);
        r.touch();
        r.setUpdatedId(actor == null ? null : actor.id());
        returns.update(r);
        addHistory(r.getId(), PENDING, APPROVED, actor, resolvedNote);
        addHistory(r.getId(), APPROVED, COMPLETED, actor, "Đã nhập kho, hoàn tất" + extra);
        oms.audit(order.getId(), "oms", "RETURN_APPROVED", "RETURN", "Duyệt yêu cầu đổi trả " + r.getCode() + extra,
                resolvedNote, actor, List.of(change("status", PENDING, COMPLETED),
                        change("totalRefundAmount", 0, refunded.longValue())));
        return detailMap(r);
    }

    @Transactional
    @Override
    public Map<String, Object> reject(String idOrCode, String note, OrderOmsService.Actor actor) {
        OrderReturnEntity r = find(idOrCode);
        requirePending(r, "từ chối");
        if (note == null || note.isBlank()) {
            throw bad("Cần nhập lý do từ chối");
        }
        r.setStatus(REJECTED);
        r.setRejectReason(note.trim());
        r.setResolvedById(actor == null ? null : actor.id());
        r.setResolvedByName(actor == null ? null : nameOf(actor.id()));
        r.setResolvedAt(new Date());
        r.setResolvedNote(note.trim());
        r.touch();
        r.setUpdatedId(actor == null ? null : actor.id());
        returns.update(r);
        addHistory(r.getId(), PENDING, REJECTED, actor, note);
        oms.audit(r.getOrderId(), "oms", "RETURN_REJECTED", "RETURN", "Từ chối yêu cầu đổi trả " + r.getCode(),
                note.trim(), actor, List.of(change("status", PENDING, REJECTED)));
        return detailMap(r);
    }
}
