package com.smartcoreinc.localpkd.icao.service;

import java.security.MessageDigest;
import java.security.cert.X509Certificate;
import java.text.SimpleDateFormat;
import java.util.HexFormat;

import org.springframework.stereotype.Service;

import com.unboundid.ldap.sdk.Entry;
import com.unboundid.ldap.sdk.LDAPConnectionPool;
import com.unboundid.ldap.sdk.LDAPException;
import com.unboundid.ldap.sdk.ResultCode;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
public class CscaLdapAddService {
    private static final String BASE_DN = "dc=ldap,dc=smartcoreinc,dc=com";
    
    private final LDAPConnectionPool writePool;
    // private final CscaCertificateValidator cscaCertificateValidator;

    public CscaLdapAddService(LDAPConnectionPool writePool) {
        this.writePool = writePool;
        // this.cscaCertificateValidator = cscaCertificateValidator;
    }

    /**
     * 국가별 CSCA 인증서를 국가 코드 OU로 구분하여 저장
     */
    public String saveCscaCertificate(X509Certificate x509Cert) {
        String result = "";
        ensureCSCAOrganizationalUnitsExist();
        try {
            // X.509 인증서 내의 Subjec 필드에서 국가 코드 추출 
            String countryCode = x509Cert.getSubjectX500Principal().getName("RFC2253")
                .replaceAll(".*C=([a-zA-Z]{2}).*", "$1");
            log.debug("X.509 인증서 내의 Subject에서 추출한 국가 코드: {}", countryCode);
            
            // 국가 코드별 하위 OU 없으면 추가
            ensureCountryOrganizationalUnitsExist(countryCode);
            
            String cscaDn = "ou=CSCA," + BASE_DN;
            String countryOu = "ou=" + countryCode + "," + cscaDn;
            String fingerprint = getCertificateFingerprint(x509Cert);
            String cnValue = String.format("CSCA-%s-%s", countryCode, fingerprint);
            String dn = String.format("cn=%s,%s", cnValue, countryOu);
            
            String serialNumber = x509Cert.getSerialNumber().toString();
            String notBefore = new SimpleDateFormat("yyyy/MM/dd").format(x509Cert.getNotBefore());
            String notAfter = new SimpleDateFormat("yyyy/MM/dd").format(x509Cert.getNotAfter());


            Entry entry = new Entry(dn);
            // 필수 objectClass
            entry.addAttribute("objectClass", "top", "inetOrgPerson", "pkiCA");
            // 필수 objectClass
            entry.addAttribute("cn", cnValue);
            entry.addAttribute("sn", serialNumber);
            entry.addAttribute("cACertificate;binary", x509Cert.getEncoded());

            // 추가 메타 정보
            // entry.addAttribute("x509Subject", x509Cert.getSubjectX500Principal().getName());
            // entry.addAttribute("x509Issuer", x509Cert.getIssuerX500Principal().getName());
            // entry.addAttribute("x509ValidityNotBefore", x509Cert.getNotBefore().toString());
            // entry.addAttribute("x509ValidityNotAfter", x509Cert.getNotAfter().toString());
            entry.addAttribute("description", "valid from " + notBefore + " to " + notAfter);

            writePool.add(entry);
            log.debug("[{}] CSCA 인증서 저장 성공", dn);

        } catch (LDAPException e) {
            log.error("CSCA Certificate LDAP 저장 오류: {}, {}, {}", e, x509Cert.getSubjectX500Principal().getName(), x509Cert.getSerialNumber());
        } catch (Exception e) {
            log.error("CSCA 인증서 저장 실패", e);
        }
        return result;
    }

    private void ensureCSCAOrganizationalUnitsExist() {
        try {
            // 최상위 OU: CSCA
            String cscaDn = "ou=CSCA," + BASE_DN;

            if (writePool.getEntry(cscaDn) == null) {
                Entry cscaEntry = new Entry(cscaDn);
                cscaEntry.addAttribute("objectClass", "top", "organizationalUnit");
                cscaEntry.addAttribute("ou", "CSCA");
                writePool.add(cscaEntry);
                log.info("CSCA OU 생성: {}", cscaDn);
            }
        } catch (LDAPException e) {
            if (e.getResultCode() == ResultCode.ENTRY_ALREADY_EXISTS) {
                log.warn("이미 존재하는 항목: {}", e.getMessage());
            } else {
                log.error("ADD CSCA OU 오류: {}", e.getResultString());
            }
        }
    }

    private void ensureCountryOrganizationalUnitsExist(String countryCode) {
        try {
            // 최상위 OU: CSCA
            String countryDn = "ou=" + countryCode + ",ou=CSCA," + BASE_DN;

            if (writePool.getEntry(countryDn) == null) {
                Entry cscaEntry = new Entry(countryDn);
                cscaEntry.addAttribute("objectClass", "top", "organizationalUnit");
                cscaEntry.addAttribute("ou", countryCode);
                writePool.add(cscaEntry);
                log.info("Country OU 생성: {}", countryDn);
            }
        } catch (LDAPException e) {
            if (e.getResultCode() == ResultCode.ENTRY_ALREADY_EXISTS) {
                log.warn("이미 존재하는 항목: {}", e.getMessage());
            } else {
                log.error("ADD Country OU 저장 오류 {}, {}", countryCode, e.getResultString());
            }
        }
    }

    private String getCertificateFingerprint(X509Certificate cert) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-1");
        byte[] encoded = cert.getEncoded();
        byte[] fingerprint = digest.digest(encoded);

        return HexFormat.of().formatHex(fingerprint);
    }
}
