# 📚 문서 가이드 & 색인

> Local PKD Evaluation Project 문서 관리 및 검색 가이드

**마지막 업데이트**: 2025-11-07
**상태**: ✅ 통합 완료 (81개 문서)
**문서 조직**: Main (60개) + Archive (21개)

---

## 🎯 여기서 시작하세요!

### 👉 **처음이신가요? → [00_START_HERE.md](./00_START_HERE.md)** ⭐⭐⭐
5분 안에 프로젝트 전체를 파악할 수 있는 가이드입니다.

### 👉 **프로젝트 현황을 알고 싶으신가요? → [PROJECT_MASTER_SUMMARY_2025-11-07.md](./PROJECT_MASTER_SUMMARY_2025-11-07.md)**
- 프로젝트 규모: 198개 Java 파일
- 구현 상태: Phase 1-17 완료 (85%+)
- 5개 Bounded Contexts (DDD)

### 👉 **다음 할 일이 뭔가요? → [TODO.md](./TODO.md)**
Phase 18+ 계획 및 작업 목록

### 👉 **전체 문서를 찾아야 하나요? → [DOCUMENTATION_INDEX_2025-11-07.md](./DOCUMENTATION_INDEX_2025-11-07.md)**
88개 모든 문서의 상세 색인

---

## 📖 주요 문서 (Quick Links)

### 🌟 필수 마스터 문서
| 파일 | 설명 | 읽는 시간 |
|------|------|---------|
| [00_START_HERE.md](./00_START_HERE.md) | 🆕 문서 시작 가이드 (이 문서의 개선판) | 5분 |
| [PROJECT_MASTER_SUMMARY_2025-11-07.md](./PROJECT_MASTER_SUMMARY_2025-11-07.md) | 프로젝트 종합 현황 | 15분 |
| [DOCUMENTATION_INDEX_2025-11-07.md](./DOCUMENTATION_INDEX_2025-11-07.md) | 전체 문서 색인 | 10분 |

### 🏗️ 아키텍처 문서
| 파일 | 설명 | 읽는 시간 |
|------|------|---------|
| [ddd-msa-migration-roadmap.md](./ddd-msa-migration-roadmap.md) | DDD & MSA 로드맵 | 20분 |
| [DDD_IMPLEMENTATION_COMPLETE.md](./DDD_IMPLEMENTATION_COMPLETE.md) | DDD 구현 완료 (Phase 1-5) | 25분 |
| [ddd_refactoring_strategy.md](./ddd_refactoring_strategy.md) | 리팩토링 전략 | 15분 |

### 🔧 개발 표준 & 가이드
| 파일 | 설명 | 대상 |
|------|------|------|
| [FRONTEND_CODING_STANDARDS.md](./FRONTEND_CODING_STANDARDS.md) | Frontend 코딩 규칙 | Frontend 개발자 |
| [API_REFERENCE_LDAP_MODULE.md](./API_REFERENCE_LDAP_MODULE.md) | LDAP API 레퍼런스 | LDAP 작업자 |
| [LDAP_USAGE_EXAMPLES_CONFIGURATION.md](./LDAP_USAGE_EXAMPLES_CONFIGURATION.md) | LDAP 사용 예시 | LDAP 작업자 |

### 📋 Phase별 구현 문서
| Phase | 파일 | 상태 |
|-------|------|------|
| **6-9** | [PHASE_6_SEARCH_IMPLEMENTATION.md](./PHASE_6_SEARCH_IMPLEMENTATION.md) | ✅ 완료 |
| **6-9** | [PHASE_7_EVENT_LISTENERS_IMPLEMENTATION.md](./PHASE_7_EVENT_LISTENERS_IMPLEMENTATION.md) | ✅ 완료 |
| **8** | [PHASE_8_UI_IMPROVEMENTS.md](./PHASE_8_UI_IMPROVEMENTS.md) | ✅ 완료 |
| **9** | [PHASE_9_SSE_IMPLEMENTATION.md](./PHASE_9_SSE_IMPLEMENTATION.md) | ✅ 완료 |
| **10** | [PHASE_10_FILE_PARSING.md](./PHASE_10_FILE_PARSING.md) | ✅ 완료 |
| **11** | [PHASE_11_CERTIFICATE_VALIDATION.md](./PHASE_11_CERTIFICATE_VALIDATION.md) | ✅ 완료 |
| **12** | [PHASE_12_COMPLETE.md](./PHASE_12_COMPLETE.md) | ✅ 완료 |
| **13-16** | Phase 13-16 문서들 | ✅ 완료 |
| **17** | [PHASE_17_PLAN.md](./PHASE_17_PLAN.md) | ✅ 완료 |
| **18+** | [TODO.md](./TODO.md) | 📅 계획 |

### 🎯 기능별 문서
| 기능 | 파일 | 상태 |
|------|------|------|
| 파일 업로드 | [file_upload_implementation_plan.md](./file_upload_implementation_plan.md) | ✅ 완료 |
| 중복 검사 | [duplicate_check_feature_summary.md](./duplicate_check_feature_summary.md) | ✅ 완료 |
| 파일 파싱 | [PHASE_10_FILE_PARSING.md](./PHASE_10_FILE_PARSING.md) | ✅ 완료 |
| 인증서 검증 | [PHASE_11_CERTIFICATE_VALIDATION.md](./PHASE_11_CERTIFICATE_VALIDATION.md) | ✅ 완료 |
| LDAP 통합 | [PHASE_14_WEEK1_FINAL_REPORT.md](./PHASE_14_WEEK1_FINAL_REPORT.md) | ✅ 완료 |
| SSE 진행률 | [PHASE_9_SSE_IMPLEMENTATION.md](./PHASE_9_SSE_IMPLEMENTATION.md) | ✅ 완료 |

---

## 🗂️ 문서 조직 구조

### Main Docs (60개)
현재 프로젝트에서 참고해야 하는 문서들

```
📁 docs/
├── 🌟 00_START_HERE.md               ← 여기서 시작!
├── 🌟 PROJECT_MASTER_SUMMARY_2025-11-07.md
├── 🌟 DOCUMENTATION_INDEX_2025-11-07.md
│
├── 🏗️ ddd-msa-migration-roadmap.md
├── 🏗️ DDD_IMPLEMENTATION_COMPLETE.md
├── 🏗️ ddd_refactoring_strategy.md
│
├── 📋 PHASE_6_SEARCH_IMPLEMENTATION.md
├── 📋 PHASE_7_EVENT_LISTENERS_IMPLEMENTATION.md
├── 📋 PHASE_8_UI_IMPROVEMENTS.md
├── 📋 PHASE_9_SSE_IMPLEMENTATION.md
├── 📋 PHASE_10_FILE_PARSING.md
├── 📋 ... (Phase 11-17)
│
├── 🎯 file_upload_implementation_plan.md
├── 🎯 duplicate_check_feature_summary.md
├── 🎯 API_REFERENCE_LDAP_MODULE.md
├── 🎯 LDAP_USAGE_EXAMPLES_CONFIGURATION.md
│
├── 🔧 FRONTEND_CODING_STANDARDS.md
├── 🔧 TODO.md
├── 🔧 README_DOCS.md              ← 이 파일
│
└── 📦 archive/                     ← 아카이브 (21개)
    ├── project_status_history/     (이전 버전)
    ├── phase_18_detailed/           (상세 분석)
    └── references/                  (참고 문서)
```

---

## 🔍 문서 검색 팁

### 특정 기능을 찾으려면
1. [DOCUMENTATION_INDEX_2025-11-07.md](./DOCUMENTATION_INDEX_2025-11-07.md) 의 "기능별" 섹션 참고
2. 원하는 기능명 검색

### 특정 Phase를 보려면
1. `PHASE_XX_` 로 시작하는 파일 찾기
2. Phase 번호로 정렬하면 순서대로 볼 수 있음

### API/설정을 찾으려면
1. `API_REFERENCE_` 또는 `USAGE_EXAMPLES_` 파일 확인
2. LDAP, FRONTEND 등 모듈별 파일 제공

### 최신 정보를 원하면
1. [00_START_HERE.md](./00_START_HERE.md) 또는
2. [PROJECT_MASTER_SUMMARY_2025-11-07.md](./PROJECT_MASTER_SUMMARY_2025-11-07.md) 확인

---

## 📊 문서 현황 (2025-11-07)

| 카테고리 | 수량 | 상태 |
|---------|------|------|
| **Main Docs** | 60개 | ✅ 정리 완료 |
| **Archive** | 21개 | ✅ 아카이브 완료 |
| **총합** | 81개 | ✅ 100% 관리 |

### Archive 상세 (21개)
- **프로젝트 상태 이력**: 2개 (PROJECT_STATUS, FINAL_PROJECT_STATUS 이전 버전)
- **Phase 18 상세**: 15개 (PHASE_18_* 상세 분석 문서)
- **기타**: 4개

---

## 💡 사용 사례별 가이드

### 📌 "프로젝트를 처음 접합니다"
1. [00_START_HERE.md](./00_START_HERE.md) (5분)
2. [PROJECT_MASTER_SUMMARY_2025-11-07.md](./PROJECT_MASTER_SUMMARY_2025-11-07.md) (15분)
3. [ddd-msa-migration-roadmap.md](./ddd-msa-migration-roadmap.md) (20분)

### 📌 "코드를 읽고 싶습니다"
1. [DDD_IMPLEMENTATION_COMPLETE.md](./DDD_IMPLEMENTATION_COMPLETE.md) - 구조 이해
2. [PHASE_XX_*.md](./PHASE_10_FILE_PARSING.md) - 각 Phase 상세
3. [FRONTEND_CODING_STANDARDS.md](./FRONTEND_CODING_STANDARDS.md) - Frontend 규칙

### 📌 "특정 기능을 구현해야 합니다"
1. [DOCUMENTATION_INDEX_2025-11-07.md](./DOCUMENTATION_INDEX_2025-11-07.md) - 관련 문서 찾기
2. 해당 기능의 Phase 문서 읽기
3. 예시 코드 또는 API 레퍼런스 참고

### 📌 "프로젝트 진행 상황을 보고합니다"
1. [PROJECT_MASTER_SUMMARY_2025-11-07.md](./PROJECT_MASTER_SUMMARY_2025-11-07.md) - 현황
2. [TODO.md](./TODO.md) - 다음 계획
3. 각 Phase 완료 문서 - 상세 내용

### 📌 "문서를 관리해야 합니다"
1. [00_START_HERE.md](./00_START_HERE.md) - "문서 정리 규칙" 섹션
2. [DOCUMENTATION_INDEX_2025-11-07.md](./DOCUMENTATION_INDEX_2025-11-07.md) - 전체 구조
3. Git commit 시 명확히 기록

---

## ❓ 자주 묻는 질문

### Q: 어떤 문서를 먼저 읽어야 하나요?
**A**: [00_START_HERE.md](./00_START_HERE.md) 에서 역할별 읽기 순서를 제시합니다.

### Q: 특정 기능의 구현 상태를 알고 싶어요
**A**: [PROJECT_MASTER_SUMMARY_2025-11-07.md](./PROJECT_MASTER_SUMMARY_2025-11-07.md) 의 "Phase 진행률" 표 확인

### Q: 오래된 문서는 어디 있나요?
**A**: `archive/` 폴더에 보관되어 있습니다.
- 프로젝트 상태 이력: `archive/project_status_history/`
- Phase 상세: `archive/phase_18_detailed/`

### Q: 새로운 문서를 어디에 만들어야 하나요?
**A**: [00_START_HERE.md](./00_START_HERE.md) 의 "문서 정리 규칙" 섹션 참고

### Q: 문서가 손상되었거나 오래되었어요
**A**: [DOCUMENTATION_INDEX_2025-11-07.md](./DOCUMENTATION_INDEX_2025-11-07.md) 의 "Issues & Cleanup" 섹션 확인

---

## 📅 최근 정리 이력

| 날짜 | 작업 | 결과 |
|------|------|------|
| 2025-11-07 | 문서 통합 정리 | 60개 Main + 21개 Archive = 81개 |
| 2025-11-07 | 00_START_HERE.md 작성 | 통합 시작 가이드 추가 |
| 2025-11-07 | 프로젝트 상태 문서 아카이브 | 3개 → archive/project_status_history/ |
| 2025-11-07 | PHASE_18 상세 문서 아카이브 | 15개 → archive/phase_18_detailed/ |

---

## 🔄 다음 정리 (예정)

- **일시**: 2025-11-14
- **작업**:
  - Phase 18 구현 문서 추가
  - archive 문서 검토 및 정리
  - 색인 업데이트

---

## 📞 문서 관련 연락

- **오류 보고**: [DOCUMENTATION_INDEX_2025-11-07.md](./DOCUMENTATION_INDEX_2025-11-07.md) 의 "Issues" 섹션
- **새 문서 제안**: 위의 "문서 정리 규칙" 참고
- **아카이빙 요청**: PR 생성 또는 이슈 등록

---

**Happy Reading! 📚**
*마지막 업데이트: 2025-11-07*
