package com.pk.core.business.service;

import com.pk.core.common.web.PageResponse;
import com.pk.core.model.dto.request.AdminSiteFooterRequestDto;
import com.pk.core.model.dto.request.AdminSiteHeaderRequestDto;
import com.pk.core.model.dto.request.AdminSiteLogoRequestDto;
import com.pk.core.model.dto.request.AdminSiteStatusRequestDto;
import com.pk.core.model.dto.response.SiteLayoutResponseDto;

/** Header (thanh thông báo), footer và logo của site: CRUD cho admin + dữ liệu công khai cho storefront. */
public interface SiteLayoutService {

    // ----- header -----
    PageResponse<SiteLayoutResponseDto.Header> listHeaders(String search, String status, int page, int take);

    SiteLayoutResponseDto.Header headerDetail(Long id);

    /** Tạo header. Trùng lịch với header ACTIVE/SCHEDULED khác: 409 HEADER_SCHEDULE_CONFLICT, trừ khi confirmReplaceActive=true (các header trùng chuyển DEACTIVE). */
    SiteLayoutResponseDto.Header createHeader(AdminSiteHeaderRequestDto req);

    SiteLayoutResponseDto.Header updateHeader(Long id, AdminSiteHeaderRequestDto req);

    SiteLayoutResponseDto.Header updateHeaderStatus(Long id, AdminSiteStatusRequestDto req);

    void deleteHeader(Long id);

    // ----- footer (một bản duy nhất) -----
    SiteLayoutResponseDto.Footer footer();

    SiteLayoutResponseDto.Footer saveFooter(AdminSiteFooterRequestDto req);

    // ----- logo -----
    PageResponse<SiteLayoutResponseDto.Logo> listLogos(String search, String status, String type, int page, int take);

    SiteLayoutResponseDto.Logo logoDetail(Long id);

    SiteLayoutResponseDto.Logo createLogo(AdminSiteLogoRequestDto req);

    SiteLayoutResponseDto.Logo updateLogo(Long id, AdminSiteLogoRequestDto req);

    SiteLayoutResponseDto.Logo updateLogoStatus(Long id, AdminSiteStatusRequestDto req);

    void archiveLogo(Long id);

    // ----- public -----
    SiteLayoutResponseDto.SiteLayout publicLayout();
}
