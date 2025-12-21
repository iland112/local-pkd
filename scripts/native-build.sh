#!/bin/bash
# =============================================================================
# Native Image Build Script for Local PKD Application
# =============================================================================
#
# GraalVM Native Image 빌드 스크립트
#
# 사전 요구사항:
#   - GraalVM 21 설치 및 JAVA_HOME 설정
#   - native-image 설치: gu install native-image
#   - PostgreSQL, OpenLDAP 컨테이너 실행 중
#
# 사용법:
#   ./scripts/native-build.sh [--skip-tests] [--clean]
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
echo -e "${BLUE}  Local PKD - Native Image Build${NC}"
echo -e "${BLUE}=============================================${NC}"

# 인자 파싱
SKIP_TESTS=""
CLEAN=""

for arg in "$@"; do
    case $arg in
        --skip-tests)
            SKIP_TESTS="-DskipTests"
            echo -e "${YELLOW}[INFO] Tests will be skipped${NC}"
            ;;
        --clean)
            CLEAN="clean"
            echo -e "${YELLOW}[INFO] Clean build enabled${NC}"
            ;;
    esac
done

# GraalVM 확인
echo -e "\n${BLUE}[1/4] Checking GraalVM...${NC}"
if ! java -version 2>&1 | grep -q "GraalVM"; then
    echo -e "${YELLOW}[WARN] GraalVM may not be active. Current Java:${NC}"
    java -version
    echo -e "${YELLOW}Consider setting JAVA_HOME to GraalVM installation${NC}"
fi

# native-image 확인
if ! command -v native-image &> /dev/null; then
    echo -e "${RED}[ERROR] native-image not found. Install with: gu install native-image${NC}"
    exit 1
fi

echo -e "${GREEN}[OK] GraalVM and native-image ready${NC}"

# 컨테이너 확인
echo -e "\n${BLUE}[2/4] Checking containers...${NC}"
if command -v podman &> /dev/null; then
    CONTAINER_CMD="podman"
elif command -v docker &> /dev/null; then
    CONTAINER_CMD="docker"
else
    echo -e "${YELLOW}[WARN] No container runtime found (podman/docker)${NC}"
    CONTAINER_CMD=""
fi

if [ -n "$CONTAINER_CMD" ]; then
    # PostgreSQL 확인
    if $CONTAINER_CMD ps --format "{{.Names}}" | grep -q "local-pkd-postgres"; then
        echo -e "${GREEN}[OK] PostgreSQL container running${NC}"
    else
        echo -e "${YELLOW}[WARN] PostgreSQL container not running${NC}"
    fi

    # OpenLDAP 확인
    if $CONTAINER_CMD ps --format "{{.Names}}" | grep -q "local-pkd-ldap"; then
        echo -e "${GREEN}[OK] OpenLDAP container running${NC}"
    else
        echo -e "${YELLOW}[WARN] OpenLDAP container not running${NC}"
    fi
fi

# 빌드 실행
echo -e "\n${BLUE}[3/4] Building Native Image...${NC}"
echo -e "${YELLOW}This may take 5-10 minutes...${NC}"

BUILD_CMD="./mvnw $CLEAN -Pnative native:compile $SKIP_TESTS"
echo -e "${BLUE}Executing: $BUILD_CMD${NC}"

START_TIME=$(date +%s)

if $BUILD_CMD; then
    END_TIME=$(date +%s)
    ELAPSED=$((END_TIME - START_TIME))

    echo -e "\n${GREEN}=============================================${NC}"
    echo -e "${GREEN}  Build Successful!${NC}"
    echo -e "${GREEN}=============================================${NC}"
    echo -e "Build time: ${ELAPSED} seconds"

    # 빌드 결과 확인
    echo -e "\n${BLUE}[4/4] Checking build output...${NC}"
    if [ -f "target/local-pkd" ]; then
        SIZE=$(du -h target/local-pkd | cut -f1)
        echo -e "${GREEN}[OK] Native executable: target/local-pkd (${SIZE})${NC}"
        echo -e "\n${BLUE}Run with:${NC}"
        echo -e "  ./scripts/native-run.sh"
        echo -e "  OR"
        echo -e "  ./target/local-pkd --spring.profiles.active=native"
    else
        echo -e "${RED}[ERROR] Native executable not found${NC}"
        exit 1
    fi
else
    echo -e "\n${RED}=============================================${NC}"
    echo -e "${RED}  Build Failed!${NC}"
    echo -e "${RED}=============================================${NC}"
    echo -e "${YELLOW}Check the error messages above.${NC}"
    echo -e "${YELLOW}Common issues:${NC}"
    echo -e "  - Missing reflection config for BouncyCastle classes"
    echo -e "  - Hibernate proxy generation issues"
    echo -e "  - Resource files not included"
    exit 1
fi
