package com.smartcoreinc.localpkd.certificatevalidation.domain.model;

import com.smartcoreinc.localpkd.shared.domain.ValueObject;
import com.smartcoreinc.localpkd.shared.exception.DomainException;
import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.EqualsAndHashCode;
import lombok.Getter;

import java.util.regex.Pattern;

/**
 * IssuerName - CRL 발급자명 Value Object (ICAO DOC 9303)
 *
 * <p><b>✅ Phase 17 Fix: Trust Chain 검증 제외</b></p>
 * <p><b>목적</b>: ICAO PKD의 인증서 체인 검증은 파일 업로드 순서에 독립적이어야 합니다.
 * 따라서 CRL 발급자명의 유효성만 검증하고 Trust Chain 검증은 별도 모듈 (Phase 18+)에서 수행합니다.</p>
 *
 * <p><b>DDD Value Object Pattern</b>:</p>
 * <ul>
 *   <li>Immutability: 생성 후 변경 불가</li>
 *   <li>Self-validation: 생성 시 형식 및 비즈니스 규칙 검증</li>
 *   <li>Value equality: 값 기반 동등성 판단</li>
 * </ul>
 *
 * <p><b>비즈니스 규칙 (유효성만 검증)</b>:</p>
 * <ul>
 *   <li>null 또는 blank 문자열 제외</li>
 *   <li>1-255자 범위 (DN 표준)</li>
 *   <li>일반적인 문자만 허용 (alphanumeric, space, hyphen, underscore)</li>
 *   <li><b>❌ CSCA-XX 형식 강제 제거</b> (Trust Chain은 별도)</li>
 * </ul>
 *
 * <p><b>ICAO DOC 9303 인증서 유형</b>:</p>
 * <ul>
 *   <li>CSCA: Country Signing CA</li>
 *   <li>DSC: Document Signer Certificate</li>
 *   <li>CRL: Certificate Revocation List</li>
 *   <li>NON-CONFORMANT: 규격 미준수</li>
 * </ul>
 *
 * <p><b>사용 예시</b>:</p>
 * <pre>{@code
 * // 모두 유효한 IssuerName
 * IssuerName issuer1 = IssuerName.of("csca-canada");           // ✓
 * IssuerName issuer2 = IssuerName.of("CSCA-QA");              // ✓
 * IssuerName issuer3 = IssuerName.of("ePassport CSCA 07");    // ✓
 * IssuerName issuer4 = IssuerName.of("Singapore Passport CA 6"); // ✓
 *
 * // 검증 실패
 * IssuerName.of(null);         // ❌ DomainException: cannot be null
 * IssuerName.of("");          // ❌ DomainException: cannot be blank
 * IssuerName.of("A".repeat(256));  // ❌ DomainException: exceeds 255 chars
 * }</pre>
 *
 * <p><b>Trust Chain 검증</b>: Phase 18+ 별도 모듈에서 수행
 * <ul>
 *   <li>CSCA 계층 구축</li>
 *   <li>DSC → CSCA 검증</li>
 *   <li>CRL Signature 검증</li>
 *   <li>PA (Public Authority) 통합</li>
 * </ul>
 * </p>
 *
 * @see CertificateRevocationList
 * @author SmartCore Inc.
 * @version 2.0 (Phase 17 Update)
 * @since 2025-10-24, Updated 2025-11-14
 */
@Embeddable
@Getter
@EqualsAndHashCode
public class IssuerName implements ValueObject {

    /**
     * CRL 발급자명 (예: csca-canada, ePassport CSCA 07, Singapore Passport CA 6)
     *
     * <p><b>✅ Phase 17 Fix</b>: 유효성만 검증, CSCA-XX 형식 제약 제거</p>
     */
    @Column(name = "issuer_name", length = 255, nullable = false)
    private String value;

    /**
     * 정규식 패턴: 유효한 발급자명 형식
     *
     * <p><b>허용</b>:
     * <ul>
     *   <li>영문자 (대소문자)</li>
     *   <li>숫자</li>
     *   <li>공백</li>
     *   <li>하이픈 (-), 언더스코어 (_)</li>
     * </ul>
     * </p>
     *
     * <p><b>제외</b>:
     * <ul>
     *   <li>특수문자 (DN 구분자 쉼표 등)</li>
     *   <li>CSCA-XX 형식 강제 (Trust Chain 검증은 Phase 18+)</li>
     * </ul>
     * </p>
     */
    private static final Pattern ISSUER_NAME_PATTERN = Pattern.compile("^[A-Za-z0-9 _\\-]+$");

    /**
     * JPA용 기본 생성자 (protected)
     */
    protected IssuerName() {
    }

    /**
     * IssuerName 생성 (Static Factory Method)
     *
     * <p><b>✅ Phase 17 Fix</b>: 유효성만 검증, Trust Chain은 Phase 18+</p>
     *
     * @param value CRL 발급자명 (예: csca-canada, ePassport CSCA 07, Singapore Passport CA 6)
     * @return IssuerName
     * @throws DomainException 다음 경우에 발생:
     *         <ul>
     *           <li>null 또는 blank 문자열</li>
     *           <li>255자 초과</li>
     *           <li>유효하지 않은 문자 포함 (DN 구분자 등)</li>
     *         </ul>
     */
    public static IssuerName of(String value) {
        if (value == null || value.isBlank()) {
            throw new DomainException(
                "INVALID_ISSUER_NAME",
                "Issuer name cannot be null or blank"
            );
        }

        String normalized = value.trim();

        // 길이 검증 (DN 표준: 최대 255자)
        if (normalized.length() > 255) {
            throw new DomainException(
                "ISSUER_NAME_TOO_LONG",
                "Issuer name must not exceed 255 characters. Got: " + normalized.length()
            );
        }

        // 형식 검증: 유효한 문자만 허용 (alphanumeric, space, hyphen, underscore)
        // ❌ CSCA-XX 형식 제약 제거 (유효성만 검증, Trust Chain은 Phase 18+)
        if (!ISSUER_NAME_PATTERN.matcher(normalized).matches()) {
            throw new DomainException(
                "INVALID_ISSUER_NAME_FORMAT",
                "Issuer name contains invalid characters. " +
                "Only alphanumeric, space, hyphen, and underscore are allowed. Got: " + value
            );
        }

        IssuerName issuerName = new IssuerName();
        issuerName.value = normalized;  // 원본 형식 유지 (대소문자 구분)
        return issuerName;
    }

    /**
     * 국가 코드 추출 (이제 사용되지 않음 - IssuerName에서는 국가 정보 미포함)
     *
     * <p><b>⚠️ Deprecated</b>: IssuerName에서는 CN 값만 저장합니다.
     * 국가 코드는 CountryCode Value Object에서 별도로 관리됩니다 (C= RDN에서 추출).</p>
     *
     * @return 빈 문자열 (국가 정보 미포함)
     * @deprecated 국가 코드는 CountryCode Value Object 사용
     */
    @Deprecated(since = "Phase 17", forRemoval = true)
    public String getCountryCode() {
        // IssuerName은 CN 값만 저장하므로 국가 코드 미포함
        // 국가 코드는 CountryCode Value Object에서 관리
        return "";
    }

    /**
     * 특정 국가 발급자 여부 (이제 사용되지 않음)
     *
     * <p><b>⚠️ Deprecated</b>: IssuerName에는 국가 정보가 없으므로 항상 false를 반환합니다.
     * 국가 검증은 CountryCode Value Object를 사용하세요.</p>
     *
     * @param countryCode 국가 코드 (대소문자 무시)
     * @return 항상 false (국가 정보 미포함)
     * @deprecated 국가 검증은 CountryCode Value Object 사용
     */
    @Deprecated(since = "Phase 17", forRemoval = true)
    public boolean isCountry(String countryCode) {
        // IssuerName은 CN 값만 저장하므로 국가 검증 불가
        return false;
    }

    /**
     * CSCA 접두사 확인 (이제 사용되지 않음)
     *
     * <p><b>⚠️ Deprecated</b>: Phase 17에서 CSCA-XX 형식 검증이 제거되었습니다.
     * IssuerName은 형식과 무관하게 모든 유효한 이름을 받아들입니다.
     * Trust Chain 검증은 Phase 18+ 별도 모듈에서 수행됩니다.</p>
     *
     * @return 항상 false (CSCA-XX 형식 강제 제거)
     * @deprecated CSCA 검증은 Phase 18+ 별도 모듈에서 수행
     */
    @Deprecated(since = "Phase 17", forRemoval = true)
    public boolean isCSCA() {
        // CSCA-XX 형식 검증이 제거됨
        // Trust Chain 검증은 Phase 18+에서 수행
        return false;
    }

    /**
     * 값 반환
     *
     * @return CSCA 발급자명
     */
    public String getValue() {
        return value;
    }

    /**
     * 문자열 표현
     *
     * @return CSCA 발급자명
     */
    @Override
    public String toString() {
        return value;
    }
}
