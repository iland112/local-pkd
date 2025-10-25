package com.smartcoreinc.localpkd.certificatevalidation.infrastructure.repository;

import com.smartcoreinc.localpkd.certificatevalidation.domain.model.Certificate;
import com.smartcoreinc.localpkd.certificatevalidation.domain.model.CertificateId;
import com.smartcoreinc.localpkd.certificatevalidation.domain.model.CertificateStatus;
import com.smartcoreinc.localpkd.certificatevalidation.domain.model.CertificateType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * SpringDataCertificateRepository - Spring Data JPA Repository
 *
 * <p><b>Infrastructure Layer</b>:</p>
 * <ul>
 *   <li>Spring Data JPA 기반 Repository 구현</li>
 *   <li>JPearl CertificateId 지원</li>
 *   <li>Query Methods 자동 생성</li>
 *   <li>@Query를 통한 복잡한 쿼리 정의</li>
 * </ul>
 *
 * <p><b>설계 패턴</b>:</p>
 * <ul>
 *   <li>Repository Pattern (JPA Implementation)</li>
 *   <li>Dependency Inversion Principle (Domain Layer는 이 인터페이스를 모름)</li>
 *   <li>Adapter Pattern (JpaCertificateRepository가 Domain Repository를 구현)</li>
 * </ul>
 *
 * <p><b>쿼리 메서드 네이밍</b>:</p>
 * <ul>
 *   <li>findBy + 필드명: 조회</li>
 *   <li>existsBy + 필드명: 존재 여부</li>
 *   <li>countBy + 필드명: 개수</li>
 * </ul>
 *
 * <p><b>Embedded Value Object 접근</b>:</p>
 * <pre>{@code
 * // x509Data.fingerprintSha256 → x509_fingerprint_sha256 컬럼
 * Optional<Certificate> findByX509Data_FingerprintSha256(String fingerprint);
 *
 * // subjectInfo.countryCode → subject_country_code 컬럼
 * List<Certificate> findBySubjectInfo_CountryCode(String countryCode);
 * }</pre>
 *
 * @see Certificate
 * @see CertificateId
 * @see JpaCertificateRepository
 * @author SmartCore Inc.
 * @version 1.0
 * @since 2025-10-24
 */
public interface SpringDataCertificateRepository extends JpaRepository<Certificate, CertificateId> {

    /**
     * SHA-256 지문으로 Certificate 조회
     *
     * <p>x509Data.fingerprintSha256 필드로 조회합니다.</p>
     *
     * @param fingerprintSha256 SHA-256 지문 (64자 16진수)
     * @return Optional<Certificate>
     */
    Optional<Certificate> findByX509Data_FingerprintSha256(String fingerprintSha256);

    /**
     * 일련 번호로 Certificate 조회
     *
     * <p>x509Data.serialNumber 필드로 조회합니다.</p>
     *
     * @param serialNumber 인증서 일련 번호 (16진수 문자열)
     * @return Optional<Certificate>
     */
    Optional<Certificate> findByX509Data_SerialNumber(String serialNumber);

    /**
     * SHA-256 지문으로 Certificate 존재 여부 확인
     *
     * @param fingerprintSha256 SHA-256 지문
     * @return 존재하면 true
     */
    boolean existsByX509Data_FingerprintSha256(String fingerprintSha256);

    /**
     * 상태별 Certificate 목록 조회
     *
     * @param status 인증서 상태
     * @return Certificate 목록
     */
    List<Certificate> findByCertificateType(CertificateType certificateType);

    /**
     * 타입별 Certificate 목록 조회
     *
     * @param status 인증서 상태
     * @return Certificate 목록
     */
    List<Certificate> findByStatus(CertificateStatus status);

    /**
     * 국가 코드별 Certificate 목록 조회
     *
     * <p>subjectInfo.countryCode 필드로 조회합니다.</p>
     *
     * @param countryCode 국가 코드 (2자리)
     * @return Certificate 목록
     */
    List<Certificate> findBySubjectInfo_CountryCode(String countryCode);

    /**
     * LDAP 미업로드 Certificate 목록 조회
     *
     * <p>uploadedToLdap = false이고 status = VALID인 인증서들을 조회합니다.</p>
     *
     * @return Certificate 목록
     */
    @Query("SELECT c FROM Certificate c WHERE c.uploadedToLdap = false AND c.status = 'VALID'")
    List<Certificate> findNotUploadedToLdap();

    /**
     * 만료 예정 Certificate 목록 조회
     *
     * <p>현재 시간으로부터 지정한 일수 이내에 만료될 인증서들을 조회합니다.</p>
     *
     * <p>계산 로직:</p>
     * <pre>
     * expiryThreshold = NOW + daysThreshold
     * WHERE validity.notAfter <= expiryThreshold
     *   AND validity.notAfter > NOW
     *   AND status = 'VALID'
     * </pre>
     *
     * @param expiryThreshold 만료 임계 날짜 (현재 시간 + daysThreshold)
     * @param now 현재 시간
     * @return Certificate 목록
     */
    @Query("""
        SELECT c FROM Certificate c
        WHERE c.validity.notAfter <= :expiryThreshold
          AND c.validity.notAfter > :now
          AND c.status = 'VALID'
        ORDER BY c.validity.notAfter ASC
        """)
    List<Certificate> findExpiringSoon(
        @Param("expiryThreshold") LocalDateTime expiryThreshold,
        @Param("now") LocalDateTime now
    );

    /**
     * 상태별 Certificate 개수 조회
     *
     * @param status 인증서 상태
     * @return 개수
     */
    long countByStatus(CertificateStatus status);

    /**
     * 타입별 Certificate 개수 조회
     *
     * @param certificateType 인증서 타입
     * @return 개수
     */
    long countByCertificateType(CertificateType certificateType);

    /**
     * Subject DN으로 Certificate 조회
     *
     * <p><b>사용 목적</b>:</p>
     * <ul>
     *   <li>Trust Chain 구축 시 발급자 인증서 검색</li>
     *   <li>Issuer DN과 Subject DN 매칭</li>
     * </ul>
     *
     * <p><b>쿼리 예시</b>:</p>
     * <pre>{@code
     * // Subject DN: "CN=CSCA KR, O=Korea, C=KR"
     * // WHERE subjectInfo.distinguishedName = ?
     * }</pre>
     *
     * @param subjectDn Subject Distinguished Name (예: "CN=CSCA KR, O=Korea, C=KR")
     * @return Optional<Certificate> (존재하면 Certificate, 없으면 empty)
     */
    Optional<Certificate> findBySubjectInfo_DistinguishedName(String subjectDn);

    /**
     * Issuer DN으로 Certificate 조회
     *
     * <p><b>사용 목적</b>:</p>
     * <ul>
     *   <li>특정 발급자가 발급한 인증서 검색</li>
     *   <li>발급자별 인증서 그룹화</li>
     * </ul>
     *
     * <p><b>쿼리 예시</b>:</p>
     * <pre>{@code
     * // Issuer DN: "CN=CSCA KR, O=Korea, C=KR"
     * // WHERE issuerInfo.distinguishedName = ?
     * }</pre>
     *
     * @param issuerDn Issuer Distinguished Name (예: "CN=CSCA KR, O=Korea, C=KR")
     * @return Optional<Certificate> (존재하면 Certificate, 없으면 empty)
     */
    Optional<Certificate> findByIssuerInfo_DistinguishedName(String issuerDn);
}
