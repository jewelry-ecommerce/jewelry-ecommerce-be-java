package com.pk.core.test.business;

import com.pk.core.business.shipping.GhnClient;
import com.pk.core.business.shipping.GhnProperties;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Khi chưa cấu hình GHN token — fee 0 và mã vận đơn mock (đủ demo offline). */
class GhnClientMockTest {

    @Test
    void unconfiguredClientReturnsMockShipmentAndZeroFee() {
        GhnProperties props = new GhnProperties();
        props.setEnabled(false);
        GhnClient client = new GhnClient(props, RestClient.create());

        assertEquals(0L, client.calculateFee(1442, "21211", 200));
        Map<String, Object> created = client.createShippingOrder("ORD-1", "A", "090", "1 Lê Lợi", 1442, "21211", 0);
        assertTrue(Boolean.TRUE.equals(created.get("mock")));
        assertEquals("MOCK-ORD-1", created.get("order_code"));
    }
}
