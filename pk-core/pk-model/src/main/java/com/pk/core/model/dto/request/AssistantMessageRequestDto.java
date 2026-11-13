package com.pk.core.model.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Câu khách nhắn cho trợ lý trang sức. */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class AssistantMessageRequestDto {

    @NotBlank
    @Size(max = 500)
    private String message;
}
