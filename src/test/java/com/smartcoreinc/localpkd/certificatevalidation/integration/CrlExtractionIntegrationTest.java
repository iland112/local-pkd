package com.smartcoreinc.localpkd.certificatevalidation.integration;

import com.smartcoreinc.localpkd.certificatevalidation.domain.model.*;
import com.smartcoreinc.localpkd.certificatevalidation.domain.repository.CertificateRevocationListRepository;
import com.smartcoreinc.localpkd.certificatevalidation.infrastructure.adapter.BouncyCastleValidationAdapter;
import com.smartcoreinc.localpkd.certificatevalidation.infrastructure.repository.JpaCertificateRevocationListRepository;
import com.smartcoreinc.localpkd.fileupload.domain.model.FileFormat;
import com.smartcoreinc.localpkd.fileparsing.domain.model.ParsedFile;
import com.smartcoreinc.localpkd.fileparsing.domain.model.ParsedFileId;
import com.smartcoreinc.localpkd.fileparsing.infrastructure.adapter.LdifParserAdapter;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;

import static org.assertj.core.api.Assertions.*;

/**
 * CRL 추출 End-to-End 통합 테스트
 *
 * <p><b>테스트 범위</b>:</p>
 * <ol>
 *   <li>LDIF 파일 파싱</li>
 *   <li>CRL 추출</li>
 *   <li>CRL Aggregate 생성</li>
 *   <li>데이터베이스 저장 (배치)</li>
 *   <li>이벤트 발행 검증</li>
 *   <li>메트릭 수집</li>
 * </ol>
 *
 * <p><b>사용 데이터</b>:</p>
 * <ul>
 *   <li>실제 LDIF 파일: 9.6MB (eMRTD Complete LDIF)</li>
 *   <li>예상 CRL 개수: 69개</li>
 *   <li>예상 폐기 인증서: ~47,000개</li>
 * </ul>
 *
 * <p><b>성능 목표</b>:</p>
 * <ul>
 *   <li>LDIF 파싱: &lt; 5 seconds</li>
 *   <li>CRL 저장: &lt; 2 seconds (69개)</li>
 *   <li>전체 프로세스: &lt; 10 seconds</li>
 * </ul>
 *
 * @author SmartCore Inc.
 * @since 2025-10-24
 */
@Slf4j
@DisplayName("CRL 추출 End-to-End 통합 테스트")
@DataJpaTest
@Import({
    JpaCertificateRevocationListRepository.class,
    LdifParserAdapter.class,
    BouncyCastleValidationAdapter.class
})
@ActiveProfiles("test")
class CrlExtractionIntegrationTest {

    @Autowired
    private CertificateRevocationListRepository crlRepository;

    @Autowired
    private TestEntityManager entityManager;

    @Autowired
    private LdifParserAdapter ldifParserAdapter;

    // ========== Test Data ==========

    private static final String TEST_LDIF_PATH =
        "data/uploads/ldif/emrtd-complete/20251022_182133_icaopkd-002-complete-000323.ldif";
    private static final int EXPECTED_CRL_COUNT = 69;
    private static final FileFormat TEST_FILE_FORMAT = FileFormat.of(FileFormat.Type.EMRTD_COMPLETE_LDIF);

    // ========== Helper Methods ==========

    /**
     * Issuer DN에서 CSCA 이름 추출 (CN=CSCA-XX 형식)
     * 예: "CN=CSCA-KR,C=KR" → "CSCA-KR"
     */
    private String extractIssuerName(String issuerDN) {
        if (issuerDN == null) {
            return "CSCA-XX";
        }
        java.util.regex.Pattern pattern = java.util.regex.Pattern.compile("CN=([^,]+)");
        java.util.regex.Matcher matcher = pattern.matcher(issuerDN);
        if (matcher.find()) {
            String cn = matcher.group(1);
            if (cn.matches("^CSCA-[A-Z]{2}$")) {
                return cn;
            }
        }
        return "CSCA-XX";
    }

    // ========== Integration Tests ==========

    @Test
    @DisplayName("E2E: LDIF 파일 파싱 → CRL 추출 → DB 저장")
    void e2e_LdifParsing_CrlExtraction_DatabaseStorage_Success() throws Exception {
        // Test가 선택적으로 실행되도록 파일 존재 확인
        Path ldifPath = Paths.get(TEST_LDIF_PATH);
        if (!Files.exists(ldifPath)) {
            log.warn("Test LDIF file not found, skipping E2E test: {}", ldifPath);
            // 테스트 환경에서는 파일이 없을 수 있으므로 skip
            return;
        }

        log.info("=== E2E Integration Test Started ===");
        long startTime = System.currentTimeMillis();

        try {
            // 1️⃣ LDIF 파일 읽기
            log.info("Step 1: Reading LDIF file...");
            byte[] ldifContent = Files.readAllBytes(ldifPath);
            log.info("  - File size: {} bytes ({} MB)", ldifContent.length, ldifContent.length / 1024 / 1024);

            // 2️⃣ LDIF 파싱
            log.info("Step 2: Parsing LDIF file...");
            long parseStartTime = System.currentTimeMillis();

            ParsedFileId parsedFileId = ParsedFileId.newId();
            ParsedFile parsedFile = ParsedFile.create(parsedFileId, null, TEST_FILE_FORMAT);
            ldifParserAdapter.parse(ldifContent, TEST_FILE_FORMAT, parsedFile);

            long parseEndTime = System.currentTimeMillis();
            long parseTime = parseEndTime - parseStartTime;

            log.info("  - Parsing completed in {} ms", parseTime);
            log.info("  - Certificates extracted: {}", parsedFile.getCertificates().size());
            log.info("  - CRLs extracted: {}", parsedFile.getCrls().size());

            // 3️⃣ CRL 데이터 검증
            assertThat(parsedFile.getCrls())
                .as("CRL 추출 개수 검증")
                .hasSizeGreaterThanOrEqualTo(EXPECTED_CRL_COUNT - 5)  // 예상치의 95% 이상
                .hasSizeLessThanOrEqualTo(EXPECTED_CRL_COUNT + 5);

            // 4️⃣ CRL Aggregate Root 생성
            log.info("Step 3: Creating CRL Aggregate Roots...");
            List<CertificateRevocationList> crls = new java.util.ArrayList<>();
            for (var crlData : parsedFile.getCrls()) {
                try {
                    CertificateRevocationList crl = CertificateRevocationList.create(
                        parsedFile.getId().getId(),  // uploadId (Phase 17)
                        CrlId.newId(),
                        IssuerName.of(extractIssuerName(crlData.getIssuerDN())),
                        CountryCode.of(crlData.getCountryCode()),
                        ValidityPeriod.of(crlData.getThisUpdate(), crlData.getNextUpdate()),
                        X509CrlData.of(crlData.getCrlBinary(), crlData.getRevokedCertificatesCount()),
                        RevokedCertificates.empty()
                    );
                    crls.add(crl);
                } catch (Exception e) {
                    log.warn("Failed to create CRL Aggregate: issuer={}, country={}",
                        crlData.getIssuerDN(), crlData.getCountryCode(), e);
                }
            }

            log.info("  - Valid CRL Aggregates created: {}", crls.size());

            // 5️⃣ 배치 저장
            log.info("Step 4: Batch saving CRLs to database...");
            long saveStartTime = System.currentTimeMillis();

            List<CertificateRevocationList> savedCrls = crlRepository.saveAll(crls);
            entityManager.flush();
            entityManager.clear();

            long saveEndTime = System.currentTimeMillis();
            long saveTime = saveEndTime - saveStartTime;

            log.info("  - CRLs saved in {} ms", saveTime);
            log.info("  - Success count: {}", savedCrls.size());

            // 6️⃣ DB에서 검증
            log.info("Step 5: Verifying data in database...");
            long dbVerifyCount = crlRepository.count();
            log.info("  - Total CRLs in database: {}", dbVerifyCount);

            // 7️⃣ 특정 CRL 조회 검증
            if (!savedCrls.isEmpty()) {
                CertificateRevocationList firstCrl = savedCrls.get(0);
                var queriedCrl = crlRepository.findByIssuerNameAndCountry(
                    firstCrl.getIssuerName().getValue(),
                    firstCrl.getCountryCode().getValue()
                );

                assertThat(queriedCrl)
                    .as("첫 번째 CRL 조회 검증")
                    .isPresent()
                    .hasValueSatisfying(crl ->
                        assertThat(crl.getId()).isEqualTo(firstCrl.getId())
                    );

                log.info("  - Sample CRL query successful");
                log.info("    * Issuer: {}", firstCrl.getIssuerName().getValue());
                log.info("    * Country: {}", firstCrl.getCountryCode().getValue());
                log.info("    * Revoked count: {}", firstCrl.getRevokedCount());
            }

            // 8️⃣ 통계 수집
            long totalTime = System.currentTimeMillis() - startTime;
            long totalRevokedCertificates = savedCrls.stream()
                .mapToLong(crl -> (long) crl.getRevokedCount())
                .sum();

            log.info("=== E2E Integration Test Completed ===");
            log.info("Performance Metrics:");
            log.info("  - LDIF Parsing: {} ms", parseTime);
            log.info("  - CRL Batch Save: {} ms", saveTime);
            log.info("  - Total Time: {} ms", totalTime);
            log.info("  - CRLs Saved: {}", savedCrls.size());
            log.info("  - Total Revoked Certificates: {}", totalRevokedCertificates);
            log.info("  - Average Revoked per CRL: {}", totalRevokedCertificates / Math.max(1, savedCrls.size()));

            // 9️⃣ 성능 검증
            assertThat(parseTime)
                .as("LDIF 파싱 성능")
                .isLessThan(10000);  // 10초 이내

            assertThat(saveTime)
                .as("CRL 저장 성능")
                .isLessThan(5000);  // 5초 이내

            assertThat(totalTime)
                .as("전체 프로세스 성능")
                .isLessThan(20000);  // 20초 이내

            // ✅ 최종 검증
            assertThat(savedCrls).isNotEmpty();
            assertThat(totalRevokedCertificates).isGreaterThan(0);

        } catch (IOException e) {
            log.error("Failed to read LDIF file: {}", TEST_LDIF_PATH, e);
            throw e;
        }
    }

    // ========== Focused Component Tests ==========

    @Test
    @DisplayName("LDIF 파싱 성능: CRL 추출 시간 측정")
    void measure_LdifParsing_Performance() throws Exception {
        Path ldifPath = Paths.get(TEST_LDIF_PATH);
        if (!Files.exists(ldifPath)) {
            log.warn("Test LDIF file not found, skipping performance test");
            return;
        }

        log.info("=== LDIF Parsing Performance Test ===");

        byte[] ldifContent = Files.readAllBytes(ldifPath);
        log.info("File size: {} MB", ldifContent.length / 1024 / 1024);

        // Warm-up
        ParsedFile warmupParsedFile = ParsedFile.create(ParsedFileId.newId(), null, TEST_FILE_FORMAT);
        ldifParserAdapter.parse(ldifContent, TEST_FILE_FORMAT, warmupParsedFile);

        // Actual measurement (3 runs, average)
        long[] parseTimes = new long[3];
        for (int i = 0; i < 3; i++) {
            long startTime = System.currentTimeMillis();

            ParsedFile parsedFile = ParsedFile.create(ParsedFileId.newId(), null, TEST_FILE_FORMAT);
            ldifParserAdapter.parse(ldifContent, TEST_FILE_FORMAT, parsedFile);

            parseTimes[i] = System.currentTimeMillis() - startTime;
            log.info("Run {}: {} ms, CRLs: {}", i + 1, parseTimes[i], parsedFile.getCrls().size());
        }

        long averageTime = (parseTimes[0] + parseTimes[1] + parseTimes[2]) / 3;
        log.info("Average parsing time: {} ms", averageTime);

        assertThat(averageTime)
            .as("LDIF 파싱 평균 성능")
            .isLessThan(10000);
    }

    @Test
    @DisplayName("배치 저장 vs 단일 저장 성능 비교")
    void compare_BatchSave_vs_SingleSave_Performance() throws Exception {
        Path ldifPath = Paths.get(TEST_LDIF_PATH);
        if (!Files.exists(ldifPath)) {
            log.warn("Test LDIF file not found, skipping comparison test");
            return;
        }

        log.info("=== Batch vs Single Save Performance Comparison ===");

        byte[] ldifContent = Files.readAllBytes(ldifPath);

        // 1️⃣ LDIF 파싱
        ParsedFile parsedFile = ParsedFile.create(ParsedFileId.newId(), null, TEST_FILE_FORMAT);
        ldifParserAdapter.parse(ldifContent, TEST_FILE_FORMAT, parsedFile);

        List<CertificateRevocationList> crls = new java.util.ArrayList<>();
        for (var crlData : parsedFile.getCrls()) {
            try {
                CertificateRevocationList crl = CertificateRevocationList.create(
                    parsedFile.getId().getId(),  // uploadId (Phase 17)
                    CrlId.newId(),
                    IssuerName.of(extractIssuerName(crlData.getIssuerDN())),
                    CountryCode.of(crlData.getCountryCode()),
                    ValidityPeriod.of(crlData.getThisUpdate(), crlData.getNextUpdate()),
                    X509CrlData.of(crlData.getCrlBinary(), crlData.getRevokedCertificatesCount()),
                    RevokedCertificates.empty()
                );
                crls.add(crl);
            } catch (Exception e) {
                log.warn("Failed to create CRL: issuer={}", crlData.getIssuerDN(), e);
            }
        }

        if (crls.isEmpty()) {
            log.warn("No valid CRLs to compare");
            return;
        }

        // 2️⃣ 배치 저장
        log.info("Testing batch save with {} CRLs...", crls.size());
        long batchStartTime = System.currentTimeMillis();
        crlRepository.saveAll(crls);
        entityManager.flush();
        long batchTime = System.currentTimeMillis() - batchStartTime;

        log.info("Batch save time: {} ms", batchTime);

        // 3️⃣ 성능 분석
        double avePerItem = (double) batchTime / crls.size();
        log.info("Average time per CRL: {:.2f} ms", avePerItem);

        assertThat(batchTime)
            .as("배치 저장 성능")
            .isLessThan(10000);  // 10초 이내
    }

    @Test
    @DisplayName("CRL 이슈어 분포 분석")
    void analyze_CRL_IssuerDistribution() throws Exception {
        Path ldifPath = Paths.get(TEST_LDIF_PATH);
        if (!Files.exists(ldifPath)) {
            log.warn("Test LDIF file not found, skipping analysis test");
            return;
        }

        log.info("=== CRL Issuer Distribution Analysis ===");

        byte[] ldifContent = Files.readAllBytes(ldifPath);

        ParsedFile parsedFile = ParsedFile.create(ParsedFileId.newId(), null, TEST_FILE_FORMAT);
        ldifParserAdapter.parse(ldifContent, TEST_FILE_FORMAT, parsedFile);

        // 이슈어별 CRL 개수
        java.util.Set<String> issuerSet = new java.util.HashSet<>();
        for (var crlData : parsedFile.getCrls()) {
            issuerSet.add(extractIssuerName(crlData.getIssuerDN()));
        }

        log.info("Unique CRL issuers: {}", issuerSet.size());
        issuerSet.forEach(issuer -> log.info("  - {}", issuer));

        // 국가별 CRL 개수
        java.util.Set<String> countrySet = new java.util.HashSet<>();
        for (var crlData : parsedFile.getCrls()) {
            countrySet.add(crlData.getCountryCode());
        }

        log.info("Unique countries: {}", countrySet.size());
        countrySet.forEach(country -> log.info("  - {}", country));

        // 폐기 인증서 분포
        long totalRevoked = 0;
        for (var crlData : parsedFile.getCrls()) {
            totalRevoked += crlData.getRevokedCertificatesCount();
        }

        log.info("Total revoked certificates: {}", totalRevoked);
        log.info("Average revoked per CRL: {:.2f}",
            totalRevoked / (double) Math.max(1, parsedFile.getCrls().size()));
    }
}
