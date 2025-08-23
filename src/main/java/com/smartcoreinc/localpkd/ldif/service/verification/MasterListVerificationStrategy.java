package com.smartcoreinc.localpkd.ldif.service.verification;

import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.bouncycastle.cert.X509CertificateHolder;
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
import org.bouncycastle.cms.CMSSignedData;
import org.bouncycastle.cms.SignerInformation;
import org.bouncycastle.cms.SignerInformationStore;
import org.bouncycastle.cms.jcajce.JcaSimpleSignerInfoVerifierBuilder;
import org.springframework.stereotype.Component;

import com.smartcoreinc.localpkd.ldif.service.CertificateExtractor;
import com.smartcoreinc.localpkd.ldif.service.MasterListInfo;
import com.smartcoreinc.localpkd.ldif.service.MasterListValidationResult;

import lombok.extern.slf4j.Slf4j;

// 4. Master List 검증 전략
@Slf4j
@Component
public class MasterListVerificationStrategy
        implements CertificateVerificationStrategy<byte[], MasterListValidationResult> {

    private final CertificateExtractor certificateExtractor;

    public MasterListVerificationStrategy(CertificateExtractor certificateExtractor) {
        this.certificateExtractor = certificateExtractor;
    }

    @Override
    public MasterListValidationResult verify(byte[] masterListData, VerificationContext context) {
        if (masterListData == null || masterListData.length < 100) {
            return MasterListValidationResult.invalid("Master List data is null or too short");
        }

        log.info("Processing Master List for country {} (record {}) - {} bytes",
                context.getCountryCode(), context.getRecordNumber(), masterListData.length);

        try {
            CMSSignedData cmsSignedData = new CMSSignedData(masterListData);

            // 서명 검증
            SignatureValidationResult signatureResult = validateSignature(cmsSignedData);

            // CSCA 인증서 추출
            List<X509Certificate> cscaCertificates = extractCscaCertificates(cmsSignedData);

            // 컨텍스트에 추출된 인증서들 저장
            context.setAttribute("extractedCertificates", cscaCertificates);

            // Master List 정보 생성
            MasterListInfo masterListInfo = createMasterListInfo(
                    context.getCountryCode(),
                    cscaCertificates.size(),
                    signatureResult.isValid());

            String message = signatureResult.isValid()
                    ? "Master List validated successfully"
                    : "Master List parsed but signature verification failed: " + signatureResult.getErrorMessage();

            return MasterListValidationResult.valid(message, cscaCertificates, masterListInfo);

        } catch (Exception e) {
            log.error("Error processing Master List for country {}: {}", context.getCountryCode(), e.getMessage(), e);
            return MasterListValidationResult.invalid("Master List parsing failed: " + e.getMessage());
        }
    }

    @Override
    public boolean supports(Class<?> dataType) {
        return byte[].class.equals(dataType);
    }

    private SignatureValidationResult validateSignature(CMSSignedData cmsSignedData) {
        try {
            SignerInformationStore signers = cmsSignedData.getSignerInfos();
            if (signers.size() == 0) {
                return new SignatureValidationResult(false, "No signers found in Master List");
            }

            for (SignerInformation signer : signers.getSigners()) {
                try {
                    @SuppressWarnings("unchecked")
                    X509CertificateHolder signerCert = (X509CertificateHolder) cmsSignedData.getCertificates()
                            .getMatches(signer.getSID()).iterator().next();

                    if (signer.verify(new JcaSimpleSignerInfoVerifierBuilder()
                            .setProvider("BC").build(signerCert))) {
                        log.debug("Master List signature verified successfully");
                        return new SignatureValidationResult(true, "Signature verified");
                    }
                } catch (Exception e) {
                    log.debug("Signature verification failed: {}", e.getMessage());
                }
            }

            return new SignatureValidationResult(false, "All signature verifications failed");
        } catch (Exception e) {
            return new SignatureValidationResult(false, "Signature validation error: " + e.getMessage());
        }
    }

    private List<X509Certificate> extractCscaCertificates(CMSSignedData cmsSignedData) {
        List<X509Certificate> certificates = new ArrayList<>();

        // 1. SignedData의 certificates 필드에서 추출
        extractFromCertificatesField(cmsSignedData, certificates);

        // 2. Content에서 추출
        extractFromContent(cmsSignedData, certificates);

        // 중복 제거
        return removeDuplicateCertificates(certificates);
    }

    private void extractFromCertificatesField(CMSSignedData cmsSignedData, List<X509Certificate> certificates) {
        try {
            SignerInformationStore signers = cmsSignedData.getSignerInfos();

            for (Object certHolder : cmsSignedData.getCertificates().getMatches(null)) {
                if (certHolder instanceof X509CertificateHolder) {
                    try {
                        X509Certificate cert = new JcaX509CertificateConverter()
                                .setProvider("BC")
                                .getCertificate((X509CertificateHolder) certHolder);

                        // 서명자 인증서가 아닌 경우만 CSCA로 간주
                        if (!isSignerCertificate(cert, certHolder, signers)) {
                            certificates.add(cert);
                            log.debug("Found CSCA certificate from certificates field: Subject={}",
                                    cert.getSubjectX500Principal().getName());
                        }
                    } catch (Exception e) {
                        log.debug("Failed to convert certificate holder: {}", e.getMessage());
                    }
                }
            }
        } catch (Exception e) {
            log.debug("Failed to extract from certificates field: {}", e.getMessage());
        }
    }

    private void extractFromContent(CMSSignedData cmsSignedData, List<X509Certificate> certificates) {
        try {
            Object content = cmsSignedData.getSignedContent().getContent();
            if (content instanceof byte[]) {
                byte[] contentBytes = (byte[]) content;
                log.debug("Processing signed content: {} bytes", contentBytes.length);

                List<X509Certificate> contentCertificates = certificateExtractor
                        .extractCertificatesFromContent(contentBytes);

                for (X509Certificate cert : contentCertificates) {
                    certificates.add(cert);
                    log.debug("Added certificate from content: Subject={}",
                            cert.getSubjectX500Principal().getName());
                }
            }
        } catch (Exception e) {
            log.debug("Failed to extract from content: {}", e.getMessage());
        }
    }

    private boolean isSignerCertificate(X509Certificate cert, Object certHolder, SignerInformationStore signers) {
        for (SignerInformation signer : signers.getSigners()) {
            if (signer.getSID().match(certHolder)) {
                return true;
            }
        }
        return false;
    }

    private List<X509Certificate> removeDuplicateCertificates(List<X509Certificate> certificates) {
        Map<String, X509Certificate> uniqueCerts = new HashMap<>();

        for (X509Certificate cert : certificates) {
            String key = cert.getSerialNumber().toString() + "|" + cert.getIssuerX500Principal().getName();
            uniqueCerts.put(key, cert);
        }

        return new ArrayList<>(uniqueCerts.values());
    }

    private MasterListInfo createMasterListInfo(String countryCode, int certificateCount, boolean signatureValid) {
        return new MasterListInfo(countryCode, certificateCount, signatureValid, System.currentTimeMillis());
    }

    private static class SignatureValidationResult {
        private final boolean valid;
        private final String errorMessage;

        public SignatureValidationResult(boolean valid, String errorMessage) {
            this.valid = valid;
            this.errorMessage = errorMessage;
        }

        public boolean isValid() {
            return valid;
        }

        public String getErrorMessage() {
            return errorMessage;
        }
    }
}
