package com.pk.core.model.entity;

import com.pk.core.model.constant.TableConstant;
import com.pk.core.common.entity.UpdateEntity;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.Id;
import vn.com.unit.miragesql.miragesql.annotation.Column;
import vn.com.unit.miragesql.miragesql.annotation.PrimaryKey;
import vn.com.unit.miragesql.miragesql.annotation.PrimaryKey.GenerationType;
import vn.com.unit.miragesql.miragesql.annotation.Table;

/** Khối nội dung trong 1 trang CMS (spec FE: BANNER, PRODUCT_CAROUSEL, PRODUCT_LIST, INFO_CARDS, IMAGE_GALLERY, ...). Bảng
 * `cms_blocks` KHÔNG có deleted_id/deleted_date nên extends UpdateEntity - xoá block là HARD delete (xem
 * CmsServiceImpl.deleteBlock()), khác CmsPageEntity. `data` lưu `config` của block dạng chuỗi JSON (TEXT); service
 * (de)serialize và kiểm tra cấu trúc cho các loại BANNER/PRODUCT_CAROUSEL. `targetSegment` là phân khúc khách mục
 * tiêu (CHƯA lọc theo phân khúc ở storefront - chỉ lưu và trả lại). */
@Getter
@Setter
@NoArgsConstructor
@Table(name = TableConstant.CMS_BLOCKS)
public class CmsBlockEntity extends UpdateEntity {

    public static final String BANNER = "BANNER";
    public static final String PRODUCT_CAROUSEL = "PRODUCT_CAROUSEL";
    public static final String INFO_CARDS = "INFO_CARDS";
    public static final String IMAGE_GALLERY = "IMAGE_GALLERY";
    public static final String CTA_BLOCK = "CTA_BLOCK";
    public static final String TOP_LINE_BANNER = "TOP_LINE_BANNER";
    public static final String NEWSLETTER_SIGNUP = "NEWSLETTER_SIGNUP";
    public static final String TEXT_BLOCK = "TEXT_BLOCK";

    public static final java.util.Set<String> TYPES = java.util.Set.of(BANNER, PRODUCT_CAROUSEL, "PRODUCT_LIST",
            INFO_CARDS, IMAGE_GALLERY, "PRODUCT_CATEGORY_NAV", "PRODUCT_COLLECTION_SHOWCASE",
            "PRODUCT_EXPANDABLE_DESCRIPTION", CTA_BLOCK, TOP_LINE_BANNER, NEWSLETTER_SIGNUP, TEXT_BLOCK);

    @Id
    @PrimaryKey(generationType = GenerationType.SEQUENCE, generator = "cms_blocks_id_seq")
    @Column(name = "id")
    private Long id;

    @Column(name = "page_id")
    private Long pageId;

    @Column(name = "type")
    private String type;

    @Column(name = "sort_order")
    private int sortOrder;

    @Column(name = "data")
    private String data;

    @Column(name = "target_segment")
    private String targetSegment;

    /** Block tắt (false) vẫn lưu nhưng không trả ở storefront. */
    @Column(name = "is_visible")
    private boolean visible = true;

    public CmsBlockEntity(Long pageId, String type, int sortOrder, String data, String targetSegment) {
        this.pageId = pageId;
        this.type = type;
        this.sortOrder = sortOrder;
        this.data = data;
        this.targetSegment = targetSegment;
    }
}
