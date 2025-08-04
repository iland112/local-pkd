package com.smartcoreinc.localpkd.icao.service;

import java.util.List;

import org.springframework.stereotype.Service;

import com.smartcoreinc.localpkd.icao.entity.CscaCertificate;
import com.smartcoreinc.localpkd.icao.repository.CscaCertificateRepository;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
public class CscaLdapSearchService {

    private final CscaCertificateRepository cscaCertificateRepository;

    public CscaLdapSearchService(CscaCertificateRepository cscaCertificateRepository) {
        this.cscaCertificateRepository = cscaCertificateRepository;
    }

    public List<CscaCertificate> findAll() {
        return cscaCertificateRepository.findAll();
    }

}
