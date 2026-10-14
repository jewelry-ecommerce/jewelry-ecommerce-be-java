package com.pk.core.model.entity;

import com.pk.core.model.constant.TableConstant;
import com.pk.core.common.entity.BaseEntity;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.Id;
import vn.com.unit.miragesql.miragesql.annotation.Column;
import vn.com.unit.miragesql.miragesql.annotation.PrimaryKey;
import vn.com.unit.miragesql.miragesql.annotation.PrimaryKey.GenerationType;
import vn.com.unit.miragesql.miragesql.annotation.Table;

/** Trang landing CMS (Admin mục O). Bảng `cms_pages` (V2__admin_extensions.sql) CÓ deleted_id/
 * deleted_date nên extends BaseEntity - xoá trang là xoá MỀM (softDelete), cùng kiểu Product/
 * Promotion. Nội dung trang (blocks) là bảng con riêng {@link CmsBlockEntity}, 1-n qua page_id,
 * KHÔNG nhúng trực tiếp ở đây. */
@Getter
@Setter
@NoArgsConstructor
@Table(name = TableConstant.CMS_PAGES)
public class CmsPageEntity extends BaseEntity {

    public static final String DRAFT = "DRAFT";
    public static final String PUBLISHED = "PUBLISHED";

    @Id
    @PrimaryKey(generationType = GenerationType.SEQUENCE, generator = "cms_pages_id_seq")
    @Column(name = "id")
    private Long id;

    @Column(name = "slug")
    private String slug;

    @Column(name = "title")
    private String title;

    @Column(name = "status")
    private String status = DRAFT;

    @Column(name = "locale")
    private String locale = "vi";

    /** Trang tắt (false) không được phục vụ ở storefront dù status = PUBLISHED. */
    @Column(name = "is_active")
    private boolean active = true;

    /** SEO của trang dạng JSON text ({title, description, keywords, canonicalUrl, imageUrl}). */
    @Column(name = "seo")
    private String seo;

    /** Thiết bị mục tiêu của bản nháp: ALL | DESKTOP | MOBILE (chép vào snapshot khi xuất bản). */
    @Column(name = "target_device")
    private String targetDevice = "ALL";

    @Column(name = "schedule_start")
    private java.util.Date scheduleStart;

    @Column(name = "schedule_end")
    private java.util.Date scheduleEnd;

    /** Slug của bản đang public (storefront tra theo cột này, không theo slug của nháp). */
    @Column(name = "published_slug")
    private String publishedSlug;

    /** Ảnh chụp bản đang public (JSON text: name/slug/locale/seo/targetDevice/schedule..., blocks[]). null = chưa xuất bản. */
    @Column(name = "published_snapshot")
    private String publishedSnapshot;

    @Column(name = "published_version")
    private int publishedVersion;

    @Column(name = "published_at")
    private java.util.Date publishedAt;

    /** SHA-256 nội dung (không gồm id block) của snapshot - so với nháp để biết còn thay đổi chưa xuất bản. */
    @Column(name = "published_hash")
    private String publishedHash;

    public CmsPageEntity(String slug, String title, String status) {
        this.slug = slug;
        this.title = title;
        this.status = status != null && !status.isBlank() ? status : DRAFT;
    }
}
