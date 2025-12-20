package com.smartcoreinc.localpkd.fileparsing.infrastructure.repository;

import com.smartcoreinc.localpkd.certificatevalidation.domain.model.CountryCode;
import com.smartcoreinc.localpkd.fileparsing.domain.model.MasterList;
import com.smartcoreinc.localpkd.fileparsing.domain.model.MasterListId;
import com.smartcoreinc.localpkd.fileparsing.fixture.MasterListTestFixture;
import com.smartcoreinc.localpkd.fileupload.domain.model.UploadId;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;

/**
 * JpaMasterListRepositoryTest - JpaMasterListRepository 통합 테스트
 *
 * <p><b>테스트 전략</b>:</p>
 * <ul>
 *   <li>@DataJpaTest: JPA 관련 컴포넌트만 로드 (빠른 테스트)</li>
 *   <li>TestEntityManager: 테스트용 EntityManager (flush, clear 가능)</li>
 *   <li>실제 데이터베이스 트랜잭션: H2 in-memory DB 사용</li>
 * </ul>
 *
 * @author SmartCore Inc.
 * @version 1.0
 * @since 2025-11-27
 */
@Slf4j
@DataJpaTest
@ActiveProfiles("test")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@EntityScan(basePackages = "com.smartcoreinc.localpkd")
@DisplayName("JpaMasterListRepository Integration Test")
class JpaMasterListRepositoryTest {

    @Autowired
    private TestEntityManager entityManager;

    @Autowired
    private SpringDataMasterListRepository springDataRepository;

    @Autowired
    private ApplicationEventPublisher eventPublisher;

    private JpaMasterListRepository repository;

    @BeforeEach
    void setUp() {
        repository = new JpaMasterListRepository(springDataRepository, eventPublisher);
    }

    @Test
    @DisplayName("Master List 저장 성공")
    void save_ShouldPersistMasterList() {
        // Given
        MasterList masterList = MasterListTestFixture.createKorea();

        // When
        MasterList saved = repository.save(masterList);
        entityManager.flush();
        entityManager.clear();

        // Then
        assertThat(saved).isNotNull();
        assertThat(saved.getId()).isEqualTo(masterList.getId());
        assertThat(saved.getCountryCode().getValue()).isEqualTo("KR");
        assertThat(saved.getCscaCount()).isEqualTo(10);
    }

    @Test
    @DisplayName("ID로 Master List 조회 성공")
    void findById_ShouldReturnMasterList_WhenExists() {
        // Given
        MasterList masterList = MasterListTestFixture.createUsa();
        repository.save(masterList);
        entityManager.flush();
        entityManager.clear();

        // When
        Optional<MasterList> found = repository.findById(masterList.getId());

        // Then
        assertThat(found).isPresent();
        assertThat(found.get().getId()).isEqualTo(masterList.getId());
        assertThat(found.get().getCountryCode().getValue()).isEqualTo("US");
    }

    @Test
    @DisplayName("ID로 Master List 조회 실패 - 존재하지 않음")
    void findById_ShouldReturnEmpty_WhenNotExists() {
        // Given
        MasterListId nonExistentId = MasterListId.newId();

        // When
        Optional<MasterList> found = repository.findById(nonExistentId);

        // Then
        assertThat(found).isEmpty();
    }

    @Test
    @DisplayName("UploadId로 Master List 조회 성공")
    void findByUploadId_ShouldReturnMasterList_WhenExists() {
        // Given
        UploadId uploadId = UploadId.newId();
        MasterList masterList = MasterListTestFixture.createWithUploadId(uploadId);
        repository.save(masterList);
        entityManager.flush();
        entityManager.clear();

        // When
        Optional<MasterList> found = repository.findByUploadId(uploadId);

        // Then
        assertThat(found).isPresent();
        assertThat(found.get().getUploadId()).isEqualTo(uploadId);
    }

    @Test
    @DisplayName("UploadId로 Master List 조회 실패 - 존재하지 않음")
    void findByUploadId_ShouldReturnEmpty_WhenNotExists() {
        // Given
        UploadId nonExistentUploadId = UploadId.newId();

        // When
        Optional<MasterList> found = repository.findByUploadId(nonExistentUploadId);

        // Then
        assertThat(found).isEmpty();
    }

    @Test
    @DisplayName("국가 코드로 Master List 조회 성공 - 최신순 정렬")
    void findByCountryCodeOrderByCreatedAtDesc_ShouldReturnSortedList() throws InterruptedException {
        // Given
        CountryCode korea = CountryCode.of("KR");
        List<MasterList> masterLists = MasterListTestFixture.createMultipleVersions(korea, 3);

        masterLists.forEach(ml -> {
            repository.save(ml);
            entityManager.flush();
            try {
                Thread.sleep(10); // Ensure different timestamps
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });
        entityManager.clear();

        // When
        List<MasterList> found = repository.findByCountryCodeOrderByCreatedAtDesc(korea);

        // Then
        assertThat(found).hasSize(3);
        assertThat(found).allMatch(ml -> ml.getCountryCode().getValue().equals("KR"));

        // Verify descending order by createdAt
        for (int i = 0; i < found.size() - 1; i++) {
            assertThat(found.get(i).getCreatedAt())
                .isAfterOrEqualTo(found.get(i + 1).getCreatedAt());
        }
    }

    @Test
    @DisplayName("국가 코드로 Master List 조회 - 결과 없음")
    void findByCountryCodeOrderByCreatedAtDesc_ShouldReturnEmptyList_WhenNoMatches() {
        // Given
        CountryCode korea = CountryCode.of("KR");
        repository.save(MasterListTestFixture.createUsa()); // Different country
        entityManager.flush();
        entityManager.clear();

        // When
        List<MasterList> found = repository.findByCountryCodeOrderByCreatedAtDesc(korea);

        // Then
        assertThat(found).isEmpty();
    }

    @Test
    @DisplayName("모든 Master List 조회 - 최신순 정렬")
    void findAllOrderByCreatedAtDesc_ShouldReturnAllMasterListsSorted() throws InterruptedException {
        // Given
        List<MasterList> masterLists = MasterListTestFixture.createMultiple(5);
        masterLists.forEach(ml -> {
            repository.save(ml);
            entityManager.flush();
            try {
                Thread.sleep(10);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });
        entityManager.clear();

        // When
        List<MasterList> found = repository.findAllOrderByCreatedAtDesc();

        // Then
        assertThat(found).hasSize(5);

        // Verify descending order
        for (int i = 0; i < found.size() - 1; i++) {
            assertThat(found.get(i).getCreatedAt())
                .isAfterOrEqualTo(found.get(i + 1).getCreatedAt());
        }
    }

    @Test
    @DisplayName("Master List 삭제 성공")
    void deleteById_ShouldRemoveMasterList() {
        // Given
        MasterList masterList = MasterListTestFixture.createJapan();
        repository.save(masterList);
        entityManager.flush();
        entityManager.clear();

        // When
        repository.deleteById(masterList.getId());
        entityManager.flush();

        // Then
        Optional<MasterList> found = repository.findById(masterList.getId());
        assertThat(found).isEmpty();
    }

    @Test
    @DisplayName("ID 존재 여부 확인 - 존재함")
    void existsById_ShouldReturnTrue_WhenExists() {
        // Given
        MasterList masterList = MasterListTestFixture.createKorea();
        repository.save(masterList);
        entityManager.flush();

        // When
        boolean exists = repository.existsById(masterList.getId());

        // Then
        assertThat(exists).isTrue();
    }

    @Test
    @DisplayName("ID 존재 여부 확인 - 존재하지 않음")
    void existsById_ShouldReturnFalse_WhenNotExists() {
        // Given
        MasterListId nonExistentId = MasterListId.newId();

        // When
        boolean exists = repository.existsById(nonExistentId);

        // Then
        assertThat(exists).isFalse();
    }

    @Test
    @DisplayName("UploadId 존재 여부 확인 - 존재함")
    void existsByUploadId_ShouldReturnTrue_WhenExists() {
        // Given
        UploadId uploadId = UploadId.newId();
        MasterList masterList = MasterListTestFixture.createWithUploadId(uploadId);
        repository.save(masterList);
        entityManager.flush();

        // When
        boolean exists = repository.existsByUploadId(uploadId);

        // Then
        assertThat(exists).isTrue();
    }

    @Test
    @DisplayName("UploadId 존재 여부 확인 - 존재하지 않음")
    void existsByUploadId_ShouldReturnFalse_WhenNotExists() {
        // Given
        UploadId nonExistentUploadId = UploadId.newId();

        // When
        boolean exists = repository.existsByUploadId(nonExistentUploadId);

        // Then
        assertThat(exists).isFalse();
    }

    @Test
    @DisplayName("Master List 전체 개수 조회")
    void count_ShouldReturnTotalCount() {
        // Given
        List<MasterList> masterLists = MasterListTestFixture.createMultiple(7);
        masterLists.forEach(ml -> {
            repository.save(ml);
            entityManager.flush();
        });

        // When
        long count = repository.count();

        // Then
        assertThat(count).isEqualTo(7);
    }

    @Test
    @DisplayName("국가별 Master List 개수 조회")
    void countByCountryCode_ShouldReturnCountryCount() {
        // Given
        CountryCode korea = CountryCode.of("KR");
        List<MasterList> koreanLists = MasterListTestFixture.createMultipleVersions(korea, 3);
        koreanLists.forEach(ml -> {
            repository.save(ml);
            entityManager.flush();
        });

        repository.save(MasterListTestFixture.createUsa()); // Different country
        entityManager.flush();

        // When
        long count = repository.countByCountryCode(korea);

        // Then
        assertThat(count).isEqualTo(3);
    }

    @Test
    @DisplayName("Master List 저장 시 Domain Event 발행")
    void save_ShouldPublishDomainEvents() {
        // Given
        MasterList masterList = MasterListTestFixture.createKorea();

        // When
        MasterList saved = repository.save(masterList);
        entityManager.flush();

        // Then
        assertThat(saved).isNotNull();
        // Note: Domain events are transient and cleared after publishing
        assertThat(saved.getDomainEvents()).isEmpty();
    }
}
