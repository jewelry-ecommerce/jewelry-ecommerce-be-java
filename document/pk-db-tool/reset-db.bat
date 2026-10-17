@echo off
setlocal EnableExtensions
rem ======================================================================
rem  reset-db.bat - XOA SACH database roi tao lai: 1. Table.sql roi 2. Data.sql
rem
rem  Chay: bam dup file nay, hoac go reset-db.bat trong cmd.
rem  Cau hinh ket noi: config\db.config.bat
rem
rem  - Chi chay voi DB tren chinh may nay (xem ALLOW_REMOTE trong config\db.config.bat).
rem  - Bat go lai ten database truoc khi xoa.
rem  - Moi buoc chay trong MOT phien psql voi ON_ERROR_STOP: loi o buoc nao la dung ngay.
rem    Moi file SQL tu boc BEGIN/COMMIT nen file loi thi rollback; nhung schema cu da bi xoa
rem    o buoc dau, nen neu loi thi DB o trang thai rong hoac thieu du lieu cho toi lan chay lai.
rem  - Nen tat app Spring Boot dang chay (hoac khoi dong lai sau do).
rem  - Dat NO_PAUSE=1 neu khong muon cua so cho bam phim o cuoi.
rem ======================================================================

call "%~dp0config\db.config.bat"

for %%I in ("%SQL_DIR%") do set "SQL_DIR=%%~fI"
set "TABLE_PATH=%SQL_DIR%\%TABLE_FILE%"
set "DATA_PATH=%SQL_DIR%\%DATA_FILE%"

"%PSQL%" --version >nul 2>&1
if errorlevel 1 goto :no_psql

set "IS_LOCAL=0"
if /i "%PGHOST%"=="localhost" set "IS_LOCAL=1"
if "%PGHOST%"=="127.0.0.1" set "IS_LOCAL=1"
if "%PGHOST%"=="::1" set "IS_LOCAL=1"
if "%IS_LOCAL%"=="1" goto :host_ok
if "%ALLOW_REMOTE%"=="1" goto :host_ok
goto :remote_refused
:host_ok

if not exist "%TABLE_PATH%" goto :no_table
if not exist "%DATA_PATH%" goto :no_data

echo.
echo  Sap XOA SACH database "%DB_NAME%" tai %PGHOST%:%PGPORT% roi tao lai tu dau.
echo  Toan bo bang va du lieu hien co trong do se mat.
set "CONFIRM="
set /p "CONFIRM= Go lai ten database de xac nhan: "
if /i not "%CONFIRM%"=="%DB_NAME%" goto :cancel

rem Bat buoc UTF-8, neu khong tieng Viet trong file SQL se loi hoac sai dau tren Windows.
set "PGCLIENTENCODING=UTF8"
set "PGOPTIONS=-c client_min_messages=warning"

echo.
echo  Dang chay...
"%PSQL%" -X -q -v ON_ERROR_STOP=1 -h "%PGHOST%" -p "%PGPORT%" -U "%PGUSER%" -d "%DB_NAME%" ^
  -c "DROP SCHEMA IF EXISTS public CASCADE" ^
  -c "CREATE SCHEMA public" ^
  -f "%TABLE_PATH%" ^
  -f "%DATA_PATH%" ^
  -c "SELECT (SELECT count(*) FROM information_schema.tables WHERE table_schema = 'public') AS so_bang, (SELECT count(*) FROM users) AS so_user" ^
  -c "SELECT phone AS tai_khoan_dang_nhap, full_name FROM users ORDER BY id"
if errorlevel 1 goto :fail

echo.
echo  XONG: da xoa, tao bang va insert du lieu mau cho "%DB_NAME%".
echo  Khoi dong lai app Spring Boot neu no dang chay.
set "EXITCODE=0"
goto :end

:no_psql
echo.
echo  LOI: khong tim thay psql: "%PSQL%"
echo       Cai PostgreSQL, hoac sua dong PSQL trong config\db.config.bat.
set "EXITCODE=1"
goto :end

:remote_refused
echo.
echo  LOI: PGHOST="%PGHOST%" khong phai may nay. Script tu choi xoa DB o may khac.
echo       Neu that su muon, dat ALLOW_REMOTE=1 trong config\db.config.bat.
set "EXITCODE=1"
goto :end

:no_table
echo.
echo  LOI: khong thay file: %TABLE_PATH%
set "EXITCODE=1"
goto :end

:no_data
echo.
echo  LOI: khong thay file: %DATA_PATH%
set "EXITCODE=1"
goto :end

:cancel
echo.
echo  Ten khong khop - da huy, khong thay doi gi.
set "EXITCODE=1"
goto :end

:fail
echo.
echo  LOI: psql dung giua chung. Xem thong bao o tren.
echo       DB co the dang rong hoac thieu du lieu - sua loi roi chay lai.
set "EXITCODE=1"
goto :end

:end
echo.
if not defined NO_PAUSE pause
exit /b %EXITCODE%
