package com.pk.core.model.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

/** Câu khách nhắn cho trợ lý trang sức. */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class AssistantMessageRequestDto {

    @NotBlank
    @Size(max = 500)
    private String message;

    /** Các bong bóng trước câu đang hỏi. Null nghĩa là không có. */
    @Size(max = AssistantHistoryLimits.MAX_ITEMS)
    private List<@NotNull @Valid AssistantHistoryItemDto> history;
}
