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
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.bouncycastle.cert.X509CertificateHolder;
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
import org.bouncycastle.cms.CMSSignedData;
import org.bouncycastle.cms.SignerId;
import org.bouncycastle.cms.SignerInformation;
import org.bouncycastle.cms.SignerInformationStore;
import org.bouncycastle.cms.jcajce.JcaSimpleSignerInfoVerifierBuilder;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.util.Store;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class MasterListVerifier {
    static {
        //BouncyCastle 등록 (JVM 전역 1회)
        if (Security.getProperty("BC") == null) {
            Security.addProvider(new BouncyCastleProvider());
        }
    }

    /** 검증 옵션 */
    public static class VerifyOptions {
        /** 신뢰 루트(TrustAnchor) 세트. (ICAO/UN 루트/중간 등 신뢰 앵커) */
        public Set<TrustAnchor> trustAnchors = new HashSet<>();
        /** 허용 정책 OID (예: ICAO/UN Master List 정책 OID들). 비워두면 정책 강제 미적용 */
        public Set<String> requiredPolicyOids = new HashSet<>();
        /** 정책 한정자 거부 여부 (일반적으로 false 권장) */
        public boolean rejectPolicyQualifiers = false;
        /** CRL/OCSP 사용 여부 (기본: false) */
        public boolean enableRevocation = false;
        /** OCSP soft-fail(네트워크 실패 시 통과) 여부 */
        public boolean ocspSoftFail = true;
        /** 검증 기준시각 (기본: now) */
        public Date validationTime = Date.from(Instant.now());
        /** 금지 해시/서명 알고리즘 (예: SHA1 등) */
        public Set<String> bannedSigAlgs = Set.of("MD5withRSA", "SHA1withRSA", "SHA1withECDSA");
        /** 서명시간(signingTime) 사용 시, 인증서 유효기간 안에 있어야 하는지 */
        public boolean requiredSigningTimeInsideCertValidity = true;
    }

    /** 서명자별 검증 결과 */
    public static class SingerReport {
        public SignerId signerId;
        public X509Certificate signerCert;
        public boolean signatureValid;
        public boolean pathValid;
        public String policyMatched;    // 매칭된 정책 OID (있다면)
        public List<String> notes = new ArrayList<>();
    }

    /** 전체 결과 */
    public static class VerificationResult {
        public byte[] eContent; // 포함 서명인 경우 원문
        public List<SingerReport> singerReports = new ArrayList<>();
        public boolean allSignaturesValid() {
            return singerReports.stream().allMatch(r -> r.signatureValid);
        }
        public boolean allPathValid() {
            return singerReports.stream().allMatch(r -> r.pathValid);
        }
    }

    private final Set<X509Certificate> trustAnchors = new HashSet<>();

    public MasterListVerifier(String trustAnchorPemPath) throws Exception {
        loadTrustAnchor(trustAnchorPemPath);
    }

    /**
     * Master List (ML) 서명 검증
     */
    public boolean verify(byte[] mlData) throws Exception {
        log.info("Starting Master List verification...");

        CMSSignedData signedData = new CMSSignedData(mlData);
        Store<X509CertificateHolder> certStore = signedData.getCertificates();
        SignerInformationStore signerInfos = signedData.getSignerInfos();

        for (SignerInformation signer : signerInfos.getSigners()) {
            log.info("Checking signer with SID: {}", signer.getSID());

            @SuppressWarnings("unchecked")
            Collection<X509CertificateHolder> certCollection = certStore.getMatches(signer.getSID());
            if (certCollection.isEmpty()) {
                log.warn("No certificate found in Master List for signer {}", signer.getSID());
                continue;
            }

            X509CertificateHolder holder = certCollection.iterator().next();
            X509Certificate signerCert = new JcaX509CertificateConverter()
                    .setProvider("BC")
                    .getCertificate(holder);

            log.info("Signer certificate: Subject={}, Issuer={}",
                    signerCert.getSubjectX500Principal(),
                    signerCert.getIssuerX500Principal());

            // 서명 검증
            boolean sigValid = signer.verify(
                    new JcaSimpleSignerInfoVerifierBuilder()
                            .setProvider("BC")
                            .build(holder)
            );

            if (!sigValid) {
                log.error("❌ Signature verification failed for signer {}", signer.getSID());
                return false;
            }
            log.info("✅ Signature verification successful for signer {}", signer.getSID());

            // Trust Anchor 체인 검증
            if (!isCertTrusted(signerCert)) {
                log.error("❌ Signer certificate is NOT trusted (no valid path to Trust Anchor).");
                return false;
            }
            log.info("✅ Signer certificate is trusted via Trust Anchor.");
        }

        log.info("✅ Master List verification completed successfully.");
        return true;
    }

    private void loadTrustAnchor(String pemPath) throws Exception {
        try (InputStream in = new FileInputStream(pemPath)) {
            CertificateFactory certFactory = CertificateFactory.getInstance("X.509", "BC");
            X509Certificate cert = (X509Certificate) certFactory.generateCertificate(in);
            trustAnchors.add(cert);
            log.info("Loaded Trust Anchor: Subject={}, Issuer={}, Serial={}",
                    cert.getSubjectX500Principal(),
                    cert.getIssuerX500Principal(),
                    cert.getSerialNumber());
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
