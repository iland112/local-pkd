package com.smartcoreinc.localpkd.certificatevalidation.domain.model;

import io.github.wimdeblauwe.jpearl.AbstractEntityId;
import jakarta.persistence.Embeddable;

import java.util.UUID;

/**
 * CertificateId - 인증서 엔티티 ID (JPearl)
 *
 * <p><b>DDD Pattern</b>: Value Object로서 Certificate 엔티티의 ID를 타입 안전하게 관리</p>
 *
 * <p><b>JPearl Integration</b>:</p>
 * <ul>
 *   <li>UUID 기반의 타입 안전 ID</li>
 *   <li>컴파일 타임 타입 안전성 (CertificateId vs ParsedFileId 구분)</li>
 *   <li>equals/hashCode: 값 기반 동등성</li>
 * </ul>
 *
 * <p><b>사용 예시</b>:</p>
 * <pre>{@code
 * // 새 Certificate ID 생성
 * CertificateId certificateId = CertificateId.newId();
 *
 * // 기존 ID로부터 생성
 * CertificateId fromUUID = new CertificateId(UUID.fromString("..."));
 *
 * // 문자열 UUID로부터 생성
 * CertificateId fromString = CertificateId.of("550e8400-e29b-41d4-a716-446655440000");
 * }</pre>
 *
 * @author SmartCore Inc.
 * @version 1.0
 * @since 2025-10-23
 * @see Certificate
 */
@Embeddable
public class CertificateId extends AbstractEntityId<UUID> {

    /**
     * JPA용 기본 생성자 (protected)
     */
    protected CertificateId() {
    }

    /**
     * UUID로부터 CertificateId 생성
     *
     * @param id UUID
     */
    public CertificateId(UUID id) {
        super(id);
    }

    /**
     * 새로운 CertificateId 생성 (랜덤 UUID)
     *
     * @return 새로운 CertificateId
     */
    public static CertificateId newId() {
        return new CertificateId(UUID.randomUUID());
    }

    /**
     * UUID로부터 CertificateId 생성
     *
     * @param id UUID
     * @return CertificateId
     * @throws IllegalArgumentException id가 null인 경우
     */
    public static CertificateId of(UUID id) {
        if (id == null) {
            throw new IllegalArgumentException("id must not be null");
        }
        return new CertificateId(id);
    }

    /**
     * 문자열로부터 CertificateId 생성
     *
     * @param id UUID 문자열
     * @return CertificateId
     * @throws IllegalArgumentException id가 null이거나 유효하지 않은 UUID 형식인 경우
     */
    public static CertificateId of(String id) {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("id must not be blank");
        }
        try {
            return new CertificateId(UUID.fromString(id));
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Invalid UUID format: " + id, e);
        }
    }
}
