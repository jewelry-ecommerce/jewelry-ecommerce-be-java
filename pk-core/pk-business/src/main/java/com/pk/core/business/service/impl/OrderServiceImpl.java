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
import com.pk.core.common.util.PhoneUtil;
import com.pk.core.model.dto.request.OmsRequestDtos;
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
        requireCod(req.getPaymentMethod());
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
                            "Idempotency-Key này đã được dùng cho một yêu cầu khác với nội dung khác");
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
        requireCod(req.getPaymentMethod());
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

    /** Tuỳ chọn đặt đơn: nguồn đơn (WEBSITE | ADMIN), id người tạo, khoản trừ cố định (đơn đổi hàng). */
    private record PlaceOpts(String source, Long createdId, BigDecimal credit) {
        boolean admin() {
            return "ADMIN".equals(source);
        }
    }

    /** Kết quả giữ chỗ tồn kho cho một nhóm dòng hàng. */
    private record LineBuild(List<OrderItemEntity> items, BigDecimal subtotal, BigDecimal productDiscount,
                             boolean hasPreOrder, List<VoucherService.Line> voucherLines) {
    }

    /** Lõi đặt hàng dùng chung cho khách đăng nhập (userId != null) và khách vãng lai (userId = null). */
    private OrderResponseDto placeOrder(Long userId, String guestEmail, String guestCartId, CustomerAddressEntity address,
                                        String paymentMethod, String shippingMethod, String voucherCode, String noteText,
                                        List<OrderItemRequestDto> requestItems) {
        return placeOrder(userId, guestEmail, guestCartId, address, paymentMethod, shippingMethod, voucherCode, noteText,
                requestItems, new PlaceOpts("WEBSITE", userId, BigDecimal.ZERO));
    }

    /** Giữ chỗ tồn kho + snapshot dòng hàng. Mọi thay đổi nằm trong transaction của caller: lỗi giữa chừng -> rollback hết. */
    private LineBuild reserveLines(Map<Long, Integer> qtyBySku) {
        List<OrderItemEntity> itemEntities = new ArrayList<>();
        BigDecimal subtotal = BigDecimal.ZERO;
        BigDecimal productDiscount = BigDecimal.ZERO;
        boolean hasPreOrderLine = false;
        List<VoucherService.Line> voucherLines = new ArrayList<>();
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
                throw BusinessException.badRequest(ErrorCode.INSUFFICIENT_STOCK,
                        "Sản phẩm \"" + sku.getName() + "\" không đủ hàng, chỉ còn " + sku.available(),
                        sku.available());
            }

            ProductEntity product = products.findOne(sku.getProductId());
            BigDecimal unitPrice = sku.effectivePrice();
            OrderItemEntity snap = new OrderItemEntity(null, sku.getProductId(), skuId,
                    product != null ? product.getName() : sku.getName(),
                    product != null ? product.getThumbnailUrl() : null, qty, unitPrice);
            snap.setSkuCode(sku.getSkuCode());
            snap.setSizeLabel(sku.getSizeLabel());
            snap.setMetalColorLabel(sku.getMetalColorLabel());
            itemEntities.add(snap);
            BigDecimal lineDiscount = promotionPricing.unitDiscount(sku.getProductId(), sku.getId(), unitPrice)
                    .multiply(BigDecimal.valueOf(qty));
            subtotal = subtotal.add(unitPrice.multiply(BigDecimal.valueOf(qty)));
            productDiscount = productDiscount.add(lineDiscount);
            voucherLines.add(new VoucherService.Line(skuId, unitPrice.multiply(BigDecimal.valueOf(qty)).subtract(lineDiscount)));
        }
        return new LineBuild(itemEntities, subtotal, productDiscount, hasPreOrderLine, voucherLines);
    }

    private OrderResponseDto placeOrder(Long userId, String guestEmail, String guestCartId, CustomerAddressEntity address,
                                        String paymentMethod, String shippingMethod, String voucherCode, String noteText,
                                        List<OrderItemRequestDto> requestItems, PlaceOpts opts) {
        // Gộp trùng skuId (client lỡ gửi 2 dòng cùng 1 SKU) để chỉ giữ chỗ tồn kho + tạo 1 dòng duy nhất.
        Map<Long, Integer> qtyBySku = new LinkedHashMap<>();
        for (OrderItemRequestDto line : requestItems) {
            qtyBySku.merge(line.getSkuId(), line.getQuantity(), Integer::sum);
        }
        LineBuild built = reserveLines(qtyBySku);
        List<OrderItemEntity> itemEntities = built.items();
        BigDecimal subtotal = built.subtotal();
        BigDecimal productDiscount = built.productDiscount();
        boolean hasPreOrderLine = built.hasPreOrder();
        if (opts.credit() != null && opts.credit().signum() > 0) {
            // Đơn đổi hàng: giá trị hàng đã trả được trừ vào đơn mới (không vượt quá tiền hàng còn phải trả).
            productDiscount = productDiscount.add(opts.credit().min(subtotal.subtract(productDiscount)));
        }

        VoucherEntity voucher = null;
        BigDecimal voucherDiscount = BigDecimal.ZERO;
        String appliedVoucherCode = null;
        if (voucherCode != null && !voucherCode.isBlank()) {
            VoucherService.Applied applied = voucherService.apply(voucherCode, built.voucherLines());
            voucher = applied.voucher();
            voucherDiscount = applied.discount();
            appliedVoucherCode = voucher.getCode();
        }

        // Khuyến mãi sản phẩm đã áp (PromotionPricingService); CHƯA tính phí ship (chưa có bảng phí theo
        // phương thức/khu vực) nên để 0.
        OrderEntity order = new OrderEntity(userId, paymentMethod, shippingMethod, address,
                subtotal, productDiscount, voucherDiscount, BigDecimal.ZERO, appliedVoucherCode, noteText);
        order.setGuestEmail(guestEmail);
        order.setCreatedId(opts.createdId());
        order.setSource(opts.source());
        orders.create(order);

        for (OrderItemEntity item : itemEntities) {
            item.setOrderId(order.getId());
            orderItems.create(item);
        }

        orderTimelines.create(new OrderTimelineEntity(order.getId(), OrderEntity.PENDING,
                (opts.admin() ? "Nhân viên tạo đơn hàng" : "Đơn hàng được tạo")
                        + (hasPreOrderLine ? " (có sản phẩm đặt trước)" : ""), opts.admin() ? "ADMIN" : "CUSTOMER"));

        // Dọn khỏi giỏ của khách CHỈ những dòng vừa đặt thành công; dòng chưa mua giữ nguyên.
        CartEntity userCart = opts.admin() ? null : userId != null ? carts.findByUserId(userId)
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
        dto.setCreatedById(o.getCreatedId());
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
        }
        List<OrderEntity> out = new ArrayList<>();
        for (OrderEntity o : orders.searchAdmin(upperOrEmpty(q.status()), upperOrEmpty(q.paymentStatus()),
                trimOrEmpty(q.search()))) {
            if (!ship.isEmpty() && !ship.equals(normToken(o.getShippingMethod()))) {
                continue;
            }
            if (q.createdById() != null && !q.createdById().equals(o.getCreatedId())) {
                continue;
            }
            if (!source.isEmpty()) {
                String os = o.getSource() == null ? "website" : normToken(o.getSource());
                if (!source.equals(os) && !(source.equals("online") && os.equals("website"))) {
                    continue;
                }
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
                // Ưu tiên SKU snapshot trên dòng đơn (đổi mã SKU sau không làm mất đơn cũ); đơn cũ chưa có snapshot -> SKU hiện tại.
                if (orderItems.findByOrderId(o.getId()).stream().noneMatch(i -> i.getSkuCode() != null
                        ? i.getSkuCode().toLowerCase(java.util.Locale.ROOT).contains(skuKw)
                        : wanted.contains(i.getSkuId()))) {
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

    // ============================== Admin OMS nâng cao: tạo / sửa / nhân bản / xác nhận thanh toán ==============================

    private static final Set<String> ADMIN_EDITABLE = Set.of(OrderEntity.PENDING, OrderEntity.CONFIRMED);

    private static String nz(String v, String fallback) {
        return v == null || v.isBlank() ? fallback : v.trim();
    }

    private static String money(BigDecimal v) {
        return String.valueOf((v == null ? BigDecimal.ZERO : v).longValue());
    }

    private static Map<Long, Integer> adminQty(OmsRequestDtos.OrderUpsert req) {
        if (req.getItems() == null || req.getItems().isEmpty()) {
            throw BusinessException.badRequest(ErrorCode.VALIDATION_FAILED, "Đơn hàng cần ít nhất một sản phẩm");
        }
        Map<Long, Integer> qty = new LinkedHashMap<>();
        for (OmsRequestDtos.OrderUpsert.Item i : req.getItems()) {
            Long sku = i.sku();
            if (sku == null || i.getQuantity() == null || i.getQuantity() < 1) {
                throw BusinessException.badRequest(ErrorCode.VALIDATION_FAILED,
                        "Mỗi dòng hàng cần variationId và quantity >= 1");
            }
            qty.merge(sku, i.getQuantity(), Integer::sum);
        }
        return qty;
    }

    private static String firstCoupon(OmsRequestDtos.OrderUpsert req) {
        if (req.getCouponCodes() != null) {
            for (String c : req.getCouponCodes()) {
                if (c != null && !c.isBlank()) {
                    return c.trim();
                }
            }
        }
        return null;
    }

    /** Phạm vi đồ án chỉ COD: storefront lẫn admin đều từ chối phương thức khác (400, mã ổn định cho FE). */
    private static void requireCod(String paymentMethod) {
        if (paymentMethod != null && !paymentMethod.isBlank() && !"COD".equalsIgnoreCase(paymentMethod.trim())) {
            throw BusinessException.badRequest(ErrorCode.PAYMENT_METHOD_UNSUPPORTED, "Hiện chỉ hỗ trợ thanh toán COD");
        }
    }

    private static String fullName(String lastName, String firstName) {
        return (nz(lastName, "") + " " + nz(firstName, "")).trim();
    }

    private CustomerAddressEntity shipSnapshot(OrderEntity o) {
        CustomerAddressEntity a = new CustomerAddressEntity();
        a.setRecipientName(o.getShipRecipientName());
        a.setPhone(o.getShipPhone());
        a.setProvince(o.getShipProvince());
        a.setDistrict(o.getShipDistrict());
        a.setWard(o.getShipWard());
        a.setAddressLine(o.getShipAddressLine());
        return a;
    }

    @Transactional(readOnly = true)
    @Override
    public Map<String, Object> quoteForAdmin(OmsRequestDtos.OrderUpsert req) {
        return computeQuote(adminQty(req), req.getCouponCodes(), req.getPaymentMethod());
    }

    /** Báo giá không giữ chỗ tồn: cùng công thức placeOrder (đơn giá hiệu lực - khuyến mãi sản phẩm - voucher), phí ship 0. */
    private Map<String, Object> computeQuote(Map<Long, Integer> qtyBySku, List<String> couponCodes, String paymentMethod) {
        List<Map<String, Object>> lines = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        BigDecimal subtotal = BigDecimal.ZERO;
        BigDecimal productDiscount = BigDecimal.ZERO;
        StringBuilder fp = new StringBuilder();
        List<VoucherService.Line> voucherLines = new ArrayList<>();
        for (Map.Entry<Long, Integer> e : qtyBySku.entrySet()) {
            ProductSkuEntity sku = productSkus.findOne(e.getKey());
            if (!sellable(sku)) {
                throw new ResourceNotFoundException("Sản phẩm", "ProductSku", e.getKey());
            }
            int qty = e.getValue();
            if (sku.available() < qty && !preOrder.appliesTo(sku)) {
                warnings.add("INSUFFICIENT_STOCK:" + sku.getSkuCode());
            }
            ProductEntity product = products.findOne(sku.getProductId());
            BigDecimal unit = sku.effectivePrice();
            BigDecimal unitDiscount = promotionPricing.unitDiscount(sku.getProductId(), sku.getId(), unit);
            BigDecimal lineTotal = unit.multiply(BigDecimal.valueOf(qty));
            BigDecimal lineDiscount = unitDiscount.multiply(BigDecimal.valueOf(qty));
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("variationId", String.valueOf(e.getKey()));
            m.put("productId", String.valueOf(sku.getProductId()));
            m.put("productName", product != null ? product.getName() : sku.getName());
            m.put("variationName", sku.getName());
            m.put("skuCode", sku.getSkuCode());
            m.put("image", product != null ? product.getThumbnailUrl() : null);
            m.put("unitPrice", money(unit));
            m.put("salePrice", money(unit.subtract(unitDiscount)));
            m.put("quantity", qty);
            m.put("lineTotal", money(lineTotal));
            m.put("discountAmount", money(lineDiscount));
            m.put("finalAmount", money(lineTotal.subtract(lineDiscount)));
            m.put("lineType", "PRODUCT");
            m.put("pricingMode", "STANDARD");
            lines.add(m);
            voucherLines.add(new VoucherService.Line(e.getKey(), lineTotal.subtract(lineDiscount)));
            subtotal = subtotal.add(lineTotal);
            productDiscount = productDiscount.add(lineDiscount);
            fp.append(e.getKey()).append(':').append(qty).append(':').append(unit.longValue()).append(':')
                    .append(lineDiscount.longValue()).append(';');
        }
        BigDecimal payable = subtotal.subtract(productDiscount);
        BigDecimal voucherDiscount = BigDecimal.ZERO;
        List<String> appliedCodes = new ArrayList<>();
        List<Map<String, Object>> outcomes = new ArrayList<>();
        boolean voucherTaken = false;
        if (couponCodes != null) {
            for (String raw : couponCodes) {
                if (raw == null || raw.isBlank()) {
                    continue;
                }
                String code = raw.trim().toUpperCase(java.util.Locale.ROOT);
                Map<String, Object> outcome = new LinkedHashMap<>();
                outcome.put("normalizedCouponCode", code);
                if (voucherTaken) {
                    outcome.put("status", "REJECTED");
                    outcome.put("reasonCodes", List.of("ONLY_ONE_VOUCHER_ALLOWED"));
                } else {
                    try {
                        VoucherService.Applied applied = voucherService.apply(code, voucherLines);
                        VoucherEntity v = applied.voucher();
                        voucherDiscount = applied.discount();
                        voucherTaken = true;
                        appliedCodes.add(v.getCode());
                        outcome.put("status", "APPLIED");
                        outcome.put("reasonCodes", List.of());
                        fp.append("V:").append(v.getCode()).append(':').append(voucherDiscount.longValue()).append(';');
                    } catch (BusinessException ex) {
                        outcome.put("status", "REJECTED");
                        outcome.put("reasonCodes", List.of(String.valueOf(ex.getCode())));
                        warnings.add("COUPON_REJECTED:" + code);
                    }
                }
                outcomes.add(outcome);
            }
        }
        BigDecimal grand = payable.subtract(voucherDiscount);
        fp.append("T:").append(grand.longValue());
        Map<String, Object> shipping = new LinkedHashMap<>();
        shipping.put("baseFeeMinor", "0");
        shipping.put("discountMinor", "0");
        shipping.put("customerFeeMinor", "0");
        Map<String, Object> pricing = new LinkedHashMap<>();
        pricing.put("subtotal", money(subtotal));
        pricing.put("shippingFee", "0");
        pricing.put("discountTotal", money(productDiscount.add(voucherDiscount)));
        pricing.put("taxTotal", "0");
        pricing.put("grandTotal", money(grand));
        pricing.put("totalPromotionSpend", money(productDiscount.add(voucherDiscount)));
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("pricingEvaluationId", "PE-" + sha256(fp.toString()).substring(0, 24));
        out.put("couponCodes", appliedCodes);
        out.put("couponOutcomes", outcomes);
        out.put("shipping", shipping);
        out.put("paymentMethod", nz(paymentMethod, "COD"));
        out.put("items", lines);
        out.put("pricing", pricing);
        out.put("warnings", warnings);
        return out;
    }

    @Transactional
    @Override
    public OrderResponseDto createByAdmin(OmsRequestDtos.OrderUpsert req, Long adminId) {
        return idempotent("A:" + adminId, req.getIdempotencyKey(), req, () -> createByAdminNew(req, adminId));
    }

    @SuppressWarnings("unchecked")
    private OrderResponseDto createByAdminNew(OmsRequestDtos.OrderUpsert req, Long adminId) {
        requireCod(req.getPaymentMethod());
        if (req.getReceiverPhone() == null || req.getReceiverPhone().isBlank()) {
            throw BusinessException.badRequest(ErrorCode.VALIDATION_FAILED, "Cần số điện thoại người nhận");
        }
        if (req.getAddressLine() == null || req.getAddressLine().isBlank()) {
            throw BusinessException.badRequest(ErrorCode.VALIDATION_FAILED, "Cần địa chỉ giao hàng");
        }
        Map<Long, Integer> qty = adminQty(req);
        Map<String, Object> quote = computeQuote(qty, req.getCouponCodes(), "COD");
        String quotedGrand = String.valueOf(((Map<String, Object>) quote.get("pricing")).get("grandTotal"));
        if ((req.getPricingEvaluationId() != null && !req.getPricingEvaluationId().isBlank()
                && !req.getPricingEvaluationId().equals(quote.get("pricingEvaluationId")))
                || (req.getExpectedGrandTotalMinor() != null
                && !String.valueOf(req.getExpectedGrandTotalMinor()).equals(quotedGrand))) {
            throw BusinessException.conflict("ORDER_PRICING_CHANGED",
                    "Giá đã thay đổi so với báo giá, vui lòng báo giá lại (tổng mới: " + quotedGrand + ")", quotedGrand);
        }
        String phone = PhoneUtil.normalize(req.getReceiverPhone().trim());
        UserEntity customer = users.findByPhone(phone);
        CustomerAddressEntity ship = new CustomerAddressEntity();
        String name = fullName(req.getLastName(), req.getFirstName());
        ship.setRecipientName(name.isEmpty() ? phone : name);
        ship.setPhone(phone);
        ship.setProvince(nz(req.getProvinceName(), "-"));
        ship.setDistrict(nz(req.getDistrictName(), "-"));
        ship.setWard(nz(req.getWardName(), "-"));
        ship.setAddressLine(req.getAddressLine().trim());
        List<OrderItemRequestDto> items = new ArrayList<>();
        qty.forEach((sku, q) -> items.add(new OrderItemRequestDto(sku, q)));
        return placeOrder(customer != null ? customer.getId() : null,
                customer == null ? nz(req.getContactEmail(), null) : null, null, ship, "COD",
                nz(req.getShippingMethod(), "STANDARD"), firstCoupon(req), req.getNote(), items,
                new PlaceOpts("ADMIN", adminId, BigDecimal.ZERO));
    }

    @Transactional
    @Override
    public OrderResponseDto updateByAdmin(String code, OmsRequestDtos.OrderUpsert req, Long adminId) {
        OrderEntity order = adminOrder(code);
        if (!ADMIN_EDITABLE.contains(order.getStatus())) {
            throw BusinessException.conflict(ErrorCode.INVALID_STATUS_TRANSITION,
                    "Chỉ sửa được đơn PENDING/CONFIRMED (hiện tại: " + order.getStatus() + ")",
                    order.getStatus(), order.getStatus());
        }
        requireCod(req.getPaymentMethod());
        // Khoá dòng đơn bằng CAS trạng thái (UPDATE status = status WHERE status = đã đọc): huỷ/đổi trạng thái chen vào giữa
        // -> 409; sửa song song được xếp hàng. Đọc lại đơn SAU khi giữ khoá để không ghi đè thay đổi của lần sửa trước.
        guardTransition(order, order.getStatus());
        order = adminOrder(code);
        if (req.getReceiverPhone() != null && !req.getReceiverPhone().isBlank()) {
            order.setShipPhone(PhoneUtil.normalize(req.getReceiverPhone().trim()));
        }
        if (req.getFirstName() != null || req.getLastName() != null) {
            String name = fullName(req.getLastName(), req.getFirstName());
            if (!name.isEmpty()) {
                order.setShipRecipientName(name);
            }
        }
        if (req.getProvinceName() != null && !req.getProvinceName().isBlank()) {
            order.setShipProvince(req.getProvinceName().trim());
        }
        if (req.getDistrictName() != null && !req.getDistrictName().isBlank()) {
            order.setShipDistrict(req.getDistrictName().trim());
        }
        if (req.getWardName() != null && !req.getWardName().isBlank()) {
            order.setShipWard(req.getWardName().trim());
        }
        if (req.getAddressLine() != null && !req.getAddressLine().isBlank()) {
            order.setShipAddressLine(req.getAddressLine().trim());
        }
        if (req.getShippingMethod() != null && !req.getShippingMethod().isBlank()) {
            order.setShippingMethod(req.getShippingMethod().trim());
        }
        if (req.getNote() != null) {
            order.setNote(req.getNote().isBlank() ? null : req.getNote().trim());
        }
        if (req.getContactEmail() != null && order.getUserId() == null) {
            order.setGuestEmail(req.getContactEmail().isBlank() ? null : req.getContactEmail().trim());
        }
        boolean itemsChanged = false;
        if (req.getItems() != null && !req.getItems().isEmpty()) {
            itemsChanged = replaceItems(order, adminQty(req));
        }
        order.touch();
        order.setUpdatedId(adminId);
        orders.update(order);
        orderTimelines.create(new OrderTimelineEntity(order.getId(), order.getStatus(),
                itemsChanged ? "Nhân viên chỉnh sửa thông tin và sản phẩm của đơn" : "Nhân viên chỉnh sửa thông tin đơn",
                "ADMIN"));
        return toDto(orders.findOne(order.getId()));
    }

    /** Đổi danh sách sản phẩm của đơn PENDING: nhả giữ chỗ cũ, giữ chỗ mới, tính lại tiền (voucher kiểm tra lại). */
    private boolean replaceItems(OrderEntity order, Map<Long, Integer> newQty) {
        List<OrderItemEntity> old = orderItems.findByOrderId(order.getId());
        Map<Long, Integer> current = new LinkedHashMap<>();
        for (OrderItemEntity it : old) {
            current.merge(it.getSkuId(), it.getQuantity(), Integer::sum);
        }
        if (current.equals(newQty)) {
            return false;
        }
        if (!OrderEntity.PENDING.equals(order.getStatus())) {
            throw BusinessException.conflict(ErrorCode.INVALID_STATUS_TRANSITION,
                    "Chỉ đổi sản phẩm khi đơn còn PENDING (hiện tại: " + order.getStatus() + ")",
                    order.getStatus(), order.getStatus());
        }
        for (OrderItemEntity it : old) {
            productSkus.releaseStock(it.getSkuId(), it.getQuantity());
            orderItems.delete(it);
        }
        LineBuild built = reserveLines(newQty);
        for (OrderItemEntity item : built.items()) {
            item.setOrderId(order.getId());
            orderItems.create(item);
        }
        BigDecimal voucherDiscount = BigDecimal.ZERO;
        String voucherCode = order.getAppliedVoucherCode();
        if (voucherCode != null && !voucherCode.isBlank()) {
            voucherService.releaseUsage(voucherCode);
            VoucherService.Applied applied = voucherService.apply(voucherCode, built.voucherLines());
            VoucherEntity v = applied.voucher();
            voucherDiscount = applied.discount();
            voucherService.applyUsage(v.getId(), v.getCode());
        }
        order.setSubtotal(built.subtotal());
        order.setProductDiscount(built.productDiscount());
        order.setVoucherDiscount(voucherDiscount);
        order.setGrandTotal(built.subtotal().subtract(built.productDiscount()).subtract(voucherDiscount)
                .add(order.getShippingFee() == null ? BigDecimal.ZERO : order.getShippingFee()));
        return true;
    }

    @Transactional
    @Override
    public OrderResponseDto confirmPaymentByAdmin(String code, String note) {
        OrderEntity order = adminOrder(code);
        if (OrderEntity.CANCELLED.equals(order.getStatus()) || OrderEntity.RETURNED.equals(order.getStatus())) {
            throw BusinessException.conflict(ErrorCode.INVALID_STATUS_TRANSITION,
                    "Không xác nhận thanh toán cho đơn " + order.getStatus(), order.getStatus(), OrderEntity.PAID);
        }
        if (!OrderEntity.UNPAID.equals(order.getPaymentStatus())) {
            throw BusinessException.conflict(ErrorCode.INVALID_STATUS_TRANSITION,
                    "Đơn đã ở trạng thái thanh toán " + order.getPaymentStatus(), order.getPaymentStatus(), OrderEntity.PAID);
        }
        order.setPaymentStatus(OrderEntity.PAID);
        order.touch();
        orders.update(order);
        orderTimelines.create(new OrderTimelineEntity(order.getId(), order.getStatus(),
                "Xác nhận đã thanh toán" + (note == null || note.isBlank() ? "" : ": " + note.trim()), "ADMIN"));
        return toDto(order);
    }

    @Transactional
    @Override
    public OrderResponseDto duplicateForAdmin(String sourceKey, boolean replaceSource, String cancelReason,
                                              String cancelNote, Long adminId) {
        OrderEntity src = adminOrder(sourceKey);
        Map<Long, Integer> qty = new LinkedHashMap<>();
        for (OrderItemEntity it : orderItems.findByOrderId(src.getId())) {
            qty.merge(it.getSkuId(), it.getQuantity(), Integer::sum);
        }
        if (replaceSource) {
            // Huỷ đơn nguồn trước để tồn kho giữ chỗ được trả lại cho đơn mới.
            String reason = nz(cancelReason, "Sao chép sang đơn mới")
                    + (cancelNote == null || cancelNote.isBlank() ? "" : " - " + cancelNote.trim());
            cancelByAdmin(src.getCode(), reason);
        }
        List<OrderItemRequestDto> items = new ArrayList<>();
        qty.forEach((sku, q) -> items.add(new OrderItemRequestDto(sku, q)));
        OrderResponseDto created = placeOrder(src.getUserId(), src.getGuestEmail(), null, shipSnapshot(src), "COD",
                src.getShippingMethod(), null, src.getNote(), items, new PlaceOpts("ADMIN", adminId, BigDecimal.ZERO));
        orderTimelines.create(new OrderTimelineEntity(created.getId(), OrderEntity.PENDING,
                "Sao chép từ đơn " + src.getCode(), "ADMIN"));
        return toDto(orders.findOne(created.getId()));
    }

    @Transactional
    @Override
    public OrderResponseDto createExchangeOrder(String sourceKey, Map<Long, Integer> qtyBySku, BigDecimal credit,
                                                String note, Long adminId) {
        OrderEntity src = adminOrder(sourceKey);
        List<OrderItemRequestDto> items = new ArrayList<>();
        qtyBySku.forEach((sku, q) -> items.add(new OrderItemRequestDto(sku, q)));
        OrderResponseDto created = placeOrder(src.getUserId(), src.getGuestEmail(), null, shipSnapshot(src), "COD",
                src.getShippingMethod(), null, note, items,
                new PlaceOpts("ADMIN", adminId, credit == null ? BigDecimal.ZERO : credit));
        return toDto(orders.findOne(created.getId()));
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
