package com.smartcoreinc.localpkd.icaomasterlist.service;

import java.io.FileInputStream;
import java.io.InputStream;
import java.security.Security;
import java.security.cert.CertPathBuilder;
import java.security.cert.CertPathBuilderResult;
import java.security.cert.CertificateFactory;
import java.security.cert.PKIXBuilderParameters;
import java.security.cert.TrustAnchor;
import java.security.cert.X509CertSelector;
import java.security.cert.X509Certificate;
import java.text.SimpleDateFormat;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;
import org.bouncycastle.cert.X509CertificateHolder;
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
import org.bouncycastle.cms.CMSSignedData;
import org.bouncycastle.cms.SignerId;
import org.bouncycastle.cms.SignerInformation;
import org.bouncycastle.cms.SignerInformationStore;
import org.bouncycastle.cms.SignerInformationVerifier;
import org.bouncycastle.cms.jcajce.JcaSimpleSignerInfoVerifierBuilder;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.util.Store;
import lombok.extern.slf4j.Slf4j;

/**
 * ICAO Master List Signer Cerificate Trust Chain Verifier
 */
@Slf4j
public class ICAOMasterListVerifier {
    static {
        //BouncyCastle 등록 (JVM 전역 1회)
        if (Security.getProperty("BC") == null) {
            Security.addProvider(new BouncyCastleProvider());
        }
    }

    private final Set<X509Certificate> trustAnchors = new HashSet<>();

    public ICAOMasterListVerifier(String trustAnchorPemPath) throws Exception {
        try {
            loadTrustAnchor(trustAnchorPemPath);
        } catch (Exception e) {
            log.error("ICAOMasterListVerifier 생성자 오류: {}", e.getMessage());
            throw new RuntimeException(e);
        }
    }

    /**
     * Master List (ML) 서명 검증
     */
    public boolean verify(byte[] mlData) throws Exception {
        log.info("Starting Master List verification...");

        CMSSignedData signedData = new CMSSignedData(mlData);
        return verify(signedData);
        
    }

    /**
     * Master List Signer 인증서들을 추출하여 
     * @param signedData
     * @return
     * @throws Exception
     */
    public boolean verify(CMSSignedData signedData) throws Exception {
        log.info("Starting Master List verification ...");

        Store<X509CertificateHolder> certStore = signedData.getCertificates();
        SignerInformationStore signerInfos = signedData.getSignerInfos();

        for (SignerInformation signer : signerInfos.getSigners()) {
            SignerId sid = signer.getSID();
            @SuppressWarnings("unchecked")
            Collection<X509CertificateHolder> certCollection = certStore.getMatches(sid);
            if (certCollection.isEmpty()) {
                log.warn("No certificate found in Master List for signer {}", sid.toString());
                continue;
            }
            X509CertificateHolder holder = certCollection.iterator().next();
            // JCA X.509 인증서로 변환
            X509Certificate signerCert = new JcaX509CertificateConverter()
                    .setProvider("BC")
                    .getCertificate(holder);

            log.info("Signer certificate: Subject={}, Issuer={}",
                    signerCert.getSubjectX500Principal(),
                    signerCert.getIssuerX500Principal());

            // 1) Signer 서명 검증
            SignerInformationVerifier siv = new JcaSimpleSignerInfoVerifierBuilder()
                            .setProvider("BC")
                            .build(holder);
            boolean sigValid = signer.verify(siv);
            if (!sigValid) {
                log.error("❌ Signature verification failed for signer {}", sid.toString());
                return false;
            }
            log.info("✅ Signature verification successful for signer {}", sid.toString());

            // 2) Trust Anchor 체인 검증
            if (!isCertTrusted(signerCert)) {
                log.error("❌ Signer certificate is NOT trusted (no valid path to Trust Anchor).");
                return false;
            }
            log.info("✅ Signer certificate is trusted via Trust Anchor.");
        }
        
        log.info("✅ Master List verification completed successfully.");
        
        return true;
    }

    /**
     * ICAO/UN Trust Anchor 인증서 PEM 파일을 X509 Certificate 객체로 변환하여
     * Trust Anchor Collection(Set)에 저장한다.
     */
    private void loadTrustAnchor(String pemPath) throws Exception {
        try (InputStream is = new FileInputStream(pemPath)) {
            // JCA X.509 인증서로 변환
            CertificateFactory certFactory = CertificateFactory.getInstance("X.509", "BC");
            X509Certificate cert = (X509Certificate) certFactory.generateCertificate(is);
            // 유효일 검사
            cert.checkValidity();
            // Trust Anchor 컨테이너에 저장
            trustAnchors.add(cert);

            // Debug Message
            SimpleDateFormat sdFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
            log.debug(
                "Loaded Certificate to Trust Anchor: Subject={}, Serial={}, NotBefore={}, NotAfter={}",
                cert.getSubjectX500Principal(),
                cert.getIssuerX500Principal(),
                cert.getSerialNumber(),
                sdFormat.format(cert.getNotBefore()),
                sdFormat.format(cert.getNotAfter())
            );
        }
    }

    private boolean isCertTrusted(X509Certificate cert) {
        try {
            X509CertSelector selector = new X509CertSelector();
            selector.setCertificate(cert);

            Set<TrustAnchor> anchorSet = new HashSet<>();
            for (X509Certificate ta : trustAnchors) {
                anchorSet.add(new TrustAnchor(ta, null));
            }

            PKIXBuilderParameters params = new PKIXBuilderParameters(anchorSet, selector);
            params.setRevocationEnabled(false); // PKD는 보통 CRL/OCSP 체크 필요 없음
            CertPathBuilder builder = CertPathBuilder.getInstance("PKIX", "BC");
            CertPathBuilderResult result = builder.build(params);

            log.debug("CertPath built successfully: {}", result);
            return true;
        } catch (Exception e) {
            log.warn("CertPath validation failed: {}", e.getMessage());
            return false;
        }
    }
}
