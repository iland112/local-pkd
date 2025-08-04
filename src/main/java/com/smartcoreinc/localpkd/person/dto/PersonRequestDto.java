package com.smartcoreinc.localpkd.person.dto;

import com.smartcoreinc.localpkd.person.entry.Person;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public class PersonRequestDto {
    
    @NotBlank(message = "공통 이름은 필수입니다.")
    @Size(min = 2, max = 50, message = "공통 이름은 2자 이상 50자 이하여야 합니다.")
    private String commonName;
    
    @NotBlank(message = "성은 필수입니다.")
    @Size(min = 1, max = 30, message = "성은 1자 이상 30자 이하여야 합니다.")
    private String surname;
    
    @Size(max = 30, message = "이름은 30자 이하여야 합니다.")
    private String givenName;
    
    @Email(message = "유효한 이메일 형식이어야 합니다.")
    private String email;
    
    @Size(max = 20, message = "전화번호는 20자 이하여야 합니다.")
    private String telephoneNumber;
    
    @Size(max = 50, message = "직책은 50자 이하여야 합니다.")
    private String title;
    
    @Size(max = 30, message = "부서는 30자 이하여야 합니다.")
    private String department;
    
    @Size(max = 200, message = "설명은 200자 이하여야 합니다.")
    private String description;
    
    public PersonRequestDto() {}
    
    public Person toEntity() {
        Person person = new Person();
        person.setCommonName(this.commonName);
        person.setSurname(this.surname);
        person.setGivenName(this.givenName);
        person.setEmail(this.email);
        person.setTelephoneNumber(this.telephoneNumber);
        person.setTitle(this.title);
        person.setDepartment(this.department);
        person.setDescription(this.description);
        return person;
    }
    
    public void updateEntity(Person person) {
        person.setSurname(this.surname);
        person.setGivenName(this.givenName);
        person.setEmail(this.email);
        person.setTelephoneNumber(this.telephoneNumber);
        person.setTitle(this.title);
        person.setDepartment(this.department);
        person.setDescription(this.description);
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
}
