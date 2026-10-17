package com.pk.core.business.service;

import com.pk.core.model.dto.request.OmsRequestDtos;

import java.util.Map;

/**
 * Yêu cầu đổi/trả hàng của admin (FE order-returns). Luồng: Pending -> (approve) Completed | (reject) Rejected |
 * (update status=Cancelled) Cancelled. Duyệt sẽ: trả hàng về kho + cập nhật đơn gốc, hoàn tiền (Refund) hoặc tạo đơn
 * đổi hàng (Exchange) có trừ giá trị hàng đã trả. Chưa có vận chuyển/kiểm hàng (AwaitingPickup, Inspection...).
 */
public interface OrderReturnService {

    /** Báo giá: kiểm tra yêu cầu + tính tiền, không ghi dữ liệu. */
    Map<String, Object> quote(OmsRequestDtos.OrderReturn req);

    /** Tạo yêu cầu từ báo giá đã duyệt (fingerprint phải còn khớp). Trả {success, orderReturnCode, shipmentQueued}. */
    Map<String, Object> commit(OmsRequestDtos.OrderReturn req, OrderOmsService.Actor actor);

    /** Tạo yêu cầu không cần báo giá; trả chi tiết yêu cầu. */
    Map<String, Object> create(OmsRequestDtos.OrderReturn req, OrderOmsService.Actor actor);

    Map<String, Object> list(Map<String, Object> filter);

    Map<String, Object> detail(String idOrCode);

    Map<String, Object> update(String idOrCode, OmsRequestDtos.OrderReturn req, OrderOmsService.Actor actor);

    Map<String, Object> approve(String idOrCode, String note, OrderOmsService.Actor actor);

    Map<String, Object> reject(String idOrCode, String note, OrderOmsService.Actor actor);
}
