#!/bin/bash
# docker-clean.sh - 완전 삭제 스크립트

echo "⚠️  경고: 모든 데이터가 삭제됩니다!"
read -p "계속하시겠습니까? (yes/no): " confirm

if [ "$confirm" != "yes" ]; then
    echo "취소되었습니다."
    exit 0
fi

echo "🗑️  컨테이너 및 볼륨 삭제 중..."

# 컨테이너 중지 및 삭제
docker compose down -v

# 볼륨 삭제
echo "📦 볼륨 삭제 중..."
docker volume rm icao-local-pkd-postgres_data 2>/dev/null || true
docker volume rm icao-local-pkd-pgadmin_data 2>/dev/null || true

# 네트워크 삭제
echo "🌐 네트워크 삭제 중..."
docker network rm local-pkd_default 2>/dev/null || true

echo "✅ 삭제 완료!"
