#!/bin/bash
# docker-start.sh - Docker 컨테이너 시작 스크립트

set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$SCRIPT_DIR"

# 옵션 파싱
BUILD_FLAG=""
SKIP_APP=""
SKIP_LDAP=""
while [[ $# -gt 0 ]]; do
    case $1 in
        --build)
            BUILD_FLAG="--build"
            shift
            ;;
        --skip-app)
            SKIP_APP="true"
            shift
            ;;
        --skip-ldap)
            SKIP_LDAP="true"
            shift
            ;;
        *)
            shift
            ;;
    esac
done

echo "🚀 ICAO PKD Docker 컨테이너 시작..."

# 1. 필요한 디렉토리 생성
echo "📁 디렉토리 생성 중..."
mkdir -p ./data/uploads
mkdir -p ./data/temp
mkdir -p ./logs

# 2. Native Image 확인 (local-pkd 빌드 시 필요)
if [ -z "$SKIP_APP" ] && [ ! -f "target/local-pkd" ]; then
    echo ""
    echo "⚠️  Native Image가 없습니다."
    echo "   Local PKD 앱을 포함하려면 먼저 빌드하세요:"
    echo "   ./scripts/native-build.sh --skip-tests"
    echo ""
    echo "   또는 DB만 시작하려면: ./docker-start.sh --skip-app"
    exit 1
fi

# 3. Docker Compose 시작
echo "🐳 Docker Compose 시작..."
if [ -n "$SKIP_APP" ]; then
    if [ -n "$SKIP_LDAP" ]; then
        # PostgreSQL과 pgAdmin만 시작
        docker compose up -d $BUILD_FLAG postgres pgadmin
    else
        # PostgreSQL, pgAdmin, OpenLDAP, HAProxy 시작
        docker compose up -d $BUILD_FLAG postgres pgadmin openldap1 openldap2 haproxy phpldapadmin
    fi
else
    # 전체 서비스 시작
    docker compose up -d $BUILD_FLAG
fi

# 4. 컨테이너 상태 확인
echo ""
echo "⏳ 컨테이너 시작 대기 중..."
sleep 5

echo ""
echo "📊 컨테이너 상태:"
docker compose ps

echo ""
echo "✅ 컨테이너 시작 완료!"
echo ""
echo "📌 접속 정보:"
echo "   - PostgreSQL:    localhost:5432 (postgres/secret)"
echo "   - pgAdmin:       http://localhost:5050 (admin@smartcoreinc.com/admin)"
if [ -z "$SKIP_LDAP" ]; then
    echo "   - LDAP (HAProxy): ldap://localhost:389 (로드밸런싱)"
    echo "   - OpenLDAP 1:    ldap://localhost:3891 (직접 연결)"
    echo "   - OpenLDAP 2:    ldap://localhost:3892 (직접 연결)"
    echo "   - HAProxy Stats: http://localhost:8404/stats"
    echo "   - phpLDAPadmin:  http://localhost:8080"
    echo "   - LDAP Admin:    cn=admin,dc=ldap,dc=smartcoreinc,dc=com / core"
fi
if [ -z "$SKIP_APP" ]; then
    echo "   - Local PKD:     http://localhost:8081"
    echo "   - Swagger UI:    http://localhost:8081/swagger-ui.html"
fi
echo ""
echo "🔍 로그 확인: docker compose logs -f [서비스명]"
echo "🛑 중지:     ./docker-stop.sh"
echo ""
echo "💡 옵션:"
echo "   --build      이미지 다시 빌드"
echo "   --skip-app   Local PKD 앱 제외"
echo "   --skip-ldap  OpenLDAP 제외"
echo ""
if [ -z "$SKIP_LDAP" ]; then
    echo "📝 LDAP 초기화가 필요하면: ./docker-ldap-init.sh"
fi
