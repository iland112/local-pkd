# TODO: Interleaved Batch Processing 구현

**작성일**: 2025-12-20
**브랜치**: `feature/interleaved-batch-processing`
**관련 문서**: [CERTIFICATE_VALIDATION_LDAP_OPTIMIZATION.md](./CERTIFICATE_VALIDATION_LDAP_OPTIMIZATION.md)

---

## 작업 개요

Certificate Validation과 LDAP Upload를 배치 단위로 인터리빙하여 성능 최적화

**예상 효과**:
- 처리 시간 20-30% 감소
- 메모리 사용 40-50% 감소
- 사용자 경험 개선 (균일한 진행률)

---

## Phase 1: 인프라 준비

### Task 1.1: LdapBatchUploadService 생성
**파일**: `src/main/java/com/smartcoreinc/localpkd/certificatevalidation/application/service/LdapBatchUploadService.java`

```java
@Service
@RequiredArgsConstructor
@Slf4j
public class LdapBatchUploadService {
    private final LdapConnectionAdapter ldapAdapter;
    private final LdifConverter ldifConverter;

    public LdapBatchUploadResult uploadCertificates(List<Certificate> certificates);
    public LdapBatchUploadResult uploadCrls(List<CertificateRevocationList> crls);
}
```

**체크리스트**:
- [ ] 서비스 클래스 생성
- [ ] LdapConnectionAdapter 의존성 주입
- [ ] LdifConverter 의존성 주입
- [ ] uploadCertificates() 메서드 구현
- [ ] uploadCrls() 메서드 구현
- [ ] LdapBatchUploadResult DTO 생성
- [ ] 단위 테스트 작성

### Task 1.2: ValidateCertificatesUseCase 의존성 추가
**파일**: `src/main/java/com/smartcoreinc/localpkd/certificatevalidation/application/usecase/ValidateCertificatesUseCase.java`

**체크리스트**:
- [ ] LdapBatchUploadService 필드 추가
- [ ] 생성자에 의존성 주입 추가
- [ ] import 문 추가

---

## Phase 2: 핵심 로직 수정

### Task 2.1: Pass 1 (CSCA) 배치 처리 수정
**파일**: `ValidateCertificatesUseCase.java` (Line 137-252)

**현재 코드**:
```java
if (cscaBatch.size() >= BATCH_SIZE) {
    certificateRepository.saveAll(cscaBatch);
    cscaBatch.clear();
}
```

**수정 코드**:
```java
if (cscaBatch.size() >= BATCH_SIZE) {
    // 1. DB 저장
    certificateRepository.saveAll(cscaBatch);

    // 2. LDAP 업로드 (NEW)
    LdapBatchUploadResult result = ldapBatchUploadService.uploadCertificates(cscaBatch);
    log.info("CSCA batch LDAP upload: {} success, {} failed",
        result.successCount(), result.failedCount());

    cscaBatch.clear();
}
```

**체크리스트**:
- [ ] 배치 저장 후 LDAP 업로드 호출 추가
- [ ] 남은 배치 처리 로직 수정 (Line 256-262)
- [ ] 진행률 계산 조정 (55-75%)
- [ ] 로깅 추가

### Task 2.2: Pass 2 (DSC) 배치 처리 수정
**파일**: `ValidateCertificatesUseCase.java` (Line 268-400)

**체크리스트**:
- [ ] 배치 저장 후 LDAP 업로드 호출 추가
- [ ] 남은 배치 처리 로직 수정
- [ ] 진행률 계산 조정 (75-95%)
- [ ] 로깅 추가

### Task 2.3: CRL 배치 처리 수정
**파일**: `ValidateCertificatesUseCase.java` (Line 410-480)

**체크리스트**:
- [ ] 배치 저장 후 LDAP 업로드 호출 추가
- [ ] 진행률 계산 조정 (95-100%)
- [ ] 로깅 추가

---

## Phase 3: 정리 및 통합

### Task 3.1: UploadToLdapUseCase 검토
**파일**: `src/main/java/com/smartcoreinc/localpkd/ldapintegration/application/usecase/UploadToLdapUseCase.java`

**옵션**:
1. **유지 (권장)**: 재시도/누락 복구용으로 보존
2. **단순화**: LDAP 미업로드 항목만 처리하도록 수정
3. **제거**: FileUploadEventHandler에서 호출 제거

**체크리스트**:
- [ ] 옵션 결정
- [ ] 필요시 코드 수정
- [ ] FileUploadEventHandler 수정

### Task 3.2: 이벤트 흐름 조정
**파일**: `src/main/java/com/smartcoreinc/localpkd/fileparsing/application/event/FileUploadEventHandler.java`

**현재 흐름**:
```
CertificatesValidatedEvent → UploadToLdapUseCase 호출
```

**수정 흐름**:
```
CertificatesValidatedEvent → (LDAP 이미 완료, 추가 작업 불필요)
```

**체크리스트**:
- [ ] handleCertificatesValidatedEvent() 수정
- [ ] LDAP 업로드 호출 제거 또는 조건부 실행
- [ ] 상태 업데이트 로직 유지

### Task 3.3: 진행률 UI 조정
**파일**: `src/main/resources/templates/file/upload.html`

**체크리스트**:
- [ ] 진행률 단계 설명 수정 (필요시)
- [ ] SSE 메시지 처리 확인

---

## Phase 4: 테스트

### Task 4.1: 단위 테스트
**파일**: `src/test/java/com/smartcoreinc/localpkd/certificatevalidation/application/service/LdapBatchUploadServiceTest.java`

**체크리스트**:
- [ ] 정상 케이스 테스트
- [ ] LDAP 실패 케이스 테스트
- [ ] 빈 배치 테스트
- [ ] 부분 성공 테스트

### Task 4.2: 통합 테스트
**파일**: `src/test/java/com/smartcoreinc/localpkd/certificatevalidation/application/usecase/ValidateCertificatesUseCaseIntegrationTest.java`

**체크리스트**:
- [ ] 전체 흐름 테스트 (ML 파일)
- [ ] 전체 흐름 테스트 (LDIF 파일)
- [ ] LDAP 연결 실패 시 동작 확인
- [ ] 트랜잭션 롤백 시나리오

### Task 4.3: 성능 벤치마크
**체크리스트**:
- [ ] 대용량 파일 테스트 (10,000+ 인증서)
- [ ] 처리 시간 측정 (개선 전/후 비교)
- [ ] 메모리 사용량 측정

---

## Phase 5: 문서화

### Task 5.1: CLAUDE.md 업데이트
**체크리스트**:
- [ ] 아키텍처 변경 사항 반영
- [ ] Phase 완료 상태 업데이트

### Task 5.2: 세션 문서 작성
**파일**: `docs/SESSION_2025-12-XX_INTERLEAVED_BATCH_PROCESSING.md`

**체크리스트**:
- [ ] 변경 내역 정리
- [ ] 성능 벤치마크 결과
- [ ] 알려진 이슈

---

## 작업 순서 (권장)

```
Phase 1 (인프라) ──▶ Phase 2 (핵심 로직) ──▶ Phase 3 (정리) ──▶ Phase 4 (테스트) ──▶ Phase 5 (문서화)
     │                    │                    │                    │
     ▼                    ▼                    ▼                    ▼
Task 1.1 ────────▶ Task 2.1 ────────▶ Task 3.1 ────────▶ Task 4.1
Task 1.2            Task 2.2            Task 3.2            Task 4.2
                    Task 2.3            Task 3.3            Task 4.3
```

---

## 롤백 계획

문제 발생 시:
1. LdapBatchUploadService 호출 제거
2. FileUploadEventHandler 원복
3. 기존 UploadToLdapUseCase 활성화

---

## 관련 파일 목록

### 수정 필요
- `ValidateCertificatesUseCase.java`
- `FileUploadEventHandler.java`
- `UploadToLdapUseCase.java` (옵션)

### 신규 생성
- `LdapBatchUploadService.java`
- `LdapBatchUploadResult.java`
- `LdapBatchUploadServiceTest.java`

### 테스트 수정
- `ValidateCertificatesUseCaseIntegrationTest.java`

---

**문서 버전**: 1.0
**상태**: 작업 계획 완료, 구현 대기
