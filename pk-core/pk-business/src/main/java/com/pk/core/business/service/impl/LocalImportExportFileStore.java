package com.pk.core.business.service.impl;

import com.pk.core.business.service.ImportExportFileStore;
import com.pk.core.model.constant.UrlConstant;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.util.UUID;

/** CSV nằm dưới `<upload.dir>/ie/...`, được phục vụ bởi cùng resource handler của file upload. */
@Service
public class LocalImportExportFileStore implements ImportExportFileStore {

    @Value("${app.upload.dir:D:/pk-uploads}")
    private String dir;

    @Value("${app.upload.public-base-url:}")
    private String publicBaseUrl;

    private Path root;

    @PostConstruct
    void init() throws IOException {
        root = Paths.get(dir).toAbsolutePath().normalize();
        Files.createDirectories(root);
    }

    @Override
    public String saveCsv(byte[] content) {
        LocalDate today = LocalDate.now();
        String relative = "ie/" + today.getYear() + "/" + String.format("%02d", today.getMonthValue()) + "/"
                + UUID.randomUUID() + ".csv";
        Path target = root.resolve(relative).normalize();
        if (!target.startsWith(root)) {
            throw new IllegalStateException("Đường dẫn lưu không hợp lệ");
        }
        try {
            Files.createDirectories(target.getParent());
            Files.write(target, content);
        } catch (IOException ex) {
            throw new IllegalStateException("Không ghi được file CSV", ex);
        }
        String base = publicBaseUrl == null ? "" : publicBaseUrl.trim().replaceAll("/+$", "");
        return base + UrlConstant.Common.API + UrlConstant.Common.VERSION + UrlConstant.Storefront.FILES + "/" + relative;
    }
}
