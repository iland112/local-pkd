package com.smartcoreinc.localpkd.icaomasterlist.repository;

import org.springframework.data.ldap.repository.LdapRepository;
import org.springframework.stereotype.Repository;

import com.smartcoreinc.localpkd.icaomasterlist.entity.CscaCertificate;

@Repository
public interface CscaCertificateRepository extends LdapRepository<CscaCertificate> {
}
