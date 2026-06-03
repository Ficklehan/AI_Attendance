#!/bin/bash

echo "======================================"
echo "  AI考勤智能助手 - 启动脚本"
echo "======================================"

PROJECT_DIR="$(cd "$(dirname "$0")" && pwd)"
cd "$PROJECT_DIR"

check_java() {
    if ! command -v java &> /dev/null; then
        echo "错误: 未检测到Java环境，请先安装JDK 1.8+"
        exit 1
    fi
    
    JAVA_VERSION=$(java -version 2>&1 | head -n 1 | cut -d'"' -f2 | cut -d'.' -f1-2)
    echo "检测到Java版本: $JAVA_VERSION"
}

check_maven() {
    if ! command -v mvn &> /dev/null; then
        echo "错误: 未检测到Maven，请先安装Maven 3.6+"
        exit 1
    fi
    echo "检测到Maven"
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
    
    if [ ! -d "target" ]; then
        echo "编译项目..."
        mvn clean compile -DskipTests
    fi
    
    echo "启动Spring Boot应用..."
    mvn spring-boot:run -DskipTests &
    BACKEND_PID=$!
    
    echo "后端服务启动中 (PID: $BACKEND_PID)..."
    echo "请访问: http://localhost:8080/attendance/api"
    
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
    echo "请访问: http://localhost:5175"
    
    cd ..
}

show_usage() {
    echo ""
    echo "用法: ./start.sh [选项]"
    echo ""
    echo "选项:"
    echo "  all         启动所有服务 (后端+前端)"
    echo "  backend     仅启动后端服务"
    echo "  frontend    仅启动前端服务"
    echo "  init        初始化数据库"
    echo "  help        显示帮助信息"
    echo ""
    echo "示例:"
    echo "  ./start.sh all       启动所有服务"
    echo "  ./start.sh backend   仅启动后端"
    echo ""
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
    frontend)
        start_frontend
        wait
        ;;
    init)
        init_database
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
