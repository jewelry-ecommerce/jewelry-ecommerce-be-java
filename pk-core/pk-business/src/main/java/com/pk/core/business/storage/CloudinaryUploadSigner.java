package com.pk.core.business.storage;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

public final class CloudinaryUploadSigner {
    private CloudinaryUploadSigner() {}

    public static String sign(String folder, String publicId, String timestamp, String secret) {
        String canonical = "folder=" + folder + "&public_id=" + publicId + "&timestamp=" + timestamp;
        try {
            byte[] digest = MessageDigest.getInstance(CloudinaryStorageConfiguration.SIGNATURE_ALGORITHM)
                    .digest((canonical + secret).getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException failure) {
            throw new IllegalStateException("Upload signature algorithm is unavailable", failure);
        }
    }
}
