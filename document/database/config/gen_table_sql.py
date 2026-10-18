#!/usr/bin/env python3
"""Sinh document/1. Table.sql bằng cách gộp toàn bộ Flyway migration V1..Vn (đúng thứ tự số).
Chạy lại mỗi khi thêm migration mới:  python gen_table_sql.py   (đứng ở đâu cũng được)
"""
import os, re, glob, datetime
REPO = os.path.abspath(os.path.join(os.path.dirname(os.path.abspath(__file__)), '..', '..', '..'))  # gốc repo (script nằm ở document/database/config)
MIG = REPO + '/pk-core/pk-service/src/main/resources/db/migration'
OUT = REPO + '/document/database/1. Table.sql'
files = sorted(glob.glob(MIG + '/V*__*.sql'), key=lambda f: int(re.match(r'V(\d+)__', os.path.basename(f)).group(1)))
n = len(files)
hdr = f"""-- =====================================================================================
-- pk-core / KLTN - SCRIPT TẠO SCHEMA (gộp {n} migration Flyway V1..V{n}, chạy tay bằng psql/pgAdmin/DBeaver)
-- =====================================================================================
-- FILE NÀY ĐƯỢC SINH TỰ ĐỘNG - ĐỪNG SỬA TAY. Nguồn thật là các file migration trong:
--   pk-core/pk-service/src/main/resources/db/migration/V1__init.sql ... V{n}__*.sql
-- Khi thêm migration mới (V{n+1}, ...): tạo file Flyway như thường lệ rồi sinh lại file này
-- bằng: python document/database/config/gen_table_sql.py (hoặc nối thêm tay đúng thứ tự số, đặt trước COMMIT).
--
-- Vì sao cần file này: profile dev (application-dev.yml) tắt Flyway (spring.flyway.enabled=false)
-- nên DB dev "kltn_ecommerce" được dựng bằng tay: "1. Table.sql" (schema) rồi "2. Data.sql" (dữ liệu mẫu).
-- Profile mặc định/prod vẫn dùng Flyway và KHÔNG cần file này.
--
-- Có kèm vài dòng dữ liệu tham chiếu mà chính migration chèn (roles ADMIN/CUSTOMER, banner_placements,
-- product_attribute_values...) - đây là phần của schema, không phải dữ liệu mẫu.
-- Dữ liệu mẫu (user demo, sản phẩm, đơn hàng...) nằm ở "2. Data.sql".
--
-- Chạy: psql -U postgres -d kltn_ecommerce -f "1. Table.sql"   (hoặc chạy reset-db.bat cùng thư mục)
-- =====================================================================================

BEGIN;

"""
parts = [hdr]
for f in files:
    name = os.path.basename(f)
    body = open(f, encoding='utf-8').read().replace('\r\n', '\n').strip('\n')
    parts.append(f"-- ===================================================================\n-- {name}\n-- ===================================================================\n{body}\n\n")
parts.append("COMMIT;\n")
open(OUT, 'w', encoding='utf-8', newline='').write(''.join(parts).replace('\n', '\r\n'))
print(n, 'migrations ->', OUT)
