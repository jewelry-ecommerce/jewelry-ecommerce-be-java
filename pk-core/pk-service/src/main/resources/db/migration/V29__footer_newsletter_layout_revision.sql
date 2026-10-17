-- Footer: tiêu đề/phụ đề khối đăng ký nhận tin (client dùng newsletterHeading / newsletterSubHeading).
ALTER TABLE site_footer ADD COLUMN IF NOT EXISTS newsletter_heading VARCHAR(200);
ALTER TABLE site_footer ADD COLUMN IF NOT EXISTS newsletter_sub_heading VARCHAR(300);

-- Banner layout: số phiên bản tăng dần theo từng vị trí (lịch sử phiên bản). Layout cũ đánh số theo thứ tự id.
ALTER TABLE banner_placement_layouts ADD COLUMN IF NOT EXISTS revision INTEGER NOT NULL DEFAULT 1;
UPDATE banner_placement_layouts l SET revision = r.rn
  FROM (SELECT id, ROW_NUMBER() OVER (PARTITION BY placement_id ORDER BY id) AS rn FROM banner_placement_layouts) r
 WHERE l.id = r.id;
