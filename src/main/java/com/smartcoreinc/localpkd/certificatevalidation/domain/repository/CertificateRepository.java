package com.smartcoreinc.localpkd.certificatevalidation.domain.repository;

import com.smartcoreinc.localpkd.certificatevalidation.domain.model.Certificate;
import com.smartcoreinc.localpkd.certificatevalidation.domain.model.CertificateId;
import com.smartcoreinc.localpkd.certificatevalidation.domain.model.CertificateSourceType;
import com.smartcoreinc.localpkd.certificatevalidation.domain.model.CertificateStatus;
import com.smartcoreinc.localpkd.certificatevalidation.domain.model.CertificateType;
import com.smartcoreinc.localpkd.certificatevalidation.domain.model.CountryCount;
import com.smartcoreinc.localpkd.certificatevalidation.domain.model.TypeCount;

import java.util.List;
import java.util.Optional;

/**
 * CertificateRepository - 인증서 Repository Interface (Domain Layer)
 *
 * <p><b>DDD Repository Pattern</b>:</p>
 * <ul>
 *   <li>Domain Layer에 인터페이스 정의 (Dependency Inversion)</li>
 *   <li>Infrastructure Layer에서 구현 (JPA, JDBC 등)</li>
 *   <li>Aggregate Root (Certificate) 접근의 유일한 진입점</li>
 * </ul>
 *
 * <p><b>책임</b>:</p>
 * <ul>
 *   <li>Certificate Aggregate의 영속성 관리</li>
 *   <li>ID 기반 조회</li>
 *   <li>지문(Fingerprint) 기반 중복 검사</li>
 *   <li>상태 및 타입별 조회</li>
 * </ul>
 *
 * <p><b>설계 원칙</b>:</p>
 * <ul>
 *   <li>Collection-like Interface: add(), remove() 대신 save(), delete()</li>
 *   <li>Aggregate Boundaries: Certificate만 반환 (내부 Value Objects 직접 노출 금지)</li>
 *   <li>Domain Events: save() 시 자동 발행</li>
 * </ul>
 *
 * <p><b>사용 예시</b>:</p>
 * <pre>{@code
 * // 인증서 저장 (생성 또는 업데이트)
 * Certificate cert = Certificate.create(...);
 * Certificate saved = certificateRepository.save(cert);
 * // → Domain Events 자동 발행
 *
 * // ID로 조회
 * Optional<Certificate> found = certificateRepository.findById(certId);
 *
 * // 지문으로 중복 검사
 * boolean exists = certificateRepository.existsByFingerprint("a1b2c3...");
 *
 * // 상태별 조회
 * List<Certificate> validCerts = certificateRepository.findByStatus(CertificateStatus.VALID);
 *
 * // 타입별 조회
 * List<Certificate> cscaCerts = certificateRepository.findByType(CertificateType.CSCA);
 * }</pre>
 *
 * @see Certificate
 * @see CertificateId
 * @author SmartCore Inc.
 * @version 1.0
 * @since 2025-10-24
 */
public interface CertificateRepository {

    /**
     * Certificate 저장 (생성 또는 업데이트)
     *
     * <p>저장 후 Domain Events를 자동으로 발행합니다.</p>
     *
     * @param certificate 저장할 Certificate Aggregate
     * @return 저장된 Certificate
     * @throws IllegalArgumentException certificate가 null인 경우
     */
    Certificate save(Certificate certificate);

    /**
     * Certificate 일괄 저장 (Phase 3에서 추가)
     *
     * <p>Master List에서 추출한 여러 CSCA 인증서를 한 번에 저장합니다.</p>
     * <p>저장 후 Domain Events를 자동으로 발행합니다.</p>
     *
     * @param certificates 저장할 Certificate 목록
     * @return 저장된 Certificate 목록
     * @throws IllegalArgumentException certificates가 null이거나 빈 리스트인 경우
     */
    List<Certificate> saveAll(List<Certificate> certificates);

    /**
     * ID로 Certificate 조회
     *
     * @param id Certificate ID
     * @return Optional<Certificate>
     */
    Optional<Certificate> findById(CertificateId id);

    /**
     * SHA-256 지문으로 Certificate 조회
     *
     * <p>중복 검사 및 동일 인증서 확인에 사용됩니다.</p>
     *
     * @param fingerprintSha256 SHA-256 지문 (64자 16진수)
     * @return Optional<Certificate>
     */
    Optional<Certificate> findByFingerprint(String fingerprintSha256);

    /**
     * 일련 번호로 Certificate 조회
     *
     * @param serialNumber 인증서 일련 번호 (16진수 문자열)
     * @return Optional<Certificate>
     */
    Optional<Certificate> findBySerialNumber(String serialNumber);

    /**
     * Subject DN으로 Certificate 조회
     *
     * <p>Trust Chain 구축 시 발급자 인증서를 찾기 위해 사용됩니다.</p>
     * <p>Issuer DN과 Subject DN이 일치하는 인증서를 검색합니다.</p>
     *
     * <p><b>사용 예시</b>:</p>
     * <pre>{@code
     * // 발급자 인증서 검색 (buildTrustChain에서 사용)
     * String issuerDn = "CN=CSCA KR, O=Korea, C=KR";
     * Optional<Certificate> issuerCert = certificateRepository.findBySubjectDn(issuerDn);
     * }</pre>
     *
     * @param subjectDn Subject Distinguished Name (예: "CN=CSCA KR, O=Korea, C=KR")
     * @return Optional<Certificate> (없으면 empty)
     * @throws IllegalArgumentException subjectDn이 null이거나 빈 문자열인 경우
     */
    Optional<Certificate> findBySubjectDn(String subjectDn);

    /**
     * Issuer DN으로 Certificate 조회 (발급자가 이 DN인 인증서)
     *
     * <p>특정 발급자가 발급한 인증서를 검색합니다.</p>
     * <p>주로 발급자별 인증서 검색에 사용됩니다.</p>
     *
     * <p><b>사용 예시</b>:</p>
     * <pre>{@code
     * // CSCA KR이 발급한 인증서 검색
     * String issuerDn = "CN=CSCA KR, O=Korea, C=KR";
     * Optional<Certificate> cert = certificateRepository.findByIssuerDn(issuerDn);
     * }</pre>
     *
     * @param issuerDn Issuer Distinguished Name (예: "CN=CSCA KR, O=Korea, C=KR")
     * @return Optional<Certificate> (없으면 empty)
     * @throws IllegalArgumentException issuerDn이 null이거나 빈 문자열인 경우
     */
    Optional<Certificate> findByIssuerDn(String issuerDn);

    /**
     * ID로 Certificate 삭제
     *
     * @param id Certificate ID
     */
    void deleteById(CertificateId id);

    /**
     * Certificate 존재 여부 확인
     *
     * @param id Certificate ID
     * @return 존재하면 true
     */
    boolean existsById(CertificateId id);

    /**
     * SHA-256 지문으로 Certificate 존재 여부 확인
     *
     * <p>중복 업로드 방지에 사용됩니다.</p>
     *
     * @param fingerprintSha256 SHA-256 지문 (64자 16진수)
     * @return 존재하면 true
     */
    boolean existsByFingerprint(String fingerprintSha256);

    /**
     * 배치 fingerprint로 기존 Certificate 조회
     *
     * <p>Phase 1-1 배치 중복 체크 최적화: N+1 query → single IN query</p>
     * <p>여러 fingerprint를 한 번에 조회하여 성능 향상</p>
     *
     * <p><b>사용 예시</b>:</p>
     * <pre>{@code
     * Set<String> fingerprintsToCheck = Set.of("abc123...", "def456...", "ghi789...");
     * List<String> existingFingerprints = certificateRepository.findFingerprintsByFingerprintSha256In(fingerprintsToCheck);
     *
     * // 중복되지 않은 인증서만 필터링
     * List<Certificate> nonDuplicates = certificates.stream()
     *     .filter(cert -> !existingFingerprints.contains(cert.getX509Data().getFingerprintSha256()))
     *     .collect(Collectors.toList());
     * }</pre>
     *
     * @param fingerprints 조회할 fingerprint 집합
     * @return DB에 이미 존재하는 fingerprint 목록 (없으면 빈 리스트)
     * @throws IllegalArgumentException fingerprints가 null인 경우
     */
    java.util.List<String> findFingerprintsByFingerprintSha256In(java.util.Set<String> fingerprints);

    /**
     * 업로드 ID로 Certificate 목록 조회
     *
     * <p>특정 업로드 파일에서 추출된 모든 인증서를 조회합니다.</p>
     * <p>Phase 16-17에서 Use Case 구현에 필요합니다.</p>
     *
     * <p><b>사용 예시</b>:</p>
     * <pre>{@code
     * UUID uploadId = UUID.fromString("550e8400-e29b-41d4-a716-446655440000");
     * List<Certificate> certificates = certificateRepository.findByUploadId(uploadId);
     *
     * // ValidateCertificatesUseCase에서 사용
     * for (Certificate cert : certificates) {
     *     ValidationResult result = trustChainValidator.validate(cert);
     *     cert.recordValidation(result);
     * }
     * }</pre>
     *
     * @param uploadId 원본 업로드 파일 ID (File Upload Context)
     * @return Certificate 목록 (빈 리스트 가능)
     * @throws IllegalArgumentException uploadId가 null인 경우
     */
    List<Certificate> findByUploadId(java.util.UUID uploadId);

    /**
     * 상태별 Certificate 목록 조회
     *
     * <p>특정 상태의 인증서들을 조회합니다.</p>
     *
     * @param status 인증서 상태 (VALID, EXPIRED, REVOKED 등)
     * @return Certificate 목록 (빈 리스트 가능)
     */
    List<Certificate> findByStatus(String status);

    /**
     * 국가 코드별 Certificate 목록 조회
     *
     * <p>특정 국가에서 발행한 인증서들을 조회합니다.</p>
     *
     * @param countryCode 국가 코드 (2자리, 예: "KR", "US")
     * @return Certificate 목록 (빈 리스트 가능)
     */
    List<Certificate> findByCountryCode(String countryCode);

    /**
     * LDAP 미업로드 Certificate 목록 조회
     *
     * <p>아직 LDAP에 업로드되지 않은 유효한 인증서들을 조회합니다.</p>
     *
     * @return Certificate 목록 (빈 리스트 가능)
     */
    List<Certificate> findNotUploadedToLdap();

    /**
     * 만료 예정 Certificate 목록 조회
     *
     * <p>지정한 일수 이내에 만료될 인증서들을 조회합니다.</p>
     *
     * @param daysThreshold 경고 범위 (일)
     * @return Certificate 목록 (빈 리스트 가능)
     */
    List<Certificate> findExpiringSoon(int daysThreshold);

    /**
     * 출처 타입별 Certificate 목록 조회
     *
     * <p>특정 출처 타입의 인증서들을 조회합니다.</p>
     * <p>Master List에서 추출한 CSCA, LDIF DSC, LDIF CSCA 구분 가능</p>
     *
     * <p><b>사용 예시</b>:</p>
     * <pre>{@code
     * // Master List에서 추출한 CSCA만 조회
     * List<Certificate> masterListCerts =
     *     certificateRepository.findBySourceType(CertificateSourceType.MASTER_LIST);
     *
     * // LDIF 파일의 DSC만 조회
     * List<Certificate> ldifDscCerts =
     *     certificateRepository.findBySourceType(CertificateSourceType.LDIF_DSC);
     * }</pre>
     *
     * @param sourceType 출처 타입 (MASTER_LIST, LDIF_DSC, LDIF_CSCA)
     * @return Certificate 목록 (빈 리스트 가능)
     * @throws IllegalArgumentException sourceType이 null인 경우
     */
    List<Certificate> findBySourceType(CertificateSourceType sourceType);

    /**
     * Master List ID로 Certificate 목록 조회
     *
     * <p>특정 Master List에서 추출된 모든 CSCA 인증서를 조회합니다.</p>
     * <p>Master List와 개별 인증서 간의 추적성(traceability)을 보장합니다.</p>
     *
     * <p><b>사용 예시</b>:</p>
     * <pre>{@code
     * UUID masterListId = masterList.getId().getValue();
     * List<Certificate> extractedCscas = certificateRepository.findByMasterListId(masterListId);
     *
     * // Master List binary와 추출된 개별 CSCA의 매핑 확인
     * System.out.println("Master List contains " + extractedCscas.size() + " CSCA certificates");
     * }</pre>
     *
     * @param masterListId Master List ID (master_list 테이블의 PK)
     * @return Certificate 목록 (빈 리스트 가능)
     * @throws IllegalArgumentException masterListId가 null인 경우
     */
    List<Certificate> findByMasterListId(java.util.UUID masterListId);

    /**
     * Master List에서 추출된 모든 CSCA 인증서 조회
     *
     * <p>sourceType이 MASTER_LIST인 모든 인증서를 조회합니다.</p>
     * <p>{@code findBySourceType(CertificateSourceType.MASTER_LIST)}와 동일합니다.</p>
     *
     * @return Certificate 목록 (빈 리스트 가능)
     */
    List<Certificate> findMasterListCertificates();

    /**
     * LDIF 파일에서 로드된 모든 인증서 조회
     *
     * <p>sourceType이 LDIF_DSC 또는 LDIF_CSCA인 모든 인증서를 조회합니다.</p>
     * <p>LDIF 파일에서 직접 로드한 개별 인증서들입니다.</p>
     *
     * @return Certificate 목록 (빈 리스트 가능)
     */
    List<Certificate> findLdifCertificates();

    /**
     * 전체 Certificate 개수 조회 (GetCertificateStatisticsUseCase에서 사용)
     *
     * @return 전체 인증서 개수
     */
    long countAllBy();

    /**
     * 상태별 Certificate 개수 조회 (GetCertificateStatisticsUseCase에서 사용)
     *
     * @param status 인증서 상태 (String, 예: "VALID")
     * @return 해당 상태의 인증서 개수
     */
    long countByStatus(String status);

    /**
     * 인증서 타입별 개수 조회 (GetCertificateStatisticsUseCase에서 사용)
     *
     * @return 인증서 타입별 개수
     */
    List<TypeCount> countCertificatesByType();

    /**
     * 발행 국가 코드별 인증서 개수 조회 (GetCertificateStatisticsUseCase에서 사용)
     *
     * @return 인증서 타입별 개수
     */
    List<CountryCount> countCertificatesByCountry();


    /**
     * ID 목록으로 Certificate 일괄 조회
     *
     * <p>비동기 LDAP 업로드 시 배치 ID 목록으로 인증서를 조회합니다.</p>
     * <p>MSA 전환 대비: RabbitMQ 메시지에서 받은 ID 목록으로 조회</p>
     *
     * @param ids Certificate ID 목록
     * @return Certificate 목록 (존재하는 것만 반환)
     */
    List<Certificate> findAllById(List<CertificateId> ids);

    /**
     * 인증서 타입별 Certificate 목록 조회
     *
     * <p>특정 타입의 모든 인증서를 조회합니다.</p>
     * <p>DSC 검증 시 CSCA 캐시 구축에 사용됩니다 (Phase 1-2 최적화)</p>
     *
     * <p><b>사용 예시</b>:</p>
     * <pre>{@code
     * // 전체 CSCA 인증서 조회 (DSC 검증용 캐시 구축)
     * List<Certificate> allCscas = certificateRepository.findAllByType(CertificateType.CSCA);
     *
     * // CSCA 캐시 구축
     * Map<String, Certificate> cscaCache = allCscas.stream()
     *     .collect(Collectors.toMap(
     *         cert -> cert.getSubjectInfo().getDistinguishedName(),
     *         cert -> cert
     *     ));
     * }</pre>
     *
     * @param certificateType 인증서 타입 (CSCA, DSC, DSC_NC)
     * @return Certificate 목록 (빈 리스트 가능)
     * @throws IllegalArgumentException certificateType이 null인 경우
     */
    List<Certificate> findAllByType(CertificateType certificateType);
}
