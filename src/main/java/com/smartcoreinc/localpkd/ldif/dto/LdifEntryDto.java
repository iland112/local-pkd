package com.smartcoreinc.localpkd.ldif.dto;

import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.smartcoreinc.localpkd.enums.EntryType;

/**
 * LDIF 레코드 (Entry) Dto
 */ 
public class LdifEntryDto {
    private String dn;                              // DN(Distinguished Name)
    private EntryType entryType;                    // ML, DSC, CRL
    private Map<String, List<String>> attributes;   // Entry 속성들을 담고 있는 맵컨테이너
    
    @JsonIgnore
    private String originalLdif;                    // Entry(Record) 전체 문자열

    public LdifEntryDto() {}

    public LdifEntryDto(String dn, EntryType entryType, Map<String, List<String>> attributes, String originalLdif) {
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

    public EntryType getEntryType() {
        return entryType;
    }

    public void setEntryType(EntryType entryType) {
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
