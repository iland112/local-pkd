package com.smartcoreinc.localpkd.icao.service;

import java.security.MessageDigest;
import java.security.cert.X509Certificate;
import java.util.HexFormat;

import javax.naming.InvalidNameException;
import javax.naming.Name;
import javax.naming.ldap.LdapName;

import org.springframework.ldap.core.DirContextAdapter;
import org.springframework.ldap.core.LdapTemplate;
import org.springframework.ldap.support.LdapNameBuilder;
import org.springframework.stereotype.Service;

import com.smartcoreinc.localpkd.icao.entity.CscaCertificate;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
public class CscaLdapAddService {

    private final LdapTemplate ldapTemplate;

    public CscaLdapAddService(LdapTemplate ldapTemplate) {
        this.ldapTemplate = ldapTemplate;
    }

    public CscaCertificate save(X509Certificate certificate) {
        CscaCertificate cscaCertificate = new CscaCertificate();
        try {
            createParentIfNotFound("ou=CSCA");
            String subject = certificate.getSubjectX500Principal().getName();
            String countryCode = extractCountryCode(subject);
            // String cscaDn = "ou=CSCA,dc=ldap,dc=smartcoreinc,dc=com";
            // String countryOu = "ou=" + countryCode + "," + cscaDn;
            String fingerprint = getCertificateFingerprint(certificate);
            String cnValue = String.format("CSCA-%s-%s", countryCode, fingerprint);
            // String dn = String.format("cn=%s,%s", cnValue, cscaDn);
            
            // 1. DN을 직접 구성합니다.
            Name dn = LdapNameBuilder.newInstance("ou=CSCA")
                                     .add("cn", cnValue)
                                     .build();

            // 2. DirContextAdapter를 사용하여 항목의 속성을 설정합니다.
            DirContextAdapter context = new DirContextAdapter(dn);
            context.setAttributeValues("objectClass", new String[]{"top", "person", "organizationalPerson", "inetOrgPerson", "pkiCA"});
            context.setAttributeValue("l", countryCode);
            context.setAttributeValue("sn", certificate.getSerialNumber().toString());
            context.setAttributeValue("cACertificate;binary", certificate.getEncoded());

            // 3. LdapTemplate의 bind() 메서드로 항목을 생성합니다.
            ldapTemplate.bind(dn, context, null);

            // 4. 새로 생성된 항목을 다시 조회하여 반환합니다.
            cscaCertificate = ldapTemplate.findByDn(dn, CscaCertificate.class);
        } catch (Exception e) {
            e.printStackTrace();
        }

        return cscaCertificate;
    }

    private String extractCountryCode(String dn) {
        for (String part : dn.split(",")) {
            part = part.trim();
            if (part.startsWith("C=")) {
                return part.substring(2).toUpperCase();
            }
        }
        return "UNKNOWN";
    }

    private String getCertificateFingerprint(X509Certificate cert) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-1");
        byte[] encoded = cert.getEncoded();
        byte[] fingerprint = digest.digest(encoded);

        return HexFormat.of().formatHex(fingerprint);
    }

    /**
     * LdapTemplate의 base DN을 기준으로 상위 항목을 확인하고 없으면 생성합니다.
     *
     * @param relativeDn 생성하려는 항목의 상대 DN (예: "ou=CSCA")
     */
    public void createParentIfNotFound(String releativeDN) {
        LdapName dn = LdapNameBuilder.newInstance(releativeDN).build();
        LdapName parentDN = (LdapName) dn.clone();

        // 최상위 항목까지 올라 가면서 상위 DN들을 생성
        while (!parentDN.isEmpty()) {
            try {
                // 상대 경로로 lookup 시도
                ldapTemplate.lookup(parentDN);
                log.debug("Parent DN {} already exits.");
                break;  // 상위 DN이 존재하면 반복문 탈출
            } catch (Exception e) {
                // 상위 DN이 존재하지 않으면 생성
                log.debug("Parent DN {} not found. Creating...", parentDN.toString());

                DirContextAdapter context = new DirContextAdapter(parentDN);
                context.setAttributeValues("objectClass", new String[]{"top", "organizationalUnit"});

                try {
                    ldapTemplate.bind(context);
                    log.debug("Successfully created parent DN: {}", parentDN.toString());
                } catch (Exception creationException) {
                    log.error("Failed to create parent DN: {}", parentDN.toString());
                    return; // 생성 실패 시 중단
                }
            }

            // 마지막 RDN 제거하여 상위 DN으로 만듦
            try {
                parentDN.remove(parentDN.size() - 1);
            } catch (InvalidNameException e) {
                log.error("유효하지 않은 DN 형식압니다: ", e.getMessage());
                e.printStackTrace();
            }
        }
    }
}
