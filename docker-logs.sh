#!/bin/bash
# docker-logs.sh - 로그 확인 스크립트

SERVICE=${1:-}

if [ -z "$SERVICE" ]; then
    echo "📋 전체 컨테이너 로그:"
    echo "   (Ctrl+C로 종료)"
    echo ""
    docker compose logs -f
else
    echo "📋 $SERVICE 컨테이너 로그:"
    echo "   (Ctrl+C로 종료)"
    echo ""
    docker compose logs -f $SERVICE
fi
