package com.pk.core.business.service;

import com.pk.core.business.shipping.GhnClient;
import com.pk.core.business.shipping.GhnProperties;
import com.pk.core.model.entity.OrderEntity;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.Map;

/** Tính phí + tạo vận đơn GHN (staging) cho đồ án. */
@Service
@RequiredArgsConstructor
public class GhnShippingService {

    private final GhnClient ghnClient;
    private final GhnProperties properties;

    public Map<String, Object> quoteFee(Integer toDistrictId, String toWardCode, Integer weightGrams) {
        int district = toDistrictId == null ? 0 : toDistrictId;
        String ward = toWardCode == null ? "" : toWardCode.trim();
        int weight = weightGrams == null ? properties.getDefaultWeightGrams() : weightGrams;
        long fee = district <= 0 || ward.isEmpty() ? 0L : ghnClient.calculateFee(district, ward, weight);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("shippingFee", fee);
        out.put("currency", "VND");
        out.put("provider", "GHN");
        out.put("staging", properties.getBaseUrl() != null && properties.getBaseUrl().contains("dev-online-gateway"));
        out.put("configured", properties.ready());
        return out;
    }

    /** Tạo vận đơn khi admin chuyển đơn sang SHIPPING; gắn trackingCode. */
    public Map<String, Object> createShipmentForOrder(OrderEntity order) {
        String name = order.getShipRecipientName() == null ? "Khach" : order.getShipRecipientName();
        String phone = order.getShipPhone() == null ? "0900000000" : order.getShipPhone();
        String address = buildAddress(order);
        int districtId = parseDistrict(order.getShipDistrict());
        String wardCode = order.getShipWard() == null ? properties.getFromWardCode() : order.getShipWard();
        long cod = "COD".equalsIgnoreCase(order.getPaymentMethod()) && order.getGrandTotal() != null
                ? order.getGrandTotal().longValue() : 0L;
        Map<String, Object> created = ghnClient.createShippingOrder(order.getCode(), name, phone, address, districtId, wardCode, cod);
        Object code = created.get("order_code");
        if (code != null && !String.valueOf(code).isBlank()) {
            order.setTrackingCode(String.valueOf(code));
        }
        return created;
    }

    private static String buildAddress(OrderEntity order) {
        String line = order.getShipAddressLine() == null ? "" : order.getShipAddressLine();
        String ward = order.getShipWard() == null ? "" : order.getShipWard();
        String district = order.getShipDistrict() == null ? "" : order.getShipDistrict();
        String province = order.getShipProvince() == null ? "" : order.getShipProvince();
        return String.join(", ", line, ward, district, province).replaceAll("(, )+", ", ").replaceAll("^, |, $", "");
    }

    private static int parseDistrict(String raw) {
        if (raw == null || raw.isBlank()) {
            return 1442;
        }
        try {
            return Integer.parseInt(raw.trim());
        } catch (NumberFormatException ignored) {
            return 1442;
        }
    }
}
