@echo off
setlocal
echo ==========================================
echo   SETUP GITHUB REPOSITORY - CLOUDSTREAM
echo ==========================================
echo.

:: Check Git
where git >nul 2>nul
if %errorlevel% neq 0 (
    echo [ERROR] Git belum terinstall di komputer ini!
    echo Silakan download dan install Git dulu dari: https://git-scm.com/download/win
    echo Setelah install, restart komputer atau terminal ini, lalu jalankan script ini lagi.
    echo.
    pause
    exit /b
)

echo [1/5] Inisialisasi Git...
if not exist .git (
    git init
) else (
    echo Git sudah diinisialisasi.
)

echo [2/5] Menambahkan file ke staging...
git add .

echo [3/5] Membuat commit pertama...
git commit -m "Initial commit: Cloudstream Bstation Extension"

echo.
echo ========================================================
echo PENTING: Anda harus membuat Repository KOSONG di GitHub.
echo 1. Buka https://github.com/new
echo 2. Beri nama (misal: Cloudstream-Extensions)
echo 3. Pilih 'Public'
echo 4. JANGAN centang Initialize with README/gitignore
echo 5. Klik 'Create repository'
echo 6. Copy URL HTTPS-nya (contoh: https://github.com/User/Repo.git)
echo ========================================================
echo.

set /p REPO_URL="Masukkan URL Repository GitHub Anda: "

if "%REPO_URL%"=="" (
    echo URL tidak boleh kosong!
    pause
    exit /b
)

echo [4/5] Menambahkan Remote Origin...
git remote remove origin >nul 2>nul
git remote add origin %REPO_URL%

echo [5/5] Mengupload ke GitHub...
git branch -M main
git push -u origin main

if %errorlevel% neq 0 (
    echo.
    echo [ERROR] Gagal upload! Pastikan:
    echo 1. URL Repository benar.
    echo 2. Repository KOSONG (belum ada file).
    echo 3. Anda sudah login jika diminta.
    echo.
) else (
    echo.
    echo [SUCCESS] Berhasil upload!
    echo Cek tab 'Actions' di repository GitHub Anda untuk melihat proses build.
)

pause
