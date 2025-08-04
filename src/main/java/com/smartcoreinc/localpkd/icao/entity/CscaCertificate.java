package com.smartcoreinc.localpkd.icao.entity;

import javax.naming.Name;

import org.springframework.ldap.odm.annotations.Attribute;
import org.springframework.ldap.odm.annotations.Entry;
import org.springframework.ldap.odm.annotations.Id;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.smartcoreinc.localpkd.ldaphelper.NameDeserializer;
import com.smartcoreinc.localpkd.ldaphelper.NameSerializer;

@Entry(base = "ou=CSCA", objectClasses = {"top", "person", "organizationalPerson", "inetOrgPerson", "pkiCA"})
public final class CscaCertificate {

    @Id
    @JsonSerialize(using = NameSerializer.class)
    @JsonDeserialize(using = NameDeserializer.class)
    private Name dn;

    @Attribute(name = "sn")
    private String seralNumber;

    @Attribute(name = "l")
    private String country;

    @Attribute(name = "cn")
    private String issuerName;
    
    @Attribute(name = "cACertificate;binary")
    private byte[] certificate;
    
    public CscaCertificate() {}

    public Name getDn() {
        return dn;
    }

    public void setDn(Name dn) {
        this.dn = dn;
    }

    public String getSeralNumber() {
        return seralNumber;
    }

    public void setSeralNumber(String seralNumber) {
        this.seralNumber = seralNumber;
    }

    public String getCountry() {
        return country;
    }

    public void setCountry(String country) {
        this.country = country;
    }

    public String getIssuerName() {
        return issuerName;
    }

    public void setIssuerName(String issuerName) {
        this.issuerName = issuerName;
    }

    public byte[] getCertificate() {
        return certificate;
    }

    public void setCertificate(byte[] certificate) {
        this.certificate = certificate;
    }

}
