package com.pk.core.model.dto.request;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** POST /storefront/ai/stylist/feedback — bắt buộc đăng nhập. */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class StylistFeedbackRequestDto {

    @NotNull
    private Long sessionId;

    @NotNull
    private Long productId;

    @NotNull
    private Boolean liked;
}
