package com.smartcoreinc.localpkd.certificatevalidation.domain.model;

import com.smartcoreinc.localpkd.certificatevalidation.domain.event.CertificateCreatedEvent;
import com.smartcoreinc.localpkd.certificatevalidation.domain.event.CertificateUploadedToLdapEvent;
import com.smartcoreinc.localpkd.certificatevalidation.domain.event.CertificateValidatedEvent;
import com.smartcoreinc.localpkd.shared.domain.AggregateRoot;
import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Certificate - X.509 인증서 Aggregate Root
 *
 * <p><b>DDD Aggregate Root Pattern</b>:</p>
 * <ul>
 *   <li>Boundary: 인증서 엔티티의 일관성 경계</li>
 *   <li>Identity: CertificateId를 통한 고유성 보장</li>
 *   <li>Lifecycle: 생성부터 폐기까지 관리</li>
 *   <li>Invariants: 모든 비즈니스 규칙 검증</li>
 * </ul>
 *
 * <p><b>책임</b>:</p>
 * <ul>
 *   <li>X.509 인증서의 완전한 생명주기 관리</li>
 *   <li>인증서 검증 상태 추적</li>
 *   <li>검증 결과 기록</li>
 *   <li>도메인 이벤트 발행</li>
 * </ul>
 *
 * <p><b>포함된 Value Objects</b>:</p>
 * <ul>
 *   <li>X509Data: 인증서 바이너리 데이터</li>
 *   <li>SubjectInfo: 인증서 주체 정보</li>
 *   <li>IssuerInfo: 인증서 발급자 정보</li>
 *   <li>ValidityPeriod: 유효기간</li>
 *   <li>CertificateType: 인증서 타입</li>
 *   <li>CertificateStatus: 검증 상태</li>
 *   <li>ValidationResult: 검증 결과</li>
 *   <li>ValidationError: 검증 오류 목록</li>
 * </ul>
 *
 * <p><b>사용 예시</b>:</p>
 * <pre>{@code
 * // 1. 인증서 생성
 * Certificate cert = Certificate.create(
 *     x509Data,                          // X.509 바이너리 데이터
 *     subjectInfo,                       // 주체 정보
 *     issuerInfo,                        // 발급자 정보
 *     validity,                          // 유효기간
 *     CertificateType.DSC,               // 인증서 타입
 *     "SHA256WithRSA"                    // 서명 알고리즘
 * );
 *
 * // 2. 검증 수행
 * ValidationResult validationResult = validator.validate(cert);
 * cert.recordValidation(validationResult);
 *
 * // 3. 상태 확인
 * if (cert.isValid()) {
 *     System.out.println("유효한 인증서입니다");
 * } else if (cert.isExpired()) {
 *     System.out.println("만료된 인증서입니다");
 * }
 *
 * // 4. 오류 확인
 * List<ValidationError> errors = cert.getValidationErrors();
 * for (ValidationError error : errors) {
 *     System.out.println(error.getErrorMessage());
 * }
 * }</pre>
 *
 * @see CertificateId
 * @see X509Data
 * @see SubjectInfo
 * @see IssuerInfo
 * @see ValidityPeriod
 * @see CertificateType
 * @see CertificateStatus
 * @see ValidationResult
 * @see ValidationError
 * @author SmartCore Inc.
 * @version 1.0
 * @since 2025-10-23
 */
@Entity
@Table(name = "certificate")
public class Certificate extends AggregateRoot<CertificateId> {

    /**
     * 인증서 고유 식별자 (JPearl)
     */
    @EmbeddedId
    private CertificateId id;

    /**
     * X.509 인증서 데이터
     *
     * <p>DER-encoded 인증서 바이너리, 공개 키, 일련 번호, 지문 포함</p>
     */
    @Embedded
    @AttributeOverride(name = "certificateBinary", column = @Column(name = "x509_certificate_binary"))
    @AttributeOverride(name = "serialNumber", column = @Column(name = "x509_serial_number"))
    @AttributeOverride(name = "fingerprintSha256", column = @Column(name = "x509_fingerprint_sha256"))
    private X509Data x509Data;

    /**
     * 인증서 주체(Subject) 정보
     */
    @Embedded
    @AttributeOverride(name = "distinguishedName", column = @Column(name = "subject_dn"))
    @AttributeOverride(name = "countryCode", column = @Column(name = "subject_country_code"))
    @AttributeOverride(name = "organization", column = @Column(name = "subject_organization"))
    @AttributeOverride(name = "organizationalUnit", column = @Column(name = "subject_organizational_unit"))
    @AttributeOverride(name = "commonName", column = @Column(name = "subject_common_name"))
    private SubjectInfo subjectInfo;

    /**
     * 인증서 발급자(Issuer) 정보
     */
    @Embedded
    @AttributeOverride(name = "distinguishedName", column = @Column(name = "issuer_dn"))
    @AttributeOverride(name = "countryCode", column = @Column(name = "issuer_country_code"))
    @AttributeOverride(name = "organization", column = @Column(name = "issuer_organization"))
    @AttributeOverride(name = "organizationalUnit", column = @Column(name = "issuer_organizational_unit"))
    @AttributeOverride(name = "commonName", column = @Column(name = "issuer_common_name"))
    @AttributeOverride(name = "isCA", column = @Column(name = "issuer_is_ca"))
    private IssuerInfo issuerInfo;

    /**
     * 인증서 유효기간
     */
    @Embedded
    @AttributeOverride(name = "notBefore", column = @Column(name = "not_before"))
    @AttributeOverride(name = "notAfter", column = @Column(name = "not_after"))
    private ValidityPeriod validity;

    /**
     * 인증서 타입 (CSCA, DSC, DSC_NC, DS, UNKNOWN)
     *
     * <p>ICAO PKD 표준에 따른 인증서 분류</p>
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "certificate_type", length = 30, nullable = false)
    private CertificateType certificateType;

    /**
     * 현재 검증 상태
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 30, nullable = false)
    private CertificateStatus status;

    /**
     * 서명 알고리즘 (예: "SHA256WithRSA", "SHA256WithEC")
     */
    @Column(name = "signature_algorithm", length = 50)
    private String signatureAlgorithm;

    /**
     * 최근 검증 결과
     */
    @Embedded
    @AttributeOverride(name = "overallStatus", column = @Column(name = "validation_overall_status"))
    @AttributeOverride(name = "signatureValid", column = @Column(name = "validation_signature_valid"))
    @AttributeOverride(name = "chainValid", column = @Column(name = "validation_chain_valid"))
    @AttributeOverride(name = "notRevoked", column = @Column(name = "validation_not_revoked"))
    @AttributeOverride(name = "validityValid", column = @Column(name = "validation_validity_valid"))
    @AttributeOverride(name = "constraintsValid", column = @Column(name = "validation_constraints_valid"))
    @AttributeOverride(name = "validatedAt", column = @Column(name = "validation_validated_at"))
    @AttributeOverride(name = "validationDurationMillis", column = @Column(name = "validation_duration_millis"))
    private ValidationResult validationResult;

    /**
     * 검증 오류 목록
     *
     * <p>검증 중 발생한 모든 오류를 기록합니다 (ERROR, WARNING 포함)</p>
     */
    @ElementCollection
    @CollectionTable(
        name = "certificate_validation_error",
        joinColumns = @JoinColumn(name = "certificate_id")
    )
    @AttributeOverride(name = "errorCode", column = @Column(name = "error_code"))
    @AttributeOverride(name = "errorMessage", column = @Column(name = "error_message"))
    @AttributeOverride(name = "severity", column = @Column(name = "error_severity"))
    @AttributeOverride(name = "occurredAt", column = @Column(name = "error_occurred_at"))
    private List<ValidationError> validationErrors = new ArrayList<>();

    /**
     * 인증서 생성 시간
     */
    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    /**
     * 인증서 마지막 수정 시간
     */
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    /**
     * 원본 업로드 파일 ID (File Upload Context)
     *
     * <p>이 인증서가 추출된 원본 LDIF/ML 파일의 uploadId입니다.</p>
     * <p>Cross-Context Reference: UploadedFile Aggregate와 연결</p>
     */
    @Column(name = "upload_id", nullable = false)
    private java.util.UUID uploadId;

    /**
     * 인증서가 LDAP 디렉토리에 저장되었는지 여부
     */
    @Column(name = "uploaded_to_ldap", nullable = false)
    private Boolean uploadedToLdap = false;

    /**
     * LDAP 업로드 시간
     */
    @Column(name = "uploaded_to_ldap_at")
    private LocalDateTime uploadedToLdapAt;

    // ========== Constructors ==========

    /**
     * JPA용 기본 생성자 (protected)
     */
    protected Certificate() {
    }

    /**
     * Private 비즈니스 생성자
     */
    private Certificate(
            CertificateId id,
            java.util.UUID uploadId,
            X509Data x509Data,
            SubjectInfo subjectInfo,
            IssuerInfo issuerInfo,
            ValidityPeriod validity,
            CertificateType certificateType,
            String signatureAlgorithm
    ) {
        this.id = id;
        this.uploadId = uploadId;
        this.x509Data = x509Data;
        this.subjectInfo = subjectInfo;
        this.issuerInfo = issuerInfo;
        this.validity = validity;
        this.certificateType = certificateType;
        this.signatureAlgorithm = signatureAlgorithm;
        this.status = CertificateStatus.VALID; // 초기 상태는 VALID (검증 전)
        this.createdAt = LocalDateTime.now();
        this.uploadedToLdap = false;
    }

    // ========== Static Factory Methods ==========

    /**
     * 새로운 Certificate 생성 (Static Factory Method)
     *
     * @param uploadId 원본 업로드 파일 ID
     * @param x509Data X.509 인증서 데이터
     * @param subjectInfo 주체 정보
     * @param issuerInfo 발급자 정보
     * @param validity 유효기간
     * @param certificateType 인증서 타입
     * @param signatureAlgorithm 서명 알고리즘
     * @return Certificate
     * @throws IllegalArgumentException 필수 필드가 null인 경우
     */
    public static Certificate create(
            java.util.UUID uploadId,
            X509Data x509Data,
            SubjectInfo subjectInfo,
            IssuerInfo issuerInfo,
            ValidityPeriod validity,
            CertificateType certificateType,
            String signatureAlgorithm
    ) {
        if (uploadId == null) {
            throw new IllegalArgumentException("uploadId cannot be null");
        }
        if (x509Data == null) {
            throw new IllegalArgumentException("x509Data cannot be null");
        }
        if (subjectInfo == null) {
            throw new IllegalArgumentException("subjectInfo cannot be null");
        }
        if (issuerInfo == null) {
            throw new IllegalArgumentException("issuerInfo cannot be null");
        }
        if (validity == null) {
            throw new IllegalArgumentException("validity cannot be null");
        }
        if (certificateType == null) {
            throw new IllegalArgumentException("certificateType cannot be null");
        }
        if (signatureAlgorithm == null || signatureAlgorithm.trim().isEmpty()) {
            throw new IllegalArgumentException("signatureAlgorithm cannot be null or blank");
        }

        CertificateId id = CertificateId.newId();
        Certificate cert = new Certificate(
            id, uploadId, x509Data, subjectInfo, issuerInfo, validity, certificateType, signatureAlgorithm
        );

        // Domain Event 발행: 인증서 생성됨
        cert.addDomainEvent(new CertificateCreatedEvent(id));

        return cert;
    }

    // ========== Getters ==========

    @Override
    public CertificateId getId() {
        return id;
    }

    public X509Data getX509Data() {
        return x509Data;
    }

    public SubjectInfo getSubjectInfo() {
        return subjectInfo;
    }

    public IssuerInfo getIssuerInfo() {
        return issuerInfo;
    }

    public ValidityPeriod getValidity() {
        return validity;
    }

    public CertificateType getCertificateType() {
        return certificateType;
    }

    public CertificateStatus getStatus() {
        return status;
    }

    public String getSignatureAlgorithm() {
        return signatureAlgorithm;
    }

    public ValidationResult getValidationResult() {
        return validationResult;
    }

    public List<ValidationError> getValidationErrors() {
        return Collections.unmodifiableList(validationErrors);
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public java.util.UUID getUploadId() {
        return uploadId;
    }

    public Boolean isUploadedToLdap() {
        return uploadedToLdap != null && uploadedToLdap;
    }

    public LocalDateTime getUploadedToLdapAt() {
        return uploadedToLdapAt;
    }

    // ========== Business Logic Methods ==========

    /**
     * 인증서 검증 결과 기록
     *
     * @param validationResult 검증 결과
     * @throws IllegalArgumentException validationResult가 null인 경우
     */
    public void recordValidation(ValidationResult validationResult) {
        if (validationResult == null) {
            throw new IllegalArgumentException("validationResult cannot be null");
        }

        this.validationResult = validationResult;
        this.status = validationResult.getOverallStatus();
        this.updatedAt = LocalDateTime.now();

        // Domain Event 발행: 인증서 검증됨
        this.addDomainEvent(new CertificateValidatedEvent(id, validationResult.getOverallStatus()));
    }

    /**
     * 검증 오류 추가
     *
     * @param error 검증 오류
     * @throws IllegalArgumentException error가 null인 경우
     */
    public void addValidationError(ValidationError error) {
        if (error == null) {
            throw new IllegalArgumentException("error cannot be null");
        }
        if (!validationErrors.contains(error)) {
            validationErrors.add(error);
            this.updatedAt = LocalDateTime.now();
        }
    }

    /**
     * 검증 오류 추가 (여러 개)
     *
     * @param errors 검증 오류 목록
     */
    public void addValidationErrors(List<ValidationError> errors) {
        if (errors != null) {
            for (ValidationError error : errors) {
                addValidationError(error);
            }
        }
    }

    /**
     * 검증 오류 초기화
     *
     * <p>재검증 전에 이전 오류를 제거할 때 사용됩니다.</p>
     */
    public void clearValidationErrors() {
        validationErrors.clear();
        this.updatedAt = LocalDateTime.now();
    }

    /**
     * 유효한 인증서 여부
     *
     * @return 상태가 VALID이면 true
     */
    public boolean isValid() {
        return status == CertificateStatus.VALID;
    }

    /**
     * 만료된 인증서 여부
     *
     * @return 상태가 EXPIRED이면 true
     */
    public boolean isExpired() {
        return status == CertificateStatus.EXPIRED;
    }

    /**
     * 아직 유효하지 않은 인증서 여부
     *
     * @return 상태가 NOT_YET_VALID이면 true
     */
    public boolean isNotYetValid() {
        return status == CertificateStatus.NOT_YET_VALID;
    }

    /**
     * 폐기된 인증서 여부
     *
     * @return 상태가 REVOKED이면 true
     */
    public boolean isRevoked() {
        return status == CertificateStatus.REVOKED;
    }

    /**
     * CA 인증서 여부
     *
     * @return 발급자가 CA이면 true
     */
    public boolean isCA() {
        return issuerInfo != null && issuerInfo.isCA();
    }

    /**
     * Self-signed 인증서 여부
     *
     * @return 주체와 발급자가 동일하고 CA이면 true
     */
    public boolean isSelfSigned() {
        return issuerInfo != null && issuerInfo.isSelfSignedCA(subjectInfo.getDistinguishedName());
    }

    /**
     * 현재 시간에 유효한 인증서 여부
     *
     * @return notBefore ≤ now ≤ notAfter이면 true
     */
    public boolean isCurrentlyValid() {
        return validity != null && validity.isCurrentlyValid();
    }

    /**
     * 유효기간 만료까지 남은 일수
     *
     * @return 일수 (만료되었으면 음수)
     */
    public long daysUntilExpiration() {
        return validity != null ? validity.daysUntilExpiration() : 0;
    }

    /**
     * 조만간 만료 예정 여부
     *
     * @return 30일 이내 만료 예정이면 true
     */
    public boolean isExpiringSoon() {
        return validity != null && validity.isExpiringSoon();
    }

    /**
     * 조만간 만료 예정 여부 (임계값 지정)
     *
     * @param daysThreshold 경고 범위 (일)
     * @return 지정한 일수 이내 만료 예정이면 true
     */
    public boolean isExpiringSoon(int daysThreshold) {
        return validity != null && validity.isExpiringSoon(daysThreshold);
    }

    /**
     * LDAP에 업로드 표시
     *
     * <p>인증서가 LDAP 디렉토리에 저장되었음을 기록합니다.</p>
     */
    public void markAsUploadedToLdap() {
        this.uploadedToLdap = true;
        this.uploadedToLdapAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();

        // Domain Event 발행: LDAP에 업로드됨
        this.addDomainEvent(new CertificateUploadedToLdapEvent(id));
    }

    /**
     * 치명적 오류 존재 여부
     *
     * <p>검증 오류 중 심각도가 ERROR인 것이 있으면 true</p>
     *
     * @return 치명적 오류가 있으면 true
     */
    public boolean hasCriticalErrors() {
        return validationErrors.stream().anyMatch(ValidationError::isCritical);
    }

    /**
     * 경고만 있는 상태 여부
     *
     * <p>검증 오류가 모두 WARNING이면 true</p>
     *
     * @return 경고만 있으면 true
     */
    public boolean hasOnlyWarnings() {
        if (validationErrors.isEmpty()) {
            return false;
        }
        return validationErrors.stream().allMatch(ValidationError::isWarning);
    }

    /**
     * 완전한 인증서 정보 여부
     *
     * @return 모든 필드가 채워져 있으면 true
     */
    public boolean isComplete() {
        return id != null &&
               x509Data != null && x509Data.isComplete() &&
               subjectInfo != null && subjectInfo.isComplete() &&
               issuerInfo != null && issuerInfo.isComplete() &&
               validity != null &&
               certificateType != null &&
               signatureAlgorithm != null && !signatureAlgorithm.trim().isEmpty();
    }

    @Override
    public String toString() {
        return String.format(
            "Certificate[id=%s, subject=%s, issuer=%s, type=%s, status=%s, createdAt=%s]",
            id != null ? id.getId() : "null",
            subjectInfo != null ? subjectInfo.getCommonName() : "null",
            issuerInfo != null ? issuerInfo.getCommonName() : "null",
            certificateType,
            status,
            createdAt
        );
    }

    // ========== Test-Only Factory Methods ==========

    /**
     * 테스트용 Certificate 생성 (Static Factory Method)
     *
     * <p><b>⚠️ 이 메서드는 테스트 코드에서만 사용하세요!</b></p>
     *
     * <p>프로덕션 코드에서는 {@link #create(X509Data, SubjectInfo, IssuerInfo, ValidityPeriod, CertificateType, String)}
     * 메서드를 사용하세요.</p>
     *
     * @param id 인증서 ID (테스트용)
     * @param certificateType 인증서 타입
     * @param subjectInfo 주체 정보
     * @param issuerInfo 발급자 정보
     * @param validity 유효기간
     * @param x509Data X.509 데이터
     * @param status 초기 상태
     * @return Certificate (테스트용)
     */
    public static Certificate createForTest(
            CertificateId id,
            CertificateType certificateType,
            SubjectInfo subjectInfo,
            IssuerInfo issuerInfo,
            ValidityPeriod validity,
            X509Data x509Data,
            CertificateStatus status
    ) {
        Certificate cert = new Certificate();
        cert.id = id;
        cert.certificateType = certificateType;
        cert.subjectInfo = subjectInfo;
        cert.issuerInfo = issuerInfo;
        cert.validity = validity;
        cert.x509Data = x509Data;
        cert.status = status;
        cert.signatureAlgorithm = "SHA256WithRSA";
        cert.createdAt = LocalDateTime.now();
        cert.uploadedToLdap = false;
        return cert;
    }
}
