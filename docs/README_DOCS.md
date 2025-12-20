# 📚 Documentation Index

> Local PKD Evaluation Project 문서 가이드

**마지막 업데이트**: 2025-10-23
**문서 버전**: 6.0

---

## 🎯 빠른 시작 (Quick Start)

### 처음 프로젝트를 접하는 경우
1. 👉 **[FINAL_PROJECT_STATUS.md](./FINAL_PROJECT_STATUS.md)** - 프로젝트 전체 개요
2. 👉 **[TODO.md](./TODO.md)** - 현재 작업 상태
3. 👉 **[PHASE_9_SSE_IMPLEMENTATION.md](./PHASE_9_SSE_IMPLEMENTATION.md)** - 최신 구현 (SSE 실시간 진행률)

### 아키텍처를 이해하고 싶은 경우
1. **[DDD_IMPLEMENTATION_COMPLETE.md](./DDD_IMPLEMENTATION_COMPLETE.md)** - DDD 구현 완료 (Phase 1-5)
2. **[ddd-msa-migration-roadmap.md](./ddd-msa-migration-roadmap.md)** - 전체 로드맵

---

## 📁 문서 구조 (간소화)

```
docs/
├── 🔥 FINAL_PROJECT_STATUS.md          # 프로젝트 전체 상태
├── 📋 TODO.md                           # 현재/다음 작업
├── 📖 README_DOCS.md                    # 이 파일
│
├── 🏗️ DDD_IMPLEMENTATION_COMPLETE.md    # DDD 아키텍처 (Phase 1-5)
├── 🗺️ ddd-msa-migration-roadmap.md      # 마이그레이션 로드맵
├── 📐 ddd_refactoring_strategy.md       # 리팩토링 전략
│
├── 📦 PHASE_6_SEARCH_IMPLEMENTATION.md  # Phase 6: JPA Specification 검색
├── 🔔 PHASE_7_EVENT_LISTENERS_IMPLEMENTATION.md  # Phase 7: Event Listeners
├── 🎨 PHASE_8_UI_IMPROVEMENTS.md        # Phase 8: DaisyUI UI
├── 📡 PHASE_9_SSE_IMPLEMENTATION.md     # Phase 9: SSE 실시간 진행률 (최신)
│
├── 🔍 duplicate_check_feature_summary.md    # 중복 검사 기능
├── 🧪 duplicate_check_api_test_results.md  # API 테스트 결과
├── 📤 file_upload_implementation_plan.md   # 파일 업로드 계획
├── 🎯 file_upload_manager_implementation.md # 업로드 매니저 구현
│
├── 📂 20251016/                         # 초기 분석 문서 (Phase 1)
├── 🗄️ archive/                          # 완료된 중간 문서들
└── 📚 references/                       # 외부 참조 문서
```

---

## 📖 핵심 문서 (Must Read)

### 1. 프로젝트 상태
| 문서 | 설명 | 우선순위 |
|------|------|----------|
| **FINAL_PROJECT_STATUS.md** | 프로젝트 최종 상태, 기술 스택, 아키텍처 개요 | ⭐⭐⭐ |
| **TODO.md** | 현재 진행 중인 작업 및 다음 Sprint 계획 | ⭐⭐⭐ |

### 2. 아키텍처
| 문서 | 설명 | 우선순위 |
|------|------|----------|
| **DDD_IMPLEMENTATION_COMPLETE.md** | DDD 구현 완료 (Shared Kernel, Domain, Application, Infrastructure) | ⭐⭐⭐ |
| **ddd-msa-migration-roadmap.md** | Modular Monolith → MSA 전환 로드맵 | ⭐⭐ |
| **ddd_refactoring_strategy.md** | DDD 리팩토링 전략 및 패턴 | ⭐⭐ |

### 3. Phase별 구현 (시간순)
| Phase | 문서 | 설명 | 완료일 |
|-------|------|------|--------|
| **Phase 6** | PHASE_6_SEARCH_IMPLEMENTATION.md | JPA Specification 기반 동적 검색 | 2025-10-19 |
| **Phase 7** | PHASE_7_EVENT_LISTENERS_IMPLEMENTATION.md | Domain Event Listeners (Checksum, Upload) | 2025-10-20 |
| **Phase 8** | PHASE_8_UI_IMPROVEMENTS.md | DaisyUI UI, 중복 검사, 체크섬 검증 | 2025-10-22 |
| **Phase 9** | PHASE_9_SSE_IMPLEMENTATION.md | Server-Sent Events (SSE) 실시간 진행률 추적 | 2025-10-23 |

### 4. 기능별 설계
| 문서 | 설명 |
|------|------|
| **duplicate_check_feature_summary.md** | 중복 파일 검사 기능 요약 |
| **duplicate_check_api_test_results.md** | 중복 검사 API 테스트 (4/4 passed) |
| **file_upload_implementation_plan.md** | 파일 업로드 11단계 프로세스 |
| **file_upload_manager_implementation.md** | 파일 업로드 매니저 구현 상세 |

---

## 🗂️ 아카이브 및 히스토리

### 📂 ./20251016/ (초기 분석)
- `icao_pkd_detailed_analysis.md` - ICAO PKD 상세 분석
- `icao_pkd_file_format_analysis.md` - 파일 포맷 분석
- `file_upload_workflow_design.md` - 워크플로우 설계
- `phase1_final_complete.md` - Phase 1 완료 보고서
- `refactoring_complete.md` - 초기 리팩토링 완료
- `2025-10-16-18:05-session-limit-contents-for-continue.md` - 세션 요약
- `implementation_summary_2025-10-17.md` - 구현 요약 (Phase 1-3)
- `session-summary-2025-10-18.md` - 세션 요약 (Phase 4-5)

### 🗄️ ./archive/ (완료된 중간 문서)
- `phase4_integration_strategy.md` - Phase 4 통합 전략
- `phase4_2_completion_summary.md` - Phase 4.2 완료 요약
- `phase4_2_legacy_removal_plan.md` - Legacy 코드 제거 계획
- `phase5_1_completion_summary.md` - Phase 5.1 완료 요약

> **Note**: 아카이브 문서는 히스토리 참고용이며, 현재 구현은 Phase 6-8 문서 참조

---

## 🎯 상황별 가이드

### 🆕 신규 개발자 온보딩
1. **FINAL_PROJECT_STATUS.md** (10분) - 프로젝트 개요
2. **DDD_IMPLEMENTATION_COMPLETE.md** (20분) - 아키텍처 이해
3. **PHASE_9_SSE_IMPLEMENTATION.md** (15분) - 최신 기능 파악 (SSE)
4. **TODO.md** (5분) - 다음 작업 확인

**예상 시간**: ~50분

### 🔧 특정 기능 개발
1. 해당 Phase 문서 확인 (PHASE_6, 7, 8, 9)
2. 관련 기능 설계 문서 참조 (duplicate_check, file_upload)
3. API 테스트 결과 확인 (duplicate_check_api_test_results)

### 🏗️ 아키텍처 개선
1. **ddd-msa-migration-roadmap.md** - 전체 계획 파악
2. **ddd_refactoring_strategy.md** - 패턴 이해
3. **DDD_IMPLEMENTATION_COMPLETE.md** - 현재 구현 확인

### 🐛 버그 수정
1. **TODO.md** - 알려진 이슈 확인
2. 해당 Phase 문서에서 관련 기능 찾기
3. 테스트 결과 문서 참조

---

## 📊 프로젝트 현황 요약

### ✅ 완료된 Phase (1-9)
- ✅ **Phase 1-5**: DDD 아키텍처 구현 (Shared Kernel, Domain, Application, Infrastructure)
- ✅ **Phase 6**: JPA Specification 기반 동적 검색
- ✅ **Phase 7**: Domain Event Listeners (3개)
- ✅ **Phase 8**: DaisyUI 기반 UI 개선 (업로드 이력, 중복 검사, 체크섬 검증)
- ✅ **Phase 9**: Server-Sent Events (SSE) 실시간 진행률 추적

### 🚀 다음 Phase (10+)
- ⏳ **Phase 10**: File Parsing (LDIF, Master List)
- ⏳ **Phase 11**: Certificate Validation
- ⏳ **Phase 12**: OpenLDAP Integration

자세한 내용은 **TODO.md** 참조.

---

## 📝 문서 작성 규칙

1. **파일명 규칙**
   - Phase 문서: `PHASE_N_DESCRIPTION.md`
   - 일반 문서: `UPPERCASE_SNAKE_CASE.md` 또는 `lowercase-with-hyphens.md`

2. **문서 구조**
   ```markdown
   # Title
   ## Overview
   ## Implementation Details
   ## Code Examples
   ## Test Results
   ## Summary
   ```

3. **버전 관리**
   - 각 문서에 작성일, 버전 명시
   - 중요 업데이트 시 문서 버전 증가

4. **아카이브 정책**
   - 완료된 Phase의 중간 문서 → `./archive/`
   - 초기 분석 문서 → `./20251016/`
   - 외부 참조 → `./references/`

---

## 🔍 문서 검색 팁

### 키워드로 찾기
```bash
# Phase 관련 문서
find docs -name "PHASE_*.md"

# 중복 검사 관련
grep -r "duplicate" docs/*.md

# DDD 관련
grep -r "DDD\|Domain-Driven" docs/*.md
```

### 최근 수정 문서
```bash
ls -lt docs/*.md | head -5
```

### 문서 크기 확인
```bash
wc -l docs/*.md | sort -n
```

---

## 📧 문의 및 기여

- **문서 오류 발견**: TODO.md에 이슈 추가
- **문서 개선 제안**: 프로젝트 리드에게 문의
- **새 문서 추가**: 작성 규칙 준수 후 커밋

---

## 📚 외부 참조

- **ICAO PKD**: https://www.icao.int/Security/FAL/PKD/
- **Spring Boot Docs**: https://spring.io/projects/spring-boot
- **DaisyUI Docs**: https://daisyui.com/
- **JPearl (Type-safe IDs)**: https://github.com/wimdeblauwe/jpearl

---

**Document Maintainer**: SmartCore Inc. Development Team
**Last Updated**: 2025-10-23
**Version**: 6.0 (Phase 9 완료)
