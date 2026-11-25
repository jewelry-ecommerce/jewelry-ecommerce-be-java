package com.pk.core.business.service.impl;

import com.pk.core.business.ai.AiCandidate;
import com.pk.core.business.ai.AiProperties;
import com.pk.core.business.ai.AiRecommendationOrchestrator;
import com.pk.core.business.ai.tryon.GeminiTryOnClient;
import com.pk.core.business.ai.tryon.TryOnAnalysis;
import com.pk.core.business.ai.tryon.TryOnBodyRegion;
import com.pk.core.business.ai.tryon.TryOnCatalogPicker;
import com.pk.core.business.ai.tryon.TryOnImageFetcher;
import com.pk.core.business.service.AiTryOnService;
import com.pk.core.model.dto.response.TryOnResponseDto;
import com.pk.core.model.dto.response.TryOnVariantResponseDto;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Orchestrate try-on: validate → vision → pick catalog → gen song song (bounded). */
@Service
public class AiTryOnServiceImpl implements AiTryOnService {

    private final AiProperties properties;
    private final AiRecommendationOrchestrator orchestrator;
    private final GeminiTryOnClient gemini;
    private final TryOnImageFetcher imageFetcher;
    private final UploadStorage uploadStorage;

    public AiTryOnServiceImpl(AiProperties properties,
                              AiRecommendationOrchestrator orchestrator,
                              GeminiTryOnClient gemini,
                              TryOnImageFetcher imageFetcher,
                              UploadStorage uploadStorage) {
        this.properties = properties;
        this.orchestrator = orchestrator;
        this.gemini = gemini;
        this.imageFetcher = imageFetcher;
        this.uploadStorage = uploadStorage;
    }

    @Override
    public TryOnResponseDto tryOn(MultipartFile image, String styleHint) {
        if (!properties.providerReady()) {
            return TryOnResponseDto.fail("AI chưa sẵn sàng");
        }
        String validationError = validateImage(image);
        if (validationError != null) {
            return TryOnResponseDto.fail(validationError);
        }
        byte[] userBytes;
        try {
            userBytes = image.getBytes();
        } catch (IOException ex) {
            return TryOnResponseDto.fail("Không đọc được ảnh tải lên");
        }
        String userMime = normalizeMime(image.getContentType());
        TryOnAnalysis analysis = gemini.analyze(userBytes, userMime, styleHint);
        if (!analysis.usable()) {
            String reason = analysis.rejectReason() == null
                    ? "Không nhận diện được bộ phận đeo trang sức trên ảnh"
                    : analysis.rejectReason();
            return TryOnResponseDto.fail(reason);
        }
        return buildVariants(userBytes, userMime, analysis);
    }

    private TryOnResponseDto buildVariants(byte[] userBytes, String userMime, TryOnAnalysis analysis) {
        List<AiCandidate> catalog = orchestrator.openCatalog();
        List<TryOnCatalogPicker.Picked> picks = TryOnCatalogPicker.pick(
                catalog, analysis.regions(), analysis.styles(), properties.getTryOnMaxVariants());
        if (picks.isEmpty()) {
            return TryOnResponseDto.fail("Catalog chưa có sản phẩm phù hợp với vùng nhận diện");
        }
        List<TryOnVariantResponseDto> variants = generateAll(userBytes, userMime, picks);
        if (variants.isEmpty()) {
            return TryOnResponseDto.fail("Không tạo được ảnh đeo thử (ghép trang sức lên ảnh của bạn), thử lại sau");
        }
        return TryOnResponseDto.success(analysis.regions(), variants);
    }

    private List<TryOnVariantResponseDto> generateAll(byte[] userBytes, String userMime,
                                                      List<TryOnCatalogPicker.Picked> picks) {
        ExecutorService pool = Executors.newFixedThreadPool(Math.min(3, picks.size()));
        try {
            List<CompletableFuture<TryOnVariantResponseDto>> futures = picks.stream()
                    .map(pick -> CompletableFuture.supplyAsync(
                            () -> oneVariant(userBytes, userMime, pick), pool))
                    .toList();
            CompletableFuture.allOf(futures.toArray(CompletableFuture[]::new)).join();
            List<TryOnVariantResponseDto> result = new ArrayList<>();
            for (CompletableFuture<TryOnVariantResponseDto> future : futures) {
                TryOnVariantResponseDto variant = future.join();
                if (variant != null) {
                    result.add(variant);
                }
            }
            return result;
        } finally {
            pool.shutdown();
        }
    }

    private TryOnVariantResponseDto oneVariant(byte[] userBytes, String userMime,
                                               TryOnCatalogPicker.Picked pick) {
        AiCandidate product = pick.product();
        // Không fallback thumbnail catalog — try-on phải là ảnh ghép AI trên ảnh khách.
        String tryOnUrl = generateAndStore(userBytes, userMime, pick);
        if (tryOnUrl == null || tryOnUrl.isBlank()) {
            return null;
        }
        return TryOnVariantResponseDto.builder()
                .style(pick.style())
                .styleLabel(TryOnBodyRegion.styleLabel(pick.style()))
                .tryOnImageUrl(tryOnUrl)
                .productId(product.productId())
                .productName(product.name())
                .productSlug(product.slug())
                .productThumbnailUrl(product.thumbnailUrl())
                .price(product.price())
                .categoryName(product.categoryName())
                .build();
    }

    private String generateAndStore(byte[] userBytes, String userMime, TryOnCatalogPicker.Picked pick) {
        TryOnImageFetcher.Fetched productImage = imageFetcher.fetch(pick.product().thumbnailUrl());
        byte[] productBytes = productImage == null ? null : productImage.bytes();
        String productMime = productImage == null ? null : productImage.mimeType();
        byte[] generated = gemini.generateLookalike(
                userBytes, userMime,
                productBytes, productMime,
                pick.product().name(), pick.style(), pick.region());
        if (generated == null || generated.length == 0) {
            return null;
        }
        try {
            String ext = looksLikePng(generated) ? "png" : "jpg";
            String key = uploadStorage.save(UploadStorage.Folder.IMAGES, ext, generated);
            return uploadStorage.publicUrl(key);
        } catch (IOException ex) {
            return null;
        }
    }

    private static boolean looksLikePng(byte[] bytes) {
        return bytes.length >= 4
                && bytes[0] == (byte) 0x89
                && bytes[1] == 0x50
                && bytes[2] == 0x4E
                && bytes[3] == 0x47;
    }

    private String validateImage(MultipartFile image) {
        if (image == null || image.isEmpty()) {
            return "Vui lòng tải lên ảnh jpeg hoặc png";
        }
        if (image.getSize() > properties.getTryOnMaxUploadBytes()) {
            return "Ảnh vượt quá dung lượng cho phép";
        }
        String mime = normalizeMime(image.getContentType());
        if (!Objects.equals(mime, "image/jpeg") && !Objects.equals(mime, "image/png")) {
            return "Chỉ hỗ trợ ảnh jpeg hoặc png";
        }
        return null;
    }

    private static String normalizeMime(String contentType) {
        if (contentType == null || contentType.isBlank()) {
            return "image/jpeg";
        }
        String lower = contentType.toLowerCase(Locale.ROOT).split(";")[0].trim();
        if ("image/jpg".equals(lower)) {
            return "image/jpeg";
        }
        return lower;
    }
}
