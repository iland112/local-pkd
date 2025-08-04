package com.smartcoreinc.localpkd.person.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.smartcoreinc.localpkd.person.entry.Person;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class PersonResponseDto {
    
    private String commonName;
    private String surname;
    private String givenName;
    private String email;
    private String telephoneNumber;
    private String title;
    private String department;
    private String description;
    private String dn;
    
    public PersonResponseDto() {}
    
    public PersonResponseDto(Person person) {
        this.commonName = person.getCommonName();
        this.surname = person.getSurname();
        this.givenName = person.getGivenName();
        this.email = person.getEmail();
        this.telephoneNumber = person.getTelephoneNumber();
        this.title = person.getTitle();
        this.department = person.getDepartment();
        this.description = person.getDescription();
        this.dn = person.getDn() != null ? person.getDn().toString() : null;
    }
    
    public static PersonResponseDto from(Person person) {
        return new PersonResponseDto(person);
    }
    
    // Getters and Setters
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
    
    public String getDn() {
        return dn;
    }
    
    public void setDn(String dn) {
        this.dn = dn;
    }
}