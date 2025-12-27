#!/bin/bash
# run-container.sh - Container 프로파일로 Docker 컨테이너 내 애플리케이션 실행
# Native Image를 Docker 컨테이너로 실행
# LDAP: Docker 네트워크 (Write: openldap1:389, Read: haproxy:389)
# DB: Docker 네트워크 (postgres:5432)

set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_DIR="$(dirname "$SCRIPT_DIR")"
cd "$PROJECT_DIR"

echo "=========================================="
echo "  Local PKD - Container Profile"
echo "=========================================="
echo ""
echo "📌 연결 정보 (Docker 네트워크):"
echo "   - PostgreSQL: localhost:5432"
echo "   - LDAP Write: localhost:3891 (OpenLDAP 1)"
echo "   - LDAP Read:  localhost:389 (HAProxy)"
echo "   - Application: http://localhost:8081"
echo ""

# Native Image 확인
if [ ! -f "target/local-pkd" ]; then
    echo "⚠️  Native Image가 없습니다."
    echo ""
    echo "빌드 방법:"
    echo "   ./scripts/native-build.sh --skip-tests"
    echo ""
    read -p "지금 빌드하시겠습니까? (y/n): " build_confirm
    if [ "$build_confirm" = "y" ] || [ "$build_confirm" = "Y" ]; then
        ./scripts/native-build.sh --skip-tests
    else
        echo "취소되었습니다."
        exit 1
    fi
fi

echo "✅ Native Image 확인: target/local-pkd"
echo ""

# Docker 컨테이너 시작 (전체)
echo "🔍 Docker 컨테이너 상태 확인..."
if ! docker ps --format '{{.Names}}' | grep -q "icao-local-pkd-postgres"; then
    echo "⚠️  컨테이너가 실행되지 않았습니다."
    echo ""
    read -p "docker-compose로 전체 서비스를 시작하시겠습니까? (y/n): " start_confirm
    if [ "$start_confirm" = "y" ] || [ "$start_confirm" = "Y" ]; then
        ./docker-start.sh
        exit 0
    else
        echo "취소되었습니다."
        exit 1
    fi
fi

# local-pkd 앱 컨테이너 확인
if docker ps --format '{{.Names}}' | grep -q "icao-local-pkd-app"; then
    echo "✅ Local PKD 앱 컨테이너가 이미 실행 중입니다."
    echo ""
    echo "📌 접속 정보:"
    echo "   - Application: http://localhost:8081"
    echo "   - Swagger UI:  http://localhost:8081/swagger-ui.html"
    echo ""
    echo "🔍 로그 확인: docker compose logs -f local-pkd"
    echo "🛑 중지:      docker compose stop local-pkd"
else
    echo "🚀 Local PKD 앱 컨테이너 시작..."
    docker compose up -d local-pkd
    echo ""
    echo "✅ 컨테이너 시작 완료!"
    echo ""
    echo "📌 접속 정보:"
    echo "   - Application: http://localhost:8081"
    echo "   - Swagger UI:  http://localhost:8081/swagger-ui.html"
    echo ""
    echo "🔍 로그 확인: docker compose logs -f local-pkd"
fi
