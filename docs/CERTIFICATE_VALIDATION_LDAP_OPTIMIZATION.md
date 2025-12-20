# Certificate Validation & LDAP Upload 최적화 분석

**작성일**: 2025-12-20
**작성자**: Claude Code Assistant
**상태**: 분석 완료, 구현 대기

---

## 1. 현재 아키텍처 분석

### 1.1 현재 처리 흐름 (Sequential Two-Phase)

```
┌─────────────────────────────────────────────────────────────────────┐
│                    Phase 1: Certificate Validation                   │
│                    (ValidateCertificatesUseCase)                     │
├─────────────────────────────────────────────────────────────────────┤
│                                                                      │
│  ┌──────────────┐    ┌──────────────┐    ┌──────────────┐          │
│  │  Pass 1:     │───▶│  Pass 2:     │───▶│  CRL 처리    │          │
│  │  CSCA 검증   │    │  DSC 검증    │    │  (배치 저장) │          │
│  │  (배치 저장) │    │  (배치 저장) │    │              │          │
│  └──────────────┘    └──────────────┘    └──────────────┘          │
│         │                   │                   │                   │
│         ▼                   ▼                   ▼                   │
│  ┌─────────────────────────────────────────────────────────────┐   │
│  │                    PostgreSQL Database                       │   │
│  │         (certificate, certificate_revocation_list)          │   │
│  └─────────────────────────────────────────────────────────────┘   │
│                                                                      │
│  ✅ 모든 검증 완료 후 CertificatesValidatedEvent 발행               │
└─────────────────────────────────────────────────────────────────────┘
                                    │
                                    ▼ (Event)
┌─────────────────────────────────────────────────────────────────────┐
│                    Phase 2: LDAP Upload                              │
│                    (UploadToLdapUseCase)                            │
├─────────────────────────────────────────────────────────────────────┤
│                                                                      │
│  ┌──────────────┐    ┌──────────────┐    ┌──────────────┐          │
│  │  DB에서      │───▶│  LDIF 변환   │───▶│  LDAP 배치   │          │
│  │  전체 조회   │    │  (각 항목)   │    │  업로드      │          │
│  └──────────────┘    └──────────────┘    └──────────────┘          │
│         │                                                            │
│         ▼ 재조회                                                     │
│  ┌─────────────────────────────────────────────────────────────┐   │
│  │                    PostgreSQL Database                       │   │
│  └─────────────────────────────────────────────────────────────┘   │
│                                                                      │
└─────────────────────────────────────────────────────────────────────┘
```

### 1.2 주요 병목 지점

| # | 병목 지점 | 설명 | 영향 |
|---|-----------|------|------|
| 1 | **Two-Phase Commit** | DB 저장 완료 → LDAP 업로드 순차적 | 전체 처리 시간 증가 |
| 2 | **DB 재조회** | LDAP 단계에서 `findByUploadId()` 다시 호출 | 불필요한 I/O |
| 3 | **대기 시간** | 수천 개 인증서 전체 검증 완료 대기 후 LDAP 시작 | 사용자 체감 시간 증가 |
| 4 | **메모리 사용** | 전체 인증서 목록을 메모리에 유지 | 대용량 파일 처리 시 OOM 위험 |

### 1.3 현재 코드 분석

**ValidateCertificatesUseCase.java** (1,105 lines):
- Line 137-252: Pass 1 CSCA 검증 + 배치 DB 저장
- Line 268-400: Pass 2 DSC 검증 + 배치 DB 저장
- Line 410-480: CRL 처리 + 배치 DB 저장
- Line 490: CertificatesValidatedEvent 발행

**UploadToLdapUseCase.java** (347 lines):
- Line 110: `certificateRepository.findByUploadId()` - DB 재조회
- Line 130-180: 인증서 LDAP 배치 업로드
- Line 190-230: CRL LDAP 배치 업로드

---

## 2. 개선 방향

### 2.1 목표

1. **처리 시간 단축**: 검증과 LDAP 업로드를 인터리빙하여 파이프라인 효과
2. **메모리 효율화**: 전체 목록 대신 배치 단위로만 메모리 사용
3. **DB 재조회 제거**: 검증된 인증서를 직접 LDAP으로 전달
4. **진행률 개선**: 사용자에게 더 일정한 진행률 제공

### 2.2 제안 아키텍처: Interleaved Batch Processing

```
┌─────────────────────────────────────────────────────────────────────┐
│              Improved: Interleaved Batch Processing                  │
│              (ValidateCertificatesUseCase 통합)                     │
├─────────────────────────────────────────────────────────────────────┤
│                                                                      │
│  ┌────────────────────────────────────────────────────────────────┐ │
│  │                      Pass 1: CSCA Processing                    │ │
│  │  ┌─────────┐   ┌─────────┐   ┌─────────┐   ┌─────────┐        │ │
│  │  │ Batch 1 │──▶│ Batch 2 │──▶│ Batch 3 │──▶│ Batch N │        │ │
│  │  │ 1000개  │   │ 1000개  │   │ 1000개  │   │ 나머지  │        │ │
│  │  └────┬────┘   └────┬────┘   └────┬────┘   └────┬────┘        │ │
│  │       │             │             │             │              │ │
│  │       ▼             ▼             ▼             ▼              │ │
│  │  ┌─────────┐   ┌─────────┐   ┌─────────┐   ┌─────────┐        │ │
│  │  │DB Save  │   │DB Save  │   │DB Save  │   │DB Save  │        │ │
│  │  │+ LDAP   │   │+ LDAP   │   │+ LDAP   │   │+ LDAP   │        │ │
│  │  └─────────┘   └─────────┘   └─────────┘   └─────────┘        │ │
│  └────────────────────────────────────────────────────────────────┘ │
│                              │                                       │
│                              ▼ CSCA Cache 빌드                       │
│  ┌────────────────────────────────────────────────────────────────┐ │
│  │                      Pass 2: DSC Processing                     │ │
│  │  ┌─────────┐   ┌─────────┐   ┌─────────┐   ┌─────────┐        │ │
│  │  │ Batch 1 │──▶│ Batch 2 │──▶│ Batch 3 │──▶│ Batch N │        │ │
│  │  └────┬────┘   └────┬────┘   └────┬────┘   └────┬────┘        │ │
│  │       │             │             │             │              │ │
│  │       ▼             ▼             ▼             ▼              │ │
│  │  ┌─────────┐   ┌─────────┐   ┌─────────┐   ┌─────────┐        │ │
│  │  │DB Save  │   │DB Save  │   │DB Save  │   │DB Save  │        │ │
│  │  │+ LDAP   │   │+ LDAP   │   │+ LDAP   │   │+ LDAP   │        │ │
│  │  └─────────┘   └─────────┘   └─────────┘   └─────────┘        │ │
│  └────────────────────────────────────────────────────────────────┘ │
│                                                                      │
│  ┌────────────────────────────────────────────────────────────────┐ │
│  │                      CRL Processing                             │ │
│  │  동일한 패턴: 배치 단위 DB Save + LDAP Upload                   │ │
│  └────────────────────────────────────────────────────────────────┘ │
│                                                                      │
└─────────────────────────────────────────────────────────────────────┘
```

### 2.3 핵심 변경 사항

#### A. ValidateCertificatesUseCase 수정

```java
// 현재 코드
if (cscaBatch.size() >= BATCH_SIZE) {
    certificateRepository.saveAll(cscaBatch);  // DB만 저장
    cscaBatch.clear();
}

// 개선 코드
if (cscaBatch.size() >= BATCH_SIZE) {
    // 1. DB 저장 (동기)
    certificateRepository.saveAll(cscaBatch);

    // 2. LDAP 업로드 (동기 - 같은 배치 사용)
    ldapBatchUploadService.uploadCertificates(cscaBatch);

    cscaBatch.clear();
}
```

#### B. 새로운 서비스 추가: LdapBatchUploadService

```java
@Service
public class LdapBatchUploadService {

    private final LdapConnectionAdapter ldapAdapter;
    private final LdifConverter ldifConverter;

    public void uploadCertificates(List<Certificate> certificates) {
        List<String> ldifEntries = certificates.stream()
            .map(ldifConverter::certificateToLdif)
            .collect(Collectors.toList());

        ldapAdapter.addLdifEntriesBatch(ldifEntries);

        // Mark as uploaded
        certificates.forEach(Certificate::markAsUploadedToLdap);
    }

    public void uploadCrls(List<CertificateRevocationList> crls) {
        // 동일한 패턴
    }
}
```

#### C. UploadToLdapUseCase 단순화

개선 후에는 이 UseCase가 대폭 단순화됩니다:
- DB 재조회 불필요
- 주로 "누락된 항목" 재처리용으로 유지
- 또는 완전히 제거 가능

---

## 3. 예상 효과

### 3.1 성능 개선

| 지표 | 현재 | 개선 후 | 개선율 |
|------|------|---------|--------|
| **전체 처리 시간** | 100% | 70-80% | 20-30% 감소 |
| **메모리 사용** | 100% | 50-60% | 40-50% 감소 |
| **DB 쿼리 수** | N + 2 (재조회) | N | 2회 감소 |
| **진행률 균일성** | 불균일 (90%에서 멈춤) | 균일 | 개선 |

### 3.2 시나리오별 예상 시간 (10,000개 인증서)

| 단계 | 현재 | 개선 후 |
|------|------|---------|
| CSCA 검증 (520개) | 5초 | 5초 |
| CSCA DB 저장 | 2초 | 0.2초 × 1회 |
| CSCA LDAP 업로드 | - | 0.5초 × 1회 |
| DSC 검증 (9,480개) | 30초 | 30초 |
| DSC DB 저장 | 10초 | 1초 × 10회 |
| DSC LDAP 업로드 | - | 2초 × 10회 |
| DB 재조회 (LDAP 단계) | 5초 | 0초 |
| LDAP 업로드 (별도) | 20초 | 0초 (이미 완료) |
| **합계** | ~72초 | ~50초 |

---

## 4. 구현 계획

### 4.1 Phase 1: 인프라 준비

1. **LdapBatchUploadService 생성**
   - `certificatevalidation/application/service/` 에 위치
   - LdifConverter, LdapConnectionAdapter 의존성 주입
   - 배치 단위 LDAP 업로드 메서드

2. **ValidateCertificatesUseCase 의존성 추가**
   - LdapBatchUploadService 주입
   - 배치 저장 로직에 LDAP 업로드 통합

### 4.2 Phase 2: 핵심 로직 수정

3. **Pass 1 (CSCA) 수정**
   - 배치 DB 저장 직후 LDAP 업로드 호출
   - 진행률 계산 로직 조정 (55-75%로 확장)

4. **Pass 2 (DSC) 수정**
   - 동일한 패턴 적용
   - 진행률 범위: 75-95%

5. **CRL 처리 수정**
   - 동일한 패턴 적용
   - 진행률 범위: 95-100%

### 4.3 Phase 3: 정리 및 테스트

6. **UploadToLdapUseCase 검토**
   - 재시도 로직만 유지하거나
   - 완전히 제거 고려

7. **이벤트 흐름 조정**
   - CertificatesValidatedEvent 발행 시점 조정
   - FileUploadEventHandler에서 LDAP 호출 제거

8. **통합 테스트**
   - 기존 테스트 수정
   - 성능 벤치마크

---

## 5. 위험 요소 및 대응

### 5.1 트랜잭션 경계

**위험**: 현재 `@Transactional`이 전체 메서드에 걸려있어 LDAP 실패 시 DB도 롤백

**대응**:
- LDAP 업로드를 `@Transactional(propagation = REQUIRES_NEW)`로 분리
- 또는 DB 저장 후 LDAP 업로드 (LDAP 실패해도 DB는 유지)
- `uploadedToLdap` 플래그로 상태 관리

### 5.2 Two-Pass 제약

**위험**: CSCA 캐시가 DSC 검증에 필요

**대응**:
- Pass 1 (CSCA) 완전 완료 후 캐시 빌드
- Pass 2 (DSC)는 캐시 빌드 후 시작
- CSCA LDAP 업로드는 Pass 1에서 완료되므로 문제 없음

### 5.3 LDAP 연결 풀

**위험**: 배치마다 LDAP 연결 시 오버헤드

**대응**:
- UnboundID SDK 연결 풀 활용 (이미 구현됨)
- 배치 크기 조정 (1000개 → 500개)으로 균형 유지

---

## 6. 결론

제안된 **Interleaved Batch Processing** 방식은:

1. ✅ 기존 아키텍처 최소 변경
2. ✅ 20-30% 성능 개선 예상
3. ✅ 메모리 효율 40-50% 개선
4. ✅ 사용자 경험 향상 (균일한 진행률)
5. ✅ 점진적 구현 가능 (Phase별)

**다음 단계**: 새 브랜치에서 구현 시작

---

## 관련 파일

- `src/main/java/com/smartcoreinc/localpkd/certificatevalidation/application/usecase/ValidateCertificatesUseCase.java`
- `src/main/java/com/smartcoreinc/localpkd/ldapintegration/application/usecase/UploadToLdapUseCase.java`
- `src/main/java/com/smartcoreinc/localpkd/ldapintegration/infrastructure/adapter/LdifConverter.java`
- `src/main/java/com/smartcoreinc/localpkd/ldapintegration/infrastructure/adapter/UnboundIdLdapConnectionAdapter.java`

---

**문서 버전**: 1.0
**상태**: 분석 완료, 구현 대기
