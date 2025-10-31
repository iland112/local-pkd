package com.smartcoreinc.localpkd.certificatevalidation.infrastructure.repository;

import com.smartcoreinc.localpkd.certificatevalidation.domain.model.Certificate;
import com.smartcoreinc.localpkd.certificatevalidation.domain.model.CertificateId;
import com.smartcoreinc.localpkd.certificatevalidation.domain.model.CertificateStatus;
import com.smartcoreinc.localpkd.certificatevalidation.domain.model.CertificateType;
import com.smartcoreinc.localpkd.certificatevalidation.domain.repository.CertificateRepository;
import com.smartcoreinc.localpkd.shared.event.EventBus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * JpaCertificateRepository - JPA 기반 인증서 Repository 구현체
 *
 * <p><b>Infrastructure Layer</b>:</p>
 * <ul>
 *   <li>Domain Layer의 {@link CertificateRepository} 인터페이스 구현</li>
 *   <li>Spring Data JPA와 EventBus 사용</li>
 *   <li>Adapter Pattern: Domain Repository를 JPA로 변환</li>
 * </ul>
 *
 * <p><b>책임 (Responsibilities)</b>:</p>
 * <ul>
 *   <li>Certificate Aggregate Root의 영속성 관리 (CRUD)</li>
 *   <li>Domain Events 자동 발행 (save 시)</li>
 *   <li>트랜잭션 경계 관리</li>
 *   <li>쿼리 메서드 구현</li>
 * </ul>
 *
 * <p><b>DDD 원칙 준수</b>:</p>
 * <ul>
 *   <li><b>의존성 역전 원칙</b>: Domain 인터페이스를 Infrastructure에서 구현</li>
 *   <li><b>Aggregate 일관성</b>: 저장 시 Domain Events 자동 발행</li>
 *   <li><b>트랜잭션 경계</b>: 각 메서드가 트랜잭션 단위</li>
 * </ul>
 *
 * <p><b>Domain Events 발행 흐름</b>:</p>
 * <pre>
 * 1. Aggregate Root 생성 시 Domain Event 추가
 *    → certificate.registerEvent(event)
 * 2. repository.save(certificate) 호출
 * 3. JPA 저장 완료
 * 4. EventBus.publishAll(events) 호출
 *    → Spring ApplicationEventPublisher로 발행
 * 5. certificate.clearDomainEvents() 호출
 *    → 이벤트 중복 발행 방지
 * 6. 저장된 Aggregate Root 반환
 * </pre>
 *
 * <p><b>사용 예시 - Use Case에서 주입</b>:</p>
 * <pre>{@code
 * @Service
 * @RequiredArgsConstructor
 * public class ValidateCertificateUseCase {
 *
 *     // Domain 인터페이스 주입 (구현체는 Spring이 자동 연결)
 *     private final CertificateRepository certificateRepository;
 *
 *     @Transactional
 *     public ValidateCertificateResponse execute(ValidateCertificateCommand command) {
 *         Certificate certificate = certificateRepository.findById(certId).orElseThrow();
 *
 *         // 검증 수행 후 결과 기록
 *         certificate.recordValidation(validationResult);
 *
 *         // 저장 시 Domain Events 자동 발행
 *         Certificate saved = certificateRepository.save(certificate);
 *
 *         return ValidateCertificateResponse.success(...);
 *     }
 * }
 * }</pre>
 *
 * @author SmartCore Inc.
 * @version 1.0
 * @since 2025-10-24
 * @see CertificateRepository
 * @see SpringDataCertificateRepository
 * @see EventBus
 */
@Slf4j
@Repository
@RequiredArgsConstructor
public class JpaCertificateRepository implements CertificateRepository {

    private final SpringDataCertificateRepository jpaRepository;
    private final EventBus eventBus;

    /**
     * Certificate 저장 및 Domain Events 발행
     *
     * <p>Aggregate Root를 저장한 후 Domain Events를 발행합니다.
     * 트랜잭션 커밋 후 이벤트가 발행됩니다.</p>
     *
     * @param certificate Certificate Aggregate Root
     * @return 저장된 Aggregate Root
     */
    @Override
    @Transactional
    public Certificate save(Certificate certificate) {
        log.debug("Saving Certificate: id={}, fingerprint={}",
            certificate.getId().getId(),
            certificate.getX509Data().getFingerprintSha256());

        // 1. Domain Events 발행 (JPA 저장 전에 발행 - transient 필드이므로 저장 후에는 사라짐)
        if (!certificate.getDomainEvents().isEmpty()) {
            log.debug("Publishing {} domain event(s) BEFORE save", certificate.getDomainEvents().size());
            eventBus.publishAll(certificate.getDomainEvents());
            certificate.clearDomainEvents();
        }

        // 2. JPA 저장
        Certificate saved = jpaRepository.save(certificate);

        log.debug("Certificate saved successfully: id={}", saved.getId().getId());

        return saved;
    }

    /**
     * ID로 Certificate 조회
     *
     * @param id Certificate ID
     * @return Optional<Certificate>
     */
    @Override
    @Transactional(readOnly = true)
    public Optional<Certificate> findById(CertificateId id) {
        log.debug("Finding Certificate by id: {}", id.getId());
        return jpaRepository.findById(id);
    }

    /**
     * SHA-256 지문으로 Certificate 조회
     *
     * @param fingerprintSha256 SHA-256 지문 (64자 16진수)
     * @return Optional<Certificate>
     */
    @Override
    @Transactional(readOnly = true)
    public Optional<Certificate> findByFingerprint(String fingerprintSha256) {
        log.debug("Finding Certificate by fingerprint: {}", fingerprintSha256.substring(0, 8) + "...");
        return jpaRepository.findByX509Data_FingerprintSha256(fingerprintSha256);
    }

    /**
     * 일련 번호로 Certificate 조회
     *
     * @param serialNumber 인증서 일련 번호 (16진수 문자열)
     * @return Optional<Certificate>
     */
    @Override
    @Transactional(readOnly = true)
    public Optional<Certificate> findBySerialNumber(String serialNumber) {
        log.debug("Finding Certificate by serialNumber: {}", serialNumber);
        return jpaRepository.findByX509Data_SerialNumber(serialNumber);
    }

    /**
     * ID로 Certificate 삭제
     *
     * @param id Certificate ID
     */
    @Override
    @Transactional
    public void deleteById(CertificateId id) {
        log.debug("Deleting Certificate by id: {}", id.getId());
        jpaRepository.deleteById(id);
    }

    /**
     * Certificate 존재 여부 확인
     *
     * @param id Certificate ID
     * @return 존재하면 true
     */
    @Override
    @Transactional(readOnly = true)
    public boolean existsById(CertificateId id) {
        log.debug("Checking existence of Certificate by id: {}", id.getId());
        return jpaRepository.existsById(id);
    }

    /**
     * SHA-256 지문으로 Certificate 존재 여부 확인
     *
     * @param fingerprintSha256 SHA-256 지문 (64자 16진수)
     * @return 존재하면 true
     */
    @Override
    @Transactional(readOnly = true)
    public boolean existsByFingerprint(String fingerprintSha256) {
        log.debug("Checking existence of Certificate by fingerprint: {}...",
            fingerprintSha256.substring(0, 8));
        return jpaRepository.existsByX509Data_FingerprintSha256(fingerprintSha256);
    }

    /**
     * 업로드 ID로 Certificate 목록 조회
     *
     * <p>특정 업로드 파일에서 추출된 모든 인증서를 조회합니다.</p>
     * <p>Phase 16-17 ValidateCertificatesUseCase에서 사용됩니다.</p>
     *
     * @param uploadId 원본 업로드 파일 ID (File Upload Context)
     * @return Certificate 목록 (빈 리스트 가능)
     * @throws IllegalArgumentException uploadId가 null인 경우
     */
    @Override
    @Transactional(readOnly = true)
    public List<Certificate> findByUploadId(java.util.UUID uploadId) {
        if (uploadId == null) {
            log.warn("Cannot find Certificates by uploadId: uploadId is null");
            throw new IllegalArgumentException("uploadId must not be null");
        }

        log.debug("Finding Certificates by uploadId: {}", uploadId);
        List<Certificate> certificates = jpaRepository.findByUploadId(uploadId);
        log.debug("Found {} Certificate(s) for uploadId: {}", certificates.size(), uploadId);

        return certificates;
    }

    /**
     * 상태별 Certificate 목록 조회
     *
     * @param status 인증서 상태 (VALID, EXPIRED, REVOKED 등)
     * @return Certificate 목록 (빈 리스트 가능)
     */
    @Override
    @Transactional(readOnly = true)
    public List<Certificate> findByStatus(CertificateStatus status) {
        log.debug("Finding Certificates by status: {}", status);
        return jpaRepository.findByStatus(status);
    }

    /**
     * 타입별 Certificate 목록 조회
     *
     * @param type 인증서 타입
     * @return Certificate 목록 (빈 리스트 가능)
     */
    @Override
    @Transactional(readOnly = true)
    public List<Certificate> findByType(CertificateType type) {
        log.debug("Finding Certificates by type: {}", type);
        return jpaRepository.findByCertificateType(type);
    }

    /**
     * 국가 코드별 Certificate 목록 조회
     *
     * @param countryCode 국가 코드 (2자리, 예: "KR", "US")
     * @return Certificate 목록 (빈 리스트 가능)
     */
    @Override
    @Transactional(readOnly = true)
    public List<Certificate> findByCountryCode(String countryCode) {
        log.debug("Finding Certificates by countryCode: {}", countryCode);
        return jpaRepository.findBySubjectInfo_CountryCode(countryCode);
    }

    /**
     * LDAP 미업로드 Certificate 목록 조회
     *
     * <p>아직 LDAP에 업로드되지 않은 유효한 인증서들을 조회합니다.</p>
     *
     * @return Certificate 목록 (빈 리스트 가능)
     */
    @Override
    @Transactional(readOnly = true)
    public List<Certificate> findNotUploadedToLdap() {
        log.debug("Finding Certificates not uploaded to LDAP");
        return jpaRepository.findNotUploadedToLdap();
    }

    /**
     * 만료 예정 Certificate 목록 조회
     *
     * <p>지정한 일수 이내에 만료될 인증서들을 조회합니다.</p>
     *
     * @param daysThreshold 경고 범위 (일)
     * @return Certificate 목록 (빈 리스트 가능)
     */
    @Override
    @Transactional(readOnly = true)
    public List<Certificate> findExpiringSoon(int daysThreshold) {
        log.debug("Finding Certificates expiring soon (within {} days)", daysThreshold);

        LocalDateTime now = LocalDateTime.now();
        LocalDateTime expiryThreshold = now.plusDays(daysThreshold);

        return jpaRepository.findExpiringSoon(expiryThreshold, now);
    }

    /**
     * 전체 Certificate 개수 조회
     *
     * @return 전체 인증서 개수
     */
    @Override
    @Transactional(readOnly = true)
    public long count() {
        log.debug("Counting all Certificates");
        return jpaRepository.count();
    }

    /**
     * 상태별 Certificate 개수 조회
     *
     * @param status 인증서 상태
     * @return 해당 상태의 인증서 개수
     */
    @Override
    @Transactional(readOnly = true)
    public long countByStatus(CertificateStatus status) {
        log.debug("Counting Certificates by status: {}", status);
        return jpaRepository.countByStatus(status);
    }

    /**
     * 타입별 Certificate 개수 조회
     *
     * @param type 인증서 타입
     * @return 해당 타입의 인증서 개수
     */
    @Override
    @Transactional(readOnly = true)
    public long countByType(CertificateType type) {
        log.debug("Counting Certificates by type: {}", type);
        return jpaRepository.countByCertificateType(type);
    }

    /**
     * Subject DN으로 Certificate 조회
     *
     * <p>Trust Chain 구축 시 발급자 인증서를 검색하기 위해 사용됩니다.</p>
     * <p>Subject DN은 인증서의 Subject Distinguished Name입니다.</p>
     * <p>발급자의 Subject DN = 피발급자의 Issuer DN이므로, 이 메서드로 발급자를 찾습니다.</p>
     *
     * @param subjectDn Subject Distinguished Name (예: "CN=CSCA KR, O=Korea, C=KR")
     * @return Optional<Certificate> (발견하면 Certificate, 없으면 empty)
     */
    @Override
    @Transactional(readOnly = true)
    public Optional<Certificate> findBySubjectDn(String subjectDn) {
        if (subjectDn == null || subjectDn.isBlank()) {
            log.warn("Cannot find Certificate by Subject DN: DN is null or blank");
            throw new IllegalArgumentException("subjectDn must not be null or blank");
        }

        log.debug("Finding Certificate by Subject DN: {}", subjectDn);
        Optional<Certificate> found = jpaRepository.findBySubjectInfo_DistinguishedName(subjectDn);

        if (found.isPresent()) {
            log.debug("Certificate found by Subject DN: id={}", found.get().getId().getId());
        } else {
            log.debug("Certificate not found by Subject DN: {}", subjectDn);
        }

        return found;
    }

    /**
     * Issuer DN으로 Certificate 조회
     *
     * <p>특정 발급자가 발급한 인증서를 검색합니다.</p>
     * <p>발급자별 인증서 그룹화 및 발급자 검증에 사용됩니다.</p>
     *
     * @param issuerDn Issuer Distinguished Name (예: "CN=CSCA KR, O=Korea, C=KR")
     * @return Optional<Certificate> (발견하면 Certificate, 없으면 empty)
     */
    @Override
    @Transactional(readOnly = true)
    public Optional<Certificate> findByIssuerDn(String issuerDn) {
        if (issuerDn == null || issuerDn.isBlank()) {
            log.warn("Cannot find Certificate by Issuer DN: DN is null or blank");
            throw new IllegalArgumentException("issuerDn must not be null or blank");
        }

        log.debug("Finding Certificate by Issuer DN: {}", issuerDn);
        Optional<Certificate> found = jpaRepository.findByIssuerInfo_DistinguishedName(issuerDn);

        if (found.isPresent()) {
            log.debug("Certificate found by Issuer DN: id={}", found.get().getId().getId());
        } else {
            log.debug("Certificate not found by Issuer DN: {}", issuerDn);
        }

        return found;
    }
}
