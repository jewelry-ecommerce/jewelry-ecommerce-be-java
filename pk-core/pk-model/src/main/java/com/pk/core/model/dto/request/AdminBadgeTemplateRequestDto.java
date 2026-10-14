package com.pk.core.model.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import jakarta.validation.groups.Default;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.Map;

/**
 * Body tạo (POST) và cập nhật một phần (PATCH) mẫu nhãn. Tạo dùng nhóm {@link OnCreate} (bắt buộc name, code, type,
 * badgeType); PATCH dùng nhóm mặc định - field null thì giữ nguyên, riêng code KHÔNG sửa qua PATCH (spec). Xoá một
 * field asset (icon/image/iconMobile/imageMobile): gửi chuỗi rỗng ""; assetMeta: gửi {}.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class AdminBadgeTemplateRequestDto {

    public interface OnCreate extends Default {
    }

    @NotBlank(groups = OnCreate.class)
    @Size(max = 120)
    private String name;

    @NotBlank(groups = OnCreate.class)
    @Size(max = 60)
    private String code;

    @Size(max = 500)
    private String description;

    @NotBlank(groups = OnCreate.class)
    private String type;

    @NotBlank(groups = OnCreate.class)
    private String badgeType;

    private String status;

    @Size(max = 60)
    private String displayText;

    private String defaultPosition;

    private Map<String, Object> styleConfig;

    @Size(max = 255)
    private String icon;

    @Size(max = 500)
    private String image;

    @Size(max = 255)
    private String iconMobile;

    @Size(max = 500)
    private String imageMobile;

    private Map<String, Object> assetMeta;

    private Integer defaultPriorityWeight;
}
