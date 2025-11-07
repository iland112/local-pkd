package com.smartcoreinc.localpkd.controller;

import com.smartcoreinc.localpkd.certificatevalidation.domain.model.CertificateStatus;
import com.smartcoreinc.localpkd.certificatevalidation.domain.model.CertificateType;
import com.smartcoreinc.localpkd.certificatevalidation.infrastructure.repository.SpringDataCertificateRepository;
import com.smartcoreinc.localpkd.certificatevalidation.infrastructure.repository.SpringDataCertificateRevocationListRepository;
import com.smartcoreinc.localpkd.certificatevalidation.application.command.ValidateCertificatesCommand;
import com.smartcoreinc.localpkd.certificatevalidation.application.usecase.ValidateCertificatesUseCase;
import com.smartcoreinc.localpkd.fileparsing.domain.model.ParsedFile;
import com.smartcoreinc.localpkd.fileparsing.infrastructure.repository.SpringDataParsedFileRepository;
import com.smartcoreinc.localpkd.controller.response.CertificateStatisticsResponse;
import com.smartcoreinc.localpkd.controller.response.CertificateDetailedStatisticsResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import java.util.HashMap;
import java.util.Map;
import java.util.LinkedHashMap;

/**
 * DashboardApiController - Dashboard API Controller
 *
 * <p><b>목적</b>: Dashboard 페이지에 표시할 인증서 및 CRL 통계 정보를 제공합니다.</p>
 *
 * <p><b>API Endpoints</b>:</p>
 * <ul>
 *   <li>GET /api/dashboard/certificate-statistics - 인증서 및 CRL 통계 조회</li>
 * </ul>
 *
 * <p><b>통계 항목</b>:</p>
 * <ul>
 *   <li>totalCertificates: 전체 인증서 수 (CSCA + DSC + DSC_NC)</li>
 *   <li>cscaCount: CSCA (Country Signing CA) 인증서 수</li>
 *   <li>dscCount: DSC (Document Signer Certificate) 인증서 수 (DSC + DSC_NC)</li>
 *   <li>totalCrls: 전체 CRL (Certificate Revocation List) 수</li>
 *   <li>validatedCertificates: 검증 완료된 인증서 수 (status = VALIDATED)</li>
 * </ul>
 *
 * <p><b>사용 위치</b>:</p>
 * <ul>
 *   <li>Dashboard 페이지 (index.html) - Alpine.js에서 AJAX 호출하여 통계 표시</li>
 * </ul>
 *
 * <p><b>응답 예시</b>:</p>
 * <pre>{@code
 * {
 *   "totalCertificates": 29587,
 *   "cscaCount": 200,
 *   "dscCount": 29387,
 *   "totalCrls": 69,
 *   "validatedCertificates": 29587
 * }
 * }</pre>
 *
 * @author SmartCore Inc.
 * @version 1.0
 * @since 2025-11-07
 */
@Slf4j
@RestController
@RequestMapping("/api/dashboard")
@RequiredArgsConstructor
public class DashboardApiController {

    private final SpringDataCertificateRepository certificateRepository;
    private final SpringDataCertificateRevocationListRepository crlRepository;
    private final SpringDataParsedFileRepository parsedFileRepository;
    private final ValidateCertificatesUseCase validateCertificatesUseCase;

    /**
     * 인증서 및 CRL 통계 조회
     *
     * <p>Dashboard 페이지에 표시할 인증서 및 CRL 통계 정보를 반환합니다.</p>
     *
     * <p><b>쿼리 수행</b>:</p>
     * <ul>
     *   <li>certificateRepository.count() - 전체 인증서 수</li>
     *   <li>certificateRepository.countByCertificateType(CSCA) - CSCA 인증서 수</li>
     *   <li>certificateRepository.countByCertificateType(DSC) + countByCertificateType(DSC_NC) - DSC 인증서 수</li>
     *   <li>crlRepository.count() - 전체 CRL 수</li>
     *   <li>certificateRepository.countByStatus(VALIDATED) - 검증 완료 인증서 수</li>
     * </ul>
     *
     * @return ResponseEntity<CertificateStatisticsResponse> 인증서 통계 응답
     */
    @GetMapping("/certificate-statistics")
    public ResponseEntity<CertificateStatisticsResponse> getCertificateStatistics() {
        log.debug("=== Certificate statistics requested ===");

        try {
            // 1. 전체 인증서 수 조회
            long totalCertificates = certificateRepository.count();
            log.debug("Total certificates: {}", totalCertificates);

            // 2. CSCA 인증서 수 조회
            long cscaCount = certificateRepository.countByCertificateType(CertificateType.CSCA);
            log.debug("CSCA count: {}", cscaCount);

            // 3. DSC 인증서 수 조회 (DSC + DSC_NC)
            long dscCount = certificateRepository.countByCertificateType(CertificateType.DSC)
                    + certificateRepository.countByCertificateType(CertificateType.DSC_NC);
            log.debug("DSC count: {}", dscCount);

            // 4. 전체 CRL 수 조회
            long totalCrls = crlRepository.count();
            log.debug("Total CRLs: {}", totalCrls);

            // 5. 검증 완료 인증서 수 조회 (유효한 인증서)
            long validatedCertificates = certificateRepository.countByStatus(CertificateStatus.VALID);
            log.debug("Validated certificates: {}", validatedCertificates);

            // 6. 응답 생성
            CertificateStatisticsResponse response = new CertificateStatisticsResponse(
                    totalCertificates,
                    cscaCount,
                    dscCount,
                    totalCrls,
                    validatedCertificates
            );

            log.info("Certificate statistics response: totalCertificates={}, cscaCount={}, dscCount={}, totalCrls={}, validatedCertificates={}",
                    totalCertificates, cscaCount, dscCount, totalCrls, validatedCertificates);

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("Failed to retrieve certificate statistics", e);
            // 오류 발생 시 빈 통계 반환
            return ResponseEntity.ok(CertificateStatisticsResponse.empty());
        }
    }

    /**
     * 파싱된 인증서 검증 트리거 (수동)
     *
     * <p>파싱된 인증서를 검증하여 Certificate 테이블로 이전합니다.
     * Dashboard 통계 검증용 수동 트리거 엔드포인트입니다.</p>
     *
     * @return 검증 트리거 결과
     */
    @PostMapping("/trigger-validation")
    public ResponseEntity<Map<String, Object>> triggerValidation() {
        log.info("=== Manual Certificate Validation Trigger ===");

        Map<String, Object> result = new HashMap<>();

        try {
            // 1. 파싱된 파일 조회 (가장 최근 파일)
            var parsedFiles = parsedFileRepository.findAll();
            if (parsedFiles.isEmpty()) {
                result.put("success", false);
                result.put("error", "파싱된 파일이 없습니다");
                result.put("timestamp", System.currentTimeMillis());
                return ResponseEntity.ok(result);
            }

            // 2. 가장 최신 파일 선택
            ParsedFile latestFile = parsedFiles.stream()
                .max((a, b) -> a.getParsingCompletedAt().compareTo(b.getParsingCompletedAt()))
                .orElse(null);

            if (latestFile == null) {
                result.put("success", false);
                result.put("error", "파싱된 파일을 찾을 수 없습니다");
                result.put("timestamp", System.currentTimeMillis());
                return ResponseEntity.ok(result);
            }

            int certCount = latestFile.getStatistics().getCertificateCount();
            int crlCount = latestFile.getStatistics().getCrlCount();

            log.info("Found latest parsed file: id={}, certificates={}, crls={}",
                latestFile.getId(), certCount, crlCount);

            // 3. ValidateCertificatesCommand 생성
            ValidateCertificatesCommand command = ValidateCertificatesCommand.builder()
                .uploadId(latestFile.getUploadId().getId())
                .parsedFileId(latestFile.getId().getId())
                .certificateCount(certCount)
                .crlCount(crlCount)
                .build();

            // 4. Validation 실행
            var validationResponse = validateCertificatesUseCase.execute(command);

            result.put("success", validationResponse.success());
            result.put("uploadId", latestFile.getUploadId().getId());
            result.put("parsedFileId", latestFile.getId().getId());
            result.put("certificateCount", certCount);
            result.put("crlCount", crlCount);
            result.put("totalValidated", validationResponse.getTotalValidated());
            result.put("totalValid", validationResponse.getTotalValid());
            result.put("totalInvalid", validationResponse.getTotalValidated() - validationResponse.getTotalValid());
            result.put("message", validationResponse.success()
                ? "검증 완료"
                : validationResponse.errorMessage());
            result.put("timestamp", System.currentTimeMillis());

            log.info("Validation completed: success={}, validated={}, valid={}, invalid={}",
                validationResponse.success(),
                validationResponse.getTotalValidated(),
                validationResponse.getTotalValid(),
                validationResponse.getTotalValidated() - validationResponse.getTotalValid());

            return ResponseEntity.ok(result);

        } catch (Exception e) {
            log.error("Failed to trigger certificate validation", e);
            result.put("success", false);
            result.put("error", "검증 트리거 실패: " + e.getMessage());
            result.put("timestamp", System.currentTimeMillis());
            return ResponseEntity.ok(result);
        }
    }

    /**
     * 상세 통계 조회 (Chart.js용)
     *
     * <p>Dashboard 차트 렌더링을 위한 상세한 통계 데이터를 반환합니다.</p>
     *
     * <p><b>응답 데이터</b>:</p>
     * <ul>
     *   <li>certificateTypeDistribution: 인증서 타입별 분포</li>
     *   <li>certificateStatusDistribution: 인증서 상태별 분포</li>
     *   <li>crlStatusDistribution: CRL 상태별 분포</li>
     * </ul>
     *
     * @return ResponseEntity<CertificateDetailedStatisticsResponse>
     */
    @GetMapping("/detailed-statistics")
    public ResponseEntity<CertificateDetailedStatisticsResponse> getDetailedStatistics() {
        log.debug("=== Detailed statistics requested ===");

        try {
            // 1. 인증서 타입별 분포
            Map<String, Long> typeDistribution = new LinkedHashMap<>();
            typeDistribution.put("CSCA", certificateRepository.countByCertificateType(CertificateType.CSCA));
            typeDistribution.put("DSC", certificateRepository.countByCertificateType(CertificateType.DSC));
            typeDistribution.put("DSC_NC", certificateRepository.countByCertificateType(CertificateType.DSC_NC));

            // 2. 인증서 상태별 분포
            Map<String, Long> statusDistribution = new LinkedHashMap<>();
            statusDistribution.put("VALID", certificateRepository.countByStatus(CertificateStatus.VALID));
            statusDistribution.put("INVALID", certificateRepository.count() - certificateRepository.countByStatus(CertificateStatus.VALID));

            // 3. CRL 상태별 분포 (현재는 VALID만 있음)
            Map<String, Long> crlDistribution = new LinkedHashMap<>();
            crlDistribution.put("VALID", crlRepository.count());
            crlDistribution.put("EXPIRED", 0L);

            // 4. 전체 통계
            long totalCertificates = certificateRepository.count();
            long totalCrls = crlRepository.count();

            CertificateDetailedStatisticsResponse response = new CertificateDetailedStatisticsResponse(
                typeDistribution,
                statusDistribution,
                crlDistribution,
                totalCertificates,
                totalCrls
            );

            log.info("Detailed statistics: certificates={}, crls={}", totalCertificates, totalCrls);

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("Failed to retrieve detailed statistics", e);
            return ResponseEntity.ok(CertificateDetailedStatisticsResponse.empty());
        }
    }
}
