#!/bin/bash
# docker-stop.sh - Docker 컨테이너 중지 스크립트

echo "🛑 ICAO PKD Docker 컨테이너 중지..."

docker compose down

echo "✅ 컨테이너 중지 완료!"
