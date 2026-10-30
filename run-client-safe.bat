@echo off
REM 安全启动客户端：先确保编译完整，再启动游戏，避免"边构建边开游戏"导致 NoClassDefFoundError。
chcp 65001 >nul
setlocal
set "ROOT=%~dp0"
cd /d "%ROOT%"

echo [1/2] 编译检查...
call "%ROOT%hc-gradle.bat" compileJava -PenableEpicFightCompat=false --offline
if errorlevel 1 (
    echo.
    echo [ERROR] 编译失败，已中止启动。先修好编译错误再试。
    exit /b 1
)

set "BASE=%ROOT%build\classes\java\main\com\whitecloud233\modid\herobrine_companion"
if not exist "%BASE%\entity\logic\HeroInvitationHelper.class" (
    echo [ERROR] 编译产物不完整，缺少 HeroInvitationHelper.class
    exit /b 1
)
if not exist "%BASE%\config\TtsSettingsScreen.class" (
    echo [ERROR] 编译产物不完整，缺少 TtsSettingsScreen.class
    exit /b 1
)

echo [2/2] 编译完整，启动客户端...
call "%ROOT%hc-gradle.bat" runClient -PenableEpicFightCompat=false
endlocal
