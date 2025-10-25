package com.smartcoreinc.localpkd.certificatevalidation.application.command;

import lombok.Builder;

/**
 * ValidateCertificateCommand - 인증서 검증 명령 (CQRS Write Side)
 *
 * <p><b>용도</b>:</p>
 * <ul>
 *   <li>X.509 인증서 검증 요청</li>
 *   <li>서명, 유효기간, 제약사항 검증</li>
 *   <li>검증 결과를 Certificate Aggregate에 저장</li>
 * </ul>
 *
 * <p><b>검증 항목</b>:</p>
 * <ul>
 *   <li>서명 검증 (Signature Validation)</li>
 *   <li>유효기간 검증 (Validity Period)</li>
 *   <li>제약사항 검증 (Basic Constraints, Key Usage 등)</li>
 *   <li>폐기 여부 확인 (CRL Check) - Optional</li>
 * </ul>
 *
 * <p><b>사용 예시</b>:</p>
 * <pre>{@code
 * ValidateCertificateCommand command = ValidateCertificateCommand.builder()
 *     .certificateId(certId)
 *     .validateSignature(true)
 *     .validateChain(false)  // Trust Chain은 별도 명령으로 검증
 *     .checkRevocation(false)  // CRL 확인은 비활성화
 *     .build();
 *
 * ValidateCertificateResponse response = validateCertificateUseCase.execute(command);
 * }</pre>
 *
 * @see com.smartcoreinc.localpkd.certificatevalidation.application.usecase.ValidateCertificateUseCase
 * @see com.smartcoreinc.localpkd.certificatevalidation.domain.model.Certificate
 * @author SmartCore Inc.
 * @version 1.0
 * @since 2025-10-24
 */
@Builder
public record ValidateCertificateCommand(
    /**
     * 검증할 인증서의 ID
     */
    String certificateId,

    /**
     * 서명 검증 여부
     * <p>인증서의 디지털 서명을 검증합니다.</p>
     */
    boolean validateSignature,

    /**
     * Trust Chain 검증 여부
     * <p>인증서 체인 전체를 검증합니다 (CSCA → DSC).</p>
     */
    boolean validateChain,

    /**
     * 폐기 여부 확인 (CRL Check)
     * <p>인증서가 폐기 목록(CRL)에 포함되어 있는지 확인합니다.</p>
     */
    boolean checkRevocation,

    /**
     * 유효기간 검증 여부
     * <p>현재 시간이 인증서의 유효기간 내에 있는지 확인합니다.</p>
     */
    boolean validateValidity,

    /**
     * 제약사항 검증 여부
     * <p>Basic Constraints, Key Usage, Extended Key Usage 등을 검증합니다.</p>
     */
    boolean validateConstraints
) {

    /**
     * 기본 검증 옵션으로 Command 생성
     *
     * <p>기본값:</p>
     * <ul>
     *   <li>validateSignature: true</li>
     *   <li>validateChain: false</li>
     *   <li>checkRevocation: false</li>
     *   <li>validateValidity: true</li>
     *   <li>validateConstraints: true</li>
     * </ul>
     *
     * @param certificateId 검증할 인증서의 ID
     * @return ValidateCertificateCommand
     */
    public static ValidateCertificateCommand withDefaults(String certificateId) {
        return ValidateCertificateCommand.builder()
            .certificateId(certificateId)
            .validateSignature(true)
            .validateChain(false)
            .checkRevocation(false)
            .validateValidity(true)
            .validateConstraints(true)
            .build();
    }

    /**
     * Command 검증
     *
     * <p>필수 필드 및 비즈니스 규칙을 검증합니다.</p>
     *
     * @throws IllegalArgumentException 검증 실패 시
     */
    public void validate() {
        if (certificateId == null || certificateId.isBlank()) {
            throw new IllegalArgumentException("certificateId must not be blank");
        }

        // 최소 하나의 검증 옵션이 활성화되어야 함
        if (!validateSignature && !validateChain && !checkRevocation &&
            !validateValidity && !validateConstraints) {
            throw new IllegalArgumentException(
                "At least one validation option must be enabled");
        }
    }
}
