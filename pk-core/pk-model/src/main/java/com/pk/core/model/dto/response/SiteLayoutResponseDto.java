package com.pk.core.model.dto.response;

import java.util.List;
import java.util.Map;

/** Response của header/footer/logo (admin + public). Thời gian ISO-8601, id số (footer: id null khi chưa từng lưu). */
public final class SiteLayoutResponseDto {

    private SiteLayoutResponseDto() {
    }

    /** {@code status} là trạng thái đã lưu; {@code displayStatus} là trạng thái hiệu lực theo giờ hiện tại (SCHEDULED đã tới giờ -> ACTIVE, hết hạn -> DEACTIVE). */
    public record Header(Long id, String status, String displayStatus, String bannerName, String bannerDisplayStyle,
                         String bannerContent, String bannerBgColor, String bannerTextColor, String bannerTargetUrl,
                         String bannerAnimationStyle, Integer bannerLoopIntervalSec, String bannerStartTime,
                         String bannerEndTime, String createdAt, String updatedAt) {
    }

    public record Logo(Long id, String name, String type, String status, String logoUrl, String logoTheme,
                       String logoTargetUrl, String createdAt, String updatedAt) {
    }

    public record SocialLink(String id, String platform, String displayName, String url, String image, int orderIndex) {
    }

    public record MenuItem(String id, String label, String url, int orderIndex) {
    }

    public record MenuColumn(String id, String title, int orderIndex, List<MenuItem> items) {
    }

    public record Footer(Long id, String copyrightText, String footerLogoUrl, String certificationImageUrl,
                         String certificationUrl, String newsletterHeading, String newsletterSubHeading,
                         List<SocialLink> socialLinks, List<MenuColumn> menuColumns, String updatedAt) {
    }

    /** Public: header đang hiển thị (null nếu không có), footer, và logo ACTIVE theo từng type (HEADER/FOOTER/AUTH/FAVICON). */
    public record SiteLayout(Header header, Footer footer, Map<String, Logo> logos) {
    }
}
