package com.pk.core.test.business;

import com.pk.core.business.payment.*;
import com.pk.core.model.entity.*;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import org.springframework.http.MediaType;
import java.math.BigDecimal;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

class MomoGatewayTest {
    @Test void signsRawFieldsInCanonicalOrderWithKnownHmacVector() {
        // Independently calculated HMAC-SHA256 of a=hello&b=world using key test-secret.
        assertEquals("c244af373aee74381b6e15140b0f8711d9c60dba205ea8d0ea045a5d94f2a503",
            MomoSignature.sign("test-secret", Map.of("b", "world", "a", "hello")));
    }
    @Test void createsSandboxRequestAndRejectsForeignPaymentUrl() {
        var settings = new MomoProperties(); settings.setEnabled(true); settings.setPartnerCode("PARTNER");
        settings.setAccessKey("access"); settings.setSecretKey("secret");
        settings.setRedirectUrl("https://client.test/thanh-toan-momo"); settings.setIpnUrl("https://api.test/ipn");
        var builder = RestClient.builder().baseUrl(MomoProperties.API_BASE);
        var server = MockRestServiceServer.bindTo(builder).build();
        var order = new OrderEntity(); order.setCode("DH-TEST");
        var payment = new PaymentEntity(1L, "MOMO", new BigDecimal("25000"));
        server.expect(requestTo(MomoProperties.API_BASE + "/v2/gateway/api/create"))
            .andExpect(jsonPath("$.requestType").value("captureWallet"))
            .andExpect(jsonPath("$.amount").value(25000)).andExpect(jsonPath("$.autoCapture").value(true))
            .andExpect(jsonPath("$.redirectUrl").value("https://client.test/thanh-toan-momo?orderCode=DH-TEST"))
            .andRespond(withSuccess("{\"resultCode\":0,\"orderId\":\"" + payment.getGatewayOrderId()
                + "\",\"requestId\":\"" + payment.getGatewayOrderId() + "\",\"payUrl\":\"https://test-payment.momo.vn/pay/1\"}", MediaType.APPLICATION_JSON));
        assertEquals("https://test-payment.momo.vn/pay/1", new MomoGateway(settings, builder.build()).create(order, payment));
        server.verify();
        assertThrows(RuntimeException.class, () -> MomoGateway.validatePayUrl("https://evil.test/pay"));
        assertThrows(RuntimeException.class, () -> MomoGateway.validatePayUrl("https://test-payment.momo.vn@evil.test/pay"));
    }
}
