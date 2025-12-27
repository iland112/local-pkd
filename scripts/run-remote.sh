#!/bin/bash
# run-remote.sh - Remote 프로파일로 애플리케이션 실행
# LDAP: 192.168.100.10 (Write: 389, Read: 10389)
# DB: localhost:5432

set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_DIR="$(dirname "$SCRIPT_DIR")"
cd "$PROJECT_DIR"

echo "=========================================="
echo "  Local PKD - Remote Profile"
echo "=========================================="
echo ""
echo "📌 연결 정보:"
echo "   - PostgreSQL: localhost:5432"
echo "   - LDAP Write: 192.168.100.10:389 (OpenLDAP Master)"
echo "   - LDAP Read:  192.168.100.10:10389 (HAProxy)"
echo ""

# .env 파일 확인 및 생성
if [ ! -f ".env" ]; then
    echo "⚠️  .env 파일이 없습니다. 기본값으로 생성:"
    cat > .env << 'EOF'
LDAP_IP=192.168.100.10
LDAP_PORT=389
LDAP_USERNAME=cn=admin,dc=ldap,dc=smartcoreinc,dc=com
LDAP_PASSWORD=core
EOF
    echo "✅ .env 파일 생성 완료"
    echo ""
fi

# PostgreSQL 컨테이너 확인
echo "🔍 로컬 PostgreSQL 컨테이너 확인..."
if ! docker ps --format '{{.Names}}' | grep -q "icao-local-pkd-postgres"; then
    echo "⚠️  PostgreSQL 컨테이너가 실행되지 않았습니다."
    echo "   먼저 실행: ./docker-start.sh --skip-app --skip-ldap"
    exit 1
fi
echo "✅ PostgreSQL 컨테이너 정상 실행 중"
echo ""

# Remote LDAP 연결 테스트
echo "🔍 원격 LDAP 서버 연결 테스트..."
if nc -z -w3 192.168.100.10 389 2>/dev/null; then
    echo "   ✅ LDAP Write (192.168.100.10:389) - 연결 가능"
else
    echo "   ⚠️  LDAP Write (192.168.100.10:389) - 연결 불가"
    echo "   원격 LDAP 서버가 실행 중인지 확인하세요."
fi

if nc -z -w3 192.168.100.10 10389 2>/dev/null; then
    echo "   ✅ LDAP Read (192.168.100.10:10389) - 연결 가능"
else
    echo "   ⚠️  LDAP Read (192.168.100.10:10389) - 연결 불가"
    echo "   HAProxy가 실행 중인지 확인하세요."
fi
echo ""

# 애플리케이션 실행
echo "🚀 애플리케이션 시작 (remote 프로파일)..."
echo ""
./mvnw spring-boot:run -Dspring-boot.run.profiles=remote
