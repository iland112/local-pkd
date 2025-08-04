package com.smartcoreinc.localpkd.person.service;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.ldap.core.DirContextAdapter;
import org.springframework.ldap.core.DirContextOperations;
import org.springframework.ldap.core.LdapTemplate;
import org.springframework.test.context.TestPropertySource;

import com.smartcoreinc.localpkd.person.entry.Person;
import com.smartcoreinc.localpkd.person.sevice.PersonService;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@TestPropertySource(properties = {
        "spring.ldap.urls=ldap://192.168.100.10:389",
        "spring.ldap.base=dc=ldap,dc=smartcoreinc,dc=com",
        "spring.ldap.username=cn=admin,dc=ldap,dc=smartcoreinc,dc=com",
        "spring.ldap.password=core"
})
class PersonServiceTest {

    @Autowired
    PersonService personService;

    @Autowired
    private LdapTemplate ldapTemplate;

    @BeforeAll
    static void setUp(@Autowired LdapTemplate ldapTemplate) {
        try {
            // LdapUtils.newLdapName() 사용하여 정확한 DN 생성
            DirContextOperations context = new DirContextAdapter();
            context.setAttributeValues("objectclass", new String[] { "top", "organizationalUnit" });
            context.setAttributeValue("ou", "people");

            ldapTemplate.bind("ou=people", context, null);
        } catch (Exception e) {
            // OU가 이미 존재하는 경우 등 예외는 무시
            System.out.println("OU people already exists or error occurred: " + e.getMessage());
        }
    }

    @AfterEach
    void cleanup() {
        try {
            // 테스트에서 생성한 Person 엔트리 삭제
            ldapTemplate.unbind("cn=test.user,ou=people");
        } catch (Exception e) {
            // 엔트리가 이미 없는 경우 무시
            System.out.println("Clean up failed or entry doesn't exist: " + e.getMessage());
        }
    }

    @Test
    void testCreatePersonDnValidation() {
        // Given
        Person person = new Person();
        person.setCommonName("test.user");
        person.setSurname("User");
        person.setGivenName("Test");
        person.setEmail("test.user@example.com");

        // When
        Person savedPerson = personService.createPerson(person);

        // Then
        assertNotNull(savedPerson);
        assertNotNull(savedPerson.getDn());
        assertEquals("cn=test.user,ou=people", savedPerson.getDn().toString());

        // Verify LDAP entry was created with correct attributes
        DirContextOperations context = ldapTemplate.lookupContext("cn=test.user,ou=people");
        assertNotNull(context);
        assertEquals("test.user", context.getStringAttribute("cn"));
        assertEquals("User", context.getStringAttribute("sn"));
        assertEquals("Test", context.getStringAttribute("givenName"));
        assertEquals("test.user@example.com", context.getStringAttribute("mail"));

        // Verify objectClass attributes
        String[] objectClasses = context.getStringAttributes("objectClass");
        assertNotNull(objectClasses);
        List<String> objectClassList = Arrays.asList(objectClasses);
        assertTrue(objectClassList.contains("top"));
        assertTrue(objectClassList.contains("person"));
        assertTrue(objectClassList.contains("organizationalPerson"));
        assertTrue(objectClassList.contains("inetOrgPerson"));
    }

    @Test
    void testSearchPersons() {
        // Given
        Person person1 = new Person();
        person1.setCommonName("jane.smith");
        person1.setSurname("Smith");
        person1.setGivenName("Jane");
        person1.setEmail("jane.smith@example.com");
        person1.setDepartment("HR");

        Person person2 = new Person();
        person2.setCommonName("bob.smith");
        person2.setSurname("Smith");
        person2.setGivenName("Bob");
        person2.setEmail("bob.smith@example.com");
        person2.setDepartment("Finance");

        // When
        Person savedPerson1 = personService.createPerson(person1);
        Person savedPerson2 = personService.createPerson(person2);

        // Then
        List<Person> smiths = personService.getPersonsBySurname("Smith");
        assertEquals(2, smiths.size());

        List<Person> hrEmployees = personService.getPersonsByDepartment("HR");
        assertEquals(1, hrEmployees.size());
        assertEquals("jane.smith", hrEmployees.get(0).getCommonName());

        // Cleanup
        personService.deletePerson(savedPerson1);
        personService.deletePerson(savedPerson2);
    }

    @Test
    void testUpdatePerson() {
        // Given
        Person person = new Person();
        person.setCommonName("update.test");
        person.setSurname("Test");
        person.setGivenName("Update");
        person.setEmail("update.test@example.com");
        person.setDepartment("QA");

        // When
        Person savedPerson = personService.createPerson(person);
        savedPerson.setTitle("Senior QA Engineer");
        savedPerson.setDescription("Test Automation Specialist");

        Person updatedPerson = personService.updatePerson(savedPerson);

        // Then
        assertEquals("Senior QA Engineer", updatedPerson.getTitle());
        assertEquals("Test Automation Specialist", updatedPerson.getDescription());

        // Cleanup
        personService.deletePerson(updatedPerson);
    }
}