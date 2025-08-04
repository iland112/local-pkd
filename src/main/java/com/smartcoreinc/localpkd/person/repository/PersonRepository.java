package com.smartcoreinc.localpkd.person.repository;

import org.springframework.data.ldap.repository.LdapRepository;
import org.springframework.stereotype.Repository;

import com.smartcoreinc.localpkd.person.entry.Person;

import java.util.List;
import java.util.Optional;

@Repository
public interface PersonRepository extends LdapRepository<Person> {
    
    // Find by common name
    Optional<Person> findByCommonName(String commonName);
    
    // Find by surname
    List<Person> findBySurname(String surname);
    
    // Find by email
    Optional<Person> findByEmail(String email);
    
    // Find by department
    List<Person> findByDepartment(String department);
    
    // Find by title
    List<Person> findByTitle(String title);
    
    // Find by surname containing (case insensitive)
    List<Person> findBySurnameContainingIgnoreCase(String surname);
    
    // Find by common name containing (case insensitive)
    List<Person> findByCommonNameContainingIgnoreCase(String commonName);
}