package com.smartcoreinc.localpkd.passiveauthentication.infrastructure.cache;

import com.smartcoreinc.localpkd.passiveauthentication.domain.port.CrlLdapPort;
import com.smartcoreinc.localpkd.certificatevalidation.domain.model.*;
import com.smartcoreinc.localpkd.certificatevalidation.domain.repository.CertificateRevocationListRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.ByteArrayInputStream;
import java.security.cert.CertificateFactory;
import java.security.cert.X509CRL;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Date;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * CrlCacheService - Two-tier CRL Caching Strategy
 *
 * <p>CRL 조회 성능을 향상시키기 위한 2단계 캐시 전략을 구현합니다.</p>
 *
 * <h3>Two-Tier Caching Architecture</h3>
 * <pre>
 * Tier 1: In-Memory Cache (ConcurrentHashMap)
 * ├─ Key: "country:cscaDn" (예: "KR:CN=CSCA-KOREA,O=Government,C=KR")
 * ├─ Value: CachedCrl (X509CRL + expiry time)
 * └─ Eviction: nextUpdate 시간 기준 자동 만료
 *
 * Tier 2: Database Cache (certificate_revocation_list table)
 * ├─ Persistent storage for CRLs
 * ├─ Fallback when memory cache misses
 * └─ Periodic cleanup of expired CRLs
 * </pre>
 *
 * <h3>Cache Lookup Flow</h3>
 * <pre>
 * 1. Check in-memory cache
 *    ├─ Hit & Fresh → Return cached CRL
 *    └─ Miss or Expired → Go to step 2
 * 2. Check database cache
 *    ├─ Hit & Fresh → Load to memory, return CRL
 *    └─ Miss or Expired → Go to step 3
 * 3. Fetch from LDAP (CrlLdapPort)
 *    ├─ Success → Save to DB & memory, return CRL
 *    └─ Failure → Return empty
 * </pre>
 *
 * <h3>Cache Expiration Strategy</h3>
 * <ul>
 *   <li>Memory cache expires based on CRL's nextUpdate field</li>
 *   <li>Database cache expires when nextUpdate < current time</li>
 *   <li>Expired entries are automatically evicted on next access</li>
 * </ul>
 *
 * @see com.smartcoreinc.localpkd.passiveauthentication.domain.port.CrlLdapPort
 * @see com.smartcoreinc.localpkd.certificatevalidation.domain.model.CertificateRevocationList
 * @since Phase 4.12
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CrlCacheService {

    private final CrlLdapPort crlLdapPort;
    private final CertificateRevocationListRepository crlRepository;

    /**
     * In-memory cache (Tier 1)
     * Key: "{countryCode}:{cscaSubjectDn}"
     * Value: CachedCrl (X509CRL + expiry timestamp)
     */
    private final Map<String, CachedCrl> memoryCache = new ConcurrentHashMap<>();

    /**
     * CRL을 2단계 캐시 전략으로 조회합니다.
     *
     * <p>조회 순서:</p>
     * <ol>
     *   <li>In-memory cache (빠름, 휘발성)</li>
     *   <li>Database cache (중간, 영구 저장)</li>
     *   <li>LDAP directory (느림, 원본)</li>
     * </ol>
     *
     * @param cscaSubjectDn CSCA Subject DN (예: "CN=CSCA-KOREA,O=Government,C=KR")
     * @param countryCode ISO 3166-1 alpha-2 country code (예: "KR")
     * @return X509CRL if found, empty otherwise
     */
    public Optional<X509CRL> getCrl(String cscaSubjectDn, String countryCode) {
        String cacheKey = buildCacheKey(countryCode, cscaSubjectDn);
        log.debug("Looking up CRL with cache key: {}", cacheKey);

        // Tier 1: In-Memory Cache Lookup
        Optional<X509CRL> memoryCached = getFromMemoryCache(cacheKey);
        if (memoryCached.isPresent()) {
            log.debug("CRL cache HIT (memory): {}", cacheKey);
            return memoryCached;
        }

        // Tier 2: Database Cache Lookup
        Optional<X509CRL> dbCached = getFromDatabaseCache(cscaSubjectDn, countryCode);
        if (dbCached.isPresent()) {
            log.debug("CRL cache HIT (database): {}", cacheKey);
            // Load to memory cache for faster future access
            putToMemoryCache(cacheKey, dbCached.get());
            return dbCached;
        }

        // Tier 3: LDAP Lookup (Primary Source)
        log.debug("CRL cache MISS: fetching from LDAP for {}", cacheKey);

        try {
            Optional<X509CRL> ldapCrl = crlLdapPort.findCrlByCsca(cscaSubjectDn, countryCode);

            if (ldapCrl.isPresent()) {
                // Save to both caches
                saveToCache(ldapCrl.get(), cscaSubjectDn, countryCode);
                log.info("CRL fetched from LDAP and cached: {}", cacheKey);
            } else {
                log.warn("CRL not found in LDAP: {}", cacheKey);
            }

            return ldapCrl;

        } catch (Exception e) {
            // LDAP lookup failed (e.g., base DN doesn't exist, connection error)
            log.warn("CRL LDAP lookup failed for {}: {}", cacheKey, e.getMessage());
            return Optional.empty();
        }
    }

    /**
     * In-memory cache에서 CRL을 조회합니다.
     *
     * @param cacheKey Cache key
     * @return X509CRL if found and not expired, empty otherwise
     */
    private Optional<X509CRL> getFromMemoryCache(String cacheKey) {
        CachedCrl cached = memoryCache.get(cacheKey);

        if (cached == null) {
            return Optional.empty();
        }

        // Check expiry
        if (cached.isExpired()) {
            log.debug("Memory cache entry expired: {}", cacheKey);
            memoryCache.remove(cacheKey);  // Evict expired entry
            return Optional.empty();
        }

        return Optional.of(cached.crl);
    }

    /**
     * Database cache에서 CRL을 조회합니다.
     *
     * @param cscaSubjectDn CSCA Subject DN
     * @param countryCode Country code
     * @return X509CRL if found and not expired, empty otherwise
     */
    private Optional<X509CRL> getFromDatabaseCache(String cscaSubjectDn, String countryCode) {
        Optional<CertificateRevocationList> crlEntity =
            crlRepository.findByIssuerNameAndCountry(cscaSubjectDn, countryCode);

        if (crlEntity.isEmpty()) {
            return Optional.empty();
        }

        CertificateRevocationList crl = crlEntity.get();

        // Check expiry
        if (crl.isExpired()) {
            log.debug("Database cache entry expired: issuer={}, country={}",
                cscaSubjectDn, countryCode);
            // Note: Expired CRLs are not automatically deleted here.
            // Consider periodic cleanup job for old CRLs.
            return Optional.empty();
        }

        // Parse X509CRL from stored binary
        try {
            byte[] crlBinary = crl.getCrlBinary();
            CertificateFactory certFactory = CertificateFactory.getInstance("X.509");
            X509CRL x509Crl = (X509CRL) certFactory.generateCRL(new ByteArrayInputStream(crlBinary));
            return Optional.of(x509Crl);
        } catch (Exception e) {
            log.error("Failed to parse X509CRL from database cache: issuer={}, country={}",
                cscaSubjectDn, countryCode, e);
            return Optional.empty();
        }
    }

    /**
     * CRL을 in-memory cache에 저장합니다.
     *
     * @param cacheKey Cache key
     * @param crl X509CRL
     */
    private void putToMemoryCache(String cacheKey, X509CRL crl) {
        Date nextUpdate = crl.getNextUpdate();
        Long expiryTimestamp = (nextUpdate != null)
            ? nextUpdate.getTime()
            : System.currentTimeMillis() + (24 * 60 * 60 * 1000L); // Default: 24 hours

        CachedCrl cachedCrl = new CachedCrl(crl, expiryTimestamp);
        memoryCache.put(cacheKey, cachedCrl);

        log.debug("CRL cached in memory: {} (expiry: {})", cacheKey, new Date(expiryTimestamp));
    }

    /**
     * CRL을 두 단계 캐시 (memory + database)에 저장합니다.
     *
     * @param crl X509CRL
     * @param cscaSubjectDn CSCA Subject DN
     * @param countryCode Country code
     */
    private void saveToCache(X509CRL crl, String cscaSubjectDn, String countryCode) {
        String cacheKey = buildCacheKey(countryCode, cscaSubjectDn);

        // Save to memory cache
        putToMemoryCache(cacheKey, crl);

        // Save to database cache
        try {
            // Check if CRL already exists in database
            Optional<CertificateRevocationList> existing =
                crlRepository.findByIssuerNameAndCountry(cscaSubjectDn, countryCode);

            if (existing.isPresent()) {
                log.debug("CRL already exists in database cache: {}", cacheKey);
                return;  // Skip duplicate save
            }

            // Create Value Objects
            LocalDateTime thisUpdate = convertToLocalDateTime(crl.getThisUpdate());
            LocalDateTime nextUpdate = (crl.getNextUpdate() != null)
                ? convertToLocalDateTime(crl.getNextUpdate())
                : null;

            CrlId crlId = CrlId.newId();
            IssuerName issuerName = IssuerName.of(cscaSubjectDn);
            CountryCode country = CountryCode.of(countryCode);
            ValidityPeriod validityPeriod = ValidityPeriod.of(thisUpdate, nextUpdate);
            X509CrlData crlData = X509CrlData.of(crl.getEncoded(), 0); // 0 = revoked count not tracked in cache
            RevokedCertificates revokedCerts = RevokedCertificates.empty(); // Empty for cache

            // Create CRL entity
            CertificateRevocationList crlEntity = CertificateRevocationList.create(
                java.util.UUID.randomUUID(),  // uploadId - use random for cached CRLs
                crlId,
                issuerName,
                country,
                validityPeriod,
                crlData,
                revokedCerts
            );

            crlRepository.save(crlEntity);
            log.debug("CRL saved to database cache: {}", cacheKey);

        } catch (Exception e) {
            log.error("Failed to save CRL to database cache: {}", cacheKey, e);
            // Not critical - memory cache is still available
        }
    }

    /**
     * Cache key 생성
     *
     * @param countryCode Country code
     * @param cscaSubjectDn CSCA Subject DN
     * @return Cache key (예: "KR:CN=CSCA-KOREA,O=Government,C=KR")
     */
    private String buildCacheKey(String countryCode, String cscaSubjectDn) {
        return countryCode + ":" + cscaSubjectDn;
    }

    /**
     * Date를 LocalDateTime으로 변환
     */
    private LocalDateTime convertToLocalDateTime(Date date) {
        if (date == null) {
            return null;
        }
        return date.toInstant()
            .atZone(ZoneId.systemDefault())
            .toLocalDateTime();
    }

    /**
     * In-memory cache entry
     *
     * @param crl X509CRL
     * @param expiryTimestamp Expiry timestamp (milliseconds since epoch)
     */
    private record CachedCrl(X509CRL crl, Long expiryTimestamp) {

        /**
         * Check if this cache entry has expired
         *
         * @return true if expired
         */
        public boolean isExpired() {
            return System.currentTimeMillis() > expiryTimestamp;
        }
    }

    // ========== Cache Management Methods ==========

    /**
     * In-memory cache를 완전히 비웁니다 (테스트용).
     */
    public void clearMemoryCache() {
        memoryCache.clear();
        log.info("Memory cache cleared");
    }

    /**
     * In-memory cache 크기 조회 (모니터링용).
     *
     * @return Number of cached CRLs in memory
     */
    public int getMemoryCacheSize() {
        return memoryCache.size();
    }

    /**
     * 만료된 in-memory cache entries를 제거합니다.
     *
     * @return Number of evicted entries
     */
    public int evictExpiredMemoryCacheEntries() {
        int evictedCount = 0;

        for (Map.Entry<String, CachedCrl> entry : memoryCache.entrySet()) {
            if (entry.getValue().isExpired()) {
                memoryCache.remove(entry.getKey());
                evictedCount++;
            }
        }

        if (evictedCount > 0) {
            log.info("Evicted {} expired entries from memory cache", evictedCount);
        }

        return evictedCount;
    }
}
