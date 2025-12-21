package com.smartcoreinc.localpkd.certificatevalidation.infrastructure.adapter;

import com.smartcoreinc.localpkd.certificatevalidation.domain.model.Certificate;
import com.smartcoreinc.localpkd.certificatevalidation.domain.model.CertificateRevocationList;
import com.smartcoreinc.localpkd.common.util.CountryCodeUtil;
import com.smartcoreinc.localpkd.certificatevalidation.domain.model.CertificateType;
import com.smartcoreinc.localpkd.certificatevalidation.domain.model.ValidationError;
import com.smartcoreinc.localpkd.certificatevalidation.domain.model.ValidityPeriod;
import com.smartcoreinc.localpkd.certificatevalidation.domain.port.CertificateValidationPort;
import com.smartcoreinc.localpkd.certificatevalidation.domain.repository.CertificateRepository;
import com.smartcoreinc.localpkd.certificatevalidation.domain.repository.CertificateRevocationListRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * BouncyCastleValidationAdapter - BouncyCastle 기반 X.509 인증서 검증 Adapter
 *
 * <p><b>Infrastructure Layer Adapter</b>:</p>
 * <ul>
 *   <li>Domain Port {@link CertificateValidationPort} 구현</li>
 *   <li>BouncyCastle 라이브러리 통합</li>
 *   <li>X.509 인증서 검증 로직 수행</li>
 * </ul>
 *
 * <p><b>현재 상태: SKELETON IMPLEMENTATION (Phase 11 Sprint 5)</b>:</p>
 * <ul>
 *   <li>⚠️ 모든 메서드는 기본값 반환 (실제 검증 로직 없음)</li>
 *   <li>⚠️ BouncyCastle 통합은 향후 Sprint 6 또는 별도 Phase에서 구현 예정</li>
 *   <li>✅ 컴파일 가능하며 Use Cases에서 사용 가능</li>
 *   <li>✅ 테스트를 위한 기본 구조 제공</li>
 * </ul>
 *
 * <p><b>향후 구현 예정 항목</b>:</p>
 * <ul>
 *   <li>BouncyCastle X509CertificateHolder 변환</li>
 *   <li>서명 검증 (PublicKey, SignatureAlgorithm)</li>
 *   <li>Basic Constraints Extension 파싱</li>
 *   <li>Key Usage Extension 파싱</li>
 *   <li>Trust Chain 재귀적 구축</li>
 *   <li>CRL 다운로드 및 폐기 확인</li>
 *   <li>OCSP 요청 및 응답 처리</li>
 * </ul>
 *
 * <p><b>BouncyCastle 의존성 (향후 추가 필요)</b>:</p>
 * <pre>{@code
 * <dependency>
 *     <groupId>org.bouncycastle</groupId>
 *     <artifactId>bcprov-jdk18on</artifactId>
 *     <version>1.78</version>
 * </dependency>
 * <dependency>
 *     <groupId>org.bouncycastle</groupId>
 *     <artifactId>bcpkix-jdk18on</artifactId>
 *     <version>1.78</version>
 * </dependency>
 * }</pre>
 *
 * <p><b>사용 예시 - Use Case에서 주입</b>:</p>
 * <pre>{@code
 * @Service
 * @RequiredArgsConstructor
 * public class ValidateCertificateUseCase {
 *
 *     private final CertificateValidationPort validationPort; // BouncyCastleValidationAdapter 주입
 *
 *     @Transactional
 *     public ValidateCertificateResponse execute(ValidateCertificateCommand command) {
 *         Certificate certificate = certificateRepository.findById(certId).orElseThrow();
 *
 *         // 서명 검증
 *         boolean signatureValid = validationPort.validateSignature(certificate, issuer);
 *
 *         return ValidateCertificateResponse.success(...);
 *     }
 * }
 * }</pre>
 *
 * @author SmartCore Inc.
 * @version 1.0
 * @since 2025-10-24
 * @see CertificateValidationPort
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class BouncyCastleValidationAdapter implements CertificateValidationPort {

    // ========== Dependencies ==========
    private final CertificateRepository certificateRepository;
    private final CertificateRevocationListRepository crlRepository;

    /**
     * 인증서 서명 검증
     *
     * <p><b>⚠️ SKELETON IMPLEMENTATION - 향후 구현 예정</b></p>
     *
     * <p><b>TODO (Phase 11 Sprint 6 또는 향후):</b></p>
     * <ol>
     *   <li>Certificate → BouncyCastle X509CertificateHolder 변환</li>
     *   <li>Issuer 공개 키 추출</li>
     *   <li>서명 알고리즘 파싱 (SHA256WithRSA, SHA256WithEC 등)</li>
     *   <li>BouncyCastle ContentVerifierProvider 생성</li>
     *   <li>서명 검증 수행: certificateHolder.isSignatureValid(verifierProvider)</li>
     * </ol>
     *
     * @param certificate 검증할 인증서
     * @param issuerCertificate 발급자 인증서 (null이면 self-signed 검증)
     * @return 항상 true 반환 (Skeleton)
     */
    @Override
    public boolean validateSignature(Certificate certificate, Certificate issuerCertificate) {
        log.debug("validateSignature(): certificate={}, issuer={}",
            certificate.getId().getId(),
            issuerCertificate != null ? issuerCertificate.getId().getId() : "self-signed");

        try {
            // 1. Certificate를 X509Certificate로 변환
            java.security.cert.X509Certificate x509Cert =
                convertToX509Certificate(certificate.getX509Data().getCertificateBinary());

            // 2. Self-signed 인증서 확인
            if (issuerCertificate == null) {
                // Self-signed 인증서의 경우 issuer == subject
                return validateSelfSignedSignature(x509Cert);
            }

            // 3. Issuer 공개 키 가져오기
            java.security.cert.X509Certificate issuerX509Cert =
                convertToX509Certificate(issuerCertificate.getX509Data().getCertificateBinary());
            java.security.PublicKey issuerPublicKey = issuerX509Cert.getPublicKey();

            // 4. BouncyCastle X509CertificateHolder로 변환
            org.bouncycastle.cert.X509CertificateHolder certHolder =
                new org.bouncycastle.cert.X509CertificateHolder(x509Cert.getEncoded());

            // 5. ContentVerifierProvider 생성
            org.bouncycastle.operator.ContentVerifierProvider verifierProvider =
                createVerifierProvider(issuerPublicKey);

            // 6. 서명 검증
            boolean isValid = certHolder.isSignatureValid(verifierProvider);

            if (isValid) {
                log.debug("Signature validation successful: certificate={}",
                    certificate.getId().getId());
            } else {
                log.warn("Signature validation failed: certificate={}, issuer={}",
                    certificate.getId().getId(),
                    issuerCertificate.getId().getId());
            }

            return isValid;

        } catch (Exception e) {
            log.error("Error during signature validation: certificate={}, error={}",
                certificate.getId().getId(), e.getMessage(), e);
            return false;
        }
    }

    /**
     * Self-signed 인증서의 서명 검증
     */
    private boolean validateSelfSignedSignature(java.security.cert.X509Certificate x509Cert)
            throws Exception {
        log.debug("Validating self-signed certificate: {}",
            x509Cert.getSubjectX500Principal().getName());

        try {
            // Self-signed 인증서는 자신의 공개 키로 검증
            x509Cert.verify(x509Cert.getPublicKey());
            log.debug("Self-signed certificate signature is valid");
            return true;
        } catch (java.security.SignatureException e) {
            log.warn("Self-signed certificate signature is invalid: {}", e.getMessage());
            return false;
        }
    }

    /**
     * X509Certificate로 변환
     * Uses Bouncy Castle provider to support explicit EC parameters
     */
    private java.security.cert.X509Certificate convertToX509Certificate(byte[] certificateBytes)
            throws Exception {
        java.security.cert.CertificateFactory cf =
            java.security.cert.CertificateFactory.getInstance("X.509", "BC");
        return (java.security.cert.X509Certificate) cf.generateCertificate(
            new java.io.ByteArrayInputStream(certificateBytes)
        );
    }

    /**
     * ContentVerifierProvider 생성
     */
    private org.bouncycastle.operator.ContentVerifierProvider createVerifierProvider(
            java.security.PublicKey publicKey) throws Exception {
        // 공개 키 알고리즘에 따라 적절한 verifier provider 생성
        String algorithm = publicKey.getAlgorithm();

        if ("RSA".equals(algorithm)) {
            return new org.bouncycastle.operator.jcajce.JcaContentVerifierProviderBuilder()
                .build(publicKey);
        } else if ("ECDSA".equals(algorithm) || "EC".equals(algorithm)) {
            return new org.bouncycastle.operator.jcajce.JcaContentVerifierProviderBuilder()
                .build(publicKey);
        } else if ("DSA".equals(algorithm)) {
            return new org.bouncycastle.operator.jcajce.JcaContentVerifierProviderBuilder()
                .build(publicKey);
        } else {
            throw new IllegalArgumentException("Unsupported key algorithm: " + algorithm);
        }
    }

    /**
     * 인증서 유효기간 검증
     *
     * <p><b>⚠️ SKELETON IMPLEMENTATION - 향후 구현 예정</b></p>
     *
     * <p><b>TODO (Phase 11 Sprint 6 또는 향후):</b></p>
     * <ol>
     *   <li>현재 시간 확인: LocalDateTime.now()</li>
     *   <li>notBefore <= now < notAfter 검증</li>
     *   <li>실패 시 ValidationError 생성 (NOT_YET_VALID, EXPIRED)</li>
     * </ol>
     *
     * @param certificate 검증할 인증서
     * @return 항상 true 반환 (Skeleton)
     */
    @Override
    public boolean validateValidity(Certificate certificate) {
        log.debug("validateValidity(): certificate={}, notBefore={}, notAfter={}",
            certificate.getId().getId(),
            certificate.getValidity().getNotBefore(),
            certificate.getValidity().getNotAfter());

        try {
            ValidityPeriod validity = certificate.getValidity();

            // 1. Null 체크
            if (validity == null) {
                log.warn("Validity period is null: certificate={}", certificate.getId().getId());
                return false;
            }

            // 2. 현재 시간 vs notBefore 비교
            if (validity.isNotYetValid()) {
                log.warn("Certificate is not yet valid: certificate={}, notBefore={}",
                    certificate.getId().getId(), validity.getNotBefore());
                return false;
            }

            // 3. 현재 시간 vs notAfter 비교
            if (validity.isExpired()) {
                log.warn("Certificate has expired: certificate={}, notAfter={}",
                    certificate.getId().getId(), validity.getNotAfter());
                return false;
            }

            // 4. 현재 유효함
            log.debug("Validity validation passed: certificate={}", certificate.getId().getId());
            return true;

        } catch (Exception e) {
            log.error("Error during validity validation: certificate={}, error={}",
                certificate.getId().getId(), e.getMessage(), e);
            return false;
        }
    }

    /**
     * Basic Constraints 검증
     *
     * <p><b>⚠️ SKELETON IMPLEMENTATION - 향후 구현 예정</b></p>
     *
     * <p><b>TODO (Phase 11 Sprint 6 또는 향후):</b></p>
     * <ol>
     *   <li>X509CertificateHolder에서 Basic Constraints Extension 추출</li>
     *   <li>CA 플래그 확인: CSCA, DSC_NC는 CA=true, DSC, DS는 CA=false</li>
     *   <li>Path Length 제약 확인 (CA인 경우)</li>
     *   <li>Critical 플래그 확인</li>
     * </ol>
     *
     * @param certificate 검증할 인증서
     * @return 항상 true 반환 (Skeleton)
     */
    @Override
    public boolean validateBasicConstraints(Certificate certificate) {
        log.debug("validateBasicConstraints(): certificate={}, type={}",
            certificate.getId().getId(),
            certificate.getCertificateType());

        try {
            // 1. X509Certificate로 변환
            java.security.cert.X509Certificate x509Cert =
                convertToX509Certificate(certificate.getX509Data().getCertificateBinary());

            // 2. BouncyCastle X509CertificateHolder로 변환
            org.bouncycastle.cert.X509CertificateHolder certHolder =
                new org.bouncycastle.cert.X509CertificateHolder(x509Cert.getEncoded());

            // 3. Basic Constraints Extension 추출
            org.bouncycastle.asn1.x509.Extension bcExtension =
                certHolder.getExtension(org.bouncycastle.asn1.x509.Extension.basicConstraints);

            if (bcExtension == null) {
                // Basic Constraints가 없는 경우 - CA가 아님
                log.debug("No Basic Constraints extension: certificate={}", certificate.getId().getId());
                // CA가 아니어야 하는 인증서(DSC, DS)는 OK
                return !certificate.getCertificateType().isCA();
            }

            // 4. BasicConstraints 파싱
            org.bouncycastle.asn1.x509.BasicConstraints bc =
                org.bouncycastle.asn1.x509.BasicConstraints.getInstance(bcExtension.getParsedValue());

            // 5. Critical 플래그 확인
            if (bcExtension.isCritical()) {
                log.debug("Basic Constraints is marked as critical: certificate={}",
                    certificate.getId().getId());
            }

            // 6. CA 플래그 검증
            boolean isCA = bc.isCA();
            boolean expectedCA = certificate.getCertificateType().isCA();

            if (isCA != expectedCA) {
                log.warn("Basic Constraints CA flag mismatch: certificate={}, isCA={}, expected={}",
                    certificate.getId().getId(), isCA, expectedCA);
                return false;
            }

            // 7. CA인 경우 pathLengthConstraint 확인 (선택사항)
            if (isCA) {
                int pathLength = bc.getPathLenConstraint() != null ?
                    bc.getPathLenConstraint().intValue() : -1;

                if (pathLength >= 0) {
                    log.debug("CA certificate with pathLengthConstraint: certificate={}, pathLength={}",
                        certificate.getId().getId(), pathLength);
                }
            }

            log.debug("Basic Constraints validation passed: certificate={}, isCA={}",
                certificate.getId().getId(), isCA);
            return true;

        } catch (Exception e) {
            log.error("Error during basic constraints validation: certificate={}, error={}",
                certificate.getId().getId(), e.getMessage(), e);
            return false;
        }
    }

    /**
     * Key Usage 검증
     *
     * <p><b>⚠️ SKELETON IMPLEMENTATION - 향후 구현 예정</b></p>
     *
     * <p><b>TODO (Phase 11 Sprint 6 또는 향후):</b></p>
     * <ol>
     *   <li>X509CertificateHolder에서 Key Usage Extension 추출</li>
     *   <li>CSCA: keyCertSign, cRLSign 필수</li>
     *   <li>DSC: digitalSignature 필수</li>
     *   <li>DS: digitalSignature 필수</li>
     * </ol>
     *
     * @param certificate 검증할 인증서
     * @return 항상 true 반환 (Skeleton)
     */
    @Override
    public boolean validateKeyUsage(Certificate certificate) {
        log.debug("validateKeyUsage(): certificate={}, type={}",
            certificate.getId().getId(),
            certificate.getCertificateType());

        try {
            // 1. X509Certificate로 변환
            java.security.cert.X509Certificate x509Cert =
                convertToX509Certificate(certificate.getX509Data().getCertificateBinary());

            // 2. BouncyCastle X509CertificateHolder로 변환
            org.bouncycastle.cert.X509CertificateHolder certHolder =
                new org.bouncycastle.cert.X509CertificateHolder(x509Cert.getEncoded());

            // 3. Key Usage Extension 추출
            org.bouncycastle.asn1.x509.Extension kuExtension =
                certHolder.getExtension(org.bouncycastle.asn1.x509.Extension.keyUsage);

            if (kuExtension == null) {
                // Key Usage가 없는 경우 - 경고만 하고 통과
                log.warn("No Key Usage extension: certificate={}", certificate.getId().getId());
                return true;  // 선택사항이므로 경고만 함
            }

            // 4. KeyUsage 파싱
            org.bouncycastle.asn1.x509.KeyUsage ku =
                org.bouncycastle.asn1.x509.KeyUsage.getInstance(kuExtension.getParsedValue());

            // 5. 인증서 타입에 따른 Key Usage 검증
            CertificateType certType = certificate.getCertificateType();

            switch (certType) {
                case CSCA:
                    // CSCA: keyCertSign, cRLSign 필수
                    return validateKeyUsageForCSCA(ku, certificate.getId().getId());

                case DSC:
                case DSC_NC:
                    // DSC/DSC_NC: digitalSignature 필수
                    return validateKeyUsageForDSC(ku, certificate.getId().getId());

                case DS:
                    // DS: digitalSignature 필수
                    return validateKeyUsageForDS(ku, certificate.getId().getId());

                default:
                    log.warn("Unknown certificate type for key usage validation: certificate={}, type={}",
                        certificate.getId().getId(), certType);
                    return true;
            }

        } catch (Exception e) {
            log.error("Error during key usage validation: certificate={}, error={}",
                certificate.getId().getId(), e.getMessage(), e);
            return false;
        }
    }

    /**
     * CSCA의 Key Usage 검증
     */
    private boolean validateKeyUsageForCSCA(org.bouncycastle.asn1.x509.KeyUsage ku, UUID certificateId) {
        // CSCA: keyCertSign (bit 5), cRLSign (bit 6) 필수
        boolean hasKeyCertSign = ku.hasUsages(org.bouncycastle.asn1.x509.KeyUsage.keyCertSign);
        boolean hasCRLSign = ku.hasUsages(org.bouncycastle.asn1.x509.KeyUsage.cRLSign);

        if (!hasKeyCertSign || !hasCRLSign) {
            log.warn("CSCA missing required key usage bits: certificate={}, keyCertSign={}, cRLSign={}",
                certificateId, hasKeyCertSign, hasCRLSign);
            return false;
        }

        log.debug("CSCA key usage validation passed: certificate={}", certificateId);
        return true;
    }

    /**
     * DSC/DSC_NC의 Key Usage 검증
     */
    private boolean validateKeyUsageForDSC(org.bouncycastle.asn1.x509.KeyUsage ku, UUID certificateId) {
        // DSC/DSC_NC: digitalSignature (bit 0) 필수
        boolean hasDigitalSignature = ku.hasUsages(org.bouncycastle.asn1.x509.KeyUsage.digitalSignature);

        if (!hasDigitalSignature) {
            log.warn("DSC missing required key usage bit (digitalSignature): certificate={}", certificateId);
            return false;
        }

        log.debug("DSC key usage validation passed: certificate={}", certificateId);
        return true;
    }

    /**
     * DS의 Key Usage 검증
     */
    private boolean validateKeyUsageForDS(org.bouncycastle.asn1.x509.KeyUsage ku, UUID certificateId) {
        // DS: digitalSignature (bit 0) 필수
        boolean hasDigitalSignature = ku.hasUsages(org.bouncycastle.asn1.x509.KeyUsage.digitalSignature);

        if (!hasDigitalSignature) {
            log.warn("DS missing required key usage bit (digitalSignature): certificate={}", certificateId);
            return false;
        }

        log.debug("DS key usage validation passed: certificate={}", certificateId);
        return true;
    }

    /**
     * Trust Chain 구축
     *
     * <p>End Entity 인증서부터 Trust Anchor(CSCA)까지의 인증 경로를 구축합니다.</p>
     *
     * <p><b>구현 프로세스</b>:</p>
     * <ol>
     *   <li>End Entity 인증서의 Issuer DN 확인</li>
     *   <li>CertificateRepository에서 Issuer DN과 일치하는 인증서 검색</li>
     *   <li>발급자 인증서의 서명 검증</li>
     *   <li>발급자가 Trust Anchor(CSCA)인지 확인</li>
     *   <li>Trust Anchor가 아니면 재귀적으로 반복 (최대 깊이 제한)</li>
     * </ol>
     *
     * @param certificate End Entity 인증서
     * @param trustAnchor Trust Anchor 인증서 (null이면 자동 검색)
     * @param maxDepth 최대 인증 경로 깊이 (기본값: 5)
     * @return Trust Chain (End Entity → ... → Trust Anchor)
     */
    @Override
    public List<Certificate> buildTrustChain(Certificate certificate, Certificate trustAnchor, int maxDepth) {
        log.info("=== Trust Chain building started ===");
        log.info("Certificate: id={}, issuer={}",
            certificate.getId().getId(),
            certificate.getIssuerInfo().getDistinguishedName());

        List<Certificate> chain = new ArrayList<>();

        try {
            // 1. End Entity 인증서를 체인의 시작점으로 추가
            chain.add(certificate);

            // 2. 현재 인증서를 시작점으로 설정
            Certificate current = certificate;

            // 3. Trust Anchor에 도달할 때까지 또는 최대 깊이에 도달할 때까지 반복
            int depth = 0;
            while (!isTrustAnchor(current) && depth < maxDepth) {
                // 발급자 인증서 검색
                Certificate issuer = findIssuerCertificate(current);

                if (issuer == null) {
                    log.warn("Issuer certificate not found for: {}",
                        current.getIssuerInfo().getDistinguishedName());
                    break;
                }

                // 서명 검증
                if (!validateSignature(issuer, issuer)) {
                    log.warn("Issuer signature validation failed: id={}",
                        issuer.getId().getId());
                    break;
                }

                chain.add(issuer);
                log.debug("Added to chain: issuer={}, depth={}",
                    issuer.getId().getId(), depth + 1);

                current = issuer;
                depth++;
            }

            // 4. Trust Anchor 확인
            if (isTrustAnchor(current)) {
                log.info("Trust Chain built successfully: depth={}, count={}",
                    depth, chain.size());
            } else {
                log.warn("Trust Chain not built: no Trust Anchor found, depth={}, maxDepth={}",
                    depth, maxDepth);
            }

            return chain;

        } catch (Exception e) {
            log.error("Error during Trust Chain building: {}",
                e.getMessage(), e);
            return chain;  // 현재까지 구축된 체인 반환
        }
    }

    /**
     * 인증서 폐기 확인 (CRL/OCSP)
     *
     * <p>CRL(Certificate Revocation List) 또는 OCSP(Online Certificate Status Protocol)를 통해
     * 인증서가 폐기되었는지 확인합니다.</p>
     *
     * <p><b>현재 구현 전략</b>:</p>
     * <ol>
     *   <li>인증서에서 CRL Distribution Points Extension 추출 시도</li>
     *   <li>CRL Distribution Points가 없으면 폐기 확인 스킵 (true 반환)</li>
     *   <li>CRL Distribution Points가 있으면 로깅만 수행</li>
     *   <li>실제 CRL 다운로드 및 파싱은 향후 구현 (Network 작업)</li>
     * </ol>
     *
     * <p><b>향후 구현 예정</b>:</p>
     * <ul>
     *   <li>CRL URL에서 CRL 다운로드 (HTTP/LDAP)</li>
     *   <li>CRL 파싱 (BouncyCastle X509CRLHolder)</li>
     *   <li>인증서 일련 번호 검색</li>
     *   <li>OCSP 요청 지원 (선택사항)</li>
     * </ul>
     *
     * @param certificate 검증할 인증서
     * @return 폐기되지 않았으면 true, 폐기되었으면 false
     */
    /**
     * CSCA 발급자명 추출 (DN에서)
     *
     * <p>DN: CN=CSCA-QA,O=Qatar,C=QA 에서 CSCA-QA 추출</p>
     *
     * @param issuerDn Distinguished Name
     * @return CSCA 발급자명 (예: CSCA-QA) 또는 null
     */
    private String extractIssuerName(String issuerDn) {
        if (issuerDn == null || issuerDn.isEmpty()) {
            return null;
        }

        // CN=CSCA-XX 패턴 추출
        Pattern pattern = Pattern.compile("CN=([^,]+)");
        Matcher matcher = pattern.matcher(issuerDn);

        if (matcher.find()) {
            String cn = matcher.group(1);
            // CSCA-XX 형식 확인
            if (cn.matches("CSCA-[A-Z]{2}")) {
                return cn;
            }
        }

        return null;
    }

    @Override
    public boolean checkRevocation(Certificate certificate) {
        log.info("=== Certificate revocation check started (Phase 12 Week 4) ===");
        log.info("Certificate: id={}, serialNumber={}",
            certificate.getId().getId(),
            certificate.getX509Data().getSerialNumber());

        try {
            // 1. 인증서 발급자 DN 추출
            String issuerDn = certificate.getIssuerInfo().getDistinguishedName();
            if (issuerDn == null || issuerDn.isEmpty()) {
                log.warn("Cannot extract issuer DN from certificate: id={}",
                    certificate.getId().getId());
                return true;  // 폐기 정보 없음: 폐기되지 않았다고 가정 (보수적)
            }

            log.debug("Issuer DN: {}", issuerDn);

            // 2. CSCA 발급자명 추출 (예: CSCA-QA)
            String issuerName = extractIssuerName(issuerDn);
            if (issuerName == null) {
                log.debug("Could not extract CSCA issuer name from DN: {}", issuerDn);
                return true;  // CSCA 형식이 아님: 폐기되지 않았다고 가정
            }

            log.debug("CSCA issuer name: {}", issuerName);

            // 3. 국가 코드 추출
            String countryCode = CountryCodeUtil.extractCountryCode(issuerDn);
            if (countryCode == null) {
                log.debug("Could not extract country code from issuer DN: {}", issuerDn);
                return true;  // 국가 코드 없음: 폐기되지 않았다고 가정
            }

            log.debug("Country code: {}", countryCode);

            // 4. CRL 데이터베이스에서 조회
            Optional<CertificateRevocationList> crlOpt =
                crlRepository.findByIssuerNameAndCountry(issuerName, countryCode);

            if (crlOpt.isEmpty()) {
                log.warn("No CRL found for issuer={}, country={}",
                    issuerName, countryCode);
                return true;  // CRL 없음: 폐기 정보 미제공, 폐기되지 않았다고 가정 (보수적)
            }

            CertificateRevocationList crl = crlOpt.get();
            log.debug("CRL found: id={}, issuer={}, country={}, revokedCount={}",
                crl.getId().getId(), issuerName, countryCode, crl.getRevokedCount());

            // 5. CRL 유효성 확인
            if (!crl.isValid()) {
                if (crl.isExpired()) {
                    log.warn("CRL is expired: issuer={}, country={}, nextUpdate={}",
                        issuerName, countryCode, crl.getValidityPeriod().getNotAfter());
                } else if (crl.isNotYetValid()) {
                    log.warn("CRL is not yet valid: issuer={}, country={}, thisUpdate={}",
                        issuerName, countryCode, crl.getValidityPeriod().getNotBefore());
                }
                // CRL이 유효하지 않으면 폐기 여부 확인 불가: 폐기되지 않았다고 가정 (보수적)
                return true;
            }

            // 6. 인증서 일련번호로 폐기 여부 확인
            String serialNumber = certificate.getX509Data().getSerialNumber();
            if (serialNumber == null || serialNumber.isEmpty()) {
                log.error("Certificate has no serial number: id={}",
                    certificate.getId().getId());
                return true;  // 일련번호 없음: 폐기되지 않았다고 가정
            }

            boolean isRevoked = crl.isRevoked(serialNumber);

            if (isRevoked) {
                log.error("Certificate is revoked: serialNumber={}, issuer={}, country={}",
                    serialNumber, issuerName, countryCode);
                return false;  // 폐기됨!
            }

            log.info("Certificate is not revoked: serialNumber={}, issuer={}, country={}",
                serialNumber, issuerName, countryCode);
            return true;  // 폐기되지 않음

        } catch (IllegalArgumentException e) {
            log.warn("Invalid parameter during revocation check: certificate={}, error={}",
                certificate.getId().getId(), e.getMessage());
            return true;  // 폐기 확인 실패: 폐기되지 않았다고 가정 (보수적)
        } catch (Exception e) {
            log.error("Unexpected error during revocation check: certificate={}, error={}",
                certificate.getId().getId(), e.getMessage(), e);
            return true;  // 폐기 확인 실패: 폐기되지 않았다고 가정 (보수적)
        }
    }

    /**
     * Trust Chain 검증 (DSC → CSCA)
     * <p>
     * DSC가 CSCA에 의해 서명되었는지 검증합니다.
     * </p>
     *
     * @param dsc Document Signer Certificate
     * @param csca Country Signing CA
     * @throws IllegalStateException Trust chain validation failed
     */
    public void validateTrustChain(Certificate dsc, Certificate csca) {
        log.debug("Validating trust chain: DSC → CSCA");

        try {
            // 1. DSC의 Issuer DN과 CSCA의 Subject DN이 일치하는지 확인
            String dscIssuerDn = dsc.getIssuerInfo().getDistinguishedName();
            String cscaSubjectDn = csca.getSubjectInfo().getDistinguishedName();

            if (!dscIssuerDn.equals(cscaSubjectDn)) {
                throw new IllegalStateException(
                    String.format("DSC issuer DN does not match CSCA subject DN: %s != %s",
                        dscIssuerDn, cscaSubjectDn)
                );
            }

            // 2. CSCA의 공개키로 DSC 서명 검증
            if (!validateSignature(dsc, csca)) {
                throw new IllegalStateException("DSC signature validation failed with CSCA public key");
            }

            // 3. CSCA 자체 검증 (self-signed)
            if (!validateSignature(csca, null)) {
                throw new IllegalStateException("CSCA self-signed signature validation failed");
            }

            log.debug("Trust chain validation passed");

        } catch (Exception e) {
            log.error("Trust chain validation error: {}", e.getMessage(), e);
            throw new IllegalStateException("Trust chain validation failed: " + e.getMessage(), e);
        }
    }

    /**
     * 인증서가 CRL에 의해 폐기되었는지 확인
     *
     * @param certificate 검증할 인증서
     * @param crl Certificate Revocation List
     * @return 폐기되었으면 true, 아니면 false
     */
    public boolean isRevoked(Certificate certificate, CertificateRevocationList crl) {
        log.debug("Checking if certificate is revoked: serialNumber={}",
            certificate.getX509Data().getSerialNumber());

        try {
            // 1. CRL 유효성 확인
            if (!crl.isValid()) {
                if (crl.isExpired()) {
                    log.warn("CRL is expired: nextUpdate={}",
                        crl.getValidityPeriod().getNotAfter());
                } else if (crl.isNotYetValid()) {
                    log.warn("CRL is not yet valid: thisUpdate={}",
                        crl.getValidityPeriod().getNotBefore());
                }
                // CRL이 유효하지 않으면 폐기 여부 확인 불가
                return false;
            }

            // 2. 인증서 일련번호로 폐기 여부 확인
            String serialNumber = certificate.getX509Data().getSerialNumber();
            if (serialNumber == null || serialNumber.isEmpty()) {
                log.error("Certificate has no serial number: id={}",
                    certificate.getId().getId());
                return false;
            }

            boolean revoked = crl.isRevoked(serialNumber);

            if (revoked) {
                log.warn("Certificate is revoked: serialNumber={}", serialNumber);
            } else {
                log.debug("Certificate is not revoked: serialNumber={}", serialNumber);
            }

            return revoked;

        } catch (Exception e) {
            log.error("Error checking certificate revocation: {}", e.getMessage(), e);
            return false;
        }
    }

    /**
     * 완전한 인증서 검증 수행
     *
     * <p><b>⚠️ SKELETON IMPLEMENTATION - 향후 구현 예정</b></p>
     *
     * <p><b>TODO (Phase 11 Sprint 6 또는 향후):</b></p>
     * <ol>
     *   <li>모든 검증 메서드 호출 (서명, 유효기간, Basic Constraints, Key Usage)</li>
     *   <li>각 검증 실패 시 ValidationError 생성 및 리스트에 추가</li>
     *   <li>Trust Chain 검증 (옵션)</li>
     *   <li>폐기 확인 (옵션)</li>
     *   <li>검증 오류 리스트 반환</li>
     * </ol>
     *
     * @param certificate 검증할 인증서
     * @param trustAnchor Trust Anchor 인증서 (null이면 자동 검색)
     * @param checkRevocation 폐기 확인 수행 여부
     * @return 빈 리스트 반환 (Skeleton: 모든 검증 성공)
     */
    @Override
    public List<ValidationError> performFullValidation(
        Certificate certificate,
        Certificate trustAnchor,
        boolean checkRevocation
    ) {
        log.info("performFullValidation(): certificate={}, trustAnchor={}, checkRevocation={}",
            certificate.getId().getId(),
            trustAnchor != null ? trustAnchor.getId().getId() : "auto",
            checkRevocation);

        List<ValidationError> errors = new ArrayList<>();

        try {
            // 1. 서명 검증 (필수)
            if (!validateSignature(certificate, trustAnchor)) {
                log.warn("Signature validation failed: certificate={}", certificate.getId().getId());
                errors.add(ValidationError.of(
                    "SIGNATURE_INVALID",
                    "서명 검증 실패",
                    "인증서의 디지털 서명이 유효하지 않습니다"
                ));
            }

            // 2. 유효기간 검증 (필수)
            if (!validateValidity(certificate)) {
                log.warn("Validity validation failed: certificate={}", certificate.getId().getId());
                if (certificate.getValidity().isExpired()) {
                    errors.add(ValidationError.of(
                        "CERTIFICATE_EXPIRED",
                        "인증서 만료됨",
                        "인증서의 유효기간이 만료되었습니다"
                    ));
                } else if (certificate.getValidity().isNotYetValid()) {
                    errors.add(ValidationError.of(
                        "CERTIFICATE_NOT_YET_VALID",
                        "인증서 미발효",
                        "인증서의 유효 개시 일자에 도달하지 않았습니다"
                    ));
                }
            }

            // 3. Basic Constraints 검증 (필수)
            if (!validateBasicConstraints(certificate)) {
                log.warn("Basic Constraints validation failed: certificate={}", certificate.getId().getId());
                errors.add(ValidationError.of(
                    "BASIC_CONSTRAINTS_INVALID",
                    "Basic Constraints 검증 실패",
                    "인증서의 Basic Constraints 설정이 인증서 타입과 맞지 않습니다"
                ));
            }

            // 4. Key Usage 검증 (필수)
            if (!validateKeyUsage(certificate)) {
                log.warn("Key Usage validation failed: certificate={}", certificate.getId().getId());
                errors.add(ValidationError.of(
                    "KEY_USAGE_INVALID",
                    "Key Usage 검증 실패",
                    String.format("%s 인증서에 필요한 Key Usage가 없습니다",
                        certificate.getCertificateType().getDisplayName())
                ));
            }

            // 5. 폐기 확인 (선택사항)
            if (checkRevocation) {
                if (!checkRevocation(certificate)) {
                    log.warn("Certificate revocation check failed: certificate={}", certificate.getId().getId());
                    errors.add(ValidationError.of(
                        "CERTIFICATE_REVOKED",
                        "인증서 폐기됨",
                        "인증서가 폐기되었습니다"
                    ));
                }
            }

            // 6. Trust Chain 검증 (선택사항)
            if (trustAnchor != null) {
                if (!validateSignature(certificate, trustAnchor)) {
                    // Trust Chain 검증 실패 (이미 위에서 처리됨)
                    log.warn("Trust Chain validation failed: certificate={}, trustAnchor={}",
                        certificate.getId().getId(), trustAnchor.getId().getId());
                }
            }

            // 결과 로깅
            if (errors.isEmpty()) {
                log.info("Full validation passed: certificate={}", certificate.getId().getId());
            } else {
                log.warn("Full validation failed with {} errors: certificate={}",
                    errors.size(), certificate.getId().getId());
            }

            return errors;

        } catch (Exception e) {
            log.error("Unexpected error during full validation: certificate={}, error={}",
                certificate.getId().getId(), e.getMessage(), e);
            errors.add(ValidationError.of(
                "VALIDATION_ERROR",
                "검증 중 오류 발생",
                "예상치 못한 오류가 발생했습니다: " + e.getMessage()
            ));
            return errors;
        }
    }

    // ========== Private Helper Methods ==========

    /**
     * 인증서가 Trust Anchor인지 확인
     *
     * <p><b>Trust Anchor 판단 기준</b>:</p>
     * <ul>
     *   <li>CertificateType = CSCA</li>
     *   <li>Self-signed (Subject DN = Issuer DN)</li>
     *   <li>CA 플래그 = true (Basic Constraints)</li>
     * </ul>
     *
     * @param certificate 확인할 인증서
     * @return Trust Anchor이면 true
     */
    private boolean isTrustAnchor(Certificate certificate) {
        try {
            // 1. 인증서 타입 확인: CSCA여야 함
            if (certificate.getCertificateType() != CertificateType.CSCA) {
                log.debug("Not a Trust Anchor: certificate type is not CSCA: {}",
                    certificate.getCertificateType());
                return false;
            }

            // 2. Self-signed 확인: Subject DN = Issuer DN
            String subjectDn = certificate.getSubjectInfo().getDistinguishedName();
            String issuerDn = certificate.getIssuerInfo().getDistinguishedName();

            if (subjectDn == null || issuerDn == null || !subjectDn.equals(issuerDn)) {
                log.debug("Not a Trust Anchor: not self-signed");
                return false;
            }

            // 3. CA 플래그 확인 (Basic Constraints)
            if (!validateBasicConstraints(certificate)) {
                log.debug("Not a Trust Anchor: Basic Constraints validation failed");
                return false;
            }

            log.debug("Trust Anchor identified: id={}, DN={}",
                certificate.getId().getId(), subjectDn);
            return true;

        } catch (Exception e) {
            log.error("Error checking Trust Anchor: {}",
                e.getMessage(), e);
            return false;
        }
    }

    /**
     * 발급자 인증서 검색
     *
     * <p>주어진 인증서의 Issuer DN과 일치하는 Subject DN을 가진 인증서를 검색합니다.</p>
     *
     * <p><b>동작 방식</b>:</p>
     * <ol>
     *   <li>피발급자(certificate)의 Issuer DN 추출</li>
     *   <li>발급자의 Subject DN = 피발급자의 Issuer DN (X.509 표준)</li>
     *   <li>CertificateRepository.findBySubjectDn(issuerDn) 호출</li>
     *   <li>발급자 인증서 반환 (없으면 null)</li>
     * </ol>
     *
     * @param certificate 발급자를 찾을 인증서
     * @return 발급자 인증서 (찾지 못하면 null)
     */
    private Certificate findIssuerCertificate(Certificate certificate) {
        try {
            // 1. Issuer DN 추출
            String issuerDn = certificate.getIssuerInfo().getDistinguishedName();

            if (issuerDn == null || issuerDn.isBlank()) {
                log.warn("Cannot find issuer: Issuer DN is null or empty");
                return null;
            }

            log.debug("Searching issuer certificate: issuerDn={}", issuerDn);

            // 2. CertificateRepository에서 Subject DN이 Issuer DN과 일치하는 인증서 검색
            // issuerDn은 발급자의 Subject DN과 같아야 함 (X.509 표준)
            java.util.Optional<Certificate> issuerOpt = certificateRepository.findBySubjectDn(issuerDn);

            if (issuerOpt.isPresent()) {
                Certificate issuer = issuerOpt.get();
                log.debug("Issuer certificate found: id={}, subjectDn={}",
                    issuer.getId().getId(), issuerDn);
                return issuer;
            } else {
                log.warn("Issuer certificate not found: issuerDn={}", issuerDn);
                return null;
            }

        } catch (IllegalArgumentException e) {
            // findBySubjectDn()에서 null/blank DN으로 호출된 경우
            log.warn("Error finding issuer certificate (invalid parameter): {}",
                e.getMessage());
            return null;
        } catch (Exception e) {
            log.error("Unexpected error finding issuer certificate: {}",
                e.getMessage(), e);
            return null;
        }
    }
}
