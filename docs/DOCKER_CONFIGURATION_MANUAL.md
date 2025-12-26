# Docker Configuration Manual

**Version**: 1.0
**Last Updated**: 2025-12-27
**Author**: SmartCore Inc.

---

## 목차

1. [개요](#1-개요)
2. [시스템 요구사항](#2-시스템-요구사항)
3. [컨테이너 구성](#3-컨테이너-구성)
4. [설치 및 시작](#4-설치-및-시작)
5. [OpenLDAP MMR 설정](#5-openldap-mmr-설정)
6. [LDAP Read/Write 분리](#6-ldap-readwrite-분리)
7. [스크립트 사용법](#7-스크립트-사용법)
8. [문제 해결](#8-문제-해결)
9. [백업 및 복구](#9-백업-및-복구)

---

## 1. 개요

이 문서는 Local PKD 프로젝트의 Docker 기반 인프라 구성 방법을 설명합니다.

### 아키텍처 개요

```
┌─────────────────────────────────────────────────────────────────┐
│                    Docker Compose Network                        │
│                                                                  │
│  ┌─────────────┐     ┌─────────────────────────────────────┐    │
│  │ PostgreSQL  │     │      OpenLDAP MMR Cluster           │    │
│  │   :5432     │     │  ┌───────────┐   ┌───────────┐      │    │
│  └──────┬──────┘     │  │ OpenLDAP1 │◄─►│ OpenLDAP2 │      │    │
│         │            │  │  :3891    │   │  :3892    │      │    │
│         │            │  └─────┬─────┘   └─────┬─────┘      │    │
│         │            │        └───────┬───────┘            │    │
│         │            │         ┌──────┴──────┐             │    │
│         │            │         │   HAProxy   │             │    │
│         │            │         │    :389     │             │    │
│         │            └─────────┴─────────────┴─────────────┘    │
│         │                             │                          │
│  ┌──────┴─────────────────────────────┴────────────────────┐    │
│  │              Local PKD Application (:8081)               │    │
│  └──────────────────────────────────────────────────────────┘    │
└──────────────────────────────────────────────────────────────────┘
```

---

## 2. 시스템 요구사항

### 하드웨어

| 항목 | 최소 사양 | 권장 사양 |
|------|----------|----------|
| CPU | 2 cores | 4+ cores |
| RAM | 4 GB | 8+ GB |
| Storage | 10 GB | 50+ GB |

### 소프트웨어

| 항목 | 버전 |
|------|------|
| OS | Windows 11 Pro + WSL2 Ubuntu 22.04 |
| Docker Desktop | 4.25+ |
| Docker Compose | v2.x |

### 네트워크 포트

| 포트 | 서비스 | 용도 |
|------|--------|------|
| 389 | HAProxy | LDAP 로드밸런서 |
| 3891 | OpenLDAP 1 | LDAP 마스터 1 (직접 연결) |
| 3892 | OpenLDAP 2 | LDAP 마스터 2 (직접 연결) |
| 5432 | PostgreSQL | 데이터베이스 |
| 5050 | pgAdmin | DB 관리 도구 |
| 8080 | phpLDAPadmin | LDAP 관리 도구 |
| 8081 | Application | 웹 애플리케이션 |
| 8404 | HAProxy Stats | HAProxy 모니터링 |

---

## 3. 컨테이너 구성

### docker-compose.yaml 구조

```yaml
services:
  postgres:        # PostgreSQL 15 (데이터베이스)
  pgadmin:         # pgAdmin 4 (DB 관리)
  openldap1:       # OpenLDAP Master 1 (MMR Node)
  openldap2:       # OpenLDAP Master 2 (MMR Node)
  haproxy:         # HAProxy (LDAP 로드밸런서)
  phpldapadmin:    # phpLDAPadmin (LDAP 관리)
  local-pkd:       # Spring Boot Application (선택)
```

### 각 컨테이너 상세

#### PostgreSQL

```yaml
postgres:
  image: postgres:15-alpine
  container_name: icao-local-pkd-postgres
  environment:
    POSTGRES_DB: icao_local_pkd
    POSTGRES_USER: postgres
    POSTGRES_PASSWORD: secret
    TZ: Asia/Seoul              # 한국 표준시
    PGTZ: Asia/Seoul
  ports:
    - "5432:5432"
  volumes:
    - ./.docker-data/postgres:/var/lib/postgresql/data
```

#### OpenLDAP (MMR Cluster)

```yaml
openldap1:
  build:
    context: ./openldap
    dockerfile: Dockerfile
  image: local-pkd-openldap:1.5.0
  container_name: icao-local-pkd-openldap1
  hostname: openldap1
  command: --copy-service        # 필수! 볼륨 마운트 시 설정 파일 복사
  environment:
    LDAP_ORGANISATION: "SmartCore Inc"
    LDAP_DOMAIN: "ldap.smartcoreinc.com"
    LDAP_BASE_DN: "dc=ldap,dc=smartcoreinc,dc=com"
    LDAP_ADMIN_PASSWORD: "core"
    LDAP_CONFIG_PASSWORD: "core"
    LDAP_TLS: "false"
    LDAP_REPLICATION: "true"
    LDAP_REPLICATION_HOSTS: "#DIFFHOST ldap://openldap1 ldap://openldap2"
  ports:
    - "3891:389"
  volumes:
    - ./.docker-data/openldap1/data:/var/lib/ldap
    - ./.docker-data/openldap1/config:/etc/ldap/slapd.d
    - ./openldap/schemas:/container/service/slapd/assets/config/bootstrap/ldif/custom
```

#### HAProxy

```yaml
haproxy:
  image: haproxy:2.9-alpine
  container_name: icao-local-pkd-haproxy
  ports:
    - "389:389"      # LDAP 로드밸런서
    - "8404:8404"    # Stats UI
  volumes:
    - ./haproxy/haproxy.cfg:/usr/local/etc/haproxy/haproxy.cfg:ro
  depends_on:
    openldap1:
      condition: service_healthy
    openldap2:
      condition: service_healthy
```

---

## 4. 설치 및 시작

### 최초 설치

```bash
# 1. 프로젝트 디렉토리 이동
cd /path/to/local-pkd

# 2. Docker 데이터 디렉토리 생성
mkdir -p .docker-data/{postgres,pgadmin,openldap1/{data,config},openldap2/{data,config}}

# 3. OpenLDAP 이미지 빌드
docker compose build openldap1

# 4. 컨테이너 시작 (앱 제외)
./docker-start.sh --skip-app

# 5. LDAP DIT 및 MMR 초기화
./docker-ldap-init.sh
```

### 일반 시작/중지

```bash
# 시작 (앱 제외)
./docker-start.sh --skip-app

# 시작 (전체)
./docker-start.sh

# 중지
./docker-stop.sh

# 재시작
./docker-restart.sh
```

### 상태 확인

```bash
# 컨테이너 상태
./docker-health.sh

# 로그 확인
./docker-logs.sh [서비스명]

# HAProxy Stats
open http://localhost:8404/stats
```

---

## 5. OpenLDAP MMR 설정

### Multi-Master Replication (MMR) 개요

MMR은 두 OpenLDAP 서버 간 양방향 실시간 동기화를 제공합니다.

```
┌───────────────────────────────────────────────────────────┐
│                    MMR Architecture                        │
│                                                            │
│   ┌─────────────┐    syncrepl     ┌─────────────┐         │
│   │  OpenLDAP1  │◄───────────────►│  OpenLDAP2  │         │
│   │  (Master)   │  bidirectional  │  (Master)   │         │
│   │  rid=001    │                 │  rid=002    │         │
│   └─────────────┘                 └─────────────┘         │
│                                                            │
│   - refreshAndPersist 모드 (실시간)                        │
│   - retry: 5초 간격, 최대 5회 → 300초 간격                  │
│   - MirrorMode: TRUE                                       │
└────────────────────────────────────────────────────────────┘
```

### MMR 수동 설정 (docker-ldap-init.sh에 포함)

OpenLDAP1에 적용:
```ldif
# Load syncprov module
dn: cn=module{0},cn=config
changetype: modify
add: olcModuleLoad
olcModuleLoad: syncprov

# Add syncprov overlay
dn: olcOverlay=syncprov,olcDatabase={1}mdb,cn=config
changetype: add
objectClass: olcOverlayConfig
objectClass: olcSyncProvConfig
olcOverlay: syncprov
olcSpCheckpoint: 100 10
olcSpSessionLog: 100

# Configure server ID
dn: cn=config
changetype: modify
replace: olcServerID
olcServerID: 1 ldap://openldap1
olcServerID: 2 ldap://openldap2

# Add syncrepl
dn: olcDatabase={1}mdb,cn=config
changetype: modify
add: olcSyncRepl
olcSyncRepl: rid=001 provider=ldap://openldap2
  binddn="cn=admin,dc=ldap,dc=smartcoreinc,dc=com"
  bindmethod=simple credentials=core
  searchbase="dc=ldap,dc=smartcoreinc,dc=com"
  type=refreshAndPersist retry="5 5 300 5" timeout=1
-
add: olcMirrorMode
olcMirrorMode: TRUE
```

### MMR 검증

```bash
# 1. OpenLDAP1에 테스트 엔트리 추가
ldapadd -x -H ldap://localhost:3891 \
  -D "cn=admin,dc=ldap,dc=smartcoreinc,dc=com" -w core <<EOF
dn: ou=test-mmr,dc=ldap,dc=smartcoreinc,dc=com
objectClass: organizationalUnit
ou: test-mmr
EOF

# 2. OpenLDAP2에서 복제 확인
ldapsearch -x -H ldap://localhost:3892 \
  -D "cn=admin,dc=ldap,dc=smartcoreinc,dc=com" -w core \
  "(ou=test-mmr)"

# 3. 테스트 엔트리 삭제
ldapdelete -x -H ldap://localhost:3891 \
  -D "cn=admin,dc=ldap,dc=smartcoreinc,dc=com" -w core \
  "ou=test-mmr,dc=ldap,dc=smartcoreinc,dc=com"
```

---

## 6. LDAP Read/Write 분리

### 설정 파일

`application-local.properties`:
```properties
# Write: OpenLDAP 1 직접 연결
app.ldap.write.enabled=true
app.ldap.write.url=ldap://localhost:3891
app.ldap.write.bind-dn=cn=admin,dc=ldap,dc=smartcoreinc,dc=com
app.ldap.write.password=core
app.ldap.write.pool-initial-size=5
app.ldap.write.pool-max-size=20

# Read: HAProxy 로드밸런싱
app.ldap.read.enabled=true
app.ldap.read.url=ldap://localhost:389
app.ldap.read.bind-dn=cn=admin,dc=ldap,dc=smartcoreinc,dc=com
app.ldap.read.password=core
app.ldap.read.pool-initial-size=3
app.ldap.read.pool-max-size=10
```

### 사용 패턴

| 작업 유형 | 연결 | 어댑터 |
|----------|------|--------|
| PKD 인증서 저장 | OpenLDAP 1 (Write) | UnboundIdLdapAdapter |
| CSCA 조회 | HAProxy (Read) | UnboundIdLdapCscaAdapter |
| CRL 조회 | HAProxy (Read) | UnboundIdCrlLdapAdapter |

---

## 7. 스크립트 사용법

### docker-start.sh

```bash
# 전체 시작
./docker-start.sh

# 앱 제외 시작 (개발 모드)
./docker-start.sh --skip-app

# 이미지 재빌드 후 시작
./docker-start.sh --build

# LDAP 제외 시작
./docker-start.sh --skip-ldap
```

### docker-ldap-init.sh

```bash
# LDAP DIT 구조 및 MMR 초기화
./docker-ldap-init.sh

# 출력 예시:
# 🔧 OpenLDAP ICAO PKD DIT 구조 및 MMR 초기화...
# 🔄 MMR (Multi-Master Replication) 설정 중...
# ✅ MMR 설정 완료!
# 📁 ICAO PKD DIT 구조 생성 중...
# ✅ ICAO PKD DIT 구조 초기화 완료!
# 🔄 MMR 복제 테스트 중...
# ✅ MMR 복제 정상 작동!
```

### docker-health.sh

```bash
./docker-health.sh

# 출력 예시:
# === Container Health Status ===
# icao-local-pkd-postgres    healthy
# icao-local-pkd-openldap1   healthy
# icao-local-pkd-openldap2   healthy
# icao-local-pkd-haproxy     healthy
```

### docker-logs.sh

```bash
# 전체 로그
./docker-logs.sh

# 특정 서비스 로그
./docker-logs.sh openldap1
./docker-logs.sh postgres
./docker-logs.sh haproxy
```

---

## 8. 문제 해결

### OpenLDAP 시작 실패

**증상**: `sed: can't read /container/service/slapd/assets/config/replication/replication-disable.ldif`

**원인**: osixia/openldap 이미지가 특정 설정 파일을 찾지 못함

**해결**:
1. `command: --copy-service` 옵션이 docker-compose.yaml에 있는지 확인
2. 커스텀 Dockerfile (`openldap/Dockerfile`)이 빌드되었는지 확인

```bash
# 이미지 재빌드
docker compose build openldap1

# 데이터 초기화 후 재시작
docker run --rm -v "$(pwd)/.docker-data:/data" alpine sh -c "rm -rf /data/openldap1/* /data/openldap2/*"
docker compose up -d openldap1 openldap2
```

### HAProxy 연결 실패

**증상**: HAProxy Stats에서 backend DOWN 표시

**확인**:
```bash
# HAProxy Stats 접속
open http://localhost:8404/stats

# OpenLDAP 직접 연결 테스트
ldapsearch -x -H ldap://localhost:3891 -b "" -s base
ldapsearch -x -H ldap://localhost:3892 -b "" -s base
```

**해결**:
1. OpenLDAP 컨테이너 상태 확인
2. `haproxy/haproxy.cfg` 포트 설정 확인 (389)

### MMR 복제 안됨

**증상**: 한쪽에 추가한 데이터가 다른 쪽에 나타나지 않음

**확인**:
```bash
# syncrepl 설정 확인
docker exec icao-local-pkd-openldap1 ldapsearch -x -H ldap://localhost \
  -D "cn=admin,cn=config" -w core \
  -b "olcDatabase={1}mdb,cn=config" olcSyncRepl olcMirrorMode
```

**해결**:
```bash
# MMR 재설정
./docker-ldap-init.sh

# 또는 수동 설정
docker exec icao-local-pkd-openldap1 ldapmodify -x -H ldap://localhost \
  -D "cn=admin,cn=config" -w core << EOF
dn: olcDatabase={1}mdb,cn=config
changetype: modify
replace: olcMirrorMode
olcMirrorMode: TRUE
EOF
```

### 권한 오류 (WSL2)

**증상**: `Permission denied` 오류

**해결**:
```bash
# Docker를 통해 파일 삭제
docker run --rm -v "$(pwd)/.docker-data:/data" alpine sh -c "rm -rf /data/*"

# 디렉토리 재생성
mkdir -p .docker-data/{postgres,pgadmin,openldap1/{data,config},openldap2/{data,config}}
```

---

## 9. 백업 및 복구

### 백업

```bash
./docker-backup.sh

# 백업 위치: ./backups/backup-YYYYMMDD-HHMMSS/
# 포함 내용:
# - postgres/     (PostgreSQL 덤프)
# - openldap1/    (LDAP 데이터)
# - openldap2/    (LDAP 데이터)
```

### 복구

```bash
./docker-restore.sh backups/backup-20251227-120000

# 복구 순서:
# 1. 컨테이너 중지
# 2. 데이터 복원
# 3. 컨테이너 시작
```

### 완전 초기화

```bash
# 주의: 모든 데이터 삭제됨!
./docker-clean.sh

# 확인 후 재설치
./docker-start.sh --skip-app
./docker-ldap-init.sh
```

---

## 부록: 접속 정보 요약

| 서비스 | URL | 인증 정보 |
|--------|-----|----------|
| 애플리케이션 | http://localhost:8081 | - |
| pgAdmin | http://localhost:5050 | admin@smartcoreinc.com / admin |
| phpLDAPadmin | http://localhost:8080 | cn=admin,dc=ldap,dc=smartcoreinc,dc=com / core |
| HAProxy Stats | http://localhost:8404/stats | - |
| PostgreSQL | localhost:5432 | postgres / secret |
| LDAP (HAProxy) | ldap://localhost:389 | cn=admin,dc=ldap,dc=smartcoreinc,dc=com / core |
| LDAP 1 (직접) | ldap://localhost:3891 | cn=admin,dc=ldap,dc=smartcoreinc,dc=com / core |
| LDAP 2 (직접) | ldap://localhost:3892 | cn=admin,dc=ldap,dc=smartcoreinc,dc=com / core |

---

**문서 끝**
