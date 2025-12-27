#!/bin/bash
# ============================================================================
# ARM64 Native Image Build Script for Luckfox Omni3576
# ============================================================================
# Usage: ./scripts/build-arm64.sh [OPTIONS]
# Options:
#   --push          Push to registry after build
#   --no-cache      Build without cache
#   --load          Load image to local docker (for testing)
#   --tag=<tag>     Custom tag (default: latest)
#   --registry=<r>  Registry URL (default: none)
# ============================================================================

set -e

# Colors
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# Default values
IMAGE_NAME="local-pkd"
TAG="arm64-latest"
PLATFORM="linux/arm64"
DOCKERFILE="Dockerfile.arm64"
PUSH=false
NO_CACHE=""
LOAD=false
REGISTRY=""

# Parse arguments
for arg in "$@"; do
    case $arg in
        --push)
            PUSH=true
            shift
            ;;
        --no-cache)
            NO_CACHE="--no-cache"
            shift
            ;;
        --load)
            LOAD=true
            shift
            ;;
        --tag=*)
            TAG="${arg#*=}"
            shift
            ;;
        --registry=*)
            REGISTRY="${arg#*=}"
            shift
            ;;
        --help)
            echo "Usage: $0 [OPTIONS]"
            echo "Options:"
            echo "  --push          Push to registry after build"
            echo "  --no-cache      Build without cache"
            echo "  --load          Load image to local docker (for testing with QEMU)"
            echo "  --tag=<tag>     Custom tag (default: arm64-latest)"
            echo "  --registry=<r>  Registry URL (e.g., docker.io/username)"
            exit 0
            ;;
        *)
            echo -e "${RED}Unknown option: $arg${NC}"
            exit 1
            ;;
    esac
done

# Build full image name
if [ -n "$REGISTRY" ]; then
    FULL_IMAGE_NAME="${REGISTRY}/${IMAGE_NAME}:${TAG}"
else
    FULL_IMAGE_NAME="${IMAGE_NAME}:${TAG}"
fi

echo -e "${BLUE}============================================================================${NC}"
echo -e "${BLUE}ARM64 Native Image Build for Luckfox Omni3576${NC}"
echo -e "${BLUE}============================================================================${NC}"
echo -e "${YELLOW}Image:${NC} ${FULL_IMAGE_NAME}"
echo -e "${YELLOW}Platform:${NC} ${PLATFORM}"
echo -e "${YELLOW}Dockerfile:${NC} ${DOCKERFILE}"
echo ""

# Check Docker buildx
echo -e "${BLUE}[1/4] Checking Docker Buildx...${NC}"
if ! docker buildx version > /dev/null 2>&1; then
    echo -e "${RED}Docker Buildx is not available. Please install it first.${NC}"
    exit 1
fi

# Ensure QEMU is setup for ARM64
echo -e "${BLUE}[2/4] Ensuring QEMU ARM64 emulation...${NC}"
if ! docker run --rm --privileged multiarch/qemu-user-static --reset -p yes > /dev/null 2>&1; then
    echo -e "${YELLOW}Warning: Could not reset QEMU. Continuing anyway...${NC}"
fi

# Create/use ARM64 builder
echo -e "${BLUE}[3/4] Setting up ARM64 builder...${NC}"
if ! docker buildx inspect arm64builder > /dev/null 2>&1; then
    echo "Creating new ARM64 builder..."
    docker buildx create --name arm64builder --driver docker-container --platform linux/arm64,linux/amd64
fi
docker buildx use arm64builder

# Build image
echo -e "${BLUE}[4/4] Building ARM64 Native Image...${NC}"
echo -e "${YELLOW}Note: This may take 30-60 minutes due to QEMU emulation${NC}"
echo ""

BUILD_ARGS="--platform ${PLATFORM} -f ${DOCKERFILE} -t ${FULL_IMAGE_NAME} ${NO_CACHE}"

if [ "$PUSH" = true ]; then
    BUILD_ARGS="${BUILD_ARGS} --push"
    echo -e "${YELLOW}Will push to registry after build${NC}"
elif [ "$LOAD" = true ]; then
    BUILD_ARGS="${BUILD_ARGS} --load"
    echo -e "${YELLOW}Will load to local Docker after build${NC}"
else
    BUILD_ARGS="${BUILD_ARGS} --output type=docker,dest=./local-pkd-arm64.tar"
    echo -e "${YELLOW}Will save as tar file: ./local-pkd-arm64.tar${NC}"
fi

echo ""
echo -e "${GREEN}Running: docker buildx build ${BUILD_ARGS} .${NC}"
echo ""

# Start timer
START_TIME=$(date +%s)

# Run build
docker buildx build ${BUILD_ARGS} .

# End timer
END_TIME=$(date +%s)
DURATION=$((END_TIME - START_TIME))
MINUTES=$((DURATION / 60))
SECONDS=$((DURATION % 60))

echo ""
echo -e "${GREEN}============================================================================${NC}"
echo -e "${GREEN}Build completed successfully!${NC}"
echo -e "${GREEN}============================================================================${NC}"
echo -e "${YELLOW}Duration:${NC} ${MINUTES}m ${SECONDS}s"
echo -e "${YELLOW}Image:${NC} ${FULL_IMAGE_NAME}"

if [ "$PUSH" = true ]; then
    echo -e "${YELLOW}Status:${NC} Pushed to registry"
elif [ "$LOAD" = true ]; then
    echo -e "${YELLOW}Status:${NC} Loaded to local Docker"
    echo ""
    echo -e "${BLUE}Test with QEMU:${NC}"
    echo "  docker run --rm -p 8081:8081 ${FULL_IMAGE_NAME}"
else
    echo -e "${YELLOW}Output:${NC} ./local-pkd-arm64.tar"
    echo ""
    echo -e "${BLUE}Transfer to ARM64 device and load:${NC}"
    echo "  scp local-pkd-arm64.tar user@luckfox:/path/"
    echo "  ssh user@luckfox 'docker load -i /path/local-pkd-arm64.tar'"
    echo "  ssh user@luckfox 'docker run -d -p 8081:8081 ${FULL_IMAGE_NAME}'"
fi
