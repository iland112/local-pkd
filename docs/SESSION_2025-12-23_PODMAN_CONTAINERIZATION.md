# Session 2025-12-23: Podman Containerization & Windows Client Integration

## Overview
Windows 클라이언트(ePassport Reader 연동)에서 WSL2에서 실행 중인 Local PKD PA API에 접근할 수 있도록 Podman 컨테이너화 및 네트워크 설정을 완료했습니다.

## Problem Statement
- Windows에서 실행되는 ePassport Reader 연동 애플리케이션이 WSL2의 Local PKD 서버에 접근 불가
- WSL2의 localhost는 Windows에서 직접 접근할 수 없음
- 초기 시도: WSL2 mirrored networking mode → VSCode Remote 연결 문제 발생

## Solution: Podman Containerization with Host Network Mode

### 1. Created Files

#### Dockerfile
```dockerfile
# Local PKD Container - Native Image
FROM debian:bookworm-slim

RUN apt-get update && apt-get install -y --no-install-recommends \
    libfreetype6 \
    fontconfig \
    curl \
    && rm -rf /var/lib/apt/lists/*

WORKDIR /app
COPY target/local-pkd /app/local-pkd

EXPOSE 8081
ENV SPRING_PROFILES_ACTIVE=container
ENV SERVER_ADDRESS=0.0.0.0
ENV SERVER_PORT=8081

ENTRYPOINT ["/app/local-pkd"]
```

#### application-container.properties
컨테이너 환경 전용 설정 파일 (환경변수로 오버라이드 가능)

### 2. Updated podman-compose.yaml

```yaml
# Local PKD Application (Native Image)
local-pkd:
  build:
    context: .
    dockerfile: Dockerfile
  image: local-pkd:latest
  container_name: icao-local-pkd-app
  network_mode: host  # WSL2 네트워크 직접 사용
  environment:
    SPRING_PROFILES_ACTIVE: container
    SPRING_DATASOURCE_URL: jdbc:postgresql://localhost:5432/icao_local_pkd
    SPRING_DATASOURCE_USERNAME: postgres
    SPRING_DATASOURCE_PASSWORD: secret
    LDAP_IP: 192.168.100.10
    LDAP_PORT: 389
    LDAP_USERNAME: "cn=admin,dc=ldap,dc=smartcoreinc,dc=com"
    LDAP_PASSWORD: core
  volumes:
    - ./data:/app/data:Z
  restart: unless-stopped
  depends_on:
    - postgres
  healthcheck:
    test: ["CMD", "curl", "-f", "http://localhost:8081/actuator/health"]
    interval: 30s
    timeout: 10s
    retries: 3
    start_period: 10s
```

### 3. Updated podman-start.sh

새로운 옵션 추가:
- `--build`: 이미지 다시 빌드
- `--skip-app`: DB만 시작 (Local PKD 앱 제외)

Native Image 존재 여부 확인 로직 추가.

## Network Architecture

```
Windows Client (ePassport Reader)
        │
        ▼ http://172.24.1.6:8081
        │
   [UFW: 8081 허용]
        │
        ▼
WSL2 (172.24.1.6)
        │
   [network_mode: host]
        │
        ▼
Local PKD Container (port 8081)
        │
        ├──▶ PostgreSQL (localhost:5432)
        └──▶ LDAP (192.168.100.10:389)
```

## Key Configuration Notes

### Why `network_mode: host`?
- 컨테이너가 WSL2의 네트워크 스택을 직접 사용
- Windows에서 WSL2 IP(172.24.1.6)로 직접 접근 가능
- Port mapping 불필요

### UFW Firewall Configuration
WSL2의 UFW가 외부 연결을 차단하므로 포트 허용 필요:
```bash
sudo ufw allow 8081/tcp
```

### Environment Variable Precedence
Spring Boot에서 환경변수가 properties 파일보다 우선:
- `SPRING_DATASOURCE_URL` 환경변수로 DB 연결 URL 오버라이드
- `local`과 `container` 프로파일 충돌 방지

## Usage

### Start All Services
```bash
./podman-start.sh
```

### Start with Image Rebuild
```bash
./podman-start.sh --build
```

### Start DB Only (Development)
```bash
./podman-start.sh --skip-app
```

### Check Container Status
```bash
podman-compose -f podman-compose.yaml ps
```

### View Logs
```bash
podman-compose -f podman-compose.yaml logs -f local-pkd
```

## PA API Integration Guide

Windows 클라이언트에서 PA API 호출 방법은 [PA_API_INTEGRATION_GUIDE.md](PA_API_INTEGRATION_GUIDE.md) 참조.

## Files Changed

| File | Change |
|------|--------|
| `Dockerfile` | 신규 생성 |
| `src/main/resources/application-container.properties` | 신규 생성 |
| `podman-compose.yaml` | local-pkd 서비스 추가 |
| `podman-start.sh` | 옵션 추가, Native Image 확인 |
| `docs/PA_API_INTEGRATION_GUIDE.md` | 신규 생성 |

## Status: COMPLETE
