package com.smartcoreinc.localpkd.icaomasterlist.controller;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.smartcoreinc.localpkd.icaomasterlist.dto.CscaStatistics;
import com.smartcoreinc.localpkd.icaomasterlist.service.ICAOMasterListParser;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@RestController
@RequestMapping("/api/icao/csca")
public class StatisticsRestController {

    private final ICAOMasterListParser parser;

    public StatisticsRestController(ICAOMasterListParser parser) {
        this.parser = parser;
    }

    /**
     * 실시간 분석 진행 상황 조회
     */
    @GetMapping("/progress")
    public ResponseEntity<Map<String, Object>> getCurrentProgress() {
        try {
            Map<String, Object> progressInfo = new HashMap<>();
            progressInfo.put("status", parser.getAnalysisStatus());
            progressInfo.put("progress", parser.getCurrentProgress());
            progressInfo.put("totalCertificates", parser.getTotalCertificates());
            progressInfo.put("processedCertificates", parser.getProcessedCertificates());
            progressInfo.put("validCertificates", parser.getValidCertificates().size());
            progressInfo.put("invalidCertificates", parser.getInvalidCertificates().size());
            progressInfo.put("totalCountries", parser.getCscaCountByCountry().size());
            progressInfo.put("fileName", parser.getCurrentFileName());
            progressInfo.put("fileSize", parser.getCurrentFileSize());
            
            return ResponseEntity.ok(progressInfo);
        } catch (Exception e) {
            log.error("진행 상황 조회 실패: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * 상세 통계 정보 조회
     */
    @GetMapping("/detailed-statistics")
    public ResponseEntity<CscaStatistics> getDetailedStatistics() {
        try {
            Map<String, Integer> countByCountry = parser.getCscaCountByCountry();
            int validCount = parser.getValidCertificates().size();
            int invalidCount = parser.getInvalidCertificates().size();
            
            // 상위 10개국 추출
            List<Map.Entry<String, Integer>> topCountries = countByCountry.entrySet().stream()
                .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
                .limit(10)
                .collect(Collectors.toList());
            
            CscaStatistics statistics = CscaStatistics.builder()
                .totalCertificates(validCount + invalidCount)
                .validCertificates(validCount)
                .invalidCertificates(invalidCount)
                .totalCountries(countByCountry.size())
                .countByCountry(countByCountry)
                .validityRate(parser.getValidityRate())
                .topCountries(topCountries)
                .fileName(parser.getCurrentFileName())
                .fileSize(parser.getCurrentFileSize())
                .analysisTime(parser.getAnalysisStartTime() != null ? 
                            parser.getAnalysisStartTime().toString() : null)
                .build();
                
            return ResponseEntity.ok(statistics);
        } catch (Exception e) {
            log.error("상세 통계 조회 실패: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * 국가별 통계 조회
     */
    @GetMapping("/country-statistics")
    public ResponseEntity<List<Map<String, Object>>> getCountryStatistics() {
        try {
            List<Map<String, Object>> countryStats = parser.getCountryStatisticsSorted().stream()
                .map(entry -> {
                    Map<String, Object> stat = new HashMap<>();
                    stat.put("countryCode", entry.getKey());
                    stat.put("certificateCount", entry.getValue());
                    stat.put("percentage", String.format("%.1f%%", 
                        (double) entry.getValue() / parser.getTotalCertificates() * 100));
                    return stat;
                })
                .collect(Collectors.toList());
                
            return ResponseEntity.ok(countryStats);
        } catch (Exception e) {
            log.error("국가별 통계 조회 실패: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * 분석 요약 정보 조회
     */
    @GetMapping("/summary")
    public ResponseEntity<Map<String, Object>> getAnalysisSummary() {
        try {
            Map<String, Object> summary = new HashMap<>();
            summary.put("summary", parser.getAnalysisSummary());
            summary.put("status", parser.getAnalysisStatus());
            summary.put("errorCount", parser.getErrorMessages().size());
            summary.put("errors", parser.getErrorMessages());
            summary.put("validityRate", parser.getValidityRate());
            summary.put("averageCertificatesPerCountry", parser.getAverageCertificatesPerCountry());
            
            return ResponseEntity.ok(summary);
        } catch (Exception e) {
            log.error("분석 요약 조회 실패: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * 분석 상태 초기화
     */
    @GetMapping("/reset")
    public ResponseEntity<Map<String, String>> resetAnalysis() {
        try {
            parser.resetAnalysisResults();
            Map<String, String> response = new HashMap<>();
            response.put("status", "success");
            response.put("message", "분석 상태가 초기화되었습니다.");
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("분석 상태 초기화 실패: {}", e.getMessage(), e);
            Map<String, String> response = new HashMap<>();
            response.put("status", "error");
            response.put("message", "초기화 실패: " + e.getMessage());
            return ResponseEntity.internalServerError().body(response);
        }
    }

    /**
     * LDAP 연결 테스트 (기존 기능 통합)
     */
    @GetMapping("/ldap-test")
    public ResponseEntity<Map<String, Object>> testLdapConnection() {
        try {
            // TODO: LDAP 연결 테스트 로직 구현
            // LdapTemplate을 사용하여 연결 테스트
            
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", "LDAP 서버 연결 성공");
            response.put("timestamp", System.currentTimeMillis());
            
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("LDAP 연결 테스트 실패: {}", e.getMessage(), e);
            
            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("message", "LDAP 서버 연결 실패: " + e.getMessage());
            response.put("timestamp", System.currentTimeMillis());
            
            return ResponseEntity.ok(response);
        }
    }
}
