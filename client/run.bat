@echo off
setlocal
cd /d "%~dp0"

echo ============================================
echo   IPR Safety Camera - Build and Run
echo ============================================
echo.

REM ── Check if setup has been run ──────────────────────────────────
set LOCAL_MVN=%~dp0tools\maven\bin\mvn.cmd
if not exist "%LOCAL_MVN%" (
    echo [INFO] Project not set up. Automatically running setup...
    call "%~dp0setup.bat"
)
if not exist "%LOCAL_MVN%" (
    echo [ERROR] Setup failed or Maven still missing.
    pause
    exit /b 1
)

set MVN_CMD="%LOCAL_MVN%"

REM ── Build only if JAR does not exist yet ─────────────────────────
set JAR_PATH=%~dp0target\safety-camera.jar
if exist "%JAR_PATH%" (
    echo [1/2] Application already built. Skipping build.
    echo       ^(Delete target\safety-camera.jar to force rebuild^)
) else (
    echo [1/2] Building application for the first time...
    call %MVN_CMD% package -DskipTests
    if %ERRORLEVEL% NEQ 0 (
        echo.
        echo [ERROR] Build failed. See output above.
        pause
        exit /b 1
    )
)

echo.
echo [2/2] Launching application...
java -jar "%JAR_PATH%"

endlocal
