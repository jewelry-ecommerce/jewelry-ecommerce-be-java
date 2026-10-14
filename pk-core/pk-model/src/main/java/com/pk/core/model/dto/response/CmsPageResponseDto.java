package com.pk.core.model.dto.response;

import com.pk.core.common.dto.BaseDto;
import com.pk.core.model.entity.CmsPageEntity;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.Date;
import java.util.List;
import java.util.Map;

/** Trang CMS cho admin: {id, name, slug, status, blocks} của BẢN NHÁP, kèm thông tin bản đang public.
 * `status` DRAFT|PUBLISHED = có/không có bản đang public. `displayStatus` (tính theo thời điểm + lịch của bản public):
 * DRAFT | PAUSED (isActive=false) | SCHEDULED | RUNNING | ENDED. `live` = {versionId, publishedAt, slug, targetDevice,
 * scheduleStart, scheduleEnd} của bản public (null nếu chưa xuất bản). */
@Getter
@Setter
@NoArgsConstructor
public class CmsPageResponseDto extends BaseDto {

    private String id;
    private String name;
    private String slug;
    private String status;
    private List<CmsBlockResponseDto> blocks;
    private String locale;
    private boolean active;
    private Map<String, Object> seo;

    private String targetDevice = "ALL";
    private Date scheduleStart;
    private Date scheduleEnd;
    private String displayStatus = "DRAFT";
    private boolean hasUnpublishedChanges;
    private int versionId;
    private Date publishedAt;
    private Map<String, Object> live;

    /** Chữ ký cũ (8 tham số). */
    public CmsPageResponseDto(String id, String name, String slug, String status, List<CmsBlockResponseDto> blocks,
                              String locale, boolean active, Map<String, Object> seo) {
        this.id = id;
        this.name = name;
        this.slug = slug;
        this.status = status;
        this.blocks = blocks;
        this.locale = locale;
        this.active = active;
        this.seo = seo;
    }

    public static CmsPageResponseDto from(CmsPageEntity e, List<CmsBlockResponseDto> blocks, Map<String, Object> seo) {
        CmsPageResponseDto dto = BaseDto.of(new CmsPageResponseDto(String.valueOf(e.getId()), e.getTitle(), e.getSlug(),
                e.getStatus(), blocks, e.getLocale(), e.isActive(), seo), e);
        dto.targetDevice = e.getTargetDevice() == null ? "ALL" : e.getTargetDevice();
        dto.scheduleStart = e.getScheduleStart();
        dto.scheduleEnd = e.getScheduleEnd();
        dto.versionId = e.getPublishedVersion();
        dto.publishedAt = e.getPublishedAt();
        return dto;
    }

    public boolean getIsActive() {
        return active;
    }
}
