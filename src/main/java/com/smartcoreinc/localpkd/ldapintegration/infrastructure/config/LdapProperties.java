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
 * app.ldap.certificate-base=ou=certificates
 * app.ldap.csca-ou=ou=csca
 * app.ldap.dsc-ou=ou=dsc
 * app.ldap.ds-ou=ou=ds
 * app.ldap.crl-base=ou=crl
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

    /**
     * LDAP에서 인증서를 저장할 기본 OU
     * 기본값: ou=certificates
     */
    private String certificateBase = "ou=certificates";

    /**
     * CSCA 저장소 OU
     * 기본값: ou=csca
     */
    private String cscaOu = "ou=csca";

    /**
     * DSC 저장소 OU
     * 기본값: ou=dsc
     */
    private String dscOu = "ou=dsc";

    /**
     * DS 저장소 OU
     * 기본값: ou=ds
     */
    private String dsOu = "ou=ds";

    /**
     * CRL을 저장할 기본 OU
     * 기본값: ou=crl
     */
    private String crlBase = "ou=crl";

    /**
     * LDAP 동기화 설정
     */
    private SyncConfig sync = new SyncConfig();

    /**
     * LDAP 배치 처리 설정
     */
    private BatchConfig batch = new BatchConfig();

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
     * 인증서 기본 DN 반환
     * 형식: ou=certificates,dc=ldap,dc=smartcoreinc,dc=com
     */
    public String getCertificateBaseDn(String baseDn) {
        return String.format("%s,%s", certificateBase, baseDn);
    }

    /**
     * CSCA 저장소 DN 반환
     */
    public String getCscaBaseDn(String baseDn) {
        return String.format("%s,%s,%s", cscaOu, certificateBase, baseDn);
    }

    /**
     * DSC 저장소 DN 반환
     */
    public String getDscBaseDn(String baseDn) {
        return String.format("%s,%s,%s", dscOu, certificateBase, baseDn);
    }

    /**
     * DS 저장소 DN 반환
     */
    public String getDsBaseDn(String baseDn) {
        return String.format("%s,%s,%s", dsOu, certificateBase, baseDn);
    }

    /**
     * CRL 기본 DN 반환
     */
    public String getCrlBaseDn(String baseDn) {
        return String.format("%s,%s", crlBase, baseDn);
    }
}
