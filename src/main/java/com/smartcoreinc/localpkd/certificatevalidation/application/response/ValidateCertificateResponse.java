package com.smartcoreinc.localpkd.certificatevalidation.application.response;

import lombok.Builder;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * ValidateCertificateResponse - 인증서 검증 응답 DTO
 *
 * <p><b>용도</b>:</p>
 * <ul>
 *   <li>인증서 검증 결과를 Presentation Layer로 전달</li>
 *   <li>검증 성공/실패 여부 및 상세 정보 제공</li>
 *   <li>검증 오류 목록 제공</li>
 * </ul>
 *
 * <p><b>검증 결과 해석</b>:</p>
 * <ul>
 *   <li>success = true: 모든 검증 통과</li>
 *   <li>success = false: 하나 이상의 검증 실패 (validationErrors 참조)</li>
 *   <li>overallStatus = VALID: 모든 검증 통과</li>
 *   <li>overallStatus = INVALID: 치명적 오류로 인한 검증 실패</li>
 * </ul>
 *
 * <p><b>사용 예시</b>:</p>
 * <pre>{@code
 * ValidateCertificateResponse response = validateCertificateUseCase.execute(command);
 *
 * if (response.success()) {
 *     log.info("Certificate validation passed");
 * } else {
 *     log.error("Certificate validation failed: {}", response.message());
 *     response.validationErrors().forEach(error ->
 *         log.error("- {}: {}", error.errorCode(), error.errorMessage())
 *     );
 * }
 * }</pre>
 *
 * @author SmartCore Inc.
 * @version 1.0
 * @since 2025-10-24
 */
@Builder
public record ValidateCertificateResponse(
    /**
     * 검증 성공 여부
     * <p>true: 모든 검증 통과, false: 하나 이상 검증 실패</p>
     */
    boolean success,

    /**
     * 응답 메시지
     * <p>성공 시: "인증서 검증이 완료되었습니다."</p>
     * <p>실패 시: "인증서 검증에 실패했습니다: ..."</p>
     */
    String message,

    /**
     * 인증서 ID
     */
    UUID certificateId,

    /**
     * 인증서 주체 DN (Subject Distinguished Name)
     */
    String subjectDn,

    /**
     * 인증서 발급자 DN (Issuer Distinguished Name)
     */
    String issuerDn,

    /**
     * 인증서 일련번호
     */
    String serialNumber,

    /**
     * 인증서 지문 (SHA-256)
     */
    String fingerprint,

    /**
     * 전체 검증 상태
     * <p>VALID, INVALID, EXPIRED, NOT_YET_VALID, REVOKED 중 하나</p>
     */
    String overallStatus,

    /**
     * 서명 검증 결과
     * <p>true: 서명 유효, false: 서명 무효, null: 검증 미수행</p>
     */
    Boolean signatureValid,

    /**
     * Trust Chain 검증 결과
     * <p>true: 체인 유효, false: 체인 무효, null: 검증 미수행</p>
     */
    Boolean chainValid,

    /**
     * 폐기 여부 확인 결과
     * <p>true: 폐기되지 않음, false: 폐기됨, null: 확인 미수행</p>
     */
    Boolean notRevoked,

    /**
     * 유효기간 검증 결과
     * <p>true: 유효기간 내, false: 유효기간 외, null: 검증 미수행</p>
     */
    Boolean validityValid,

    /**
     * 제약사항 검증 결과
     * <p>true: 제약사항 준수, false: 제약사항 위반, null: 검증 미수행</p>
     */
    Boolean constraintsValid,

    /**
     * 검증 수행 시간
     */
    LocalDateTime validatedAt,

    /**
     * 검증 소요 시간 (밀리초)
     */
    Long durationMillis,

    /**
     * 검증 오류 목록
     * <p>검증 실패 시 오류 상세 정보를 제공합니다.</p>
     */
    List<ValidationErrorDto> validationErrors
) {

    /**
     * ValidationErrorDto - 검증 오류 상세 정보
     */
    @Builder
    public record ValidationErrorDto(
        String errorCode,
        String errorMessage,
        String severity,
        LocalDateTime occurredAt
    ) {}

    /**
     * 성공 응답 생성
     *
     * @param certificateId 인증서 ID
     * @param subjectDn 인증서 주체 DN
     * @param issuerDn 인증서 발급자 DN
     * @param serialNumber 인증서 일련번호
     * @param fingerprint 인증서 지문
     * @param signatureValid 서명 검증 결과
     * @param chainValid Trust Chain 검증 결과
     * @param notRevoked 폐기 여부 확인 결과
     * @param validityValid 유효기간 검증 결과
     * @param constraintsValid 제약사항 검증 결과
     * @param validatedAt 검증 수행 시간
     * @param durationMillis 검증 소요 시간
     * @return ValidateCertificateResponse
     */
    public static ValidateCertificateResponse success(
        UUID certificateId, String subjectDn, String issuerDn,
        String serialNumber, String fingerprint,
        Boolean signatureValid, Boolean chainValid, Boolean notRevoked,
        Boolean validityValid, Boolean constraintsValid,
        LocalDateTime validatedAt, Long durationMillis
    ) {
        return ValidateCertificateResponse.builder()
            .success(true)
            .message("인증서 검증이 완료되었습니다.")
            .certificateId(certificateId)
            .subjectDn(subjectDn)
            .issuerDn(issuerDn)
            .serialNumber(serialNumber)
            .fingerprint(fingerprint)
            .overallStatus("VALID")
            .signatureValid(signatureValid)
            .chainValid(chainValid)
            .notRevoked(notRevoked)
            .validityValid(validityValid)
            .constraintsValid(constraintsValid)
            .validatedAt(validatedAt)
            .durationMillis(durationMillis)
            .validationErrors(List.of())
            .build();
    }

    /**
     * 실패 응답 생성
     *
     * @param certificateId 인증서 ID
     * @param subjectDn 인증서 주체 DN
     * @param issuerDn 인증서 발급자 DN
     * @param serialNumber 인증서 일련번호
     * @param fingerprint 인증서 지문
     * @param overallStatus 전체 검증 상태
     * @param signatureValid 서명 검증 결과
     * @param chainValid Trust Chain 검증 결과
     * @param notRevoked 폐기 여부 확인 결과
     * @param validityValid 유효기간 검증 결과
     * @param constraintsValid 제약사항 검증 결과
     * @param validatedAt 검증 수행 시간
     * @param durationMillis 검증 소요 시간
     * @param validationErrors 검증 오류 목록
     * @return ValidateCertificateResponse
     */
    public static ValidateCertificateResponse failure(
        UUID certificateId, String subjectDn, String issuerDn,
        String serialNumber, String fingerprint, String overallStatus,
        Boolean signatureValid, Boolean chainValid, Boolean notRevoked,
        Boolean validityValid, Boolean constraintsValid,
        LocalDateTime validatedAt, Long durationMillis,
        List<ValidationErrorDto> validationErrors
    ) {
        String errorMessage = validationErrors.isEmpty() ?
            "인증서 검증에 실패했습니다." :
            "인증서 검증에 실패했습니다: " + validationErrors.get(0).errorMessage();

        return ValidateCertificateResponse.builder()
            .success(false)
            .message(errorMessage)
            .certificateId(certificateId)
            .subjectDn(subjectDn)
            .issuerDn(issuerDn)
            .serialNumber(serialNumber)
            .fingerprint(fingerprint)
            .overallStatus(overallStatus)
            .signatureValid(signatureValid)
            .chainValid(chainValid)
            .notRevoked(notRevoked)
            .validityValid(validityValid)
            .constraintsValid(constraintsValid)
            .validatedAt(validatedAt)
            .durationMillis(durationMillis)
            .validationErrors(validationErrors)
            .build();
    }
}
