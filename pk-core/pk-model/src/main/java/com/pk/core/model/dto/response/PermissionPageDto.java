package com.pk.core.model.dto.response;

import com.pk.core.model.constant.PermissionPageCatalog;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class PermissionPageDto {

    private String code;
    private String label;
    private String menuCode;
    private String tabCode;

    public static PermissionPageDto from(PermissionPageCatalog.Page page) {
        return new PermissionPageDto(page.code(), page.label(), page.menuCode(), page.tabCode());
    }
}
