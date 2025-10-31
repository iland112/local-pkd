package com.smartcoreinc.localpkd.certificatevalidation.infrastructure.repository;

import com.smartcoreinc.localpkd.certificatevalidation.domain.model.CertificateRevocationList;
import com.smartcoreinc.localpkd.certificatevalidation.domain.model.CrlId;
import com.smartcoreinc.localpkd.certificatevalidation.domain.repository.CertificateRevocationListRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * JpaCertificateRevocationListRepository - Domain Repository 구현체
 *
 * <p><b>DDD Adapter Pattern</b>:</p>
 * <ul>
 *   <li>Domain Layer의 CertificateRevocationListRepository 구현</li>
 *   <li>Infrastructure Layer의 SpringDataCertificateRevocationListRepository 사용</li>
 *   <li>의존성 역전 원칙 (DIP) 준수</li>
 * </ul>
 *
 * <p><b>책임</b>:</p>
 * <ul>
 *   <li>Domain Repository 메서드 구현</li>
 *   <li>Domain Events 발행</li>
 *   <li>예외 처리 및 로깅</li>
 *   <li>Transactional 경계 관리</li>
 * </ul>
 *
 * <p><b>사용 패턴</b>:</p>
 * <pre>{@code
 * // 주입받기 (Domain Repository 인터페이스 사용)
 * @RequiredArgsConstructor
 * public class SomeUseCase {
 *     private final CertificateRevocationListRepository crlRepository;
 *
 *     public void execute() {
 *         // 조회
 *         Optional<CertificateRevocationList> crl =
 *             crlRepository.findByIssuerNameAndCountry("CSCA-QA", "QA");
 *
 *         // 저장
 *         CertificateRevocationList saved = crlRepository.save(crl.get());
 *     }
 * }
 * }</pre>
 *
 * @see CertificateRevocationListRepository
 * @see SpringDataCertificateRevocationListRepository
 * @see CertificateRevocationList
 * @author SmartCore Inc.
 * @version 1.0
 * @since 2025-10-24
 */
@Slf4j
@Repository
@RequiredArgsConstructor
public class JpaCertificateRevocationListRepository implements CertificateRevocationListRepository {

    private final SpringDataCertificateRevocationListRepository jpaRepository;
    private final ApplicationEventPublisher eventPublisher;

    /**
     * CRL 저장 (생성 또는 수정)
     *
     * <p>Aggregate Root를 저장하고 Domain Events를 발행합니다.</p>
     *
     * @param crl 저장할 CRL Aggregate
     * @return 저장된 CRL
     * @throws IllegalArgumentException crl이 null인 경우
     */
    @Override
    @Transactional
    public CertificateRevocationList save(CertificateRevocationList crl) {
        if (crl == null) {
            throw new IllegalArgumentException("CRL cannot be null");
        }

        log.debug("Saving CRL: id={}, issuer={}, country={}",
            crl.getId().getId(), crl.getIssuerName().getValue(), crl.getCountryCode().getValue());

        try {
            // 1. Domain Events 발행 (JPA 저장 전에 발행 - transient 필드이므로 저장 후에는 사라짐)
            if (!crl.getDomainEvents().isEmpty()) {
                log.debug("Publishing {} domain events from CRL BEFORE save: {}",
                    crl.getDomainEvents().size(), crl.getId().getId());
                crl.getDomainEvents().forEach(event -> {
                    log.debug("Publishing CRL event: {}", event.getClass().getSimpleName());
                    eventPublisher.publishEvent(event);
                });
                crl.clearDomainEvents();
            }

            // 2. JPA를 통해 저장
            CertificateRevocationList saved = jpaRepository.save(crl);

            log.info("CRL saved successfully: id={}", saved.getId().getId());
            return saved;

        } catch (Exception e) {
            log.error("Error saving CRL: id={}, issuer={}, country={}",
                crl.getId().getId(), crl.getIssuerName().getValue(),
                crl.getCountryCode().getValue(), e);
            throw e;
        }
    }

    /**
     * 여러 CRL 일괄 저장 (배치 저장)
     *
     * <p>LDIF 파일에서 추출된 여러 CRL을 한 번에 저장합니다.
     * 배치 저장 최적화로 성능을 향상시키고, 모든 CRL의 Domain Events를 발행합니다.</p>
     *
     * @param crls 저장할 CRL Aggregate 목록
     * @return 저장된 CRL 목록
     * @throws IllegalArgumentException crls이 null이거나 빈 리스트인 경우
     */
    @Override
    @Transactional
    public List<CertificateRevocationList> saveAll(List<CertificateRevocationList> crls) {
        if (crls == null) {
            throw new IllegalArgumentException("CRL list cannot be null");
        }
        if (crls.isEmpty()) {
            throw new IllegalArgumentException("CRL list cannot be empty");
        }

        log.info("=== Batch saving {} CRLs ===", crls.size());

        try {
            // 1. 배치 저장
            List<CertificateRevocationList> savedCrls = new ArrayList<>();
            int successCount = 0;
            int failureCount = 0;

            for (CertificateRevocationList crl : crls) {
                try {
                    log.debug("Saving CRL: issuer={}, country={}",
                        crl.getIssuerName().getValue(), crl.getCountryCode().getValue());

                    // JPA를 통해 저장
                    CertificateRevocationList saved = jpaRepository.save(crl);
                    savedCrls.add(saved);
                    successCount++;

                } catch (Exception e) {
                    log.warn("Failed to save CRL: issuer={}, country={}",
                        crl.getIssuerName().getValue(), crl.getCountryCode().getValue(), e);
                    failureCount++;
                    // Continue with next CRL
                }
            }

            // 2. Domain Events 발행
            for (CertificateRevocationList saved : savedCrls) {
                if (!saved.getDomainEvents().isEmpty()) {
                    log.debug("Publishing {} domain events from CRL: {}",
                        saved.getDomainEvents().size(), saved.getId().getId());
                    saved.getDomainEvents().forEach(event -> {
                        log.debug("Publishing CRL event: {}", event.getClass().getSimpleName());
                        eventPublisher.publishEvent(event);
                    });
                    saved.clearDomainEvents();
                }
            }

            log.info("Batch save completed: total={}, success={}, failure={}",
                crls.size(), successCount, failureCount);

            return savedCrls;

        } catch (Exception e) {
            log.error("Error during batch CRL saving", e);
            throw e;
        }
    }

    /**
     * CRL ID로 조회
     *
     * @param id CRL ID
     * @return CRL (없으면 empty Optional)
     * @throws IllegalArgumentException id가 null인 경우
     */
    @Override
    @Transactional(readOnly = true)
    public Optional<CertificateRevocationList> findById(CrlId id) {
        if (id == null) {
            throw new IllegalArgumentException("CRL ID cannot be null");
        }

        log.debug("Finding CRL by ID: {}", id.getId());
        return jpaRepository.findById(id);
    }

    /**
     * CSCA 발급자명과 국가 코드로 조회
     *
     * @param issuerName CSCA 발급자명
     * @param countryCode 국가 코드
     * @return CRL (없으면 empty Optional)
     * @throws IllegalArgumentException 입력값이 null이거나 비어있는 경우
     */
    @Override
    @Transactional(readOnly = true)
    public Optional<CertificateRevocationList> findByIssuerNameAndCountry(
            String issuerName,
            String countryCode
    ) {
        if (issuerName == null || issuerName.isBlank()) {
            throw new IllegalArgumentException("Issuer name cannot be null or blank");
        }
        if (countryCode == null || countryCode.isBlank()) {
            throw new IllegalArgumentException("Country code cannot be null or blank");
        }

        log.debug("Finding CRL by issuer={}, country={}", issuerName, countryCode);
        Optional<CertificateRevocationList> found =
            jpaRepository.findByIssuerName_ValueAndCountryCode_Value(issuerName, countryCode);

        if (found.isPresent()) {
            log.debug("CRL found: id={}", found.get().getId().getId());
        } else {
            log.debug("CRL not found for issuer={}, country={}", issuerName, countryCode);
        }

        return found;
    }

    /**
     * CSCA 발급자명으로 모든 CRL 조회
     *
     * @param issuerName CSCA 발급자명
     * @return CRL 목록
     * @throws IllegalArgumentException issuerName이 null이거나 비어있는 경우
     */
    @Override
    @Transactional(readOnly = true)
    public List<CertificateRevocationList> findByIssuerName(String issuerName) {
        if (issuerName == null || issuerName.isBlank()) {
            throw new IllegalArgumentException("Issuer name cannot be null or blank");
        }

        log.debug("Finding all CRLs by issuer={}", issuerName);
        List<CertificateRevocationList> crls = jpaRepository.findByIssuerName_Value(issuerName);
        log.debug("Found {} CRLs for issuer={}", crls.size(), issuerName);

        return crls;
    }

    /**
     * 국가 코드로 모든 CRL 조회
     *
     * @param countryCode 국가 코드
     * @return CRL 목록
     * @throws IllegalArgumentException countryCode가 null이거나 비어있는 경우
     */
    @Override
    @Transactional(readOnly = true)
    public List<CertificateRevocationList> findByCountryCode(String countryCode) {
        if (countryCode == null || countryCode.isBlank()) {
            throw new IllegalArgumentException("Country code cannot be null or blank");
        }

        log.debug("Finding all CRLs by country={}", countryCode);
        List<CertificateRevocationList> crls = jpaRepository.findByCountryCode_Value(countryCode);
        log.debug("Found {} CRLs for country={}", crls.size(), countryCode);

        return crls;
    }

    /**
     * 업로드 ID로 CRL 목록 조회
     *
     * <p>특정 업로드 파일에서 추출된 모든 CRL을 조회합니다.</p>
     * <p>Phase 17: ValidateCertificatesUseCase에서 사용됩니다.</p>
     *
     * @param uploadId 원본 업로드 파일 ID (File Upload Context)
     * @return CRL 목록 (빈 리스트 가능)
     * @throws IllegalArgumentException uploadId가 null인 경우
     * @since Phase 17 Task 1.2
     */
    @Override
    @Transactional(readOnly = true)
    public List<CertificateRevocationList> findByUploadId(java.util.UUID uploadId) {
        if (uploadId == null) {
            log.warn("Cannot find CRLs by uploadId: uploadId is null");
            throw new IllegalArgumentException("uploadId must not be null");
        }

        log.debug("Finding CRLs by uploadId: {}", uploadId);
        List<CertificateRevocationList> crls = jpaRepository.findByUploadId(uploadId);
        log.debug("Found {} CRL(s) for uploadId: {}", crls.size(), uploadId);

        return crls;
    }

    /**
     * CRL ID로 존재 여부 확인
     *
     * @param id CRL ID
     * @return 존재하면 true
     * @throws IllegalArgumentException id가 null인 경우
     */
    @Override
    @Transactional(readOnly = true)
    public boolean existsById(CrlId id) {
        if (id == null) {
            throw new IllegalArgumentException("CRL ID cannot be null");
        }

        return jpaRepository.existsById(id);
    }

    /**
     * 발급자와 국가로 존재 여부 확인
     *
     * @param issuerName CSCA 발급자명
     * @param countryCode 국가 코드
     * @return 존재하면 true
     * @throws IllegalArgumentException 입력값이 null이거나 비어있는 경우
     */
    @Override
    @Transactional(readOnly = true)
    public boolean existsByIssuerNameAndCountry(String issuerName, String countryCode) {
        if (issuerName == null || issuerName.isBlank()) {
            throw new IllegalArgumentException("Issuer name cannot be null or blank");
        }
        if (countryCode == null || countryCode.isBlank()) {
            throw new IllegalArgumentException("Country code cannot be null or blank");
        }

        return jpaRepository.existsByIssuerName_ValueAndCountryCode_Value(issuerName, countryCode);
    }

    /**
     * CRL ID로 삭제
     *
     * @param id 삭제할 CRL ID
     * @throws IllegalArgumentException id가 null인 경우
     */
    @Override
    @Transactional
    public void deleteById(CrlId id) {
        if (id == null) {
            throw new IllegalArgumentException("CRL ID cannot be null");
        }

        log.debug("Deleting CRL: id={}", id.getId());
        try {
            jpaRepository.deleteById(id);
            log.info("CRL deleted successfully: id={}", id.getId());
        } catch (Exception e) {
            log.error("Error deleting CRL: id={}", id.getId(), e);
            throw e;
        }
    }

    /**
     * 전체 CRL 개수
     *
     * @return CRL 총 개수
     */
    @Override
    @Transactional(readOnly = true)
    public long count() {
        return jpaRepository.count();
    }

    /**
     * 전체 CRL 조회
     *
     * @return 전체 CRL 목록
     */
    @Override
    @Transactional(readOnly = true)
    public List<CertificateRevocationList> findAll() {
        log.debug("Finding all CRLs");
        List<CertificateRevocationList> crls = jpaRepository.findAll();
        log.debug("Found {} CRLs", crls.size());

        return crls;
    }
}
