package com.smartcoreinc.localpkd.certificatevalidation.application.usecase;

import com.smartcoreinc.localpkd.certificatevalidation.domain.model.Certificate;
import com.smartcoreinc.localpkd.certificatevalidation.domain.model.CertificateId;
import com.smartcoreinc.localpkd.certificatevalidation.domain.model.ValidationResult;
import com.smartcoreinc.localpkd.certificatevalidation.domain.repository.CertificateRepository;
import com.smartcoreinc.localpkd.shared.exception.DomainException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

/**
 * RecordValidationUseCase - 인증서 검증 결과 기록 Use Case
 *
 * <p><b>책임</b>:</p>
 * <ul>
 *   <li>검증 결과를 Certificate Aggregate에 저장</li>
 *   <li>검증 이력 추적 및 감사</li>
 *   <li>검증 통계 업데이트</li>
 * </ul>
 *
 * <p><b>기록 프로세스</b>:</p>
 * <ol>
 *   <li>검증 결과 Command 검증</li>
 *   <li>Certificate Aggregate 조회</li>
 *   <li>ValidationResult Value Object 생성</li>
 *   <li>Certificate에 검증 결과 기록</li>
 *   <li>Certificate 저장 (Domain Events 자동 발행)</li>
 *   <li>검증 통계 업데이트</li>
 * </ol>
 *
 * <p><b>검증 기록 항목</b>:</p>
 * <ul>
 *   <li>검증 수행 시간</li>
 *   <li>검증 소요 시간</li>
 *   <li>서명 검증 결과</li>
 *   <li>Trust Chain 검증 결과</li>
 *   <li>폐기 여부 확인 결과</li>
 *   <li>유효기간 검증 결과</li>
 *   <li>제약사항 검증 결과</li>
 * </ul>
 *
 * @see ValidationResult
 * @see Certificate
 * @author SmartCore Inc.
 * @version 1.0
 * @since 2025-10-25
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RecordValidationUseCase {

    private final CertificateRepository certificateRepository;

    /**
     * 검증 결과 기록 실행
     *
     * <p>여러 검증 결과를 종합하여 Certificate에 기록합니다.</p>
     *
     * @param certificateId 검증한 인증서 ID
     * @param signatureValid 서명 검증 결과
     * @param chainValid Trust Chain 검증 결과
     * @param notRevoked 폐기 여부 확인 결과
     * @param validityValid 유효기간 검증 결과
     * @param constraintsValid 제약사항 검증 결과
     * @param durationMillis 검증 소요 시간 (밀리초)
     * @return 검증 결과가 기록된 Certificate
     */
    @Transactional
    public Certificate execute(
        UUID certificateId,
        Boolean signatureValid,
        Boolean chainValid,
        Boolean notRevoked,
        Boolean validityValid,
        Boolean constraintsValid,
        Long durationMillis
    ) {
        log.info("=== Recording validation result ===");
        log.info("Certificate ID: {}", certificateId);

        try {
            // 1. Certificate Aggregate 조회
            CertificateId certId = CertificateId.of(certificateId);
            Optional<Certificate> certificateOpt = certificateRepository.findById(certId);

            if (certificateOpt.isEmpty()) {
                throw new DomainException("CERTIFICATE_NOT_FOUND",
                    "Certificate not found: " + certificateId);
            }

            Certificate certificate = certificateOpt.get();
            log.debug("Certificate found: subjectDn={}", certificate.getSubjectInfo().getDistinguishedName());

            // 2. ValidationResult Value Object 생성
            boolean allValid = isAllValid(signatureValid, chainValid, notRevoked, validityValid, constraintsValid);
            ValidationResult validationResult = ValidationResult.of(
                allValid ? certificate.getStatus() : certificate.getStatus(),  // TODO: 상태 결정 로직 개선
                signatureValid != null && signatureValid,
                chainValid != null && chainValid,
                notRevoked != null && notRevoked,
                validityValid != null && validityValid,
                constraintsValid != null && constraintsValid,
                durationMillis
            );

            log.debug("ValidationResult created: allValid={}, duration={}ms", allValid, durationMillis);

            // 3. Certificate에 검증 결과 기록
            certificate.recordValidation(validationResult);

            // 4. Certificate 저장 (Domain Events 자동 발행)
            Certificate saved = certificateRepository.save(certificate);
            log.info("Validation result recorded and saved: certificateId={}", saved.getId().getId());

            // 5. 검증 통계 업데이트 (TODO: Phase 11 Sprint 5에서 통계 서비스 구현 예정)
            updateValidationStatistics(certificate.getCertificateType().name(), allValid);

            return saved;

        } catch (DomainException e) {
            log.error("Domain error during validation recording: {}", e.getMessage());
            throw e;
        } catch (Exception e) {
            log.error("Unexpected error during validation recording", e);
            throw new DomainException("RECORD_VALIDATION_ERROR",
                "Failed to record validation result: " + e.getMessage());
        }
    }

    /**
     * 검증 통계 업데이트
     *
     * <p>TODO: Phase 11 Sprint 5에서 실제 통계 업데이트 구현 예정</p>
     *
     * @param certificateType 인증서 타입 (CSCA, DSC, DS 등)
     * @param isValid 검증 결과 (true: 유효, false: 무효)
     */
    private void updateValidationStatistics(String certificateType, boolean isValid) {
        log.debug("Updating validation statistics: type={}, valid={}", certificateType, isValid);

        // TODO: 통계 저장소에 검증 결과 기록
        // statisticsService.recordValidation(certificateType, isValid);
    }

    /**
     * 모든 검증 결과가 유효한지 확인
     *
     * <p>null은 검증 미수행으로 간주하여 무시합니다.</p>
     *
     * @param values 검증 결과 목록
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
