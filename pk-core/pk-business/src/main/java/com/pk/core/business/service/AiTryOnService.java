package com.pk.core.business.service;

import com.pk.core.model.dto.response.TryOnResponseDto;
import org.springframework.web.multipart.MultipartFile;

/** Try-on lookalike: analyze ảnh → chọn 2–3 SP catalog → gen minh họa. */
public interface AiTryOnService {

    TryOnResponseDto tryOn(MultipartFile image, String styleHint);
}
