package com.smartcoreinc.localpkd.person.sevice;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.ldap.core.DirContextAdapter;
import org.springframework.ldap.core.LdapTemplate;
import org.springframework.ldap.support.LdapNameBuilder;
import org.springframework.stereotype.Service;

import com.smartcoreinc.localpkd.person.entry.Person;
import com.smartcoreinc.localpkd.person.repository.PersonRepository;

import java.util.List;
import java.util.Optional;

@Service
public class PersonService {

    @Autowired
    private PersonRepository personRepository;

    @Autowired
    private LdapTemplate ldapTemplate;

    public List<Person> getAllPersons() {
        return (List<Person>) personRepository.findAll();
    }

    public Optional<Person> getPersonByCommonName(String commonName) {
        return personRepository.findByCommonName(commonName);
    }

    public List<Person> getPersonsBySurname(String surname) {
        return personRepository.findBySurname(surname);
    }

    public Optional<Person> getPersonByEmail(String email) {
        return personRepository.findByEmail(email);
    }

    public List<Person> getPersonsByDepartment(String department) {
        return personRepository.findByDepartment(department);
    }

    public List<Person> searchPersonsBySurname(String surname) {
        return personRepository.findBySurnameContainingIgnoreCase(surname);
    }

    public List<Person> searchPersonsByCommonName(String commonName) {
        return personRepository.findByCommonNameContainingIgnoreCase(commonName);
    }

    public Person createPerson(Person person) {
        try {
            // Create DirContextAdapter for the new entry
            DirContextAdapter context = new DirContextAdapter();
            
            // Set objectClass attributes
            context.setAttributeValues("objectClass", 
                new String[] {"top", "person", "organizationalPerson", "inetOrgPerson"});
            
            // Set mandatory attributes
            context.setAttributeValue("cn", person.getCommonName());
            context.setAttributeValue("sn", person.getSurname());
            
            // Set optional attributes if present
            if (person.getGivenName() != null) {
                context.setAttributeValue("givenName", person.getGivenName());
            }
            if (person.getEmail() != null) {
                context.setAttributeValue("mail", person.getEmail());
            }
            if (person.getTelephoneNumber() != null) {
                context.setAttributeValue("telephoneNumber", person.getTelephoneNumber());
            }
            if (person.getTitle() != null) {
                context.setAttributeValue("title", person.getTitle());
            }
            if (person.getDepartment() != null) {
                context.setAttributeValue("departmentNumber", person.getDepartment());
            }
            if (person.getDescription() != null) {
                context.setAttributeValue("description", person.getDescription());
            }
            
            // Create relative DN for the new entry
            String rdn = "cn=" + person.getCommonName() + ",ou=people";
            
            // Bind the new entry
            ldapTemplate.bind(rdn, context, null);
            
            // Set DN in person object and return
            person.setDn(LdapNameBuilder.newInstance()
                .add("ou", "people")
                .add("cn", person.getCommonName())
                .build());
                
            return person;
        } catch (Exception e) {
            throw new RuntimeException("Error creating person: " + e.getMessage(), e);
        }
    }

    public Person updatePerson(Person person) {
        return personRepository.save(person);
    }

    public void deletePerson(Person person) {
        personRepository.delete(person);
    }

    public void deletePersonByCommonName(String commonName) {
        Optional<Person> person = personRepository.findByCommonName(commonName);
        person.ifPresent(personRepository::delete);
    }

    public boolean existsByCommonName(String commonName) {
        return personRepository.findByCommonName(commonName).isPresent();
    }

    public boolean existsByEmail(String email) {
        return personRepository.findByEmail(email).isPresent();
    }

    public long countPersons() {
        return personRepository.count();
    }
}
