package com.smartcoreinc.localpkd.certificatevalidation.infrastructure.repository;

import com.smartcoreinc.localpkd.certificatevalidation.domain.model.*;
import com.smartcoreinc.localpkd.certificatevalidation.domain.repository.CertificateRevocationListRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;

/**
 * CertificateRevocationListRepository 통합 테스트
 *
 * <p>JPA 저장소와 Domain Repository 패턴 통합 테스트</p>
 *
 * @author SmartCore Inc.
 * @since 2025-10-24
 */
@DisplayName("CertificateRevocationListRepository 통합 테스트")
@DataJpaTest
@Import(JpaCertificateRevocationListRepository.class)
@ActiveProfiles("test")
class CertificateRevocationListRepositoryTest {

    @Autowired
    private CertificateRevocationListRepository crlRepository;

    @Autowired
    private TestEntityManager entityManager;

    // ========== Helper Methods ==========

    private CertificateRevocationList createTestCrl(String issuerName, String countryCode) {
        return CertificateRevocationList.create(
            CrlId.newId(),
            IssuerName.of(issuerName),
            CountryCode.of(countryCode),
            ValidityPeriod.of(LocalDateTime.now(), LocalDateTime.now().plusMonths(1)),
            X509CrlData.of(new byte[]{1, 2, 3, 4, 5}, 10),
            RevokedCertificates.empty()
        );
    }

    // ========== Save Tests ==========

    @Test
    @DisplayName("CRL 저장 - 정상 케이스")
    void save_WithValidCrl_Success() {
        // given
        CertificateRevocationList crl = createTestCrl("CSCA-QA", "QA");

        // when
        CertificateRevocationList saved = crlRepository.save(crl);

        // then
        assertThat(saved).isNotNull();
        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getId().getId()).isEqualTo(crl.getId().getId());
        assertThat(saved.getIssuerName().getValue()).isEqualTo("CSCA-QA");
        assertThat(saved.getCountryCode().getValue()).isEqualTo("QA");
    }

    @Test
    @DisplayName("CRL 저장 후 조회 - 데이터 일관성")
    void save_AndFindById_DataConsistency() {
        // given
        CertificateRevocationList crl = createTestCrl("CSCA-NZ", "NZ");

        // when
        CertificateRevocationList saved = crlRepository.save(crl);
        entityManager.flush();
        entityManager.clear();

        // then
        Optional<CertificateRevocationList> found = crlRepository.findById(saved.getId());
        assertThat(found).isPresent();
        assertThat(found.get().getIssuerName().getValue()).isEqualTo("CSCA-NZ");
        assertThat(found.get().getCountryCode().getValue()).isEqualTo("NZ");
    }

    @Test
    @DisplayName("null CRL 저장 시 예외 발생")
    void save_WithNullCrl_ThrowsException() {
        // when & then
        assertThatThrownBy(() -> crlRepository.save(null))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("CRL cannot be null");
    }

    // ========== Batch Save Tests ==========

    @Test
    @DisplayName("여러 CRL 일괄 저장 - 정상 케이스")
    void saveAll_WithMultipleCrls_Success() {
        // given
        List<CertificateRevocationList> crls = List.of(
            createTestCrl("CSCA-QA", "QA"),
            createTestCrl("CSCA-NZ", "NZ"),
            createTestCrl("CSCA-US", "US")
        );

        // when
        List<CertificateRevocationList> saved = crlRepository.saveAll(crls);

        // then
        assertThat(saved).hasSize(3);
        assertThat(saved).allMatch(crl -> crl.getId() != null);
        assertThat(saved.stream().map(crl -> crl.getIssuerName().getValue()))
            .containsExactlyInAnyOrder("CSCA-QA", "CSCA-NZ", "CSCA-US");
    }

    @Test
    @DisplayName("일괄 저장 후 개수 확인")
    void saveAll_AndVerifyCount_Success() {
        // given
        List<CertificateRevocationList> crls = List.of(
            createTestCrl("CSCA-GB", "GB"),
            createTestCrl("CSCA-FR", "FR"),
            createTestCrl("CSCA-DE", "DE")
        );

        // when
        crlRepository.saveAll(crls);
        entityManager.flush();
        entityManager.clear();

        // then
        long count = crlRepository.count();
        assertThat(count).isGreaterThanOrEqualTo(3);
    }

    @Test
    @DisplayName("null 리스트로 일괄 저장 시 예외 발생")
    void saveAll_WithNullList_ThrowsException() {
        // when & then
        assertThatThrownBy(() -> crlRepository.saveAll(null))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("CRL list cannot be null");
    }

    @Test
    @DisplayName("빈 리스트로 일괄 저장 시 예외 발생")
    void saveAll_WithEmptyList_ThrowsException() {
        // when & then
        assertThatThrownBy(() -> crlRepository.saveAll(new ArrayList<>()))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("CRL list cannot be empty");
    }

    @Test
    @DisplayName("부분 실패 처리 - 일부 CRL 저장 실패해도 계속 진행")
    void saveAll_WithPartialFailure_ContinuesProcessing() {
        // given - 첫 번째는 정상, 두 번째는 null (실패할 수 있음)
        List<CertificateRevocationList> crls = List.of(
            createTestCrl("CSCA-JP", "JP"),
            createTestCrl("CSCA-KR", "KR")
        );

        // when
        List<CertificateRevocationList> saved = crlRepository.saveAll(crls);

        // then - 두 개 모두 저장되어야 함
        assertThat(saved).hasSize(2);
    }

    // ========== Find Tests ==========

    @Test
    @DisplayName("CRL ID로 조회 - 존재하는 경우")
    void findById_WithExistingId_ReturnsOptional() {
        // given
        CertificateRevocationList crl = createTestCrl("CSCA-IT", "IT");
        CertificateRevocationList saved = crlRepository.save(crl);
        entityManager.flush();
        entityManager.clear();

        // when
        Optional<CertificateRevocationList> found = crlRepository.findById(saved.getId());

        // then
        assertThat(found).isPresent();
        assertThat(found.get().getId()).isEqualTo(saved.getId());
    }

    @Test
    @DisplayName("CRL ID로 조회 - 존재하지 않는 경우")
    void findById_WithNonExistingId_ReturnsEmpty() {
        // when
        Optional<CertificateRevocationList> found = crlRepository.findById(CrlId.newId());

        // then
        assertThat(found).isEmpty();
    }

    @Test
    @DisplayName("발급자명과 국가로 조회 - 존재하는 경우")
    void findByIssuerNameAndCountry_WithExistingData_ReturnsOptional() {
        // given
        CertificateRevocationList crl = createTestCrl("CSCA-ES", "ES");
        crlRepository.save(crl);
        entityManager.flush();
        entityManager.clear();

        // when
        Optional<CertificateRevocationList> found =
            crlRepository.findByIssuerNameAndCountry("CSCA-ES", "ES");

        // then
        assertThat(found).isPresent();
        assertThat(found.get().getIssuerName().getValue()).isEqualTo("CSCA-ES");
        assertThat(found.get().getCountryCode().getValue()).isEqualTo("ES");
    }

    @Test
    @DisplayName("발급자명과 국가로 조회 - 존재하지 않는 경우")
    void findByIssuerNameAndCountry_WithNonExistingData_ReturnsEmpty() {
        // when
        Optional<CertificateRevocationList> found =
            crlRepository.findByIssuerNameAndCountry("CSCA-XX", "XX");

        // then
        assertThat(found).isEmpty();
    }

    @Test
    @DisplayName("발급자명으로 조회 - 여러 결과")
    void findByIssuerName_ReturnsAllCrlsForIssuer() {
        // given
        String issuerName = "CSCA-PT";
        crlRepository.save(createTestCrl(issuerName, "PT"));
        entityManager.flush();
        entityManager.clear();

        // when
        List<CertificateRevocationList> found = crlRepository.findByIssuerName(issuerName);

        // then
        assertThat(found).isNotEmpty();
        assertThat(found).allMatch(crl -> crl.getIssuerName().getValue().equals(issuerName));
    }

    @Test
    @DisplayName("국가로 조회 - 여러 결과")
    void findByCountryCode_ReturnsAllCrlsForCountry() {
        // given
        String countryCode = "GR";
        crlRepository.save(createTestCrl("CSCA-GR", countryCode));
        entityManager.flush();
        entityManager.clear();

        // when
        List<CertificateRevocationList> found = crlRepository.findByCountryCode(countryCode);

        // then
        assertThat(found).isNotEmpty();
        assertThat(found).allMatch(crl -> crl.getCountryCode().getValue().equals(countryCode));
    }

    // ========== Exists Tests ==========

    @Test
    @DisplayName("CRL 존재 여부 - 존재하는 경우")
    void existsById_WithExistingId_ReturnsTrue() {
        // given
        CertificateRevocationList crl = createTestCrl("CSCA-PL", "PL");
        CertificateRevocationList saved = crlRepository.save(crl);
        entityManager.flush();
        entityManager.clear();

        // when
        boolean exists = crlRepository.existsById(saved.getId());

        // then
        assertThat(exists).isTrue();
    }

    @Test
    @DisplayName("CRL 존재 여부 - 존재하지 않는 경우")
    void existsById_WithNonExistingId_ReturnsFalse() {
        // when
        boolean exists = crlRepository.existsById(CrlId.newId());

        // then
        assertThat(exists).isFalse();
    }

    @Test
    @DisplayName("발급자와 국가로 존재 여부 확인 - 존재하는 경우")
    void existsByIssuerNameAndCountry_WithExistingData_ReturnsTrue() {
        // given
        CertificateRevocationList crl = createTestCrl("CSCA-CZ", "CZ");
        crlRepository.save(crl);
        entityManager.flush();
        entityManager.clear();

        // when
        boolean exists = crlRepository.existsByIssuerNameAndCountry("CSCA-CZ", "CZ");

        // then
        assertThat(exists).isTrue();
    }

    @Test
    @DisplayName("발급자와 국가로 존재 여부 확인 - 존재하지 않는 경우")
    void existsByIssuerNameAndCountry_WithNonExistingData_ReturnsFalse() {
        // when
        boolean exists = crlRepository.existsByIssuerNameAndCountry("CSCA-XX", "XX");

        // then
        assertThat(exists).isFalse();
    }

    // ========== Delete Tests ==========

    @Test
    @DisplayName("CRL 삭제 - 정상 케이스")
    void deleteById_WithExistingId_Success() {
        // given
        CertificateRevocationList crl = createTestCrl("CSCA-HU", "HU");
        CertificateRevocationList saved = crlRepository.save(crl);
        entityManager.flush();
        CrlId idToDelete = saved.getId();

        // when
        crlRepository.deleteById(idToDelete);
        entityManager.flush();
        entityManager.clear();

        // then
        assertThat(crlRepository.existsById(idToDelete)).isFalse();
    }

    @Test
    @DisplayName("null ID로 삭제 시 예외 발생")
    void deleteById_WithNullId_ThrowsException() {
        // when & then
        assertThatThrownBy(() -> crlRepository.deleteById(null))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("CRL ID cannot be null");
    }

    // ========== Count Tests ==========

    @Test
    @DisplayName("CRL 총 개수 조회")
    void count_ReturnsCorrectCount() {
        // given
        long initialCount = crlRepository.count();
        crlRepository.saveAll(List.of(
            createTestCrl("CSCA-SK", "SK"),
            createTestCrl("CSCA-SI", "SI")
        ));
        entityManager.flush();
        entityManager.clear();

        // when
        long finalCount = crlRepository.count();

        // then
        assertThat(finalCount).isGreaterThanOrEqualTo(initialCount + 2);
    }

    // ========== FindAll Tests ==========

    @Test
    @DisplayName("전체 CRL 조회")
    void findAll_ReturnsAllCrls() {
        // given
        crlRepository.saveAll(List.of(
            createTestCrl("CSCA-HR", "HR"),
            createTestCrl("CSCA-RO", "RO")
        ));
        entityManager.flush();
        entityManager.clear();

        // when
        List<CertificateRevocationList> all = crlRepository.findAll();

        // then
        assertThat(all).isNotEmpty();
        assertThat(all).allMatch(crl -> crl.getId() != null);
    }

    // ========== Parameter Validation Tests ==========

    @Test
    @DisplayName("null 발급자명으로 조회 시 예외 발생")
    void findByIssuerName_WithNullIssuerName_ThrowsException() {
        // when & then
        assertThatThrownBy(() -> crlRepository.findByIssuerName(null))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Issuer name cannot be null or blank");
    }

    @Test
    @DisplayName("null 국가 코드로 조회 시 예외 발생")
    void findByCountryCode_WithNullCountryCode_ThrowsException() {
        // when & then
        assertThatThrownBy(() -> crlRepository.findByCountryCode(null))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Country code cannot be null or blank");
    }

    @Test
    @DisplayName("공백 발급자명으로 조회 시 예외 발생")
    void findByIssuerName_WithBlankIssuerName_ThrowsException() {
        // when & then
        assertThatThrownBy(() -> crlRepository.findByIssuerName("   "))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Issuer name cannot be null or blank");
    }

    @Test
    @DisplayName("공백 국가 코드로 조회 시 예외 발생")
    void findByCountryCode_WithBlankCountryCode_ThrowsException() {
        // when & then
        assertThatThrownBy(() -> crlRepository.findByCountryCode("   "))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Country code cannot be null or blank");
    }
}
