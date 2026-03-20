@echo off
setlocal

set MVN_VERSION=3.9.6
set MVN_DIR=%~dp0tools\maven
set MVN_CMD=%MVN_DIR%\bin\mvn.cmd
set DOWNLOAD_URL=https://archive.apache.org/dist/maven/maven-3/%MVN_VERSION%/binaries/apache-maven-%MVN_VERSION%-bin.zip
set ZIP_FILE=%~dp0tools\maven.zip

echo ============================================
echo   IPR Safety Camera - Setup
echo ============================================
echo.

REM ── Check Java ──────────────────────────────────────────────────
java -version >nul 2>&1
if %ERRORLEVEL% NEQ 0 (
    echo [ERROR] Java is not installed or not in PATH.
    echo Please install Java 11 or later from: https://adoptium.net/
    pause
    exit /b 1
)
echo [OK] Java detected.

REM ── Check / download Maven ───────────────────────────────────────
if exist "%MVN_CMD%" (
    echo [OK] Maven already present at tools\maven
    goto :build
)

echo.
echo [INFO] Maven not found. Downloading Apache Maven %MVN_VERSION%...
echo        URL: %DOWNLOAD_URL%
echo.

if not exist "%~dp0tools" mkdir "%~dp0tools"

REM Use curl (built into Windows 10 1803+) with progress bar
curl -L --progress-bar -o "%ZIP_FILE%" "%DOWNLOAD_URL%"

if %ERRORLEVEL% NEQ 0 (
    echo.
    echo [ERROR] Download failed. Check your internet connection.
    del /q "%ZIP_FILE%" 2>nul
    pause
    exit /b 1
)
echo.
echo [OK] Download complete.

echo [INFO] Extracting Maven...
REM Use tar (built into Windows 10 1803+) to extract zip
tar -xf "%ZIP_FILE%" -C "%~dp0tools"

REM Rename the versioned folder to simply "maven"
if exist "%~dp0tools\apache-maven-%MVN_VERSION%" (
    move "%~dp0tools\apache-maven-%MVN_VERSION%" "%MVN_DIR%" >nul
)

del /q "%ZIP_FILE%" 2>nul
echo [OK] Maven extracted to tools\maven
echo.
echo ============================================
echo   Setup Complete!
echo   You can now use run.bat to start the app.
echo ============================================

endlocal
