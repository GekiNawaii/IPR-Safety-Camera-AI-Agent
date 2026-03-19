@echo off
echo ============================================
echo   IPR Safety Camera - Build and Run
echo ============================================

echo.
echo [1/2] Building application...
call mvn -q package -DskipTests
if %ERRORLEVEL% NEQ 0 (
    echo BUILD FAILED. See output above for errors.
    pause
    exit /b 1
)

echo [2/2] Launching application...
echo.
java -jar target\safety-camera.jar
