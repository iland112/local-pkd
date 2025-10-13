# podman-health.sh - 헬스 체크 스크립트

#!/bin/bash
echo "🏥 컨테이너 헬스 체크..."
echo ""

# PostgreSQL 체크
echo "🐘 PostgreSQL:"
podman exec icao-local-pkd-postgres pg_isready -U postgres && echo "  ✅ 정상" || echo "  ❌ 오류"

# 컨테이너 상태
echo ""
echo "📊 컨테이너 상태:"
podman-compose -f podman-compose.yaml ps

# 볼륨 사용량
echo ""
echo "💾 볼륨 사용량:"
podman volume ls | grep icao-local-pkd

# 리소스 사용량
echo ""
echo "💻 리소스 사용량:"
podman stats --no-stream icao-local-pkd-postgres icao-local-pkd-pgadmin 2>/dev/null || echo "  ⚠️  stats 명령을 사용할 수 없습니다"

echo ""
echo "✅ 헬스 체크 완료!"