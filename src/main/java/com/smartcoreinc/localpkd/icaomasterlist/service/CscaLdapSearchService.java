package com.smartcoreinc.localpkd.icaomasterlist.service;

import java.util.List;

import org.springframework.stereotype.Service;

import com.smartcoreinc.localpkd.icaomasterlist.entity.CscaCertificate;
import com.smartcoreinc.localpkd.icaomasterlist.repository.CscaCertificateRepository;

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

    /**
     * 특정 국가 코드(countryCode)에 해당하는 모든 CSCA 인증서를 검색합니다.
     * @param countryCode 검색할 국가 코드 (예: "AE")
     * @return 해당 국가의 CSCA 인증서 리스트
     */
    public List<CscaCertificate> findCscaCertificatesByCountryCode(String countryCode) {
        log.info("Searching for CSCA certificates for country: {}", countryCode);
        return cscaCertificateRepository.findByCountryCode(countryCode);
    }

}
