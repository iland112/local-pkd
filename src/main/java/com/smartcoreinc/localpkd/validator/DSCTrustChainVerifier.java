package com.smartcoreinc.localpkd.validator;

import java.io.ByteArrayInputStream;
import java.security.cert.CertPath;
import java.security.cert.CertPathValidator;
import java.security.cert.CertPathValidatorException;
import java.security.cert.PKIXParameters;
import java.security.cert.TrustAnchor;
import java.security.cert.X509Certificate;
import java.util.Arrays;
import java.util.Base64;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import javax.naming.Name;

import org.springframework.ldap.support.LdapNameBuilder;
import org.springframework.stereotype.Service;

import com.smartcoreinc.localpkd.icaomasterlist.entity.CscaCertificate;
import com.smartcoreinc.localpkd.icaomasterlist.service.CscaLdapSearchService;
import com.smartcoreinc.localpkd.ldif.dto.LdifEntryDto;
import com.smartcoreinc.localpkd.ldif.dto.TrustChainValidationResult;
import com.smartcoreinc.localpkd.ldif.dto.ValidationResult;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
public class DSCTrustChainVerifier {

    private final CscaLdapSearchService cscaLdapSearchService;

    public DSCTrustChainVerifier(CscaLdapSearchService cscaLdapSearchService) {
        this.cscaLdapSearchService = cscaLdapSearchService;
    }

    public ValidationResult verify(LdifEntryDto entryDto) {

        ValidationResult validationResult = null;

        // LdifEntryDto에서 속성 맵을 가져옴
        Map<String, List<String>> attributes = entryDto.getAttributes();

        // 1. DN에 "o=dsc"가 포함되고, 2. userCertificate 속성이 존재하는 경우에만 DSC 엔트리로 간주합니다.
        boolean isDscEntry = entryDto.getDn().contains("o=dsc") && attributes.containsKey("userCertificate;binary");

        // DSC 엔트리인 경우에만 신뢰 체인 검증 수행
        if (isDscEntry) {
            log.info("DSC 엔트리 발견, 신뢰 체인 검증 시작");
            String validationResultMessage = null;

            try {
                Name dn = LdapNameBuilder.newInstance(entryDto.getDn()).build();
                String countryCode = getCountryCodeFromDn(dn);
                if (countryCode == null) {
                    validationResultMessage = TrustChainValidationResult.FAILED.name()
                            + ": Country code not found in DN";
                    validationResult = new ValidationResult(TrustChainValidationResult.FAILED, validationResultMessage);
                    return validationResult;
                } else {
                    List<CscaCertificate> cscaCerts = cscaLdapSearchService
                            .findCscaCertificatesByCountryCode(countryCode);
                    if (cscaCerts.isEmpty()) {
                        validationResultMessage = TrustChainValidationResult.FAILED.name()
                                + ": No CSCA certificates found for country " + countryCode;
                        validationResult = new ValidationResult(TrustChainValidationResult.FAILED, validationResultMessage);
                        return validationResult;
                    } else {
                        List<String> dscCertValues = attributes.get("userCertificate;binary");
                        if (dscCertValues != null && !dscCertValues.isEmpty()) {
                            byte[] dscCertBytes = Base64.getDecoder().decode(dscCertValues.get(0));

                            // dscCertBytes를 X509Certificate로 변환
                            X509Certificate dscCert = convertBytesToX509Cert(dscCertBytes);

                            // CscaCertificate 엔티티에서 X509Certificate 객체 리스트로 변환
                            List<X509Certificate> x509CscaCerts = cscaCerts.stream()
                                    .map(CscaCertificate::getCertificate)
                                    .map(this::convertBytesToX509Cert)
                                    .filter(Objects::nonNull)
                                    .collect(Collectors.toList());

                            // 수정된 함수 호출
                            ValidationResult result = validateDscTrustChain(dscCert, x509CscaCerts);

                            // DTO에서 상태와 메시지를 추출하여 LDAP 저장용 문자열 생성
                            if (result.getStatus() == TrustChainValidationResult.SUCCESS) {
                                validationResultMessage = TrustChainValidationResult.SUCCESS.name();
                                validationResult = new ValidationResult(TrustChainValidationResult.SUCCESS, "");
                                return validationResult;
                            } else {
                                validationResultMessage = TrustChainValidationResult.FAILED.name()
                                        + ": userCertificate attribute not found";
                                validationResult = new ValidationResult(TrustChainValidationResult.FAILED, validationResultMessage);
                                return validationResult;
                            }
                        }
                    }
                }
            } catch (Exception e) {
                log.error("신뢰 체인 검증 중 예외 발생: {}", e.getMessage());
                validationResultMessage = TrustChainValidationResult.FAILED.name() + ": Internal error - "
                        + e.getClass().getSimpleName();
                validationResult = new ValidationResult(TrustChainValidationResult.FAILED, validationResultMessage);
                return validationResult;
            }

            // 검증 결과를 entryDto의 속성에 추가
            if (validationResultMessage != null) {
                attributes.put("validationResult", Collections.singletonList(validationResultMessage));
            }
        }
        validationResult = new ValidationResult(TrustChainValidationResult.FAILED, "DSC 인증서가 아님.");
        return validationResult;
    }

    /**
     * 바이트 배열을 X509Certificate 객체로 변환
     */
    private X509Certificate convertBytesToX509Cert(byte[] certBytes) {
        try {
            // Bouncy Castle 제공자를 명시적으로 사용하여 인증서 변환
            return (X509Certificate) java.security.cert.CertificateFactory
                    .getInstance("X.509", "BC")
                    .generateCertificate(new ByteArrayInputStream(certBytes));
        } catch (Exception e) {
            log.error("Failed to convert byte array to X509Certificate: {}", e.getMessage());
            return null;
        }
    }

    /**
     * DSC 인증서의 신뢰 체인 검증
     * 
     * @param dscCertBytes DSC 인증서 바이트 배열
     * @param cscaCerts    신뢰할 수 있는 CSCA 인증서 리스트
     * @return 검증 성공 시 "Success", 실패 시 실패 이유 문자열
     */
    private ValidationResult validateDscTrustChain(X509Certificate dscCert, List<X509Certificate> cscaCerts) {
        try {
            // 1. DSC 인증서의 서명 알고리즘 및 유효 기간 검증
            log.info("Validating DSC certificate: {}", dscCert.getSubjectX500Principal().getName());
            dscCert.checkValidity(); // 유효 기간 검증

            // 2. 신뢰할 수 있는 CSCA 인증서들을 TrustAnchor로 변환
            Set<TrustAnchor> trustAnchors = cscaCerts.stream()
                    .map(cert -> new TrustAnchor(cert, null))
                    .collect(Collectors.toSet());

            // 3. 인증 경로(CertPath) 생성
            List<X509Certificate> certs = Arrays.asList(dscCert);
            CertPath certPath = java.security.cert.CertificateFactory
                    .getInstance("X.509")
                    .generateCertPath(certs);

            // 4. PKIX 파라미터 설정 (신뢰할 수 있는 앵커 설정)
            PKIXParameters pkixParams = new PKIXParameters(trustAnchors);
            pkixParams.setRevocationEnabled(false); // CRL 검증은 이 예제에서 제외

            // 5. 인증 경로 유효성 검증
            CertPathValidator certPathValidator = CertPathValidator.getInstance("PKIX");
            certPathValidator.validate(certPath, pkixParams);

            return new ValidationResult(TrustChainValidationResult.SUCCESS, "Validation successful");
        } catch (CertPathValidatorException e) {
            return new ValidationResult(TrustChainValidationResult.FAILED, e.getMessage());
        } catch (Exception e) {
            log.error("인증서 검증 중 예외 발생: {}", e.getMessage());
            return new ValidationResult(TrustChainValidationResult.FAILED, "Internal error: " + e.getMessage());
        }
    }

    /**
     * DN에서 국가 코드 (c=) 추출
     * 
     * @param dn DN 객체
     * @return 국가 코드 (예: "kr"), 찾지 못하면 null
     */
    private String getCountryCodeFromDn(Name dn) {
        for (int i = 0; i < dn.size(); i++) {
            String rdn = dn.get(i);
            if (rdn.toLowerCase().startsWith("c=")) {
                return rdn.substring(2);
            }
        }
        return null;
    }
}
