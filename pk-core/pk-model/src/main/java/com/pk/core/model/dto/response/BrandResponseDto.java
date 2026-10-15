package com.pk.core.model.dto.response;

import com.pk.core.model.entity.BrandEntity;

import java.util.Date;

public record BrandResponseDto(Long id, String code, String name, String image, String websiteUrl, String status,
                               Date createdDate, Date updatedDate) {
    public static BrandResponseDto from(BrandEntity b) {
        return new BrandResponseDto(b.getId(), b.getCode(), b.getName(), b.getImageUrl(), b.getWebsiteUrl(),
                b.getStatus(), b.getCreatedDate(), b.getUpdatedDate());
    }
}
