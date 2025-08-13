package com.smartcoreinc.localpkd.icaomasterlist.service;

import java.security.Security;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

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

import com.smartcoreinc.localpkd.icaomasterlist.entity.CscaCertificate;
import com.smartcoreinc.localpkd.sse.Progress;
import com.smartcoreinc.localpkd.sse.ProgressEvent;
import com.smartcoreinc.localpkd.sse.ProgressListener;
import com.smartcoreinc.localpkd.sse.ProgressPublisher;
import com.smartcoreinc.localpkd.validator.X509CertificateValidator;

import lombok.extern.slf4j.Slf4j;

/**
 * ICAO Master List (회원국들의 CSCA Master List) 파셔
 */
@Slf4j
@Service
public class CscaMasterListParser {
    static {
        Security.addProvider(new BouncyCastleProvider());
    }

    // Dependencies
    private final X509CertificateValidator certificateValidator;
    private final CscaLdapAddService cscaCertificateService;
    // SSE publisher(파싱 진행 상태 정보 생성자)
    private final ProgressPublisher progressPublisher;

    private int numberOfCertsTotal;
    private int numberOfCertsParsered;
    private Map<String, Integer> cscaCountByCountry = new HashMap<>();
    private List<X509Certificate> validCerts = new ArrayList<>();
    private List<X509Certificate> invalidCerts = new ArrayList<>();


    public CscaMasterListParser(X509CertificateValidator cscaCertificateValidator,
                                CscaLdapAddService cscaCertificateService,
                                ProgressPublisher progressPublisher) {
        this.certificateValidator = cscaCertificateValidator;
        this.cscaCertificateService = cscaCertificateService;
        this.progressPublisher = progressPublisher;
    }

    public Map<String, Integer> getCscaCountByCountry() {
        return cscaCountByCountry;
    }

    public List<X509Certificate> getValidCertificates() {
        return validCerts;
    }

    public List<X509Certificate> getInvalidCertificates() {
        return invalidCerts;
    }

    /**
     * ICAO Master List 파일의 내용(byte array)을 분석하고 개별 CSCA 인증서를
     * CscaLdapAddService에 전달하여 LDAP(local PKD)등록한다.
     * 
     * @param byte[] data
     * @param boolean isAddLdap : 디버깅 용으로 이 플래그 값을 false 로 하면 ldap에 저장하지 않음.
     * @return List<X509Certificate> : 파싱 결과, 유효한 CSCA 인증서 들만 리턴
     * @throws Exception
     */
    public List<X509Certificate> parseMasterList(byte[] data, boolean isAddLdap) throws Exception {
        // X.509 인증서 Converter Provider를 Bouncy Castle로 지정 
        JcaX509CertificateConverter converter = new JcaX509CertificateConverter().setProvider("BC");
        
        // PKCS7-signature message 처리 클래스 생성
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

        for (ASN1Encodable encodable : certSet) {
            Certificate bcCert = Certificate.getInstance(encodable);
            X509CertificateHolder holder = new X509CertificateHolder(bcCert);

            X509Certificate x509Cert = converter.getCertificate(holder);
            // CSCA(X.509)인증서 검증 후 클래스 내부 멤버 변수에 저장.
            boolean isValid = certificateValidator.isCertificateValid(x509Cert);
            if (isValid) {
                validCerts.add(x509Cert);
            } else {
                invalidCerts.add(x509Cert);
            }

            String subject = x509Cert.getSubjectX500Principal().getName();
            String country = extractCountryCode(subject);
            if (!cscaCountByCountry.containsKey(country)) {
                cscaCountByCountry.put(country, 1);
            } else {
                cscaCountByCountry.replace(country, cscaCountByCountry.get(country) + 1);
            }
            // cscaCountByCountry.forEach((key, value) -> log.debug("key: {}, value: {}", key, value));
            
            // TODO: 개발 완료 시 아래 코드 수정할 것 
            // LDAP 저장 플래그 여부에 따라 실행
            if (isAddLdap) {
                // LDAP Entry description attribute 값 설정
                String valid = "";
                if (isValid) {
                    valid = "Valid";
                } else {
                    valid = "Invalid";
                }
                CscaCertificate cscaCertificate = cscaCertificateService.save(x509Cert, valid);
                log.debug("Added DN: {}", cscaCertificate.getDn().toString());
            }
            
            numberOfCertsParsered += 1;

            // 0.01 초 간 sleep 
            sleepQuietly(10);

            // SSE Emitter에 전달
            Progress progress = new Progress(numberOfCertsParsered/ (double) numberOfCertsTotal);
            ProgressEvent progressEvent = new ProgressEvent(progress, numberOfCertsParsered, numberOfCertsTotal, subject);
            progressPublisher.notifyProgressListeners(progressEvent);
        }

        log.debug("the count of valid certs: ", validCerts.size());
        log.debug("the count of invalid certs", invalidCerts.size());

        asn1In.close();
        // 유효한 인증서들만 리턴 
        return validCerts;
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
            @SuppressWarnings("unchecked")
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

    private static void sleepQuietly(int ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

}
