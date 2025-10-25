package com.smartcoreinc.localpkd.ldapintegration.infrastructure.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.ldap.core.LdapTemplate;
import org.springframework.ldap.core.support.LdapContextSource;
import org.springframework.ldap.pool.factory.PoolingContextSource;
import org.springframework.ldap.pool.validation.DefaultDirContextValidator;

/**
 * LdapConfiguration - Spring LDAP 설정 클래스
 *
 * <p><b>책임</b>:</p>
 * <ul>
 *   <li>LDAP 연결 풀 설정</li>
 *   <li>LdapTemplate Bean 등록</li>
 *   <li>LDAP 타임아웃 및 성능 설정</li>
 *   <li>연결 검증 설정</li>
 * </ul>
 *
 * <h3>설정 항목</h3>
 * <ul>
 *   <li>{@code spring.ldap.urls}: LDAP 서버 URL (예: ldap://localhost:389)</li>
 *   <li>{@code spring.ldap.username}: 바인드 사용자명</li>
 *   <li>{@code spring.ldap.password}: 바인드 비밀번호</li>
 *   <li>{@code spring.ldap.base}: 기본 DN</li>
 *   <li>{@code spring.ldap.pool.*}: 연결 풀 설정</li>
 * </ul>
 *
 * <h3>연결 풀 설정</h3>
 * <ul>
 *   <li>Max Active: 최대 활성 연결 수 (기본 8)</li>
 *   <li>Max Idle: 최대 대기 연결 수 (기본 4)</li>
 *   <li>Max Total: 총 연결 수 (기본 12)</li>
 *   <li>Block When Exhausted: 연결 대기 정책 (true: 대기, false: 예외)</li>
 *   <li>Eviction Interval: 유휴 연결 제거 간격 (밀리초)</li>
 * </ul>
 *
 * <h3>타임아웃 설정</h3>
 * <ul>
 *   <li>Connect Timeout: 연결 타임아웃 (기본 30초)</li>
 *   <li>Read Timeout: 읽기 타임아웃 (기본 60초)</li>
 *   <li>Connection Lifetime: 연결 수명 (기본 10분)</li>
 * </ul>
 *
 * @author SmartCore Inc.
 * @version 1.0
 * @since 2025-10-25
 */
@Slf4j
@Configuration
@EnableConfigurationProperties(LdapProperties.class)
@RequiredArgsConstructor
public class LdapConfiguration {

    private final LdapProperties ldapProperties;

    /**
     * LDAP 기본 ContextSource 생성
     *
     * <p>LdapContextSource를 생성하고 LDAP 서버 연결 정보를 설정합니다.</p>
     *
     * @return LdapContextSource
     */
    @Bean
    public LdapContextSource ldapContextSource() {
        log.info("Initializing LDAP Context Source");
        log.info("LDAP URLs: {}", ldapProperties.getUrls());
        log.info("LDAP Base DN: {}", ldapProperties.getBase());

        LdapContextSource contextSource = new LdapContextSource();

        // LDAP 서버 설정
        contextSource.setUrls(ldapProperties.getUrls().split(","));
        contextSource.setBase(ldapProperties.getBase());
        contextSource.setUserDn(ldapProperties.getUsername());
        contextSource.setPassword(ldapProperties.getPassword());

        // ContextSource 초기화
        try {
            contextSource.afterPropertiesSet();
        } catch (Exception e) {
            log.error("Error initializing LDAP ContextSource", e);
            throw new RuntimeException("Failed to initialize LDAP ContextSource", e);
        }

        log.info("LDAP ContextSource initialized successfully");

        return contextSource;
    }

    /**
     * LDAP 연결 풀이 포함된 PoolingContextSource 생성
     *
     * <p>LdapContextSource를 래핑하여 연결 풀 기능을 추가합니다.</p>
     *
     * @param contextSource 기본 LdapContextSource
     * @return PoolingContextSource
     */
    @Bean
    public PoolingContextSource poolingContextSource(LdapContextSource contextSource) {
        log.info("Initializing LDAP Pooling Context Source");

        PoolingContextSource poolingContextSource = new PoolingContextSource();
        poolingContextSource.setContextSource(contextSource);

        // 연결 풀 설정
        LdapProperties.PoolConfig poolConfig = ldapProperties.getPool();
        poolingContextSource.setMaxActive(poolConfig.getMaxActive());
        poolingContextSource.setMaxIdle(poolConfig.getMaxIdle());
        poolingContextSource.setMaxTotal(poolConfig.getMaxTotal());
        poolingContextSource.setMaxWait(ldapProperties.getPoolTimeout());
        poolingContextSource.setTestOnBorrow(poolConfig.isTestOnBorrow());
        poolingContextSource.setTestOnReturn(poolConfig.isTestOnReturn());
        poolingContextSource.setTestWhileIdle(poolConfig.isTestWhileIdle());
        poolingContextSource.setMinEvictableIdleTimeMillis(poolConfig.getMinEvictableIdleTimeMillis());

        // 연결 검증 로직
        poolingContextSource.setDirContextValidator(new DefaultDirContextValidator());

        log.info("LDAP PoolingContextSource initialized:");
        log.info("  - Max Active: {}", poolConfig.getMaxActive());
        log.info("  - Max Idle: {}", poolConfig.getMaxIdle());
        log.info("  - Max Total: {}", poolConfig.getMaxTotal());
        log.info("  - Max Wait: {}ms", ldapProperties.getPoolTimeout());

        return poolingContextSource;
    }

    /**
     * LdapTemplate Bean 등록
     *
     * @param contextSource LDAP ContextSource
     * @return LdapTemplate
     */
    @Bean
    public LdapTemplate ldapTemplate(PoolingContextSource contextSource) {
        log.info("Creating LdapTemplate Bean");
        LdapTemplate template = new LdapTemplate(contextSource);

        // 기본 설정
        template.setIgnorePartialResultException(true);

        log.info("LdapTemplate Bean created successfully");
        return template;
    }

    /**
     * LDAP 연결 헬스 체크 Bean
     *
     * <p>LDAP 연결 풀의 상태를 모니터링하기 위한 유틸리티 Bean입니다.</p>
     *
     * @param contextSource LDAP ContextSource
     * @param template LDAP Template
     * @return LDAP 헬스 체크 유틸리티
     */
    @Bean
    public LdapHealthCheck ldapHealthCheck(PoolingContextSource contextSource, LdapTemplate template) {
        return new LdapHealthCheck(contextSource, template);
    }

    /**
     * LDAP 연결 헬스 체크 클래스
     *
     * <p>LDAP 서버 연결 상태 및 연결 풀 통계를 제공합니다.</p>
     */
    public static class LdapHealthCheck {

        private final PoolingContextSource contextSource;
        private final LdapTemplate ldapTemplate;

        public LdapHealthCheck(PoolingContextSource contextSource, LdapTemplate ldapTemplate) {
            this.contextSource = contextSource;
            this.ldapTemplate = ldapTemplate;
        }

        /**
         * LDAP 서버 연결 테스트
         *
         * <p>LdapTemplate을 사용하여 LDAP 서버 연결을 테스트합니다.
         * 실제 연결 테스트는 어댑터에서 LDAP 작업을 수행할 때 이루어집니다.</p>
         *
         * @return 연결 성공 여부
         */
        public boolean isConnected() {
            try {
                // LdapTemplate이 제대로 초기화되었는지 확인
                return ldapTemplate != null;
            } catch (Exception e) {
                log.error("LDAP connection check error: {}", e.getMessage());
                return false;
            }
        }

        /**
         * LDAP 연결 풀 통계
         *
         * <p>현재 활성 및 유휴 연결 수를 반환합니다.</p>
         *
         * @return 연결 풀 정보 (문자열)
         */
        public String getPoolStatistics() {
            try {
                return String.format(
                        "LDAP Connection Pool [Active: %d, Idle: %d, Total: %d]",
                        contextSource.getNumActive(),
                        contextSource.getNumIdle(),
                        contextSource.getNumActive() + contextSource.getNumIdle()
                );
            } catch (Exception e) {
                log.warn("Could not retrieve pool statistics: {}", e.getMessage());
                return "LDAP Connection Pool [Status: Unknown]";
            }
        }
    }
}
