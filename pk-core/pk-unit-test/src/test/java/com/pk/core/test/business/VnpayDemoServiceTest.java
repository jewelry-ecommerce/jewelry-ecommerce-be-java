package com.pk.core.test.business;

import com.pk.core.business.payment.VnpayDemoService;
import com.pk.core.business.payment.VnpayProperties;
import com.pk.core.business.payment.VnpaySignature;
import com.pk.core.business.repository.OrderRepo;
import com.pk.core.business.repository.OrderTimelineRepo;
import com.pk.core.business.repository.PaymentRepo;
import com.pk.core.common.exception.BusinessException;
import com.pk.core.common.exception.ErrorCode;
import com.pk.core.model.dto.request.VnpayDemoConfirmRequestDto;
import com.pk.core.model.dto.response.VnpayReturnDto;
import com.pk.core.model.entity.OrderEntity;
import com.pk.core.model.entity.PaymentEntity;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class VnpayDemoServiceTest {
    @Mock PaymentRepo payments;
    @Mock OrderRepo orders;
    @Mock OrderTimelineRepo timelines;

    @Test
    void confirmMarksPaid() {
        VnpayProperties settings = demo();
        OrderEntity order = order();
        PaymentEntity payment = payment();
        when(payments.findByGatewayOrderId("DH19")).thenReturn(payment);
        when(orders.findByIdForUpdate(1L)).thenReturn(order);
        when(payments.findOne(9L)).thenReturn(payment);
        VnpayReturnDto result = service(settings).confirm(request(true, signature(settings)));
        assertTrue(result.success());
        assertEquals(OrderEntity.PAID, order.getPaymentStatus());
        assertEquals(PaymentEntity.SUCCESS, payment.getStatus());
        assertEquals("DEMO", payment.getTransactionId());
        verify(payments).update(payment);
        verify(orders).update(order);
        verify(timelines).create(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void badSignatureIsRejected() {
        VnpayProperties settings = demo();
        BusinessException error = assertThrows(BusinessException.class,
                () -> service(settings).confirm(request(true, "ab")));
        assertEquals(ErrorCode.PAYMENT_SIGNATURE_INVALID, error.getCode());
        verifyNoInteractions(payments, orders, timelines);
    }

    @Test
    void confirmRejectedWhenDemoModeOff() {
        VnpayProperties settings = demo();
        settings.setDemoMode(false);
        BusinessException error = assertThrows(BusinessException.class,
                () -> service(settings).confirm(request(true, signature(settings))));
        assertEquals(ErrorCode.VNPAY_UNAVAILABLE, error.getCode());
        verify(payments, never()).update(org.mockito.ArgumentMatchers.any());
    }

    private VnpayDemoService service(VnpayProperties settings) {
        return new VnpayDemoService(settings, payments, orders, timelines);
    }

    private static VnpayDemoConfirmRequestDto request(boolean success, String sig) {
        return new VnpayDemoConfirmRequestDto("DH1", "DH19", new BigDecimal("25000"), success, sig);
    }

    private static String signature(VnpayProperties settings) {
        return VnpaySignature.signText(settings.getHashSecret(), "DH1|DH19|25000");
    }

    private static VnpayProperties demo() {
        VnpayProperties settings = new VnpayProperties();
        settings.setEnabled(true);
        settings.setDemoMode(true);
        settings.setHashSecret("jewelry-vnpay-demo-only");
        settings.setClientBaseUrl("http://localhost:3100");
        return settings;
    }

    private static OrderEntity order() {
        OrderEntity order = new OrderEntity();
        order.setId(1L);
        order.setCode("DH1");
        order.setStatus(OrderEntity.PENDING);
        order.setPaymentStatus(OrderEntity.UNPAID);
        order.setPaymentMethod("VNPAY");
        order.setGrandTotal(new BigDecimal("25000"));
        return order;
    }

    private static PaymentEntity payment() {
        PaymentEntity payment = new PaymentEntity(1L, "VNPAY", new BigDecimal("25000"));
        payment.setId(9L);
        payment.setGatewayOrderId("DH19");
        payment.setStatus(PaymentEntity.PENDING);
        return payment;
    }
}
