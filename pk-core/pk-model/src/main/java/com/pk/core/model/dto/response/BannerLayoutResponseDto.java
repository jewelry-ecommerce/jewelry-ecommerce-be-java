package com.pk.core.model.dto.response;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.Date;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
public class BannerLayoutResponseDto {

    private String id;
    private String placementId;
    private String name;
    private String status;
    private Date activeFrom;
    private Date activeTo;
    private String note;
    /** Phiên bản tăng dần trong vị trí; layout mới nhất có revision lớn nhất. */
    private int revision;
    private Date createdDate;
    private Date updatedDate;
    private List<BannerLayoutItemResponseDto> items;
}
