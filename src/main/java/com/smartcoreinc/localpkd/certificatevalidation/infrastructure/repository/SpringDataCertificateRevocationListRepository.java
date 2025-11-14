package com.smartcoreinc.localpkd.certificatevalidation.infrastructure.repository;

import com.smartcoreinc.localpkd.certificatevalidation.domain.model.CertificateRevocationList;
import com.smartcoreinc.localpkd.certificatevalidation.domain.model.CrlId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * SpringDataCertificateRevocationListRepository - Spring Data JPA 기반 CRL Repository
 *
 * <p><b>Spring Data JPA Integration</b>:</p>
 * <ul>
 *   <li>자동 생성된 CRUD 메서드 제공</li>
 *   <li>Query Method Naming Convention 사용</li>
 *   <li>@Query 어노테이션으로 복잡한 쿼리 작성</li>
 * </ul>
 *
 * <p><b>책임</b>:</p>
 * <ul>
 *   <li>CertificateRevocationList Aggregate의 데이터베이스 I/O</li>
 *   <li>JPQL 쿼리 실행</li>
 *   <li>페이징 및 정렬 지원</li>
 * </ul>
 *
 * <p><b>주의</b>:</p>
 * <ul>
 *   <li>Domain Layer의 CertificateRevocationListRepository를 직접 호출하지 말 것</li>
 *   <li>JpaCertificateRevocationListRepository를 통해서만 접근</li>
 *   <li>Query Method 이름은 정확해야 함 (오타 시 쿼리 실행 불가)</li>
 * </ul>
 *
 * @see CertificateRevocationList
 * @see JpaRepository
 * @author SmartCore Inc.
 * @version 1.0
 * @since 2025-10-24
 */
@Repository
public interface SpringDataCertificateRevocationListRepository extends JpaRepository<CertificateRevocationList, CrlId> {

    /**
     * CSCA 발급자명과 국가 코드로 CRL 조회
     *
     * <p>Query Method Naming Convention 사용</p>
     *
     * @param issuerName CSCA 발급자명 (IssuerName Value Object의 값)
     * @param countryCode 국가 코드 (CountryCode Value Object의 값)
     * @return CRL (없으면 empty Optional)
     */
    Optional<CertificateRevocationList> findByIssuerName_ValueAndCountryCode_Value(
            String issuerName,
            String countryCode
    );

    /**
     * CSCA 발급자명으로 모든 CRL 조회
     *
     * <p>특정 CSCA가 발행한 모든 CRL을 조회합니다.</p>
     *
     * @param issuerName CSCA 발급자명 (IssuerName Value Object의 값)
     * @return CRL 목록 (없으면 빈 리스트)
     */
    List<CertificateRevocationList> findByIssuerName_Value(String issuerName);

    /**
     * 국가 코드로 모든 CRL 조회
     *
     * <p>특정 국가의 모든 CRL을 조회합니다.</p>
     *
     * @param countryCode 국가 코드 (CountryCode Value Object의 값)
     * @return CRL 목록 (없으면 빈 리스트)
     */
    List<CertificateRevocationList> findByCountryCode_Value(String countryCode);

    /**
     * 업로드 ID로 CRL 목록 조회
     *
     * <p>특정 업로드 파일에서 추출된 모든 CRL을 조회합니다.</p>
     * <p>Phase 17: ValidateCertificatesUseCase에서 사용됩니다.</p>
     *
     * <p><b>Attempt 9 (2025-10-30): Native SQL with UUID Casting</b>:</p>
     * <ul>
     *   <li>Spring Data JPA의 기본 UUID 파라미터 바인딩 문제 회피</li>
     *   <li>Native SQL 쿼리로 UUID를 명시적으로 ::uuid로 캐스팅</li>
     *   <li>8개 시도 실패 후의 최종 해결책</li>
     * </ul>
     *
     * @param uploadId 원본 업로드 파일 ID (File Upload Context)
     * @return CRL 목록 (빈 리스트 가능)
     * @since Phase 17 Task 1.2 (Attempt 9 - Native SQL with UUID Casting)
     */
    @Query(value = "SELECT * FROM certificate_revocation_list WHERE upload_id = CAST(:uploadId AS uuid)",
           nativeQuery = true)
    List<CertificateRevocationList> findByUploadId(java.util.UUID uploadId);

    /**
     * 발급자명과 국가 코드로 존재 여부 확인
     *
     * @param issuerName CSCA 발급자명
     * @param countryCode 국가 코드
     * @return 존재하면 true
     */
    boolean existsByIssuerName_ValueAndCountryCode_Value(
            String issuerName,
            String countryCode
    );

    /**
     * 만료되지 않은 CRL 조회
     *
     * <p>현재 시간이 nextUpdate 이전인 CRL만 조회합니다.</p>
     *
     * @return 유효한 CRL 목록
     */
    @Query("SELECT c FROM CertificateRevocationList c WHERE c.validityPeriod.notAfter > CURRENT_TIMESTAMP ORDER BY c.validityPeriod.notAfter DESC")
    List<CertificateRevocationList> findValidCrls();

    /**
     * 특정 CSCA의 가장 최신 CRL 조회
     *
     * <p>nextUpdate 기준 가장 최신 CRL을 반환합니다.</p>
     *
     * @param issuerName CSCA 발급자명
     * @return 가장 최신 CRL (없으면 empty Optional)
     */
    @Query("SELECT c FROM CertificateRevocationList c " +
           "WHERE c.issuerName.value = :issuerName " +
           "ORDER BY c.validityPeriod.notAfter DESC LIMIT 1")
    Optional<CertificateRevocationList> findLatestByIssuerName(
            @Param("issuerName") String issuerName
    );

    /**
     * 폐기된 인증서가 있는 CRL 조회
     *
     * <p>revokedCount > 0인 모든 CRL을 조회합니다.</p>
     *
     * @return 폐기된 인증서가 있는 CRL 목록
     */
    @Query("SELECT c FROM CertificateRevocationList c " +
           "WHERE c.x509CrlData.revokedCount > 0 " +
           "ORDER BY c.x509CrlData.revokedCount DESC")
    List<CertificateRevocationList> findCrlsWithRevokedCertificates();
}
