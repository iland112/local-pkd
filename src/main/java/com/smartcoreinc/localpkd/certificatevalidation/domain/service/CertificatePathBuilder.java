package com.smartcoreinc.localpkd.certificatevalidation.domain.service;

import com.smartcoreinc.localpkd.certificatevalidation.domain.model.Certificate;
import com.smartcoreinc.localpkd.certificatevalidation.domain.model.CertificateId;
import com.smartcoreinc.localpkd.certificatevalidation.domain.model.TrustPath;

import java.util.Optional;

/**
 * CertificatePathBuilder - 인증서 신뢰 경로 자동 구축 Domain Service
 *
 * <p>주어진 인증서로부터 CSCA (Root of Trust)까지의 경로를 자동으로 구축합니다.
 * Issuer DN을 따라 재귀적으로 부모 인증서를 검색하여 Trust Path를 생성합니다.</p>
 *
 * <h3>알고리즘</h3>
 * <pre>
 * 1. 시작 인증서 (Leaf)
 * 2. Issuer DN 추출
 * 3. Repository에서 Issuer DN으로 부모 인증서 검색
 * 4. 부모 인증서 발견 → 경로에 추가 → 2단계 반복
 * 5. Self-Signed 인증서 도달 (CSCA) → 경로 구축 완료
 * 6. 최대 깊이 도달 또는 Issuer 없음 → 실패
 * </pre>
 *
 * <h3>사용 예시</h3>
 * <pre>{@code
 * CertificatePathBuilder builder = new CertificatePathBuilderImpl(certificateRepository);
 * Optional<TrustPath> pathOpt = builder.buildPath(dscCertificateId);
 *
 * if (pathOpt.isPresent()) {
 *     TrustPath path = pathOpt.get();
 *     log.info("Trust path built: depth={}, path={}", path.getDepth(), path.toShortString());
 * } else {
 *     log.error("Failed to build trust path: CSCA not found");
 * }
 * }</pre>
 *
 * @author SmartCore Inc.
 * @version 1.0
 * @since 2025-10-24
 */
public interface CertificatePathBuilder {

    /**
     * 인증서 ID로부터 Trust Path 구축
     *
     * <p>주어진 인증서 ID로부터 CSCA까지의 경로를 자동으로 구축합니다.
     * Issuer DN을 따라 재귀적으로 검색합니다.</p>
     *
     * @param certificateId 시작 인증서 ID (DSC or DS)
     * @return Optional<TrustPath> (경로 구축 성공 시 TrustPath, 실패 시 empty)
     */
    Optional<TrustPath> buildPath(CertificateId certificateId);

    /**
     * 인증서 객체로부터 Trust Path 구축
     *
     * <p>주어진 인증서 객체로부터 CSCA까지의 경로를 자동으로 구축합니다.</p>
     *
     * @param certificate 시작 인증서 (DSC or DS)
     * @return Optional<TrustPath> (경로 구축 성공 시 TrustPath, 실패 시 empty)
     */
    Optional<TrustPath> buildPath(Certificate certificate);

    /**
     * 인증서가 Self-Signed (CSCA)인지 확인
     *
     * <p>인증서의 Subject DN과 Issuer DN이 동일한지 확인합니다.</p>
     *
     * @param certificate 확인 대상 인증서
     * @return true: Self-Signed (CSCA), false: Issued by another CA
     */
    boolean isSelfSigned(Certificate certificate);

    /**
     * 특정 Issuer DN을 가진 부모 인증서 검색
     *
     * <p>Repository에서 주어진 Subject DN과 일치하는 인증서를 검색합니다.
     * 여러 개 발견 시 유효기간이 가장 긴 인증서를 반환합니다.</p>
     *
     * @param issuerDn Issuer Distinguished Name
     * @return Optional<Certificate> (발견 시 Certificate, 없으면 empty)
     */
    Optional<Certificate> findIssuerCertificate(String issuerDn);
}
