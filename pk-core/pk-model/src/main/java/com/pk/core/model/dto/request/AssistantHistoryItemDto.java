package com.pk.core.model.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Một bong bóng đã có, để model nhớ vài lượt gần nhất. */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class AssistantHistoryItemDto {

    @NotBlank
    @Pattern(regexp = "user|assistant")
    private String role;

    @NotBlank
    @Size(max = AssistantHistoryLimits.MAX_CONTENT)
    private String content;
}
