package com.smartcoreinc.localpkd.passiveauthentication.infrastructure.repository;

import com.smartcoreinc.localpkd.passiveauthentication.domain.model.PassportData;
import com.smartcoreinc.localpkd.passiveauthentication.domain.model.PassportDataId;
import com.smartcoreinc.localpkd.passiveauthentication.domain.model.PassiveAuthenticationStatus;
import com.smartcoreinc.localpkd.passiveauthentication.domain.repository.PassportDataRepository;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

/**
 * JPA implementation of PassportDataRepository.
 *
 * <p>Provides persistence operations for PassportData aggregate root
 * using Spring Data JPA.
 *
 * <p><b>Infrastructure Layer Repository</b>:</p>
 * <ul>
 *   <li>Extends both JpaRepository (Spring Data) and PassportDataRepository (Domain)</li>
 *   <li>Provides CRUD operations via JpaRepository</li>
 *   <li>Implements custom queries via @Query annotations</li>
 *   <li>Leverages JPearl's EntityId type safety</li>
 * </ul>
 *
 * <p><b>Query Methods</b>:</p>
 * <ul>
 *   <li>findByStatus - Find by verification status</li>
 *   <li>findCompleted - Find all completed verifications</li>
 *   <li>findInProgress - Find all in-progress verifications</li>
 *   <li>countByStatus - Count by status</li>
 *   <li>countInProgress - Count in-progress verifications</li>
 *   <li>findByStartedAtBetween - Find by date range</li>
 * </ul>
 */
@Repository
public interface JpaPassportDataRepository
    extends JpaRepository<PassportData, PassportDataId>, PassportDataRepository {

    /**
     * Find PassportData by verification status.
     *
     * @param status verification status
     * @return list of PassportData with the given status
     */
    @Query("SELECT p FROM PassportData p WHERE p.verificationStatus = :status")
    List<PassportData> findByVerificationStatus(@Param("status") PassiveAuthenticationStatus status);

    /**
     * Find all completed PassportData (completedAt is not null).
     *
     * @return list of completed PassportData
     */
    @Query("SELECT p FROM PassportData p WHERE p.completedAt IS NOT NULL")
    List<PassportData> findCompleted();

    /**
     * Find all in-progress PassportData (startedAt is not null, completedAt is null).
     *
     * @return list of in-progress PassportData
     */
    @Query("SELECT p FROM PassportData p WHERE p.startedAt IS NOT NULL AND p.completedAt IS NULL")
    List<PassportData> findInProgress();

    /**
     * Count PassportData by verification status.
     *
     * @param status verification status
     * @return count of PassportData with the given status
     */
    @Query("SELECT COUNT(p) FROM PassportData p WHERE p.verificationStatus = :status")
    long countByVerificationStatus(@Param("status") PassiveAuthenticationStatus status);

    /**
     * Count completed PassportData (completedAt is not null).
     *
     * @return count of completed PassportData
     */
    @Query("SELECT COUNT(p) FROM PassportData p WHERE p.completedAt IS NOT NULL")
    long countCompleted();

    /**
     * Count in-progress PassportData.
     *
     * @return count of in-progress PassportData
     */
    @Query("SELECT COUNT(p) FROM PassportData p WHERE p.startedAt IS NOT NULL AND p.completedAt IS NULL")
    long countInProgress();

    /**
     * Find PassportData by start time range.
     *
     * @param startTime start of the time range
     * @param endTime end of the time range
     * @return list of PassportData started within the given range
     */
    @Query("SELECT p FROM PassportData p WHERE p.startedAt BETWEEN :startTime AND :endTime")
    List<PassportData> findByStartedAtBetween(
        @Param("startTime") LocalDateTime startTime,
        @Param("endTime") LocalDateTime endTime
    );

    // TODO: Add dscFingerprintSha256 field to PassportData entity
    // /**
    //  * Find PassportData by DSC certificate fingerprint.
    //  *
    //  * <p>DSC (Document Signer Certificate) fingerprint is used to link
    //  * PassportData to the certificate used for SOD signature verification.
    //  *
    //  * @param fingerprintSha256 SHA-256 fingerprint of DSC certificate
    //  * @return Optional PassportData
    //  */
    // @Query("SELECT p FROM PassportData p WHERE p.dscFingerprintSha256 = :fingerprintSha256")
    // Optional<PassportData> findByDscFingerprint(@Param("fingerprintSha256") String fingerprintSha256);
}
