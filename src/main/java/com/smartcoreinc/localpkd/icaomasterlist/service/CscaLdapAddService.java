package com.smartcoreinc.localpkd.icaomasterlist.service;

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

import com.smartcoreinc.localpkd.icaomasterlist.entity.CscaCertificate;

import lombok.extern.slf4j.Slf4j;

/**
 * CscaLdapAddService
 * 
 * 기능:
 *  ICAO Master list 내의 개별 CSCA 인증서를 국가 별로 나누어서 LDAP에 저장.
 */
@Slf4j
@Service
public class CscaLdapAddService {

    private final LdapTemplate ldapTemplate;

    public CscaLdapAddService(LdapTemplate ldapTemplate) {
        this.ldapTemplate = ldapTemplate;
    }

    public CscaCertificate save(X509Certificate certificate, String valid) {
        CscaCertificate cscaCertificate = new CscaCertificate();
        try {
            // 1.
            String baseDn = "dc=ml-data,dc=download,dc=pkd";
            // createParentIfNotFound(baseDn);
            
            String subject = certificate.getSubjectX500Principal().getName();
            String countryCode = extractCountryCode(subject);
            // String countryDn = "c=%s,%s".formatted(countryCode, baseDn);
            // createParentIfNotFound(countryDn);
            
            String organizationDn = "o=csca,c=%s,%s".formatted(countryCode, baseDn);
            createParentIfNotFound(organizationDn);
           
            String fingerprint = getCertificateFingerprint(certificate);
            String cnValue = String.format("CSCA-%s-%s", countryCode, fingerprint);
            // String dn = String.format("cn=%s,%s", cnValue, cscaDn);
            
            // 2. DN을 직접 구성합니다.
            Name dn = LdapNameBuilder.newInstance(organizationDn)
                                     .add("cn", cnValue)
                                     .build();

            // 2. DirContextAdapter를 사용하여 항목의 속성을 설정합니다.
            DirContextAdapter context = new DirContextAdapter(dn);
            context.setAttributeValues("objectClass", new String[]{"top", "person", "organizationalPerson", "inetOrgPerson", "pkiCA"});
            context.setAttributeValue("gn", certificate.getSubjectX500Principal().getName());
            context.setAttributeValue("ou", certificate.getIssuerX500Principal().getName());
            context.setAttributeValue("sn", certificate.getSerialNumber().toString());
            context.setAttributeValue("description", valid);
            context.setAttributeValue("cACertificate;binary", certificate.getEncoded());

            // 3. LdapTemplate의 bind() 메서드로 항목을 생성합니다.
            ldapTemplate.bind(dn, context, null);

            // 4. 새로 생성된 항목을 다시 조회하여 반환합니다.
            cscaCertificate = ldapTemplate.findByDn(dn, CscaCertificate.class);
            log.debug("등록된 CSCA 인증서 DN: {}", cscaCertificate.getDn().toString());
            // log.debug("certificate: {}", cscaCertificate.getCertificate());
            // CertificateFactory cf = CertificateFactory.getInstance("X.509");
            // X509Certificate cert = (X509Certificate) cf.generateCertificate(new ByteArrayInputStream(cscaCertificate.getCertificate()));
            // log.debug("{}", cert.getSubjectX500Principal().getName());
            
        } catch (Exception e) {
            log.error("error: {}", e.getMessage());
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
    public void createParentIfNotFound(String dnString) {
        try {
            LdapName targetDn = new LdapName(dnString);
            createParentRecursively(targetDn);
        } catch (InvalidNameException e) {
            log.error("유효하지 않은 DN 형식입니다: {}", e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * 재귀적으로 DN의 상위 항목들을 생성합니다.
     *
     * @param targetDn 생성하려는 DN
     */
    private void createParentRecursively(LdapName targetDn) {
        if (targetDn.isEmpty()) {
            return;
        }

        try {
            // 현재 DN이 존재하는지 확인
            ldapTemplate.lookup(targetDn);
            log.debug("DN {} already exists.", targetDn.toString());
            return;
        } catch (Exception e) {
            log.debug("DN {} not found. Checking parent...", targetDn.toString());
        }

        // 상위 DN이 있다면 먼저 상위 DN을 재귀적으로 생성
        if (targetDn.size() > 1) {
            LdapName parentDn = (LdapName) targetDn.clone();
            try {
                parentDn.remove(parentDn.size() - 1);
                createParentRecursively(parentDn);
            } catch (InvalidNameException e) {
                log.error("상위 DN 생성 중 오류: {}", e.getMessage());
                return;
            }
        }

        // 현재 DN 생성
        createDnEntry(targetDn);
    }

    /**
     * 특정 DN 항목을 생성합니다.
     *
     * @param dn 생성할 DN
     */
    private void createDnEntry(LdapName dn) {
        try {
            
            DirContextAdapter context = new DirContextAdapter(dn);
    
            // DN의 가장 왼쪽 RDN(가장 구체적인 부분)을 분석하여 objectClass 설정
            String firstRdn = dn.get(dn.size() - 1).toLowerCase();
    
            if (firstRdn.startsWith("c=")) {
                // 국가 코드
                context.setAttributeValues("objectClass", new String[]{"top", "country"});
                String countryCode = firstRdn.substring(2);
                context.setAttributeValue("c", countryCode);
            } else if (firstRdn.startsWith("dc=")) {
                // 도메인 컴포넌트
                context.setAttributeValues("objectClass", new String[]{"top", "domain"});
                String dcValue = firstRdn.substring(3);
                context.setAttributeValue("dc", dcValue);
            } else if (firstRdn.startsWith("o=")) {
                // 조직
                context.setAttributeValues("objectClass", new String[]{"top", "organization"});
                String orgValue = firstRdn.substring(2);
                context.setAttributeValue("o", orgValue);
            } else if (firstRdn.startsWith("ou=")) {
                // 조직 단위
                context.setAttributeValues("objectClass", new String[]{"top", "organizationalUnit"});
                String ouValue = firstRdn.substring(3);
                context.setAttributeValue("ou", ouValue);
            } else {
                // 기본값 (일반적인 경우)
                context.setAttributeValues("objectClass", new String[]{"top"});
            }

            ldapTemplate.bind(context);
            log.debug("Successfully created DN: {}", dn.toString());
        } catch (Exception e) {
            log.error("Failed to create DN {}: {}", dn.toString(), e.getMessage());
            e.printStackTrace();
        }
    }
}
