package com.smartcoreinc.localpkd.icao.service;

import java.security.cert.X509Certificate;
import java.text.SimpleDateFormat;

import org.springframework.stereotype.Service;

import com.unboundid.ldap.sdk.Entry;
import com.unboundid.ldap.sdk.LDAPConnectionPool;
import com.unboundid.ldap.sdk.LDAPException;
import com.unboundid.ldap.sdk.ResultCode;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
public class CscaLdapAddServiceWithValidity {

    private static final String BASE_DN = "dc=ldap,dc=smartcoreinc,dc=com";
    
    private final LDAPConnectionPool writePool;
    private final CscaCertificateValidator cscaCertificateValidator;

    public CscaLdapAddServiceWithValidity(LDAPConnectionPool writePool, CscaCertificateValidator cscaCertificateValidator) {
        this.writePool = writePool;
        this.cscaCertificateValidator = cscaCertificateValidator;
    }

    public String saveCscaCertificate(X509Certificate x509Cert) {
        String result = "";
        ensureOrganizationalUnitsExist();
        try {
            if (!cscaCertificateValidator.isCertificateValid(x509Cert)) {
                log.warn("무효한 인증서: {}", x509Cert.getSubjectX500Principal());
                saveInvalidCsca(x509Cert);
                result = String.format("무효한 인증서: %s", x509Cert.getSubjectX500Principal());
            } else {
                log.debug("유효한 인증서: {}", x509Cert.getSubjectX500Principal());
                saveValidCsca(x509Cert);
                result = String.format("유효한 인증서: %s", x509Cert.getSubjectX500Principal());
            }
        } catch (Exception e) {
            log.error("CSCA 인증서 등록 중 오류 발생: {}", e.getMessage());
        }
        return result;
    }

    /**
     * 유효한 CSCA 인증서를 저장
     */
    public void saveValidCsca(X509Certificate cert) {
        saveCscaCertificate(cert, "valid");
    }

    /**
     * 무효한 CSCA 인증서를 저장
     */
    public void saveInvalidCsca(X509Certificate cert) {
        saveCscaCertificate(cert, "invalid");
    }


    /**
     * 국가별 CSCA 인증서를 유효/무효 OU로 구분하여 저장
     */
    private void saveCscaCertificate(X509Certificate cert, String validity) {
        try {
            String cscaDn = "ou=CSCA," + BASE_DN;
            
            String countryCode = cert.getSubjectX500Principal().getName("RFC2253")
                    .replaceAll(".*C=([A-Z]{2}).*", "$1");
            
            // 국가 코드별 하위 OU 없으면 추가
            if (countryCode == "ro") {
                System.out.println("country code: " + countryCode);
            }
            String countryOu = "ou=" + countryCode + ",ou=" + validity + "," + cscaDn;
            if (writePool.getEntry(countryOu) == null) {
                Entry countryEntry = new Entry(countryOu);
                countryEntry.addAttribute("objectClass", "top", "organizationalUnit");
                countryEntry.addAttribute("ou", countryCode);
                writePool.add(countryEntry);
                log.info("국가 OU 생성: {}", countryOu);
            }

            String serial = cert.getSerialNumber().toString(16).toUpperCase();
            String cnValue = String.format("CSCA-%s-%s", countryCode, serial);
            String dn = String.format("cn=%s,%s", cnValue, countryOu);

            String notBefore = new SimpleDateFormat("yyyyMMdd").format(cert.getNotBefore());
            String notAfter = new SimpleDateFormat("yyyyMMdd").format(cert.getNotAfter());


            Entry entry = new Entry(dn);
            entry.addAttribute("objectClass", "top", "inetOrgPerson", "pkiCA");
            entry.addAttribute("cn", cnValue);
            entry.addAttribute("sn", cert.getIssuerX500Principal().getName());
            entry.addAttribute("description", "valid from " + notBefore + " to " + notAfter);
            entry.addAttribute("cACertificate;binary", cert.getEncoded());

            writePool.add(entry);
            log.info("[{}] CSCA 저장 성공: {}", validity.toUpperCase(), dn);

        } catch (LDAPException e) {
            if (e.getResultCode() == ResultCode.ENTRY_ALREADY_EXISTS) {
                log.warn("이미 존재하는 항목: {}", e.getMessage());
            } else {
                log.error("LDAP 저장 오류", e);
            }
            try {
                Thread.sleep(5000);
            } catch (InterruptedException ie) {
                throw new RuntimeException(ie);
            }
        } catch (Exception e) {
            log.error("인증서 저장 실패", e);
            try {
                Thread.sleep(5000);
            } catch (InterruptedException ie) {
                throw new RuntimeException(ie);
            }
        }
    }

    private void ensureOrganizationalUnitsExist() {
        try {
            // 최상위 OU: CSCA
            String cscaDn = "ou=CSCA," + BASE_DN;

            if (writePool.getEntry(cscaDn) == null) {
                Entry cscaEntry = new Entry(cscaDn);
                cscaEntry.addAttribute("objectClass", "top", "organizationalUnit");
                cscaEntry.addAttribute("ou", "CSCA");
                writePool.add(cscaEntry);
                log.info("OU 생성: {}", cscaDn);
            }

            // 하위 OU: valid, invalid
            String[] subOus = {"valid", "invalid"};
            for (String ou : subOus) {
                String subDn = "ou=" + ou + "," + cscaDn;
                if (writePool.getEntry(subDn) == null) {
                    Entry subEntry = new Entry(subDn);
                    subEntry.addAttribute("objectClass", "top", "organizationalUnit");
                    subEntry.addAttribute("ou", ou);
                    writePool.add(subEntry);
                    log.info("OU 생성: {}", subDn);
                }
            }
        } catch (LDAPException e) {
            log.error("OU 생성 중 오류 발생", e);
            try {
                Thread.sleep(5000);
            } catch (InterruptedException ie) {
                throw new RuntimeException(ie);
            }
        }
    }
}
