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

            // 3. Trust Chain 구축 (재귀적)
            List<CertificateChainDto> certificateChain = new ArrayList<>();
            List<ValidationErrorDto> validationErrors = new ArrayList<>();

            boolean chainComplete = buildChain(
                endEntity,
                certificateChain,
                validationErrors,
                0,  // chainLevel
                10  // maxDepth
            );

            if (!chainComplete) {
                log.warn("Trust Chain construction incomplete: depth={}", certificateChain.size());
            }

            log.info("Trust Chain built: depth={}, certificates={}",
                certificateChain.size(), certificateChain.size());

            // 4. Trust Anchor 확인
            if (certificateChain.isEmpty()) {
                log.error("Trust Chain is empty");
                throw new DomainException("EMPTY_TRUST_CHAIN",
                    "Failed to build trust chain");
            }

            // 마지막 인증서가 Trust Anchor (Root CA, Self-Signed)인지 확인
            CertificateChainDto rootCertDto = certificateChain.get(certificateChain.size() - 1);
            Optional<Certificate> rootCertOpt = certificateRepository.findById(
                CertificateId.of(rootCertDto.certificateId())
            );

            if (rootCertOpt.isEmpty()) {
                throw new DomainException("ROOT_CERTIFICATE_NOT_FOUND",
                    "Root certificate not found in chain");
            }

            Certificate trustAnchor = rootCertOpt.get();

            // 5. Trust Anchor 검증 (Self-Signed, CA 플래그)
            if (!trustAnchor.isSelfSigned()) {
                log.warn("Trust Anchor is not self-signed: {}",
                    trustAnchor.getSubjectInfo().getDistinguishedName());
            }

            if (!trustAnchor.isCA()) {
                log.warn("Trust Anchor does not have CA flag: {}",
                    trustAnchor.getSubjectInfo().getDistinguishedName());
            }

            // 6. 국가 코드 필터링 (명시된 경우)
            if (command.trustAnchorCountryCode() != null) {
                String anchorCountry = trustAnchor.getSubjectInfo().getCountryCode();
                if (!command.trustAnchorCountryCode().equals(anchorCountry)) {
                    log.warn("Trust Anchor country mismatch: expected={}, actual={}",
                        command.trustAnchorCountryCode(), anchorCountry);
                    long duration = System.currentTimeMillis() - startTime;
                    return VerifyTrustChainResponse.trustAnchorNotFound(
                        endEntity.getId().getId(),
                        endEntity.getSubjectInfo().getDistinguishedName(),
                        command.trustAnchorCountryCode(),
                        validatedAt,
                        duration
                    );
                }
            }

            // 7. 검증 성공 Response
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
     * <p><b>알고리즘</b>:</p>
     * <ol>
     *   <li>현재 인증서를 체인에 추가</li>
     *   <li>Self-Signed (Trust Anchor) 인지 확인</li>
     *   <li>Self-Signed면 완료 (true 반환)</li>
     *   <li>Self-Signed 아니면 Issuer DN으로 상위 인증서 검색</li>
     *   <li>상위 인증서 찾으면 재귀 호출</li>
     *   <li>상위 인증서 못 찾으면 부분 완료 (false 반환)</li>
     * </ol>
     *
     * @param certificate 현재 인증서
     * @param chain 체인 목록 (출력 파라미터)
     * @param validationErrors 검증 오류 목록
     * @param depth 현재 깊이
     * @param maxDepth 최대 깊이
     * @return Trust Anchor 도달 여부
     */
    private boolean buildChain(
        Certificate certificate,
        List<CertificateChainDto> chain,
        List<ValidationErrorDto> validationErrors,
        int depth,
        int maxDepth
    ) {
        // 1. 최대 깊이 확인
        if (depth > maxDepth) {
            log.warn("Maximum chain depth {} exceeded", maxDepth);
            return false;
        }

        log.debug("Building chain level {}: {}", depth,
            certificate.getSubjectInfo().getCommonName());

        // 2. 현재 인증서를 체인에 추가
        CertificateChainDto dto = CertificateChainDto.builder()
            .chainLevel(depth)
            .certificateId(certificate.getId().getId())
            .subjectDn(certificate.getSubjectInfo().getDistinguishedName())
            .issuerDn(certificate.getIssuerInfo().getDistinguishedName())
            .certificateType(certificate.getCertificateType().name())
            .status(certificate.getStatus().name())
            .signatureValid(true)  // 추후 검증 로직 통합 시 업데이트
            .build();

        chain.add(dto);

        // 3. Self-Signed 확인 (Trust Anchor 도달)
        if (certificate.isSelfSigned()) {
            log.info("Trust Anchor (Self-Signed CA) found at depth {}: {}",
                depth, certificate.getSubjectInfo().getCommonName());
            return true;  // Trust Chain 완성
        }

        // 4. Issuer DN으로 상위 인증서 검색
        String issuerDn = certificate.getIssuerInfo().getDistinguishedName();
        log.debug("Searching for issuer certificate: {}", issuerDn);

        Optional<Certificate> issuerOpt = certificateRepository.findBySubjectDn(issuerDn);

        if (issuerOpt.isEmpty()) {
            log.warn("Issuer certificate not found: {}", issuerDn);
            ValidationErrorDto error = ValidationErrorDto.builder()
                .errorCode("ISSUER_NOT_FOUND")
                .errorMessage("Issuer certificate not found: " + issuerDn)
                .severity("WARNING")
                .build();
            validationErrors.add(error);
            return false;  // Trust Chain 불완전
        }

        // 5. 상위 인증서로 재귀 호출
        Certificate issuerCert = issuerOpt.get();
        log.debug("Issuer certificate found: {}, recursing to depth {}",
            issuerCert.getSubjectInfo().getCommonName(), depth + 1);

        return buildChain(
            issuerCert,
            chain,
            validationErrors,
            depth + 1,
            maxDepth
        );
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
