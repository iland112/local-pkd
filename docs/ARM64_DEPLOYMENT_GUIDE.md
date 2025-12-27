# ARM64 Native Image Deployment Guide

**Version**: 1.1
**Last Updated**: 2025-12-27
**Target Device**: Luckfox Omni3576 (ARM64)

---

## Overview

이 가이드는 Local PKD 애플리케이션을 ARM64 기반 Luckfox Omni3576 디바이스에 Docker 컨테이너로 배포하는 방법을 설명합니다.

### LDAP Infrastructure (External)

Luckfox 시스템에는 기존 OpenLDAP/HAProxy 인프라가 운영 중이므로, Docker 컨테이너에서는 이를 사용합니다.

| 서비스 | 호스트 | 포트 | 용도 |
|--------|--------|------|------|
| **HAProxy** | 192.168.100.10 | 10389 | Read Load Balancer |
| **OpenLDAP1** | 192.168.100.10 | 389 | Write Master |
| **OpenLDAP2** | 192.168.100.101 | 389 | Read Slave |

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

Luckfox 시스템에는 `docker-compose.arm64.yaml`이 포함되어 있습니다. 이 파일은 외부 LDAP 인프라를 사용하도록 구성되어 있습니다.

```yaml
services:
  # PostgreSQL Database
  postgres:
    image: postgres:15-alpine
    container_name: icao-local-pkd-postgres
    environment:
      POSTGRES_DB: icao_local_pkd
      POSTGRES_USER: postgres
      POSTGRES_PASSWORD: secret
      TZ: Asia/Seoul
      PGTZ: Asia/Seoul
    ports:
      - "5432:5432"
    volumes:
      - postgres_data:/var/lib/postgresql/data
    restart: unless-stopped

  # pgAdmin (Optional)
  pgadmin:
    image: dpage/pgadmin4:latest
    container_name: icao-local-pkd-pgadmin
    environment:
      PGADMIN_DEFAULT_EMAIL: admin@smartcoreinc.com
      PGADMIN_DEFAULT_PASSWORD: admin
    ports:
      - "5050:80"
    restart: unless-stopped

  # Local PKD Application (ARM64 Native Image)
  local-pkd:
    image: local-pkd:arm64-latest
    container_name: icao-local-pkd-app
    environment:
      SPRING_PROFILES_ACTIVE: arm64
      # PostgreSQL
      SPRING_DATASOURCE_URL: jdbc:postgresql://postgres:5432/icao_local_pkd
      SPRING_DATASOURCE_USERNAME: postgres
      SPRING_DATASOURCE_PASSWORD: secret
      # LDAP Write (OpenLDAP1 - Master)
      APP_LDAP_WRITE_ENABLED: "true"
      APP_LDAP_WRITE_URL: ldap://192.168.100.10:389
      APP_LDAP_WRITE_BASE_DN: dc=ldap,dc=smartcoreinc,dc=com
      APP_LDAP_WRITE_USERNAME: cn=admin,dc=ldap,dc=smartcoreinc,dc=com
      APP_LDAP_WRITE_PASSWORD: core
      # LDAP Read (HAProxy - Load Balanced)
      APP_LDAP_READ_ENABLED: "true"
      APP_LDAP_READ_URL: ldap://192.168.100.10:10389
      APP_LDAP_READ_BASE_DN: dc=ldap,dc=smartcoreinc,dc=com
      APP_LDAP_READ_USERNAME: cn=admin,dc=ldap,dc=smartcoreinc,dc=com
      APP_LDAP_READ_PASSWORD: core
      TZ: Asia/Seoul
    ports:
      - "8081:8081"
    restart: unless-stopped
    depends_on:
      postgres:
        condition: service_healthy

volumes:
  postgres_data:
  pgadmin_data:
  app_data:
```

Run with:

```bash
docker-compose -f docker-compose.arm64.yaml up -d
```

### LDAP Connection Diagram

```
┌─────────────────────────────────────────────────────────────────┐
│                 Local PKD Container (ARM64)                      │
│                                                                  │
│   ┌─────────────────────────────────────────────────────────┐   │
│   │              application-arm64.properties                │   │
│   │                                                          │   │
│   │   Write Connection        Read Connection                │   │
│   │   ────────────────        ───────────────                │   │
│   │   192.168.100.10:389      192.168.100.10:10389          │   │
│   │   (OpenLDAP1 Master)      (HAProxy LB)                   │   │
│   └───────────┬────────────────────────┬─────────────────────┘   │
└───────────────┼────────────────────────┼─────────────────────────┘
                │                        │
    ┌───────────┴───────────┐   ┌────────┴────────┐
    ▼                       │   ▼                 │
┌───────────────┐           │  ┌──────────────┐   │
│  OpenLDAP1    │           │  │   HAProxy    │   │
│  Master       │◄──MMR────►│  │   (LB)       │   │
│  192.168.100.10:389       │  │  :10389      │   │
└───────────────┘           │  └──────┬───────┘   │
                            │         │           │
                            │  ┌──────┴───────┐   │
                            │  ▼              ▼   │
                            │ ┌────────┐ ┌────────┐
                            └─│LDAP1   │ │LDAP2   │
                              │(Read)  │ │(Read)  │
                              └────────┘ └────────┘
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
| `docker-compose.arm64.yaml` | ARM64 Docker Compose (External LDAP) |
| `scripts/build-arm64.sh` | Build script with options |
| `src/main/resources/application-arm64.properties` | ARM64 profile configuration |
| `docs/ARM64_DEPLOYMENT_GUIDE.md` | This guide |

---

## Related Documentation

- [NATIVE_IMAGE_GUIDE.md](NATIVE_IMAGE_GUIDE.md) - x86_64 Native Image build
- [DOCKER_CONFIGURATION_MANUAL.md](DOCKER_CONFIGURATION_MANUAL.md) - Docker setup
- [PA_API_INTEGRATION_GUIDE.md](PA_API_INTEGRATION_GUIDE.md) - API integration
