package com.pk.core.model.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.Map;

@Getter
@Setter
@NoArgsConstructor
public class BannerLayoutItemResponseDto {

    private String id;
    private String layoutId;
    private String slotKey;
    private String bannerId;
    @JsonProperty("isActive")
    private Boolean isActive;
    private int priority;
    private Map<String, Object> displayMetadata;
    /** Banner đầy đủ để builder xem trước; null nếu banner đã bị xoá mềm. */
    private BannerResponseDto banner;
}
