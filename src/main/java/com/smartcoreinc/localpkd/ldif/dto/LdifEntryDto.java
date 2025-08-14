package com.smartcoreinc.localpkd.ldif.dto;

import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonIgnore;

public class LdifEntryDto {
    private String dn;
    private String entryType;   // ADD, MODIFY, DELETE
    private Map<String, List<String>> attributes;
    
    @JsonIgnore
    private String originalLdif;

    public LdifEntryDto() {}

    public LdifEntryDto(String dn, String entryType, Map<String, List<String>> attributes, String originalLdif) {
        this.dn = dn;
        this.entryType = entryType;
        this.attributes = attributes;
        this.originalLdif = originalLdif;
    }

    public String getDn() {
        return dn;
    }

    public void setDn(String dn) {
        this.dn = dn;
    }

    public String getEntryType() {
        return entryType;
    }

    public void setEntryType(String entryType) {
        this.entryType = entryType;
    }

    public Map<String, List<String>> getAttributes() {
        return attributes;
    }

    public void setAttributes(Map<String, List<String>> attributes) {
        this.attributes = attributes;
    }

    public String getOriginalLdif() {
        return originalLdif;
    }

    public void setOriginalLdif(String originalLdif) {
        this.originalLdif = originalLdif;
    }

    @Override
    public String toString() {
        return "LdifEntryDto{" +
                "dn='" + dn + '\'' +
                ", entryType='" + entryType + '\'' +
                ", attributes=" + attributes +
                '}';
    }
}
