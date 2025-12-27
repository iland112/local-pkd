# ARM64 Native Image Deployment Guide

**Version**: 1.0
**Last Updated**: 2025-12-27
**Target Device**: Luckfox Omni3576 (ARM64)

---

## Overview

이 가이드는 Local PKD 애플리케이션을 ARM64 기반 Luckfox Omni3576 디바이스에 Docker 컨테이너로 배포하는 방법을 설명합니다.

### Target Device Specifications

| 항목 | 스펙 |
|------|------|
| **CPU** | 4×Cortex-A72@2.3GHz + 4×Cortex-A53@2.2GHz (ARM64) |
| **RAM** | 4GB / 8GB LPDDR4 |
| **Storage** | 32GB / 64GB eMMC + NVMe SSD |
| **OS** | Debian 12 / Ubuntu 22.04 |

---

## Prerequisites

### Build System (x86_64)

- Docker Desktop with Buildx support
- QEMU user-mode emulation
- 16GB+ RAM (for Native Image build)
- 20GB+ free disk space

### Target Device (ARM64)

- Docker Engine installed
- Network connectivity
- 1GB+ free disk space

---

## Build Process

### 1. Setup Build Environment (One-time)

```bash
# Setup QEMU for ARM64 emulation
docker run --rm --privileged multiarch/qemu-user-static --reset -p yes

# Create ARM64 builder
docker buildx create --name arm64builder --driver docker-container --platform linux/arm64,linux/amd64
docker buildx use arm64builder
docker buildx inspect --bootstrap
```

### 2. Build ARM64 Native Image

```bash
# Option A: Quick build script (recommended)
./scripts/build-arm64.sh

# Option B: Build and save as tar file
./scripts/build-arm64.sh --no-cache

# Option C: Build and push to registry
./scripts/build-arm64.sh --push --registry=docker.io/yourusername

# Option D: Manual build
docker buildx build --platform linux/arm64 -f Dockerfile.arm64 \
    -t local-pkd:arm64 --output type=docker,dest=./local-pkd-arm64.tar .
```

> **Note**: Build time is approximately 30-60 minutes due to QEMU emulation.

### 3. Build Output

| Output | Description |
|--------|-------------|
| `local-pkd-arm64.tar` | Docker image tar file (default) |
| Registry push | When using `--push` option |
| Local image | When using `--load` option |

---

## Deployment to Luckfox Omni3576

### Method 1: Transfer TAR File

```bash
# On build machine: transfer image
scp local-pkd-arm64.tar user@luckfox-ip:/home/user/

# On Luckfox: load and run
ssh user@luckfox-ip
docker load -i local-pkd-arm64.tar
docker run -d --name local-pkd -p 8081:8081 local-pkd:arm64
```

### Method 2: Pull from Registry

```bash
# On Luckfox (if pushed to registry)
docker pull yourusername/local-pkd:arm64
docker run -d --name local-pkd -p 8081:8081 yourusername/local-pkd:arm64
```

---

## Docker Compose (Production)

Create `docker-compose.arm64.yaml` on the Luckfox device:

```yaml
version: '3.8'

services:
  local-pkd:
    image: local-pkd:arm64
    container_name: local-pkd
    restart: unless-stopped
    ports:
      - "8081:8081"
    environment:
      - SPRING_PROFILES_ACTIVE=container
      - SERVER_ADDRESS=0.0.0.0
      - SERVER_PORT=8081
      # Database settings
      - SPRING_DATASOURCE_URL=jdbc:postgresql://postgres:5432/localpkd
      - SPRING_DATASOURCE_USERNAME=pkduser
      - SPRING_DATASOURCE_PASSWORD=pkdpass
      # LDAP settings
      - APP_LDAP_READ_URL=ldap://ldap-host:389
      - APP_LDAP_WRITE_URL=ldap://ldap-host:389
    healthcheck:
      test: ["CMD", "curl", "-f", "http://localhost:8081/actuator/health"]
      interval: 30s
      timeout: 10s
      retries: 3
      start_period: 60s
    depends_on:
      - postgres

  postgres:
    image: postgres:15-alpine
    container_name: local-pkd-postgres
    restart: unless-stopped
    environment:
      - POSTGRES_DB=localpkd
      - POSTGRES_USER=pkduser
      - POSTGRES_PASSWORD=pkdpass
      - TZ=Asia/Seoul
    volumes:
      - postgres_data:/var/lib/postgresql/data
    healthcheck:
      test: ["CMD-SHELL", "pg_isready -U pkduser -d localpkd"]
      interval: 10s
      timeout: 5s
      retries: 5

volumes:
  postgres_data:
```

Run with:

```bash
docker-compose -f docker-compose.arm64.yaml up -d
```

---

## Performance Expectations

### Native Image on ARM64

| Metric | Expected Value |
|--------|----------------|
| Startup Time | ~0.5 sec |
| Memory (RSS) | ~150 MB |
| Image Size | ~200 MB |

### Comparison with JVM Mode

| Metric | JVM | Native Image |
|--------|-----|--------------|
| Startup | ~10 sec | ~0.5 sec |
| Memory | ~600 MB | ~150 MB |
| CPU (idle) | Higher | Lower |

---

## Troubleshooting

### Build Issues

**Problem**: Build fails with "exec format error"

```bash
# Solution: Reset QEMU
docker run --rm --privileged multiarch/qemu-user-static --reset -p yes
```

**Problem**: Build runs out of memory

```bash
# Solution: Increase Docker memory limit in Docker Desktop settings
# Or reduce parallel GC threads:
# Add to Dockerfile: -J-XX:ParallelGCThreads=2
```

**Problem**: Build takes too long

```bash
# Solution: Use --no-cache only when necessary
# Enable BuildKit cache:
docker buildx build --cache-from type=local,src=/tmp/.buildx-cache \
    --cache-to type=local,dest=/tmp/.buildx-cache-new ...
```

### Runtime Issues

**Problem**: Container fails to start

```bash
# Check logs
docker logs local-pkd

# Common issues:
# - Missing environment variables
# - Database connection refused
# - LDAP connection timeout
```

**Problem**: Application crashes with SIGSEGV

```bash
# Solution: Ensure all reflection configs are included
# Check src/main/resources/META-INF/native-image/reflect-config.json
```

---

## Security Considerations

1. **Non-root User**: Container runs as non-root `pkd` user
2. **Minimal Base Image**: Uses `debian:bookworm-slim`
3. **Health Checks**: Built-in health endpoint monitoring
4. **Environment Variables**: Sensitive data via env vars, not hardcoded

---

## File Reference

| File | Description |
|------|-------------|
| `Dockerfile.arm64` | ARM64 multi-stage Dockerfile |
| `scripts/build-arm64.sh` | Build script with options |
| `docs/ARM64_DEPLOYMENT_GUIDE.md` | This guide |

---

## Related Documentation

- [NATIVE_IMAGE_GUIDE.md](NATIVE_IMAGE_GUIDE.md) - x86_64 Native Image build
- [DOCKER_CONFIGURATION_MANUAL.md](DOCKER_CONFIGURATION_MANUAL.md) - Docker setup
- [PA_API_INTEGRATION_GUIDE.md](PA_API_INTEGRATION_GUIDE.md) - API integration
