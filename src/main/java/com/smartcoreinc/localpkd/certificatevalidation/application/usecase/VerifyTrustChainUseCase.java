package com.smartcoreinc.localpkd.certificatevalidation.application.usecase;

import com.smartcoreinc.localpkd.certificatevalidation.application.command.VerifyTrustChainCommand;
import com.smartcoreinc.localpkd.certificatevalidation.application.response.VerifyTrustChainResponse;
import com.smartcoreinc.localpkd.certificatevalidation.application.response.VerifyTrustChainResponse.CertificateChainDto;
import com.smartcoreinc.localpkd.certificatevalidation.application.response.VerifyTrustChainResponse.ValidationErrorDto;
import com.smartcoreinc.localpkd.certificatevalidation.domain.model.Certificate;
import com.smartcoreinc.localpkd.certificatevalidation.domain.model.CertificateId;
import com.smartcoreinc.localpkd.certificatevalidation.domain.model.CertificateType;
import com.smartcoreinc.localpkd.certificatevalidation.domain.repository.CertificateRepository;
import com.smartcoreinc.localpkd.shared.exception.DomainException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * VerifyTrustChainUseCase - Trust Chain 검증 Use Case
 *
 * <p><b>책임</b>:</p>
 * <ul>
 *   <li>Trust Chain 검증 비즈니스 프로세스 오케스트레이션</li>
 *   <li>End Entity부터 Root CA까지 체인 구축</li>
 *   <li>각 인증서의 서명 검증</li>
 *   <li>Trust Anchor 확인</li>
 * </ul>
 *
 * <p><b>Trust Chain 검증 프로세스</b>:</p>
 * <ol>
 *   <li>Command 검증</li>
 *   <li>End Entity Certificate 조회</li>
 *   <li>Issuer DN을 기반으로 상위 인증서 조회 (재귀)</li>
 *   <li>Trust Anchor (CSCA) 도달 확인</li>
 *   <li>각 인증서의 서명 검증</li>
 *   <li>검증 결과 반환</li>
 * </ol>
 *
 * @see VerifyTrustChainCommand
 * @see VerifyTrustChainResponse
 * @see Certificate
 * @author SmartCore Inc.
 * @version 1.0
 * @since 2025-10-24
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class VerifyTrustChainUseCase {

    private final CertificateRepository certificateRepository;

    /**
     * Trust Chain 검증 실행
     *
     * @param command 검증 명령
     * @return 검증 결과 응답
     */
    @Transactional(readOnly = true)
    public VerifyTrustChainResponse execute(VerifyTrustChainCommand command) {
        log.info("=== Trust Chain verification started ===");
        log.info("Certificate ID: {}, Trust Anchor Country: {}",
            command.certificateId(), command.trustAnchorCountryCode());

        long startTime = System.currentTimeMillis();
        LocalDateTime validatedAt = LocalDateTime.now();

        try {
            // 1. Command 검증
            command.validate();

            // 2. End Entity Certificate 조회
            CertificateId endEntityId = CertificateId.of(UUID.fromString(command.certificateId()));
            Optional<Certificate> endEntityOpt = certificateRepository.findById(endEntityId);

            if (endEntityOpt.isEmpty()) {
                throw new DomainException("CERTIFICATE_NOT_FOUND",
                    "Certificate not found: " + command.certificateId());
            }

            Certificate endEntity = endEntityOpt.get();
            log.info("End Entity Certificate found: type={}, subjectDn={}",
                endEntity.getCertificateType().name(),
                endEntity.getSubjectInfo().getDistinguishedName());

            // 3. Trust Chain 구축
            List<CertificateChainDto> certificateChain = new ArrayList<>();
            List<ValidationErrorDto> validationErrors = new ArrayList<>();

            // TODO: Phase 11 Sprint 5에서 실제 Trust Chain 구축 로직 구현 예정
            // 현재는 skeleton 구현: End Entity만 체인에 추가

            CertificateChainDto endEntityDto = CertificateChainDto.builder()
                .chainLevel(0)
                .certificateId(endEntity.getId().getId())
                .subjectDn(endEntity.getSubjectInfo().getDistinguishedName())
                .issuerDn(endEntity.getIssuerInfo().getDistinguishedName())
                .certificateType(endEntity.getCertificateType().name())
                .status(endEntity.getStatus().name())
                .signatureValid(true)  // Skeleton
                .build();

            certificateChain.add(endEntityDto);

            // 4. Trust Anchor 검색 (Skeleton)
            Certificate trustAnchor = findTrustAnchor(command.trustAnchorCountryCode());

            if (trustAnchor == null) {
                log.warn("Trust Anchor not found for country: {}", command.trustAnchorCountryCode());
                long duration = System.currentTimeMillis() - startTime;
                return VerifyTrustChainResponse.trustAnchorNotFound(
                    endEntity.getId().getId(),
                    endEntity.getSubjectInfo().getDistinguishedName(),
                    command.trustAnchorCountryCode(),
                    validatedAt,
                    duration
                );
            }

            // 5. 검증 성공 Response
            long duration = System.currentTimeMillis() - startTime;
            log.info("Trust Chain verification completed: chainDepth={}, duration={}ms",
                certificateChain.size(), duration);

            return VerifyTrustChainResponse.success(
                endEntity.getId().getId(),
                endEntity.getSubjectInfo().getDistinguishedName(),
                trustAnchor.getId().getId(),
                trustAnchor.getSubjectInfo().getDistinguishedName(),
                trustAnchor.getSubjectInfo().getCountryCode(),
                certificateChain,
                validatedAt,
                duration
            );

        } catch (DomainException e) {
            log.error("Domain error during trust chain verification: {}", e.getMessage());
            throw e;
        } catch (Exception e) {
            log.error("Unexpected error during trust chain verification", e);
            throw new DomainException("TRUST_CHAIN_VERIFICATION_ERROR",
                "Trust Chain verification failed: " + e.getMessage());
        }
    }

    /**
     * Trust Anchor (CSCA) 검색
     *
     * <p>TODO: Phase 11 Sprint 5에서 실제 검색 로직 구현 예정</p>
     * <p>현재는 skeleton 구현: 임의의 CSCA 반환 또는 null</p>
     *
     * @param countryCode Trust Anchor 국가 코드 (null이면 모든 CSCA 허용)
     * @return Trust Anchor Certificate (CSCA) 또는 null
     */
    private Certificate findTrustAnchor(String countryCode) {
        log.debug("Searching for Trust Anchor: countryCode={}", countryCode);

        // TODO: 실제 로직 구현 필요
        // if (countryCode != null) {
        //     List<Certificate> cscas = certificateRepository.findByTypeAndCountry(
        //         CertificateType.CSCA, countryCode
        //     );
        //     return cscas.isEmpty() ? null : cscas.get(0);
        // } else {
        //     List<Certificate> cscas = certificateRepository.findByType(CertificateType.CSCA);
        //     return cscas.isEmpty() ? null : cscas.get(0);
        // }

        // Skeleton: DB에서 CSCA 조회 (첫 번째 것 반환)
        List<Certificate> cscas = certificateRepository.findByType(CertificateType.CSCA);
        if (cscas.isEmpty()) {
            return null;
        }

        // countryCode 필터링
        if (countryCode != null) {
            return cscas.stream()
                .filter(csca -> countryCode.equals(csca.getSubjectInfo().getCountryCode()))
                .findFirst()
                .orElse(null);
        }

        return cscas.get(0);
    }

    /**
     * Trust Chain 구축 (재귀적으로 상위 인증서 검색)
     *
     * <p>TODO: Phase 11 Sprint 5에서 구현 예정</p>
     *
     * @param certificate 현재 인증서
     * @param chain 체인 목록 (출력 파라미터)
     * @param depth 현재 깊이
     * @param maxDepth 최대 깊이
     * @return Trust Anchor 도달 여부
     */
    private boolean buildChain(
        Certificate certificate,
        List<CertificateChainDto> chain,
        int depth,
        int maxDepth
    ) {
        // TODO: 구현 필요
        // 1. 현재 인증서가 Self-Signed인지 확인 (Trust Anchor)
        // 2. Issuer DN으로 상위 인증서 조회
        // 3. 상위 인증서로 재귀 호출
        // 4. 최대 깊이 도달 시 실패
        return false;
    }

    /**
     * Self-Signed 인증서 여부 확인
     *
     * <p>Subject DN과 Issuer DN이 동일하면 Self-Signed입니다.</p>
     *
     * @param certificate 인증서
     * @return Self-Signed 여부
     */
    private boolean isSelfSigned(Certificate certificate) {
        String subjectDn = certificate.getSubjectInfo().getDistinguishedName();
        String issuerDn = certificate.getIssuerInfo().getDistinguishedName();
        return subjectDn.equals(issuerDn);
    }
}
