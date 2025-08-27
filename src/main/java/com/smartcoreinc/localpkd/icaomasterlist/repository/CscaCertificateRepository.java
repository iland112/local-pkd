package com.smartcoreinc.localpkd.icaomasterlist.repository;

import java.util.List;

import org.springframework.data.ldap.repository.LdapRepository;
import org.springframework.stereotype.Repository;

import com.smartcoreinc.localpkd.icaomasterlist.entity.CscaCertificate;

@Repository
public interface CscaCertificateRepository extends LdapRepository<CscaCertificate> {

    /**
     * 특정 국가 코드(countryCode)에 해당하는 모든 CSCA 인증서를 조회합니다.
     * Spring Data LDAP의 Repository Method Query 기능을 활용합니다.
     */
    List<CscaCertificate> findByCountryCode(String countryCode);
}
