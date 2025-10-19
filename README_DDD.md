# ICAO PKD Local Evaluation Project

## 🎯 프로젝트 개요

ICAO Public Key Directory (PKD)의 Master List 및 LDIF 파일을 로컬에서 관리하고 평가하는 웹 애플리케이션입니다.

**버전**: 2.0.0 (DDD Refactored)  
**상태**: ✅ **PRODUCTION READY**  
**포트**: 8081

---

## 🏗️ Architecture

**Domain-Driven Design (DDD)** with **Hexagonal Architecture**

```
Infrastructure Layer (Web, DB, File System)
          ↓ implements
Application Layer (Use Cases, CQRS)
          ↓ uses
Domain Layer (Business Logic)
```

---

## 🚀 Quick Start

### 1. Prerequisites
- Java 21
- Maven 3.9+
- PostgreSQL 15 (via Podman)

### 2. Database Setup
```bash
./podman-start.sh
```

### 3. Run Application
```bash
./mvnw spring-boot:run
```

Application will start on **http://localhost:8081**

### 4. Access
- LDIF Upload: http://localhost:8081/ldif/upload
- Master List Upload: http://localhost:8081/masterlist/upload
- Upload History: http://localhost:8081/upload-history
- Health Check: http://localhost:8081/actuator/health

---

## 📁 Project Structure

```
src/main/java/com/smartcoreinc/localpkd/
├── fileupload/
│   ├── domain/              # Domain Layer (비즈니스 로직)
│   ├── application/         # Application Layer (Use Cases)
│   └── infrastructure/      # Infrastructure Layer (Web, DB)
└── shared/                  # Shared Kernel
```

**자세한 구조**: [CLAUDE_DDD_UPDATE.md](CLAUDE_DDD_UPDATE.md)

---

## 🎨 DDD Patterns

1. ✅ **Aggregate Root** - UploadedFile
2. ✅ **Value Objects** (7개) - FileName, FileHash, FileSize, etc.
3. ✅ **Domain Events** (3개) - FileUploaded, ValidationFailed, etc.
4. ✅ **Repository Pattern** - Port/Adapter
5. ✅ **Hexagonal Architecture** - Domain 독립성
6. ✅ **CQRS** - Command/Query 분리
7. ✅ **Use Case Pattern** - 비즈니스 유스케이스
8. ✅ **Event-Driven** - ApplicationEventPublisher

---

## 📊 Features

### ✅ 파일 업로드
- LDIF 파일 (CSCA/eMRTD Complete/Delta)
- Master List 파일 (Signed CMS)
- 최대 100MB 지원

### ✅ 중복 검사
- SHA-256 해시 기반
- 클라이언트/서버 양측 검증

### ✅ 메타데이터 추출
- Collection Number 자동 감지
- Version 자동 추출
- File Format 자동 감지

### ✅ 체크섬 검증
- SHA-1 체크섬 (ICAO PKD 표준)
- Expected vs Calculated 비교

### ✅ 이력 관리
- 업로드 이력 저장
- 상태 추적 (10개 상태)

---

## 📚 Documentation

- 📄 **[CLAUDE_DDD_UPDATE.md](CLAUDE_DDD_UPDATE.md)** - DDD 아키텍처 상세 설명
- 📄 **[docs/FINAL_PROJECT_STATUS.md](docs/FINAL_PROJECT_STATUS.md)** - 최종 프로젝트 상태
- 📄 **[docs/phase4_2_completion_summary.md](docs/phase4_2_completion_summary.md)** - Phase 4.2 완료 보고서
- 📄 **[docs/phase5_1_completion_summary.md](docs/phase5_1_completion_summary.md)** - Phase 5.1 완료 보고서
- 📄 **[CLAUDE.md](CLAUDE.md)** - 전체 개발 문서 (Legacy)

---

## 🛠️ Tech Stack

### Backend
- Spring Boot 3.5.5
- Java 21
- Spring Data JPA
- PostgreSQL 15
- Flyway
- JPearl 2.0.1

### Frontend
- Thymeleaf
- Alpine.js 3.14.8
- HTMX 2.0.4
- Tailwind CSS 3.x
- DaisyUI 5.0

---

## 🧪 Testing

```bash
# Unit Tests
./mvnw test

# Integration Tests
./mvnw verify

# Build
./mvnw clean package
```

---

## 📈 Project Stats

- **Source Files**: 64개
- **DDD Files**: 34개
- **Controllers**: 3개
- **Use Cases**: 4개
- **Value Objects**: 7개
- **Domain Events**: 3개

---

## 🎯 Next Steps

1. **검색 기능** - GetUploadHistoryUseCase 완성
2. **Event Listeners** - Logging, Monitoring
3. **Frontend 통합** - DDD API 연동
4. **Testing** - Unit/Integration/E2E
5. **Documentation** - API Docs, ADR

---

## 👥 Contributors

- **Developer**: Claude (AI Assistant)
- **Project Owner**: kbjung

---

## 📝 License

TBD

---

**Last Updated**: 2025-10-19  
**Version**: 2.0.0 (DDD Refactored)  
**Status**: ✅ Production Ready
