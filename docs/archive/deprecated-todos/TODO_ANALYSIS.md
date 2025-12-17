# TODO 분석 및 정리

**분석 날짜**: 2025-11-21
**총 TODO 개수**: 105개

## 1. Deprecated 레거시 코드 TODO (제거 필요)

### ldapintegration 패키지 (12개)
- `LdapUploadApiController.java:170` - 실제 상태 조회 구현
- `LdapUploadEventHandler.java:64,79,80,81,132,140,156` - Phase 4 구현
- `UploadToLdapUseCase.java:112,135,226,247` - Phase 4 LDAP 업로드 로직

**조치**: 이 파일들은 deprecated되었으며, certificatevalidation 패키지의 새로운 구현으로 교체되었습니다.
향후 리팩토링에서 완전히 제거 예정.

---

## 2. Manual Mode 관련 TODO (구현 보류)

### ProcessingController (11개)
- Line 126: Phase 19 Use Cases 구현 예정
- Line 199: ParseFileUseCase 호출
- Line 275: ValidateCertificatesUseCase 호출  
- Line 351: UploadToLdapUseCase 호출
- Line 428,434,435,438: 데이터베이스 상태 조회

**현황**: Manual Mode는 현재 Auto Mode와 함께 Dual Mode Architecture로 구현됨.
ProcessingController는 Manual Mode용 개별 단계 트리거 API이지만, 현재는 Event-Driven Auto Mode가 주로 사용됨.

**조치**: Manual Mode 실제 사용 여부 확인 후 결정.

---

## 3. Future Enhancements (선택적 구현)

### 메트릭 및 모니터링 (9개)
- ChecksumValidationEventListener:68 - Prometheus 메트릭
- FileUploadEventHandler:105,276 - 통계 업데이트
- FileUploadFailedEventListener:83 - 메트릭 증가
- FileUploadCompletedEventListener:86 - 메트릭 증가

### 알림 시스템 (6개)
- ChecksumValidationEventListener:102 - 관리자 알림
- FileUploadEventHandler:262,279 - 알림 발송
- FileUploadFailedEventListener:134 - 알림 발송  
- FileUploadCompletedEventListener:119 - 완료 알림

### 리포트 및 분석 (4개)
- ChecksumValidationEventListener:109 - 에러 리포트
- FileUploadEventHandler:293 - 중복 패턴 분석
- FileUploadCompletedEventListener:127 - 리포트 생성

### 자동화 (2개)
- FileUploadEventHandler:292,305 - 중복 파일 자동 삭제
- FileUploadFailedEventListener:124 - 재시도 큐 등록

**조치**: Phase 20 (모니터링 & 운영) 에서 구현 계획.

---

## 4. 인증서 검증 개선 TODO (향후 Phase)

### BouncyCastleValidationAdapter (6개)
- Line 106: 고급 CRL 확장 검증
- Line 228: 인증서 정책 검증
- Line 284: 이름 제약 검증
- Line 368: OCSP 응답 캐싱
- Line 744: CRL 캐싱 개선

**현황**: 기본적인 검증 기능은 구현 완료. 고급 검증은 ICAO 표준 확장 기능.

**조치**: 실제 운영 환경에서 필요성 확인 후 구현.

---

## 5. 이벤트 발행 TODO (구현 필요)

### UploadToLdapEventHandler (2개)
- Line 185,186: UploadToLdapCompletedEvent 발행

**현황**: 이벤트 클래스는 존재하지만 실제 발행 로직이 TODO로 남아있음.

**조치**: Phase 17에서 구현했어야 했으나 누락. 즉시 구현 필요.

---

## 6. API 상태 조회 TODO (구현 필요)

### CertificateValidationApiController (1개)
- Line 158: 실제 상태 조회 구현

**현황**: Mock 응답 반환 중.

**조치**: 데이터베이스 기반 상태 조회 구현.

---

## 7. 파싱 후 검증 트리거 TODO (완료?)

### LdifParsingEventHandler (1개)
- Line 75: Certificate Validation Context 검증 트리거

**현황**: 이벤트 기반 자동 검증이 이미 구현되어 있을 가능성 높음.

**조치**: 코드 확인 후 TODO 제거.

---

## 우선순위별 조치 계획

### High Priority (즉시)
1. UploadToLdapCompletedEvent 발행 구현
2. CertificateValidationApiController 상태 조회 구현
3. LdifParsingEventHandler TODO 확인 및 제거

### Medium Priority (Phase 19-20)
1. Future Enhancements 중 메트릭/알림 시스템
2. ProcessingController Manual Mode 활용 여부 결정

### Low Priority (향후)
1. Deprecated ldapintegration 패키지 완전 제거
2. BouncyCastleValidationAdapter 고급 검증 기능
