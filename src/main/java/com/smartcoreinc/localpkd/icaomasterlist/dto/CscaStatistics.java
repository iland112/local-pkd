package com.smartcoreinc.localpkd.icaomasterlist.dto;

import java.util.List;
import java.util.Map;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class CscaStatistics {

    // 기본 통계
    private int totalCertificates;          // 총 인증서 수
    private int validCertificates;          // 유효한 인증서 수
    private int invalidCertificates;        // 무효한 인증서 수
    private int totalCountries;             // 총 참여 국가 수
    private double validityRate;            // 유효성 비율 (%)

    // 국가별 통계
    private Map<String, Integer> countByCountry;            // 국가별 인증서 수
    private List<Map.Entry<String, Integer>> topCountries;  // 상위 국가 목록

    // 추가 메타데이터
    private String analysisTime;    // 분석 완료 시간
    private String fileName;        // 분석된 파일 명
    private long fileSize;          // 파일 크기

    /**
     * 유효성 비율을 백분율 문자열로 변환
     * @return String
     */
    public String getValidityRateAsString() {
        return String.format("%.1f%%", validityRate);
    }

    /**
     * 무효 인증서 비율 계산
     */
    public double getInvalidityRate() {
        return 100.0 - validityRate;
    }
    
    /**
     * 평균 국가당 인증서 수 계산
     */
    public double getAverageCertificatesPerCountry() {
        if (totalCountries == 0) return 0.0;
        return (double) totalCertificates / totalCountries;
    }
    
    /**
     * 최다 인증서 보유 국가 반환
     */
    public String getTopCountryCode() {
        if (topCountries == null || topCountries.isEmpty()) {
            return "N/A";
        }
        return topCountries.get(0).getKey();
    }
    
    /**
     * 최다 인증서 보유 국가의 인증서 수 반환
     */
    public int getTopCountryCount() {
        if (topCountries == null || topCountries.isEmpty()) {
            return 0;
        }
        return topCountries.get(0).getValue();
    }

    /**
     * 통계 요약 문자열 생성
     */
    public String getSummary() {
        return String.format(
            "총 %d개국에서 %d개의 CSCA 인증서 분석 완료 (유효: %d개, 무효: %d개, 유효율: %.1f%%)",
            totalCountries, totalCertificates, validCertificates, invalidCertificates, validityRate
        );
    }

    /**
     * JSON 직렬화를 위한 단순화된 맵 반환
     */
    public Map<String, Object> toSimpleMap() {
        return Map.of(
            "totalCertificates", totalCertificates,
            "validCertificates", validCertificates,
            "invalidCertificates", invalidCertificates,
            "totalCountries", totalCountries,
            "validityRate", validityRate,
            "topCountry", getTopCountryCode(),
            "topCountryCount", getTopCountryCount(),
            "summary", getSummary()
        );
    }
}
