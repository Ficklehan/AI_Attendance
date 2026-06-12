@echo off
chcp 65001 > nul
setlocal

if /i "%~1"=="help" goto :usage
if /i "%~1"=="--help" goto :usage
if /i "%~1"=="-h" goto :usage
if /i "%~1"=="render-deploy" goto :render_deploy
if /i "%~1"=="deploy-render" goto :render_deploy

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
echo [3/4] 启动后端服务 (profile: dev)...

cd /d %PROJECT_DIR%backend
echo 正在编译并启动 Spring Boot（需 JDK 8，见 scripts\setup-jdk8.sh）...
start "Backend" cmd /k "bash ..\scripts\mvn-jdk8.sh compile -DskipTests -q && bash ..\scripts\mvn-jdk8.sh spring-boot:run -Dspring-boot.run.profiles=dev -DskipTests"

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
echo 后端: http://localhost:8080/attendance/api
echo 前端: http://localhost:5175/attendance/
echo 环境切换: production.yaml + Git Bash 执行 ./start.sh apply
echo.
echo 仅渲染配置: start.bat render-deploy  详见 deploy\README.md
echo.
echo 按任意键退出...
pause > nul
exit /b 0

:render_deploy
set PROJECT_DIR=%~dp0
cd /d %PROJECT_DIR%
where node > nul 2>&1
if %ERRORLEVEL% neq 0 (
    echo 错误: 需要 Node.js 运行 render 脚本
    pause
    exit /b 1
)
echo 渲染部署配置...
node "%PROJECT_DIR%scripts\render-deploy-config.mjs" --env production
node "%PROJECT_DIR%scripts\render-deploy-config.mjs" --env uat
echo.
echo 已生成 deploy\rendered\*.env、config.prod.js、config.runtime.js
pause
exit /b 0

:usage
echo.
echo 用法: start.bat [选项]
echo.
echo   (无参数)        启动后端 dev + 前端
echo   render-deploy   渲染公网配置 (production + uat)
echo   help            显示帮助
echo.
echo   改配置并重启请用 Git Bash: ./start.sh apply
echo   本地仅后端: bash scripts/use-local-dev.sh
echo.
echo 公网部署详见 deploy\README.md
pause
exit /b 0
