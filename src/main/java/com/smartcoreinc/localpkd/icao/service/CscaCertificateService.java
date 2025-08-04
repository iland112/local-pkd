package com.smartcoreinc.localpkd.icao.service;

import java.security.MessageDigest;
import java.security.cert.X509Certificate;
import java.util.HexFormat;
import java.util.List;

import javax.naming.InvalidNameException;
import javax.naming.Name;
import javax.naming.ldap.LdapName;

import org.springframework.ldap.core.DirContextAdapter;
import org.springframework.ldap.core.LdapTemplate;
import org.springframework.ldap.support.LdapNameBuilder;
import org.springframework.stereotype.Service;

import com.smartcoreinc.localpkd.icao.entity.CscaCertificate;
import com.smartcoreinc.localpkd.icao.repository.CscaCertificateRepository;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
public class CscaCertificateService {

    private final LdapTemplate ldapTemplate;
    private final CscaCertificateRepository repository;

    public CscaCertificateService(LdapTemplate ldapTemplate, CscaCertificateRepository repository) {
        this.ldapTemplate = ldapTemplate;
        this.repository = repository;
    }

    public List<CscaCertificate> findAll() {
        return repository.findAll();
    }

    public CscaCertificate save(X509Certificate certificate) {
        CscaCertificate cscaCertificate = new CscaCertificate();
        try {
            createOrganizationalUnitIfNotFound("ou=CSCA,dc=ldap,dc=smartcoreinc,dc=com");
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

    public void delete(CscaCertificate entry) {
        repository.delete(entry);
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

    private void createOrganizationalUnitIfNotFound(String fullDn) {
        LdapName dn = LdapNameBuilder.newInstance(fullDn).build();
        LdapName parentDn = (LdapName) dn.clone();

        // 최상위까지 올라가면서 상위 DN들을 생성
        while (!parentDn.isEmpty()) {
            // 마지막 RDN 제거하여 상위 DN으로 만듦
            try {
                parentDn.remove(parentDn.size() - 1);
            } catch (InvalidNameException e) {
                log.error("Invalid Distinguished Name: {}", fullDn);
                e.printStackTrace();
            }

            // 상위 DN이 더 이상 없으면 루프 종료
            if (parentDn.isEmpty()) {
                break;
            }

            try {
                ldapTemplate.lookup(parentDn);
                log.debug("Parent DN {} already exists.", parentDn.toString());
            } catch (Exception e) {
                // 상위 DN이 존재하지 않으면 생성
                log.debug("Parent DN {} not found. Creating...", parentDn.toString());

                DirContextAdapter context = new DirContextAdapter(parentDn);
                context.setAttributeValues("objectClass", new String[]{"top", "organizationalUnit"});
                
                try {
                    ldapTemplate.bind(context);
                    log.debug("Successfully created parent DN: {}", parentDn.toString());
                } catch (Exception creationException) {
                    log.error("Failed to create parent DN: {}", parentDn.toString());
                    return;
                }
            }
        }
    }
}
