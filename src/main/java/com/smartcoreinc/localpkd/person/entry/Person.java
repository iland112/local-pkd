package com.smartcoreinc.localpkd.person.entry;

import org.springframework.ldap.odm.annotations.Attribute;
import org.springframework.ldap.odm.annotations.DnAttribute;
import org.springframework.ldap.odm.annotations.Entry;
import org.springframework.ldap.odm.annotations.Id;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.smartcoreinc.localpkd.ldaphelper.NameDeserializer;
import com.smartcoreinc.localpkd.ldaphelper.NameSerializer;

import jakarta.validation.constraints.NotBlank;
import javax.naming.Name;

@JsonIgnoreProperties(ignoreUnknown = true)
@Entry(base = "ou=people", objectClasses = {"inetOrgPerson", "organizationalPerson", "person", "top"})
public class Person {
    
    @Id
    @JsonSerialize(using = NameSerializer.class)
    @JsonDeserialize(using = NameDeserializer.class)
    private Name dn;
    
    @Attribute(name = "cn")
    @DnAttribute(value = "cn")
    @NotBlank
    private String commonName;
    
    @Attribute(name = "sn")
    @NotBlank
    private String surname;
    
    @Attribute(name = "givenName")
    private String givenName;
    
    @Attribute(name = "mail")
    private String email;
    
    @Attribute(name = "telephoneNumber")
    private String telephoneNumber;
    
    @Attribute(name = "title")
    private String title;
    
    @Attribute(name = "departmentNumber")
    private String department;
    
    @Attribute(name = "description")
    private String description;
    
    // Constructors
    public Person() {}
    
    public Person(String commonName, String surname) {
        this.commonName = commonName;
        this.surname = surname;
    }
    
    // Getters and Setters
    public Name getDn() {
        return dn;
    }
    
    public void setDn(Name dn) {
        this.dn = dn;
    }
    
    public String getCommonName() {
        return commonName;
    }
    
    public void setCommonName(String commonName) {
        this.commonName = commonName;
    }
    
    public String getSurname() {
        return surname;
    }
    
    public void setSurname(String surname) {
        this.surname = surname;
    }
    
    public String getGivenName() {
        return givenName;
    }
    
    public void setGivenName(String givenName) {
        this.givenName = givenName;
    }
    
    public String getEmail() {
        return email;
    }
    
    public void setEmail(String email) {
        this.email = email;
    }
    
    public String getTelephoneNumber() {
        return telephoneNumber;
    }
    
    public void setTelephoneNumber(String telephoneNumber) {
        this.telephoneNumber = telephoneNumber;
    }
    
    public String getTitle() {
        return title;
    }
    
    public void setTitle(String title) {
        this.title = title;
    }
    
    public String getDepartment() {
        return department;
    }
    
    public void setDepartment(String department) {
        this.department = department;
    }
    
    public String getDescription() {
        return description;
    }
    
    public void setDescription(String description) {
        this.description = description;
    }
    
    @Override
    public String toString() {
        return "Person{" +
                "dn=" + dn +
                ", commonName='" + commonName + '\'' +
                ", surname='" + surname + '\'' +
                ", givenName='" + givenName + '\'' +
                ", email='" + email + '\'' +
                ", telephoneNumber='" + telephoneNumber + '\'' +
                ", title='" + title + '\'' +
                ", department='" + department + '\'' +
                ", description='" + description + '\'' +
                '}';
    }
}