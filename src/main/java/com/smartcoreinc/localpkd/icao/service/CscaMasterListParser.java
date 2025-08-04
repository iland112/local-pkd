package com.smartcoreinc.localpkd.icao.service;

import java.security.Security;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.naming.InvalidNameException;
import javax.naming.ldap.LdapName;
import javax.naming.ldap.Rdn;

import org.bouncycastle.asn1.ASN1Encodable;
import org.bouncycastle.asn1.ASN1InputStream;
import org.bouncycastle.asn1.ASN1Sequence;
import org.bouncycastle.asn1.ASN1Set;
import org.bouncycastle.asn1.x509.Certificate;
import org.bouncycastle.cert.X509CertificateHolder;
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
import org.bouncycastle.cms.CMSProcessable;
import org.bouncycastle.cms.CMSSignedData;
import org.bouncycastle.cms.SignerInformation;
import org.bouncycastle.cms.SignerInformationStore;
import org.bouncycastle.cms.jcajce.JcaSimpleSignerInfoVerifierBuilder;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.util.Store;
import org.springframework.stereotype.Service;

import com.smartcoreinc.localpkd.icao.entity.CscaCertificate;
import com.smartcoreinc.localpkd.icao.sse.Progress;
import com.smartcoreinc.localpkd.icao.sse.ProgressListener;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
public class CscaMasterListParser {
    static {
        Security.addProvider(new BouncyCastleProvider());
    }

    private final Set<ProgressListener> progressListeners = new HashSet<>();
    private final CscaLdapAddService ldapService;
    private final CscaCertificateService cscaCertificateService;

    private int numberOfCertsTotal;
    private int numberOfCertsParsered;
    private Map<String, Integer> cscaCountByCountry = new HashMap<>();

    public CscaMasterListParser(CscaLdapAddService ldapService, CscaCertificateService cscaCertificateService) {
        this.ldapService = ldapService;
        this.cscaCertificateService = cscaCertificateService;
    }

    public Map<String, Integer> getCscaCountByCountry() {
        return cscaCountByCountry;
    }

    public List<X509Certificate> parseMasterList(byte[] data, boolean isAddLdap) throws Exception {
        JcaX509CertificateConverter converter = new JcaX509CertificateConverter().setProvider("BC");
        
        CMSSignedData signedData = new CMSSignedData(data);

        // ICAO/UN 서명자 인증서 검증
        verifyBySignerCerts(signedData);
        
        // CSCA Master list 데이터 추출
        CMSProcessable signedContent = signedData.getSignedContent();
        byte[] contentBytes = (byte[]) signedContent.getContent();
        
        // ASN.1 SET OF Certificate 추출
        ASN1InputStream asn1In = new ASN1InputStream(contentBytes);
        ASN1Sequence masterListSeq = (ASN1Sequence) asn1In.readObject();
        ASN1Set certSet = (ASN1Set) masterListSeq.getObjectAt(1);
        numberOfCertsTotal = certSet.size();

        List<X509Certificate> x509Certs = new ArrayList<>();
        for (ASN1Encodable encodable : certSet) {
            Certificate bcCert = Certificate.getInstance(encodable);
            X509CertificateHolder holder = new X509CertificateHolder(bcCert);

            X509Certificate x509Cert = converter.getCertificate(holder);
            x509Certs.add(x509Cert);

            String subject = x509Cert.getSubjectX500Principal().toString();
            String country = extractCountryCode(subject);
            if (!cscaCountByCountry.containsKey(country)) {
                cscaCountByCountry.put(country, 1);
            } else {
                cscaCountByCountry.replace(country, cscaCountByCountry.get(country) + 1);
            }
            // cscaCountByCountry.forEach((key, value) -> log.debug("key: {}, value: {}", key, value));
            
            String resultOfAddLdap = null;
            if (isAddLdap) {
                resultOfAddLdap = ldapService.saveCscaCertificate(x509Cert);
                // CscaCertificate cscaCertificate = cscaCertificateService.save(x509Cert);
            }

            sleepQuietly(10);
            numberOfCertsParsered += 1;
            notifyProgressListeners(subject);
        }
        asn1In.close();
        return x509Certs;
    }

    /**
     * ICAO/UN 서명자 인증서 검증
     * 
     * @param signedData: CMSSingedData
     * @throws Exception
     */
    private void verifyBySignerCerts(CMSSignedData signedData) throws Exception {
        // 서명자 인증서 검증
        log.debug("서명자 검증 시작!");
        SignerInformationStore signers = signedData.getSignerInfos();
        Store<X509CertificateHolder> certStore = signedData.getCertificates();
        for (SignerInformation signer : signers.getSigners()) {
            X509CertificateHolder signerCert = (X509CertificateHolder) certStore.getMatches(signer.getSID()).iterator().next();
            boolean valid = signer.verify(new JcaSimpleSignerInfoVerifierBuilder().build(signerCert));
            if (!valid) {
                log.error("서명자 {} 검증 실패", signer.getSID().toString());
                throw new SecurityException("서명자 검증 실패");
            }
        }
        log.debug("서명자 검증 성공!!");
    }

    private String extractCountryCode(String dn) {
        for (String part : dn.split(",")) {
            if (part.trim().startsWith("C=")) {
                return part.trim().substring(2).toUpperCase();
            }
        }
        return "UNKNOWN";
    }

    private void extractFieldFromDN(String dn) {
        try {
            LdapName ldapName = new LdapName(dn);
            List<Rdn> rnds = ldapName.getRdns();
            rnds.forEach(rdn -> log.debug("type: {}, value: {}, class: {}", rdn.getType(), rdn.getValue().toString(), rdn.getClass().getName()));
        } catch (InvalidNameException e) {
            log.error("유효하지 않은 DN 형식압니다: ", e.getMessage());
        }
    }

    public void addProgressListener(ProgressListener progressListener) {
        progressListeners.add(progressListener);
    }

    public void removeProgressListener(ProgressListener progressListener) {
        progressListeners.remove(progressListener);
    }

    private static void sleepQuietly(int ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

    private void notifyProgressListeners(String subject) {
        for (ProgressListener progressListener : progressListeners) {
            Progress progress = new Progress(numberOfCertsParsered / (double) numberOfCertsTotal);
            log.debug("current progress: {}%", (int) (progress.value() * 100));
            progressListener.onProgress(progress, cscaCountByCountry, "Processing Subject: " + subject);
        }
    }
}
