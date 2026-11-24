package com.pk.core.business.shipping;

import com.fasterxml.jackson.databind.JsonNode;
import com.pk.core.common.exception.BusinessException;
import com.pk.core.common.exception.ErrorCode;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Client GHN staging/production. Khi chưa cấu hình token → mock fee 0 / mã vận đơn giả (đủ demo offline).
 */
@Component
public class GhnClient {

    private final GhnProperties properties;
    private final RestClient ghnRestClient;

    public GhnClient(GhnProperties properties, @Qualifier("ghnRestClient") RestClient ghnRestClient) {
        this.properties = properties;
        this.ghnRestClient = ghnRestClient;
    }

    public long calculateFee(int toDistrictId, String toWardCode, int weightGrams) {
        if (!properties.ready()) {
            return 0L;
        }
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("from_district_id", properties.getFromDistrictId());
        body.put("from_ward_code", properties.getFromWardCode());
        body.put("service_type_id", properties.getServiceTypeId());
        body.put("to_district_id", toDistrictId);
        body.put("to_ward_code", toWardCode);
        body.put("weight", weightGrams > 0 ? weightGrams : properties.getDefaultWeightGrams());
        body.put("length", properties.getDefaultLengthCm());
        body.put("width", properties.getDefaultWidthCm());
        body.put("height", properties.getDefaultHeightCm());
        JsonNode data = post("/shiip/public-api/v2/shipping-order/fee", body);
        return data.path("total").asLong(data.path("service_fee").asLong(0));
    }

    public Map<String, Object> createShippingOrder(String clientOrderCode, String toName, String toPhone,
                                                   String toAddress, int toDistrictId, String toWardCode,
                                                   long codAmount) {
        if (!properties.ready()) {
            Map<String, Object> mock = new LinkedHashMap<>();
            mock.put("order_code", "MOCK-" + clientOrderCode);
            mock.put("total_fee", 0);
            mock.put("mock", true);
            return mock;
        }
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("payment_type_id", 1);
        body.put("required_note", "CHOXEMHANGKHONGTHU");
        body.put("client_order_code", clientOrderCode);
        body.put("to_name", toName);
        body.put("to_phone", toPhone);
        body.put("to_address", toAddress);
        body.put("to_ward_code", toWardCode);
        body.put("to_district_id", toDistrictId);
        body.put("cod_amount", Math.max(codAmount, 0));
        body.put("weight", properties.getDefaultWeightGrams());
        body.put("length", properties.getDefaultLengthCm());
        body.put("width", properties.getDefaultWidthCm());
        body.put("height", properties.getDefaultHeightCm());
        body.put("service_type_id", properties.getServiceTypeId());
        JsonNode data = post("/shiip/public-api/v2/shipping-order/create", body);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("order_code", data.path("order_code").asText());
        out.put("total_fee", data.path("total_fee").asLong(0));
        out.put("expected_delivery_time", data.path("expected_delivery_time").asText(null));
        out.put("mock", false);
        return out;
    }

    private JsonNode post(String path, Map<String, Object> body) {
        try {
            JsonNode root = ghnRestClient.post()
                    .uri(path)
                    .contentType(MediaType.APPLICATION_JSON)
                    .header("Token", properties.getToken())
                    .header("ShopId", String.valueOf(properties.getShopId()))
                    .body(body)
                    .retrieve()
                    .body(JsonNode.class);
            if (root == null || root.path("code").asInt(-1) != 200) {
                String message = root == null ? "GHN không phản hồi" : root.path("message").asText("GHN lỗi");
                throw BusinessException.badRequest(ErrorCode.VALIDATION_FAILED, message);
            }
            return root.path("data");
        } catch (RestClientException ex) {
            throw BusinessException.badRequest(ErrorCode.VALIDATION_FAILED, "Không gọi được GHN: " + ex.getMessage());
        }
    }
}
