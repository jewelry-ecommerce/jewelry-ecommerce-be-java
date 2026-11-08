package com.pk.core.model.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record MomoStartRequestDto(@NotBlank @Size(max = 100) String orderCode,
                                  @Size(max = 30) String phone) {}
