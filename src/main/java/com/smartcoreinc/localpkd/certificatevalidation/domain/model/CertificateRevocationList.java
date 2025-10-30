package com.smartcoreinc.localpkd.certificatevalidation.domain.model;

import com.smartcoreinc.localpkd.shared.domain.AggregateRoot;
import com.smartcoreinc.localpkd.shared.exception.DomainException;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.Objects;

/**
 * CertificateRevocationList - X.509 CRL (Certificate Revocation List) Aggregate Root
 *
 * <p><b>DDD Aggregate Root Pattern</b>:</p>
 * <ul>
 *   <li>도메인 이벤트 발행 및 관리</li>
 *   <li>비즈니스 규칙 검증</li>
 *   <li>CRL 데이터의 일관성 경계</li>
 * </ul>
 *
 * <p><b>책임</b>:</p>
 * <ul>
 *   <li>특정 CSCA(Country Signing CA)의 CRL 관리</li>
 *   <li>폐기된 인증서 목록 유지</li>
 *   <li>CRL 유효성 기간 검증</li>
 *   <li>인증서 폐기 여부 확인</li>
 * </ul>
 *
 * <p><b>라이프사이클</b>:</p>
 * <pre>
 * LDIF 파싱
 *   │
 *   ├─ cRLDistributionPoint 엔트리 감지
 *   ├─ certificateRevocationList;binary 필드 추출
 *   ├─ Base64 디코딩
 *   ├─ X.509 CRL 파싱
 *   │
 *   ▼
 * CertificateRevocationList.create()
 *   │
 *   ├─ IssuerName: CSCA-QA
 *   ├─ CountryCode: QA
 *   ├─ ValidityPeriod: thisUpdate, nextUpdate
 *   ├─ RevokedCertificates: 폐기 목록
 *   │
 *   ▼
 * Repository.save()
 *   │
 *   ├─ 데이터베이스 저장
 *   ├─ Domain Events 발행
 *   │
 *   ▼
 * Certificate.checkRevocation()
 *   │
 *   ├─ 폐기 여부 조회
 *   └─ Validation 결과 반환
 * </pre>
 *
 * <p><b>사용 예시</b>:</p>
 * <pre>{@code
 * // CRL 생성 (LDIF 파싱 후)
 * CertificateRevocationList crl = CertificateRevocationList.create(
 *     CrlId.newId(),
 *     IssuerName.of("CSCA-QA"),
 *     CountryCode.of("QA"),
 *     ValidityPeriod.of(thisUpdate, nextUpdate),
 *     X509CrlData.of(crlBinary, revokedCount),
 *     RevokedCertificates.of(revokedSerialNumbers)
 * );
 *
 * // 인증서 폐기 여부 확인
 * boolean isRevoked = crl.isRevoked("01234567890ABCDEF");
 *
 * // CRL 만료 확인
 * boolean isExpired = crl.isExpired();
 *
 * // 통계
 * int revokedCount = crl.getRevokedCount();
 * }</pre>
 *
 * @see CrlId
 * @see IssuerName
 * @see CountryCode
 * @see ValidityPeriod
 * @see X509CrlData
 * @see RevokedCertificates
 * @author SmartCore Inc.
 * @version 1.0
 * @since 2025-10-24
 */
@Entity
@Table(name = "certificate_revocation_list", indexes = {
    @Index(name = "idx_crl_issuer_country", columnList = "issuer_name,country_code"),
    @Index(name = "idx_crl_issuer", columnList = "issuer_name"),
    @Index(name = "idx_crl_country", columnList = "country_code"),
    @Index(name = "idx_crl_not_after", columnList = "not_after DESC")
})
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CertificateRevocationList extends AggregateRoot<CrlId> {

    /**
     * CRL ID (Primary Key)
     */
    @EmbeddedId
    private CrlId id;

    /**
     * CSCA 발급자명 (예: CSCA-QA, CSCA-NZ)
     */
    @Embedded
    @AttributeOverride(name = "value", column = @Column(name = "issuer_name"))
    private IssuerName issuerName;

    /**
     * 국가 코드 (ISO 3166-1 alpha-2)
     */
    @Embedded
    @AttributeOverride(name = "value", column = @Column(name = "country_code"))
    private CountryCode countryCode;

    /**
     * CRL 유효성 기간 (thisUpdate, nextUpdate)
     */
    @Embedded
    private ValidityPeriod validityPeriod;

    /**
     * X.509 CRL 바이너리 데이터
     */
    @Embedded
    private X509CrlData x509CrlData;

    /**
     * 폐기된 인증서 일련번호 집합
     */
    @Embedded
    private RevokedCertificates revokedCertificates;

    /**
     * 원본 업로드 파일 ID (File Upload Context)
     *
     * <p>이 CRL이 추출된 원본 LDIF/ML 파일의 uploadId입니다.</p>
     * <p>Cross-Context Reference: UploadedFile Aggregate와 연결</p>
     * <p>Phase 17: ValidateCertificatesUseCase에서 사용됩니다.</p>
     *
     * @since Phase 17 Task 1.2
     */
    @Column(name = "upload_id", nullable = false)
    private java.util.UUID uploadId;

    /**
     * CRL 저장 일시
     */
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    /**
     * CRL 마지막 수정 일시
     */
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    /**
     * CertificateRevocationList Aggregate Root 생성 (Static Factory Method)
     *
     * <p>LDIF 파싱 후 CRL 데이터를 Aggregate Root로 변환합니다.</p>
     *
     * @param uploadId 원본 업로드 파일 ID (File Upload Context)
     * @param id CRL ID
     * @param issuerName CSCA 발급자명 (예: CSCA-QA)
     * @param countryCode 국가 코드 (QA)
     * @param validityPeriod CRL 유효성 기간
     * @param x509CrlData X.509 CRL 바이너리 데이터
     * @param revokedCertificates 폐기된 인증서 목록
     * @return 새로운 CertificateRevocationList Aggregate
     * @throws DomainException 입력 값이 유효하지 않은 경우
     */
    public static CertificateRevocationList create(
            java.util.UUID uploadId,
            CrlId id,
            IssuerName issuerName,
            CountryCode countryCode,
            ValidityPeriod validityPeriod,
            X509CrlData x509CrlData,
            RevokedCertificates revokedCertificates
    ) {
        // 입력 값 검증
        Objects.requireNonNull(uploadId, "Upload ID cannot be null");
        Objects.requireNonNull(id, "CRL ID cannot be null");
        Objects.requireNonNull(issuerName, "Issuer name cannot be null");
        Objects.requireNonNull(countryCode, "Country code cannot be null");
        Objects.requireNonNull(validityPeriod, "Validity period cannot be null");
        Objects.requireNonNull(x509CrlData, "X509 CRL data cannot be null");
        Objects.requireNonNull(revokedCertificates, "Revoked certificates cannot be null");

        // Issuer name과 Country code 일치 검증
        if (!issuerName.isCountry(countryCode.getValue())) {
            throw new DomainException(
                "ISSUER_COUNTRY_MISMATCH",
                String.format("Issuer country (%s) does not match Country code (%s)",
                    issuerName.getCountryCode(), countryCode.getValue())
            );
        }

        // CRL Aggregate 생성
        CertificateRevocationList crl = new CertificateRevocationList();
        crl.id = id;
        crl.uploadId = uploadId;
        crl.issuerName = issuerName;
        crl.countryCode = countryCode;
        crl.validityPeriod = validityPeriod;
        crl.x509CrlData = x509CrlData;
        crl.revokedCertificates = revokedCertificates;
        crl.createdAt = LocalDateTime.now();
        crl.updatedAt = LocalDateTime.now();

        // TODO: Domain Event 발행
        // crl.registerEvent(new CrlCreatedEvent(id, issuerName, countryCode));

        return crl;
    }

    /**
     * 특정 인증서가 폐기되었는지 확인
     *
     * <p>일련번호를 기반으로 CRL 폐기 목록을 검사합니다.</p>
     *
     * @param serialNumber 확인할 인증서 일련번호 (16진수)
     * @return 폐기되었으면 true
     * @throws DomainException 일련번호가 null이거나 유효하지 않은 경우
     */
    public boolean isRevoked(String serialNumber) {
        if (serialNumber == null || serialNumber.isBlank()) {
            throw new DomainException(
                "INVALID_SERIAL_NUMBER",
                "Serial number cannot be null or blank"
            );
        }

        return revokedCertificates.contains(serialNumber);
    }

    /**
     * CRL이 만료되었는지 확인
     *
     * <p>현재 시간이 nextUpdate를 지났는지 확인합니다.</p>
     *
     * @return CRL이 만료되었으면 true
     */
    public boolean isExpired() {
        if (validityPeriod == null) {
            return true;
        }
        return validityPeriod.isExpired();
    }

    /**
     * CRL이 유효한지 확인
     *
     * <p>현재 시간이 thisUpdate와 nextUpdate 사이인지 확인합니다.</p>
     *
     * @return CRL이 유효하면 true
     */
    public boolean isValid() {
        if (validityPeriod == null) {
            return false;
        }
        return validityPeriod.isCurrentlyValid();
    }

    /**
     * CRL이 아직 발행되지 않았는지 확인
     *
     * <p>현재 시간이 thisUpdate 이전인지 확인합니다.</p>
     *
     * @return CRL이 아직 발행되지 않았으면 true
     */
    public boolean isNotYetValid() {
        if (validityPeriod == null) {
            return true;
        }
        return validityPeriod.isNotYetValid();
    }

    /**
     * 폐기된 인증서 개수
     *
     * @return 폐기된 인증서 수
     */
    public int getRevokedCount() {
        return revokedCertificates.getCount();
    }

    /**
     * CRL 바이너리 데이터 크기
     *
     * @return 바이트 단위 크기
     */
    public int getSize() {
        return x509CrlData.getSize();
    }

    /**
     * CRL 바이너리 데이터 (읽기 전용)
     *
     * @return DER-encoded CRL 바이너리 데이터
     */
    public byte[] getCrlBinary() {
        return x509CrlData.getCrlBinary();
    }

    /**
     * CSCA 발급자 확인
     *
     * @param issuerName 확인할 CSCA 발급자명
     * @return 일치하면 true
     */
    public boolean isIssuedBy(IssuerName issuerName) {
        if (issuerName == null) {
            return false;
        }
        return this.issuerName.equals(issuerName);
    }

    /**
     * 국가 확인
     *
     * @param countryCode 확인할 국가 코드
     * @return 일치하면 true
     */
    public boolean isFromCountry(CountryCode countryCode) {
        if (countryCode == null) {
            return false;
        }
        return this.countryCode.equals(countryCode);
    }

    /**
     * 원본 업로드 파일 ID 조회
     *
     * <p>이 CRL이 추출된 원본 LDIF/ML 파일의 uploadId를 반환합니다.</p>
     * <p>Phase 17: ValidateCertificatesUseCase에서 사용됩니다.</p>
     *
     * @return 원본 업로드 파일 ID (File Upload Context)
     * @since Phase 17 Task 1.2
     */
    public java.util.UUID getUploadId() {
        return uploadId;
    }

    /**
     * 문자열 표현
     *
     * @return CRL 정보 문자열
     */
    @Override
    public String toString() {
        return String.format("CRL[id=%s, issuer=%s, country=%s, revoked=%d, valid=%s]",
            id, issuerName, countryCode, getRevokedCount(), isValid());
    }
}
