package com.smartcoreinc.localpkd;

import java.security.cert.CertPath;
import java.security.cert.CertPathValidator;
import java.security.cert.CertificateFactory;
import java.security.cert.PKIXParameters;
import java.security.cert.TrustAnchor;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.bouncycastle.cert.X509CertificateHolder;
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
import org.bouncycastle.cms.CMSSignedData;
import org.bouncycastle.cms.SignerInformation;
import org.bouncycastle.cms.SignerInformationStore;
import org.bouncycastle.cms.jcajce.JcaSimpleSignerInfoVerifierBuilder;
import org.bouncycastle.util.Store;
import org.springframework.stereotype.Service;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
public class CertificateVerificationService {

    /**
     * Document Signer Certificate 검증
     *
     * @param dscCert      Document Signer Certificate
     * @param masterList   ICAO Master List (DER encoded CMS SignedData)
     * @param trustAnchor  ICAO Trust Anchor certificate
     * @return true → 검증 성공, false → 검증 실패
     */
    public boolean verifyDocumentSignerCertificate(
        X509Certificate dscCert,
        byte[] masterlist,
        X509Certificate trustAnchor) {
        try {
            // 1. Master List Signature 검증 (ICAO Trust Anchor 사용)
            CMSSignedData signedData = new CMSSignedData(masterlist);
            Store<X509CertificateHolder> certStore = signedData.getCertificates();
            SignerInformationStore signers = signedData.getSignerInfos();

            boolean masterListVerified = false;
            for (SignerInformation signer : signers.getSigners()) {
                // Collection<X509CertificateHolder> certCollection = certStore.getMatches(signer.getSID());
                // X509CertificateHolder certHolder = certCollection.iterator().next();
                // X509Certificate signerCert = new JcaX509CertificateConverter()
                //     .getCertificate(certHolder);

                // 서명 검증
                if (signer.verify(new JcaSimpleSignerInfoVerifierBuilder().build(trustAnchor))) {
                    masterListVerified = true;
                    break;
                }
            }

            if (!masterListVerified) {
                log.error("Master List 서명 검증 실패");
                return false;
            }
            log.info("Master List 서명 검증 성공.");

            // 2. Master List에서 CSCA 인증서 추출
            List<X509Certificate> cscaCerts = extractCscaCerts(certStore);

            // 3. Document Signer Certificate -> CSCA 체인 검증
            return validateCertificatePath(dscCert, cscaCerts);
        } catch (Exception e) {
            log.error("DSC 인증서 체인 검증 실패: {}", e.getMessage());
            return false;
        }
    }

    /**
     * Master List에서 CSCA 인증서 리스트 컨테이너 추출
     */
    private List<X509Certificate> extractCscaCerts(Store<X509CertificateHolder> certStore) throws Exception {
        JcaX509CertificateConverter converter = new JcaX509CertificateConverter();
        List<X509Certificate> certs = new ArrayList<>();
        for (Object obj : certStore.getMatches(null)) {
            X509CertificateHolder holder = (X509CertificateHolder) obj;
            X509Certificate cert = converter.getCertificate(holder);

            // CSCA 인증서 여부 판단 필터 (Self-signed, BasicConstraints: CA=true)
            if (cert.getBasicConstraints() != -1) {
                certs.add(cert);
            }
        }
        log.info("CSCA 인증서[{} 개] 추출 완료", certs.size());
        return certs;
    }

     /**
     * DSC → CSCA → 신뢰 경로 검증
     */
    private boolean validateCertificatePath(X509Certificate dscCert, List<X509Certificate> cscaCerts) {
        try {
            CertificateFactory certFactory = CertificateFactory.getInstance("X.509");
            CertPath certPath = certFactory.generateCertPath(Collections.singletonList(dscCert));
    
            Set<TrustAnchor> trustAnchors = cscaCerts.stream()
                .map(cert -> new TrustAnchor(cert, null))
                .collect(Collectors.toSet());
    
            PKIXParameters params = new PKIXParameters(trustAnchors);
            params.setRevocationEnabled(false);
    
            CertPathValidator validator = CertPathValidator.getInstance("PKIX");
            validator.validate(certPath, params);
    
            log.info("DSC 인증서 체인 검증 성공");
            return true;
        } catch (Exception e) {
            log.error("DSC 인증서 체인 검증 실패: ", e.getMessage());
            return false;
        }
    }
}
