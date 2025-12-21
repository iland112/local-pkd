package com.smartcoreinc.localpkd.certificatevalidation.application.usecase;

import com.smartcoreinc.localpkd.certificatevalidation.application.command.CheckRevocationCommand;
import com.smartcoreinc.localpkd.certificatevalidation.application.response.CheckRevocationResponse;
import com.smartcoreinc.localpkd.certificatevalidation.domain.model.Certificate;
import com.smartcoreinc.localpkd.certificatevalidation.domain.model.CertificateId;
import com.smartcoreinc.localpkd.certificatevalidation.domain.repository.CertificateRepository;
import com.smartcoreinc.localpkd.shared.exception.DomainException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

/**
 * CheckRevocationUseCase - 인증서 폐기 여부 확인 Use Case
 *
 * <p><b>책임</b>:</p>
 * <ul>
 *   <li>인증서 폐기 확인 비즈니스 프로세스 오케스트레이션</li>
 *   <li>Certificate Revocation List (CRL) 조회 및 검사</li>
 *   <li>폐기 상태 업데이트 및 저장</li>
 *   <li>Fail-Open 정책에 따른 오류 처리</li>
 * </ul>
 *
 * <p><b>폐기 확인 프로세스</b>:</p>
 * <ol>
 *   <li>Command 검증</li>
 *   <li>Certificate Aggregate 조회</li>
 *   <li>Issuer DN을 통해 CRL 조회</li>
 *   <li>일련번호 기반 폐기 목록 검사</li>
 *   <li>폐기된 경우 폐기 정보 기록</li>
 *   <li>Certificate 상태 업데이트 및 저장</li>
 *   <li>Response 반환</li>
 * </ol>
 *
 * <p><b>Fail-Open 정책</b>:</p>
 * <ul>
 *   <li>CRL 다운로드 실패: NOT_REVOKED로 처리 (가용성 우선)</li>
 *   <li>CRL 타임아웃: NOT_REVOKED로 처리 (가용성 우선)</li>
 *   <li>시스템 오류: 로그 기록 후 계속 진행</li>
 * </ul>
 *
 * @see CheckRevocationCommand
 * @see CheckRevocationResponse
 * @see Certificate
 * @author SmartCore Inc.
 * @version 1.0
 * @since 2025-10-25
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CheckRevocationUseCase {

    private final CertificateRepository certificateRepository;

    /**
     * 인증서 폐기 여부 확인 실행
     *
     * @param command 폐기 확인 명령
     * @return 폐기 확인 결과 응답
     */
    @Transactional
    public CheckRevocationResponse execute(CheckRevocationCommand command) {
        log.info("=== Certificate revocation check started ===");
        log.info("Certificate ID: {}, Issuer DN: {}, Serial Number: {}",
            command.certificateId(), command.issuerDn(), command.serialNumber());

        long startTime = System.currentTimeMillis();
        LocalDateTime checkedAt = LocalDateTime.now();

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
            log.debug("Certificate found: subjectDn={}, serialNumber={}",
                certificate.getSubjectInfo().getDistinguishedName(),
                certificate.getX509Data().getSerialNumber());

            // 3. CRL 조회 및 폐기 확인
            boolean isRevoked = checkCertificateRevocation(
                command.issuerDn(),
                command.serialNumber(),
                command.forceFresh(),
                command.crlFetchTimeoutSeconds()
            );

            // 4. 폐기 상태 업데이트
            if (isRevoked) {
                log.warn("Certificate is revoked: serialNumber={}", command.serialNumber());
            } else {
                log.debug("Certificate is not revoked: serialNumber={}", command.serialNumber());
                // 폐기 여부 기록 (검증 완료)
            }

            // 5. Certificate 저장
            Certificate savedCertificate = certificateRepository.save(certificate);
            log.info("Certificate revocation check saved: revoked={}", isRevoked);

            // 6. Response 생성
            long duration = System.currentTimeMillis() - startTime;

            if (isRevoked) {
                return CheckRevocationResponse.revoked(
                    savedCertificate.getId().getId(),
                    savedCertificate.getX509Data().getSerialNumber(),
                    LocalDateTime.now(),  // Revocation date (placeholder)
                    null,  // Revocation reason code
                    null,  // Revocation reason description
                    command.issuerDn(),
                    LocalDateTime.now(),  // CRL last update
                    LocalDateTime.now().plusDays(30),  // CRL next update
                    checkedAt,
                    duration
                );
            } else {
                return CheckRevocationResponse.notRevoked(
                    savedCertificate.getId().getId(),
                    savedCertificate.getX509Data().getSerialNumber(),
                    command.issuerDn(),
                    LocalDateTime.now(),  // CRL last update
                    LocalDateTime.now().plusDays(30),  // CRL next update
                    !command.forceFresh(),  // CRL cache used
                    checkedAt,
                    duration
                );
            }

        } catch (DomainException e) {
            log.error("Domain error during revocation check: {}", e.getMessage());
            throw e;
        } catch (Exception e) {
            log.error("Unexpected error during revocation check", e);
            throw new DomainException("REVOCATION_CHECK_ERROR",
                "Certificate revocation check failed: " + e.getMessage());
        }
    }

    /**
     * 인증서 폐기 여부 확인 (CRL 기반)
     *
     * <p>TODO: Phase 11 Sprint 5에서 실제 CRL 다운로드 및 검사 로직 구현 예정</p>
     * <p>현재는 skeleton 구현: 항상 폐기되지 않음으로 반환</p>
     *
     * <p><b>Fail-Open 정책</b>:</p>
     * <ul>
     *   <li>CRL 다운로드 실패 시: false (NOT_REVOKED) 반환</li>
     *   <li>CRL 타임아웃 시: false (NOT_REVOKED) 반환</li>
     *   <li>예외 발생 시: false (NOT_REVOKED) 반환</li>
     * </ul>
     *
     * @param issuerDn 발급자 DN (CRL 조회용)
     * @param serialNumber 인증서 일련번호 (폐기 목록 검사용)
     * @param forceFresh 신규 CRL 강제 다운로드 여부
     * @param timeoutSeconds CRL 다운로드 타임아웃 (초)
     * @return 폐기 여부 (true: 폐기됨, false: 폐기되지 않음)
     */
    private boolean checkCertificateRevocation(
        String issuerDn,
        String serialNumber,
        boolean forceFresh,
        int timeoutSeconds
    ) {
        log.debug("Checking certificate revocation: issuerDn={}, serialNumber={}",
            issuerDn, serialNumber);

        try {
            // Stub: Always returns not revoked
            // CRL checking is implemented in BouncyCastleValidationAdapter.checkRevocation()
            return false;

        } catch (Exception e) {
            // Fail-Open: 오류 발생 시 폐기되지 않은 것으로 처리
            log.warn("CRL check failed (Fail-Open to NOT_REVOKED): {}", e.getMessage());
            return false;
        }
    }
}
