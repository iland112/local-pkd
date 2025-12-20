package com.smartcoreinc.localpkd.ldapintegration.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;

/**
 * LdapUploadAsyncConfig - LDAP 업로드 전용 비동기 설정
 *
 * <p><b>목적</b>: Certificate Validation과 LDAP Upload를 별도 스레드 풀에서 실행</p>
 *
 * <p><b>스레드 풀 분리 이유</b>:</p>
 * <ul>
 *   <li>LDAP 네트워크 I/O 블로킹을 Certificate Validation 스레드와 분리</li>
 *   <li>LDAP 서버 부하에 따른 병목이 Validation에 영향 주지 않음</li>
 *   <li>독립적인 큐 크기 및 거부 정책 설정 가능</li>
 * </ul>
 *
 * <p><b>MSA 전환 대비</b>:</p>
 * <pre>
 * 현재 구조:
 * ┌─────────────────┐    ┌──────────────────────┐
 * │ Validation      │───▶│ ldapUploadExecutor   │
 * │ Thread Pool     │    │ (별도 스레드 풀)      │
 * └─────────────────┘    └──────────────────────┘
 *
 * MSA 전환 후:
 * ┌─────────────────┐    ┌──────────────┐    ┌──────────────────────┐
 * │ Validation      │───▶│ RabbitMQ     │───▶│ LDAP Upload          │
 * │ Service (Pod)   │    │ Exchange     │    │ Service (별도 Pod)   │
 * └─────────────────┘    └──────────────┘    └──────────────────────┘
 * </pre>
 *
 * <p><b>스레드 풀 설정</b>:</p>
 * <ul>
 *   <li>Core Pool Size: 4개 스레드 (LDAP 연결 4개 동시 활용)</li>
 *   <li>Max Pool Size: 8개 스레드 (피크 시 확장)</li>
 *   <li>Queue Capacity: 200개 작업 (배치 단위로 큐잉)</li>
 *   <li>Keep Alive Time: 120초 (LDAP 연결 재사용 고려)</li>
 *   <li>Thread Name Prefix: "ldap-upload-"</li>
 * </ul>
 *
 * @author SmartCore Inc.
 * @version 1.0
 * @since 2025-12-20
 */
@Slf4j
@Configuration
@EnableAsync
public class LdapUploadAsyncConfig {

    /**
     * LDAP 업로드 전용 스레드 풀 생성
     *
     * <p><b>스레드 풀 특성</b>:</p>
     * <ul>
     *   <li>LDAP I/O 블로킹에 최적화</li>
     *   <li>CallerRunsPolicy: 큐 가득 시 호출자 스레드에서 실행 (안정성 우선)</li>
     *   <li>Graceful shutdown 지원</li>
     * </ul>
     *
     * @return ThreadPoolTaskExecutor LDAP 업로드 작업 실행기
     */
    @Bean(name = "ldapUploadExecutor")
    public Executor ldapUploadExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();

        // Core Pool Size: 기본적으로 유지할 스레드 수
        // LDAP 연결 풀 크기와 맞추어 설정 (보통 4-8개)
        executor.setCorePoolSize(4);

        // Max Pool Size: 최대 동시 실행 스레드 수
        // LDAP 서버 연결 제한 고려
        executor.setMaxPoolSize(8);

        // Queue Capacity: 대기 작업 큐 크기
        // 배치 단위 이벤트이므로 상대적으로 적은 큐
        executor.setQueueCapacity(200);

        // Thread Name Prefix: 스레드 이름 접두사 (모니터링/디버깅 용)
        executor.setThreadNamePrefix("ldap-upload-");

        // Keep Alive Time: 코어 수를 초과하는 스레드가 유휴 상태일 때 유지 시간 (초)
        // LDAP 연결 재사용을 위해 길게 설정
        executor.setKeepAliveSeconds(120);

        // Wait For Tasks To Complete On Shutdown: 종료 시 모든 작업 완료 대기
        executor.setWaitForTasksToCompleteOnShutdown(true);

        // Await Termination: 모든 작업 완료 대기 시간 (초)
        executor.setAwaitTerminationSeconds(120);

        // Rejection Policy: 큐가 가득 찼을 때의 처리 정책
        // CallerRunsPolicy: 호출자 스레드에서 직접 실행 (데이터 손실 방지)
        executor.setRejectedExecutionHandler(new java.util.concurrent.ThreadPoolExecutor.CallerRunsPolicy());

        log.info("LDAP Upload Async Executor configured:");
        log.info("  - Core Pool Size: 4");
        log.info("  - Max Pool Size: 8");
        log.info("  - Queue Capacity: 200");
        log.info("  - Keep Alive: 120s");
        log.info("  - Rejection Policy: CallerRunsPolicy");

        return executor;
    }
}
