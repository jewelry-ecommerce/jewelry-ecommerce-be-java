-- =====================================================================================
-- pk-core / KLTN - DỮ LIỆU MẪU (chạy SAU "1. Table.sql", trên schema rỗng)
-- =====================================================================================
-- Dùng subquery theo khóa tự nhiên (email/code/slug/sku_code/name) để lấy id thay vì ghi cứng
-- số - an toàn dù bảng đang có id GENERATED AS IDENTITY bắt đầu từ đâu. Tôn trọng đúng mọi
-- CHECK constraint thật trong "1. Table.sql" (status enum, giá >= 0, reserved <= on_hand,
-- published_at bắt buộc khi status=PUBLISHED, ends_at > starts_at...).
--
-- ĐĂNG NHẬP BẰNG SỐ ĐIỆN THOẠI (email chỉ là thông tin phụ). Tài khoản demo, mật khẩu chung Test@1234:
--   0901234567  Quản trị viên (ADMIN)      - đăng nhập trang admin: POST /api/admin/auth/login
--   0912345678  Nguyễn Thị Lan (CUSTOMER)
--   0923456789  Trần Văn Minh  (CUSTOMER)
--   0934567890  Phạm Thị Hoa   (CUSTOMER)
--   0945678901  Nhân viên sản phẩm (CATALOG_MANAGER) - sản phẩm/khuyến mãi/CMS/banner/badge
--   0956789012  Nhân viên đơn hàng (ORDER_MANAGER)   - đơn hàng/kho/khách hàng/yêu cầu khách
-- Mật khẩu đăng nhập demo cho TẤT CẢ user bên dưới: Test@1234
-- (password_hash là BCrypt thật, strength 10, khớp đúng BCryptPasswordEncoder() mặc định mà
-- SecurityConfig của project đang dùng - nghĩa là có thể đăng nhập thật bằng các tài khoản này).
--
-- Chạy: psql -U postgres -d kltn_ecommerce -f "2. Data.sql"
-- =====================================================================================

BEGIN;

-- ===================== IDENTITY =====================
INSERT INTO roles (name) VALUES ('ADMIN'), ('CUSTOMER'), ('CATALOG_MANAGER'), ('ORDER_MANAGER');

INSERT INTO users (phone, email, password_hash, full_name, enabled) VALUES
    ('0901234567', 'admin@pkjewelry.vn', '$2b$10$vvpsrB3Gg5cI73JTIzUQve.gR3tWn0KoBOabK8UKCXEpwtj0p6M.2', 'Quản trị viên', TRUE),
    ('0912345678', 'lan.nguyen@gmail.com', '$2b$10$vvpsrB3Gg5cI73JTIzUQve.gR3tWn0KoBOabK8UKCXEpwtj0p6M.2', 'Nguyễn Thị Lan', TRUE),
    ('0923456789', 'minh.tran@gmail.com', '$2b$10$vvpsrB3Gg5cI73JTIzUQve.gR3tWn0KoBOabK8UKCXEpwtj0p6M.2', 'Trần Văn Minh', TRUE),
    ('0934567890', 'hoa.pham@gmail.com', '$2b$10$vvpsrB3Gg5cI73JTIzUQve.gR3tWn0KoBOabK8UKCXEpwtj0p6M.2', 'Phạm Thị Hoa', TRUE),
    ('0945678901', 'catalog@pkjewelry.vn', '$2b$10$vvpsrB3Gg5cI73JTIzUQve.gR3tWn0KoBOabK8UKCXEpwtj0p6M.2', 'Nhân viên sản phẩm', TRUE),
    ('0956789012', 'orders@pkjewelry.vn', '$2b$10$vvpsrB3Gg5cI73JTIzUQve.gR3tWn0KoBOabK8UKCXEpwtj0p6M.2', 'Nhân viên đơn hàng', TRUE);

INSERT INTO user_roles (user_id, role_id)
SELECT u.id, r.id FROM users u, roles r WHERE u.email = 'admin@pkjewelry.vn' AND r.name = 'ADMIN';
INSERT INTO user_roles (user_id, role_id)
SELECT u.id, r.id FROM users u, roles r WHERE u.email IN ('lan.nguyen@gmail.com','minh.tran@gmail.com','hoa.pham@gmail.com') AND r.name = 'CUSTOMER';
INSERT INTO user_roles (user_id, role_id)
SELECT u.id, r.id FROM users u, roles r WHERE u.email = 'catalog@pkjewelry.vn' AND r.name = 'CATALOG_MANAGER';
INSERT INTO user_roles (user_id, role_id)
SELECT u.id, r.id FROM users u, roles r WHERE u.email = 'orders@pkjewelry.vn' AND r.name = 'ORDER_MANAGER';

-- ===================== ĐỊA CHỈ GIAO HÀNG =====================
INSERT INTO customer_addresses (user_id, recipient_name, phone, province, district, ward, address_line, is_default)
SELECT id, 'Nguyễn Thị Lan', '0912345678', 'TP. Hồ Chí Minh', 'Quận 1', 'Phường Bến Nghé', '123 Nguyễn Huệ', TRUE FROM users WHERE email = 'lan.nguyen@gmail.com';
INSERT INTO customer_addresses (user_id, recipient_name, phone, province, district, ward, address_line, is_default)
SELECT id, 'Nguyễn Thị Lan (công ty)', '0912345678', 'TP. Hồ Chí Minh', 'Quận 3', 'Phường Võ Thị Sáu', '45 Cách Mạng Tháng 8', FALSE FROM users WHERE email = 'lan.nguyen@gmail.com';
INSERT INTO customer_addresses (user_id, recipient_name, phone, province, district, ward, address_line, is_default)
SELECT id, 'Trần Văn Minh', '0923456789', 'Hà Nội', 'Quận Cầu Giấy', 'Phường Dịch Vọng', '78 Xuân Thuỷ', TRUE FROM users WHERE email = 'minh.tran@gmail.com';
INSERT INTO customer_addresses (user_id, recipient_name, phone, province, district, ward, address_line, is_default)
SELECT id, 'Phạm Thị Hoa', '0934567890', 'Đà Nẵng', 'Quận Hải Châu', 'Phường Thạch Thang', '12 Trần Phú', TRUE FROM users WHERE email = 'hoa.pham@gmail.com';

-- ===================== DANH MỤC (cây 2 cấp) =====================
INSERT INTO categories (parent_id, name, slug, description, sort_order) VALUES
    (NULL, 'Trang sức Vàng', 'trang-suc-vang', 'Các sản phẩm trang sức vàng 18K/24K', 1),
    (NULL, 'Trang sức Bạc',  'trang-suc-bac',  'Các sản phẩm trang sức bạc 925', 2);
INSERT INTO categories (parent_id, name, slug, description, sort_order)
SELECT id, 'Nhẫn', 'nhan', 'Nhẫn vàng các loại', 1 FROM categories WHERE slug = 'trang-suc-vang';
INSERT INTO categories (parent_id, name, slug, description, sort_order)
SELECT id, 'Dây chuyền', 'day-chuyen', 'Dây chuyền vàng các loại', 2 FROM categories WHERE slug = 'trang-suc-vang';
INSERT INTO categories (parent_id, name, slug, description, sort_order)
SELECT id, 'Vòng tay', 'vong-tay', 'Vòng tay bạc các loại', 1 FROM categories WHERE slug = 'trang-suc-bac';
INSERT INTO categories (parent_id, name, slug, description, sort_order)
SELECT id, 'Bông tai', 'bong-tai', 'Bông tai bạc các loại', 2 FROM categories WHERE slug = 'trang-suc-bac';

-- ===================== BỘ SƯU TẬP =====================
INSERT INTO collections (name, slug, description, status) VALUES
    ('Bộ sưu tập Mùa Xuân 2026', 'mua-xuan-2026', 'Thiết kế mới ra mắt đầu năm 2026', 'PUBLISHED'),
    ('Sản phẩm bán chạy', 'ban-chay', 'Những mẫu được khách đặt nhiều nhất', 'PUBLISHED'),
    ('Bộ sưu tập Thu Đông (đang soạn)', 'thu-dong-nhap', 'Chưa công bố', 'DRAFT');

-- ===================== SẢN PHẨM =====================
INSERT INTO products (category_id, code, name, slug, short_description, material, base_price, status, published_at)
SELECT id, 'NV-001', 'Nhẫn vàng 18K đính đá Solitaire', 'nhan-vang-18k-dinh-da-solitaire', 'Nhẫn cầu hôn đính đá CZ cao cấp', 'Vàng 18K', 5500000, 'PUBLISHED', now() - interval '20 days' FROM categories WHERE slug = 'nhan';
INSERT INTO products (category_id, code, name, slug, short_description, material, base_price, status, published_at)
SELECT id, 'NV-002', 'Nhẫn vàng 24K trơn truyền thống', 'nhan-vang-24k-tron-truyen-thong', 'Nhẫn vàng trơn kiểu dáng cổ điển', 'Vàng 24K', 8200000, 'PUBLISHED', now() - interval '15 days' FROM categories WHERE slug = 'nhan';
INSERT INTO products (category_id, code, name, slug, short_description, material, base_price, status, published_at)
SELECT id, 'DC-001', 'Dây chuyền vàng 18K mặt trái tim', 'day-chuyen-vang-18k-mat-trai-tim', 'Dây chuyền mặt trái tim đính đá', 'Vàng 18K', 12000000, 'PUBLISHED', now() - interval '25 days' FROM categories WHERE slug = 'day-chuyen';
INSERT INTO products (category_id, code, name, slug, short_description, material, base_price, status, published_at)
SELECT id, 'DC-002', 'Dây chuyền vàng 18K mặt Phật Di Lặc', 'day-chuyen-vang-18k-mat-phat-di-lac', 'Đang hoàn thiện ảnh sản phẩm', 'Vàng 18K', 9500000, 'DRAFT', NULL FROM categories WHERE slug = 'day-chuyen';
INSERT INTO products (category_id, code, name, slug, short_description, material, base_price, status, published_at)
SELECT id, 'VT-001', 'Vòng tay bạc 925 đính đá', 'vong-tay-bac-925-dinh-da', 'Vòng tay bạc 925 xi rhodium chống xỉn', 'Bạc 925', 1200000, 'PUBLISHED', now() - interval '10 days' FROM categories WHERE slug = 'vong-tay';
INSERT INTO products (category_id, code, name, slug, short_description, material, base_price, status, published_at)
SELECT id, 'VT-002', 'Vòng tay bạc 925 trơn', 'vong-tay-bac-925-tron', 'Kiểu dáng tối giản, đeo hàng ngày', 'Bạc 925', 850000, 'PUBLISHED', now() - interval '8 days' FROM categories WHERE slug = 'vong-tay';
INSERT INTO products (category_id, code, name, slug, short_description, material, base_price, status, published_at)
SELECT id, 'BT-001', 'Bông tai bạc 925 hình hoa', 'bong-tai-bac-925-hinh-hoa', 'Bông tai bạc 925 chạm khắc hình hoa', 'Bạc 925', 650000, 'PUBLISHED', now() - interval '5 days' FROM categories WHERE slug = 'bong-tai';
INSERT INTO products (category_id, code, name, slug, short_description, material, base_price, status, published_at)
SELECT id, 'BT-002', 'Bông tai bạc 925 ngọc trai', 'bong-tai-bac-925-ngoc-trai', 'Ngừng kinh doanh - hết hàng vĩnh viễn', 'Bạc 925', 1450000, 'ARCHIVED', now() - interval '60 days' FROM categories WHERE slug = 'bong-tai';

-- Gán sản phẩm PUBLISHED vào 2 bộ sưu tập
INSERT INTO product_collections (product_id, collection_id)
SELECT p.id, c.id FROM products p, collections c WHERE p.code IN ('NV-001','DC-001','VT-001') AND c.slug = 'mua-xuan-2026';
INSERT INTO product_collections (product_id, collection_id)
SELECT p.id, c.id FROM products p, collections c WHERE p.code IN ('NV-001','VT-002','BT-001') AND c.slug = 'ban-chay';

-- ===================== SKU (biến thể) =====================
-- NV-001: nhẫn có 3 size
INSERT INTO product_skus (product_id, sku_code, name, status, size_label, list_price, on_hand, reserved, is_default)
SELECT id, 'NV-001-S6', 'Size 6', 'PUBLISHED', 'Size 6', 5500000, 12, 1, TRUE  FROM products WHERE code = 'NV-001';
INSERT INTO product_skus (product_id, sku_code, name, status, size_label, list_price, on_hand, reserved, is_default)
SELECT id, 'NV-001-S7', 'Size 7', 'PUBLISHED', 'Size 7', 5500000, 9,  0, FALSE FROM products WHERE code = 'NV-001';
INSERT INTO product_skus (product_id, sku_code, name, status, size_label, list_price, on_hand, reserved, is_default)
SELECT id, 'NV-001-S8', 'Size 8', 'PUBLISHED', 'Size 8', 5500000, 0,  0, FALSE FROM products WHERE code = 'NV-001';
-- NV-002
INSERT INTO product_skus (product_id, sku_code, name, status, size_label, list_price, sale_price, on_hand, reserved, is_default)
SELECT id, 'NV-002-S7', 'Size 7', 'PUBLISHED', 'Size 7', 8200000, 7800000, 6, 0, TRUE FROM products WHERE code = 'NV-002';
-- DC-001
INSERT INTO product_skus (product_id, sku_code, name, status, metal_color_label, list_price, on_hand, reserved, is_default)
SELECT id, 'DC-001-VANG', 'Vàng vàng', 'PUBLISHED', 'Vàng vàng', 12000000, 5, 1, TRUE FROM products WHERE code = 'DC-001';
-- DC-002 (DRAFT - SKU cũng để DRAFT cho khớp)
INSERT INTO product_skus (product_id, sku_code, name, status, list_price, on_hand, reserved, is_default)
SELECT id, 'DC-002-DEFAULT', 'Mặc định', 'DRAFT', 9500000, 0, 0, TRUE FROM products WHERE code = 'DC-002';
-- VT-001
INSERT INTO product_skus (product_id, sku_code, name, status, list_price, on_hand, reserved, is_default)
SELECT id, 'VT-001-DEFAULT', 'Mặc định', 'PUBLISHED', 1200000, 20, 2, TRUE FROM products WHERE code = 'VT-001';
-- VT-002
INSERT INTO product_skus (product_id, sku_code, name, status, list_price, on_hand, reserved, is_default)
SELECT id, 'VT-002-DEFAULT', 'Mặc định', 'PUBLISHED', 850000, 30, 0, TRUE FROM products WHERE code = 'VT-002';
-- BT-001
INSERT INTO product_skus (product_id, sku_code, name, status, list_price, on_hand, reserved, is_default)
SELECT id, 'BT-001-DEFAULT', 'Mặc định', 'PUBLISHED', 650000, 25, 1, TRUE FROM products WHERE code = 'BT-001';
-- BT-002 (ARCHIVED - hết hàng)
INSERT INTO product_skus (product_id, sku_code, name, status, list_price, on_hand, reserved, is_default)
SELECT id, 'BT-002-DEFAULT', 'Mặc định', 'ARCHIVED', 1450000, 0, 0, TRUE FROM products WHERE code = 'BT-002';

-- ===================== THUỘC TÍNH SẢN PHẨM (Admin PIM) =====================
-- Lưu ý: cột options lưu dạng chuỗi phân cách "||" (theo đúng ProductAttributeEntity.
-- getOptionsList()/setOptionsList() thật trong code - KHÔNG phải JSON), TEXT thì để NULL.
INSERT INTO product_attributes (name, code, type, options) VALUES
    ('Chất liệu',     'material',   'TEXT',       NULL),
    ('Kích thước nhẫn','ring_size', 'SELECT',     'Size 6||Size 7||Size 8||Size 9'),
    ('Màu vàng',      'gold_color', 'MULTISELECT','Vàng trắng||Vàng hồng||Vàng vàng');

-- ===================== VOUCHER (khách tự nhập mã) =====================
INSERT INTO vouchers (code, discount_type, discount_value, max_discount_amount, min_order_value, usage_limit, used_count, starts_at, ends_at, status) VALUES
    ('WELCOME10', 'PERCENT', 10, 500000, 500000, 100, 12, now() - interval '10 days', now() + interval '20 days', 'PUBLISHED'),
    ('FREESHIP',  'FIXED',   30000, NULL, 0,      NULL, 34, now() - interval '30 days', now() + interval '60 days', 'PUBLISHED'),
    ('SUMMER50K', 'FIXED',   50000, NULL, 1000000, 200, 0,  now() + interval '5 days',  now() + interval '35 days', 'DRAFT');

-- ===================== PROMOTION (tự động áp theo sản phẩm) =====================
INSERT INTO promotions (name, discount_type, discount_value, starts_at, ends_at, status) VALUES
    ('Khuyến mãi vàng tháng 10', 'PERCENT', 5, now() - interval '5 days', now() + interval '25 days', 'PUBLISHED'),
    ('Giảm giá bạc cuối tuần',   'FIXED', 50000, now() + interval '2 days', now() + interval '9 days', 'DRAFT');

INSERT INTO promotion_products (promotion_id, product_id)
SELECT pr.id, p.id FROM promotions pr, products p WHERE pr.name = 'Khuyến mãi vàng tháng 10' AND p.code IN ('NV-001','NV-002','DC-001');
INSERT INTO promotion_products (promotion_id, product_id)
SELECT pr.id, p.id FROM promotions pr, products p WHERE pr.name = 'Giảm giá bạc cuối tuần' AND p.code IN ('VT-001','VT-002');

-- ===================== ĐƠN HÀNG (3 đơn, 3 trạng thái khác nhau) =====================
-- Đơn 1 - khách Lan - đã giao, đã thanh toán COD
INSERT INTO orders (code, user_id, status, payment_status, payment_method, shipping_method, tracking_code,
                     ship_recipient_name, ship_phone, ship_province, ship_district, ship_ward, ship_address_line,
                     subtotal, product_discount, voucher_discount, shipping_fee, grand_total, applied_voucher_code, placed_at)
VALUES (
    'DH20260905001', (SELECT id FROM users WHERE email = 'lan.nguyen@gmail.com'),
    'DELIVERED', 'PAID', 'COD', 'STANDARD', 'GHN20260905001',
    'Nguyễn Thị Lan', '0912345678', 'TP. Hồ Chí Minh', 'Quận 1', 'Phường Bến Nghé', '123 Nguyễn Huệ',
    6700000, 0, 0, 30000, 6730000, NULL, now() - interval '18 days'
);

INSERT INTO order_items (order_id, product_id, sku_id, name, quantity, unit_price, line_total)
SELECT o.id, p.id, s.id, p.name, 1, 5500000, 5500000
FROM orders o, products p, product_skus s
WHERE o.code = 'DH20260905001' AND p.code = 'NV-001' AND s.sku_code = 'NV-001-S6';
INSERT INTO order_items (order_id, product_id, sku_id, name, quantity, unit_price, line_total)
SELECT o.id, p.id, s.id, p.name, 1, 1200000, 1200000
FROM orders o, products p, product_skus s
WHERE o.code = 'DH20260905001' AND p.code = 'VT-001' AND s.sku_code = 'VT-001-DEFAULT';

INSERT INTO order_timeline (order_id, status, occurred_at, actor)
SELECT id, 'PENDING',   placed_at,                      'SYSTEM' FROM orders WHERE code = 'DH20260905001';
INSERT INTO order_timeline (order_id, status, occurred_at, actor)
SELECT id, 'CONFIRMED', placed_at + interval '1 hour',   'admin@pkjewelry.vn' FROM orders WHERE code = 'DH20260905001';
INSERT INTO order_timeline (order_id, status, occurred_at, actor)
SELECT id, 'SHIPPING',  placed_at + interval '1 day',    'admin@pkjewelry.vn' FROM orders WHERE code = 'DH20260905001';
INSERT INTO order_timeline (order_id, status, occurred_at, actor)
SELECT id, 'DELIVERED', placed_at + interval '3 days',   'GHN' FROM orders WHERE code = 'DH20260905001';

INSERT INTO payments (order_id, provider, transaction_id, amount, status)
SELECT id, 'COD', NULL, 6730000, 'SUCCESS' FROM orders WHERE code = 'DH20260905001';

-- Đơn 2 - khách Minh - đang chờ xử lý, chưa thanh toán, có áp voucher WELCOME10
INSERT INTO orders (code, user_id, status, payment_status, payment_method, shipping_method,
                     ship_recipient_name, ship_phone, ship_province, ship_district, ship_ward, ship_address_line,
                     subtotal, product_discount, voucher_discount, shipping_fee, grand_total, applied_voucher_code, placed_at)
VALUES (
    'DH20260918002', (SELECT id FROM users WHERE email = 'minh.tran@gmail.com'),
    'PENDING', 'UNPAID', 'VNPAY', 'EXPRESS',
    'Trần Văn Minh', '0923456789', 'Hà Nội', 'Quận Cầu Giấy', 'Phường Dịch Vọng', '78 Xuân Thuỷ',
    8200000, 0, 500000, 20000, 7720000, 'WELCOME10', now() - interval '2 days'
);

INSERT INTO order_items (order_id, product_id, sku_id, name, quantity, unit_price, line_total)
SELECT o.id, p.id, s.id, p.name, 1, 8200000, 8200000
FROM orders o, products p, product_skus s
WHERE o.code = 'DH20260918002' AND p.code = 'NV-002' AND s.sku_code = 'NV-002-S7';

INSERT INTO order_timeline (order_id, status, occurred_at, actor)
SELECT id, 'PENDING', placed_at, 'SYSTEM' FROM orders WHERE code = 'DH20260918002';

INSERT INTO payments (order_id, provider, transaction_id, amount, status)
SELECT id, 'VNPAY', NULL, 7720000, 'PENDING' FROM orders WHERE code = 'DH20260918002';

-- Đơn 3 - khách Hoa - đã huỷ, đã hoàn tiền
INSERT INTO orders (code, user_id, status, payment_status, payment_method, shipping_method,
                     ship_recipient_name, ship_phone, ship_province, ship_district, ship_ward, ship_address_line,
                     subtotal, product_discount, voucher_discount, shipping_fee, grand_total, applied_voucher_code, placed_at)
VALUES (
    'DH20260910003', (SELECT id FROM users WHERE email = 'hoa.pham@gmail.com'),
    'CANCELLED', 'REFUNDED', 'MOMO', 'STANDARD',
    'Phạm Thị Hoa', '0934567890', 'Đà Nẵng', 'Quận Hải Châu', 'Phường Thạch Thang', '12 Trần Phú',
    650000, 0, 0, 25000, 675000, NULL, now() - interval '9 days'
);

INSERT INTO order_items (order_id, product_id, sku_id, name, quantity, unit_price, line_total)
SELECT o.id, p.id, s.id, p.name, 1, 650000, 650000
FROM orders o, products p, product_skus s
WHERE o.code = 'DH20260910003' AND p.code = 'BT-001' AND s.sku_code = 'BT-001-DEFAULT';

INSERT INTO order_timeline (order_id, status, occurred_at, actor)
SELECT id, 'PENDING',   placed_at,                    'SYSTEM' FROM orders WHERE code = 'DH20260910003';
INSERT INTO order_timeline (order_id, status, occurred_at, actor, note)
SELECT id, 'CANCELLED', placed_at + interval '6 hours','hoa.pham@gmail.com', 'Khách đổi ý' FROM orders WHERE code = 'DH20260910003';

INSERT INTO payments (order_id, provider, transaction_id, amount, status)
SELECT id, 'MOMO', 'MOMO-TXN-000321', 675000, 'SUCCESS' FROM orders WHERE code = 'DH20260910003';

-- ===================== CMS =====================
INSERT INTO cms_pages (slug, title, status) VALUES
    ('trang-chu',   'Trang chủ',  'PUBLISHED'),
    ('gioi-thieu',  'Giới thiệu', 'PUBLISHED'),
    ('khuyen-mai',  'Khuyến mãi', 'DRAFT');

INSERT INTO cms_blocks (page_id, type, sort_order, data)
SELECT id, 'BANNER', 1, '{"placementCode":"HOME_HERO","layout":"SLIDER"}' FROM cms_pages WHERE slug = 'trang-chu';
INSERT INTO cms_blocks (page_id, type, sort_order, data)
SELECT id, 'PRODUCT_CAROUSEL', 2, '{"header":{"title":"Bán chạy"},"collectionSlug":"ban-chay"}' FROM cms_pages WHERE slug = 'trang-chu';
INSERT INTO cms_blocks (page_id, type, sort_order, data)
SELECT id, 'INFO_CARDS', 1, '{"cards":[{"title":"Trang sức PK","description":"Tinh xảo từng chi tiết"}]}' FROM cms_pages WHERE slug = 'gioi-thieu';

-- ===================== BADGE =====================
-- Quy tắc hiển thị storefront (mỗi SKU 1 nhãn): PRE_ORDER -> OUT_OF_STOCK -> CAMPAIGN (flow đang ACTIVE).
INSERT INTO badge_templates (name, code, description, type, badge_type, status, display_text, default_position, style_config, default_priority_weight) VALUES
    ('Đặt trước',   'PRE_ORDER',    'Sản phẩm hết hàng nhưng đang mở đặt trước', 'TEXT', 'PRE_ORDER',    'ACTIVE', 'Đặt trước',  'TOP_LEFT',  '{"shape":"PILL","backgroundColor":"#7c3aed","textColor":"#FFFFFF","fontSize":12}', 30),
    ('Hết hàng',    'OUT_OF_STOCK', 'Sản phẩm tạm hết hàng',                     'TEXT', 'OUT_OF_STOCK', 'ACTIVE', 'Hết hàng',   'TOP_LEFT',  '{"shape":"PILL","backgroundColor":"#6b7280","textColor":"#FFFFFF","fontSize":12}', 20),
    ('Khuyến mãi',  'SALE',         'Sản phẩm đang khuyến mãi',                  'TEXT', 'CAMPAIGN',     'ACTIVE', 'Giảm giá',   'TOP_RIGHT', '{"shape":"PILL","backgroundColor":"#ef4444","textColor":"#FFFFFF","fontSize":12}', 10),
    ('Hàng mới',    'NEW_ARRIVAL',  'Sản phẩm mới ra mắt',                       'TEXT', 'NEW_ARRIVAL',  'ACTIVE', 'Mới',        'TOP_LEFT',  '{"shape":"PILL","backgroundColor":"#B8860B","textColor":"#FFFFFF","fontSize":12}', 5),
    ('Bán chạy',    'BEST_SELLER',  'Sản phẩm bán chạy',                         'TEXT', 'BEST_SELLER',  'ACTIVE', 'Bán chạy',   'TOP_LEFT',  '{"shape":"ROUNDED","backgroundColor":"#f97316","textColor":"#FFFFFF","fontSize":12}', 5);

INSERT INTO badge_flow (name, description, status, rule_type, rule_config, channel, templates)
SELECT 'Nhãn sản phẩm hết hàng / đặt trước', 'Áp theo tồn kho: hết hàng + đang mở pre-order -> Đặt trước, ngược lại -> Hết hàng', 'ACTIVE', 'OUT_OF_STOCK', NULL, 'ALL',
       '[{"badgeTemplateId":"' || (SELECT id FROM badge_templates WHERE code = 'PRE_ORDER') || '","priorityWeight":30,"isPinned":false},'
    || '{"badgeTemplateId":"' || (SELECT id FROM badge_templates WHERE code = 'OUT_OF_STOCK') || '","priorityWeight":20,"isPinned":false}]';

INSERT INTO badge_flow (name, description, status, rule_type, rule_config, channel, templates)
SELECT 'Nhãn sản phẩm khuyến mãi', 'Sản phẩm thuộc chương trình khuyến mãi đang chạy', 'ACTIVE', 'PROMOTION', NULL, 'ALL',
       '[{"badgeTemplateId":"' || (SELECT id FROM badge_templates WHERE code = 'SALE') || '","priorityWeight":10,"isPinned":false}]';

INSERT INTO badge_flow (name, description, status, rule_type, rule_config, channel, templates)
SELECT 'Nhãn nhẫn mới', 'Danh mục Nhẫn hiển thị nhãn Hàng mới', 'ACTIVE', 'CATEGORY', '{"categoryIds":["' || c.id || '"]}', 'ALL',
       '[{"badgeTemplateId":"' || (SELECT id FROM badge_templates WHERE code = 'NEW_ARRIVAL') || '","priorityWeight":5,"isPinned":false}]'
  FROM categories c WHERE c.slug = 'nhan';

INSERT INTO badge_flow (name, description, status, rule_type, rule_config, channel, templates)
SELECT 'Nhãn bộ sưu tập bán chạy', 'Collection Bán chạy hiển thị nhãn Bán chạy', 'ACTIVE', 'COLLECTION', '{"collectionIds":["' || col.id || '"]}', 'ALL',
       '[{"badgeTemplateId":"' || (SELECT id FROM badge_templates WHERE code = 'BEST_SELLER') || '","priorityWeight":5,"isPinned":false}]'
  FROM collections col WHERE col.slug = 'ban-chay';

-- ===================== BANNER =====================
INSERT INTO banner_placements (code, name, display_type) VALUES
    ('HOME_HERO',    'Banner chính trang chủ',      'CAROUSEL'),
    ('HOME_PROMO',   'Khuyến mãi trang chủ',        'GRID'),
    ('CATEGORY_TOP', 'Đầu trang danh mục sản phẩm', 'SINGLE')
ON CONFLICT (code) DO NOTHING;

INSERT INTO banners (internal_name, media_url, media_type, layout, title, subtitle, title_color, actions_layout, actions, overlay_opacity, status, placement_code, sort_order) VALUES
    ('Hero - Bộ sưu tập thu đông', 'https://example.com/banners/hero-1.jpg', 'IMAGE', 'LEFT_CENTER', 'Bộ sưu tập thu đông', 'Trang sức vàng 18K tinh xảo', '#ffffff', 'INLINE',
     '[{"actionType":"COLLECTION","actionTarget":"ban-chay","ctaText":"Khám phá ngay","ctaBg":"#b8860b","ctaColor":"#ffffff"}]', 0.25, 'ACTIVE', 'HOME_HERO', 1),
    ('Hero - Nhẫn cưới', 'https://example.com/banners/hero-2.jpg', 'IMAGE', 'CENTER', 'Nhẫn cưới trọn đời', 'Ưu đãi đến 15%', '#ffffff', 'STACK',
     '[{"actionType":"CATEGORY","actionTarget":"nhan","ctaText":"Xem nhẫn","ctaBg":"#111111","ctaColor":"#ffffff"}]', 0.30, 'ACTIVE', 'HOME_HERO', 2),
    ('Promo - Khuyến mãi vàng', 'https://example.com/banners/promo-1.jpg', 'IMAGE', 'CENTER_BOTTOM', 'Khuyến mãi vàng tháng 10', NULL, '#ffffff', 'STACK',
     '[{"actionType":"URL","actionTarget":"/khuyen-mai","ctaText":"Mua ngay"}]', 0, 'DRAFT', 'HOME_PROMO', 1);

COMMIT;
