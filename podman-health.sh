#!/bin/bash
# podman-health.sh - 헬스 체크 스크립트

echo "🏥 컨테이너 헬스 체크..."
echo ""

# PostgreSQL 체크
echo "🐘 PostgreSQL:"
if podman exec icao-local-pkd-postgres pg_isready -U postgres > /dev/null 2>&1; then
    echo "  ✅ 정상 (ready to accept connections)"
else
    echo "  ❌ 오류 (not responding)"
fi

# 컨테이너 상태
echo ""
echo "📊 컨테이너 상태:"
podman-compose -f podman-compose.yaml ps

# 볼륨 사용량
echo ""
echo "💾 볼륨 사용량:"
podman volume ls | grep icao-local-pkd || echo "  ⚠️  볼륨을 찾을 수 없습니다"

# 리소스 사용량
echo ""
echo "💻 리소스 사용량:"
if podman stats --no-stream icao-local-pkd-postgres icao-local-pkd-pgadmin 2>/dev/null; then
    : # 성공 시 아무것도 하지 않음
else
    echo "  ⚠️  컨테이너가 실행 중이지 않거나 stats 명령을 사용할 수 없습니다"
fi

echo ""
echo "✅ 헬스 체크 완료!"