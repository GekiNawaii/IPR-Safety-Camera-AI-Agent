@echo off
setlocal

echo ============================================
echo   IPR Safety Camera - Build and Run
echo ============================================
echo.

REM ── Check if setup has been run ──────────────────────────────────
set LOCAL_MVN=%~dp0tools\maven\bin\mvn.cmd
if not exist "%LOCAL_MVN%" (
    echo [ERROR] Project not set up.
    echo Please run setup.bat first to download the required tools.
    pause
    exit /b 1
)

set MVN_CMD="%LOCAL_MVN%"

echo [1/2] Building application...
call %MVN_CMD% package -DskipTests
if %ERRORLEVEL% NEQ 0 (
    echo.
    echo [ERROR] Build failed. See output above.
    pause
    exit /b 1
)

echo.
echo [2/2] Launching application...
java -jar "%~dp0target\safety-camera.jar"

endlocal
