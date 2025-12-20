package com.smartcoreinc.localpkd.ldapintegration.domain.event;

import com.smartcoreinc.localpkd.shared.domain.DomainEvent;
import lombok.Getter;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * LdapBatchUploadEvent - LDAP 배치 업로드 도메인 이벤트
 *
 * <p><b>목적</b>: 인증서/CRL 검증 완료 후 LDAP 업로드를 비동기로 트리거</p>
 *
 * <p><b>MSA 전환 대비 (RabbitMQ)</b>:</p>
 * <ul>
 *   <li>현재: Spring ApplicationEventPublisher로 In-Memory 전달</li>
 *   <li>향후: RabbitMQ Message로 전환 (이 클래스를 Message DTO로 재사용)</li>
 *   <li>Serializable 구현 + JSON 직렬화 가능한 필드만 포함</li>
 *   <li>Exchange: ldap.upload, Routing Key: ldap.upload.{type}</li>
 * </ul>
 *
 * <p><b>RabbitMQ 전환 시 변경 사항</b>:</p>
 * <ul>
 *   <li>Publisher: RabbitTemplate.convertAndSend() 사용</li>
 *   <li>Consumer: @RabbitListener + Manual ACK</li>
 *   <li>DLQ: ldap.upload.dlq (실패 메시지 재처리)</li>
 * </ul>
 *
 * <p><b>멱등성 보장</b>:</p>
 * <ul>
 *   <li>batchId: 배치 고유 식별자 (중복 처리 방지)</li>
 *   <li>Handler에서 이미 처리된 batchId는 스킵</li>
 * </ul>
 *
 * @author SmartCore Inc.
 * @version 1.0
 * @since 2025-12-20
 */
@Getter
public class LdapBatchUploadEvent implements DomainEvent, Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 배치 고유 식별자 (멱등성 키)
     * RabbitMQ에서 Message Deduplication에 사용
     */
    private final UUID batchId;

    /**
     * 업로드 ID (파일 업로드 추적용)
     */
    private final UUID uploadId;

    /**
     * 업로드 대상 타입 (RabbitMQ Routing Key로 사용)
     */
    private final UploadType uploadType;

    /**
     * 업로드할 인증서/CRL ID 목록
     * (엔티티 직접 전달 X → 네트워크 전송 가능)
     */
    private final List<UUID> targetIds;

    /**
     * 이벤트 발생 시각
     */
    private final LocalDateTime occurredAt;

    /**
     * 배치 순번 (진행률 계산용)
     */
    private final int batchNumber;

    /**
     * 총 배치 수 (진행률 계산용)
     */
    private final int totalBatches;

    /**
     * 업로드 대상 타입 열거형
     * RabbitMQ Routing Key: ldap.upload.certificate, ldap.upload.crl
     */
    public enum UploadType {
        CERTIFICATE("certificate"),
        CRL("crl");

        private final String routingKey;

        UploadType(String routingKey) {
            this.routingKey = routingKey;
        }

        public String getRoutingKey() {
            return "ldap.upload." + routingKey;
        }
    }

    private LdapBatchUploadEvent(Builder builder) {
        this.batchId = builder.batchId != null ? builder.batchId : UUID.randomUUID();
        this.uploadId = builder.uploadId;
        this.uploadType = builder.uploadType;
        this.targetIds = List.copyOf(builder.targetIds);
        this.occurredAt = LocalDateTime.now();
        this.batchNumber = builder.batchNumber;
        this.totalBatches = builder.totalBatches;
    }

    public static Builder builder() {
        return new Builder();
    }

    /**
     * 인증서 배치 업로드 이벤트 생성
     */
    public static LdapBatchUploadEvent forCertificates(
            UUID uploadId,
            List<UUID> certificateIds,
            int batchNumber,
            int totalBatches) {
        return builder()
                .uploadId(uploadId)
                .uploadType(UploadType.CERTIFICATE)
                .targetIds(certificateIds)
                .batchNumber(batchNumber)
                .totalBatches(totalBatches)
                .build();
    }

    /**
     * CRL 배치 업로드 이벤트 생성
     */
    public static LdapBatchUploadEvent forCrls(
            UUID uploadId,
            List<UUID> crlIds,
            int batchNumber,
            int totalBatches) {
        return builder()
                .uploadId(uploadId)
                .uploadType(UploadType.CRL)
                .targetIds(crlIds)
                .batchNumber(batchNumber)
                .totalBatches(totalBatches)
                .build();
    }

    /**
     * RabbitMQ Message ID로 사용
     */
    public String getMessageId() {
        return batchId.toString();
    }

    // ========== DomainEvent 인터페이스 구현 ==========

    @Override
    public UUID eventId() {
        return batchId;
    }

    @Override
    public LocalDateTime occurredOn() {
        return occurredAt;
    }

    @Override
    public String eventType() {
        return "LdapBatchUpload";
    }

    @Override
    public String toString() {
        return String.format("LdapBatchUploadEvent{batchId=%s, uploadId=%s, type=%s, count=%d, batch=%d/%d}",
                batchId, uploadId, uploadType, targetIds.size(), batchNumber, totalBatches);
    }

    public static class Builder {
        private UUID batchId;
        private UUID uploadId;
        private UploadType uploadType;
        private List<UUID> targetIds;
        private int batchNumber;
        private int totalBatches;

        public Builder batchId(UUID batchId) {
            this.batchId = batchId;
            return this;
        }

        public Builder uploadId(UUID uploadId) {
            this.uploadId = uploadId;
            return this;
        }

        public Builder uploadType(UploadType uploadType) {
            this.uploadType = uploadType;
            return this;
        }

        public Builder targetIds(List<UUID> targetIds) {
            this.targetIds = targetIds;
            return this;
        }

        public Builder batchNumber(int batchNumber) {
            this.batchNumber = batchNumber;
            return this;
        }

        public Builder totalBatches(int totalBatches) {
            this.totalBatches = totalBatches;
            return this;
        }

        public LdapBatchUploadEvent build() {
            if (uploadId == null) throw new IllegalArgumentException("uploadId is required");
            if (uploadType == null) throw new IllegalArgumentException("uploadType is required");
            if (targetIds == null || targetIds.isEmpty()) throw new IllegalArgumentException("targetIds must not be empty");
            return new LdapBatchUploadEvent(this);
        }
    }
}
