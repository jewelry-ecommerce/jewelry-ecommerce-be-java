package com.pk.core.model.dto.request;

import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.Map;

/** Tạo/sửa vị trí banner. Khi sửa chỉ đổi name/displayType/platform/status/settings (code là khoá nên không đổi). */
@Getter
@Setter
@NoArgsConstructor
public class AdminBannerPlacementRequestDto {

    /** Chữ/số/gạch dưới, ví dụ HOME_HERO; tự chuyển IN HOA. Bỏ trống khi tạo = tự sinh từ name. Bỏ qua khi sửa. */
    @Size(max = 60)
    private String code;
    @Size(max = 150)
    private String name;
    /** SLIDER | CAROUSEL | GRID | DOUBLE_BANNER | POPUP | SINGLE (mặc định CAROUSEL khi tạo). */
    private String displayType;
    /** WEB | APP | ALL (mặc định ALL). */
    private String platform;
    /** ACTIVE | INACTIVE (mặc định ACTIVE). */
    private String status;
    /** {columns 1-12, rows 1-50, gap 0-100, mobileOddFullWidthItem FIRST|LAST}. Gửi {} để xoá cấu hình. */
    private Map<String, Object> settings;
}
