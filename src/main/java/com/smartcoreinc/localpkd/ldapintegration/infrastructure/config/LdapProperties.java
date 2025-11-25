package com.smartcoreinc.localpkd.ldapintegration.infrastructure.config;

import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * LDAP Integration Configuration Properties
 *
 * <p>application.properties의 LDAP 관련 설정을 매핑합니다.</p>
 *
 * <p>프로퍼티 예시:</p>
 * <pre>{@code
 * app.ldap.csca-o=o=csca
 * app.ldap.dsc-o=o=dsc
 * app.ldap.ds-o=o=ds
 * app.ldap.crl-o=o=crl
 * app.ldap.sync.enabled=true
 * app.ldap.sync.batch-size=100
 * app.ldap.sync.max-retries=3
 * app.ldap.batch.thread-pool-size=4
 * }</pre>
 *
 * @author SmartCore Inc.
 * @version 1.0
 * @since 2025-10-25
 */
@Data
@NoArgsConstructor
@Component
@ConfigurationProperties(prefix = "app.ldap")
public class LdapProperties {

    // ========== Spring LDAP 기본 설정 ==========

    /**
     * LDAP 서버 URL
     * 예: ldap://localhost:389 또는 ldaps://localhost:636
     */
    private String urls = "ldap://localhost:389";

    /**
     * LDAP 기본 Distinguished Name (Base DN)
     * 예: dc=ldap,dc=smartcoreinc,dc=com
     */
    private String base = "dc=ldap,dc=smartcoreinc,dc=com";

    /**
     * LDAP 바인드 사용자명 (DN)
     * 예: cn=admin,dc=ldap,dc=smartcoreinc,dc=com
     */
    private String username = "cn=admin,dc=ldap,dc=smartcoreinc,dc=com";

    /**
     * LDAP 바인드 비밀번호
     */
    private String password = "";

    /**
     * LDAP 연결 풀 설정
     */
    private PoolConfig pool = new PoolConfig();

    /**
     * LDAP 연결 타임아웃 (밀리초)
     * 기본값: 30000 (30초)
     */
    private int connectTimeout = 30000;

    /**
     * LDAP 읽기 타임아웃 (밀리초)
     * 기본값: 60000 (60초)
     */
    private int readTimeout = 60000;

    /**
     * LDAP 연결 풀 타임아웃 (밀리초)
     * 기본값: 5000 (5초)
     */
    private int poolTimeout = 5000;

    // ========== 애플리케이션 커스텀 설정 ==========

    /**
     * CSCA 저장소 O (Organization)
     * 기본값: o=csca
     */
    private String cscaO = "o=csca";

    /**
     * DSC 저장소 O (Organization)
     * 기본값: o=dsc
     */
    private String dscO = "o=dsc";

    /**
     * DS 저장소 O (Organization)
     * 기본값: o=ds
     */
    private String dsO = "o=ds";

    /**
     * CRL을 저장할 O (Organization)
     * 기본값: o=crl
     */
    private String crlO = "o=crl";

    /**
     * LDAP 동기화 설정
     */
    private SyncConfig sync = new SyncConfig();

    /**
     * LDAP 배치 처리 설정
     */
    private BatchConfig batch = new BatchConfig();

    /**
     * LDAP 연결 풀 설정
     *
     * @author SmartCore Inc.
     */
    @Data
    @NoArgsConstructor
    public static class PoolConfig {

        /**
         * 최대 활성 연결 수
         * 기본값: 8
         */
        private int maxActive = 8;

        /**
         * 최대 대기 연결 수
         * 기본값: 4
         */
        private int maxIdle = 4;

        /**
         * 총 최대 연결 수
         * 기본값: 12
         */
        private int maxTotal = 12;

        /**
         * 최소 대기 연결 수
         * 기본값: 2
         */
        private int minIdle = 2;

        /**
         * 연결 고갈 시 대기 정책
         * 기본값: true (대기), false (예외 발생)
         */
        private boolean blockWhenExhausted = true;

        /**
         * 유휴 연결 검증 여부
         * 기본값: true
         */
        private boolean testOnBorrow = true;

        /**
         * 반환된 연결 검증 여부
         * 기본값: true
         */
        private boolean testOnReturn = true;

        /**
         * 유휴 상태에서 연결 검증 여부
         * 기본값: true
         */
        private boolean testWhileIdle = true;

        /**
         * 유휴 연결 제거 간격 (밀리초)
         * 기본값: 600000 (10분)
         */
        private long evictionIntervalMillis = 600000L;

        /**
         * 유휴 연결 최소 유지 시간 (밀리초)
         * 기본값: 300000 (5분)
         */
        private long minEvictableIdleTimeMillis = 300000L;
    }

    /**
     * LDAP 동기화 설정
     *
     * @author SmartCore Inc.
     */
    @Data
    @NoArgsConstructor
    public static class SyncConfig {

        /**
         * LDAP 동기화 활성화 여부
         * 기본값: true
         */
        private boolean enabled = true;

        /**
         * 배치 크기 (한 번에 처리할 항목 수)
         * 기본값: 100
         */
        private int batchSize = 100;

        /**
         * 최대 재시도 횟수
         * 기본값: 3
         */
        private int maxRetries = 3;

        /**
         * 초기 지연 시간 (밀리초)
         * 기본값: 1000 (1초)
         */
        private long initialDelayMs = 1000;

        /**
         * 정기적 동기화 Cron 표현식
         * 기본값: "0 0 0 * * *" (매일 자정)
         * 형식: second minute hour day month day-of-week
         */
        private String scheduledCron = "0 0 0 * * *";

        /**
         * LDAP 동기화 타임아웃 (초)
         * 기본값: 300 (5분)
         */
        private int syncTimeoutSeconds = 300;
    }

    /**
     * LDAP 배치 처리 설정
     *
     * @author SmartCore Inc.
     */
    @Data
    @NoArgsConstructor
    public static class BatchConfig {

        /**
         * 스레드 풀 크기
         * 기본값: 4
         */
        private int threadPoolSize = 4;

        /**
         * 스레드 풀 큐 용량
         * 기본값: 1000
         */
        private int queueCapacity = 1000;

        /**
         * 스레드 Keep-Alive 시간 (초)
         * 기본값: 60
         */
        private int keepAliveSeconds = 60;

        /**
         * 스레드 풀 이름 접두어
         * 기본값: ldap-batch-
         */
        private String threadNamePrefix = "ldap-batch-";

        /**
         * 배치 처리 중 실패 항목 저장 여부
         * 기본값: true
         */
        private boolean recordFailures = true;

        /**
         * 최대 배치 크기
         * 기본값: 1000 (한 번에 처리할 최대 항목 수)
         */
        private int maxBatchSize = 1000;
    }

    /**
     * 완성된 LDAP DN 생성 메서드들
     */

    /**
     * CSCA 저장소 DN 반환 (o=csca)
     */
    public String getCscaBaseDn() {
        return cscaO;
    }

    /**
     * DSC 저장소 DN 반환 (o=dsc)
     */
    public String getDscBaseDn() {
        return dscO;
    }

    /**
     * DS 저장소 DN 반환 (o=ds)
     */
    public String getDsBaseDn() {
        return dsO;
    }

    /**
     * CRL 기본 DN 반환 (o=crl)
     */
    public String getCrlBaseDn() {
        return crlO;
    }
}