package com.pk.core.business.service;

import com.pk.core.model.dto.request.AdminCmsBlockRequestDto;
import com.pk.core.model.dto.request.AdminCmsPageRequestDto;
import com.pk.core.model.dto.response.CmsBlockResponseDto;
import com.pk.core.model.dto.response.CmsPageResponseDto;
import com.pk.core.model.dto.response.CmsStorefrontPageResponseDto;

import java.util.List;

/** Admin mục O spec (document/09-tong-hop-api-fe.md). */
public interface CmsService {

    List<CmsPageResponseDto> findAllPages();

    CmsPageResponseDto findPageById(Long pageId);

    /** GET /storefront/cms/pages/{slug} (công khai) - trang CMS theo slug kèm blocks (theo sortOrder). CHỈ trả trang
     * PUBLISHED và chưa xoá mềm; DRAFT/không có -> 404 (không lộ trang chưa xuất bản). Thêm 2026-10-06. */
    default CmsStorefrontPageResponseDto findPublishedBySlug(String slug) {
        return findPublishedBySlug(slug, null);
    }

    /** Như trên, có lọc theo thiết bị (DESKTOP|MOBILE; null = không lọc). Phục vụ BẢN PUBLIC (snapshot), kiểm tra lịch. */
    CmsStorefrontPageResponseDto findPublishedBySlug(String slug, String device);

    /** POST /admin/cms/pages/{pageId}/publish - chụp bản nháp thành bản public mới. */
    CmsPageResponseDto publishPage(Long pageId);

    /** POST /admin/cms/pages/{pageId}/unpublish - gỡ bản public (nháp giữ nguyên). */
    CmsPageResponseDto unpublishPage(Long pageId);

    /** DELETE /admin/cms/pages/{pageId} - xoá mềm trang. */
    void deletePage(Long pageId);

    /** POST /admin/cms/pages - tạo page, seed blocks ban đầu nếu request.blocks != null. */
    CmsPageResponseDto createPage(AdminCmsPageRequestDto req);

    /** PUT /admin/cms/pages/{pageId} - cập nhật thông tin page; nếu request.blocks != null thì THAY
     * TOÀN BỘ blocks hiện có (xoá hết rồi tạo lại theo danh sách mới). */
    CmsPageResponseDto updatePage(Long pageId, AdminCmsPageRequestDto req);

    /** POST /admin/cms/pages/{pageId}/blocks - thêm 1 block mới vào cuối trang. */
    CmsBlockResponseDto addBlock(Long pageId, AdminCmsBlockRequestDto req);

    /** PUT /admin/cms/pages/{pageId}/blocks/{blockId} - sửa 1 block đã có. */
    CmsBlockResponseDto updateBlock(Long pageId, Long blockId, AdminCmsBlockRequestDto req);

    /** DELETE /admin/cms/pages/{pageId}/blocks/{blockId} - xoá (HARD delete) 1 block. */
    void deleteBlock(Long pageId, Long blockId);
}
