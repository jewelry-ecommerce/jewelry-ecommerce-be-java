package com.pk.core.business.service;

import com.pk.core.model.dto.request.AdminOrderReturnRequestDto;
import com.pk.core.model.dto.request.AdminOrderStatusRequestDto;
import com.pk.core.model.dto.request.OmsRequestDtos;
import com.pk.core.model.dto.response.OrderResponseDto;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * OMS nâng cao của admin: ghi chú đơn, nhật ký thao tác (audit), tạo/sửa/nhân bản đơn, xác nhận thanh toán. Các thao
 * tác đổi trạng thái/huỷ/trả hàng/hoàn tiền cũ cũng đi qua đây để được ghi audit (người thao tác + thay đổi trường).
 */
public interface OrderOmsService {

    /** Người thao tác: id tài khoản + tên vai trò (ADMIN, ORDER_MANAGER...). */
    record Actor(Long id, String roleName) {
    }

    List<Map<String, Object>> notes(String orderKey, String type);

    Map<String, Object> addNote(String orderKey, OmsRequestDtos.Note req, Actor actor);

    /** entityName: oms | order_refund | wms (rỗng = mọi loại); entityId: id hoặc mã đơn. */
    Map<String, Object> auditLogs(String entityName, String entityId, int page, int take);

    Map<String, Object> quote(OmsRequestDtos.OrderUpsert req);

    OrderResponseDto create(OmsRequestDtos.OrderUpsert req, Actor actor);

    OrderResponseDto update(String orderKey, OmsRequestDtos.OrderUpsert req, Actor actor);

    OrderResponseDto duplicate(OmsRequestDtos.Duplicate req, Actor actor);

    OrderResponseDto confirmPayment(String orderKey, String note, Actor actor);

    OrderResponseDto updateStatus(String orderKey, AdminOrderStatusRequestDto req, Actor actor);

    OrderResponseDto cancel(String orderKey, String reason, Actor actor);

    OrderResponseDto returnLines(String orderKey, AdminOrderReturnRequestDto req, Actor actor);

    OrderResponseDto refund(String orderKey, BigDecimal amount, String reason, Actor actor);

    /** Ghi một dòng audit (dùng cả bởi OrderReturnService). */
    void audit(Long orderId, String entityName, String action, String actionType, String message, String note,
               Actor actor, List<Map<String, Object>> changes);
}
