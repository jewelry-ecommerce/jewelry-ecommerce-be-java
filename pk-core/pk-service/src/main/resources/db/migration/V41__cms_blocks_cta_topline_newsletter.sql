-- Bổ sung 3 loại block CMS khớp Sevatrading (CTA, top line banner, newsletter).
ALTER TABLE cms_blocks DROP CONSTRAINT IF EXISTS ck_cms_blocks_type;

ALTER TABLE cms_blocks ADD CONSTRAINT ck_cms_blocks_type CHECK (
    type IN (
        'BANNER',
        'PRODUCT_CAROUSEL',
        'PRODUCT_LIST',
        'INFO_CARDS',
        'IMAGE_GALLERY',
        'PRODUCT_CATEGORY_NAV',
        'PRODUCT_COLLECTION_SHOWCASE',
        'PRODUCT_EXPANDABLE_DESCRIPTION',
        'CTA_BLOCK',
        'TOP_LINE_BANNER',
        'NEWSLETTER_SIGNUP'
    )
);
