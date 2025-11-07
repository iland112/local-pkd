package com.smartcoreinc.localpkd.controller.response;

/**
 * CertificateStatisticsResponse - Dashboard 인증서 통계 응답 DTO
 *
 * <p><b>목적</b>: Dashboard에 표시할 인증서 및 CRL 통계 정보를 반환합니다.</p>
 *
 * <p><b>사용 위치</b>:</p>
 * <ul>
 *   <li>Dashboard API Controller (/api/dashboard/certificate-statistics)</li>
 *   <li>Dashboard 페이지 (index.html) - Alpine.js에서 소비</li>
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
 * <p><b>사용 예시</b>:</p>
 * <pre>{@code
 * // Controller에서 생성
 * CertificateStatisticsResponse response = new CertificateStatisticsResponse(
 *     1000,  // totalCertificates
 *     200,   // cscaCount
 *     800,   // dscCount
 *     50,    // totalCrls
 *     950    // validatedCertificates
 * );
 *
 * // 빈 통계 (데이터 없을 때)
 * CertificateStatisticsResponse empty = CertificateStatisticsResponse.empty();
 * }</pre>
 *
 * @param totalCertificates 전체 인증서 수
 * @param cscaCount CSCA 인증서 수
 * @param dscCount DSC 인증서 수 (DSC + DSC_NC)
 * @param totalCrls 전체 CRL 수
 * @param validatedCertificates 검증 완료 인증서 수
 *
 * @author SmartCore Inc.
 * @version 1.0
 * @since 2025-11-07
 */
public record CertificateStatisticsResponse(
    long totalCertificates,
    long cscaCount,
    long dscCount,
    long totalCrls,
    long validatedCertificates
) {
    /**
     * 빈 통계 응답 생성
     *
     * <p>데이터베이스에 인증서가 없거나, 에러 발생 시 사용됩니다.</p>
     *
     * @return 모든 값이 0인 CertificateStatisticsResponse
     */
    public static CertificateStatisticsResponse empty() {
        return new CertificateStatisticsResponse(0, 0, 0, 0, 0);
    }

    /**
     * 총 인증서 대비 검증 완료율 계산
     *
     * @return 검증 완료율 (0.0 ~ 100.0)
     */
    public double getValidationRate() {
        if (totalCertificates == 0) {
            return 0.0;
        }
        return (validatedCertificates * 100.0) / totalCertificates;
    }

    /**
     * CSCA 비율 계산
     *
     * @return CSCA 비율 (0.0 ~ 100.0)
     */
    public double getCscaRate() {
        if (totalCertificates == 0) {
            return 0.0;
        }
        return (cscaCount * 100.0) / totalCertificates;
    }

    /**
     * DSC 비율 계산
     *
     * @return DSC 비율 (0.0 ~ 100.0)
     */
    public double getDscRate() {
        if (totalCertificates == 0) {
            return 0.0;
        }
        return (dscCount * 100.0) / totalCertificates;
    }
}
