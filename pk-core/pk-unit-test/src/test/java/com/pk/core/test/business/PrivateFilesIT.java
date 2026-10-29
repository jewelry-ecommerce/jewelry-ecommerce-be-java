package com.pk.core.test.business;

import com.fasterxml.jackson.databind.JsonNode;
import com.pk.core.business.repository.ImportExportJobRepo;
import com.pk.core.business.service.impl.UploadStorage;
import com.pk.core.model.constant.UrlConstant;
import com.pk.core.model.constant.admin.UrlAdminConstant;
import com.pk.core.model.entity.ImportExportJobEntity;
import com.pk.core.test.common.IntegrationTestBase;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Kho file chung: folder riêng tư (CSV) không bao giờ công khai, tải qua link ký có hạn; folder công khai vẫn phục vụ bình thường. */
class PrivateFilesIT extends IntegrationTestBase {

    private static final String V1 = UrlConstant.Common.API + UrlConstant.Common.VERSION;
    private static final String FILES = V1 + UrlConstant.Storefront.FILES + "/";

    @Autowired UploadStorage storage;
    @Autowired ImportExportJobRepo jobs;

    @Test
    void privateAndLegacyIeFoldersAreNeverServedPublicly() throws Exception {
        String t = UUID.randomUUID().toString().substring(0, 8);
        String privateKey = storage.save(UploadStorage.Folder.IMPORT_EXPORT, "csv", "a,b\n".getBytes(StandardCharsets.UTF_8));
        Path legacy = storage.root().resolve("ie/legacy-" + t + ".csv");
        Files.createDirectories(legacy.getParent());
        Files.writeString(legacy, "a,b\n");
        String imageKey = storage.save(UploadStorage.Folder.IMAGES, "png", new byte[] {1, 2, 3});

        mvc.perform(get(FILES + privateKey)).andExpect(status().isNotFound());
        mvc.perform(get(FILES + "ie/legacy-" + t + ".csv")).andExpect(status().isNotFound());
        // URI dựng sẵn để giữ nguyên %-encoding (get(String) sẽ encode lại dấu %).
        mvc.perform(get(java.net.URI.create(FILES + "%69e/legacy-" + t + ".csv"))).andExpect(status().isNotFound());
        mvc.perform(get(java.net.URI.create(FILES + "%70rivate/import-export/x.csv"))).andExpect(status().isNotFound());
        mvc.perform(get(FILES + imageKey)).andExpect(status().isOk());   // folder công khai vẫn phục vụ
    }

    private long exportJobId(String admin) throws Exception {
        String t = UUID.randomUUID().toString().substring(0, 8);
        long categoryId = body(mvc.perform(jsonRequest(post(V1 + UrlAdminConstant.Catalog.CATEGORIES),
                        Map.of("name", "DM " + t, "slug", "dm-" + t, "sortOrder", 0)).header("Authorization", admin))
                .andExpect(status().isCreated())).get("id").asLong();
        long productId = body(mvc.perform(jsonRequest(post(V1 + UrlAdminConstant.Catalog.PRODUCTS), Map.of(
                        "categoryId", categoryId, "code", "P-" + t, "name", "Nhẫn " + t, "slug", "nhan-" + t,
                        "variants", List.of(Map.of("skuCode", "SKU-" + t, "sizeLabel", "M", "listPrice", 1_000_000,
                                "onHand", 5, "isDefault", true)))).header("Authorization", admin))
                .andExpect(status().isCreated())).get("id").asLong();
        return body(mvc.perform(jsonRequest(post(V1 + UrlAdminConstant.Ie.EXPORT_PRODUCTS),
                        Map.of("productIds", List.of(productId))).header("Authorization", admin))
                .andExpect(status().isOk())).get("jobId").asLong();
    }

    private String linkOf(long jobId, String admin) throws Exception {
        return body(mvc.perform(get(V1 + UrlAdminConstant.Ie.JOB_STATUS + "/" + jobId).header("Authorization", admin))
                .andExpect(status().isOk())).get("fileUrl").asText();
    }

    @Test
    void exportLinkIsSignedPrivateAndDownloadsTheCsv() throws Exception {
        String admin = bearer(adminAccessToken());
        long jobId = exportJobId(admin);
        String link = linkOf(jobId, admin);

        assertTrue(link.contains("/storefront/private-files/import-export/" + jobId + "?exp="), link);
        assertTrue(link.contains("&sig="), link);
        assertFalse(link.contains("/ie/") || link.contains("private/import-export/2"), "không lộ đường dẫn lưu trữ: " + link);

        String csv = mvc.perform(get(link)).andExpect(status().isOk())
                .andExpect(header().string("Content-Type", org.hamcrest.Matchers.startsWith("text/csv")))
                .andExpect(header().string("Content-Disposition", org.hamcrest.Matchers.containsString("attachment")))
                .andExpect(header().string("Cache-Control", "no-store"))
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertTrue(csv.contains("product_code"), csv);
    }

    @Test
    void tamperedExpiredMissingOrForeignLinksAreRefused() throws Exception {
        String admin = bearer(adminAccessToken());
        long jobId = exportJobId(admin);
        long otherJob = exportJobId(admin);
        String link = linkOf(jobId, admin);
        String path = link.substring(0, link.indexOf('?'));
        String exp = link.replaceAll(".*exp=(\\d+)&.*", "$1");
        String sig = link.replaceAll(".*sig=", "");

        String flipped = sig.substring(0, sig.length() - 1) + (sig.endsWith("0") ? "1" : "0");
        for (String bad : new String[] {
                path + "?exp=" + exp + "&sig=" + flipped,
                path + "?exp=1&sig=" + sig,
                path + "?exp=" + exp,
                path,
                path.replace("/" + jobId, "/" + otherJob) + "?exp=" + exp + "&sig=" + sig}) {
            mvc.perform(get(bad)).andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.errors[0].code").value("FILE_LINK_INVALID"));
        }
    }

    @Test
    void missingFileOnDiskIsReportedAsNotAvailableAndLegacyJobsStillDownload() throws Exception {
        String admin = bearer(adminAccessToken());
        long jobId = exportJobId(admin);
        ImportExportJobEntity job = jobs.findOne(jobId);
        Files.delete(storage.root().resolve(job.getFileUrl()));
        mvc.perform(get(linkOf(jobId, admin))).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errors[0].code").value("FILE_NOT_AVAILABLE"));

        // Job cũ lưu URL công khai ".../storefront/files/ie/<tên>" vẫn tải được qua link ký.
        String name = "legacy-" + UUID.randomUUID() + ".csv";
        Path legacy = storage.root().resolve("ie/" + name);
        Files.createDirectories(legacy.getParent());
        Files.writeString(legacy, "product_code\nOLD\n");
        job.setFileUrl("http://old-host/api/v1/storefront/files/ie/" + name);
        jobs.update(job);
        String csv = mvc.perform(get(linkOf(jobId, admin))).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertEquals("product_code\nOLD\n", csv);
    }
}
