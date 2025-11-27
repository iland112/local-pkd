package com.smartcoreinc.localpkd.certificatevalidation.infrastructure.repository;

import com.smartcoreinc.localpkd.certificatevalidation.domain.model.CertificateSourceType;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.UUID;

import static org.assertj.core.api.Assertions.*;

/**
 * SpringDataCertificateRepositoryQueryMethodsTest - 새 쿼리 메서드 검증
 *
 * <p><b>목적</b>: Phase 2에서 추가된 쿼리 메서드가 정상적으로 컴파일되고 실행되는지 검증</p>
 *
 * @author SmartCore Inc.
 * @version 1.0
 * @since 2025-11-27
 */
@Slf4j
@DataJpaTest
@ActiveProfiles("test")
@DisplayName("SpringDataCertificateRepository Query Methods Compilation Test")
class SpringDataCertificateRepositoryQueryMethodsTest {

    @Autowired
    private SpringDataCertificateRepository repository;

    @Test
    @DisplayName("findBySourceType 메서드가 정상적으로 실행됨")
    void findBySourceType_ShouldExecuteWithoutError() {
        // When & Then - Should not throw exception
        assertThatCode(() ->
            repository.findBySourceType(CertificateSourceType.MASTER_LIST)
        ).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("findByMasterListId 메서드가 정상적으로 실행됨")
    void findByMasterListId_ShouldExecuteWithoutError() {
        // Given
        UUID testId = UUID.randomUUID();

        // When & Then - Should not throw exception
        assertThatCode(() ->
            repository.findByMasterListId(testId)
        ).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("findMasterListCertificates 메서드가 정상적으로 실행됨")
    void findMasterListCertificates_ShouldExecuteWithoutError() {
        // When & Then - Should not throw exception
        assertThatCode(() ->
            repository.findMasterListCertificates()
        ).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("findLdifCertificates 메서드가 정상적으로 실행됨")
    void findLdifCertificates_ShouldExecuteWithoutError() {
        // When & Then - Should not throw exception
        assertThatCode(() ->
            repository.findLdifCertificates()
        ).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("모든 새 쿼리 메서드가 빈 결과를 반환함 (데이터 없음)")
    void allNewQueryMethods_ShouldReturnEmptyResults_WhenNoData() {
        // When & Then
        assertThat(repository.findBySourceType(CertificateSourceType.MASTER_LIST)).isEmpty();
        assertThat(repository.findByMasterListId(UUID.randomUUID())).isEmpty();
        assertThat(repository.findMasterListCertificates()).isEmpty();
        assertThat(repository.findLdifCertificates()).isEmpty();
    }
}
