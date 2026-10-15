package com.pk.core.business.service.impl;

import com.pk.core.business.repository.CategoryRepo;
import com.pk.core.business.repository.OrderItemRepo;
import com.pk.core.business.repository.OrderRepo;
import com.pk.core.business.repository.ProductRepo;
import com.pk.core.business.repository.ProductSkuRepo;
import com.pk.core.business.repository.UserRepo;
import com.pk.core.business.service.DashboardService;
import com.pk.core.common.exception.BusinessException;
import com.pk.core.common.exception.ErrorCode;
import com.pk.core.model.dto.response.DashboardResponseDto;
import com.pk.core.model.dto.response.DashboardResponseDto.Kpi;
import com.pk.core.model.dto.response.DashboardResponseDto.LowStockRow;
import com.pk.core.model.dto.response.DashboardResponseDto.Overview;
import com.pk.core.model.dto.response.DashboardResponseDto.RecentOrder;
import com.pk.core.model.dto.response.DashboardResponseDto.Series;
import com.pk.core.model.dto.response.DashboardResponseDto.SeriesSet;
import com.pk.core.model.dto.response.DashboardResponseDto.StatusCount;
import com.pk.core.model.dto.response.DashboardResponseDto.TopProductRow;
import com.pk.core.model.entity.CategoryEntity;
import com.pk.core.model.entity.OrderEntity;
import com.pk.core.model.entity.OrderItemEntity;
import com.pk.core.model.entity.ProductEntity;
import com.pk.core.model.entity.ProductSkuEntity;
import com.pk.core.model.entity.UserEntity;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Số liệu dashboard tổng hợp trong Java trên dữ liệu thật (quy mô đồ án; cùng kiểu với OrderServiceImpl/InventoryServiceImpl).
 * Doanh thu ròng = tổng (grandTotal - refundedAmount) của đơn đã xác nhận trở đi (CONFIRMED/SHIPPING/DELIVERED), tính theo
 * ngày đặt (placedAt, múi giờ hệ thống). Kỳ trước = khoảng liền kề cùng độ dài, dùng cho deltaPercent.
 * Kênh đơn: hệ thống chỉ bán online nên channel luôn "ONLINE".
 */
@Service
@RequiredArgsConstructor
public class DashboardServiceImpl implements DashboardService {

    static final int LOW_STOCK_THRESHOLD = 5; // trùng ngưỡng ProductSkuEntity.stockStatus()
    private static final Set<String> REVENUE_STATUSES = Set.of(OrderEntity.CONFIRMED, OrderEntity.SHIPPING, OrderEntity.DELIVERED);
    private static final ZoneId ZONE = ZoneId.systemDefault();

    private final OrderRepo orders;
    private final OrderItemRepo orderItems;
    private final ProductRepo products;
    private final CategoryRepo categories;
    private final ProductSkuRepo skus;
    private final UserRepo users;

    private enum Range { LAST_7_DAYS, LAST_30_DAYS, LAST_12_MONTHS }

    private static Range parse(String range) {
        if (range == null || range.isBlank()) {
            return Range.LAST_30_DAYS;
        }
        try {
            return Range.valueOf(range.trim().toUpperCase(java.util.Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw BusinessException.badRequest(ErrorCode.VALIDATION_FAILED,
                    "range chỉ nhận LAST_7_DAYS | LAST_30_DAYS | LAST_12_MONTHS", range);
        }
    }

    /** [from, toExclusive) của kỳ hiện tại; 12 tháng tính theo tháng lịch (tháng hiện tại là điểm cuối). */
    private static LocalDate[] window(Range r, LocalDate today) {
        return switch (r) {
            case LAST_7_DAYS -> new LocalDate[]{today.minusDays(6), today.plusDays(1)};
            case LAST_30_DAYS -> new LocalDate[]{today.minusDays(29), today.plusDays(1)};
            case LAST_12_MONTHS -> new LocalDate[]{YearMonth.from(today).minusMonths(11).atDay(1), today.plusDays(1)};
        };
    }

    private static LocalDate day(Date d) {
        return d.toInstant().atZone(ZONE).toLocalDate();
    }

    private static boolean inRange(OrderEntity o, LocalDate from, LocalDate toExclusive) {
        if (o.getPlacedAt() == null) {
            return false;
        }
        LocalDate d = day(o.getPlacedAt());
        return !d.isBefore(from) && d.isBefore(toExclusive);
    }

    private static double net(OrderEntity o) {
        BigDecimal total = o.getGrandTotal() == null ? BigDecimal.ZERO : o.getGrandTotal();
        BigDecimal refunded = o.getRefundedAmount() == null ? BigDecimal.ZERO : o.getRefundedAmount();
        return total.subtract(refunded).max(BigDecimal.ZERO).doubleValue();
    }

    private static boolean isRevenue(OrderEntity o) {
        return REVENUE_STATUSES.contains(o.getStatus());
    }

    @Transactional(readOnly = true)
    @Override
    public Overview overview(String range) {
        Range r = parse(range);
        LocalDate today = LocalDate.now(ZONE);
        LocalDate[] w = window(r, today);
        long days = w[1].toEpochDay() - w[0].toEpochDay();
        LocalDate prevFrom = r == Range.LAST_12_MONTHS ? w[0].minusYears(1) : w[0].minusDays(days);
        List<OrderEntity> all = orders.findAll(Sort.unsorted());

        List<OrderEntity> cur = all.stream().filter(o -> inRange(o, w[0], w[1])).toList();
        List<OrderEntity> prev = all.stream().filter(o -> inRange(o, prevFrom, w[0])).toList();
        double rev = cur.stream().filter(DashboardServiceImpl::isRevenue).mapToDouble(DashboardServiceImpl::net).sum();
        double prevRev = prev.stream().filter(DashboardServiceImpl::isRevenue).mapToDouble(DashboardServiceImpl::net).sum();
        long cnt = cur.stream().filter(DashboardServiceImpl::isRevenue).count();
        long prevCnt = prev.stream().filter(DashboardServiceImpl::isRevenue).count();
        double aov = cnt == 0 ? 0 : Math.round(rev / cnt);
        double prevAov = prevCnt == 0 ? 0 : Math.round(prevRev / prevCnt);

        List<UserEntity> customers = users.searchCustomers("");
        long newCust = customers.stream().filter(u -> u.getCreatedDate() != null && !day(u.getCreatedDate()).isBefore(w[0])
                && day(u.getCreatedDate()).isBefore(w[1])).count();
        long prevNewCust = customers.stream().filter(u -> u.getCreatedDate() != null && !day(u.getCreatedDate()).isBefore(prevFrom)
                && day(u.getCreatedDate()).isBefore(w[0])).count();

        List<Kpi> kpis = List.of(
                kpi("net_revenue", "Net revenue", rev, prevRev, "CURRENCY", "Confirmed through delivered orders, minus refunds"),
                kpi("order_count", "Orders", cnt, prevCnt, "NUMBER", "Confirmed through delivered orders"),
                kpi("average_order_value", "Average order value", aov, prevAov, "CURRENCY", "Net revenue divided by orders"),
                kpi("new_customers", "New customers", newCust, prevNewCust, "NUMBER", "Accounts registered in this period"));

        Map<String, Long> byStatus = new LinkedHashMap<>();
        cur.stream().sorted(Comparator.comparing(OrderEntity::getStatus))
                .forEach(o -> byStatus.merge(o.getStatus(), 1L, Long::sum));
        List<StatusCount> statusCounts = byStatus.entrySet().stream().map(e -> new StatusCount(e.getKey(), e.getValue())).toList();

        return new Overview(kpis, series(r, w, cur), statusCounts, recent(all, 8), lowStockRows(), top(cur, 5));
    }

    private static Kpi kpi(String key, String label, double value, double previous, String format, String caption) {
        Double delta = previous == 0 ? null : Math.round((value - previous) / previous * 1000.0) / 10.0;
        String tone = delta == null ? "neutral" : (delta > 0 ? "success" : (delta < 0 ? "danger" : "neutral"));
        return new Kpi(key, label, value, delta, tone, format, caption);
    }

    private SeriesSet series(Range r, LocalDate[] w, List<OrderEntity> cur) {
        List<String> labels = new ArrayList<>();
        List<Double> revenue = new ArrayList<>();
        List<Double> counts = new ArrayList<>();
        if (r == Range.LAST_12_MONTHS) {
            for (YearMonth m = YearMonth.from(w[0]); m.isBefore(YearMonth.from(w[1].minusDays(1)).plusMonths(1)); m = m.plusMonths(1)) {
                final YearMonth ym = m;
                labels.add(ym.toString());
                List<OrderEntity> in = cur.stream().filter(DashboardServiceImpl::isRevenue)
                        .filter(o -> YearMonth.from(day(o.getPlacedAt())).equals(ym)).toList();
                revenue.add(in.stream().mapToDouble(DashboardServiceImpl::net).sum());
                counts.add((double) in.size());
            }
        } else {
            for (LocalDate d = w[0]; d.isBefore(w[1]); d = d.plusDays(1)) {
                final LocalDate dd = d;
                labels.add(dd.toString());
                List<OrderEntity> in = cur.stream().filter(DashboardServiceImpl::isRevenue)
                        .filter(o -> day(o.getPlacedAt()).equals(dd)).toList();
                revenue.add(in.stream().mapToDouble(DashboardServiceImpl::net).sum());
                counts.add((double) in.size());
            }
        }
        return new SeriesSet(labels, List.of(new Series("Revenue", revenue), new Series("Orders", counts)));
    }

    private List<RecentOrder> recent(List<OrderEntity> all, int limit) {
        return all.stream().sorted(Comparator.comparing(OrderEntity::getPlacedAt, Comparator.nullsLast(Comparator.reverseOrder())))
                .limit(Math.max(1, Math.min(limit, 50)))
                .map(o -> new RecentOrder(o.getCode(), o.getShipRecipientName(), o.getStatus(), o.getPaymentStatus(), "ONLINE",
                        o.getGrandTotal() == null ? 0 : o.getGrandTotal().doubleValue(),
                        o.getPlacedAt() == null ? null : o.getPlacedAt().toInstant().toString()))
                .toList();
    }

    private List<LowStockRow> lowStockRows() {
        Map<Long, String> productNames = new HashMap<>();
        products.findAll(Sort.unsorted()).forEach(p -> productNames.put(p.getId(), p.getName()));
        return skus.findAll(Sort.unsorted()).stream()
                .filter(s -> s.available() <= LOW_STOCK_THRESHOLD)
                .sorted(Comparator.comparingInt(ProductSkuEntity::available).thenComparing(ProductSkuEntity::getSkuCode))
                .limit(8)
                .map(s -> new LowStockRow(s.getSkuCode(), productNames.getOrDefault(s.getProductId(), s.getName()),
                        s.getName() == null ? "" : s.getName(), InventoryServiceImpl.MAIN_WAREHOUSE_ID, s.available(),
                        LOW_STOCK_THRESHOLD))
                .toList();
    }

    private List<TopProductRow> top(List<OrderEntity> inWindow, int limit) {
        record Acc(String name, String image, long units, double revenue) { }
        Map<Long, Acc> acc = new LinkedHashMap<>();
        for (OrderEntity o : inWindow) {
            if (!isRevenue(o)) {
                continue;
            }
            for (OrderItemEntity it : orderItems.findByOrderId(o.getId())) {
                acc.merge(it.getProductId(), new Acc(it.getName(), it.getImageUrl(), it.getQuantity(),
                                it.getLineTotal() == null ? 0 : it.getLineTotal().doubleValue()),
                        (a, b) -> new Acc(a.name(), a.image(), a.units() + b.units(), a.revenue() + b.revenue()));
            }
        }
        Map<Long, ProductEntity> prod = new HashMap<>();
        products.findAll(Sort.unsorted()).forEach(p -> prod.put(p.getId(), p));
        Map<Long, String> cat = new HashMap<>();
        for (CategoryEntity c : categories.findAll(Sort.unsorted())) {
            cat.put(c.getId(), c.getName());
        }
        return acc.entrySet().stream()
                .sorted((a, b) -> Double.compare(b.getValue().revenue(), a.getValue().revenue()))
                .limit(Math.max(1, Math.min(limit, 50)))
                .map(e -> {
                    ProductEntity p = prod.get(e.getKey());
                    String category = p == null ? "—" : cat.getOrDefault(p.getCategoryId(), "—");
                    return new TopProductRow(String.valueOf(e.getKey()), p != null ? p.getName() : e.getValue().name(), category,
                            e.getValue().image() == null ? "" : e.getValue().image(), e.getValue().units(), e.getValue().revenue());
                }).toList();
    }

    @Transactional(readOnly = true)
    @Override
    public SeriesSet revenue(String range) {
        Range r = parse(range);
        LocalDate[] w = window(r, LocalDate.now(ZONE));
        return series(r, w, orders.findAll(Sort.unsorted()).stream().filter(o -> inRange(o, w[0], w[1])).toList());
    }

    @Transactional(readOnly = true)
    @Override
    public List<RecentOrder> recentOrders(int limit) {
        return recent(orders.findAll(Sort.unsorted()), limit);
    }

    @Transactional(readOnly = true)
    @Override
    public List<LowStockRow> lowStock() {
        return lowStockRows();
    }

    @Transactional(readOnly = true)
    @Override
    public List<TopProductRow> topProducts(String range, int limit) {
        Range r = parse(range);
        LocalDate[] w = window(r, LocalDate.now(ZONE));
        return top(orders.findAll(Sort.unsorted()).stream().filter(o -> inRange(o, w[0], w[1])).toList(), limit);
    }
}
