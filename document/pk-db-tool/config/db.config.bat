@echo off
rem config\db.config.bat - cau hinh ket noi cho reset-db.bat (duoc "call" vao, khong chay rieng).
rem Moi gia tri chi duoc dat neu chua co san trong moi truong, nen co the ghi de nhanh:
rem     set DB_NAME=kltn_demo
rem     reset-db.bat

if not defined PGHOST set "PGHOST=localhost"
if not defined PGPORT set "PGPORT=5432"
if not defined PGUSER set "PGUSER=postgres"
if not defined DB_NAME set "DB_NAME=kltn_ecommerce"

rem Mat khau: de trong thi psql tu hoi (an khi go). Co the dat PGPASSWORD nhung khong nen ghi vao file.

rem Duong dan psql.exe cua PostgreSQL tren may nay (psql khong nam trong PATH). Cai ban khac thi sua so phien ban cho dung.
if not defined PSQL set "PSQL=C:\Program Files\PostgreSQL\18\bin\psql.exe"

rem Thu muc chua 2 file SQL (mac dinh: ..\..\..\document tinh tu thu muc config nay) va ten 2 file.
if not defined SQL_DIR set "SQL_DIR=%~dp0..\..\..\document"
if not defined TABLE_FILE set "TABLE_FILE=1. Table.sql"
if not defined DATA_FILE set "DATA_FILE=2. Data.sql"

rem Script chi cho phep xoa DB tren CHINH may nay (localhost, 127.0.0.1, ::1).
rem Muon tro toi may khac phai dat ALLOW_REMOTE=1 mot cach co chu y.
if not defined ALLOW_REMOTE set "ALLOW_REMOTE=0"
