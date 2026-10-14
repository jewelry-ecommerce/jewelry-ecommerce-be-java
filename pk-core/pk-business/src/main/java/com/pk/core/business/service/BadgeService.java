package com.pk.core.business.service;

import com.pk.core.model.dto.request.AdminBadgeFlowRequestDto;
import com.pk.core.model.dto.request.AdminBadgeTemplateRequestDto;
import com.pk.core.model.dto.response.BadgeFlowPageResponseDto;
import com.pk.core.model.dto.response.BadgeFlowResponseDto;
import com.pk.core.model.dto.response.BadgeTemplatePageResponseDto;
import com.pk.core.model.dto.response.BadgeTemplateResponseDto;
import com.pk.core.model.dto.response.SkuBadgeResponseDto;

import java.util.List;

/**
 * Nhãn dán sản phẩm: admin cấu hình mẫu nhãn (badge-templates) và luồng hiển thị (badge-flow); storefront đọc đúng cấu
 * hình đó để gắn nhãn cho SKU.
 * <p>Quy tắc đã chốt (mỗi SKU TỐI ĐA 1 nhãn): PRE_ORDER -> OUT_OF_STOCK -> CAMPAIGN.
 * (1) SKU hết hàng: nếu đặt trước đang bật thì nhãn PRE_ORDER, ngược lại OUT_OF_STOCK (mẫu lấy từ flow OUT_OF_STOCK
 * đang hiệu lực, không có thì lấy mẫu ACTIVE cùng badgeType). (2) Còn lại: các flow đang hiệu lực (ACTIVE, trong thời
 * gian, đúng kênh) khớp SKU theo ruleType (ALL/MANUAL/CATEGORY/COLLECTION/PROMOTION) - chọn mẫu ghim trước, rồi theo
 * priorityWeight giảm dần.</p>
 */
public interface BadgeService {

    /** GET /storefront/badge?skuIds=... - mỗi SKU tồn tại có 1 phần tử, `badges` rỗng nếu không khớp luật nào. */
    List<SkuBadgeResponseDto> forSkus(List<String> skuIds, String channel);

    default BadgeTemplatePageResponseDto findTemplates(int page, int take, String status, String type, String badgeType,
                                                       String defaultPosition) {
        return findTemplates(page, take, status, type, badgeType, defaultPosition, null);
    }

    /** `search` lọc theo tên hoặc mã template (không phân biệt hoa thường). */
    BadgeTemplatePageResponseDto findTemplates(int page, int take, String status, String type, String badgeType,
                                               String defaultPosition, String search);

    BadgeTemplateResponseDto findTemplateById(String badgeId);

    BadgeTemplateResponseDto createTemplate(AdminBadgeTemplateRequestDto req);

    BadgeTemplateResponseDto updateTemplate(String badgeId, AdminBadgeTemplateRequestDto req);

    void deleteTemplate(String badgeId);

    default BadgeFlowPageResponseDto findFlows(int page, int take, String status) {
        return findFlows(page, take, status, null, null);
    }

    /** `search`: tên/mô tả chiến dịch; `displayStatus`: DRAFT | PAUSED | SCHEDULED | RUNNING | ENDED. counts tính trên tập đã lọc `search`. */
    BadgeFlowPageResponseDto findFlows(int page, int take, String status, String search, String displayStatus);

    BadgeFlowResponseDto createFlow(AdminBadgeFlowRequestDto req);

    BadgeFlowResponseDto updateFlow(String flowId, AdminBadgeFlowRequestDto req);

    /** Xoá luồng: 409 BADGE_FLOW_LOCKED nếu đang SCHEDULED/RUNNING (phải tạm dừng trước). */
    void deleteFlow(String flowId);
}
