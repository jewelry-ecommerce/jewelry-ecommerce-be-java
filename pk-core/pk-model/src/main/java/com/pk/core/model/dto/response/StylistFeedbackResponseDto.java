package com.pk.core.model.dto.response;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class StylistFeedbackResponseDto {

    private Long sessionId;
    private Long productId;
    private boolean liked;
}
