package com.smartcoreinc.localpkd.ldif.service;

import java.io.ByteArrayInputStream;
import java.security.cert.CertPath;
import java.security.cert.CertPathValidator;
import java.security.cert.CertPathValidatorException;
import java.security.cert.PKIXParameters;
import java.security.cert.TrustAnchor;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import javax.naming.Name;
import javax.naming.NamingException;
import javax.naming.directory.Attribute;
import javax.naming.directory.Attributes;
import javax.naming.directory.BasicAttribute;
import javax.naming.directory.BasicAttributes;
import javax.naming.directory.SearchControls;

import org.springframework.ldap.NameNotFoundException;
import org.springframework.ldap.core.AttributesMapper;
import org.springframework.ldap.core.DirContextAdapter;
import org.springframework.ldap.core.LdapTemplate;
import org.springframework.ldap.support.LdapNameBuilder;
import org.springframework.stereotype.Service;

import com.smartcoreinc.localpkd.enums.X509ValidationResult;
import com.smartcoreinc.localpkd.icaomasterlist.entity.CscaCertificate;
import com.smartcoreinc.localpkd.icaomasterlist.service.CscaLdapSearchService;
import com.smartcoreinc.localpkd.ldif.dto.LdifEntryDto;
import com.smartcoreinc.localpkd.ldif.dto.TrustChainValidationResult;
import com.smartcoreinc.localpkd.ldif.dto.ValidationResult;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
public class LdapService {

    private final LdapTemplate ldapTemplate;
    private final CscaLdapSearchService cscaLdapSearchService; // CscaLdapSearchService 주입

    // 바이너리 처리가 필요한 속성들
    private static final List<String> BINARY_ATTRIBUTES = Arrays.asList(
        "userCertificate", "caCertificate", "crossCertificatePair", 
        "certificateRevocationList", "authorityRevocationList",
        "jpegPhoto", "audio"
    );

    public LdapService(LdapTemplate ldapTemplate, CscaLdapSearchService cscaLdapSearchService) {
        this.ldapTemplate = ldapTemplate;
        this.cscaLdapSearchService = cscaLdapSearchService; // 생성자 주입
    }

    public boolean saveEntry(LdifEntryDto entryDto) {
        // LDAP 서버에 저장하기전 DN 값을 현재 DIT 구조에 맞게 변경한다.
        log.debug("원래 DN: {}", entryDto.getDn());
        String newDN = entryDto.getDn().replace(",dc=icao,dc=int", "");
        log.debug("변경된 DN: {}", newDN);
        Name dn = LdapNameBuilder.newInstance(newDN).build();
        ensureParentEntriesExist(dn);
        
        try {
            // LdifEntryDto에서 속성 맵을 가져옴
            Map<String, List<String>> attributes = entryDto.getAttributes();

            // 1. DN에 "o=dsc"가 포함되고, 2. userCertificate 속성이 존재하는 경우에만 DSC 엔트리로 간주합니다.
            boolean isDscEntry = entryDto.getDn().contains("o=dsc") && attributes.containsKey("userCertificate;binary");

            // DSC 엔트리인 경우에만 신뢰 체인 검증 수행
            if (isDscEntry) {
                log.info("DSC 엔트리 발견, 신뢰 체인 검증 시작");
                String validationResultMessage = null;

                try {
                    String countryCode = getCountryCodeFromDn(dn);
                    if (countryCode == null) {
                        validationResultMessage = TrustChainValidationResult.FAILED.name() + ": Country code not found in DN";
                    } else {
                        List<CscaCertificate> cscaCerts = cscaLdapSearchService.findCscaCertificatesByCountryCode(countryCode);
                        if (cscaCerts.isEmpty()) {
                           validationResultMessage = TrustChainValidationResult.FAILED.name() + ": No CSCA certificates found for country " + countryCode; 
                        } else {
                            List<String> dscCertValues = attributes.get("userCertificate;binary");
                            if (dscCertValues != null && !dscCertValues.isEmpty()) {
                                byte[] dscCertBytes = Base64.getDecoder().decode(dscCertValues.get(0));

                                // dscCertBytes를 X509Certificate로 변환
                                X509Certificate dscCert = convertBytesToX509Cert(dscCertBytes);

                                // CscaCertificate 엔티티에서 X509Certificate 객체 리스트로 변환
                                List<X509Certificate> x509CscaCerts = cscaCerts.stream()
                                    .map(CscaCertificate::getCertificate)
                                    .map(this::convertBytesToX509Cert)
                                    .filter(Objects::nonNull)
                                    .collect(Collectors.toList());
    
                                // 수정된 함수 호출
                                ValidationResult result = validateDscTrustChain(dscCert, x509CscaCerts);
    
                                // DTO에서 상태와 메시지를 추출하여 LDAP 저장용 문자열 생성
                                if (result.getStatus() == TrustChainValidationResult.SUCCESS) {
                                    validationResultMessage = TrustChainValidationResult.SUCCESS.name();
                                } else {
                                    validationResultMessage = TrustChainValidationResult.FAILED.name() + ": userCertificate attribute not found";
                                }
                            }
                        }
                    }
                } catch (Exception e) {
                    log.error("신뢰 체인 검증 중 예외 발생: {}", e.getMessage());
                    validationResultMessage = TrustChainValidationResult.FAILED.name() + ": Internal error - " + e.getClass().getSimpleName();
                }

                // 검증 결과를 entryDto의 속성에 추가
                if (validationResultMessage != null) {
                    attributes.put("validationResult", Collections.singletonList(validationResultMessage));
                }
            }
            // Create attributes from the entry
            Attributes attrs = new BasicAttributes();

            for (Map.Entry<String, List<String>> attrEntry : entryDto.getAttributes().entrySet()) {
                String attrName = attrEntry.getKey();
                List<String> attrValues = attrEntry.getValue();
                log.debug("Processing attribute: {}, values count = {}", attrName, attrValues.size());

                if (attrValues != null && !attrValues.isEmpty()) {
                    BasicAttribute attr = new BasicAttribute(attrName);
                    // 바이너리 속성 처리
                    if (isBinaryAttribute(attrName)) {
                        // attr = new BasicAttribute(attrName + ";binary");
                        log.debug("Processing binary attribute: {}", attrName);
                        processBinaryAttribute(attr, attrValues);
                    } else {
                        // attr = new BasicAttribute(attrName);
                        for (String value : attrValues) {
                            attr.add(value);
                        }
                    }
                    attrs.put(attr);
                }
            }

            // Create context adapter
            DirContextAdapter context = new DirContextAdapter(attrs, dn);

            // Bind to LDAP
            ldapTemplate.bind(context);

            log.info("Successfully saved entry: {}", dn.toString());
            return true;
        } catch (Exception e) {
            log.error("Failed to save entry {}: {}", dn.toString(), e.getMessage());
            return false;
        }
    }

    /**
     * 특정 국가의 CSCA 인증서들을 LDAP에서 검색
     * @param countryCode 국가 코드 (예: "kr")
     * @return X509Certificate 객체 리스트
     */
    // private List<X509Certificate> findCscaCertificatesByCountry(String countryCode) {
    //     String baseDn = "ou=csca,c=" + countryCode + ",dc=ml-data,dc=download,dc=pkd,dc=ldap,dc=smartcoreinc,dc=com";
    //     String filter = "(&(objectClass=pkiCA)(caCertificate=*))";
        
    //     List<X509Certificate> cscaCerts = new ArrayList<>();
        
    //     try {
    //         ldapTemplate.search(
    //             baseDn,
    //             filter,
    //             SearchControls.SUBTREE_SCOPE,
    //             new String[]{"caCertificate"},
    //             (AttributesMapper<Void>) attrs -> {
    //                 Attribute caCertAttr = attrs.get("caCertificate");
    //                 if (caCertAttr != null) {
    //                     try {
    //                         for (int i = 0; i < caCertAttr.size(); i++) {
    //                             byte[] certBytes = (byte[]) caCertAttr.get(i);
    //                             X509Certificate cert = (X509Certificate) java.security.cert.CertificateFactory
    //                                 .getInstance("X.509")
    //                                 .generateCertificate(new ByteArrayInputStream(certBytes));
    //                             cscaCerts.add(cert);
    //                         }
    //                     } catch (Exception e) {
    //                         log.error("CSCA 인증서 파싱 실패: {}", e.getMessage());
    //                     }
    //                 }
    //                 return null;
    //             }
    //         );
    //     } catch (Exception e) {
    //         log.error("LDAP에서 CSCA 인증서 검색 실패: {}", e.getMessage());
    //     }
        
    //     return cscaCerts;
    // }

    /**
     * 바이트 배열을 X509Certificate 객체로 변환
     */
    private X509Certificate convertBytesToX509Cert(byte[] certBytes) {
        try {
            // Bouncy Castle 제공자를 명시적으로 사용하여 인증서 변환
            return (X509Certificate) java.security.cert.CertificateFactory
                .getInstance("X.509", "BC")
                .generateCertificate(new ByteArrayInputStream(certBytes));
        } catch (Exception e) {
            log.error("Failed to convert byte array to X509Certificate: {}", e.getMessage());
            return null;
        }
    }

    /**
     * DSC 인증서의 신뢰 체인 검증
     * @param dscCertBytes DSC 인증서 바이트 배열
     * @param cscaCerts 신뢰할 수 있는 CSCA 인증서 리스트
     * @return 검증 성공 시 "Success", 실패 시 실패 이유 문자열
     */
    private ValidationResult validateDscTrustChain(X509Certificate dscCert, List<X509Certificate> cscaCerts) {
        try {
            // 1. DSC 인증서의 서명 알고리즘 및 유효 기간 검증
            log.info("Validating DSC certificate: {}", dscCert.getSubjectX500Principal().getName());
            dscCert.checkValidity(); // 유효 기간 검증
            
            // 2. 신뢰할 수 있는 CSCA 인증서들을 TrustAnchor로 변환
            Set<TrustAnchor> trustAnchors = cscaCerts.stream()
                .map(cert -> new TrustAnchor(cert, null))
                .collect(Collectors.toSet());
            
            // 3. 인증 경로(CertPath) 생성
            List<X509Certificate> certs = Arrays.asList(dscCert);
            CertPath certPath = java.security.cert.CertificateFactory
                .getInstance("X.509")
                .generateCertPath(certs);

            // 4. PKIX 파라미터 설정 (신뢰할 수 있는 앵커 설정)
            PKIXParameters pkixParams = new PKIXParameters(trustAnchors);
            pkixParams.setRevocationEnabled(false); // CRL 검증은 이 예제에서 제외
            
            // 5. 인증 경로 유효성 검증
            CertPathValidator certPathValidator = CertPathValidator.getInstance("PKIX");
            certPathValidator.validate(certPath, pkixParams);

            return new ValidationResult(TrustChainValidationResult.SUCCESS, "Validation successful");
        } catch (CertPathValidatorException e) {
            return new ValidationResult(TrustChainValidationResult.FAILED, e.getMessage());
        } catch (Exception e) {
            log.error("인증서 검증 중 예외 발생: {}", e.getMessage());
            return new ValidationResult(TrustChainValidationResult.FAILED, "Internal error: " + e.getMessage());
        }
    }

     /**
     * DN에서 국가 코드 (c=) 추출
     * @param dn DN 객체
     * @return 국가 코드 (예: "kr"), 찾지 못하면 null
     */
    private String getCountryCodeFromDn(Name dn) {
        for (int i = 0; i < dn.size(); i++) {
            String rdn = dn.get(i);
            if (rdn.toLowerCase().startsWith("c=")) {
                return rdn.substring(2);
            }
        }
        return null;
    }

    /**
     * 바이너리 속성 처리
     */
    private void processBinaryAttribute(BasicAttribute attr, List<String> values) {
        for (String value : values) {
            try {
                // Base64 인코딩된 문자열을 바이트 배열로 변환
                byte[] binaryData = Base64.getDecoder().decode(value);
                attr.add(binaryData);
                log.debug("Added binary data for attribute '{}', size: {} bytes", 
                         attr.getID(), binaryData.length);
            } catch (IllegalArgumentException e) {
                log.warn("Failed to decode Base64 for attribute '{}', treating as string: {}", 
                        attr.getID(), e.getMessage());
                // Base64 디코딩 실패시 원본 문자열 사용
                attr.add(value);
            }
        }
    }

    /**
     * 바이너리 속성 여부 확인
     */
    private boolean isBinaryAttribute(String attributeName) {
        String normalizedName = attributeName.toLowerCase().replace(";binary", "");
        return BINARY_ATTRIBUTES.stream()
                .anyMatch(binaryAttr -> normalizedName.equals(binaryAttr.toLowerCase()));
    }

    /**
     * 부모 엔트리들이 존재하는지 확인하고 없으면 생성
     */
    private void ensureParentEntriesExist(Name dn) {
        List<Name> parentDNs = new ArrayList<>();
        Name current = dn.getPrefix(dn.size() - 1); // 마지막 RDN 제외
        
        // 부모 DN들을 역순으로 수집
        while (current.size() > 0) {
            parentDNs.add(0, current); // 앞에 추가하여 root부터 순서대로
            current = current.getPrefix(current.size() - 1);
        }
        
        // 각 부모 DN이 존재하는지 확인하고 없으면 생성
        for (Name parentDN : parentDNs) {
            try {
                ldapTemplate.lookup(parentDN);
                log.debug("Parent entry exists: {}", parentDN);
            } catch (NameNotFoundException e) {
                log.info("Creating missing parent entry: {}", parentDN);
                createParentEntry(parentDN);
            } catch (Exception e) {
                log.warn("Error checking parent entry {}: {}", parentDN, e.getMessage());
            }
        }
    }

    /**
     * 부모 엔트리 생성
     */
    private void createParentEntry(Name dn) {
        try {
            Attributes attrs = new BasicAttributes();
            
            // 마지막 RDN에서 속성과 값 추출
            String lastRdn = dn.get(dn.size() - 1);
            
            if (lastRdn.startsWith("dc=")) {
                String dcValue = lastRdn.substring(3);
                BasicAttribute dcAttr = new BasicAttribute("dc", dcValue);
                BasicAttribute ocAttr = new BasicAttribute("objectClass");
                ocAttr.add("top");
                ocAttr.add("domain");
                
                attrs.put(dcAttr);
                attrs.put(ocAttr);
            } else if (lastRdn.startsWith("ou=")) {
                String ouValue = lastRdn.substring(3);
                BasicAttribute ouAttr = new BasicAttribute("ou", ouValue);
                BasicAttribute ocAttr = new BasicAttribute("objectClass");
                ocAttr.add("organizationalUnit");
                
                attrs.put(ouAttr);
                attrs.put(ocAttr);
            } else if (lastRdn.startsWith("o=")) {
                String oValue = lastRdn.substring(2);
                BasicAttribute oAttr = new BasicAttribute("o", oValue);
                BasicAttribute ocAttr = new BasicAttribute("objectClass");
                ocAttr.add("top");
                ocAttr.add("organization");
                
                attrs.put(oAttr);
                attrs.put(ocAttr);
            } else if (lastRdn.startsWith("c=")) {
                String cValue = lastRdn.substring(2);
                BasicAttribute cAttr = new BasicAttribute("c", cValue);
                BasicAttribute ocAttr = new BasicAttribute("objectClass");
                ocAttr.add("top");
                ocAttr.add("country");
                
                attrs.put(cAttr);
                attrs.put(ocAttr);
            } else {
                // 기본 조직 단위로 생성
                String defaultValue = lastRdn.contains("=") ? 
                    lastRdn.substring(lastRdn.indexOf("=") + 1) : lastRdn;
                BasicAttribute ouAttr = new BasicAttribute("ou", defaultValue);
                BasicAttribute ocAttr = new BasicAttribute("objectClass");
                ocAttr.add("organizationalUnit");
                
                attrs.put(ouAttr);
                attrs.put(ocAttr);
            }
            
            DirContextAdapter context = new DirContextAdapter(attrs, dn);
            ldapTemplate.bind(context);
            log.info("Successfully created parent entry: {}", dn);
        } catch (Exception e) {
            log.error("Failed to create parent entry {}: {}", dn, e.getMessage());
            throw new RuntimeException("Failed to create parent entry: " + dn, e);
        }
    }

    public int saveAllEntries(List<LdifEntryDto> entries) {
        int successCount = 0;

        for (LdifEntryDto entryDto : entries) {
            try {
                if (saveEntry(entryDto)) {
                    successCount++;
                }

                // Add small delay to avoid overwhelming LDAP server
                Thread.sleep(10);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                log.warn("Save operation interrupted");
                break;
            } catch (Exception e) {
                log.error("Unexpected error saving entry {}: {}", entryDto.getDn(), e.getMessage());
            }
        }

        log.info("Saved {}/{} entries to LDAP", successCount, entries.size());
        return successCount;
    }

    public boolean testConnection() {
        try {
            // 간단한 연결 테스트: base DN lookup
            Object result = ldapTemplate.lookup("");
            log.info("LDAP connection test successful: {}", result.toString());
            return true;
        } catch (Exception e) {
            try {
                // 대안: 최소한의 search로 연결 테스트
                List<String> results = ldapTemplate.search(
                    "",
                    "(objectclass=*)",
                    SearchControls.ONELEVEL_SCOPE,
                    new String[]{"objectclass"},
                    new AttributesMapper<String>() {
                        @Override
                        public String mapFromAttributes(Attributes attributes) throws NamingException {
                            return "connected";
                        }
                    }
                );
                log.info("LDAP connection test successful via search: {}", results.size());
                return true;
            } catch (Exception searchException) {
                // 마지막 시도: context source 직접 테스트
                try {
                    ldapTemplate.getContextSource().getContext("", "");
                    log.info("LDAP connection test successful via context");
                    return true;
                } catch (Exception contextException) {
                    log.error("LDAP connection test failed: {}", contextException.getMessage());
                    return false;
                }
            }
        }
    }

    public boolean entryExists(String dn) {
        try {
            Name name = LdapNameBuilder.newInstance(dn).build();
            Object obj = ldapTemplate.lookup(name);
            return obj != null;
        } catch (Exception e) {
            // Entry doesn't exist or other error
            return false;
        }
    }

    /**
     * 특정 Base DN의 존재 여부 확인
     * @param baseDn
     * @return
     */
    public boolean baseDNExists(String baseDn) {
        try {
            Object result = ldapTemplate.lookup(baseDn);
            return result != null;
        } catch (Exception e) {
            log.error("Base DN {} does not exist: {}", baseDn, e.getMessage());
            return false;
        }
    }

    /**
     * LDAP 스키마 정보 조회
     */
    public List<String> getSupportedObjectClasses() {
        try {
            return ldapTemplate.search(
                "cn=schema",
                "(objectclass=*)",
                SearchControls.OBJECT_SCOPE,
                new String[]{"objectClasses"},
                new AttributesMapper<String>() {
                    @Override
                    public String mapFromAttributes(Attributes attributes) throws NamingException {
                        if (attributes.get("objectClasses") != null) {
                            return attributes.get("objectClasses").get().toString();
                        }
                        return null;
                    }
                }
            );
        } catch (Exception e) {
            log.warn("Could not retrieve schema information: {}", e.getMessage());
            return new ArrayList<>();
        }
    }

    public void deleteEntry(String dn) {
        try {
            Name name = LdapNameBuilder.newInstance(dn).build();
            ldapTemplate.unbind(name);
            log.info("Successfully deleted entry: {}", dn);
        } catch (Exception e) {
            log.error("Failed to delete entry {}: {}", dn, e.getMessage());
            throw new RuntimeException("Failed to delete entry: " + dn, e);
        }
    }

    /**
     * 디버깅용: 특정 엔트리의 속성 조회
     */
    public void debugEntry(String dn) {
        try {
            Name name = LdapNameBuilder.newInstance(dn).build();
            DirContextAdapter context = (DirContextAdapter) ldapTemplate.lookup(name);
            
            log.debug("=== Debug Entry: {} ===", dn);
            Attributes attrs = context.getAttributes();
            javax.naming.NamingEnumeration<? extends javax.naming.directory.Attribute> allAttrs = attrs.getAll();
            
            while (allAttrs.hasMore()) {
                javax.naming.directory.Attribute attr = allAttrs.next();
                log.debug("Attribute: {} = {}", attr.getID(), attr.get());
            }
            log.debug("=== End Debug ===");
            
        } catch (Exception e) {
            log.error("Failed to debug entry {}: {}", dn, e.getMessage());
        }
    }
}
