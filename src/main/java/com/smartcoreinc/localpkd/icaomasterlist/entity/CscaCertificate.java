package com.smartcoreinc.localpkd.icaomasterlist.entity;

import javax.naming.Name;

import org.springframework.ldap.odm.annotations.Attribute;
import org.springframework.ldap.odm.annotations.Entry;
import org.springframework.ldap.odm.annotations.Id;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.smartcoreinc.localpkd.ldaphelper.NameDeserializer;
import com.smartcoreinc.localpkd.ldaphelper.NameSerializer;

@Entry(base = "dc=ml-data,dc=download,dc=pkd", objectClasses = {"top", "device", "cscaCertificateObject"})
public final class CscaCertificate {

    @Id
    @JsonSerialize(using = NameSerializer.class)
    @JsonDeserialize(using = NameDeserializer.class)
    private Name dn;

    @Attribute(name = "cn")
    private String cn; // CSCA-[국가코드]-[인증서 SHA-1 지문]

    @Attribute(name = "countryConde")
    private String countryCode; // 2자리 국가 코드

    @Attribute(name = "issuer")
    private String issuer; // 발급자 DN

    @Attribute(name = "serialNumber")
    private String serialNumber; // 인증서 시리얼 번호

    @Attribute(name = "cscaFingerprint")
    private String fingerprint; // 2자리 국가 코드

    @Attribute(name = "notBefore")
    private String notBefore;

    @Attribute(name = "notAfter")
    private String notAfter;

    @Attribute(name = "description")
    private String valid;   // valid, expired, revoked
    
    @Attribute(name = "cscaCertificate", type = Attribute.Type.BINARY)
    private byte[] certificate;
    
    public CscaCertificate() {}

    public Name getDn() {
        return dn;
    }

    public void setDn(Name dn) {
        this.dn = dn;
    }

    public String getCn() {
        return cn;
    }

    public void setCn(String cn) {
        this.cn = cn;
    }

    public String getCountryCode() {
        return countryCode;
    }

    public void setCountryCode(String countryCode) {
        this.countryCode = countryCode;
    }

    public String getIssuer() {
        return issuer;
    }

    public void setIssuer(String issuer) {
        this.issuer = issuer;
    }

    public String getSerialNumber() {
        return serialNumber;
    }

    public void setSerialNumber(String serialNumber) {
        this.serialNumber = serialNumber;
    }

    public String getFingerprint() {
        return fingerprint;
    }

    public void setFingerprint(String fingerprint) {
        this.fingerprint = fingerprint;
    }

    public String getNotBefore() {
        return notBefore;
    }

    public void setNotBefore(String notBefore) {
        this.notBefore = notBefore;
    }

    public String getNotAfter() {
        return notAfter;
    }

    public void setNotAfter(String notAfter) {
        this.notAfter = notAfter;
    }

    public String getValid() {
        return valid;
    }

    public void setValid(String valid) {
        this.valid = valid;
    }

    public byte[] getCertificate() {
        return certificate;
    }

    public void setCertificate(byte[] certificate) {
        this.certificate = certificate;
    }

}
