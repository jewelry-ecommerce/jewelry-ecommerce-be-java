package com.pk.core.test.business;

import com.fasterxml.jackson.databind.JsonNode;
import com.pk.core.model.constant.UrlConstant;
import com.pk.core.model.constant.admin.UrlAdminConstant;
import com.pk.core.test.common.IntegrationTestBase;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import java.nio.charset.StandardCharsets;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Upload ảnh (admin) -> URL công khai tải lại được; từ chối file giả mạo (HTML đổi đuôi .png) và thiếu quyền. */
class AdminUploadIT extends IntegrationTestBase {

    private static final String UPLOAD = UrlConstant.Common.API + UrlConstant.Common.VERSION + UrlAdminConstant.Catalog.UPLOAD;
    private static final byte[] PNG = {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 0, 0, 0, 0x0D, 'I', 'H', 'D', 'R'};

    @Test
    void adminUploadsImageAndItIsServedPublicly() throws Exception {
        JsonNode res = body(mvc.perform(multipart(UPLOAD).file(new MockMultipartFile("file", "nhan.png", "image/png", PNG))
                        .header("Authorization", bearer(adminAccessToken())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.type").value("IMAGE"))
                .andExpect(jsonPath("$.data.contentType").value("image/png"))
                .andExpect(jsonPath("$.data.originalName").value("nhan.png")));
        String url = res.get("url").asText();
        org.junit.jupiter.api.Assertions.assertTrue(url.endsWith(".png"));
        // Tải lại KHÔNG cần đăng nhập.
        mvc.perform(get(url)).andExpect(status().isOk());
    }

    @Test
    void fakeImageAndMissingFileAreRejected() throws Exception {
        String admin = bearer(adminAccessToken());
        mvc.perform(multipart(UPLOAD).file(new MockMultipartFile("file", "x.png", "image/png",
                        "<html><script>alert(1)</script></html>".getBytes(StandardCharsets.UTF_8)))
                        .header("Authorization", admin))
                .andExpect(status().isBadRequest());
        mvc.perform(multipart(UPLOAD).file(new MockMultipartFile("file", "empty.png", "image/png", new byte[0]))
                        .header("Authorization", admin))
                .andExpect(status().isBadRequest());
    }

    @Test
    void imagesAndVideosGoToSeparateFolders() throws Exception {
        String admin = bearer(adminAccessToken());
        String image = body(mvc.perform(multipart(UPLOAD).file(new MockMultipartFile("file", "a.png", "image/png", PNG))
                .header("Authorization", admin)).andExpect(status().isCreated())).get("url").asText();
        byte[] mp4 = {0, 0, 0, 0x18, 'f', 't', 'y', 'p', 'm', 'p', '4', '2', 0, 0, 0, 0};
        JsonNode video = body(mvc.perform(multipart(UPLOAD).file(new MockMultipartFile("file", "v.mp4", "video/mp4", mp4))
                .header("Authorization", admin)).andExpect(status().isCreated()));
        org.junit.jupiter.api.Assertions.assertTrue(image.contains("/storefront/files/images/"), image);
        org.junit.jupiter.api.Assertions.assertTrue(video.get("url").asText().contains("/storefront/files/videos/"));
        org.junit.jupiter.api.Assertions.assertEquals("VIDEO", video.get("type").asText());
        mvc.perform(get(video.get("url").asText())).andExpect(status().isOk());
    }

    @Test
    void staticSvgIsAcceptedAndServedWithLockdownHeaders() throws Exception {
        String svg = "<?xml version=\"1.0\"?><svg xmlns=\"http://www.w3.org/2000/svg\" viewBox=\"0 0 10 10\"><path d=\"M0 0L10 10\" fill=\"#c00\"/></svg>";
        JsonNode res = body(mvc.perform(multipart(UPLOAD).file(new MockMultipartFile("file", "logo.svg", "image/svg+xml",
                        svg.getBytes(StandardCharsets.UTF_8))).header("Authorization", bearer(adminAccessToken())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.contentType").value("image/svg+xml"))
                .andExpect(jsonPath("$.data.type").value("IMAGE")));
        String url = res.get("url").asText();
        org.junit.jupiter.api.Assertions.assertTrue(url.contains("/storefront/files/images/") && url.endsWith(".svg"), url);
        mvc.perform(get(url)).andExpect(status().isOk())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.header().string("X-Content-Type-Options", "nosniff"))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.header().string("Content-Security-Policy",
                        org.hamcrest.Matchers.containsString("default-src 'none'")));
    }

    @Test
    void unsafeSvgIsRejectedWithStableCode() throws Exception {
        String admin = bearer(adminAccessToken());
        for (String bad : new String[] {
                "<svg xmlns=\"http://www.w3.org/2000/svg\"><script>alert(1)</script></svg>",
                "<svg xmlns=\"http://www.w3.org/2000/svg\" onload=\"alert(1)\"/>",
                "<svg xmlns=\"http://www.w3.org/2000/svg\"><image href=\"http://evil/x.png\"/></svg>",
                "<!DOCTYPE svg [<!ENTITY x SYSTEM \"file:///etc/passwd\">]><svg xmlns=\"http://www.w3.org/2000/svg\">&x;</svg>"}) {
            mvc.perform(multipart(UPLOAD).file(new MockMultipartFile("file", "x.svg", "image/svg+xml",
                            bad.getBytes(StandardCharsets.UTF_8))).header("Authorization", admin))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errors[0].code").value("FILE_REJECTED"));
        }
    }

    @Test
    void uploadRequiresAdminToken() throws Exception {
        mvc.perform(multipart(UPLOAD).file(new MockMultipartFile("file", "a.png", "image/png", PNG)))
                .andExpect(status().isUnauthorized());
        mvc.perform(multipart(UPLOAD).file(new MockMultipartFile("file", "a.png", "image/png", PNG))
                        .header("Authorization", bearer(customerAccessToken())))
                .andExpect(status().isForbidden());
    }
}
