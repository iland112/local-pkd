# podman-stop.sh - Podman 컨테이너 중지 스크립트

#!/bin/bash
echo "🛑 ICAO PKD Podman 컨테이너 중지..."

podman-compose -f podman-compose.yaml down

echo "✅ 컨테이너 중지 완료!"
