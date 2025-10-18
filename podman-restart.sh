#!/bin/bash
# podman-restart.sh - Podman 컨테이너 재시작 스크립트

echo "🔄 ICAO PKD Podman 컨테이너 재시작..."

podman-compose -f podman-compose.yaml restart

echo ""
echo "📊 컨테이너 상태:"
podman-compose -f podman-compose.yaml ps

echo ""
echo "✅ 컨테이너 재시작 완료!"