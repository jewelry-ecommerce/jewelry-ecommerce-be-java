package com.pk.core.model.dto.request;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Một dòng giá trị thuộc tính. `id` có (giá trị đã lưu) -> sửa; không có -> thêm. Mã giá trị đã lưu bị khoá. */
@Getter
@Setter
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class AdminAttributeValueRequestDto {

    private Long id;

    @Size(max = 255)
    private String code;

    /** Tên hiển thị của giá trị (FE cũng gửi dưới tên `name`/`label`). */
    @JsonAlias({"name", "label"})
    @Size(max = 255)
    private String value;

    @Size(max = 500)
    private String imageUrl;
}
