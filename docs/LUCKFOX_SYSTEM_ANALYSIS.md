# Luckfox Omni3576 System Analysis

**Version**: 1.0
**Analysis Date**: 2025-12-28
**Analyst**: Claude Code

---

## Overview

Luckfox Omni3576 ARM64 디바이스 2대의 시스템 환경 분석 결과입니다.

---

## System Comparison

| 항목 | 192.168.100.10 | 192.168.100.11 |
|------|----------------|----------------|
| **Hostname** | luckfox | luckfox |
| **OS** | Debian 12 (bookworm) | Debian 12 (bookworm) |
| **Kernel** | 6.1.75 aarch64 | 6.1.75 aarch64 |
| **CPU** | 4×Cortex-A72 + 4×Cortex-A53 (8코어) | 4×Cortex-A72 + 4×Cortex-A53 (8코어) |
| **RAM Total** | 3.8GB | 3.8GB |
| **RAM Available** | 3.2GB | 3.3GB |
| **Disk Total** | 29GB (eMMC) | 29GB (eMMC) |
| **Disk Used** | 65% (여유 9.7GB) | 42% (여유 16GB) |
| **Docker** | ❌ 미설치 | ✅ 28.4.0 |
| **Docker Compose** | ❌ 미설치 | ✅ v2.39.2 (plugin) |
| **OpenLDAP** | ✅ :389 | ✅ :389 |
| **HAProxy** | ✅ :10389, :8404 | ❌ 없음 |

---

## Hardware Specifications

### CPU Architecture

```
Architecture:        aarch64
CPU op-mode(s):      32-bit, 64-bit
Byte Order:          Little Endian
CPU(s):              8
Vendor ID:           ARM

Cortex-A72 (Performance Cores):
  - Model: 0
  - Cores: 4
  - Thread(s) per core: 1
  - Stepping: r1p0
  - BogoMIPS: 48.00

Cortex-A53 (Efficiency Cores):
  - Model: 4
  - Cores: 4
  - Thread(s) per core: 1
  - Stepping: r0p4
  - BogoMIPS: 48.00

CPU Flags: fp asimd evtstrm aes pmull sha1 sha2 crc32 cpuid
```

### Storage (192.168.100.10)

```
NAME         MAJ:MIN RM  SIZE RO TYPE MOUNTPOINTS
mmcblk0      179:0    0 29.1G  0 disk
├─mmcblk0p1  179:1    0    4M  0 part   (bootloader)
├─mmcblk0p2  179:2    0    4M  0 part   (env)
├─mmcblk0p3  179:3    0   64M  0 part   (boot)
├─mmcblk0p4  179:4    0  128M  0 part   (recovery)
├─mmcblk0p5  179:5    0   32M  0 part   (misc)
└─mmcblk0p6  179:6    0 28.9G  0 part / (rootfs)
```

### Network Interfaces

```
1: lo        - Loopback (127.0.0.1/8)
2: can0      - CAN Bus (not active)
3: end0      - Ethernet (192.168.100.x/24) - Primary
4: end1      - Ethernet (not connected)
5: wlan0     - WiFi (not connected)
```

---

## Infrastructure Architecture

```
┌─────────────────────────────────────────────────────────────────────────┐
│                    LDAP MMR Replication Cluster                          │
│                                                                          │
│   192.168.100.10 (Master)              192.168.100.11 (Slave)           │
│   ┌─────────────────────┐              ┌─────────────────────┐          │
│   │  OpenLDAP           │◄── syncrepl ──►│  OpenLDAP          │          │
│   │  Master :389        │               │  Slave :389        │          │
│   │                     │               │                     │          │
│   │  HAProxy            │               │  Docker 28.4.0     │          │
│   │  LB :10389          │               │  Compose v2.39.2   │          │
│   │  Stats :8404        │               │                     │          │
│   └─────────────────────┘               └─────────────────────┘          │
│                                                                          │
│   역할: LDAP Master + HAProxy LB         역할: LDAP Slave + App Server   │
└─────────────────────────────────────────────────────────────────────────┘
```

---

## Services Status

### 192.168.100.10 (Master Node)

| Service | Port | Status | Description |
|---------|------|--------|-------------|
| **slapd (OpenLDAP)** | 389 | ✅ Running | LDAP Master (MMR) |
| **HAProxy** | 10389 | ✅ Running | LDAP Load Balancer |
| **HAProxy Stats** | 8404 | ✅ Running | Statistics UI |
| **SSH** | 22 | ✅ Running | Remote Access |
| **ADB** | 5555 | ✅ Listening | Android Debug Bridge |

### 192.168.100.11 (Slave Node)

| Service | Port | Status | Description |
|---------|------|--------|-------------|
| **slapd (OpenLDAP)** | 389 | ✅ Running | LDAP Slave (MMR) |
| **Docker** | - | ✅ Running | Container Runtime |
| **SSH** | 22 | ✅ Running | Remote Access |
| **ADB** | 5555 | ✅ Listening | Android Debug Bridge |

---

## HAProxy Configuration

```
# /etc/haproxy/haproxy.cfg

global
    log /dev/log local0
    log /dev/log local1 notice
    chroot /var/lib/haproxy
    stats socket /run/haproxy/admin.sock mode 660 level admin
    stats timeout 30s
    user haproxy
    group haproxy
    daemon

defaults
    log     global
    mode    tcp
    option  tcplog
    option  dontlognull
    timeout connect 5000
    timeout client  50000
    timeout server  50000

listen stats
   bind *:8404
   mode http
   stats enable
   stats uri /haproxy?stats
   stats refresh 5s

frontend ldap_frontend
    bind *:10389
    default_backend ldap_backend

backend ldap_backend
    balance roundrobin
    server local_ldap 192.168.100.10:389
    server remote_ldap 192.168.100.11:389
```

---

## LDAP Structure

### Base DN

```
dc=ldap,dc=smartcoreinc,dc=com
├── dc=pkd                           # PKD Root
│   └── dc=download
│       ├── dc=data                  # CSCA/DSC/CRL Storage
│       └── dc=nc-data               # Non-Compliant DSC Storage
├── ou=groups                        # Group Management
└── ou=people                        # User Management
```

### ICAO PKD DIT (Data Information Tree)

```
dc=ldap,dc=smartcoreinc,dc=com
└── dc=pkd
    └── dc=download
        ├── dc=data
        │   ├── c=KR
        │   │   ├── o=csca    # CSCA Certificates
        │   │   ├── o=dsc     # DSC Certificates
        │   │   └── o=crl     # CRLs
        │   ├── c=JP
        │   ├── c=US
        │   └── ...
        └── dc=nc-data
            ├── c=KR
            │   └── o=dsc     # Non-Compliant DSCs
            └── ...
```

---

## Docker Environment (192.168.100.11)

### Version Information

```
Client: Docker Engine - Community
 Version:    28.4.0
 Context:    default
 Debug Mode: false

Plugins:
  compose: Docker Compose (Docker Inc.)
    Version:  v2.39.2
    Path:     /usr/libexec/docker/cli-plugins/docker-compose

Server:
 Server Version: 28.4.0
 Storage Driver: overlay2
  Backing Filesystem: extfs
  Supports d_type: true
 Cgroup Driver: systemd
 Cgroup Version: 2
```

### Docker Usage

```bash
# Docker Compose (Plugin 방식 - 권장)
docker compose -f docker-compose.arm64.yaml up -d

# Docker Compose (Standalone - 미설치)
docker-compose -f docker-compose.arm64.yaml up -d  # 사용 불가
```

---

## Deployment Recommendation

### 배포 대상: 192.168.100.11

**선정 이유**:
1. ✅ Docker 28.4.0 + Compose v2.39.2 설치됨
2. ✅ 디스크 여유 공간 더 많음 (16GB vs 9.7GB)
3. ✅ 메모리 여유 더 많음 (3.3GB vs 3.2GB)
4. ✅ HAProxy 부하 없음 (전용 앱 서버)
5. ✅ OpenLDAP Slave로 읽기 부하 분산 가능

### LDAP 연결 설정

```properties
# Write: Master Direct (PKD Upload)
app.ldap.write.url=ldap://192.168.100.10:389

# Read: HAProxy Load Balanced (PA Verification, Statistics)
app.ldap.read.url=ldap://192.168.100.10:10389
```

### 배포 명령어

```bash
# 1. 이미지 전송
scp local-pkd-arm64.tar luckfox@192.168.100.11:/home/luckfox/

# 2. 이미지 로드
ssh luckfox@192.168.100.11 'sudo docker load -i /home/luckfox/local-pkd-arm64.tar'

# 3. Docker Compose 파일 전송
scp docker-compose.arm64.yaml luckfox@192.168.100.11:/home/luckfox/

# 4. 서비스 시작
ssh luckfox@192.168.100.11 'cd /home/luckfox && sudo docker compose -f docker-compose.arm64.yaml up -d'
```

---

## Security Considerations

1. **LDAP 인증**: `cn=admin,dc=ldap,dc=smartcoreinc,dc=com` 사용
2. **SSH 접속**: `luckfox` 사용자 (sudo 권한 보유)
3. **Docker 권한**: sudo 필요 (luckfox 사용자 docker 그룹 미등록)
4. **네트워크**: 192.168.100.0/24 내부 네트워크

---

## Troubleshooting

### SSH 연결 실패

```bash
# 네트워크 확인
ping 192.168.100.10
ping 192.168.100.11

# SSH 연결 테스트
ssh -v luckfox@192.168.100.11
```

### Docker 권한 오류

```bash
# docker 그룹에 사용자 추가 (권장)
sudo usermod -aG docker luckfox
newgrp docker

# 또는 sudo 사용
sudo docker ps
```

### LDAP 연결 테스트

```bash
# Master 테스트
ldapsearch -x -H ldap://192.168.100.10:389 -b 'dc=ldap,dc=smartcoreinc,dc=com' -s base

# HAProxy LB 테스트
ldapsearch -x -H ldap://192.168.100.10:10389 -b 'dc=ldap,dc=smartcoreinc,dc=com' -s base

# Slave 테스트
ldapsearch -x -H ldap://192.168.100.11:389 -b 'dc=ldap,dc=smartcoreinc,dc=com' -s base
```

---

## Related Documentation

- [ARM64_DEPLOYMENT_GUIDE.md](ARM64_DEPLOYMENT_GUIDE.md) - ARM64 배포 가이드
- [DOCKER_CONFIGURATION_MANUAL.md](DOCKER_CONFIGURATION_MANUAL.md) - Docker 설정 매뉴얼
- [NATIVE_IMAGE_GUIDE.md](NATIVE_IMAGE_GUIDE.md) - Native Image 빌드 가이드
