package com.pk.core.business.service;

import com.pk.core.model.dto.response.DashboardResponseDto;

import java.util.List;

/** Tổng hợp số liệu dashboard admin từ DB thật. `range`: LAST_7_DAYS | LAST_30_DAYS | LAST_12_MONTHS (mặc định 30 ngày). */
public interface DashboardService {

    DashboardResponseDto.Overview overview(String range);

    DashboardResponseDto.SeriesSet revenue(String range);

    List<DashboardResponseDto.RecentOrder> recentOrders(int limit);

    List<DashboardResponseDto.LowStockRow> lowStock();

    List<DashboardResponseDto.TopProductRow> topProducts(String range, int limit);
}
