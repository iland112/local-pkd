#!/bin/bash
# podman-start.sh - Podman 컨테이너 시작 스크립트

set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$SCRIPT_DIR"

# 옵션 파싱
BUILD_FLAG=""
SKIP_APP=""
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
        *)
            shift
            ;;
    esac
done

echo "🚀 ICAO PKD Podman 컨테이너 시작..."

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
    echo "   또는 DB만 시작하려면: ./podman-start.sh --skip-app"
    exit 1
fi

# 3. Podman Compose 시작
echo "🐳 Podman Compose 시작..."
if [ -n "$SKIP_APP" ]; then
    # DB와 pgAdmin만 시작
    podman-compose -f podman-compose.yaml up -d $BUILD_FLAG postgres pgadmin 2>&1 | grep -v "failed to move the rootless netns slirp4netns process"
else
    # 전체 서비스 시작
    podman-compose -f podman-compose.yaml up -d $BUILD_FLAG 2>&1 | grep -v "failed to move the rootless netns slirp4netns process"
fi

# 4. 컨테이너 상태 확인
echo ""
echo "⏳ 컨테이너 시작 대기 중..."
sleep 10

echo ""
echo "📊 컨테이너 상태:"
podman-compose -f podman-compose.yaml ps

echo ""
echo "✅ 컨테이너 시작 완료!"
echo ""
echo "📌 접속 정보:"
echo "   - PostgreSQL:    localhost:5432 (postgres/secret)"
echo "   - pgAdmin:       http://localhost:5050 (admin@smartcoreinc.com/admin)"
if [ -z "$SKIP_APP" ]; then
    echo "   - Local PKD:     http://localhost:8081"
    echo "   - Swagger UI:    http://localhost:8081/swagger-ui.html"
fi
echo ""
echo "🔍 로그 확인: podman-compose -f podman-compose.yaml logs -f [서비스명]"
echo "🛑 중지:     ./podman-stop.sh"
echo ""
echo "💡 옵션:"
echo "   --build     이미지 다시 빌드"
echo "   --skip-app  DB만 시작 (Local PKD 앱 제외)"