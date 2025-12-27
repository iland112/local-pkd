# Local PKD - Profile Execution Guide

**Version**: 1.0
**Last Updated**: 2025-12-27

---

## 📋 개요

Local PKD 애플리케이션은 다양한 환경에서 실행할 수 있도록 3가지 프로파일을 제공합니다.

| 프로파일 | 용도 | LDAP 연결 | PostgreSQL |
|---------|------|----------|------------|
| `local` | 로컬 개발 (Docker) | localhost Docker | localhost:5432 |
| `remote` | 원격 LDAP 서버 연결 | 192.168.100.10 | localhost:5432 |
| `container` | Docker 컨테이너 배포 | Docker 네트워크 | Docker 네트워크 |

---

## 🚀 Quick Start

### 1. Local 프로파일 (로컬 개발)

로컬 Docker 컨테이너의 PostgreSQL과 OpenLDAP을 사용합니다.

```bash
# 1. Docker 컨테이너 시작
./docker-start.sh --skip-app

# 2. 애플리케이션 실행
./scripts/run-local.sh
```

**연결 정보:**
- PostgreSQL: `localhost:5432`
- LDAP Write: `localhost:3891` (OpenLDAP 1 직접 연결)
- LDAP Read: `localhost:389` (HAProxy 로드밸런싱)

### 2. Remote 프로파일 (원격 LDAP)

로컬 PostgreSQL과 원격 LDAP 서버(192.168.100.10)를 사용합니다.

```bash
# 1. PostgreSQL만 시작 (LDAP 제외)
./docker-start.sh --skip-app --skip-ldap

# 2. 애플리케이션 실행
./scripts/run-remote.sh
```

**연결 정보:**
- PostgreSQL: `localhost:5432`
- LDAP Write: `192.168.100.10:389` (OpenLDAP Master)
- LDAP Read: `192.168.100.10:10389` (HAProxy)

### 3. Container 프로파일 (Docker 배포)

Native Image를 Docker 컨테이너로 실행합니다.

```bash
# 1. Native Image 빌드 (최초 1회)
./scripts/native-build.sh --skip-tests

# 2. 전체 서비스 시작
./docker-start.sh

# 또는 개별 시작
./scripts/run-container.sh
```

**연결 정보:**
- Application: `http://localhost:8081`
- PostgreSQL: Docker 네트워크 내부
- LDAP: Docker 네트워크 내부

---

## 📁 프로파일 설정 파일

| 프로파일 | 설정 파일 |
|---------|----------|
| local | `src/main/resources/application-local.properties` |
| remote | `src/main/resources/application-remote.properties` |
| container | `src/main/resources/application-container.properties` |

### LDAP Read/Write 분리 설정

모든 프로파일은 LDAP Read/Write 분리를 지원합니다:

```properties
# Write: PKD 업로드 시 데이터 저장
app.ldap.write.enabled=true
app.ldap.write.url=ldap://...
app.ldap.write.pool-initial-size=5
app.ldap.write.pool-max-size=20

# Read: 통계, PA 검증 등 조회
app.ldap.read.enabled=true
app.ldap.read.url=ldap://...
app.ldap.read.pool-initial-size=3
app.ldap.read.pool-max-size=10
```

---

## 🔧 실행 스크립트

### `scripts/run-local.sh`

```bash
./scripts/run-local.sh
```

- Docker 컨테이너 상태 확인
- `.env` 파일 자동 생성 (없는 경우)
- `local` 프로파일로 Spring Boot 실행

### `scripts/run-remote.sh`

```bash
./scripts/run-remote.sh
```

- PostgreSQL 컨테이너 확인
- 원격 LDAP 서버 연결 테스트
- `remote` 프로파일로 Spring Boot 실행

### `scripts/run-container.sh`

```bash
./scripts/run-container.sh
```

- Native Image 존재 확인
- Docker Compose로 전체 서비스 시작
- `container` 프로파일로 컨테이너 실행

---

## ⚙️ 환경 변수 (.env)

`.env` 파일을 프로젝트 루트에 생성하여 LDAP 인증 정보를 설정합니다:

```properties
# Local/Remote 프로파일용
LDAP_IP=localhost
LDAP_PORT=389
LDAP_USERNAME=cn=admin,dc=ldap,dc=smartcoreinc,dc=com
LDAP_PASSWORD=core
```

---

## 📊 프로파일별 아키텍처

### Local 프로파일

```
┌─────────────────┐     ┌─────────────────────────────────────┐
│   Application   │     │           Docker Compose            │
│   (JVM Mode)    │     ├─────────────────────────────────────┤
│                 │────►│ PostgreSQL   │ localhost:5432      │
│ localhost:8081  │     │ HAProxy      │ localhost:389       │
│                 │────►│ OpenLDAP 1   │ localhost:3891      │
│                 │     │ OpenLDAP 2   │ localhost:3892      │
└─────────────────┘     └─────────────────────────────────────┘
```

### Remote 프로파일

```
┌─────────────────┐     ┌─────────────────┐     ┌─────────────────────┐
│   Application   │     │  Docker Local   │     │   Remote Server     │
│   (JVM Mode)    │     │                 │     │   192.168.100.10    │
│                 │────►│ PostgreSQL      │     ├─────────────────────┤
│ localhost:8081  │     │ localhost:5432  │     │ OpenLDAP :389       │
│                 │────────────────────────────►│ HAProxy  :10389     │
└─────────────────┘     └─────────────────┘     └─────────────────────┘
```

### Container 프로파일

```
┌─────────────────────────────────────────────────────────────┐
│                      Docker Compose                          │
├─────────────────────────────────────────────────────────────┤
│  ┌─────────────┐  ┌────────────┐  ┌──────────────────────┐  │
│  │ local-pkd   │  │ PostgreSQL │  │     HAProxy          │  │
│  │ (Native)    │─►│            │  │  ┌────────────────┐  │  │
│  │ :8081       │  └────────────┘  │  │ OpenLDAP 1     │  │  │
│  │             │─────────────────►│  │ OpenLDAP 2     │  │  │
│  └─────────────┘                  │  └────────────────┘  │  │
└─────────────────────────────────────────────────────────────┘
```

---

## 🛠️ 수동 실행 방법

Maven을 직접 사용하여 실행할 수도 있습니다:

```bash
# Local 프로파일
./mvnw spring-boot:run -Dspring-boot.run.profiles=local

# Remote 프로파일
./mvnw spring-boot:run -Dspring-boot.run.profiles=remote

# Container 프로파일 (JVM 모드)
./mvnw spring-boot:run -Dspring-boot.run.profiles=container

# 환경변수로 프로파일 설정
SPRING_PROFILES_ACTIVE=remote ./mvnw spring-boot:run
```

---

## 🔍 Troubleshooting

### 1. Docker 컨테이너가 시작되지 않음

```bash
# 컨테이너 상태 확인
docker compose ps

# 로그 확인
docker compose logs -f postgres
docker compose logs -f haproxy
```

### 2. LDAP 연결 실패

```bash
# LDAP 연결 테스트
ldapsearch -x -H ldap://localhost:389 -D "cn=admin,dc=ldap,dc=smartcoreinc,dc=com" -w core -b "dc=ldap,dc=smartcoreinc,dc=com"

# HAProxy 상태 확인
curl http://localhost:8404/stats
```

### 3. PostgreSQL 연결 실패

```bash
# PostgreSQL 연결 테스트
docker exec icao-local-pkd-postgres psql -U postgres -d icao_local_pkd -c "SELECT 1;"
```

### 4. 포트 충돌

```bash
# 사용 중인 포트 확인
lsof -ti:8081 | xargs kill -9  # 8081 포트 해제
lsof -ti:5432 | xargs kill -9  # 5432 포트 해제
```

---

## 📌 접속 정보 요약

| 서비스 | URL | 계정 |
|--------|-----|------|
| Application | http://localhost:8081 | - |
| Swagger UI | http://localhost:8081/swagger-ui.html | - |
| pgAdmin | http://localhost:5050 | admin@smartcoreinc.com / admin |
| phpLDAPadmin | http://localhost:8080 | cn=admin,dc=ldap,dc=smartcoreinc,dc=com / core |
| HAProxy Stats | http://localhost:8404/stats | - |

---

*상세한 설정 정보는 각 프로파일 설정 파일을 참조하세요.*
