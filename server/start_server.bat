@echo off
setlocal
cd /d "%~dp0"

echo ============================================
echo   IPR Safety Camera - Server Launcher
echo ============================================
echo.

REM ── Check Python ─────────────────────────────────────────────────
where python >nul 2>&1
if %ERRORLEVEL% NEQ 0 (
    where py >nul 2>&1
    if %ERRORLEVEL% NEQ 0 (
        echo [ERROR] Python is not installed or not in PATH.
        echo Please run the IPR Safety Camera installer to set up Python.
        pause
        exit /b 1
    )
    set PY_CMD=py
) else (
    set PY_CMD=python
)

echo [OK] Python detected.

REM ── Create venv if needed ────────────────────────────────────────
if not exist "%~dp0venv" (
    echo [INFO] Creating virtual environment...
    %PY_CMD% -m venv "%~dp0venv"
)

REM ── Activate venv ────────────────────────────────────────────────
call "%~dp0venv\Scripts\activate.bat"

REM ── Install requirements ─────────────────────────────────────────
echo [INFO] Installing/updating dependencies...
pip install -r "%~dp0requirements.txt" --quiet
if %ERRORLEVEL% NEQ 0 (
    echo [ERROR] Failed to install dependencies. See output above.
    pause
    exit /b 1
)

echo.
echo ============================================
echo   Starting API Server on port 8000...
echo ============================================
echo.

python "%~dp0api_server.py"

endlocal
