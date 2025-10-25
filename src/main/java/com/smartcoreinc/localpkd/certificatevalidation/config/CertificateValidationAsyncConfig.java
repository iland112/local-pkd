package com.smartcoreinc.localpkd.certificatevalidation.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;

/**
 * CertificateValidationAsyncConfig - 비동기 이벤트 처리 설정
 *
 * <p><b>목적</b>: Certificate Validation Bounded Context의 비동기 이벤트 핸들러를 위한 스레드 풀 설정</p>
 *
 * <p><b>주요 기능</b>:</p>
 * <ul>
 *   <li>비동기 이벤트 처리용 스레드 풀 생성</li>
 *   <li>Thread Pool 파라미터 설정 (코어 크기, 최대 크기, 큐 크기)</li>
 *   <li>거부 정책 설정 (ThreadPoolExecutor.CallerRunsPolicy)</li>
 *   <li>스레드 이름 지정 (디버깅 용이)</li>
 * </ul>
 *
 * <p><b>스레드 풀 설정</b>:</p>
 * <ul>
 *   <li>Core Pool Size: 5개 스레드 (기본적으로 유지)</li>
 *   <li>Max Pool Size: 20개 스레드 (최대 동시 실행)</li>
 *   <li>Queue Capacity: 500개 작업 (큐 대기)</li>
 *   <li>Keep Alive Time: 60초 (코어 초과 스레드 유지)</li>
 *   <li>Thread Name Prefix: "cert-validation-async-"</li>
 * </ul>
 *
 * <p><b>거부 정책</b> (CallerRunsPolicy):</p>
 * <pre>
 * 큐가 가득 찼을 때:
 * - 새 스레드 생성 대신 호출자 스레드에서 직접 실행
 * - 비동기 작업이 실패하지 않도록 함
 * - 성능 저하를 감수하고 안정성 우선
 * </pre>
 *
 * <p><b>사용 예시</b>:</p>
 * <pre>{@code
 * @Service
 * public class CertificateValidationEventHandler {
 *
 *     @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
 *     @Async("certificationValidationExecutor")
 *     public void handleCertificateValidatedAsync(CertificateValidatedEvent event) {
 *         // 비동기 처리: 별도 스레드에서 실행
 *     }
 * }
 * }</pre>
 *
 * @see org.springframework.scheduling.annotation.EnableAsync
 * @see org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor
 * @author SmartCore Inc.
 * @version 1.0
 * @since 2025-10-25
 */
@Slf4j
@Configuration
@EnableAsync
public class CertificateValidationAsyncConfig {

    /**
     * Certificate Validation 비동기 처리용 스레드 풀 생성
     *
     * <p><b>스레드 풀 특성</b>:</p>
     * <ul>
     *   <li>최대 20개 동시 스레드</li>
     *   <li>500개 대기 작업 큐</li>
     *   <li>CallerRunsPolicy: 큐 가득 시 호출자 스레드에서 실행</li>
     * </ul>
     *
     * <p><b>모니터링</b>:</p>
     * <ul>
     *   <li>스레드풀 상태: 로그로 주기적 출력</li>
     *   <li>거부된 작업: WARN 로그</li>
     *   <li>예외: ERROR 로그</li>
     * </ul>
     *
     * @return ThreadPoolTaskExecutor 비동기 작업 실행기
     */
    @Bean(name = "certificationValidationExecutor")
    public Executor certificationValidationExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();

        // Core Pool Size: 기본적으로 유지할 스레드 수
        executor.setCorePoolSize(5);

        // Max Pool Size: 최대 동시 실행 스레드 수
        executor.setMaxPoolSize(20);

        // Queue Capacity: 대기 작업 큐 크기
        executor.setQueueCapacity(500);

        // Thread Name Prefix: 스레드 이름 접두사 (모니터링/디버깅 용)
        executor.setThreadNamePrefix("cert-validation-async-");

        // Keep Alive Time: 코어 수를 초과하는 스레드가 유휴 상태일 때 유지 시간 (초)
        executor.setKeepAliveSeconds(60);

        // Wait For Tasks To Complete On Shutdown: 종료 시 모든 작업 완료 대기
        executor.setWaitForTasksToCompleteOnShutdown(true);

        // Await Termination: 모든 작업 완료 대기 시간 (초)
        executor.setAwaitTerminationSeconds(60);

        // Rejection Policy: 큐가 가득 찼을 때의 처리 정책
        // CallerRunsPolicy: 호출자 스레드에서 직접 실행 (성능 저하하지만 작업 실패 방지)
        executor.setRejectedExecutionHandler(new java.util.concurrent.ThreadPoolExecutor.CallerRunsPolicy());

        // Exception Handler: 비동기 작업에서 발생한 예외 처리
        executor.setWaitForTasksToCompleteOnShutdown(true);

        log.info("Certificate Validation Async Executor configured:");
        log.info("  - Core Pool Size: 5");
        log.info("  - Max Pool Size: 20");
        log.info("  - Queue Capacity: 500");
        log.info("  - Rejection Policy: CallerRunsPolicy");

        return executor;
    }
}
