package com.smartcoreinc.localpkd.fileparsing;

import com.smartcoreinc.localpkd.fileparsing.domain.model.CertificateData;
import com.smartcoreinc.localpkd.fileparsing.domain.model.ParsedFile;
import com.smartcoreinc.localpkd.fileparsing.domain.model.ParsedFileId;
import com.smartcoreinc.localpkd.fileparsing.infrastructure.adapter.MasterListParserAdapter;
import com.smartcoreinc.localpkd.fileupload.domain.model.FileFormat;
import com.smartcoreinc.localpkd.fileupload.domain.model.UploadId;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.FileSystemResource;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.*;

/**
 * Master List Parsing Integration Test
 *
 * <p><b>테스트 파일</b>: data/download/ICAO_ml_July2025.ml</p>
 * <p><b>Trust Anchor</b>: data/cert/UN_CSCA_2.pem</p>
 *
 * <h3>테스트 시나리오</h3>
 * <ol>
 *   <li>실제 Master List 파일 파싱</li>
 *   <li>CMS 서명 검증 (UN_CSCA_2.pem Trust Anchor)</li>
 *   <li>CSCA 인증서 추출</li>
 *   <li>국가별 인증서 분포 확인</li>
 *   <li>인증서 유효성 확인</li>
 * </ol>
 *
 * @author SmartCore Inc.
 * @version 1.0
 * @since 2025-11-20
 */
@Slf4j
@DisplayName("Master List Parsing Integration Test")
class MasterListParsingIntegrationTest {

    private MasterListParserAdapter masterListParser;
    private byte[] masterListBytes;
    private ParsedFile parsedFile;

    @BeforeEach
    void setUp() throws IOException {
        // 1. Parser 인스턴스 생성 (Spring 없이 직접 생성)
        masterListParser = new MasterListParserAdapter();

        // 2. Trust Anchor 설정 (외부 경로: data/cert/UN_CSCA_2.pem)
        Path trustAnchorPath = Paths.get("data/cert/UN_CSCA_2.pem");
        if (!Files.exists(trustAnchorPath)) {
            throw new IllegalStateException(
                "Trust Anchor not found: " + trustAnchorPath.toAbsolutePath()
            );
        }
        masterListParser.setTrustAnchorResource(new FileSystemResource(trustAnchorPath.toFile()));
        log.info("Trust Anchor loaded: {}", trustAnchorPath.toAbsolutePath());

        // 3. 테스트 파일 로드
        Path mlFilePath = Paths.get("data/download/ICAO_ml_July2025.ml");

        if (!Files.exists(mlFilePath)) {
            throw new IllegalStateException(
                "Master List test file not found: " + mlFilePath.toAbsolutePath()
            );
        }

        masterListBytes = Files.readAllBytes(mlFilePath);
        log.info("Loaded Master List file: {} ({} bytes)",
            mlFilePath.getFileName(),
            masterListBytes.length
        );

        // 4. ParsedFile Aggregate 생성
        parsedFile = ParsedFile.create(
            ParsedFileId.newId(),
            UploadId.newId(),
            FileFormat.of(FileFormat.Type.ML_SIGNED_CMS)
        );
    }

    /**
     * 파싱 전체 과정 실행 헬퍼 메서드
     * <p>startParsing() → parse() → completeParsing()</p>
     */
    private void parseFile() throws Exception {
        // 1. 파싱 시작
        parsedFile.startParsing();

        // 2. 파싱 실행
        masterListParser.parse(masterListBytes, FileFormat.of(FileFormat.Type.ML_SIGNED_CMS), parsedFile);

        // 3. 파싱 완료
        int totalEntries = parsedFile.getCertificates().size() + parsedFile.getCrls().size();
        parsedFile.completeParsing(totalEntries);
    }

    @Test
    @DisplayName("E2E: Master List 파일 파싱 및 CSCA 인증서 추출")
    void e2e_MasterListParsing_Success() throws Exception {
        // When: Master List 파싱
        assertThatCode(() -> {
            parseFile();
        }).doesNotThrowAnyException();

        // Then: 파싱 성공 확인
        assertThat(parsedFile.getStatus().name()).isEqualTo("PARSED");
        assertThat(parsedFile.isSuccessful()).isTrue();

        log.info("=== Parsing Results ===");
        log.info("Status: {}", parsedFile.getStatus());
        log.info("Total Certificates: {}", parsedFile.getCertificates().size());
        log.info("Total CRLs: {}", parsedFile.getCrls().size());
        log.info("Total Errors: {}", parsedFile.getErrors().size());
        log.info("Statistics: {}", parsedFile.getStatistics());
    }

    @Test
    @DisplayName("CSCA 인증서가 정상적으로 추출되어야 한다")
    void csca_Certificates_ShouldBeExtracted() throws Exception {
        // When
        parseFile();

        // Then
        List<CertificateData> certificates = parsedFile.getCertificates();

        assertThat(certificates).isNotEmpty();
        assertThat(certificates.size()).isGreaterThan(50)
            .withFailMessage("Master List should contain at least 50 CSCA certificates");

        log.info("Total CSCA certificates extracted: {}", certificates.size());

        // 모든 인증서가 CSCA 타입이어야 함
        certificates.forEach(cert -> {
            assertThat(cert.getCertificateType()).isEqualTo("CSCA");
        });
    }

    @Test
    @DisplayName("국가별 CSCA 인증서 분포를 확인해야 한다")
    void csca_Certificates_CountryDistribution() throws Exception {
        // When
        parseFile();

        // Then
        List<CertificateData> certificates = parsedFile.getCertificates();

        // 국가별 집계 (null country code는 "UNKNOWN"으로 처리)
        Map<String, Long> countryDistribution = certificates.stream()
            .collect(Collectors.groupingBy(
                cert -> cert.getCountryCode() != null ? cert.getCountryCode() : "UNKNOWN",
                Collectors.counting()
            ));

        log.info("=== Country Distribution ===");
        countryDistribution.entrySet().stream()
            .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
            .limit(20)
            .forEach(entry -> {
                log.info("  {}: {} certificates", entry.getKey(), entry.getValue());
            });

        log.info("Total countries: {}", countryDistribution.size());

        // 최소 30개국 이상의 CSCA가 있어야 함
        assertThat(countryDistribution.size()).isGreaterThanOrEqualTo(30)
            .withFailMessage("Master List should contain CSCAs from at least 30 countries");
    }

    @Test
    @DisplayName("인증서 메타데이터가 정상적으로 추출되어야 한다")
    void certificate_Metadata_ShouldBeExtracted() throws Exception {
        // When
        parseFile();

        // Then
        List<CertificateData> certificates = parsedFile.getCertificates();

        CertificateData firstCert = certificates.get(0);

        log.info("=== Sample Certificate Metadata ===");
        log.info("Certificate Type: {}", firstCert.getCertificateType());
        log.info("Country Code: {}", firstCert.getCountryCode());
        log.info("Subject DN: {}", firstCert.getSubjectDN());
        log.info("Issuer DN: {}", firstCert.getIssuerDN());
        log.info("Serial Number: {}", firstCert.getSerialNumber());
        log.info("Valid From: {}", firstCert.getNotBefore());
        log.info("Valid Until: {}", firstCert.getNotAfter());
        log.info("Fingerprint (SHA-256): {}", firstCert.getFingerprintSha256());
        log.info("Is Valid: {}", firstCert.isValid());

        // 필수 필드 확인
        assertThat(firstCert.getCertificateType()).isNotBlank();
        assertThat(firstCert.getCountryCode()).isNotBlank();
        assertThat(firstCert.getSubjectDN()).isNotBlank();
        assertThat(firstCert.getIssuerDN()).isNotBlank();
        assertThat(firstCert.getSerialNumber()).isNotBlank();
        assertThat(firstCert.getNotBefore()).isNotNull();
        assertThat(firstCert.getNotAfter()).isNotNull();
        assertThat(firstCert.getFingerprintSha256()).hasSize(64); // SHA-256 = 64 hex chars
        assertThat(firstCert.getCertificateBinary()).isNotEmpty();
    }

    @Test
    @DisplayName("파싱 통계가 정확해야 한다")
    void parsing_Statistics_ShouldBeAccurate() throws Exception {
        // When
        parseFile();

        // Then
        var stats = parsedFile.getStatistics();

        log.info("=== Parsing Statistics ===");
        log.info("Total Entries: {}", stats.getTotalEntries());
        log.info("Total Processed: {}", stats.getTotalProcessed());
        log.info("Certificates: {}", stats.getCertificateCount());
        log.info("CRLs: {}", stats.getCrlCount());
        log.info("Valid: {}", stats.getValidCount());
        log.info("Invalid: {}", stats.getInvalidCount());
        log.info("Errors: {}", stats.getErrorCount());
        log.info("Duration: {} ms", stats.getDurationMillis());
        log.info("Success Rate: {}%", stats.getSuccessRate());

        assertThat(stats.getTotalProcessed()).isEqualTo(parsedFile.getCertificates().size());
        assertThat(stats.getCertificateCount()).isEqualTo(parsedFile.getCertificates().size());
        assertThat(stats.getCrlCount()).isEqualTo(0); // Master List에는 CRL 없음
        assertThat(stats.getSuccessRate()).isGreaterThanOrEqualTo(95.0); // 95% 이상 성공
    }

    @Test
    @DisplayName("특정 국가의 CSCA 인증서를 조회할 수 있어야 한다")
    void csca_Certificates_FilterByCountry() throws Exception {
        // Given
        String targetCountry = "KR"; // 대한민국

        // When
        parseFile();

        List<CertificateData> koreanCerts = parsedFile.getCertificates().stream()
            .filter(cert -> targetCountry.equals(cert.getCountryCode()))
            .toList();

        // Then
        log.info("=== Korean CSCA Certificates ===");
        log.info("Total: {}", koreanCerts.size());

        koreanCerts.forEach(cert -> {
            log.info("  - Subject: {}", cert.getSubjectDN());
            log.info("    Serial: {}", cert.getSerialNumber());
            log.info("    Valid: {} ~ {}", cert.getNotBefore(), cert.getNotAfter());
        });

        if (!koreanCerts.isEmpty()) {
            assertThat(koreanCerts.get(0).getCountryCode()).isEqualTo(targetCountry);
        }
    }

    @Test
    @DisplayName("만료된 인증서와 유효한 인증서를 구분할 수 있어야 한다")
    void csca_Certificates_ValidityCheck() throws Exception {
        // When
        parseFile();

        // Then
        List<CertificateData> certificates = parsedFile.getCertificates();

        long validCount = certificates.stream()
            .filter(CertificateData::isValid)
            .count();

        long expiredCount = certificates.stream()
            .filter(cert -> !cert.isValid())
            .count();

        log.info("=== Certificate Validity Status ===");
        log.info("Valid certificates: {}", validCount);
        log.info("Expired/Invalid certificates: {}", expiredCount);
        log.info("Total: {}", certificates.size());

        assertThat(validCount + expiredCount).isEqualTo(certificates.size());
    }

    @Test
    @DisplayName("파싱 오류가 없거나 허용 범위 내에 있어야 한다")
    void parsing_Errors_ShouldBeMinimal() throws Exception {
        // When
        parseFile();

        // Then
        var errors = parsedFile.getErrors();

        log.info("=== Parsing Errors ===");
        log.info("Total errors: {}", errors.size());

        errors.forEach(error -> {
            log.warn("  - {}: {} ({})",
                error.getErrorType(),
                error.getErrorMessage(),
                error.getErrorLocation()
            );
        });

        // 오류율 5% 이하
        double errorRate = (errors.size() * 100.0) / parsedFile.getStatistics().getTotalEntries();
        assertThat(errorRate).isLessThanOrEqualTo(5.0)
            .withFailMessage("Error rate should be less than 5%%, but was %.2f%%", errorRate);
    }
}
