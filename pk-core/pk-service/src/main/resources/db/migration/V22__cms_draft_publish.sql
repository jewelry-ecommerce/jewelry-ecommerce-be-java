-- V22: CMS tách bản nháp (cms_pages + cms_blocks) khỏi bản đang public (published_snapshot, JSON text).
-- Lưu nháp không đổi bản public; Xuất bản chụp lại nháp -> snapshot + tăng published_version. Trang PUBLISHED cũ chưa có
-- snapshot sẽ được chụp tự động ở lần sửa đầu tiên (xem CmsServiceImpl.ensureSnapshot), storefront vẫn phục vụ như cũ.
-- target_device/schedule_* là cấu hình của bản nháp, được chép vào snapshot khi xuất bản.
ALTER TABLE cms_pages ADD COLUMN IF NOT EXISTS target_device VARCHAR(10) NOT NULL DEFAULT 'ALL';
ALTER TABLE cms_pages ADD COLUMN IF NOT EXISTS schedule_start TIMESTAMPTZ;
ALTER TABLE cms_pages ADD COLUMN IF NOT EXISTS schedule_end TIMESTAMPTZ;
ALTER TABLE cms_pages ADD COLUMN IF NOT EXISTS published_slug VARCHAR(140);
ALTER TABLE cms_pages ADD COLUMN IF NOT EXISTS published_snapshot TEXT;
ALTER TABLE cms_pages ADD COLUMN IF NOT EXISTS published_version INTEGER NOT NULL DEFAULT 0;
ALTER TABLE cms_pages ADD COLUMN IF NOT EXISTS published_at TIMESTAMPTZ;
ALTER TABLE cms_pages ADD COLUMN IF NOT EXISTS published_hash VARCHAR(64);
ALTER TABLE cms_pages DROP CONSTRAINT IF EXISTS ck_cms_pages_target_device;
ALTER TABLE cms_pages ADD CONSTRAINT ck_cms_pages_target_device CHECK (target_device IN ('ALL', 'DESKTOP', 'MOBILE'));
CREATE UNIQUE INDEX IF NOT EXISTS uq_cms_pages_published_slug ON cms_pages (published_slug) WHERE published_slug IS NOT NULL;
