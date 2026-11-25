package com.pk.core.model.dto.response;

import com.pk.core.model.entity.UserPagePermissionEntity;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class PagePermissionItemDto {

    private String pageCode;
    private boolean view;
    private boolean edit;
    private boolean delete;

    public static PagePermissionItemDto from(UserPagePermissionEntity e) {
        return new PagePermissionItemDto(e.getPageCode(), e.isCanView(), e.isCanEdit(), e.isCanDelete());
    }
}
