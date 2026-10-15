package com.pk.core.business.service.impl;

import java.util.Set;
import com.pk.core.model.entity.UserEntity;
import com.pk.core.model.dto.response.OrderCountsDto;
import com.pk.core.business.service.AdminOrderQuery;
import com.pk.core.business.repository.UserRepo;
import lombok.RequiredArgsConstructor;

import com.pk.core.business.repository.CartItemRepo;
import com.pk.core.business.repository.CartRepo;
import com.pk.core.business.repository.CustomerAddressRepo;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pk.core.business.repository.OrderIdempotencyKeyRepo;
import com.pk.core.business.repository.OrderItemRepo;
import com.pk.core.business.repository.OrderRepo;
import com.pk.core.business.repository.OrderTimelineRepo;
import com.pk.core.business.repository.PaymentRepo;
import com.pk.core.business.repository.ProductRepo;
import com.pk.core.business.repository.ProductSkuRepo;
import com.pk.core.business.service.OrderService;
import com.pk.core.business.service.PromotionPricingService;
import com.pk.core.business.service.PreOrderService;
import com.pk.core.business.service.VoucherService;
import com.pk.core.common.exception.BusinessException;
import com.pk.core.common.exception.ErrorCode;
import com.pk.core.common.exception.ResourceNotFoundException;
import com.pk.core.common.web.PageResponse;
import com.pk.core.model.dto.request.AdminOrderReturnRequestDto;
import com.pk.core.model.dto.request.AdminOrderStatusRequestDto;
import com.pk.core.model.dto.request.GuestOrderRequestDto;
import com.pk.core.model.dto.request.CreateOrderRequestDto;
import com.pk.core.model.dto.request.OrderItemRequestDto;
import com.pk.core.model.dto.response.OrderItemResponseDto;
import com.pk.core.model.dto.response.OrderResponseDto;
import com.pk.core.model.dto.response.OrderTimelineResponseDto;
import com.pk.core.model.entity.CartEntity;
import com.pk.core.model.entity.CustomerAddressEntity;
import com.pk.core.model.entity.OrderEntity;
import com.pk.core.model.entity.OrderIdempotencyKeyEntity;
import com.pk.core.model.entity.OrderItemEntity;
import com.pk.core.model.entity.OrderTimelineEntity;
import com.pk.core.model.entity.PaymentEntity;
import com.pk.core.model.entity.ProductEntity;
import com.pk.core.model.entity.ProductSkuEntity;
import com.pk.core.model.entity.VoucherEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class OrderServiceImpl implements OrderService {

    private final OrderRepo orders;
    private final UserRepo users;
    private final OrderItemRepo orderItems;
    private final OrderTimelineRepo orderTimelines;
    private final PaymentRepo payments;
    private final CustomerAddressRepo addresses;
    private final ProductRepo products;
    private final ProductSkuRepo productSkus;
    private final VoucherService voucherService;
    private final PromotionPricingService promotionPricing;
    private final PreOrderService preOrder;
    private final CartRepo carts;
    private final CartItemRepo cartItems;
    private final OrderIdempotencyKeyRepo idempotencyKeys;
    private final ObjectMapper objectMapper;

    /** Thời gian hiệu lực của Idempotency-Key. */
    static final long IDEMPOTENCY_TTL_MILLIS = 24L * 60 * 60 * 1000;
    private static final java.util.regex.Pattern IDEMPOTENCY_KEY_PATTERN = java.util.regex.Pattern.compile("[A-Za-z0-9._:-]{8,100}");

    @Transactional
    @Override
    public OrderResponseDto create(Long userId, CreateOrderRequestDto req) {
        return create(userId, req, null);
    }

    @Transactional
    @Override
    public OrderResponseDto create(Long userId, CreateOrderRequestDto req, String idempotencyKey) {
        return idempotent("U:" + userId, idempotencyKey, req, () -> createNew(userId, req));
    }

    private OrderResponseDto createNew(Long userId, CreateOrderRequestDto req) {
        CustomerAddressEntity address = addresses.findByIdAndUserId(req.getAddressId(), userId);
        if (address == null) {
            throw new ResourceNotFoundException("Địa chỉ giao hàng", "Address", req.getAddressId());
        }
        return placeOrder(userId, null, null, address, req.getPaymentMethod(), req.getShippingMethod(),
                req.getVoucherCode(), req.getNote(), req.getItems());
    }

    @Transactional
    @Override
    public OrderResponseDto createGuest(GuestOrderRequestDto req, String guestCartId) {
        return createGuest(req, guestCartId, null);
    }

    @Transactional
    @Override
    public OrderResponseDto createGuest(GuestOrderRequestDto req, String guestCartId, String idempotencyKey) {
        return idempotent("G:" + digits(req.getPhone()), idempotencyKey, req, () -> createGuestNew(req, guestCartId));
    }

    /** Bọc đặt đơn bằng Idempotency-Key. Khoá được ghi CÙNG transaction với đơn: nếu hai request cùng khoá chạy đồng thời thì
     * request chậm hơn vi phạm UNIQUE (owner, idem_key) -> rollback toàn bộ (tồn/voucher không bị trừ hai lần) và
     * OrderPlacementService thử lại để nhận đơn của request kia. */
    private OrderResponseDto idempotent(String owner, String key, Object payload, java.util.function.Supplier<OrderResponseDto> action) {
        if (key == null || key.isBlank()) {
            return action.get();
        }
        String k = key.trim();
        if (!IDEMPOTENCY_KEY_PATTERN.matcher(k).matches()) {
            throw BusinessException.badRequest(ErrorCode.IDEMPOTENCY_KEY_INVALID,
                    "Idempotency-Key phải dài 8-100 ký tự (chữ, số, - _ . :)");
        }
        String hash = sha256(payload);
        OrderIdempotencyKeyEntity existing = idempotencyKeys.findByOwnerAndKey(owner, k);
        if (existing != null) {
            boolean expired = existing.getCreatedDate() != null
                    && System.currentTimeMillis() - existing.getCreatedDate().getTime() > IDEMPOTENCY_TTL_MILLIS;
            if (!expired) {
                if (!existing.getRequestHash().equals(hash)) {
                    throw BusinessException.conflict(ErrorCode.IDEMPOTENCY_KEY_REUSED,
                            "Idempotency-Key này đã được dùng cho một đơn hàng với nội dung khác");
                }
                OrderEntity previous = orders.findByCode(existing.getOrderCode());
                if (previous != null) {
                    return toDto(previous);
                }
            }
            idempotencyKeys.delete(existing); // hết hạn (hoặc đơn không còn) -> cho phép dùng lại khoá
        }
        OrderResponseDto created = action.get();
        OrderIdempotencyKeyEntity row = new OrderIdempotencyKeyEntity();
        row.setOwner(owner);
        row.setIdemKey(k);
        row.setRequestHash(hash);
        row.setOrderCode(created.getCode());
        idempotencyKeys.create(row);
        return created;
    }

    private String sha256(Object payload) {
        try {
            byte[] digest = java.security.MessageDigest.getInstance("SHA-256")
                    .digest(objectMapper.writeValueAsString(payload).getBytes(java.nio.charset.StandardCharsets.UTF_8));
            return java.util.HexFormat.of().formatHex(digest);
        } catch (Exception e) {
            throw new IllegalStateException("Không băm được nội dung đặt đơn", e);
        }
    }

    private OrderResponseDto createGuestNew(GuestOrderRequestDto req, String guestCartId) {
        CustomerAddressEntity shipTo = new CustomerAddressEntity();
        shipTo.setRecipientName(req.getRecipientName().trim());
        shipTo.setPhone(req.getPhone().trim());
        shipTo.setProvince(req.getProvince().trim());
        shipTo.setDistrict(req.getDistrict().trim());
        shipTo.setWard(req.getWard().trim());
        shipTo.setAddressLine(req.getAddressLine().trim());
        String email = req.getEmail() == null || req.getEmail().isBlank() ? null : req.getEmail().trim();
        return placeOrder(null, email, guestCartId, shipTo, req.getPaymentMethod(), req.getShippingMethod(),
                req.getVoucherCode(), req.getNote(), req.getItems());
    }

    @Transactional(readOnly = true)
    @Override
    public OrderResponseDto findGuestOrder(String code, String phone) {
        OrderEntity order = code == null || code.isBlank() ? null : orders.findByCode(code.trim());
        if (order == null || order.getUserId() != null || phone == null
                || !digits(phone).equals(digits(order.getShipPhone()))) {
            throw new ResourceNotFoundException("Đơn hàng", "Order", code);
        }
        return toDto(order);
    }

    private static String digits(String value) {
        return value == null ? "" : value.replaceAll("[^0-9]", "");
    }

    /** Lõi đặt hàng dùng chung cho khách đăng nhập (userId != null) và khách vãng lai (userId = null). */
    private OrderResponseDto placeOrder(Long userId, String guestEmail, String guestCartId, CustomerAddressEntity address,
                                        String paymentMethod, String shippingMethod, String voucherCode, String noteText,
                                        List<OrderItemRequestDto> requestItems) {
        // Gộp trùng skuId (client lỡ gửi 2 dòng cùng 1 SKU) để chỉ giữ chỗ tồn kho + tạo 1 dòng duy nhất.
        Map<Long, Integer> qtyBySku = new LinkedHashMap<>();
        for (OrderItemRequestDto line : requestItems) {
            qtyBySku.merge(line.getSkuId(), line.getQuantity(), Integer::sum);
        }

        List<OrderItemEntity> itemEntities = new ArrayList<>();
        BigDecimal subtotal = BigDecimal.ZERO;
        BigDecimal productDiscount = BigDecimal.ZERO;
        boolean hasPreOrderLine = false;
        // Mỗi lần reserveStock/tạo dòng hàng đều nằm trong transaction này: nếu 1 SKU giữa chừng
        // không đủ hàng và ném exception, Spring sẽ rollback TOÀN BỘ (kể cả các reserveStock đã chạy
        // trước đó trong vòng lặp) - không cần tự tay nhả lại tồn kho ở đây.
        for (Map.Entry<Long, Integer> line : qtyBySku.entrySet()) {
            Long skuId = line.getKey();
            int qty = line.getValue();

            ProductSkuEntity sku = productSkus.findOne(skuId);
            if (!sellable(sku)) {
                throw new ResourceNotFoundException("Sản phẩm", "ProductSku", skuId);
            }
            // Đặt trước đang bật và SKU đang hết hàng: nhận đơn đặt trước (reserved vượt on_hand, available âm).
            boolean preOrderLine = sku.available() <= 0 && preOrder.appliesTo(sku);
            int reserved = preOrderLine ? productSkus.reservePreOrderStock(skuId, qty)
                    : productSkus.reserveStock(skuId, qty);
            if (preOrderLine && reserved > 0) {
                hasPreOrderLine = true;
            }
            if (reserved == 0) {
                // args khớp placeholder {0} của messages_vi/en (INSUFFICIENT_STOCK) - available() đọc
                // TRƯỚC lần reserveStock vừa thất bại nên chỉ mang tính tham khảo (có thể vừa đổi do
                // request khác), đủ tốt cho thông báo UX, không cần chính xác tuyệt đối tức thời.
                throw BusinessException.badRequest(ErrorCode.INSUFFICIENT_STOCK,
                        "Sản phẩm \"" + sku.getName() + "\" không đủ hàng, chỉ còn " + sku.available(),
                        sku.available());
            }

            ProductEntity product = products.findOne(sku.getProductId());
            BigDecimal unitPrice = sku.effectivePrice();
            itemEntities.add(new OrderItemEntity(null, sku.getProductId(), skuId,
                    product != null ? product.getName() : sku.getName(),
                    product != null ? product.getThumbnailUrl() : null, qty, unitPrice));
            subtotal = subtotal.add(unitPrice.multiply(BigDecimal.valueOf(qty)));
            productDiscount = productDiscount.add(
                    promotionPricing.unitDiscount(sku.getProductId(), unitPrice).multiply(BigDecimal.valueOf(qty)));
        }

        VoucherEntity voucher = null;
        BigDecimal voucherDiscount = BigDecimal.ZERO;
        String appliedVoucherCode = null;
        if (voucherCode != null && !voucherCode.isBlank()) {
            BigDecimal payable = subtotal.subtract(productDiscount);
            voucher = voucherService.validate(voucherCode, payable);
            voucherDiscount = voucher.computeDiscount(payable);
            appliedVoucherCode = voucher.getCode();
        }

        // Khuyến mãi sản phẩm đã áp (PromotionPricingService); CHƯA tính phí ship (chưa có bảng phí theo
        // phương thức/khu vực) nên để 0.
        OrderEntity order = new OrderEntity(userId, paymentMethod, shippingMethod, address,
                subtotal, productDiscount, voucherDiscount, BigDecimal.ZERO, appliedVoucherCode, noteText);
        order.setGuestEmail(guestEmail);
        orders.create(order);

        for (OrderItemEntity item : itemEntities) {
            item.setOrderId(order.getId());
            orderItems.create(item);
        }

        orderTimelines.create(new OrderTimelineEntity(order.getId(), OrderEntity.PENDING,
                hasPreOrderLine ? "Đơn hàng được tạo (có sản phẩm đặt trước)" : "Đơn hàng được tạo", "CUSTOMER"));

        // Dọn khỏi giỏ của khách CHỈ những dòng vừa đặt thành công; dòng chưa mua giữ nguyên.
        CartEntity userCart = userId != null ? carts.findByUserId(userId)
                : (guestCartId == null || guestCartId.isBlank() ? null : carts.findByGuestId(guestCartId.trim()));
        if (userCart != null) {
            for (OrderItemEntity item : itemEntities) {
                cartItems.deleteByCartIdAndSkuId(userCart.getId(), item.getSkuId());
            }
        }

        if (voucher != null) {
            voucherService.applyUsage(voucher.getId(), voucher.getCode());
        }

        // Thanh toán online (khác COD): tạo bản ghi Payment PENDING, cổng thanh toán sẽ gọi lại
        // POST /storefront/payment/{paymentId}/callback (paymentId = id vừa tạo) - xem PaymentService.
        // COD thì không tạo Payment (paymentStatus của đơn giữ nguyên UNPAID, thu tiền khi giao).
        if (!"COD".equalsIgnoreCase(order.getPaymentMethod())) {
            payments.create(new PaymentEntity(order.getId(), order.getPaymentMethod(), order.getGrandTotal()));
        }

        return toDto(order);
    }

    @Transactional(readOnly = true)
    @Override
    public PageResponse<OrderResponseDto> listMine(Long userId, int page, int take) {
        List<OrderResponseDto> all = orders.findByUserId(userId).stream().map(this::toDto).toList();
        return PageResponse.paginate(all, page, take);
    }

    @Transactional(readOnly = true)
    @Override
    public OrderResponseDto findByCodeForUser(Long userId, String code) {
        OrderEntity order = orders.findByCodeAndUserId(code, userId);
        if (order == null) {
            throw new ResourceNotFoundException("Đơn hàng", "Order", code);
        }
        return toDto(order);
    }

    @Transactional
    @Override
    public OrderResponseDto cancel(Long userId, String code, String reason) {
        OrderEntity order = orders.findByCodeAndUserId(code, userId);
        if (order == null) {
            throw new ResourceNotFoundException("Đơn hàng", "Order", code);
        }
        // args khớp placeholder {0}/{1} của messages_vi/en (ORDER_ALREADY_CANCELLED/ORDER_STATUS_TRANSITION_INVALID).
        if (OrderEntity.CANCELLED.equals(order.getStatus())) {
            throw BusinessException.badRequest(ErrorCode.ORDER_ALREADY_CANCELLED,
                    "Đơn hàng " + order.getCode() + " đã được huỷ trước đó", order.getCode());
        }
        if (!order.isCancellableByCustomer()) {
            throw BusinessException.badRequest(ErrorCode.ORDER_STATUS_TRANSITION_INVALID,
                    "Đơn hàng đang ở trạng thái " + order.getStatus() + ", không thể tự huỷ",
                    order.getStatus(), OrderEntity.CANCELLED);
        }

        guardTransition(order, OrderEntity.CANCELLED);
        order.cancel();
        order.touch();
        orders.update(order);

        releaseOrderResources(order);

        orderTimelines.create(new OrderTimelineEntity(order.getId(), OrderEntity.CANCELLED, reason, "CUSTOMER"));

        return toDto(order);
    }

    private OrderResponseDto toDto(OrderEntity o) {
        List<OrderItemResponseDto> items = orderItems.findByOrderId(o.getId()).stream()
                .map(OrderItemResponseDto::from).toList();
        List<OrderTimelineResponseDto> timeline = orderTimelines.findByOrderId(o.getId()).stream()
                .map(OrderTimelineResponseDto::from).toList();
        PaymentEntity payment = payments.findByOrderId(o.getId());
        OrderResponseDto dto = OrderResponseDto.from(o, payment != null ? payment.getId() : null, items, timeline);
        dto.setDeliveredAt(deliveredAtOf(orderTimelines.findByOrderId(o.getId())));
        if (o.getUserId() != null) {
            UserEntity user = users.findOne(o.getUserId());
            if (user != null && user.getEmail() != null && !user.getEmail().isBlank()) {
                dto.setContactEmail(user.getEmail());
            }
        }
        if ("COD".equalsIgnoreCase(o.getPaymentMethod()) && o.getGrandTotal() != null) {
            dto.setCodAmount(o.getGrandTotal());
        }
        return dto;
    }

    // ============================== Admin (mục L spec) ==============================

    @Transactional(readOnly = true)
    @Override
    public PageResponse<OrderResponseDto> searchForAdmin(String search, String status, String paymentStatus,
                                                           String fromDate, String toDate, int page, int take) {
        return searchForAdmin(new AdminOrderQuery(search, status, paymentStatus, fromDate, toDate, null, null, null,
                null, null, null, null, page, take));
    }

    @Transactional(readOnly = true)
    @Override
    public PageResponse<OrderResponseDto> searchForAdmin(AdminOrderQuery q) {
        List<OrderResponseDto> all = adminMatch(q).stream().map(this::toDto).toList();
        return PageResponse.paginate(all, q.page(), q.take());
    }

    private static final List<String> ORDER_STATUSES = List.of(OrderEntity.PENDING, OrderEntity.CONFIRMED,
            OrderEntity.SHIPPING, OrderEntity.DELIVERED, OrderEntity.CANCELLED, OrderEntity.RETURNED);

    @Transactional(readOnly = true)
    @Override
    public OrderCountsDto countsForAdmin(AdminOrderQuery q) {
        Map<String, Long> by = new LinkedHashMap<>();
        ORDER_STATUSES.forEach(st -> by.put(st, 0L));
        List<OrderEntity> matched = adminMatch(q.withoutStatus());
        for (OrderEntity o : matched) {
            by.merge(o.getStatus(), 1L, Long::sum);
        }
        return new OrderCountsDto(matched.size(), by);
    }

    /** Tra đơn cho admin: ưu tiên mã đơn, nếu không có và tham số toàn chữ số thì tra theo id số. */
    private OrderEntity adminOrder(String codeOrId) {
        String key = trimOrEmpty(codeOrId);
        OrderEntity order = key.isEmpty() ? null : orders.findByCode(key);
        if (order == null && key.matches("\\d{1,18}")) {
            order = orders.findOne(Long.parseLong(key));
        }
        if (order == null) {
            throw new ResourceNotFoundException("Đơn hàng", "Order", codeOrId);
        }
        return order;
    }

    private static String normToken(String v) {
        return v == null ? "" : v.replaceAll("[^A-Za-z0-9]", "").toLowerCase(java.util.Locale.ROOT);
    }

    private static Date deliveredAtOf(List<OrderTimelineEntity> timeline) {
        Date latest = null;
        for (OrderTimelineEntity t : timeline) {
            if (OrderEntity.DELIVERED.equals(t.getStatus()) && t.getOccurredAt() != null
                    && (latest == null || t.getOccurredAt().after(latest))) {
                latest = t.getOccurredAt();
            }
        }
        return latest;
    }

    /** Lọc bằng SQL (status/paymentStatus/từ khoá) rồi lọc tiếp + sắp xếp bằng Java. */
    private List<OrderEntity> adminMatch(AdminOrderQuery q) {
        String dateField = normToken(q.dateField());
        if (!dateField.isEmpty() && !dateField.equals("createdat") && !dateField.equals("deliveredat")) {
            throw BusinessException.badRequest(ErrorCode.VALIDATION_FAILED,
                    "dateField không hợp lệ: " + q.dateField() + " (createdAt | deliveredAt)", q.dateField());
        }
        boolean byDelivered = dateField.equals("deliveredat");
        String sortKey = normToken(q.orderBy());
        if (!Set.of("", "createdat", "updatedat", "ordercode", "grandtotal").contains(sortKey)) {
            throw BusinessException.badRequest(ErrorCode.VALIDATION_FAILED,
                    "orderBy không hợp lệ: " + q.orderBy() + " (createdAt | updatedAt | orderCode | grandTotal)", q.orderBy());
        }
        String dir = trimOrEmpty(q.orderType()).toUpperCase(java.util.Locale.ROOT);
        if (!Set.of("", "ASC", "DESC").contains(dir)) {
            throw BusinessException.badRequest(ErrorCode.VALIDATION_FAILED,
                    "orderType không hợp lệ: " + q.orderType() + " (ASC | DESC)", q.orderType());
        }
        String source = normToken(q.source());
        if (!source.isEmpty() && !source.equals("website") && !source.equals("online")) {
            return List.of(); // mọi đơn hiện đều đặt từ website
        }
        Date from = parseAdminDate(q.fromDate(), false);
        Date to = parseAdminDate(q.toDate(), true);
        String ship = normToken(q.shippingMethod());
        String province = trimOrEmpty(q.provinceName()).toLowerCase(java.util.Locale.ROOT);
        String skuKw = trimOrEmpty(q.skuCode()).toLowerCase(java.util.Locale.ROOT);
        Set<Long> skuIds = null;
        if (!skuKw.isEmpty()) {
            skuIds = new java.util.HashSet<>();
            for (ProductSkuEntity sku : productSkus.searchByKeyword(skuKw)) {
                if (sku.getSkuCode() != null && sku.getSkuCode().toLowerCase(java.util.Locale.ROOT).contains(skuKw)) {
                    skuIds.add(sku.getId());
                }
            }
            if (skuIds.isEmpty()) {
                return List.of();
            }
        }
        List<OrderEntity> out = new ArrayList<>();
        for (OrderEntity o : orders.searchAdmin(upperOrEmpty(q.status()), upperOrEmpty(q.paymentStatus()),
                trimOrEmpty(q.search()))) {
            if (!ship.isEmpty() && !ship.equals(normToken(o.getShippingMethod()))) {
                continue;
            }
            if (!province.isEmpty() && (o.getShipProvince() == null
                    || !o.getShipProvince().toLowerCase(java.util.Locale.ROOT).contains(province))) {
                continue;
            }
            if (from != null || to != null) {
                Date at = byDelivered ? deliveredAtOf(orderTimelines.findByOrderId(o.getId())) : o.getPlacedAt();
                if (at == null || (from != null && at.before(from)) || (to != null && at.after(to))) {
                    continue;
                }
            }
            if (skuIds != null) {
                final Set<Long> wanted = skuIds;
                if (orderItems.findByOrderId(o.getId()).stream().noneMatch(i -> wanted.contains(i.getSkuId()))) {
                    continue;
                }
            }
            out.add(o);
        }
        if (!sortKey.isEmpty() || !dir.isEmpty()) {
            java.util.Comparator<OrderEntity> cmp = switch (sortKey) {
                case "updatedat" -> java.util.Comparator.comparing(
                        (OrderEntity o) -> o.getUpdatedDate() != null ? o.getUpdatedDate() : o.getPlacedAt(),
                        java.util.Comparator.nullsFirst(java.util.Comparator.<Date>naturalOrder()));
                case "ordercode" -> java.util.Comparator.comparing(OrderEntity::getCode,
                        java.util.Comparator.nullsFirst(java.util.Comparator.<String>naturalOrder()));
                case "grandtotal" -> java.util.Comparator.comparing(OrderEntity::getGrandTotal,
                        java.util.Comparator.nullsFirst(java.util.Comparator.<java.math.BigDecimal>naturalOrder()));
                default -> java.util.Comparator.comparing(OrderEntity::getPlacedAt,
                        java.util.Comparator.nullsFirst(java.util.Comparator.<Date>naturalOrder()));
            };
            out.sort("ASC".equals(dir) ? cmp : cmp.reversed());
        }
        return out;
    }

    /** Bộ lọc tuỳ chọn: bỏ qua query / null / rỗng đều nghĩa là KHÔNG lọc (SQL chỉ hiểu chuỗi rỗng, NULL sẽ loại hết dòng). */
    private static String trimOrEmpty(String v) {
        return v == null ? "" : v.trim();
    }

    private static String upperOrEmpty(String v) {
        return trimOrEmpty(v).toUpperCase(java.util.Locale.ROOT);
    }

    /** Parse "yyyy-MM-dd" thành mốc đầu ngày (endOfDay=false, dùng cho fromDate) hoặc cuối ngày
     * (endOfDay=true, dùng cho toDate) theo múi giờ hệ thống - null/rỗng trả null (không lọc). Sai
     * định dạng -> 400 VALIDATION_FAILED thay vì 500 nội bộ. */
    private Date parseAdminDate(String value, boolean endOfDay) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            LocalDate d = LocalDate.parse(value);
            LocalDateTime dt = endOfDay ? d.atTime(23, 59, 59) : d.atStartOfDay();
            return Date.from(dt.atZone(ZoneId.systemDefault()).toInstant());
        } catch (Exception ex) {
            throw BusinessException.badRequest(ErrorCode.VALIDATION_FAILED,
                    "Định dạng ngày không hợp lệ (yêu cầu yyyy-MM-dd): " + value, value);
        }
    }

    @Transactional(readOnly = true)
    @Override
    public OrderResponseDto findByCodeForAdmin(String code) {
        OrderEntity order = adminOrder(code);
        return toDto(order);
    }

    @Transactional
    @Override
    public OrderResponseDto updateStatus(String code, AdminOrderStatusRequestDto request) {
        OrderEntity order = adminOrder(code);
        String oldStatus = order.getStatus();
        String newStatus = request.getStatus();
        // args khớp placeholder {0}/{1} của messages_vi/en (INVALID_STATUS_TRANSITION).
        if (!order.canTransitionTo(newStatus)) {
            throw BusinessException.conflict(ErrorCode.INVALID_STATUS_TRANSITION,
                    "Không thể chuyển đơn hàng từ trạng thái " + oldStatus + " sang " + newStatus,
                    oldStatus, newStatus);
        }

        guardTransition(order, newStatus);
        order.setStatus(newStatus);
        // DELIVERED: chốt xuất tồn đúng một lần (guardTransition đã chặn giao trùng) - on_hand và reserved cùng giảm.
        if (OrderEntity.DELIVERED.equals(newStatus) && !order.isStockFulfilled()) {
            for (OrderItemEntity item : orderItems.findByOrderId(order.getId())) {
                productSkus.fulfillStock(item.getSkuId(), item.getQuantity());
            }
            order.setStockFulfilled(true);
        }
        // COD: shipper thu tiền khi giao -> ghi nhận đã thanh toán lúc đơn sang DELIVERED.
        boolean codCollected = OrderEntity.DELIVERED.equals(newStatus)
                && "COD".equalsIgnoreCase(order.getPaymentMethod())
                && OrderEntity.UNPAID.equals(order.getPaymentStatus());
        if (codCollected) {
            order.setPaymentStatus(OrderEntity.PAID);
        }
        if (request.getTrackingCode() != null && !request.getTrackingCode().isBlank()) {
            order.setTrackingCode(request.getTrackingCode());
        }
        order.touch();
        orders.update(order);

        // Chuyển sang CANCELLED qua updateStatus cũng phải nhả tồn như cancel()/cancelByAdmin().
        // CANCELLED không có trạng thái đi tiếp nên không thể nhả hai lần.
        if (OrderEntity.CANCELLED.equals(newStatus) && !OrderEntity.CANCELLED.equals(oldStatus)) {
            releaseOrderResources(order);
        }

        String note = request.getNote() != null && !request.getNote().isBlank()
                ? request.getNote() : "Admin cập nhật trạng thái từ " + oldStatus + " sang " + newStatus;
        if (codCollected) {
            note = note + " (đã thu tiền COD)";
        }
        orderTimelines.create(new OrderTimelineEntity(order.getId(), newStatus, note, "ADMIN"));

        return toDto(order);
    }

    @Transactional
    @Override
    public OrderResponseDto cancelByAdmin(String code, String reason) {
        OrderEntity order = adminOrder(code);
        // Cùng quy ước cancel() của khách (xem javadoc phía trên) - chỉ khác isCancellableByAdmin().
        if (OrderEntity.CANCELLED.equals(order.getStatus())) {
            throw BusinessException.badRequest(ErrorCode.ORDER_ALREADY_CANCELLED,
                    "Đơn hàng " + order.getCode() + " đã được huỷ trước đó", order.getCode());
        }
        if (!order.isCancellableByAdmin()) {
            throw BusinessException.badRequest(ErrorCode.ORDER_STATUS_TRANSITION_INVALID,
                    "Đơn hàng đang ở trạng thái " + order.getStatus() + ", không thể huỷ",
                    order.getStatus(), OrderEntity.CANCELLED);
        }

        guardTransition(order, OrderEntity.CANCELLED);
        order.cancel();
        order.touch();
        orders.update(order);

        releaseOrderResources(order);

        orderTimelines.create(new OrderTimelineEntity(order.getId(), OrderEntity.CANCELLED, reason, "ADMIN"));

        return toDto(order);
    }

    @Transactional
    @Override
    public OrderResponseDto returnOrder(String code, AdminOrderReturnRequestDto request) {
        OrderEntity order = adminOrder(code);
        if (!order.isReturnable()) {
            throw BusinessException.conflict(ErrorCode.INVALID_STATUS_TRANSITION,
                    "Chỉ xác nhận hoàn trả được khi đơn đang DELIVERED (hiện tại: " + order.getStatus() + ")",
                    order.getStatus(), OrderEntity.RETURNED);
        }

        // lineId -> số lượng muốn trả (-1 = toàn bộ số còn lại, dùng cho cách cũ `lineIds`).
        Map<Long, Integer> wanted = new LinkedHashMap<>();
        if (request.getLines() != null) {
            for (AdminOrderReturnRequestDto.Line l : request.getLines()) {
                wanted.merge(l.getLineId(), l.getQuantity(), Integer::sum);
            }
        }
        if (request.getLineIds() != null) {
            for (Long id : request.getLineIds()) {
                wanted.putIfAbsent(id, -1);
            }
        }
        if (wanted.isEmpty()) {
            throw BusinessException.badRequest(ErrorCode.VALIDATION_FAILED, "Cần lines hoặc lineIds để hoàn trả");
        }

        Map<Long, OrderItemEntity> byId = new LinkedHashMap<>();
        for (OrderItemEntity item : orderItems.findByOrderId(order.getId())) {
            byId.put(item.getId(), item);
        }
        // Kiểm tra TOÀN BỘ trước khi ghi.
        Map<Long, Integer> toReturn = new LinkedHashMap<>();
        for (Map.Entry<Long, Integer> e : wanted.entrySet()) {
            OrderItemEntity item = byId.get(e.getKey());
            if (item == null) {
                throw BusinessException.badRequest(ErrorCode.NOT_FOUND,
                        "Không tìm thấy dòng hàng " + e.getKey() + " trong đơn " + code);
            }
            int remaining = item.getQuantity() - item.getReturnedQuantity();
            int qty = e.getValue() == -1 ? remaining : e.getValue();
            if (qty <= 0 || qty > remaining) {
                throw BusinessException.badRequest(ErrorCode.VALIDATION_FAILED,
                        "Dòng " + e.getKey() + " chỉ còn trả được " + remaining + " sản phẩm", remaining);
            }
            toReturn.put(e.getKey(), qty);
        }

        for (Map.Entry<Long, Integer> e : toReturn.entrySet()) {
            OrderItemEntity item = byId.get(e.getKey());
            int qty = e.getValue();
            // CAS: hai request trả trùng cùng lúc thì request sau bị từ chối (rollback cả transaction).
            if (orderItems.addReturned(item.getId(), qty) == 0) {
                throw BusinessException.conflict(ErrorCode.INVALID_STATUS_TRANSITION,
                        "Dòng " + item.getId() + " vừa được hoàn trả bởi thao tác khác, vui lòng tải lại",
                        order.getStatus(), OrderEntity.RETURNED);
            }
            // Đã chốt xuất tồn lúc giao -> hàng trả về cộng lại on_hand; đơn cũ chưa chốt -> nhả reserved như trước.
            if (order.isStockFulfilled()) {
                productSkus.adjustOnHand(item.getSkuId(), qty);
            } else {
                productSkus.releaseStock(item.getSkuId(), qty);
            }
        }

        boolean allReturned = orderItems.findByOrderId(order.getId()).stream()
                .allMatch(i -> i.getReturnedQuantity() >= i.getQuantity());
        int units = toReturn.values().stream().mapToInt(Integer::intValue).sum();
        String note = "Hoàn trả " + units + " sản phẩm (" + toReturn + ")" + (allReturned ? " - đã trả hết đơn" : " - trả một phần");
        if (allReturned) {
            orders.updateStatusIfCurrent(order.getId(), OrderEntity.DELIVERED, OrderEntity.RETURNED);
            order.markReturned();
        }
        orderTimelines.create(new OrderTimelineEntity(order.getId(), order.getStatus(), note, "ADMIN"));
        return toDto(orders.findOne(order.getId()));
    }

    @Transactional
    @Override
    public OrderResponseDto refund(String code, BigDecimal amount, String reason) {
        OrderEntity order = adminOrder(code);
        if (amount == null || amount.signum() <= 0 || amount.stripTrailingZeros().scale() > 0) {
            throw BusinessException.badRequest(ErrorCode.VALIDATION_FAILED,
                    "Số tiền hoàn phải là số nguyên dương (VND)");
        }
        if (!OrderEntity.PAID.equals(order.getPaymentStatus())) {
            throw BusinessException.conflict(ErrorCode.INVALID_STATUS_TRANSITION,
                    "Chỉ hoàn tiền được đơn đã thanh toán và chưa hoàn đủ (paymentStatus hiện tại: "
                            + order.getPaymentStatus() + ")",
                    order.getPaymentStatus(), OrderEntity.REFUNDED);
        }
        BigDecimal refundable = order.getGrandTotal().subtract(order.getRefundedAmount());
        if (amount.compareTo(refundable) > 0) {
            throw BusinessException.badRequest(ErrorCode.VALIDATION_FAILED,
                    "Số tiền hoàn (" + amount + ") vượt số còn hoàn được (" + refundable + ")", refundable);
        }
        // CAS: hai lần hoàn đồng thời không thể cùng vượt tổng đơn. Hoàn đủ -> REFUNDED, một phần -> vẫn PAID.
        if (orders.addRefund(order.getId(), amount) == 0) {
            throw BusinessException.conflict(ErrorCode.INVALID_STATUS_TRANSITION,
                    "Đơn hàng " + order.getCode() + " vừa được hoàn tiền bởi thao tác khác, vui lòng tải lại",
                    order.getPaymentStatus(), OrderEntity.REFUNDED);
        }
        OrderEntity fresh = orders.findOne(order.getId());
        orderTimelines.create(new OrderTimelineEntity(order.getId(), fresh.getStatus(),
                "Hoàn tiền " + amount + " (đã hoàn " + fresh.getRefundedAmount() + "/" + fresh.getGrandTotal() + ") - Lý do: " + reason,
                "ADMIN"));
        return toDto(fresh);
    }

    /** Nhả tồn đã giữ chỗ và trả lượt dùng voucher (nếu đơn có áp voucher) khi đơn bị huỷ. */
    private void releaseOrderResources(OrderEntity order) {
        for (OrderItemEntity item : orderItems.findByOrderId(order.getId())) {
            productSkus.releaseStock(item.getSkuId(), item.getQuantity());
        }
        if (order.getAppliedVoucherCode() != null && !order.getAppliedVoucherCode().isBlank()) {
            voucherService.releaseUsage(order.getAppliedVoucherCode());
        }
    }

    /** Chuyển trạng thái nguyên tử: nếu request khác đã đổi trạng thái đơn trước thì dừng (không nhả tồn hai lần). */
    private void guardTransition(OrderEntity order, String toStatus) {
        if (orders.updateStatusIfCurrent(order.getId(), order.getStatus(), toStatus) == 0) {
            throw BusinessException.conflict(ErrorCode.INVALID_STATUS_TRANSITION,
                    "Đơn hàng " + order.getCode() + " vừa được cập nhật bởi thao tác khác, vui lòng tải lại",
                    order.getStatus(), toStatus);
        }
    }

    /** SKU bán được: SKU PUBLISHED và sản phẩm cha còn tồn tại, chưa xóa, đang PUBLISHED. */
    private boolean sellable(ProductSkuEntity sku) {
        if (sku == null || !ProductSkuEntity.PUBLISHED.equals(sku.getStatus())) {
            return false;
        }
        ProductEntity product = products.findOne(sku.getProductId());
        return product != null && product.isSellable();
    }
}
