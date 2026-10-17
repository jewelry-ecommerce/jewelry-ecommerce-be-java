package com.pk.core.business.service.impl;

import com.pk.core.business.repository.OrderAuditLogRepo;
import com.pk.core.business.repository.OrderNoteRepo;
import com.pk.core.business.repository.OrderRepo;
import com.pk.core.business.repository.UserRepo;
import com.pk.core.business.service.OrderOmsService;
import com.pk.core.business.service.OrderService;
import com.pk.core.common.exception.BusinessException;
import com.pk.core.common.exception.ErrorCode;
import com.pk.core.common.exception.ResourceNotFoundException;
import com.pk.core.model.dto.request.AdminOrderReturnRequestDto;
import com.pk.core.model.dto.request.AdminOrderStatusRequestDto;
import com.pk.core.model.dto.request.OmsRequestDtos;
import com.pk.core.model.dto.response.OrderItemResponseDto;
import com.pk.core.model.dto.response.OrderResponseDto;
import com.pk.core.model.entity.OrderAuditLogEntity;
import com.pk.core.model.entity.OrderEntity;
import com.pk.core.model.entity.OrderNoteEntity;
import com.pk.core.model.entity.UserEntity;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class OrderOmsServiceImpl implements OrderOmsService {

    private static final Set<String> NOTE_TYPES = Set.of("order", "internal");

    private final OrderService orderService;
    private final OrderRepo orders;
    private final OrderNoteRepo notes;
    private final OrderAuditLogRepo logs;
    private final UserRepo users;

    // ============================== helpers ==============================

    private OrderEntity order(String key) {
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

    private static String trunc(String v, int max) {
        if (v == null) {
            return null;
        }
        return v.length() <= max ? v : v.substring(0, max);
    }

    private static Object plain(Object v) {
        if (v instanceof BigDecimal b) {
            return b.longValue();
        }
        return v;
    }

    private static Map<String, Object> snapshot(OrderResponseDto d) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("status", d.getStatus());
        m.put("paymentStatus", d.getPaymentStatus());
        m.put("paymentMethod", d.getPaymentMethod());
        m.put("shippingMethod", d.getShippingMethod());
        m.put("trackingCode", d.getTrackingCode());
        m.put("shipRecipientName", d.getShipRecipientName());
        m.put("shipPhone", d.getShipPhone());
        m.put("shipProvince", d.getShipProvince());
        m.put("shipDistrict", d.getShipDistrict());
        m.put("shipWard", d.getShipWard());
        m.put("shipAddressLine", d.getShipAddressLine());
        m.put("subtotal", d.getSubtotal());
        m.put("productDiscount", d.getProductDiscount());
        m.put("voucherDiscount", d.getVoucherDiscount());
        m.put("shippingFee", d.getShippingFee());
        m.put("grandTotal", d.getGrandTotal());
        m.put("refundedAmount", d.getRefundedAmount());
        m.put("appliedVoucherCode", d.getAppliedVoucherCode());
        m.put("note", d.getNote());
        StringBuilder items = new StringBuilder();
        if (d.getItems() != null) {
            for (OrderItemResponseDto i : d.getItems()) {
                if (items.length() > 0) {
                    items.append("; ");
                }
                items.append(i.getSkuCode() != null ? i.getSkuCode() : i.getSkuId()).append(" x").append(i.getQuantity());
            }
        }
        m.put("items", items.toString());
        return m;
    }

    private static List<Map<String, Object>> diff(OrderResponseDto before, OrderResponseDto after) {
        List<Map<String, Object>> out = new ArrayList<>();
        if (before == null || after == null) {
            return out;
        }
        Map<String, Object> a = snapshot(before);
        Map<String, Object> b = snapshot(after);
        for (String field : a.keySet()) {
            Object x = plain(a.get(field));
            Object y = plain(b.get(field));
            if (!Objects.equals(x, y)) {
                Map<String, Object> c = new LinkedHashMap<>();
                c.put("field", field);
                c.put("oldValue", x);
                c.put("newValue", y);
                out.add(c);
            }
        }
        return out;
    }

    // ============================== audit ==============================

    @Transactional
    @Override
    public void audit(Long orderId, String entityName, String action, String actionType, String message, String note,
                      Actor actor, List<Map<String, Object>> changes) {
        OrderAuditLogEntity e = new OrderAuditLogEntity();
        e.setOrderId(orderId);
        e.setEntityName(entityName == null || entityName.isBlank() ? "oms" : entityName);
        e.setAction(action);
        e.setActionType(actionType);
        e.setSource("admin");
        e.setMessage(trunc(message, 500));
        e.setNote(trunc(note, 1000));
        if (actor != null) {
            e.setActorId(actor.id());
            e.setActorRole(actor.roleName());
            e.setActorName(nameOf(actor.id()));
            e.setCreatedId(actor.id());
        }
        if (changes != null && !changes.isEmpty()) {
            e.setChanges(PromoJson.write(changes));
        }
        logs.create(e);
    }

    @Transactional(readOnly = true)
    @Override
    public Map<String, Object> auditLogs(String entityName, String entityId, int page, int take) {
        int p = Math.max(page, 1);
        int t = Math.min(Math.max(take, 1), 200);
        List<OrderAuditLogEntity> all = new ArrayList<>();
        try {
            OrderEntity o = order(entityId);
            for (OrderAuditLogEntity l : logs.findByOrderId(o.getId())) {
                if (entityName == null || entityName.isBlank() || entityName.trim().equalsIgnoreCase(l.getEntityName())) {
                    all.add(l);
                }
            }
        } catch (ResourceNotFoundException ex) {
            // không có đơn -> danh sách rỗng
        }
        Collections.reverse(all); // mới nhất trước (findByOrderId sắp theo id tăng dần)
        int from = Math.min((p - 1) * t, all.size());
        int to = Math.min(from + t, all.size());
        List<Map<String, Object>> list = new ArrayList<>();
        for (OrderAuditLogEntity l : all.subList(from, to)) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", String.valueOf(l.getId()));
            m.put("entityName", l.getEntityName());
            m.put("action", l.getAction());
            m.put("actionType", l.getActionType());
            m.put("source", l.getSource());
            m.put("message", l.getMessage());
            m.put("note", l.getNote());
            m.put("createdAt", PromoJson.iso(l.getCreatedDate()));
            m.put("updatedAt", PromoJson.iso(l.getCreatedDate()));
            Map<String, Object> actor = new LinkedHashMap<>();
            actor.put("id", l.getActorId() == null ? null : String.valueOf(l.getActorId()));
            actor.put("name", l.getActorName());
            actor.put("roleName", l.getActorRole());
            m.put("actor", actor);
            List<Object> changes = new ArrayList<>();
            if (l.getChanges() != null && !l.getChanges().isBlank()) {
                try {
                    changes = new com.fasterxml.jackson.databind.ObjectMapper().readValue(l.getChanges(),
                            new com.fasterxml.jackson.core.type.TypeReference<List<Object>>() {
                            });
                } catch (Exception ignored) {
                    // bỏ qua JSON hỏng
                }
            }
            m.put("changes", changes);
            list.add(m);
        }
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("list", list);
        out.put("total", all.size());
        out.put("page", p);
        out.put("take", t);
        return out;
    }

    // ============================== notes ==============================

    private Map<String, Object> noteMap(OrderEntity o, OrderNoteEntity n) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", String.valueOf(n.getId()));
        m.put("orderId", String.valueOf(o.getId()));
        m.put("orderCode", o.getCode());
        m.put("type", n.getType());
        m.put("content", n.getContent());
        m.put("authorId", n.getAuthorId() == null ? null : String.valueOf(n.getAuthorId()));
        m.put("authorType", n.getAuthorType());
        m.put("authorName", n.getAuthorName());
        m.put("warehouseId", null);
        m.put("createdAt", PromoJson.iso(n.getCreatedDate()));
        return m;
    }

    @Transactional(readOnly = true)
    @Override
    public List<Map<String, Object>> notes(String orderKey, String type) {
        OrderEntity o = order(orderKey);
        String t = type == null || type.isBlank() ? null : type.trim().toLowerCase(java.util.Locale.ROOT);
        if (t != null && !NOTE_TYPES.contains(t)) {
            throw BusinessException.badRequest(ErrorCode.VALIDATION_FAILED, "type chỉ nhận order | internal");
        }
        List<Map<String, Object>> out = new ArrayList<>();
        for (OrderNoteEntity n : notes.findByOrderId(o.getId())) {
            if (t == null || t.equals(n.getType())) {
                out.add(noteMap(o, n));
            }
        }
        return out;
    }

    @Transactional
    @Override
    public Map<String, Object> addNote(String orderKey, OmsRequestDtos.Note req, Actor actor) {
        OrderEntity o = order(orderKey);
        String content = req == null || req.getContent() == null ? "" : req.getContent().trim();
        if (content.isEmpty() || content.length() > 2000) {
            throw BusinessException.badRequest(ErrorCode.VALIDATION_FAILED, "Nội dung ghi chú cần 1-2000 ký tự");
        }
        String type = req.getType() == null || req.getType().isBlank() ? "order"
                : req.getType().trim().toLowerCase(java.util.Locale.ROOT);
        if (!NOTE_TYPES.contains(type)) {
            throw BusinessException.badRequest(ErrorCode.VALIDATION_FAILED, "type chỉ nhận order | internal");
        }
        OrderNoteEntity n = new OrderNoteEntity();
        n.setOrderId(o.getId());
        n.setType(type);
        n.setContent(content);
        if (actor != null) {
            n.setAuthorId(actor.id());
            n.setAuthorType(actor.roleName());
            n.setAuthorName(nameOf(actor.id()));
            n.setCreatedId(actor.id());
        }
        notes.create(n);
        audit(o.getId(), "oms", "ADD_NOTE", "NOTE", "Thêm ghi chú " + type, content, actor, null);
        return noteMap(o, n);
    }

    // ============================== create / update / duplicate / payment ==============================

    @Transactional(readOnly = true)
    @Override
    public Map<String, Object> quote(OmsRequestDtos.OrderUpsert req) {
        return orderService.quoteForAdmin(req);
    }

    @Transactional
    @Override
    public OrderResponseDto create(OmsRequestDtos.OrderUpsert req, Actor actor) {
        OrderResponseDto created = orderService.createByAdmin(req, actor.id());
        audit(created.getId(), "oms", "CREATE_ORDER", "CREATE", "Tạo đơn hàng thủ công " + created.getCode(),
                req.getNote(), actor, List.of(change("grandTotal", null, created.getGrandTotal())));
        return created;
    }

    @Transactional
    @Override
    public OrderResponseDto update(String orderKey, OmsRequestDtos.OrderUpsert req, Actor actor) {
        OrderResponseDto before = orderService.findByCodeForAdmin(orderKey);
        OrderResponseDto after = orderService.updateByAdmin(orderKey, req, actor.id());
        audit(after.getId(), "oms", "UPDATE_ORDER", "UPDATE", "Cập nhật đơn hàng " + after.getCode(), null, actor,
                diff(before, after));
        return after;
    }

    @Transactional
    @Override
    public OrderResponseDto duplicate(OmsRequestDtos.Duplicate req, Actor actor) {
        if (req == null || req.getSourceOrderId() == null || req.getSourceOrderId().isBlank()) {
            throw BusinessException.badRequest(ErrorCode.VALIDATION_FAILED, "Cần sourceOrderId");
        }
        OrderResponseDto source = orderService.findByCodeForAdmin(req.getSourceOrderId());
        boolean replace = Boolean.TRUE.equals(req.getReplaceSourceAfterDuplicate());
        OrderResponseDto created = orderService.duplicateForAdmin(req.getSourceOrderId(), replace,
                req.getCancellationReason(), req.getCancellationNote(), actor.id());
        audit(created.getId(), "oms", "DUPLICATE_ORDER", "CREATE", "Sao chép từ đơn " + source.getCode(), null, actor,
                List.of(change("grandTotal", null, created.getGrandTotal())));
        audit(source.getId(), "oms", replace ? "DUPLICATE_AND_CANCEL" : "DUPLICATED", "UPDATE",
                "Đã sao chép sang đơn " + created.getCode() + (replace ? " và huỷ đơn này" : ""),
                req.getCancellationNote(), actor, null);
        return created;
    }

    @Transactional
    @Override
    public OrderResponseDto confirmPayment(String orderKey, String note, Actor actor) {
        OrderResponseDto before = orderService.findByCodeForAdmin(orderKey);
        OrderResponseDto after = orderService.confirmPaymentByAdmin(orderKey, note);
        audit(after.getId(), "oms", "CONFIRM_PAYMENT", "PAYMENT", "Xác nhận đã thanh toán", note, actor,
                diff(before, after));
        return after;
    }

    // ============================== luồng cũ có ghi audit ==============================

    @Transactional
    @Override
    public OrderResponseDto updateStatus(String orderKey, AdminOrderStatusRequestDto req, Actor actor) {
        OrderResponseDto before = orderService.findByCodeForAdmin(orderKey);
        OrderResponseDto after = orderService.updateStatus(orderKey, req);
        audit(after.getId(), "oms", "UPDATE_STATUS", "STATUS",
                "Chuyển trạng thái " + before.getStatus() + " → " + after.getStatus(), req.getNote(), actor,
                diff(before, after));
        return after;
    }

    @Transactional
    @Override
    public OrderResponseDto cancel(String orderKey, String reason, Actor actor) {
        OrderResponseDto before = orderService.findByCodeForAdmin(orderKey);
        OrderResponseDto after = orderService.cancelByAdmin(orderKey, reason);
        audit(after.getId(), "oms", "CANCEL_ORDER", "STATUS", "Huỷ đơn hàng", reason, actor, diff(before, after));
        return after;
    }

    @Transactional
    @Override
    public OrderResponseDto returnLines(String orderKey, AdminOrderReturnRequestDto req, Actor actor) {
        OrderResponseDto before = orderService.findByCodeForAdmin(orderKey);
        OrderResponseDto after = orderService.returnOrder(orderKey, req);
        audit(after.getId(), "oms", "RETURN_ITEMS", "RETURN", "Xác nhận hoàn trả hàng", null, actor, diff(before, after));
        return after;
    }

    @Transactional
    @Override
    public OrderResponseDto refund(String orderKey, BigDecimal amount, String reason, Actor actor) {
        OrderResponseDto before = orderService.findByCodeForAdmin(orderKey);
        OrderResponseDto after = orderService.refund(orderKey, amount, reason);
        audit(after.getId(), "order_refund", "REFUND", "REFUND", "Hoàn tiền " + amount, reason, actor, diff(before, after));
        return after;
    }

    private static Map<String, Object> change(String field, Object oldValue, Object newValue) {
        Map<String, Object> c = new LinkedHashMap<>();
        c.put("field", field);
        c.put("oldValue", plain(oldValue));
        c.put("newValue", plain(newValue));
        return c;
    }
}
