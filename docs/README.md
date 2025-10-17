# Local PKD System - Documentation

## 📚 문서 목록

### 1. [File Upload Manager 시스템 설계 및 구현](./file_upload_manager_implementation.md)
**내용:**
- 시스템 아키텍처
- 데이터베이스 설계 (ERD)
- 구현 내역 (Entity, Repository, Service)
- UI/UX 개선 사항
- 다음 작업 계획

**주요 내용:**
- ✅ Flyway 마이그레이션 (V4__Add_File_Upload_History.sql)
- ✅ 파일 업로드 이력 추적 시스템
- ✅ 중복 업로드 방지
- ✅ 체크섬 검증
- ✅ LDIF 및 ML 페이지 UI/UX 개선

---

### 2. [TODO - 개발 계획](./TODO.md)
**내용:**
- 우선순위별 작업 목록
- Sprint 계획
- 기술 개선 사항
- 코딩 컨벤션

**다음 Sprint 주요 작업:**
1. 🔴 파일 업로드 이력 조회 페이지 구현
2. 🔴 중복 파일 업로드 처리 UI
3. 🔴 체크섬 검증 결과 표시 UI

---

### 3. [ICAO PKD 상세 분석](./icao_pkd_detailed_analysis.md)
**내용:**
- ICAO PKD 개요
- 파일 형식 분석
- 데이터 구조
- 보안 요구사항

---

### 4. [파일 포맷 분석](./icao_pkd_file_format_analysis.md)
**내용:**
- LDIF 파일 형식
- Master List 파일 형식
- 파일명 규칙
- 파싱 전략

---

### 5. [파일 업로드 워크플로우 설계](./file_upload_workflow_design.md)
**내용:**
- 업로드 프로세스 플로우
- 에러 처리 전략
- 상태 관리
- SSE 통신 구조

---

## 🚀 빠른 시작

### 1. 개발 환경 설정
```bash
# PostgreSQL 시작
podman-compose up -d postgres

# 애플리케이션 실행
mvn spring-boot:run -Dspring-boot.run.arguments="--spring.profiles.active=local"
```

### 2. 데이터베이스 마이그레이션
```bash
# Flyway 마이그레이션 실행
mvn flyway:migrate

# 마이그레이션 상태 확인
mvn flyway:info
```

### 3. 애플리케이션 접속
- **LDIF 업로드:** http://localhost:8080/ldif/upload
- **ML 업로드:** http://localhost:8080/icao/ml/upload
- **업로드 이력:** http://localhost:8080/upload-history (개발 예정)

---

## 📊 현재 진행 상황

### ✅ 완료된 작업 (Sprint 1)
1. ✅ Flyway 마이그레이션 설정 및 실행
2. ✅ `file_upload_history` 테이블 생성
3. ✅ FileUploadHistory Entity 구현
4. ✅ FileUploadHistoryRepository 구현
5. ✅ FileUploadService 구현
6. ✅ UI/UX 개선
   - 자동 알림 숨김
   - 파일 정보 미리보기 (LDIF)
   - SSE 재연결 로직
   - 파일 크기 검증 강화
   - 페이지 가시성 처리
   - 페이드 아웃 효과

### 🔄 진행 중 (Sprint 2)
- 파일 업로드 이력 조회 페이지 (예정)
- 중복 파일 처리 UI (예정)
- 체크섬 검증 결과 표시 (예정)

### 📋 계획 중 (Sprint 3+)
- 파일 다운로드 기능
- 업로드 통계 대시보드
- 배치 업로드
- 파일 비교 기능

---

## 🏗️ 시스템 아키텍처 개요

```
┌─────────────────────────────────────────────┐
│            Frontend (Thymeleaf)              │
│  ┌────────────┐         ┌────────────┐      │
│  │ LDIF Page  │         │  ML Page   │      │
│  └─────┬──────┘         └─────┬──────┘      │
│        │                      │             │
│        └──────────┬───────────┘             │
│                   │ HTMX + SSE              │
└───────────────────┼─────────────────────────┘
                    │
┌───────────────────▼─────────────────────────┐
│          Spring Boot Application            │
│  ┌──────────────────────────────────────┐   │
│  │      FileUploadService               │   │
│  └──────────────┬───────────────────────┘   │
│                 │                            │
│  ┌──────────────▼───────────────────────┐   │
│  │  FileUploadHistoryRepository         │   │
│  └──────────────┬───────────────────────┘   │
└─────────────────┼──────────────────────────┘
                  │
┌─────────────────▼──────────────────────────┐
│         PostgreSQL Database                 │
│  ┌────────────────────────────────────┐    │
│  │    file_upload_history (table)     │    │
│  └────────────────────────────────────┘    │
└────────────────────────────────────────────┘
```

---

## 🗃️ 데이터베이스 스키마

### file_upload_history 테이블
| 컬럼명 | 타입 | 설명 |
|--------|------|------|
| id | BIGSERIAL | 기본키 |
| original_file_name | VARCHAR(500) | 원본 파일명 |
| file_format | VARCHAR(50) | 파일 포맷 |
| file_hash | VARCHAR(64) | SHA-256 해시 (중복 방지) |
| upload_status | VARCHAR(50) | 업로드 상태 |
| uploaded_at | TIMESTAMP | 업로드 시간 |
| metadata | JSONB | 메타데이터 |

**인덱스:**
- `idx_file_hash` (UNIQUE)
- `idx_upload_status`
- `idx_uploaded_at`

---

## 🔑 주요 기능

### 1. 파일 업로드 이력 추적
- 모든 업로드 시도를 데이터베이스에 기록
- 파일 메타데이터 자동 추출
- 업로드 상태 추적 (PENDING, SUCCESS, FAILED)

### 2. 중복 업로드 방지
- SHA-256 해시 기반 중복 검사
- 파일 내용이 동일하면 업로드 차단
- 사용자에게 기존 업로드 정보 제공

### 3. 체크섬 검증
- ICAO 공식 체크섬 입력 (선택사항)
- SHA-1 체크섬 자동 계산
- 일치 여부 검증 및 기록

### 4. 실시간 진행률 표시
- Server-Sent Events (SSE) 활용
- 파싱 진행률 실시간 업데이트
- 자동 재연결 (최대 3회 시도)

### 5. 개선된 UI/UX
- 자동 알림 숨김 (성공: 5초, 오류: 10초)
- 파일 정보 미리보기
- 파일 크기 검증 (최대 100MB)
- 부드러운 페이드 효과

---

## 🛠️ 기술 스택

| 카테고리 | 기술 |
|----------|------|
| Backend | Spring Boot 3.x, Java 17+ |
| Database | PostgreSQL 15.x |
| Migration | Flyway |
| Frontend | Thymeleaf, HTMX, Tailwind CSS |
| Real-time | Server-Sent Events (SSE) |
| Build Tool | Maven |
| Version Control | Git |

---

## 📞 문의 및 지원

- **개발 팀:** SmartCore Development Team
- **프로젝트:** Local PKD System
- **버전:** 1.0.0
- **최종 업데이트:** 2025-10-17

---

## 📖 추가 리소스

- [Spring Boot 공식 문서](https://spring.io/projects/spring-boot)
- [Flyway 마이그레이션 가이드](https://flywaydb.org/documentation/)
- [HTMX 공식 문서](https://htmx.org/)
- [Tailwind CSS 공식 문서](https://tailwindcss.com/)
- [ICAO PKD 공식 사이트](https://pkddownloadsg.icao.int/)

---

**이 문서는 지속적으로 업데이트됩니다.**
