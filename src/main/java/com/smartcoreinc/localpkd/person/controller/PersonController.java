package com.smartcoreinc.localpkd.person.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import com.smartcoreinc.localpkd.person.entry.Person;
import com.smartcoreinc.localpkd.person.sevice.PersonService;

import jakarta.validation.Valid;

import java.util.List;
import java.util.Optional;

@RestController
@RequestMapping("/api/persons")
@CrossOrigin(origins = "*")
public class PersonController {
    
    @Autowired
    private PersonService personService;
    
    // 모든 사람 조회
    @GetMapping
    public ResponseEntity<List<Person>> getAllPersons() {
        List<Person> persons = personService.getAllPersons();
        for (Person person : persons) {
            System.out.println(person.toString());
        }
        return ResponseEntity.ok(persons);
    }
    
    // 공통 이름으로 조회
    @GetMapping("/cn/{commonName}")
    public ResponseEntity<Person> getPersonByCommonName(@PathVariable String commonName) {
        Optional<Person> person = personService.getPersonByCommonName(commonName);
        return person.map(ResponseEntity::ok)
                    .orElse(ResponseEntity.notFound().build());
    }
    
    // 성으로 조회
    @GetMapping("/surname/{surname}")
    public ResponseEntity<List<Person>> getPersonsBySurname(@PathVariable String surname) {
        List<Person> persons = personService.getPersonsBySurname(surname);
        return ResponseEntity.ok(persons);
    }
    
    // 이메일로 조회
    @GetMapping("/email/{email}")
    public ResponseEntity<Person> getPersonByEmail(@PathVariable String email) {
        Optional<Person> person = personService.getPersonByEmail(email);
        return person.map(ResponseEntity::ok)
                    .orElse(ResponseEntity.notFound().build());
    }
    
    // 부서로 조회
    @GetMapping("/department/{department}")
    public ResponseEntity<List<Person>> getPersonsByDepartment(@PathVariable String department) {
        List<Person> persons = personService.getPersonsByDepartment(department);
        return ResponseEntity.ok(persons);
    }
    
    // 성으로 검색 (부분 일치)
    @GetMapping("/search/surname")
    public ResponseEntity<List<Person>> searchPersonsBySurname(@RequestParam String surname) {
        List<Person> persons = personService.searchPersonsBySurname(surname);
        return ResponseEntity.ok(persons);
    }
    
    // 공통 이름으로 검색 (부분 일치)
    @GetMapping("/search/cn")
    public ResponseEntity<List<Person>> searchPersonsByCommonName(@RequestParam String commonName) {
        List<Person> persons = personService.searchPersonsByCommonName(commonName);
        return ResponseEntity.ok(persons);
    }
    
    // 새로운 사람 생성
    @PostMapping
    public ResponseEntity<Person> createPerson(@Valid @RequestBody Person person) {
        try {
            // 중복 체크
            if (personService.existsByCommonName(person.getCommonName())) {
                return ResponseEntity.status(HttpStatus.CONFLICT).build();
            }
            
            Person createdPerson = personService.createPerson(person);
            return ResponseEntity.status(HttpStatus.CREATED).body(createdPerson);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }
    
    // 사람 정보 수정
    @PutMapping("/cn/{commonName}")
    public ResponseEntity<Person> updatePerson(@PathVariable String commonName, 
                                             @Valid @RequestBody Person person) {
        try {
            Optional<Person> existingPerson = personService.getPersonByCommonName(commonName);
            if (existingPerson.isEmpty()) {
                return ResponseEntity.notFound().build();
            }
            
            Person existing = existingPerson.get();
            // DN 유지
            person.setDn(existing.getDn());
            person.setCommonName(commonName); // CN은 변경하지 않음
            
            Person updatedPerson = personService.updatePerson(person);
            return ResponseEntity.ok(updatedPerson);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }
    
    // 사람 삭제
    @DeleteMapping("/cn/{commonName}")
    public ResponseEntity<Void> deletePerson(@PathVariable String commonName) {
        try {
            Optional<Person> person = personService.getPersonByCommonName(commonName);
            if (person.isEmpty()) {
                return ResponseEntity.notFound().build();
            }
            
            personService.deletePerson(person.get());
            return ResponseEntity.noContent().build();
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }
    
    // 사람 존재 여부 확인
    @GetMapping("/exists/cn/{commonName}")
    public ResponseEntity<Boolean> existsByCommonName(@PathVariable String commonName) {
        boolean exists = personService.existsByCommonName(commonName);
        return ResponseEntity.ok(exists);
    }
    
    // 전체 사람 수 조회
    @GetMapping("/count")
    public ResponseEntity<Long> countPersons() {
        long count = personService.countPersons();
        return ResponseEntity.ok(count);
    }
}
