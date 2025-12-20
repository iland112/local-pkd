package com.smartcoreinc.localpkd.passiveauthentication.domain.repository;

import com.smartcoreinc.localpkd.passiveauthentication.domain.model.PassportData;
import com.smartcoreinc.localpkd.passiveauthentication.domain.model.PassportDataId;
import com.smartcoreinc.localpkd.passiveauthentication.domain.model.PassiveAuthenticationStatus;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Repository interface for PassportData aggregate.
 *
 * <p>Provides persistence operations for PassportData entities,
 * following the Repository pattern from Domain-Driven Design.
 */
public interface PassportDataRepository {

    /**
     * Save PassportData aggregate.
     *
     * @param passportData PassportData to save
     * @return saved PassportData
     */
    PassportData save(PassportData passportData);

    /**
     * Find PassportData by ID.
     *
     * @param id PassportData ID
     * @return Optional containing PassportData if found
     */
    Optional<PassportData> findById(PassportDataId id);

    /**
     * Find all PassportData entities.
     *
     * @return list of all PassportData
     */
    List<PassportData> findAll();

    /**
     * Find PassportData by verification status.
     *
     * @param status verification status
     * @return list of PassportData with given status
     */
    List<PassportData> findByVerificationStatus(PassiveAuthenticationStatus status);

    /**
     * Find PassportData created within date range.
     *
     * @param startDate start date (inclusive)
     * @param endDate end date (inclusive)
     * @return list of PassportData created in date range
     */
    List<PassportData> findByStartedAtBetween(LocalDateTime startDate, LocalDateTime endDate);

    /**
     * Find completed PassportData.
     *
     * @return list of completed PassportData
     */
    List<PassportData> findCompleted();

    /**
     * Find in-progress PassportData.
     *
     * @return list of in-progress PassportData
     */
    List<PassportData> findInProgress();

    /**
     * Count PassportData by verification status.
     *
     * @param status verification status
     * @return count of PassportData with given status
     */
    long countByVerificationStatus(PassiveAuthenticationStatus status);

    /**
     * Count completed PassportData.
     *
     * @return count of completed PassportData
     */
    long countCompleted();

    /**
     * Count in-progress PassportData.
     *
     * @return count of in-progress PassportData
     */
    long countInProgress();

    /**
     * Delete PassportData by ID.
     *
     * @param id PassportData ID
     */
    void deleteById(PassportDataId id);

    /**
     * Delete PassportData entity.
     *
     * @param passportData PassportData to delete
     */
    void delete(PassportData passportData);

    /**
     * Check if PassportData exists by ID.
     *
     * @param id PassportData ID
     * @return true if exists
     */
    boolean existsById(PassportDataId id);
}
