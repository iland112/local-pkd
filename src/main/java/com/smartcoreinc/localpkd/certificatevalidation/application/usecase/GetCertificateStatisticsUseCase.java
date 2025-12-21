package com.smartcoreinc.localpkd.certificatevalidation.application.usecase;

import com.smartcoreinc.localpkd.certificatevalidation.application.response.CertificateStatisticsResponse;
import com.smartcoreinc.localpkd.certificatevalidation.domain.model.CountryCount;
import com.smartcoreinc.localpkd.certificatevalidation.domain.model.TypeCount;
import com.smartcoreinc.localpkd.certificatevalidation.domain.repository.CertificateRepository;
import com.smartcoreinc.localpkd.fileparsing.domain.repository.MasterListRepository;
import com.smartcoreinc.localpkd.fileparsing.infrastructure.repository.SpringDataParsedFileRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class GetCertificateStatisticsUseCase {

    private final SpringDataParsedFileRepository springDataParsedFileRepository;
    private final CertificateRepository certificateRepository;
    private final MasterListRepository masterListRepository;

    public CertificateStatisticsResponse execute() {
        long totalCertificates = certificateRepository.countAllBy();
        long validCertificates = certificateRepository.countByStatus("VALID");
        long invalidCertificates = totalCertificates - validCertificates;
        long totalCrls = springDataParsedFileRepository.countAllCrls();

        List<TypeCount> typeCounts = certificateRepository.countCertificatesByType();
        Map<String, Long> certificatesByType = typeCounts.stream()
                .collect(Collectors.toMap(tc -> tc.type().name(), TypeCount::count));

        // CRL 개수 추가
        if (totalCrls > 0) {
            certificatesByType.put("CRL", totalCrls);
        }

        // Master List 개수 추가
        long totalMasterLists = masterListRepository.count();
        if (totalMasterLists > 0) {
            certificatesByType.put("Master List", totalMasterLists);
        }

        List<CountryCount> countryCounts = certificateRepository.countCertificatesByCountry();
        Map<String, Long> certificatesByCountry = countryCounts.stream()
                .collect(Collectors.toMap(CountryCount::country, CountryCount::count));

        LocalDateTime lastUpdated = LocalDateTime.now();

        return CertificateStatisticsResponse.builder()
                .totalCertificates(totalCertificates)
                .validCertificates(validCertificates)
                .invalidCertificates(invalidCertificates)
                .totalCrls(totalCrls)
                .certificatesByType(certificatesByType)
                .certificatesByCountry(certificatesByCountry)
                .lastUpdated(lastUpdated)
                .build();
    }
}
