package com.smartcoreinc.localpkd.ldif.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import javax.naming.Name;
import javax.naming.NamingException;
import javax.naming.directory.Attributes;
import javax.naming.directory.BasicAttribute;
import javax.naming.directory.BasicAttributes;
import javax.naming.directory.SearchControls;

import org.springframework.ldap.core.AttributesMapper;
import org.springframework.ldap.core.DirContextAdapter;
import org.springframework.ldap.core.LdapTemplate;
import org.springframework.ldap.support.LdapNameBuilder;
import org.springframework.stereotype.Service;

import com.smartcoreinc.localpkd.ldif.dto.LdifEntryDto;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
public class LdapService {

    private final LdapTemplate ldapTemplate;

    public LdapService(LdapTemplate ldapTemplate) {
        this.ldapTemplate = ldapTemplate;
    }

    public boolean saveEntry(LdifEntryDto entryDto) {
        try {
            Name dn = LdapNameBuilder.newInstance(entryDto.getDn()).build();
            
            // Create attributes from the entry
            Attributes attrs = new BasicAttributes();

            for (Map.Entry<String, List<String>> attrEntry : entryDto.getAttributes().entrySet()) {
                String attrName = attrEntry.getKey();
                List<String> attrValues = attrEntry.getValue();

                if (attrValues != null && !attrValues.isEmpty()) {
                    BasicAttribute attr = new BasicAttribute(attrName);
                    for (String value : attrValues) {
                        attr.add(value);
                    }
                    attrs.put(attr);
                }
            }

            // Create context adapter
            DirContextAdapter context = new DirContextAdapter(attrs, dn);

            // Bind to LDAP
            ldapTemplate.bind(context);

            log.info("Successfully saved entry: {}", entryDto.getDn());
            return true;
        } catch (Exception e) {
            log.error("Failed to save entry {}: {}", entryDto.getDn(), e.getMessage());
            return false;
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
            log.info("LDAP connection test successful");
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
                log.info("LDAP connection test successful via search");
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
}
