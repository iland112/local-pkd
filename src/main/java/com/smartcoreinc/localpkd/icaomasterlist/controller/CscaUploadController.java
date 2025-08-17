package com.smartcoreinc.localpkd.icaomasterlist.controller;

import java.security.cert.X509Certificate;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.multipart.MultipartFile;

import com.smartcoreinc.localpkd.icaomasterlist.dto.CscaStatistics;
import com.smartcoreinc.localpkd.icaomasterlist.service.CscaMasterListParser;
import io.github.wimdeblauwe.htmx.spring.boot.mvc.HxRequest;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Controller
@RequestMapping("/icao/csca")
public class CscaUploadController {
    private final CscaMasterListParser parser;
    
    private int count = 0;

    public CscaUploadController(CscaMasterListParser parser) {
        this.parser = parser;
    }

    @GetMapping
    public String uploadForm() {
        return "masterlist/upload-ml";
    }

    /**
     * ICAO Master list 파일(.ml) 업로드 및 분석
     * @param file 업로드된 파일
     * @param model 모델
     * @return 통계 정보를 포함한 응답
     * @throws Exception
     */
    @HxRequest
    @PostMapping("/upload")
    @ResponseBody
    public ResponseEntity<String> handleUpload(@RequestParam("file") MultipartFile file,
                             Model model) throws Exception {
        log.info("=== File upload started: {} ({} bytes) ===", 
                file.getOriginalFilename(), file.getSize());
        
        try {
            // Master List 파싱 실행 (LDAP 저장 포함)
            // TODO: 개발 완료 후 isAddLdap 파라미터 제거할 것
            List<X509Certificate> validCerts = parser.parseMasterList(file.getBytes(), true);
            
            // 통계 정보 생성
            CscaStatistics statistics = generateStatistics();
            log.info("파싱 완료 - 총 {}개 인증서 중 유효: {}개, 무효: {}개", 
                    statistics.getTotalCertificates(), 
                    statistics.getValidCertificates(), 
                    statistics.getInvalidCertificates());
            
            // 성공 응답과 함께 통계 정보를 HTML로 반환
            String responseHtml = createStatisticsHtml(statistics);
            return ResponseEntity.ok(responseHtml);
        } catch (Exception e) {
            log.error("파일 업로드 및 분석 실패: {}", e.getMessage(), e);
            String errorHtml = createErrorHtml("파일 분석 중 오류가 발생했습니다: " + e.getMessage());
            return ResponseEntity.ok(errorHtml);
        }
    }

    /**
     * 현재 파싱 결과 통계 조회 (AJAX 요청용)
     */
    @GetMapping("/statistics")
    @ResponseBody
    public ResponseEntity<CscaStatistics> getStatistics() {
        try {
            CscaStatistics statistics = generateStatistics();
            return ResponseEntity.ok(statistics);
        } catch (Exception e) {
            log.error("통계 정보 조회 실패: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError().build();
        }
    }
    
    /**
     * 통계 정보 생성
     */
    private CscaStatistics generateStatistics() {
        Map<String, Integer> countByCountry = parser.getCscaCountByCountry();
        List<X509Certificate> validCerts = parser.getValidCertificates();
        List<X509Certificate> invalidCerts = parser.getInvalidCertificates();
        
        return CscaStatistics.builder()
            .totalCertificates(validCerts.size() + invalidCerts.size())
            .validCertificates(validCerts.size())
            .invalidCertificates(invalidCerts.size())
            .totalCountries(countByCountry.size())
            .countByCountry(countByCountry)
            .validityRate(calculateValidityRate(validCerts.size(), invalidCerts.size()))
            .topCountries(getTopCountries(countByCountry, 5))
            .build();
    }
    
    /**
     * 유효성 비율 계산
     */
    private double calculateValidityRate(int validCount, int invalidCount) {
        int total = validCount + invalidCount;
        if (total == 0) return 0.0;
        return (double) validCount / total * 100;
    }
    
    /**
     * 상위 국가 목록 반환
     */
    private List<Map.Entry<String, Integer>> getTopCountries(Map<String, Integer> countByCountry, int limit) {
        return countByCountry.entrySet().stream()
            .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
            .limit(limit)
            .collect(Collectors.toList());
    }
    
    /**
     * 통계 정보를 HTML로 변환
     */
    private String createStatisticsHtml(CscaStatistics stats) {
        StringBuilder html = new StringBuilder();
        html.append("<div id=\"statistics-result\" class=\"mt-6 space-y-6\">");
        
        // 메인 통계 카드
        html.append("""
            <div class="bg-white rounded-lg shadow-lg p-6">
                <h3 class="text-xl font-bold text-gray-800 mb-4">
                    <i class="fas fa-chart-bar text-blue-600 mr-2"></i>
                    분석 결과 요약
                </h3>
                <div class="grid grid-cols-2 md:grid-cols-4 gap-4">
                    <div class="bg-blue-50 rounded-lg p-4 text-center">
                        <div class="text-2xl font-bold text-blue-600">%d</div>
                        <div class="text-sm text-gray-600">총 인증서</div>
                    </div>
                    <div class="bg-green-50 rounded-lg p-4 text-center">
                        <div class="text-2xl font-bold text-green-600">%d</div>
                        <div class="text-sm text-gray-600">유효 인증서</div>
                    </div>
                    <div class="bg-red-50 rounded-lg p-4 text-center">
                        <div class="text-2xl font-bold text-red-600">%d</div>
                        <div class="text-sm text-gray-600">무효 인증서</div>
                    </div>
                    <div class="bg-purple-50 rounded-lg p-4 text-center">
                        <div class="text-2xl font-bold text-purple-600">%d</div>
                        <div class="text-sm text-gray-600">참여 국가</div>
                    </div>
                </div>
                <div class="mt-4 bg-gray-50 rounded-lg p-4">
                    <div class="flex justify-between items-center mb-2">
                        <span class="text-sm font-medium text-gray-700">전체 유효성 비율</span>
                        <span class="text-sm font-bold text-gray-900">%.1f%%</span>
                    </div>
                    <div class="w-full bg-gray-200 rounded-full h-2">
                        <div class="bg-green-600 h-2 rounded-full" style="width: %.1f%%"></div>
                    </div>
                </div>
            </div>
            """.formatted(
                stats.getTotalCertificates(),
                stats.getValidCertificates(), 
                stats.getInvalidCertificates(),
                stats.getTotalCountries(),
                stats.getValidityRate(),
                stats.getValidityRate()
            ));
        
        // 상위 국가 통계
        if (!stats.getTopCountries().isEmpty()) {
            html.append("""
                <div class="bg-white rounded-lg shadow-lg p-6">
                    <h4 class="text-lg font-bold text-gray-800 mb-4">
                        <i class="fas fa-globe text-green-600 mr-2"></i>
                        국가별 CSCA 인증서 현황 (상위 5개국)
                    </h4>
                    <div class="space-y-3">
                """);
            
            for (Map.Entry<String, Integer> entry : stats.getTopCountries()) {
                double percentage = (double) entry.getValue() / stats.getTotalCertificates() * 100;
                html.append("""
                    <div class="flex items-center justify-between p-3 bg-gray-50 rounded-lg">
                        <div class="flex items-center">
                            <span class="font-semibold text-gray-800 w-12">%s</span>
                            <div class="ml-4 flex-1">
                                <div class="w-full bg-gray-200 rounded-full h-2">
                                    <div class="bg-blue-600 h-2 rounded-full" style="width: %.1f%%"></div>
                                </div>
                            </div>
                        </div>
                        <div class="text-right ml-4">
                            <div class="font-bold text-gray-800">%d</div>
                            <div class="text-xs text-gray-500">%.1f%%</div>
                        </div>
                    </div>
                    """.formatted(entry.getKey(), percentage, entry.getValue(), percentage));
            }
            
            html.append("</div></div>");
        }
        
        // 액션 버튼들
        html.append("""
            <div class="bg-white rounded-lg shadow-lg p-6">
                <h4 class="text-lg font-bold text-gray-800 mb-4">
                    <i class="fas fa-tools text-orange-600 mr-2"></i>
                    추가 작업
                </h4>
                <div class="flex flex-wrap gap-3">
                    <button onclick="downloadStatistics()" 
                            class="btn btn-outline btn-primary">
                        <i class="fas fa-download mr-2"></i>통계 다운로드
                    </button>
                    <button onclick="viewDetailedReport()" 
                            class="btn btn-outline btn-info">
                        <i class="fas fa-file-alt mr-2"></i>상세 보고서
                    </button>
                    <button onclick="exportToLdif()" 
                            class="btn btn-outline btn-success">
                        <i class="fas fa-database mr-2"></i>LDIF 내보내기
                    </button>
                    <button onclick="location.reload()" 
                            class="btn btn-outline btn-secondary">
                        <i class="fas fa-sync mr-2"></i>새로 분석
                    </button>
                </div>
            </div>
            """);
        
        html.append("</div>");
        return html.toString();
    }
    
    /**
     * 에러 메시지를 HTML로 변환
     */
    private String createErrorHtml(String errorMessage) {
        return """
            <div id="error-result" class="mt-6">
                <div class="bg-red-50 border-l-4 border-red-500 rounded-md p-4">
                    <div class="flex items-center">
                        <i class="fas fa-exclamation-triangle text-red-500 mr-3"></i>
                        <div class="flex-1">
                            <h3 class="text-lg font-medium text-red-800">분석 실패</h3>
                            <p class="text-red-700 mt-1">%s</p>
                        </div>
                    </div>
                    <div class="mt-4">
                        <button onclick="location.reload()" 
                                class="bg-red-600 text-white px-4 py-2 rounded hover:bg-red-700 transition-colors">
                            <i class="fas fa-redo mr-2"></i>다시 시도
                        </button>
                    </div>
                </div>
            </div>
            """.formatted(errorMessage);
    }

}
