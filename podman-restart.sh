# podman-restart.sh - Podman 컨테이너 재시작 스크립트

#!/bin/bash
echo "🔄 ICAO PKD Podman 컨테이너 재시작..."

podman-compose -f podman-compose.yaml restart

echo "✅ 컨테이너 재시작 완료!"