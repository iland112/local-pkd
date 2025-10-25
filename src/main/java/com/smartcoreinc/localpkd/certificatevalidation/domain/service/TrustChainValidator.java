package com.smartcoreinc.localpkd.certificatevalidation.domain.service;

import com.smartcoreinc.localpkd.certificatevalidation.domain.model.Certificate;
import com.smartcoreinc.localpkd.certificatevalidation.domain.model.TrustPath;
import com.smartcoreinc.localpkd.certificatevalidation.domain.model.ValidationResult;

/**
 * TrustChainValidator - Trust Chain 검증 Domain Service
 *
 * <p>ICAO PKD 인증서 계층 구조에서 Trust Chain을 검증합니다.
 * CSCA (Root of Trust) → DSC (Intermediate) → DS (Leaf) 경로를 검증합니다.</p>
 *
 * <h3>검증 단계</h3>
 * <ol>
 *   <li>CSCA 검증: Self-Signed, CA 플래그, KeyUsage, Signature</li>
 *   <li>DSC 검증: Issuer 확인, Signature 검증, Validity, KeyUsage, CRL</li>
 *   <li>DS 검증 (선택): Issuer 확인, Signature 검증, Validity</li>
 * </ol>
 *
 * <h3>사용 예시</h3>
 * <pre>{@code
 * TrustChainValidator validator = new TrustChainValidatorImpl(certificateRepository, crlRepository);
 * TrustPath path = TrustPath.ofThree(cscaId, dscId, dsId);
 * ValidationResult result = validator.validate(path);
 *
 * if (result.isValid()) {
 *     log.info("Trust chain validated successfully");
 * } else {
 *     log.error("Trust chain validation failed: {}", result.getSummary());
 * }
 * }</pre>
 *
 * @author SmartCore Inc.
 * @version 1.0
 * @since 2025-10-24
 */
public interface TrustChainValidator {

    /**
     * Trust Path 전체 검증
     *
     * <p>주어진 Trust Path의 모든 인증서를 순차적으로 검증합니다.
     * Root (CSCA) → Intermediate (DSC) → Leaf (DS) 순서로 검증합니다.</p>
     *
     * @param path Trust Path (CSCA → DSC → DS)
     * @return ValidationResult (성공/실패, 오류 목록)
     */
    ValidationResult validate(TrustPath path);

    /**
     * 단일 인증서 검증 (Trust Path 없이)
     *
     * <p>단일 인증서의 기본 속성만 검증합니다 (Validity, KeyUsage, BasicConstraints).
     * Trust Chain 검증은 수행하지 않습니다.</p>
     *
     * @param certificate 검증 대상 인증서
     * @return ValidationResult (성공/실패, 오류 목록)
     */
    ValidationResult validateSingle(Certificate certificate);

    /**
     * CSCA (Root of Trust) 검증
     *
     * <p>CSCA 인증서가 Root of Trust로서 유효한지 검증합니다.</p>
     *
     * <h4>검증 항목</h4>
     * <ul>
     *   <li>Self-Signed 확인 (Subject == Issuer)</li>
     *   <li>CA 플래그 확인 (BasicConstraints: CA=true)</li>
     *   <li>KeyUsage 확인 (keyCertSign, cRLSign)</li>
     *   <li>Signature 자기 검증</li>
     *   <li>유효기간 확인</li>
     * </ul>
     *
     * @param csca CSCA 인증서
     * @return ValidationResult (성공/실패, 오류 목록)
     */
    ValidationResult validateCsca(Certificate csca);

    /**
     * DSC (Document Signer Certificate) 검증
     *
     * <p>DSC 인증서가 주어진 CSCA에 의해 발급되었으며 유효한지 검증합니다.</p>
     *
     * <h4>검증 항목</h4>
     * <ul>
     *   <li>Issuer 확인 (Issuer DN == CSCA Subject DN)</li>
     *   <li>Signature 검증 (CSCA Public Key 사용)</li>
     *   <li>유효기간 확인</li>
     *   <li>KeyUsage 확인 (digitalSignature)</li>
     *   <li>CRL 확인 (폐기 여부)</li>
     * </ul>
     *
     * @param dsc DSC 인증서
     * @param csca Issuer CSCA 인증서
     * @return ValidationResult (성공/실패, 오류 목록)
     */
    ValidationResult validateDsc(Certificate dsc, Certificate csca);

    /**
     * 2개 인증서 간 Issuer-Subject 관계 검증
     *
     * <p>Child 인증서가 Parent 인증서에 의해 발급되었는지 검증합니다.</p>
     *
     * <h4>검증 항목</h4>
     * <ul>
     *   <li>Issuer DN 일치 확인</li>
     *   <li>Signature 검증 (Parent Public Key 사용)</li>
     * </ul>
     *
     * @param child Child 인증서 (DSC or DS)
     * @param parent Parent 인증서 (CSCA or DSC)
     * @return ValidationResult (성공/실패, 오류 목록)
     */
    ValidationResult validateIssuerRelationship(Certificate child, Certificate parent);
}
