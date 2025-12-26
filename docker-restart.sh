#!/bin/bash
# docker-restart.sh - Docker 컨테이너 재시작 스크립트

echo "🔄 ICAO PKD Docker 컨테이너 재시작..."

docker compose restart

echo ""
echo "📊 컨테이너 상태:"
docker compose ps

echo ""
echo "✅ 컨테이너 재시작 완료!"
