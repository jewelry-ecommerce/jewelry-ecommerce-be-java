package com.pk.core.business.storage;

import java.nio.charset.StandardCharsets;

/** File signatures are checked before either local or remote storage is called. */
public enum UploadMediaKind {
    PNG("png", "image/png", "IMAGE"), JPEG("jpg", "image/jpeg", "IMAGE"),
    GIF("gif", "image/gif", "IMAGE"), WEBP("webp", "image/webp", "IMAGE"),
    MP4("mp4", "video/mp4", "VIDEO"), WEBM("webm", "video/webm", "VIDEO");

    public final String extension;
    public final String contentType;
    public final String type;

    UploadMediaKind(String extension, String contentType, String type) {
        this.extension = extension;
        this.contentType = contentType;
        this.type = type;
    }

    public String resourceType() {
        return "VIDEO".equals(type) ? "video" : "image";
    }

    public static UploadMediaKind detect(byte[] header, int length) {
        if (length >= 8 && (header[0] & 255) == 0x89 && text(header, 1, 3).equals("PNG")) return PNG;
        if (length >= 3 && (header[0] & 255) == 0xFF && (header[1] & 255) == 0xD8
                && (header[2] & 255) == 0xFF) return JPEG;
        if (length >= 6 && text(header, 0, 4).equals("GIF8")) return GIF;
        if (length >= 12 && text(header, 0, 4).equals("RIFF") && text(header, 8, 4).equals("WEBP")) return WEBP;
        if (length >= 12 && text(header, 4, 4).equals("ftyp")) return MP4;
        if (length >= 4 && (header[0] & 255) == 0x1A && (header[1] & 255) == 0x45
                && (header[2] & 255) == 0xDF && (header[3] & 255) == 0xA3) return WEBM;
        return null;
    }

    private static String text(byte[] bytes, int offset, int length) {
        return new String(bytes, offset, length, StandardCharsets.ISO_8859_1);
    }
}
