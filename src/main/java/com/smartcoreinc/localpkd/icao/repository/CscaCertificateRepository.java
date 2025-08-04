package com.smartcoreinc.localpkd.icao.repository;

import org.springframework.data.ldap.repository.LdapRepository;
import org.springframework.stereotype.Repository;

import com.smartcoreinc.localpkd.icao.entity.CscaCertificate;

@Repository
public interface CscaCertificateRepository extends LdapRepository<CscaCertificate> {
}
