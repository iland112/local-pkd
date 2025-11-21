package com.smartcoreinc.localpkd.certificatevalidation.application.event;

import com.smartcoreinc.localpkd.certificatevalidation.application.command.UploadToLdapCommand;
import com.smartcoreinc.localpkd.certificatevalidation.application.response.UploadToLdapResponse;
import com.smartcoreinc.localpkd.certificatevalidation.domain.event.CertificatesValidatedEvent;
import com.smartcoreinc.localpkd.certificatevalidation.domain.model.Certificate;
import com.smartcoreinc.localpkd.certificatevalidation.domain.repository.CertificateRepository;
import com.smartcoreinc.localpkd.certificatevalidation.application.usecase.UploadToLdapUseCase;
import com.smartcoreinc.localpkd.ldapintegration.domain.event.LdapUploadCompletedEvent;
import com.smartcoreinc.localpkd.ldapintegration.domain.event.LdapUploadFailedEvent;
import com.smartcoreinc.localpkd.shared.event.EventBus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

/**
 * UploadToLdapEventHandler - LDAP 업로드 이벤트 핸들러
 *
 * <p><b>목적</b>: `CertificatesValidatedEvent`를 리스닝하고 LDAP 업로드를 자동으로 트리거합니다.</p>
 *
 * <p><b>이벤트 구독</b>:
 * <ul>
 *   <li>CertificatesValidatedEvent: 인증서 검증 완료 시 발행</li>
 * </ul>
 * </p>
 *
 * <p><b>처리 방식</b>:
 * <ul>
 *   <li>동기: handleCertificatesValidated() - 트랜잭션 커밋 후 즉시 처리</li>
 * </ul>
 * </p>
 *
 * <p><b>처리 흐름</b>:
 * <ol>
 *   <li>CertificatesValidatedEvent 수신</li>
 *   <li>UploadToLdapCommand 생성</li>
 *   <li>UploadToLdapUseCase.execute() 호출</li>
 *   <li>LDAP 업로드 결과 로깅</li>
 *   <li>UploadToLdapCompletedEvent 발행 (향후 구현)</li>
 * </ol>
 * </p>
 *
 * <p><b>에러 처리</b>:
 * <ul>
 *   <li>LDAP 업로드 실패: 로깅 후 계속 진행</li>
 *   <li>Exception: 로깅, 사용자에게 영향 최소화</li>
 * </ul>
 * </p>
 *
 * <p><b>Phase 17</b>: Event-Driven 파이프라인 완성 (자동 LDAP 업로드)</p>
 *
 * @see CertificatesValidatedEvent
 * @see UploadToLdapUseCase
 * @see UploadToLdapCommand
 * @author SmartCore Inc.
 * @version 1.0
 * @since 2025-10-30 (Phase 17 Task 1.6)
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class UploadToLdapEventHandler {

    private final UploadToLdapUseCase uploadToLdapUseCase;
    private final CertificateRepository certificateRepository;
    private final EventBus eventBus;

    @org.springframework.beans.factory.annotation.Value("${spring.ldap.base:dc=ldap,dc=smartcoreinc,dc=com}")
    private String defaultBaseDn;

    /**
     * CertificatesValidatedEvent 동기 처리
     *
     * <p><b>실행 시점</b>: 트랜잭션 커밋 직후</p>
     *
     * <p><b>처리</b>:
     * <ol>
     *   <li>이벤트 로깅</li>
     *   <li>UploadToLdapCommand 생성</li>
     *   <li>LDAP 업로드 실행</li>
     *   <li>결과 로깅</li>
     * </ol>
     * </p>
     *
     * @param event CertificatesValidatedEvent
     * @since Phase 17 Task 1.6
     */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handleCertificatesValidated(CertificatesValidatedEvent event) {
        log.info("=== CertificatesValidatedEvent received ===");
        log.info("Upload ID: {}, Valid certificates: {}, Invalid certificates: {}",
            event.getUploadId(),
            event.getValidCertificateCount(),
            event.getInvalidCertificateCount()
        );

        try {
            // 1. 해당 업로드의 모든 인증서 조회 (Task 1.1 findByUploadId 활용)
            List<Certificate> certificates = certificateRepository.findByUploadId(event.getUploadId());

            if (certificates.isEmpty()) {
                log.warn("No certificates found for upload ID: {}", event.getUploadId());
                return;
            }

            // 2. 인증서 ID 추출
            List<java.util.UUID> certificateIds = certificates.stream()
                .map(cert -> cert.getId().getId())
                .collect(Collectors.toList());

            log.debug("Found {} certificates to upload to LDAP", certificateIds.size());

            // 3. UploadToLdapCommand 생성
            UploadToLdapCommand command = UploadToLdapCommand.builder()
                .uploadId(event.getUploadId())
                .certificateIds(certificateIds)
                .baseDn(defaultBaseDn)
                .isBatch(certificateIds.size() > 1)
                .build();

            log.debug("Executing UploadToLdapUseCase with {} certificates", certificateIds.size());

            // 4. LDAP 업로드 실행
            UploadToLdapResponse response = uploadToLdapUseCase.execute(command);

            // 5. 결과 로깅
            logUploadResult(response);

            // 6. 성공/실패 이벤트 발행
            if (response.isSuccess() || response.isPartialSuccess()) {
                publishUploadCompletedEvent(response, certificateIds.size());
            } else {
                publishUploadFailedEvent(event, certificateIds.size(),
                    response.getErrorMessage() != null ? response.getErrorMessage() : "Unknown error");
            }

        } catch (Exception e) {
            log.error("Error handling CertificatesValidatedEvent for upload ID: {}", event.getUploadId(), e);
            // 실패 이벤트 발행 (예외 발생 시 인증서 개수 조회 불가, 0으로 설정)
            try {
                List<Certificate> certificates = certificateRepository.findByUploadId(event.getUploadId());
                publishUploadFailedEvent(event, certificates.size(), e.getMessage());
            } catch (Exception ex) {
                log.error("Failed to publish UploadFailedEvent due to repository error", ex);
                // Repository에서도 예외 발생 시 인증서 개수 0으로 이벤트 발행
                publishUploadFailedEvent(event, 0, e.getMessage());
            }
        }
    }

    // ========== Helper Methods ==========

    /**
     * LDAP 업로드 결과 로깅
     *
     * @param response 업로드 응답
     */
    private void logUploadResult(UploadToLdapResponse response) {
        if (response.isSuccess()) {
            log.info("✅ LDAP upload success: {}/{} certificates uploaded",
                response.getSuccessCount(),
                response.getTotalCount()
            );
        } else if (response.isPartialSuccess()) {
            log.warn("⚠️ LDAP upload partial success: {}/{} certificates uploaded ({} failed)",
                response.getSuccessCount(),
                response.getTotalCount(),
                response.getFailureCount()
            );
            response.getFailedCertificateIds().forEach(id ->
                log.debug("Failed certificate ID: {}", id)
            );
        } else {
            log.error("❌ LDAP upload failed: {}",
                response.getErrorMessage() != null ?
                    response.getErrorMessage() :
                    "Unknown error"
            );
        }

        // 성공한 DN 로깅
        if (response.getSuccessCount() > 0) {
            log.debug("Uploaded LDAP DNs:");
            response.getUploadedDns().forEach(dn ->
                log.debug("  - {}", dn)
            );
        }
    }

    /**
     * 업로드 완료 이벤트 발행
     *
     * @param response 업로드 응답
     * @param totalCount 총 인증서 개수
     */
    private void publishUploadCompletedEvent(UploadToLdapResponse response, int totalCount) {
        try {
            LdapUploadCompletedEvent event = new LdapUploadCompletedEvent(
                response.getUploadId(),
                response.getSuccessCount(),
                0,  // CRL count (현재는 인증서만 처리)
                response.getFailureCount(),
                LocalDateTime.now()
            );

            eventBus.publish(event);
            log.info("✅ Published LdapUploadCompletedEvent: uploadId={}, success={}/{}, failed={}",
                response.getUploadId(),
                response.getSuccessCount(),
                totalCount,
                response.getFailureCount()
            );
        } catch (Exception e) {
            log.error("Failed to publish LdapUploadCompletedEvent", e);
        }
    }

    /**
     * 업로드 실패 이벤트 발행
     *
     * @param event 원본 이벤트
     * @param attemptedCount 시도한 인증서 개수
     * @param errorMessage 에러 메시지
     */
    private void publishUploadFailedEvent(CertificatesValidatedEvent event, int attemptedCount, String errorMessage) {
        try {
            LdapUploadFailedEvent failedEvent = new LdapUploadFailedEvent(
                event.getUploadId(),
                errorMessage,
                attemptedCount,
                LocalDateTime.now()
            );

            eventBus.publish(failedEvent);
            log.error("❌ Published LdapUploadFailedEvent: uploadId={}, attempted={}, error={}",
                event.getUploadId(),
                attemptedCount,
                errorMessage
            );
        } catch (Exception e) {
            log.error("Failed to publish LdapUploadFailedEvent", e);
        }
    }
}
