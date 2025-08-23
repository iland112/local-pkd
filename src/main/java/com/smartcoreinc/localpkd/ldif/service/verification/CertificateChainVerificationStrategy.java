package com.smartcoreinc.localpkd.ldif.service.verification;

import java.security.cert.X509Certificate;
import java.util.Arrays;
import java.util.List;

import org.springframework.stereotype.Component;

import com.smartcoreinc.localpkd.ldif.service.CertificateChainValidationResult;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
public class CertificateChainVerificationStrategy
        implements CertificateVerificationStrategy<CertificateChainRequest, CertificateChainValidationResult> {

    private final TrustAnchorRepository trustAnchorRepository;

    public CertificateChainVerificationStrategy(TrustAnchorRepository trustAnchorRepository) {
        this.trustAnchorRepository = trustAnchorRepository;
    }

    @Override
    public CertificateChainValidationResult verify(CertificateChainRequest request, VerificationContext context) {
        try {
            List<X509Certificate> trustAnchorsForCountry = trustAnchorRepository
                    .getTrustAnchors(request.getIssuerCountry());
            if (trustAnchorsForCountry == null || trustAnchorsForCountry.isEmpty()) {
                return CertificateChainValidationResult
                        .invalid("No trust anchors found for country: " + request.getIssuerCountry());
            }

            // 각 Trust Anchor로 검증 시도
            for (X509Certificate trustAnchor : trustAnchorsForCountry) {
                try {
                    if (validateAgainstTrustAnchor(request.getCertificate(), trustAnchor)) {
                        log.info("Certificate chain validation successful using Trust Anchor: {}",
                                trustAnchor.getSubjectX500Principal().getName());

                        return CertificateChainValidationResult.valid(
                                "Certificate chain validated successfully",
                                Arrays.asList(request.getCertificate(), trustAnchor));
                    }
                } catch (Exception e) {
                    log.debug("Validation failed with trust anchor {}: {}",
                            trustAnchor.getSubjectX500Principal().getName(), e.getMessage());
                }
            }

            return CertificateChainValidationResult.invalid(
                    "Certificate could not be validated against any trust anchor for country: "
                            + request.getIssuerCountry());

        } catch (Exception e) {
            return CertificateChainValidationResult.invalid(
                    "Error during certificate chain validation: " + e.getMessage());
        }
    }

    @Override
    public boolean supports(Class<?> dataType) {
        return CertificateChainRequest.class.equals(dataType);
    }

    private boolean validateAgainstTrustAnchor(X509Certificate certificate, X509Certificate trustAnchor)
            throws Exception {
        // 발급자 확인
        if (!certificate.getIssuerX500Principal().equals(trustAnchor.getSubjectX500Principal())) {
            return false;
        }

        // 서명 검증
        certificate.verify(trustAnchor.getPublicKey());

        // 유효기간 확인
        certificate.checkValidity();
        trustAnchor.checkValidity();

        return true;
    }
}
