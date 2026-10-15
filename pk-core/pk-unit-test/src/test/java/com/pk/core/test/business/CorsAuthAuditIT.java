package com.pk.core.test.business;

import com.pk.core.model.constant.UrlConstant;
import com.pk.core.model.constant.admin.UrlAdminConstant;
import com.pk.core.test.common.IntegrationTestBase;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Audit CORS theo route FE thật: origin FE được phép + header FE gửi (Idempotency-Key, guest cart); origin lạ bị từ chối. */
class CorsAuthAuditIT extends IntegrationTestBase {

    private static final String V1 = UrlConstant.Common.API + UrlConstant.Common.VERSION;

    private int preflight(String origin, String method, String headers, String path) throws Exception {
        return mvc.perform(options(V1 + path).header("Origin", origin).header("Access-Control-Request-Method", method)
                .header("Access-Control-Request-Headers", headers)).andReturn().getResponse().getStatus();
    }

    @Test
    void feOriginsAndHeadersAreAllowed() throws Exception {
        for (String origin : new String[] {"http://localhost:6100", "http://localhost:3100"}) {
            assertEquals(200, preflight(origin, "POST", "content-type,idempotency-key,x-guest-cart-id", UrlConstant.Order.GUEST), origin);
            assertEquals(200, preflight(origin, "POST", "authorization,content-type,idempotency-key", UrlConstant.Order.BASE), origin);
            assertEquals(200, preflight(origin, "PATCH", "authorization,content-type", UrlAdminConstant.Order.BASE + "/X/status"), origin);
            assertEquals(200, preflight(origin, "GET", "authorization,x-guest-id", UrlConstant.Cart.BASE), origin);
        }
    }

    @Test
    void unknownOriginAndUnknownHeaderAreRejected() throws Exception {
        assertEquals(403, preflight("https://evil.example.com", "POST", "content-type", UrlConstant.Order.GUEST));
        // Header lạ không nằm trong Access-Control-Allow-Headers (Spring chỉ trả các header được phép) -> trình duyệt sẽ chặn request thật.
        String allowed = mvc.perform(options(V1 + UrlConstant.Order.GUEST).header("Origin", "http://localhost:6100")
                        .header("Access-Control-Request-Method", "POST")
                        .header("Access-Control-Request-Headers", "content-type,x-evil-header"))
                .andReturn().getResponse().getHeader("Access-Control-Allow-Headers");
        org.junit.jupiter.api.Assertions.assertFalse(allowed != null && allowed.toLowerCase().contains("x-evil-header"), allowed);
        // FE gửi x-guest-order-access-token / x-pre-order-access-token (token của hệ khác): BE Java không phát hành nên không cho phép.
        assertEquals(403, preflight("http://localhost:6100", "POST", "x-guest-order-access-token", UrlConstant.Order.GUEST));
    }
}
