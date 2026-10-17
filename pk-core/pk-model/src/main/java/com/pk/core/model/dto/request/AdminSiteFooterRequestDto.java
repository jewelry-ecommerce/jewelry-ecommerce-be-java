package com.pk.core.model.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

/** PUT footer (ghi đè toàn bộ). {@code id} của mạng xã hội/cột/mục là tuỳ chọn: không gửi thì server tự sinh. */
@Getter
@Setter
@NoArgsConstructor
public class AdminSiteFooterRequestDto {

    @Size(max = 500)
    private String copyrightText;

    @Size(max = 500)
    private String footerLogoUrl;

    @Size(max = 500)
    private String certificationImageUrl;

    @Size(max = 500)
    private String certificationUrl;

    @Size(max = 200)
    private String newsletterHeading;

    @Size(max = 300)
    private String newsletterSubHeading;

    @Valid
    @Size(max = 10)
    private List<SocialLink> socialLinks = new ArrayList<>();

    @Valid
    @Size(max = 6)
    private List<MenuColumn> menuColumns = new ArrayList<>();

    @Getter
    @Setter
    @NoArgsConstructor
    public static class SocialLink {
        @Size(max = 60)
        private String id;

        @NotBlank
        @Pattern(regexp = "^(Facebook|Instagram|TikTok|YouTube|Twitter|LinkedIn|Zalo|Other)$", message = "platform không hợp lệ")
        private String platform;

        @NotBlank
        @Size(max = 100)
        private String displayName;

        @NotBlank
        @Size(max = 500)
        private String url;

        @Size(max = 500)
        private String image;

        private int orderIndex;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    public static class MenuColumn {
        @Size(max = 60)
        private String id;

        @NotBlank
        @Size(max = 100)
        private String title;

        private int orderIndex;

        @Valid
        @Size(max = 12)
        private List<MenuItem> items = new ArrayList<>();
    }

    @Getter
    @Setter
    @NoArgsConstructor
    public static class MenuItem {
        @Size(max = 60)
        private String id;

        @NotBlank
        @Size(max = 100)
        private String label;

        @NotBlank
        @Size(max = 500)
        private String url;

        private int orderIndex;
    }
}
