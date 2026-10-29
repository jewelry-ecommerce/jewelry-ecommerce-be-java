package com.pk.core.test.business;

import com.pk.core.business.repository.CustomerRequestRepo;
import com.pk.core.business.repository.OrderRepo;
import com.pk.core.business.repository.ProductRepo;
import com.pk.core.business.repository.ProductSkuRepo;
import com.pk.core.business.service.impl.CustomerRequestServiceImpl;
import com.pk.core.common.exception.BusinessException;
import com.pk.core.common.exception.ResourceNotFoundException;
import com.pk.core.model.dto.request.BackInStockRequestDto;
import com.pk.core.model.dto.request.CustomerRequestBulkStatusRequestDto;
import com.pk.core.model.dto.request.NewsletterRequestDto;
import com.pk.core.model.dto.request.OrderSupportRequestDto;
import com.pk.core.model.dto.response.CustomerRequestPageResponseDto;
import com.pk.core.model.dto.response.MessageResponseDto;
import com.pk.core.model.entity.CustomerRequestEntity;
import com.pk.core.model.entity.OrderEntity;
import com.pk.core.model.entity.ProductEntity;
import com.pk.core.model.entity.ProductSkuEntity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Sort;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Date;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CustomerRequestServiceTest {

    private static final String PHONE = "0901234567";
    private static final ZoneId VN = ZoneId.of("Asia/Ho_Chi_Minh");

    @Mock CustomerRequestRepo requests;
    @Mock ProductSkuRepo productSkus;
    @Mock ProductRepo products;
    @Mock OrderRepo orders;

    private CustomerRequestServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new CustomerRequestServiceImpl(requests, productSkus, products, orders);
    }

    private static CustomerRequestEntity request(long id, String type, String contact, String status, Date created) {
        CustomerRequestEntity r = new CustomerRequestEntity(type, CustomerRequestEntity.PHONE, contact);
        r.setId(id);
        r.setStatus(status);
        r.setCreatedDate(created);
        return r;
    }

    // ------------------------------------------------------------------ gửi yêu cầu (công khai)

    @Test
    void backInStockStoresNormalizedPhoneAndProductSnapshot() {
        ProductSkuEntity sku = new ProductSkuEntity("SKU-1", "M", BigDecimal.valueOf(1_000_000), 0);
        sku.setId(5L);
        sku.setProductId(100L);
        sku.setMetalColorLabel("Vàng");
        ProductEntity product = new ProductEntity(1L, "P-100", "Nhẫn vàng", "nhan-vang");
        product.setThumbnailUrl("https://img/x.jpg");
        when(productSkus.findBySkuCode("SKU-1")).thenReturn(sku);
        when(products.findOne(100L)).thenReturn(product);
        when(requests.findOpen(CustomerRequestEntity.BACK_IN_STOCK, PHONE, 5L, "")).thenReturn(null);

        MessageResponseDto res = service.createBackInStock(new BackInStockRequestDto("+84901234567", " SKU-1 "));

        assertTrue(res.isSuccess());
        ArgumentCaptor<CustomerRequestEntity> saved = ArgumentCaptor.forClass(CustomerRequestEntity.class);
        verify(requests).create(saved.capture());
        CustomerRequestEntity r = saved.getValue();
        assertEquals(CustomerRequestEntity.BACK_IN_STOCK, r.getType());
        assertEquals(CustomerRequestEntity.PHONE, r.getContactChannel());
        assertEquals(PHONE, r.getContactValue());
        assertEquals(CustomerRequestEntity.NEW, r.getStatus());
        assertEquals(5L, r.getSkuId());
        assertEquals("Nhẫn vàng", r.getProductNameSnapshot());
        assertEquals("https://img/x.jpg", r.getProductImageUrlSnapshot());
        assertEquals("M / Vàng", r.getVariantTextSnapshot());
    }

    @Test
    void backInStockDuplicateDoesNotCreateAnotherRow() {
        ProductSkuEntity sku = new ProductSkuEntity("SKU-1", "M", BigDecimal.TEN, 0);
        sku.setId(5L);
        when(productSkus.findBySkuCode("SKU-1")).thenReturn(sku);
        when(requests.findOpen(CustomerRequestEntity.BACK_IN_STOCK, PHONE, 5L, ""))
                .thenReturn(request(1L, CustomerRequestEntity.BACK_IN_STOCK, PHONE, "NEW", new Date()));

        MessageResponseDto res = service.createBackInStock(new BackInStockRequestDto(PHONE, "SKU-1"));

        assertTrue(res.isSuccess());
        verify(requests, never()).create(any(CustomerRequestEntity.class));
    }

    @Test
    void backInStockUnknownSkuIs404() {
        when(productSkus.findBySkuCode("NOPE")).thenReturn(null);

        assertThrows(ResourceNotFoundException.class,
                () -> service.createBackInStock(new BackInStockRequestDto(PHONE, "NOPE")));
        verify(requests, never()).create(any(CustomerRequestEntity.class));
    }

    @Test
    void orderSupportLinksOrderWhenFound() {
        OrderEntity order = new OrderEntity();
        order.setId(77L);
        when(orders.findByCode("DH1")).thenReturn(order);
        when(requests.findOpen(CustomerRequestEntity.ORDER_SUPPORT, PHONE, 0L, "DH1")).thenReturn(null);

        service.createOrderSupport(new OrderSupportRequestDto(PHONE, "DH1"));

        ArgumentCaptor<CustomerRequestEntity> saved = ArgumentCaptor.forClass(CustomerRequestEntity.class);
        verify(requests).create(saved.capture());
        assertEquals(77L, saved.getValue().getOrderId());
        assertEquals("DH1", saved.getValue().getOrderCode());
        assertEquals(CustomerRequestEntity.ORDER, saved.getValue().getOrderType());
    }

    @Test
    void orderSupportUnknownOrderIsStillAcceptedWithoutLeakingExistence() {
        when(orders.findByCode("KHONGCO")).thenReturn(null);
        when(requests.findOpen(CustomerRequestEntity.ORDER_SUPPORT, PHONE, 0L, "KHONGCO")).thenReturn(null);

        MessageResponseDto res = service.createOrderSupport(new OrderSupportRequestDto(PHONE, "KHONGCO"));

        assertTrue(res.isSuccess());
        ArgumentCaptor<CustomerRequestEntity> saved = ArgumentCaptor.forClass(CustomerRequestEntity.class);
        verify(requests).create(saved.capture());
        assertNull(saved.getValue().getOrderId());
        assertEquals("KHONGCO", saved.getValue().getOrderCode());
    }

    @Test
    void newsletterLowercasesEmailAndSkipsDuplicates() {
        when(requests.findOpen(CustomerRequestEntity.NEWSLETTER, "a@b.com", 0L, "")).thenReturn(null);

        service.subscribeNewsletter(new NewsletterRequestDto(" A@B.com "));

        ArgumentCaptor<CustomerRequestEntity> saved = ArgumentCaptor.forClass(CustomerRequestEntity.class);
        verify(requests).create(saved.capture());
        assertEquals("a@b.com", saved.getValue().getContactValue());
        assertEquals(CustomerRequestEntity.EMAIL, saved.getValue().getContactChannel());
    }

    @Test
    void newsletterDuplicateIsNotCreatedAgain() {
        when(requests.findOpen(CustomerRequestEntity.NEWSLETTER, "a@b.com", 0L, ""))
                .thenReturn(request(1L, CustomerRequestEntity.NEWSLETTER, "a@b.com", "NEW", new Date()));

        MessageResponseDto ack = service.subscribeNewsletter(new NewsletterRequestDto("a@b.com"));
        assertTrue(ack.isSuccess());
        // Chỉ lưu yêu cầu, chưa có bộ gửi thật: không được báo là đã gửi thông báo.
        assertTrue(((com.pk.core.model.dto.response.CustomerRequestAckResponseDto) ack).isRecorded());
        assertFalse(((com.pk.core.model.dto.response.CustomerRequestAckResponseDto) ack).isNotificationSent());
        verify(requests, never()).create(any(CustomerRequestEntity.class));
    }

    // ------------------------------------------------------------------ admin: danh sách / trạng thái

    private void stubList() {
        Date now = new Date();
        Date old = Date.from(Instant.now().minusSeconds(40L * 24 * 3600));
        CustomerRequestEntity a = request(1L, CustomerRequestEntity.BACK_IN_STOCK, "0911111111", "NEW", now);
        a.setSkuCode("SKU-AAA");
        a.setProductNameSnapshot("Nhẫn Kim Cương");
        CustomerRequestEntity b = request(2L, CustomerRequestEntity.BACK_IN_STOCK, "0922222222", "COMPLETED", old);
        CustomerRequestEntity c = request(3L, CustomerRequestEntity.NEWSLETTER, "x@y.com", "NEW", now);
        when(requests.findAll(any(Sort.class))).thenReturn(List.of(a, b, c));
    }

    @Test
    void listFiltersByTypeAndPutsNewestFirst() {
        stubList();

        CustomerRequestPageResponseDto page = service.list("BACK_IN_STOCK", 1, 20, null, null, null, null, null, null);

        assertEquals(2, page.getTotal());
        assertEquals("1", page.getItems().get(0).getId());
        assertEquals("2", page.getItems().get(1).getId());
        assertEquals(1, page.getPage());
    }

    @Test
    void listFiltersByStatusKeywordAndTime() {
        stubList();

        assertEquals(1, service.list("BACK_IN_STOCK", 1, 20, null, "completed", null, null, null, null).getTotal());
        assertEquals(1, service.list("BACK_IN_STOCK", 1, 20, "kim cương", null, null, null, null, null).getTotal());
        assertEquals(1, service.list("BACK_IN_STOCK", 1, 20, "sku-aaa", null, null, null, null, null).getTotal());
        assertEquals(1, service.list("BACK_IN_STOCK", 1, 20, null, null, "Last7Days", null, null, null).getTotal());
        assertEquals(1, service.list("BACK_IN_STOCK", 1, 20, null, null, "Custom", Instant.now().minusSeconds(3600).toString(), null, null).getTotal());
        assertEquals(0, service.list("NEWSLETTER", 1, 20, null, "COMPLETED", null, null, null, null).getTotal());
    }

    @Test
    void listWithUnknownFilterValuesIs400() {
        assertThrows(BusinessException.class, () -> service.list("BACK_IN_STOCK", 1, 20, null, "WHATEVER", null, null, null, null));
        assertThrows(BusinessException.class, () -> service.list("BACK_IN_STOCK", 1, 20, null, null, null, null, null, "X"));
        assertThrows(BusinessException.class, () -> service.list("BACK_IN_STOCK", 1, 20, null, null, "Forever", null, null, null));
        assertThrows(BusinessException.class, () -> service.list("BACK_IN_STOCK", 1, 20, null, null, "Custom", "không phải ngày", null, null));
    }

    @Test
    void updateStatusAndNotFound() {
        CustomerRequestEntity r = request(1L, CustomerRequestEntity.NEWSLETTER, "a@b.com", "NEW", new Date());
        when(requests.findOne(1L)).thenReturn(r);
        when(requests.findOne(9L)).thenReturn(null);

        service.updateStatus(1L, "CONTACTED");

        assertEquals("CONTACTED", r.getStatus());
        verify(requests).update(r);
        assertThrows(ResourceNotFoundException.class, () -> service.updateStatus(9L, "CONTACTED"));
        assertThrows(ResourceNotFoundException.class, () -> service.findById(9L));
    }

    @Test
    void bulkUpdateOnlyTouchesExistingRowsOfTheSameType() {
        CustomerRequestEntity ok = request(1L, CustomerRequestEntity.NEWSLETTER, "a@b.com", "NEW", new Date());
        CustomerRequestEntity wrongType = request(2L, CustomerRequestEntity.BACK_IN_STOCK, PHONE, "NEW", new Date());
        when(requests.findOne(1L)).thenReturn(ok);
        when(requests.findOne(2L)).thenReturn(wrongType);
        when(requests.findOne(3L)).thenReturn(null);

        MessageResponseDto res = service.bulkUpdateStatus(new CustomerRequestBulkStatusRequestDto(
                "COMPLETED", List.of("1", "2", "x", "3"), CustomerRequestEntity.NEWSLETTER));

        assertEquals("COMPLETED", ok.getStatus());
        assertEquals("NEW", wrongType.getStatus());
        assertTrue(res.getMessage().contains("1"));
        verify(requests).update(ok);
        verify(requests, never()).update(wrongType);
    }

    // ------------------------------------------------------------------ resolveRange

    @Test
    void resolveRangeCalendarFilters() {
        ZonedDateTime now = ZonedDateTime.of(2026, 10, 6, 15, 30, 0, 0, VN);

        Date[] today = CustomerRequestServiceImpl.resolveRange("Today", null, null, now);
        assertEquals(ZonedDateTime.of(2026, 10, 6, 0, 0, 0, 0, VN).toInstant(), today[0].toInstant());
        assertEquals(ZonedDateTime.of(2026, 10, 7, 0, 0, 0, 0, VN).toInstant(), today[1].toInstant());

        Date[] last7 = CustomerRequestServiceImpl.resolveRange("Last7Days", null, null, now);
        assertEquals(ZonedDateTime.of(2026, 9, 30, 0, 0, 0, 0, VN).toInstant(), last7[0].toInstant());

        Date[] thisMonth = CustomerRequestServiceImpl.resolveRange("ThisMonth", null, null, now);
        assertEquals(ZonedDateTime.of(2026, 10, 1, 0, 0, 0, 0, VN).toInstant(), thisMonth[0].toInstant());
        assertEquals(ZonedDateTime.of(2026, 11, 1, 0, 0, 0, 0, VN).toInstant(), thisMonth[1].toInstant());

        Date[] lastMonth = CustomerRequestServiceImpl.resolveRange("LastMonth", null, null, now);
        assertEquals(ZonedDateTime.of(2026, 9, 1, 0, 0, 0, 0, VN).toInstant(), lastMonth[0].toInstant());
        assertEquals(ZonedDateTime.of(2026, 10, 1, 0, 0, 0, 0, VN).toInstant(), lastMonth[1].toInstant());
    }

    @Test
    void resolveRangeNoFilterAndCustom() {
        ZonedDateTime now = ZonedDateTime.of(2026, 10, 6, 15, 30, 0, 0, VN);

        Date[] none = CustomerRequestServiceImpl.resolveRange(null, null, null, now);
        assertNull(none[0]);
        assertNull(none[1]);

        Date[] custom = CustomerRequestServiceImpl.resolveRange("Custom", "2026-10-01T00:00:00Z", null, now);
        assertEquals(Instant.parse("2026-10-01T00:00:00Z"), custom[0].toInstant());
        assertNull(custom[1]);
    }
}
