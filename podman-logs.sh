# podman-logs.sh - 로그 확인 스크립트

#!/bin/bash
SERVICE=${1:-}

if [ -z "$SERVICE" ]; then
    echo "📋 전체 컨테이너 로그:"
    podman-compose -f podman-compose.yaml logs -f
else
    echo "📋 $SERVICE 컨테이너 로그:"
    podman-compose -f podman-compose.yaml logs -f $SERVICE
fi
