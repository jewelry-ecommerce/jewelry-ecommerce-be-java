package com.pk.core.business.service;

import com.pk.core.model.dto.request.AdminBannerRequestDto;
import com.pk.core.model.dto.response.BannerPageResponseDto;
import com.pk.core.model.dto.response.BannerPlacementRenderResponseDto;
import com.pk.core.model.dto.response.BannerResponseDto;

/** Banner theo spec FE (nhóm Banner). Thêm 2026-10-06. */
public interface BannerService {

    /**
     * GET /admin/banner/banners - danh sách phân trang, mới nhất trước. Lọc (tuỳ chọn, rỗng/null = không lọc): status,
     * mediaType, actionType (LINK khớp cả URL/LINK), internalName (chứa, không phân biệt hoa thường). Lọc/cắt trang
     * bằng Java như ProductServiceImpl (quy mô đồ án nhỏ). page 1-based, take mặc định 20 tối đa 100.
     */
    BannerPageResponseDto findAll(int page, int take, String status, String mediaType, String actionType,
                                  String internalName);

    /** GET /admin/banner/banners/{id} - 404 nếu không có hoặc đã xoá mềm. */
    BannerResponseDto findById(Long id);

    /** POST /admin/banner/banners - tạo banner. placementCode (nếu có) phải là vị trí tồn tại, nếu không 404. */
    BannerResponseDto create(AdminBannerRequestDto req);

    /** PATCH /admin/banner/banners/{id} - cập nhật một phần: chỉ field khác null được ghi. */
    BannerResponseDto update(Long id, AdminBannerRequestDto req);

    /** DELETE /admin/banner/banners/{id} - xoá MỀM. */
    void delete(Long id);

    /** GET /storefront/banner/placements/code/{code}/render - banner ACTIVE của vị trí; mã lạ -> 404. */
    default BannerPlacementRenderResponseDto renderByPlacementCode(String code) {
        return renderByPlacementCode(code, null);
    }

    /**
     * Như trên; platform (WEB | APP, null = không lọc): vị trí có platform khác (không phải ALL) hoặc status INACTIVE
     * thì trả về không có banner/slot. Vị trí có layout -> slot thật từ layout hiệu lực; chưa có layout -> một slot "main".
     */
    BannerPlacementRenderResponseDto renderByPlacementCode(String code, String platform);
}
