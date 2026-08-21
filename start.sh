#!/bin/bash

echo "======================================"
echo "  AI考勤智能助手 - 启动脚本"
echo "======================================"

PROJECT_DIR="$(cd "$(dirname "$0")" && pwd)"
cd "$PROJECT_DIR"

# 优先使用项目内置 JDK 8
# shellcheck source=/dev/null
source "$PROJECT_DIR/scripts/env-jdk8.sh"

check_java() {
    if ! command -v java &> /dev/null; then
        echo "错误: 未检测到 Java 环境，请先安装 JDK 1.8"
        exit 1
    fi

    JAVA_VERSION=$(java -version 2>&1 | awk -F'"' '/version/ {print $2; exit}')
    JAVA_MAJOR=$(echo "$JAVA_VERSION" | cut -d'.' -f1)
    JAVA_MINOR=$(echo "$JAVA_VERSION" | cut -d'.' -f2)
    if [ "$JAVA_MAJOR" != "1" ] || [ "$JAVA_MINOR" != "8" ]; then
        echo "错误: 本项目仅支持 JDK 1.8，当前版本: $JAVA_VERSION"
        exit 1
    fi
    echo "检测到 Java 版本: $JAVA_VERSION"
}

check_maven() {
    if ! command -v mvn &> /dev/null; then
        echo "错误: 未检测到 Maven，请先安装 Maven 3.6+"
        exit 1
    fi
    if ! "$PROJECT_DIR/scripts/mvn-jdk8.sh" -v -q -DskipTests >/dev/null 2>&1; then
        echo "错误: mvn-jdk8 无法运行，请检查 JDK 8（bash scripts/setup-jdk8.sh）"
        exit 1
    fi
    echo "检测到 Maven（JDK 8）"
}

check_mysql() {
    if ! command -v mysql &> /dev/null; then
        echo "警告: 未检测到MySQL命令行客户端"
        echo "请确保MySQL服务正在运行"
    else
        echo "检测到MySQL客户端"
    fi
}

init_database() {
    if [ "${SKIP_DB_INIT:-}" = "1" ]; then
        return 0
    fi
    echo ""
    echo ">>> 初始化数据库..."
    if [ -f "backend/config/init.sql" ]; then
        read -p "是否初始化数据库？(y/n): " -n 1 -r
        echo
        if [[ $REPLY =~ ^[Yy]$ ]]; then
            mysql -u root -p < backend/config/init.sql
            echo "数据库初始化完成"
        fi
    fi
}

start_backend() {
    echo ""
    echo ">>> 启动后端服务..."
    cd backend

    echo "编译项目（dev）..."
    "$PROJECT_DIR/scripts/mvn-jdk8.sh" compile -DskipTests -q

    echo "启动 Spring Boot (profile: dev)..."
    echo "请访问: http://localhost:8080/clockai/api"
    "$PROJECT_DIR/scripts/mvn-jdk8.sh" spring-boot:run -Dspring-boot.run.profiles=dev -DskipTests &
    BACKEND_PID=$!
    echo "后端启动中 (shell PID: $BACKEND_PID，Java 进程请用: lsof -i :8080)"

    cd ..
}

start_frontend() {
    echo ""
    echo ">>> 启动前端服务..."
    
    if [ ! -d "frontend/node_modules" ]; then
        echo "安装前端依赖..."
        cd frontend
        npm install
        cd ..
    fi
    
    cd frontend
    echo "启动Vue开发服务器..."
    npm run dev &
    FRONTEND_PID=$!
    
    echo "前端服务启动中 (PID: $FRONTEND_PID)..."
    echo "请访问: http://localhost:5175/clockai/"
    
    cd ..
}

show_usage() {
    echo ""
    echo "用法: ./start.sh [选项]"
    echo ""
    echo "选项:"
    echo "  all            启动所有服务 (后端 dev + 前端)"
    echo "  backend        仅启动后端 (profile: dev, localhost:8080)"
    echo "  dev            同 use-local-dev：杀 8080 后前台启动 dev（推荐）"
    echo "  frontend       仅启动前端 (http://localhost:5175/clockai/)"
    echo "  init           初始化数据库"
    echo "  apply          ★ 改 production.yaml 后执行（render + 按 mode 重启）"
    echo "  render-deploy  仅渲染配置（不重启）"
    echo "  prod           启动生产/UAT 后端（自动 render + 加载 env）"
    echo "  restart-prod   同 apply（兼容旧命令）"
    echo "  help           显示帮助信息"
    echo ""
    echo "本地开发:"
    echo "  ./start.sh dev"
    echo "  SKIP_DB_INIT=1 ./start.sh backend   # 跳过数据库初始化询问"
    echo ""
    echo "唯一配置: deploy/environments/production.yaml → ./start.sh apply"
    echo ""
}

render_deploy() {
    echo ""
    echo ">>> 渲染部署配置..."
    if ! command -v node &> /dev/null; then
        echo "错误: 需要 Node.js 运行 render 脚本"
        exit 1
    fi
    node "$PROJECT_DIR/scripts/render-deploy-config.mjs" --env production
    node "$PROJECT_DIR/scripts/render-deploy-config.mjs" --env uat
    echo ""
    echo "已生成:"
    echo "  deploy/rendered/production.env"
    echo "  deploy/rendered/uat.env"
    echo "  feishu-miniprogram/config.prod.js"
    echo "  feishu-miniprogram/config.runtime.js"
}

apply_site_config() {
    check_java
    check_maven
    exec bash "$PROJECT_DIR/scripts/apply-site-config.sh"
}

start_backend_prod() {
    check_java
    check_maven
    echo ""
    echo ">>> 启动生产/UAT 后端（自动 render + 加载 env）..."
    echo "改配置后: ./start.sh apply"
    exec bash "$PROJECT_DIR/scripts/start-backend-prod.sh"
}

restart_backend_prod() {
    check_java
    check_maven
    echo ""
    exec bash "$PROJECT_DIR/scripts/restart-backend-prod.sh"
}

case "${1:-all}" in
    all)
        check_java
        check_maven
        init_database
        start_backend
        start_frontend
        echo ""
        echo "所有服务已启动!"
        echo "按 Ctrl+C 停止服务"
        wait
        ;;
    backend)
        check_java
        check_maven
        init_database
        start_backend
        wait
        ;;
    dev|local)
        check_java
        check_maven
        exec bash "$PROJECT_DIR/scripts/use-local-dev.sh"
        ;;
    frontend)
        start_frontend
        wait
        ;;
    init)
        init_database
        ;;
    render-deploy|deploy-render)
        render_deploy
        ;;
    apply|site)
        apply_site_config
        ;;
    prod|backend-prod)
        start_backend_prod
        wait
        ;;
    restart-prod)
        restart_backend_prod
        ;;
    restart)
        echo "错误: ./start.sh restart 已移除（曾误指向生产）。"
        echo "  本地: ./start.sh dev  或  ./scripts/use-local-dev.sh"
        echo "  生产: ./start.sh restart-prod"
        exit 1
        ;;
    help|--help|-h)
        show_usage
        ;;
    *)
        echo "未知选项: $1"
        show_usage
        exit 1
        ;;
esac
