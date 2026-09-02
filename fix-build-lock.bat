@echo off
echo Killing Java/Gradle processes...
taskkill /F /IM java.exe /T 2>nul
taskkill /F /IM javaw.exe /T 2>nul
timeout /t 3 /nobreak >nul

echo Clearing build directory...
if exist "app\build" (
    rmdir /S /Q "app\build"
    echo Build directory cleared.
) else (
    echo Build directory already clean.
)

echo.
echo Done! Now sync Gradle in Android Studio (File > Sync Project with Gradle Files)
pause
