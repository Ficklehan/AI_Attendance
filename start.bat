@echo off
chcp 65001 > nul
echo ======================================
echo   AI考勤智能助手 - 启动脚本 (Windows)
echo ======================================

set PROJECT_DIR=%~dp0
cd /d %PROJECT_DIR%

echo.
echo [1/4] 检查环境...

where java > nul 2>&1
if %ERRORLEVEL% neq 0 (
    echo 错误: 未检测到Java环境，请先安装JDK 1.8+
    pause
    exit /b 1
)

where mvn > nul 2>&1
if %ERRORLEVEL% neq 0 (
    echo 错误: 未检测到Maven，请先安装Maven 3.6+
    pause
    exit /b 1
)

echo 环境检查完成

echo.
echo [2/4] 检查数据库...

set /p INIT_DB="是否初始化数据库？(y/n): "
if /i "%INIT_DB%"=="y" (
    if exist "backend\config\init.sql" (
        echo 请手动执行: mysql -u root -p ^< backend\config\init.sql
    )
)

echo.
echo [3/4] 启动后端服务...

cd /d %PROJECT_DIR%backend
echo 正在启动Spring Boot...
start "Backend" cmd /k "mvn spring-boot:run -DskipTests"

cd /d %PROJECT_DIR%

echo.
echo [4/4] 启动前端服务...

cd /d %PROJECT_DIR%frontend
if not exist "node_modules" (
    echo 安装前端依赖...
    call npm install
)

echo 正在启动Vue开发服务器...
start "Frontend" cmd /k "npm run dev"

cd /d %PROJECT_DIR%

echo.
echo ======================================
echo   服务已启动！
echo ======================================
echo.
echo 后端: http://localhost:3000/api
echo 前端: http://localhost:5175
echo.
echo 按任意键退出...
pause > nul
