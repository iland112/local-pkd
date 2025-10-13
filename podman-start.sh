#!/bin/bash
# podman-start.sh - Podman 컨테이너 시작 스크립트

echo "🚀 ICAO PKD Podman 컨테이너 시작..."

# 1. 필요한 디렉토리 생성
echo "📁 디렉토리 생성 중..."
# mkdir -p ./init-scripts
mkdir -p ./data/uploads
mkdir -p ./data/temp
mkdir -p ./logs
mkdir -p ./ldap-schemas

# 2. Podman Compose 시작
echo "🐳 Podman Compose 시작..."
podman-compose -f podman-compose.yaml up -d

# 3. 컨테이너 상태 확인
echo ""
echo "⏳ 컨테이너 시작 대기 중..."
sleep 10

echo ""
echo "📊 컨테이너 상태:"
podman-compose -f podman-compose.yaml ps

echo ""
echo "✅ 컨테이너 시작 완료!"
echo ""
echo "📌 접속 정보:"
echo "   - PostgreSQL:    localhost:5432 (postgres/secret)"
echo "   - pgAdmin:       http://localhost:5050 (admin@icao.int/admin)"
echo ""
echo "🔍 로그 확인: podman-compose -f podman-compose.yaml logs -f [서비스명]"
echo "🛑 중지:     ./podman-stop.sh"