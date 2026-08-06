@echo off
REM Node-RED Dashboard Converter - Run Script (Windows)
REM This script starts the server which serves both the API and web UI

echo.
echo 🚀 Starting Node-RED Dashboard Converter...
echo.

REM Check if node_modules exists, if not install dependencies
if not exist "node_modules" (
    echo 📦 Installing dependencies...
    call npm install
    echo.
)

REM Check if node is available
where node >nul 2>nul
if %ERRORLEVEL% NEQ 0 (
    echo ❌ Error: Node.js is not installed or not in PATH
    echo Please install Node.js from https://nodejs.org/
    pause
    exit /b 1
)

REM Get the port from environment or use default
if "%PORT%"=="" (
    set PORT=3000
)

echo ✅ Dependencies installed
echo 🌐 Starting server on port %PORT%...
echo.
echo 📝 Access the web interface at: http://localhost:%PORT%
echo.
echo Press Ctrl+C to stop the server
echo.

REM Start the server
node server.js

pause

