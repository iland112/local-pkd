#!/bin/bash
# =============================================================================
# Native Image Run Script for Local PKD Application
# =============================================================================
#
# GraalVM Native Image 실행 스크립트
#
# 사전 요구사항:
#   - Native Image 빌드 완료 (target/local-pkd 존재)
#   - PostgreSQL, OpenLDAP 컨테이너 실행 중
#
# 사용법:
#   ./scripts/native-run.sh [--debug] [--port=8081]
#
# =============================================================================

set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_DIR="$(dirname "$SCRIPT_DIR")"

cd "$PROJECT_DIR"

# 색상 정의
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

echo -e "${BLUE}=============================================${NC}"
echo -e "${BLUE}  Local PKD - Native Image Runner${NC}"
echo -e "${BLUE}=============================================${NC}"

# 기본값
PORT=8081
DEBUG=""
PROFILE="native"

# 인자 파싱
for arg in "$@"; do
    case $arg in
        --debug)
            DEBUG="-agentlib:native-image-agent=config-output-dir=target/native-agent-config"
            echo -e "${YELLOW}[INFO] Debug mode enabled (native-image-agent)${NC}"
            ;;
        --port=*)
            PORT="${arg#*=}"
            echo -e "${YELLOW}[INFO] Using port: $PORT${NC}"
            ;;
        --profile=*)
            PROFILE="${arg#*=}"
            echo -e "${YELLOW}[INFO] Using profile: $PROFILE${NC}"
            ;;
    esac
done

# Native 실행 파일 확인
echo -e "\n${BLUE}[1/3] Checking native executable...${NC}"
if [ ! -f "target/local-pkd" ]; then
    echo -e "${RED}[ERROR] Native executable not found: target/local-pkd${NC}"
    echo -e "${YELLOW}Run ./scripts/native-build.sh first${NC}"
    exit 1
fi

echo -e "${GREEN}[OK] Native executable found${NC}"

# 컨테이너 확인
echo -e "\n${BLUE}[2/3] Checking required containers...${NC}"
if command -v podman &> /dev/null; then
    CONTAINER_CMD="podman"
elif command -v docker &> /dev/null; then
    CONTAINER_CMD="docker"
else
    echo -e "${YELLOW}[WARN] No container runtime found${NC}"
    CONTAINER_CMD=""
fi

CONTAINERS_OK=true

if [ -n "$CONTAINER_CMD" ]; then
    # PostgreSQL 확인
    if $CONTAINER_CMD ps --format "{{.Names}}" | grep -q "local-pkd-postgres"; then
        echo -e "${GREEN}[OK] PostgreSQL running${NC}"
    else
        echo -e "${RED}[ERROR] PostgreSQL container not running${NC}"
        CONTAINERS_OK=false
    fi

    # OpenLDAP 확인
    if $CONTAINER_CMD ps --format "{{.Names}}" | grep -q "local-pkd-ldap"; then
        echo -e "${GREEN}[OK] OpenLDAP running${NC}"
    else
        echo -e "${RED}[ERROR] OpenLDAP container not running${NC}"
        CONTAINERS_OK=false
    fi

    if [ "$CONTAINERS_OK" = false ]; then
        echo -e "${YELLOW}Start containers with: ./podman-start.sh${NC}"
        exit 1
    fi
fi

# 포트 확인
if lsof -Pi :$PORT -sTCP:LISTEN -t >/dev/null 2>&1; then
    echo -e "${RED}[ERROR] Port $PORT is already in use${NC}"
    echo -e "${YELLOW}Kill existing process: lsof -ti:$PORT | xargs kill -9${NC}"
    exit 1
fi

# 실행
echo -e "\n${BLUE}[3/3] Starting application...${NC}"
echo -e "${GREEN}URL: http://localhost:$PORT${NC}"
echo -e "${YELLOW}Press Ctrl+C to stop${NC}\n"

# 환경 변수 설정
export SERVER_PORT=$PORT

# 실행
exec ./target/local-pkd \
    --spring.profiles.active=$PROFILE \
    --server.port=$PORT \
    $DEBUG
