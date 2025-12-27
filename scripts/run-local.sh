#!/bin/bash
# run-local.sh - Local 프로파일로 애플리케이션 실행
# LDAP: localhost Docker (Write: 3891, Read: 389)
# DB: localhost:5432

set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_DIR="$(dirname "$SCRIPT_DIR")"
cd "$PROJECT_DIR"

echo "=========================================="
echo "  Local PKD - Local Profile"
echo "=========================================="
echo ""
echo "📌 연결 정보:"
echo "   - PostgreSQL: localhost:5432"
echo "   - LDAP Write: localhost:3891 (OpenLDAP 1)"
echo "   - LDAP Read:  localhost:389 (HAProxy)"
echo ""

# .env 파일 확인
if [ ! -f ".env" ]; then
    echo "⚠️  .env 파일이 없습니다. 기본값 사용:"
    echo "   LDAP_IP=localhost"
    echo "   LDAP_PORT=389"
    echo "   LDAP_USERNAME=cn=admin,dc=ldap,dc=smartcoreinc,dc=com"
    echo "   LDAP_PASSWORD=core"
    echo ""

    # 기본 .env 생성
    cat > .env << 'EOF'
LDAP_IP=localhost
LDAP_PORT=389
LDAP_USERNAME=cn=admin,dc=ldap,dc=smartcoreinc,dc=com
LDAP_PASSWORD=core
EOF
    echo "✅ .env 파일 생성 완료"
    echo ""
fi

# Docker 컨테이너 확인
echo "🔍 Docker 컨테이너 상태 확인..."
if ! docker ps --format '{{.Names}}' | grep -q "icao-local-pkd-postgres"; then
    echo "⚠️  PostgreSQL 컨테이너가 실행되지 않았습니다."
    echo "   먼저 실행: ./docker-start.sh --skip-app"
    exit 1
fi

if ! docker ps --format '{{.Names}}' | grep -q "icao-local-pkd-haproxy"; then
    echo "⚠️  HAProxy 컨테이너가 실행되지 않았습니다."
    echo "   먼저 실행: ./docker-start.sh --skip-app"
    exit 1
fi

echo "✅ Docker 컨테이너 정상 실행 중"
echo ""

# 애플리케이션 실행
echo "🚀 애플리케이션 시작 (local 프로파일)..."
echo ""
./mvnw spring-boot:run -Dspring-boot.run.profiles=local
