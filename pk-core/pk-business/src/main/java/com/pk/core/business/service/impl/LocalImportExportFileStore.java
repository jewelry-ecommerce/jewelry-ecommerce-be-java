package com.pk.core.business.service.impl;

import com.pk.core.business.service.ImportExportFileStore;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.io.IOException;

/** CSV nằm dưới `private/import-export/` của {@link UploadStorage} (không được phục vụ công khai). */
@Service
@RequiredArgsConstructor
public class LocalImportExportFileStore implements ImportExportFileStore {

    private final UploadStorage storage;

    @Override
    public String saveCsv(byte[] content) {
        try {
            return storage.save(UploadStorage.Folder.IMPORT_EXPORT, "csv", content);
        } catch (IOException ex) {
            throw new IllegalStateException("Không ghi được file CSV", ex);
        }
    }

    @Override
    public byte[] readCsv(String key) throws IOException {
        return storage.read(key);
    }
}
