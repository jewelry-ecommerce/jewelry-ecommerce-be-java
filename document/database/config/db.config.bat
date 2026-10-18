@echo off
rem ======================================================================
rem  db.config.bat - cau hinh cho reset-db.bat (duoc "call" vao, khong chay rieng).
rem
rem  KHONG gan cung o dia / duong dan: thu muc SQL tinh tu vi tri file nay, psql tu dong tim.
rem  => chep ca thu muc "database" sang o khac, hoac pull git ve may khac, van chay duoc.
rem
rem  Moi gia tri chi duoc dat neu chua co san, nen co 3 cach doi (uu tien tu tren xuong):
rem   1) Bien moi truong truoc khi chay:   set DB_NAME=kltn_demo   roi   reset-db.bat
rem   2) File rieng cho tung may (khong commit): config\db.config.local.bat, vi du:
rem          set "PGPORT=5433"
rem          set "PGUSER=postgres"
rem          set "PGPASSWORD=matkhau"
rem   3) Sua gia tri mac dinh ngay duoi day.
rem ======================================================================

if exist "%~dp0db.config.local.bat" call "%~dp0db.config.local.bat"

if not defined PGHOST set "PGHOST=localhost"
if not defined PGPORT set "PGPORT=5432"
if not defined PGUSER set "PGUSER=postgres"
if not defined DB_NAME set "DB_NAME=kltn_ecommerce"
rem Mat khau: de trong thi psql tu hoi (an khi go). Dat PGPASSWORD trong db.config.local.bat neu muon, dung ghi vao file nay.

rem psql.exe tu dong tim theo thu tu: (1) bien PSQL neu da dat san, (2) psql trong PATH,
rem (3) noi cai PostgreSQL ghi trong registry (dung o dia nao cung duoc),
rem (4) thu muc %ProgramFiles%\PostgreSQL\<phien ban>\bin.
rem Muon ep dung ban khac:  set "PSQL=D:\pg\bin\psql.exe"  (trong cmd hoac db.config.local.bat).
if not defined PSQL call :find_psql
if not defined PSQL set "PSQL=psql"

rem Thu muc chua 2 file SQL = thu muc cha cua thu muc config nay (document\database, cung cho voi reset-db.bat).
rem "1. Table.sql" la schema gop tu migration V1..V36; "2. Data.sql" la du lieu mau.
if not defined SQL_DIR set "SQL_DIR=%~dp0.."
if not defined TABLE_FILE set "TABLE_FILE=1. Table.sql"
if not defined DATA_FILE set "DATA_FILE=2. Data.sql"

rem Script chi cho phep xoa DB tren CHINH may nay (localhost, 127.0.0.1, ::1).
rem Muon tro toi may khac phai dat ALLOW_REMOTE=1 mot cach co chu y.
if not defined ALLOW_REMOTE set "ALLOW_REMOTE=0"

exit /b 0

:find_psql
where psql >nul 2>&1
if not errorlevel 1 set "PSQL=psql"
if defined PSQL exit /b 0
for /f "tokens=1-3*" %%A in ('reg query "HKLM\SOFTWARE\PostgreSQL\Installations" /s /v "Base Directory" 2^>nul') do if "%%C"=="REG_SZ" if exist "%%D\bin\psql.exe" set "PSQL=%%D\bin\psql.exe"
if defined PSQL exit /b 0
for /d %%D in ("%ProgramFiles%\PostgreSQL\*") do if exist "%%D\bin\psql.exe" set "PSQL=%%D\bin\psql.exe"
exit /b 0
