package com.smartcoreinc.localpkd.icaomasterlist.service;

import java.security.MessageDigest;
import java.security.cert.X509Certificate;
import java.util.HexFormat;
import javax.naming.InvalidNameException;
import javax.naming.Name;
import javax.naming.ldap.LdapName;
import org.springframework.ldap.core.DirContextAdapter;
import org.springframework.ldap.core.LdapTemplate;
import org.springframework.ldap.filter.AndFilter;
import org.springframework.ldap.filter.EqualsFilter;
import org.springframework.ldap.support.LdapNameBuilder;
import org.springframework.stereotype.Service;

import com.smartcoreinc.localpkd.icaomasterlist.entity.CscaCertificate;

import lombok.extern.slf4j.Slf4j;

/**
 * CscaLdapAddService
 * 
 * 기능:
 *  1. ICAO Master list 내의 개별 CSCA 인증서를 국가 별로 나누어서 LDAP에 저장.
 *  2. CSCA 인증서 저장 전 이미 등록된 인증서가 있는 경우 별도의 duplicated-csca OU 하위에 저장
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
            // 1. Base DN 설정
            String baseDn = "dc=ml-data,dc=download,dc=pkd";
            
            String subject = certificate.getSubjectX500Principal().getName();
            String countryCode = extractCountryCodeFromDN(subject);
            String fingerprint = getCertificateFingerprint(certificate);
            String cnValue = String.format("CSCA-%s-%s", countryCode, fingerprint);
            
            // 1. 중복 검사
            if (isCertificateDuplicate(countryCode, fingerprint)) {
                log.warn("이미 존재하는 인증서입니다. 중복 그룹에 저장합니다. Country: {}, Fingerprint: {}", countryCode, fingerprint);
                // 중복된 인증서는 'ou=duplicates' 그룹에 저장
                // String dupOrganizationalUnitDn = "ou=duplicated-csca,c=%s,%s".formatted(countryCode, baseDn);
                // createParentIfNotFound(dupOrganizationalUnitDn);
                // Name dn = LdapNameBuilder.newInstance(dupOrganizationalUnitDn)
                //                          .add("cn", cnValue)
                //                          .build();
                // return bindCertificate(dn, certificate, cnValue, countryCode, valid);
                return null;
            }

            // 2. 중복이 아닐 경우, ou=csca 하위에 저장
            String cscaOrganizationalUnitDn = "ou=csca,c=%s,%s".formatted(countryCode, baseDn);
            createParentIfNotFound(cscaOrganizationalUnitDn);
            
            Name dn = LdapNameBuilder.newInstance(cscaOrganizationalUnitDn)
                                     .add("cn", cnValue)
                                     .build();

            cscaCertificate = bindCertificate(dn, certificate, cnValue, countryCode, valid);
            log.debug("등록된 CSCA 인증서 DN: {}", cscaCertificate.getDn().toString());
            return cscaCertificate;
        } catch (Exception e) {
            log.error("error: {}", e.getMessage());
            e.printStackTrace();
        }

        return cscaCertificate;
    }

    /**
     * LDAP에 인증서를 바인딩하는 공통 로직
     *
     * @param dn 바인딩할 DN
     * @param certificate X.509 인증서
     * @param cnValue cn 속성 값
     * @param countryCode 국가 코드
     * @param valid 유효성 상태
     * @return 저장된 CscaCertificate 객체
     * @throws Exception
     */
    private CscaCertificate bindCertificate(Name dn, X509Certificate certificate, String cnValue, String countryCode, String valid) throws Exception {
        DirContextAdapter context = new DirContextAdapter(dn);
        context.setAttributeValues("objectClass", new String[]{"top", "device", "cscaCertificateObject", "pkiCA"});
        context.setAttributeValue("cn", cnValue);
        context.setAttributeValue("countryCode", countryCode);
        String issuerDn = LdapDnUtil.getLdapCompatibleDn(certificate);
        context.setAttributeValue("issuer", issuerDn);
        context.setAttributeValue("serialNumber", certificate.getSerialNumber().toString());
        context.setAttributeValue("cscaFingerprint", getCertificateFingerprint(certificate));
        java.text.SimpleDateFormat ldapTime = new java.text.SimpleDateFormat("yyyyMMddHHmmss'Z'");
        ldapTime.setTimeZone(java.util.TimeZone.getTimeZone("UTC"));
        context.setAttributeValue("notBefore", ldapTime.format(certificate.getNotBefore()));
        context.setAttributeValue("notAfter",  ldapTime.format(certificate.getNotAfter()));
        context.setAttributeValue("description", valid);
        context.setAttributeValue("cACertificate;binary", certificate.getEncoded());

        ldapTemplate.bind(dn, context, null);
        log.debug("등록된 CSCA 인증서 DN: {}", dn.toString());

        return ldapTemplate.findByDn(dn, CscaCertificate.class);
    }

    /**
     * 동일한 국가 코드와 지문(fingerprint)를 가진 인증서가 이미 존재하는지 확인
     * @param countryCode 국가 코드
     * @param fingerprint 인증서 지문
     * @return boolean 중복 여부
     */
    private boolean isCertificateDuplicate(String countryCode, String fingerprint) {
        AndFilter filter = new AndFilter();
        filter.and(new EqualsFilter("countryCode", countryCode));
        filter.and(new EqualsFilter("cscaFingerprint", fingerprint));

        String searchBase = "dc=ml-data,dc=download,dc=pkd";

        try {
            return !ldapTemplate.search(searchBase, filter.encode(), (Object ctx) -> null).isEmpty();
        } catch (Exception e) {
            log.error("중복 인중서 확인 중 오류 발생: {}", e.getMessage());
            return false;
        }
    }

    /**
     * DN 값으로 부터 2자리 국가 코드 추출하여 리턴 
     * @param dn : String
     * @return String : 2자리 국가 코드
     */
    private String extractCountryCodeFromDN(String dn) {
        for (String part : dn.split(",")) {
            part = part.trim();
            if (part.startsWith("C=")) {
                return part.substring(2).toUpperCase();
            }
        }
        return "UNKNOWN";
    }

    /**
     * X.509 인증서 Fingerprint계산하여 16진수 문자열로 리턴
     * 
     * @param cert : java.security.cert.X509Certificate
     * @return String (HexString)
     * @throws Exception
     */
    private String getCertificateFingerprint(X509Certificate cert) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-1");
        byte[] encoded = cert.getEncoded();
        byte[] fingerprint = digest.digest(encoded);

        return HexFormat.of().formatHex(fingerprint).toUpperCase();
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
