package com.pk.core.business.service.impl;

import lombok.RequiredArgsConstructor;

import com.pk.core.business.repository.CustomerRequestRepo;
import com.pk.core.business.repository.OrderRepo;
import com.pk.core.business.repository.ProductRepo;
import com.pk.core.business.repository.ProductSkuRepo;
import com.pk.core.business.service.CustomerRequestService;
import com.pk.core.common.exception.BusinessException;
import com.pk.core.common.exception.ErrorCode;
import com.pk.core.common.exception.ResourceNotFoundException;
import com.pk.core.common.util.PhoneUtil;
import com.pk.core.common.web.PageResponse;
import com.pk.core.model.dto.request.BackInStockRequestDto;
import com.pk.core.model.dto.request.CustomerRequestBulkStatusRequestDto;
import com.pk.core.model.dto.request.NewsletterRequestDto;
import com.pk.core.model.dto.request.OrderSupportRequestDto;
import com.pk.core.model.dto.response.CustomerRequestAckResponseDto;
import com.pk.core.model.dto.response.CustomerRequestPageResponseDto;
import com.pk.core.model.dto.response.CustomerRequestResponseDto;
import com.pk.core.model.dto.response.MessageResponseDto;
import com.pk.core.model.entity.CustomerRequestEntity;
import com.pk.core.model.entity.OrderEntity;
import com.pk.core.model.entity.ProductEntity;
import com.pk.core.model.entity.ProductSkuEntity;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class CustomerRequestServiceImpl implements CustomerRequestService {

    private static final Set<String> STATUSES = Set.of("NEW", "UNPROCESSED", "CONTACTED", "IN_PROGRESS", "COMPLETED");
    private static final Set<String> ORDER_TYPES = Set.of("ORDER", "PRE_ORDER");

    private final CustomerRequestRepo requests;
    private final ProductSkuRepo productSkus;
    private final ProductRepo products;
    private final OrderRepo orders;

    @Transactional
    @Override
    public MessageResponseDto createBackInStock(BackInStockRequestDto req) {
        String phone = canonicalPhone(req.getPhone());
        String skuCode = req.getSkuCode().trim();
        ProductSkuEntity sku = productSkus.findBySkuCode(skuCode);
        if (sku == null) {
            throw new ResourceNotFoundException("Sản phẩm", "ProductSku", skuCode);
        }
        if (requests.findOpen(CustomerRequestEntity.BACK_IN_STOCK, phone, sku.getId(), "") == null) {
            CustomerRequestEntity r = new CustomerRequestEntity(CustomerRequestEntity.BACK_IN_STOCK,
                    CustomerRequestEntity.PHONE, phone);
            ProductEntity product = products.findOne(sku.getProductId());
            r.setProductId(sku.getProductId());
            r.setSkuId(sku.getId());
            r.setSkuCode(sku.getSkuCode());
            r.setProductNameSnapshot(product != null ? product.getName() : sku.getName());
            r.setProductImageUrlSnapshot(product != null ? product.getThumbnailUrl() : null);
            r.setVariantTextSnapshot(variantText(sku));
            requests.create(r);
        }
        return new CustomerRequestAckResponseDto("Đã ghi nhận yêu cầu. Cửa hàng sẽ liên hệ với bạn khi sản phẩm có hàng trở lại");
    }

    @Transactional
    @Override
    public MessageResponseDto createOrderSupport(OrderSupportRequestDto req) {
        String phone = canonicalPhone(req.getPhone());
        String orderCode = req.getOrderCode().trim();
        if (requests.findOpen(CustomerRequestEntity.ORDER_SUPPORT, phone, 0L, orderCode) == null) {
            CustomerRequestEntity r = new CustomerRequestEntity(CustomerRequestEntity.ORDER_SUPPORT,
                    CustomerRequestEntity.PHONE, phone);
            OrderEntity order = orders.findByCode(orderCode);
            r.setOrderId(order != null ? order.getId() : null);
            r.setOrderCode(orderCode);
            r.setOrderType(CustomerRequestEntity.ORDER);
            requests.create(r);
        }
        return new CustomerRequestAckResponseDto("Đã ghi nhận yêu cầu hỗ trợ. Cửa hàng sẽ liên hệ với bạn sớm");
    }

    @Transactional
    @Override
    public MessageResponseDto subscribeNewsletter(NewsletterRequestDto req) {
        String email = req.getEmail().trim().toLowerCase(Locale.ROOT);
        if (requests.findOpen(CustomerRequestEntity.NEWSLETTER, email, 0L, "") == null) {
            requests.create(new CustomerRequestEntity(CustomerRequestEntity.NEWSLETTER,
                    CustomerRequestEntity.EMAIL, email));
        }
        return new CustomerRequestAckResponseDto("Đã lưu email đăng ký nhận tin của bạn");
    }

    @Transactional(readOnly = true)
    @Override
    public CustomerRequestPageResponseDto list(String type, int page, int take, String keyword, String status,
                                               String timeFilter, String timeFrom, String timeTo, String orderType) {
        String statusFilter = blank(status) ? null : upper(status);
        if (statusFilter != null && !STATUSES.contains(statusFilter)) {
            throw invalid("status không hợp lệ: " + status);
        }
        String orderTypeFilter = blank(orderType) ? null : upper(orderType);
        if (orderTypeFilter != null && !ORDER_TYPES.contains(orderTypeFilter)) {
            throw invalid("orderType không hợp lệ: " + orderType);
        }
        Date[] range = resolveRange(timeFilter, timeFrom, timeTo, ZonedDateTime.now());
        String kw = blank(keyword) ? null : keyword.trim().toLowerCase(Locale.ROOT);

        List<CustomerRequestEntity> matched = new ArrayList<>();
        for (CustomerRequestEntity r : requests.findAll(Sort.unsorted())) {
            if (!type.equals(r.getType())) {
                continue;
            }
            if (statusFilter != null && !statusFilter.equals(r.getStatus())) {
                continue;
            }
            if (orderTypeFilter != null && !orderTypeFilter.equals(r.getOrderType())) {
                continue;
            }
            if (range[0] != null && r.getCreatedDate().before(range[0])) {
                continue;
            }
            if (range[1] != null && !r.getCreatedDate().before(range[1])) {
                continue;
            }
            if (kw != null && !(contains(r.getContactValue(), kw) || contains(r.getSkuCode(), kw)
                    || contains(r.getProductNameSnapshot(), kw) || contains(r.getOrderCode(), kw))) {
                continue;
            }
            matched.add(r);
        }
        matched.sort(Comparator.comparing(CustomerRequestEntity::getCreatedDate)
                .thenComparing(CustomerRequestEntity::getId).reversed());

        PageResponse<CustomerRequestEntity> paged = PageResponse.paginate(matched, page, take);
        List<CustomerRequestResponseDto> items = paged.content().stream().map(CustomerRequestResponseDto::from).toList();
        return new CustomerRequestPageResponseDto(items, paged.totalElements(), paged.page(), paged.size());
    }

    @Transactional(readOnly = true)
    @Override
    public CustomerRequestResponseDto findById(Long id) {
        return CustomerRequestResponseDto.from(findOrThrow(id));
    }

    @Transactional
    @Override
    public MessageResponseDto updateStatus(Long id, String status) {
        CustomerRequestEntity r = findOrThrow(id);
        r.setStatus(status);
        r.touch();
        requests.update(r);
        return MessageResponseDto.ok("Đã cập nhật trạng thái yêu cầu");
    }

    @Transactional
    @Override
    public MessageResponseDto bulkUpdateStatus(CustomerRequestBulkStatusRequestDto req) {
        int updated = 0;
        for (String raw : req.getIds()) {
            CustomerRequestEntity r;
            try {
                r = requests.findOne(Long.valueOf(raw));
            } catch (NumberFormatException ex) {
                continue;
            }
            if (r == null || !req.getType().equals(r.getType())) {
                continue;
            }
            r.setStatus(req.getStatus());
            r.touch();
            requests.update(r);
            updated++;
        }
        return MessageResponseDto.ok("Đã cập nhật " + updated + " yêu cầu");
    }

    // ------------------------------------------------------------------ helpers

    /**
     * Khoảng thời gian [từ, đến) cho bộ lọc timeFilter; phần tử nào null = không giới hạn phía đó. Today/Yesterday/
     * Last7Days/Last30Days/Last90Days tính theo ngày lịch (7 ngày = hôm nay + 6 ngày trước), ThisMonth/LastMonth theo
     * tháng lịch, Custom dùng timeFrom/timeTo (ISO-8601, có hoặc không offset). Public static để test trực tiếp.
     */
    public static Date[] resolveRange(String timeFilter, String timeFrom, String timeTo, ZonedDateTime now) {
        if (timeFilter == null || timeFilter.isBlank()) {
            return new Date[] {null, null};
        }
        ZoneId zone = now.getZone();
        ZonedDateTime today = now.toLocalDate().atStartOfDay(zone);
        ZonedDateTime from;
        ZonedDateTime to;
        switch (timeFilter.trim()) {
            case "Today" -> {
                from = today;
                to = today.plusDays(1);
            }
            case "Yesterday" -> {
                from = today.minusDays(1);
                to = today;
            }
            case "Last7Days" -> {
                from = today.minusDays(6);
                to = today.plusDays(1);
            }
            case "Last30Days" -> {
                from = today.minusDays(29);
                to = today.plusDays(1);
            }
            case "Last90Days" -> {
                from = today.minusDays(89);
                to = today.plusDays(1);
            }
            case "ThisMonth" -> {
                from = today.withDayOfMonth(1);
                to = from.plusMonths(1);
            }
            case "LastMonth" -> {
                to = today.withDayOfMonth(1);
                from = to.minusMonths(1);
            }
            case "Custom" -> {
                Instant f = parseInstant(timeFrom, zone);
                Instant t = parseInstant(timeTo, zone);
                return new Date[] {f == null ? null : Date.from(f), t == null ? null : Date.from(t)};
            }
            default -> throw invalid("timeFilter không hợp lệ: " + timeFilter);
        }
        return new Date[] {Date.from(from.toInstant()), Date.from(to.toInstant())};
    }

    private static Instant parseInstant(String s, ZoneId zone) {
        if (s == null || s.isBlank()) {
            return null;
        }
        String v = s.trim();
        try {
            return OffsetDateTime.parse(v).toInstant();
        } catch (DateTimeParseException ignored) {
            // thử dạng không có offset
        }
        try {
            return LocalDateTime.parse(v).atZone(zone).toInstant();
        } catch (DateTimeParseException ex) {
            throw invalid("Thời gian không hợp lệ (cần ISO-8601): " + s);
        }
    }

    private static String canonicalPhone(String raw) {
        String phone = PhoneUtil.normalize(raw);
        if (phone == null || !PhoneUtil.isValid(phone)) {
            throw invalid("Số điện thoại không hợp lệ");
        }
        return phone;
    }

    private static String variantText(ProductSkuEntity sku) {
        List<String> parts = new ArrayList<>();
        for (String p : new String[] {sku.getSizeLabel(), sku.getMetalColorLabel(), sku.getGemstone(), sku.getMaterial()}) {
            if (p != null && !p.isBlank()) {
                parts.add(p.trim());
            }
        }
        return parts.isEmpty() ? null : String.join(" / ", parts);
    }

    private CustomerRequestEntity findOrThrow(Long id) {
        CustomerRequestEntity r = requests.findOne(id);
        if (r == null) {
            throw new ResourceNotFoundException("Yêu cầu khách hàng", "CustomerRequest", id);
        }
        return r;
    }

    private static BusinessException invalid(String message) {
        return BusinessException.badRequest(ErrorCode.VALIDATION_FAILED, message);
    }

    private static boolean contains(String value, String lowerKeyword) {
        return value != null && value.toLowerCase(Locale.ROOT).contains(lowerKeyword);
    }

    private static boolean blank(String s) {
        return s == null || s.isBlank();
    }

    private static String upper(String s) {
        return s.trim().toUpperCase(Locale.ROOT);
    }
}
