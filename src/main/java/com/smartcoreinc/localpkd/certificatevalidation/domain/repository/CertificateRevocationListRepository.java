package com.smartcoreinc.localpkd.certificatevalidation.domain.repository;

import com.smartcoreinc.localpkd.certificatevalidation.domain.model.*;

import java.util.List;
import java.util.Optional;

/**
 * CertificateRevocationListRepository - CRL (Certificate Revocation List) Repository 인터페이스
 *
 * <p><b>DDD Repository Pattern</b>:</p>
 * <ul>
 *   <li>Domain Layer 인터페이스 (Infrastructure 불의존)</li>
 *   <li>Aggregate Root 저장/조회 책임</li>
 *   <li>의존성 역전 원칙 (DIP) 적용</li>
 * </ul>
 *
 * <p><b>책임</b>:</p>
 * <ul>
 *   <li>CertificateRevocationList Aggregate 저장</li>
 *   <li>발급자 및 국가로 CRL 조회</li>
 *   <li>폐기 여부 확인</li>
 * </ul>
 *
 * <p><b>사용 예시</b>:</p>
 * <pre>{@code
 * // CRL 저장
 * CertificateRevocationList crl = CertificateRevocationList.create(...);
 * repository.save(crl);
 *
 * // 특정 CSCA와 국가로 CRL 조회
 * Optional<CertificateRevocationList> crlOpt =
 *     repository.findByIssuerNameAndCountry("CSCA-QA", "QA");
 *
 * // 발급자로 모든 CRL 조회
 * List<CertificateRevocationList> crls =
 *     repository.findByIssuerName("CSCA-QA");
 *
 * // 폐기 여부 확인
 * if (crlOpt.isPresent()) {
 *     CertificateRevocationList crl = crlOpt.get();
 *     boolean isRevoked = crl.isRevoked(serialNumber);
 * }
 * }</pre>
 *
 * @see CertificateRevocationList
 * @see CrlId
 * @see IssuerName
 * @see CountryCode
 * @author SmartCore Inc.
 * @version 1.0
 * @since 2025-10-24
 */
public interface CertificateRevocationListRepository {

    /**
     * CRL 저장 (생성 또는 수정)
     *
     * <p>새로운 CRL을 저장하거나 기존 CRL을 수정합니다.
     * Domain Events도 함께 발행됩니다.</p>
     *
     * @param crl 저장할 CRL Aggregate
     * @return 저장된 CRL (ID 포함)
     * @throws IllegalArgumentException crl이 null인 경우
     */
    CertificateRevocationList save(CertificateRevocationList crl);

    /**
     * 여러 CRL 일괄 저장 (배치 저장)
     *
     * <p>LDIF 파일에서 추출된 여러 CRL을 한 번에 저장합니다.
     * 배치 저장 최적화로 성능을 향상시킵니다.
     * 모든 CRL의 Domain Events를 발행합니다.</p>
     *
     * <p><b>사용 예시</b>:</p>
     * <pre>{@code
     * List<CertificateRevocationList> crls = List.of(
     *     CertificateRevocationList.create(...),
     *     CertificateRevocationList.create(...),
     *     CertificateRevocationList.create(...)
     * );
     *
     * List<CertificateRevocationList> saved = repository.saveAll(crls);
     *
     * // CrlsExtractedEvent 발행
     * eventPublisher.publishEvent(new CrlsExtractedEvent(
     *     parsedFileId,
     *     saved.size(),           // totalCrlCount
     *     saved.size(),           // successCount
     *     0,                       // failureCount
     *     issuerNames,            // crlIssuerNames
     *     revokedCount            // totalRevokedCertificates
     * ));
     * }</pre>
     *
     * @param crls 저장할 CRL Aggregate 목록
     * @return 저장된 CRL 목록 (ID 포함)
     * @throws IllegalArgumentException crls이 null이거나 빈 리스트인 경우
     */
    List<CertificateRevocationList> saveAll(List<CertificateRevocationList> crls);

    /**
     * CRL ID로 조회
     *
     * @param id CRL ID
     * @return CRL (없으면 empty Optional)
     * @throws IllegalArgumentException id가 null인 경우
     */
    Optional<CertificateRevocationList> findById(CrlId id);

    /**
     * CSCA 발급자명과 국가 코드로 조회
     *
     * <p>특정 CSCA(예: CSCA-QA)가 발행한 특정 국가(QA)의 CRL을 조회합니다.
     * CSCA와 국가는 1:1 매핑이므로 최대 1개 결과입니다.</p>
     *
     * @param issuerName CSCA 발급자명 (예: CSCA-QA)
     * @param countryCode 국가 코드 (예: QA)
     * @return CRL (없으면 empty Optional)
     * @throws IllegalArgumentException 입력값이 null이거나 비어있는 경우
     */
    Optional<CertificateRevocationList> findByIssuerNameAndCountry(
            String issuerName,
            String countryCode
    );

    /**
     * CSCA 발급자명으로 모든 CRL 조회
     *
     * <p>특정 CSCA(예: CSCA-QA)의 모든 CRL을 조회합니다.
     * 일반적으로 1개 결과이지만, 여러 버전이 있을 수 있습니다.</p>
     *
     * @param issuerName CSCA 발급자명 (예: CSCA-QA)
     * @return CRL 목록 (없으면 빈 리스트)
     * @throws IllegalArgumentException issuerName이 null이거나 비어있는 경우
     */
    List<CertificateRevocationList> findByIssuerName(String issuerName);

    /**
     * 국가 코드로 모든 CRL 조회
     *
     * <p>특정 국가(예: QA)의 모든 CRL을 조회합니다.
     * 일반적으로 1개 결과입니다.</p>
     *
     * @param countryCode 국가 코드 (예: QA)
     * @return CRL 목록 (없으면 빈 리스트)
     * @throws IllegalArgumentException countryCode가 null이거나 비어있는 경우
     */
    List<CertificateRevocationList> findByCountryCode(String countryCode);

    /**
     * 업로드 ID로 CRL 목록 조회
     *
     * <p>특정 업로드 파일에서 추출된 모든 CRL을 조회합니다.</p>
     * <p>Phase 17: ValidateCertificatesUseCase에서 사용됩니다.</p>
     *
     * <p><b>사용 예시</b>:</p>
     * <pre>{@code
     * UUID uploadId = UUID.fromString("550e8400-e29b-41d4-a716-446655440000");
     * List<CertificateRevocationList> crls = repository.findByUploadId(uploadId);
     *
     * // ValidateCertificatesUseCase에서 사용
     * for (CertificateRevocationList crl : crls) {
     *     // CRL 유효성 검증
     *     if (crl.isExpired()) {
     *         log.warn("CRL expired: {}", crl.getId());
     *     }
     * }
     * }</pre>
     *
     * @param uploadId 원본 업로드 파일 ID (File Upload Context)
     * @return CRL 목록 (빈 리스트 가능)
     * @throws IllegalArgumentException uploadId가 null인 경우
     * @since Phase 17 Task 1.2
     */
    List<CertificateRevocationList> findByUploadId(java.util.UUID uploadId);

    /**
     * CRL ID로 존재 여부 확인
     *
     * @param id CRL ID
     * @return 존재하면 true
     * @throws IllegalArgumentException id가 null인 경우
     */
    boolean existsById(CrlId id);

    /**
     * 발급자와 국가로 존재 여부 확인
     *
     * @param issuerName CSCA 발급자명
     * @param countryCode 국가 코드
     * @return 존재하면 true
     * @throws IllegalArgumentException 입력값이 null이거나 비어있는 경우
     */
    boolean existsByIssuerNameAndCountry(String issuerName, String countryCode);

    /**
     * CRL ID로 삭제
     *
     * <p>해당 CRL을 데이터베이스에서 삭제합니다.</p>
     *
     * @param id 삭제할 CRL ID
     * @throws IllegalArgumentException id가 null인 경우
     */
    void deleteById(CrlId id);

    /**
     * 전체 CRL 개수
     *
     * @return CRL 총 개수
     */
    long count();

    /**
     * 전체 CRL 조회
     *
     * <p>주의: 많은 수의 CRL이 있을 수 있으므로 페이징 사용 권장</p>
     *
     * @return 전체 CRL 목록
     */
    List<CertificateRevocationList> findAll();
}
