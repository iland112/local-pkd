package com.smartcoreinc.localpkd.ldapintegration.application.event;

import com.smartcoreinc.localpkd.certificatevalidation.application.response.LdapBatchUploadResult;
import com.smartcoreinc.localpkd.certificatevalidation.application.service.LdapBatchUploadService;
import com.smartcoreinc.localpkd.certificatevalidation.domain.model.Certificate;
import com.smartcoreinc.localpkd.certificatevalidation.domain.model.CertificateId;
import com.smartcoreinc.localpkd.certificatevalidation.domain.model.CertificateRevocationList;
import com.smartcoreinc.localpkd.certificatevalidation.domain.model.CrlId;
import com.smartcoreinc.localpkd.certificatevalidation.domain.repository.CertificateRepository;
import com.smartcoreinc.localpkd.certificatevalidation.domain.repository.CertificateRevocationListRepository;
import com.smartcoreinc.localpkd.ldapintegration.domain.event.LdapBatchUploadEvent;
import com.smartcoreinc.localpkd.shared.progress.ProcessingProgress;
import com.smartcoreinc.localpkd.shared.progress.ProcessingStage;
import com.smartcoreinc.localpkd.shared.progress.ProgressService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.context.event.EventListener;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * AsyncLdapUploadHandler - 비동기 LDAP 배치 업로드 이벤트 핸들러
 *
 * <p><b>목적</b>: Certificate Validation과 LDAP Upload를 별도 스레드에서 실행하여 성능 향상</p>
 *
 * <p><b>Event-Driven 아키텍처</b>:</p>
 * <ul>
 *   <li>현재: Spring ApplicationEventPublisher로 In-Memory 이벤트 전달</li>
 *   <li>향후: RabbitMQ Consumer로 전환 (@RabbitListener)</li>
 *   <li>별도 스레드 풀에서 LDAP 업로드 실행</li>
 * </ul>
 *
 * <p><b>멱등성 보장</b>:</p>
 * <ul>
 *   <li>batchId 기반 중복 처리 방지</li>
 *   <li>processedBatches Map으로 처리 완료 배치 추적</li>
 *   <li>RabbitMQ 전환 시 Redis로 교체 가능</li>
 * </ul>
 *
 * <p><b>RabbitMQ 전환 시 변경 사항</b>:</p>
 * <pre>
 * // 현재 (Spring Events)
 * @TransactionalEventListener
 * @Async("ldapUploadExecutor")
 * public void handleLdapBatchUpload(LdapBatchUploadEvent event)
 *
 * // 향후 (RabbitMQ)
 * @RabbitListener(queues = "ldap.upload.queue")
 * public void handleLdapBatchUpload(LdapBatchUploadEvent event, Channel channel, @Header(AmqpHeaders.DELIVERY_TAG) long tag)
 * </pre>
 *
 * @author SmartCore Inc.
 * @version 1.0
 * @since 2025-12-20
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AsyncLdapUploadHandler {

    private final LdapBatchUploadService ldapBatchUploadService;
    private final CertificateRepository certificateRepository;
    private final CertificateRevocationListRepository crlRepository;
    private final ProgressService progressService;

    /**
     * 처리 완료된 배치 ID 추적 (멱등성 보장)
     * RabbitMQ 전환 시: Redis SET으로 교체
     */
    private final Map<UUID, Boolean> processedBatches = new ConcurrentHashMap<>();

    /**
     * 비동기 LDAP 배치 업로드 이벤트 핸들러
     *
     * <p>이벤트 발행 즉시 별도 스레드에서 비동기로 실행됩니다.</p>
     * <p>새로운 트랜잭션에서 실행 (REQUIRES_NEW)하여 호출자 트랜잭션과 분리</p>
     * <p>RabbitMQ 전환 시 @RabbitListener로 변경됩니다.</p>
     *
     * @param event LDAP 배치 업로드 이벤트
     */
    @Async("ldapUploadExecutor")
    @EventListener
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void handleLdapBatchUpload(LdapBatchUploadEvent event) {
        UUID batchId = event.getBatchId();
        
        // 멱등성 체크: 이미 처리된 배치인지 확인
        if (processedBatches.containsKey(batchId)) {
            log.info("Batch already processed, skipping: batchId={}", batchId);
            return;
        }

        log.info("=== [Async] LDAP Batch Upload Started ===");
        log.info("BatchId: {}, UploadId: {}, Type: {}, Count: {}, Batch: {}/{}",
                batchId, event.getUploadId(), event.getUploadType(),
                event.getTargetIds().size(), event.getBatchNumber(), event.getTotalBatches());

        try {
            LdapBatchUploadResult result;

            switch (event.getUploadType()) {
                case CERTIFICATE:
                    result = uploadCertificates(event);
                    break;
                case CRL:
                    result = uploadCrls(event);
                    break;
                default:
                    log.error("Unknown upload type: {}", event.getUploadType());
                    return;
            }

            // 처리 완료 표시
            processedBatches.put(batchId, true);

            // SSE 진행 상황 전송
            sendProgressUpdate(event, result);

            log.info("=== [Async] LDAP Batch Upload Completed ===");
            log.info("BatchId: {}, Success: {}, Skipped: {}, Failed: {}",
                    batchId, result.successCount(), result.skippedCount(), result.failedCount());

        } catch (Exception e) {
            log.error("LDAP Batch Upload failed: batchId={}, error={}", batchId, e.getMessage(), e);
            
            // 실패 시에도 SSE로 에러 전송
            progressService.sendProgress(ProcessingProgress.builder()
                    .uploadId(event.getUploadId())
                    .stage(ProcessingStage.LDAP_SAVING_IN_PROGRESS)
                    .percentage(calculatePercentage(event))
                    .message("LDAP 업로드 실패: " + e.getMessage())
                    .errorMessage(e.getMessage())
                    .build());
        }
    }

    /**
     * 인증서 배치 업로드
     */
    private LdapBatchUploadResult uploadCertificates(LdapBatchUploadEvent event) {
        // ID 목록으로 Certificate 조회
        List<CertificateId> certificateIds = event.getTargetIds().stream()
                .map(CertificateId::new)
                .collect(Collectors.toList());

        List<Certificate> certificates = certificateRepository.findAllById(certificateIds);

        if (certificates.isEmpty()) {
            log.warn("No certificates found for IDs: {}", event.getTargetIds());
            return LdapBatchUploadResult.empty();
        }

        log.debug("Found {} certificates for LDAP upload", certificates.size());
        return ldapBatchUploadService.uploadCertificates(certificates);
    }

    /**
     * CRL 배치 업로드
     */
    private LdapBatchUploadResult uploadCrls(LdapBatchUploadEvent event) {
        // ID 목록으로 CRL 조회
        List<CrlId> crlIds = event.getTargetIds().stream()
                .map(CrlId::new)
                .collect(Collectors.toList());

        List<CertificateRevocationList> crls = crlRepository.findAllById(crlIds);

        if (crls.isEmpty()) {
            log.warn("No CRLs found for IDs: {}", event.getTargetIds());
            return LdapBatchUploadResult.empty();
        }

        log.debug("Found {} CRLs for LDAP upload", crls.size());
        return ldapBatchUploadService.uploadCrls(crls);
    }

    /**
     * SSE 진행 상황 전송
     */
    private void sendProgressUpdate(LdapBatchUploadEvent event, LdapBatchUploadResult result) {
        int percentage = calculatePercentage(event);
        String message = String.format("LDAP 배치 %d/%d 완료 (%s): 성공 %d, 스킵 %d, 실패 %d",
                event.getBatchNumber(), event.getTotalBatches(),
                event.getUploadType().name(),
                result.successCount(), result.skippedCount(), result.failedCount());

        ProcessingProgress progress = ProcessingProgress.builder()
                .uploadId(event.getUploadId())
                .stage(ProcessingStage.LDAP_SAVING_IN_PROGRESS)
                .percentage(percentage)
                .message(message)
                .build();

        progressService.sendProgress(progress);
    }

    /**
     * 진행률 계산 (배치 번호 기반)
     */
    private int calculatePercentage(LdapBatchUploadEvent event) {
        if (event.getTotalBatches() == 0) {
            return 100;
        }
        return (int) ((double) event.getBatchNumber() / event.getTotalBatches() * 100);
    }

    /**
     * 처리된 배치 캐시 정리 (메모리 관리)
     * 
     * <p>주기적으로 호출하여 오래된 항목 제거</p>
     * <p>RabbitMQ 전환 시: Redis TTL로 자동 만료</p>
     */
    public void clearProcessedBatches() {
        int size = processedBatches.size();
        processedBatches.clear();
        log.info("Cleared {} processed batch entries", size);
    }
}
