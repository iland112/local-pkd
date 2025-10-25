package com.smartcoreinc.localpkd.certificatevalidation.domain.repository;

import com.smartcoreinc.localpkd.certificatevalidation.domain.model.Certificate;
import com.smartcoreinc.localpkd.certificatevalidation.domain.model.CertificateId;
import com.smartcoreinc.localpkd.certificatevalidation.domain.model.CertificateStatus;
import com.smartcoreinc.localpkd.certificatevalidation.domain.model.CertificateType;

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
     * 상태별 Certificate 목록 조회
     *
     * <p>특정 상태의 인증서들을 조회합니다.</p>
     *
     * @param status 인증서 상태 (VALID, EXPIRED, REVOKED 등)
     * @return Certificate 목록 (빈 리스트 가능)
     */
    List<Certificate> findByStatus(CertificateStatus status);

    /**
     * 타입별 Certificate 목록 조회
     *
     * <p>CSCA, DSC 등 특정 타입의 인증서들을 조회합니다.</p>
     *
     * @param type 인증서 타입
     * @return Certificate 목록 (빈 리스트 가능)
     */
    List<Certificate> findByType(CertificateType type);

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
     * 전체 Certificate 개수 조회
     *
     * @return 전체 인증서 개수
     */
    long count();

    /**
     * 상태별 Certificate 개수 조회
     *
     * @param status 인증서 상태
     * @return 해당 상태의 인증서 개수
     */
    long countByStatus(CertificateStatus status);

    /**
     * 타입별 Certificate 개수 조회
     *
     * @param type 인증서 타입
     * @return 해당 타입의 인증서 개수
     */
    long countByType(CertificateType type);
}
