@echo off
setlocal EnableExtensions

set "BACKEND_DIR=%~dp0"
for %%I in ("%BACKEND_DIR%..") do set "PROJECT_ROOT=%%~fI"
set "ENV_FILE=%PROJECT_ROOT%\.env"

echo ================================
echo  WhatsApp Agent v3 - Start
echo ================================
echo Projekt: %PROJECT_ROOT%

if exist "%ENV_FILE%" (
    echo Lade Umgebung aus: %ENV_FILE%
    for /f "usebackq eol=# tokens=1,* delims==" %%A in ("%ENV_FILE%") do (
        if not "%%A"=="" set "%%A=%%B"
    )
) else (
    echo Keine .env gefunden, nutze Defaults.
)

if not defined BACKEND_PORT set "BACKEND_PORT=8000"
if not defined BACKEND_HOST set "BACKEND_HOST=0.0.0.0"
if not defined APP_API_TOKEN set "APP_API_TOKEN=dev-token-change-me"
if not defined BACKEND_BASE_URL set "BACKEND_BASE_URL=https://humming-opposite-deforest.ngrok-free.dev/"
if not defined CONDA_ENV set "CONDA_ENV=base"
if not defined CONDA_ACTIVATE set "CONDA_ACTIVATE=%USERPROFILE%\anaconda3\Scripts\activate.bat"

set "NGROK_DOMAIN=%BACKEND_BASE_URL%"
set "NGROK_DOMAIN=%NGROK_DOMAIN:https://=%"
set "NGROK_DOMAIN=%NGROK_DOMAIN:http://=%"
if "%NGROK_DOMAIN:~-1%"=="/" set "NGROK_DOMAIN=%NGROK_DOMAIN:~0,-1%"

set "START_NGROK=1"
echo %BACKEND_BASE_URL% | findstr /i "localhost 127.0.0.1 10.0.2.2" >nul && set "START_NGROK=0"
if /i "%NGROK_DISABLED%"=="1" set "START_NGROK=0"

echo.
echo Backend URL fuer Android: %BACKEND_BASE_URL%
echo Backend lokal: http://%BACKEND_HOST%:%BACKEND_PORT%
echo Conda Env: %CONDA_ENV%
echo.

if "%START_NGROK%"=="1" (
    where ngrok >nul 2>nul
    if errorlevel 1 (
        echo WARNUNG: ngrok wurde nicht gefunden. Backend startet trotzdem lokal.
    ) else (
        echo Starte ngrok Tunnel fuer Domain: %NGROK_DOMAIN%
        start "Ngrok Tunnel" cmd /k "ngrok http --domain=%NGROK_DOMAIN% %BACKEND_PORT%"
        timeout /t 3 /nobreak >nul
    )
) else (
    echo ngrok wird uebersprungen fuer lokale BACKEND_BASE_URL.
)

if not exist "%CONDA_ACTIVATE%" (
    echo FEHLER: Conda activate.bat nicht gefunden:
    echo %CONDA_ACTIVATE%
    echo Setze CONDA_ACTIVATE in .env, falls Anaconda woanders installiert ist.
    pause
    exit /b 1
)

echo Starte FastAPI Backend...
start "WhatsApp Backend v3" cmd /k "cd /d ""%PROJECT_ROOT%"" && call ""%CONDA_ACTIVATE%"" %CONDA_ENV% && python -m uvicorn backend.main:app --host %BACKEND_HOST% --port %BACKEND_PORT% --reload"

echo.
echo Backend und ggf. ngrok wurden gestartet.
echo Android BuildConfig muss dieselbe BACKEND_BASE_URL und APP_API_TOKEN verwenden.
echo Beispiel:
echo   .\gradlew.bat :app:assembleDebug -PBACKEND_BASE_URL=%BACKEND_BASE_URL% -PAPP_API_TOKEN=...
echo.
