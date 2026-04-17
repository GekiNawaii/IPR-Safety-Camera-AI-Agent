@echo off
setlocal

echo ============================================
echo   IPR Safety Camera - Build Installer
echo ============================================
echo.

set NSIS_DIR=%~dp0tools\nsis
set MAKENSIS=%NSIS_DIR%\makensis.exe

REM ── Check if NSIS is already in tools ───────────────────────────
if exist "%MAKENSIS%" (
    echo [OK] NSIS found at tools\nsis
    goto :build
)

REM ── Check if makensis is in PATH ────────────────────────────────
where makensis >nul 2>&1
if %ERRORLEVEL% EQU 0 (
    set MAKENSIS=makensis
    echo [OK] NSIS found in PATH
    goto :build
)

REM ── Check common install locations ──────────────────────────────
if exist "C:\Program Files (x86)\NSIS\makensis.exe" (
    set MAKENSIS="C:\Program Files (x86)\NSIS\makensis.exe"
    echo [OK] NSIS found at Program Files
    goto :build
)
if exist "C:\Program Files\NSIS\makensis.exe" (
    set MAKENSIS="C:\Program Files\NSIS\makensis.exe"
    echo [OK] NSIS found at Program Files
    goto :build
)

REM ── NSIS not found – try to install via winget ──────────────────
echo [INFO] NSIS not found. Attempting to install via winget...
echo.
where winget >nul 2>&1
if %ERRORLEVEL% EQU 0 (
    winget install NSIS.NSIS --accept-package-agreements --accept-source-agreements
    if %ERRORLEVEL% EQU 0 (
        echo.
        echo [OK] NSIS installed via winget.
        echo [INFO] Please restart this script so the new PATH is picked up.
        exit /b 0
    )
)

echo.
echo ============================================
echo   NSIS is required to build the installer.
echo ============================================
echo.
echo   Please install NSIS using one of these methods:
echo.
echo   Option 1: winget install NSIS.NSIS
echo   Option 2: Download from https://nsis.sourceforge.io/Download
echo.
echo   After installing, re-run this script.
echo.
pause
exit /b 1

:build
echo ============================================
echo   Compiling installer...
echo ============================================
echo.

%MAKENSIS% "%~dp0installer.nsi"

if %ERRORLEVEL% NEQ 0 (
    echo.
    echo [ERROR] Installer compilation failed. See output above.
    pause
    exit /b 1
)

echo.
echo ============================================
echo   SUCCESS! Installer created:
echo   IPR-Safety-Camera-Setup.exe
echo ============================================
echo.

endlocal
pause
