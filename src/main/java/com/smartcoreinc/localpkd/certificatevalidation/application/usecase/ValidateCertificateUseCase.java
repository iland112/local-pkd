package com.smartcoreinc.localpkd.certificatevalidation.application.usecase;

import com.smartcoreinc.localpkd.certificatevalidation.application.command.ValidateCertificateCommand;
import com.smartcoreinc.localpkd.certificatevalidation.application.response.ValidateCertificateResponse;
import com.smartcoreinc.localpkd.certificatevalidation.application.response.ValidateCertificateResponse.ValidationErrorDto;
import com.smartcoreinc.localpkd.certificatevalidation.domain.model.Certificate;
import com.smartcoreinc.localpkd.certificatevalidation.domain.model.CertificateId;
import com.smartcoreinc.localpkd.certificatevalidation.domain.model.CertificateStatus;
import com.smartcoreinc.localpkd.certificatevalidation.domain.model.ValidationResult;
import com.smartcoreinc.localpkd.certificatevalidation.domain.repository.CertificateRepository;
import com.smartcoreinc.localpkd.shared.exception.DomainException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * ValidateCertificateUseCase - 인증서 검증 Use Case
 *
 * <p><b>책임</b>:</p>
 * <ul>
 *   <li>인증서 검증 비즈니스 프로세스 오케스트레이션</li>
 *   <li>Domain Layer 호출 (Certificate Aggregate)</li>
 *   <li>검증 결과를 Database에 저장</li>
 *   <li>Response DTO로 변환</li>
 * </ul>
 *
 * <p><b>검증 프로세스</b>:</p>
 * <ol>
 *   <li>Command 검증</li>
 *   <li>Certificate Aggregate 조회</li>
 *   <li>서명 검증 (validateSignature = true)</li>
 *   <li>유효기간 검증 (validateValidity = true)</li>
 *   <li>제약사항 검증 (validateConstraints = true)</li>
 *   <li>검증 결과를 ValidationResult Value Object로 저장</li>
 *   <li>Certificate Aggregate 저장 (Domain Events 자동 발행)</li>
 *   <li>Response 반환</li>
 * </ol>
 *
 * @see ValidateCertificateCommand
 * @see ValidateCertificateResponse
 * @see Certificate
 * @author SmartCore Inc.
 * @version 1.0
 * @since 2025-10-24
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ValidateCertificateUseCase {

    private final CertificateRepository certificateRepository;

    /**
     * 인증서 검증 실행
     *
     * @param command 검증 명령
     * @return 검증 결과 응답
     */
    @Transactional
    public ValidateCertificateResponse execute(ValidateCertificateCommand command) {
        log.info("=== Certificate validation started ===");
        log.info("Certificate ID: {}", command.certificateId());

        long startTime = System.currentTimeMillis();

        try {
            // 1. Command 검증
            command.validate();

            // 2. Certificate Aggregate 조회
            CertificateId certificateId = CertificateId.of(UUID.fromString(command.certificateId()));
            Optional<Certificate> certificateOpt = certificateRepository.findById(certificateId);

            if (certificateOpt.isEmpty()) {
                throw new DomainException("CERTIFICATE_NOT_FOUND",
                    "Certificate not found: " + command.certificateId());
            }

            Certificate certificate = certificateOpt.get();
            log.info("Certificate found: subjectDn={}", certificate.getSubjectInfo().getDistinguishedName());

            // 3. 검증 수행
            LocalDateTime validatedAt = LocalDateTime.now();

            // TODO: Phase 11 Sprint 5에서 X509CertificateValidationAdapter 구현 예정
            // 현재는 skeleton 구현으로 모든 검증을 true로 설정

            Boolean signatureValid = command.validateSignature() ? performSignatureValidation(certificate) : null;
            Boolean chainValid = command.validateChain() ? performChainValidation(certificate) : null;
            Boolean notRevoked = command.checkRevocation() ? performRevocationCheck(certificate) : null;
            Boolean validityValid = command.validateValidity() ? performValidityCheck(certificate) : null;
            Boolean constraintsValid = command.validateConstraints() ? performConstraintsCheck(certificate) : null;

            // 4. 검증 결과 판단
            boolean allValid = isAllValid(signatureValid, chainValid, notRevoked, validityValid, constraintsValid);
            CertificateStatus overallStatus = allValid ? CertificateStatus.VALID : CertificateStatus.INVALID;

            // 5. ValidationResult Value Object 생성 및 Certificate에 설정
            long duration = System.currentTimeMillis() - startTime;
            ValidationResult validationResult = ValidationResult.of(
                overallStatus,
                signatureValid != null && signatureValid,
                chainValid != null && chainValid,
                notRevoked != null && notRevoked,
                validityValid != null && validityValid,
                constraintsValid != null && constraintsValid,
                duration
            );

            certificate.recordValidation(validationResult);

            // 6. Certificate 저장 (ValidationResult 업데이트)
            Certificate savedCertificate = certificateRepository.save(certificate);
            log.info("Certificate validation result saved: overallStatus={}, duration={}ms",
                overallStatus.name(), duration);

            // 7. Response 생성
            if (allValid) {
                return ValidateCertificateResponse.success(
                    savedCertificate.getId().getId(),
                    savedCertificate.getSubjectInfo().getDistinguishedName(),
                    savedCertificate.getIssuerInfo().getDistinguishedName(),
                    savedCertificate.getX509Data().getSerialNumber(),
                    savedCertificate.getX509Data().getFingerprintSha256(),
                    signatureValid, chainValid, notRevoked, validityValid, constraintsValid,
                    validatedAt, duration
                );
            } else {
                // ValidationErrors 변환
                List<ValidationErrorDto> errors = savedCertificate.getValidationErrors().stream()
                    .map(error -> new ValidationErrorDto(
                        error.getErrorCode(),
                        error.getErrorMessage(),
                        error.getSeverity(),  // severity는 이미 String
                        error.getOccurredAt()
                    ))
                    .collect(Collectors.toList());

                return ValidateCertificateResponse.failure(
                    savedCertificate.getId().getId(),
                    savedCertificate.getSubjectInfo().getDistinguishedName(),
                    savedCertificate.getIssuerInfo().getDistinguishedName(),
                    savedCertificate.getX509Data().getSerialNumber(),
                    savedCertificate.getX509Data().getFingerprintSha256(),
                    overallStatus.name(),
                    signatureValid, chainValid, notRevoked, validityValid, constraintsValid,
                    validatedAt, duration,
                    errors
                );
            }

        } catch (DomainException e) {
            log.error("Domain error during certificate validation: {}", e.getMessage());
            throw e;
        } catch (Exception e) {
            log.error("Unexpected error during certificate validation", e);
            throw new DomainException("VALIDATION_ERROR",
                "Certificate validation failed: " + e.getMessage());
        }
    }

    /**
     * 서명 검증 수행
     *
     * <p>TODO: Phase 11 Sprint 5에서 X509CertificateValidationAdapter 구현 예정</p>
     * <p>현재는 skeleton 구현</p>
     *
     * @param certificate 인증서
     * @return 서명 유효 여부
     */
    private Boolean performSignatureValidation(Certificate certificate) {
        log.debug("Performing signature validation (skeleton implementation)");
        // TODO: BouncyCastle을 사용한 실제 서명 검증 구현 필요
        // X509Certificate x509 = parseX509Certificate(certificate.getX509Data().getCertificateBinary());
        // x509.verify(issuerPublicKey);
        return true;  // Skeleton
    }

    /**
     * Trust Chain 검증 수행
     *
     * <p>TODO: Phase 11 Sprint 5에서 VerifyTrustChainUseCase와 연동 예정</p>
     * <p>현재는 skeleton 구현</p>
     *
     * @param certificate 인증서
     * @return 체인 유효 여부
     */
    private Boolean performChainValidation(Certificate certificate) {
        log.debug("Performing chain validation (skeleton implementation)");
        // TODO: VerifyTrustChainUseCase와 연동
        return true;  // Skeleton
    }

    /**
     * 폐기 여부 확인 (CRL Check)
     *
     * <p>TODO: Phase 11 Sprint 5에서 CRL 확인 로직 구현 예정</p>
     * <p>현재는 skeleton 구현</p>
     *
     * @param certificate 인증서
     * @return 폐기되지 않음 여부
     */
    private Boolean performRevocationCheck(Certificate certificate) {
        log.debug("Performing revocation check (skeleton implementation)");
        // TODO: CRL 다운로드 및 확인 로직 구현
        return true;  // Skeleton
    }

    /**
     * 유효기간 검증 수행
     *
     * @param certificate 인증서
     * @return 유효기간 내 여부
     */
    private Boolean performValidityCheck(Certificate certificate) {
        log.debug("Performing validity check");
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime notBefore = certificate.getValidity().getNotBefore();
        LocalDateTime notAfter = certificate.getValidity().getNotAfter();

        return now.isAfter(notBefore) && now.isBefore(notAfter);
    }

    /**
     * 제약사항 검증 수행
     *
     * <p>TODO: Phase 11 Sprint 5에서 Basic Constraints, Key Usage 검증 구현 예정</p>
     * <p>현재는 skeleton 구현</p>
     *
     * @param certificate 인증서
     * @return 제약사항 준수 여부
     */
    private Boolean performConstraintsCheck(Certificate certificate) {
        log.debug("Performing constraints check (skeleton implementation)");
        // TODO: Basic Constraints, Key Usage, Extended Key Usage 검증
        return true;  // Skeleton
    }

    /**
     * 모든 검증 결과가 유효한지 확인
     *
     * @param values 검증 결과 목록 (null은 검증 미수행으로 간주하여 무시)
     * @return 모든 검증 통과 여부
     */
    private boolean isAllValid(Boolean... values) {
        for (Boolean value : values) {
            if (value != null && !value) {
                return false;
            }
        }
        return true;
    }
}
