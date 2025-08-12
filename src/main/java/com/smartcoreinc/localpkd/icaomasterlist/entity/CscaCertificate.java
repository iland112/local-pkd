package com.smartcoreinc.localpkd.icaomasterlist.entity;

import javax.naming.Name;

import org.springframework.ldap.odm.annotations.Attribute;
import org.springframework.ldap.odm.annotations.Entry;
import org.springframework.ldap.odm.annotations.Id;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.smartcoreinc.localpkd.ldaphelper.NameDeserializer;
import com.smartcoreinc.localpkd.ldaphelper.NameSerializer;

@Entry(base = "dc=ml-data,dc=download,dc=pkd", objectClasses = {"top", "person", "organizationalPerson", "inetOrgPerson", "pkiCA"})
public final class CscaCertificate {

    @Id
    @JsonSerialize(using = NameSerializer.class)
    @JsonDeserialize(using = NameDeserializer.class)
    private Name dn;

    @Attribute(name = "gn")
    private String subjectName;

    @Attribute(name = "ou")
    private String issuerName;

    @Attribute(name = "sn")
    private String seralNumber;

    @Attribute(name = "description")
    private String valid;
    
    @Attribute(name = "cACertificate;binary", type = Attribute.Type.BINARY)
    private byte[] certificate;
    
    public CscaCertificate() {}

    public Name getDn() {
        return dn;
    }

    public void setDn(Name dn) {
        this.dn = dn;
    }

    public String getSubjectName() {
        return subjectName;
    }

    public void setSubjectName(String subjectName) {
        this.subjectName = subjectName;
    }

    public String getIssuerName() {
        return issuerName;
    }

    public void setIssuerName(String issuerName) {
        this.issuerName = issuerName;
    }

    public String getSeralNumber() {
        return seralNumber;
    }

    public void setSeralNumber(String seralNumber) {
        this.seralNumber = seralNumber;
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
