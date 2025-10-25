package com.smartcoreinc.localpkd.certificatevalidation.domain.model;

import io.github.wimdeblauwe.jpearl.AbstractEntityId;
import jakarta.persistence.Embeddable;

import java.util.UUID;

/**
 * CrlId - CRL (Certificate Revocation List) 엔티티 ID (JPearl)
 *
 * <p><b>DDD Pattern</b>: Value Object로서 CertificateRevocationList 엔티티의 ID를 타입 안전하게 관리</p>
 *
 * <p><b>JPearl Integration</b>:</p>
 * <ul>
 *   <li>UUID 기반의 타입 안전 ID</li>
 *   <li>컴파일 타임 타입 안전성 (CrlId vs CertificateId 구분)</li>
 *   <li>equals/hashCode: 값 기반 동등성</li>
 * </ul>
 *
 * <p><b>사용 예시</b>:</p>
 * <pre>{@code
 * // 새 CRL ID 생성
 * CrlId crlId = CrlId.newId();
 *
 * // 기존 ID로부터 생성
 * CrlId fromUUID = new CrlId(UUID.fromString("..."));
 *
 * // 문자열 UUID로부터 생성
 * CrlId fromString = CrlId.of("550e8400-e29b-41d4-a716-446655440001");
 * }</pre>
 *
 * @author SmartCore Inc.
 * @version 1.0
 * @since 2025-10-24
 * @see CertificateRevocationList
 */
@Embeddable
public class CrlId extends AbstractEntityId<UUID> {

    /**
     * JPA용 기본 생성자 (protected)
     */
    protected CrlId() {
    }

    /**
     * UUID로부터 CrlId 생성
     *
     * @param id UUID
     */
    public CrlId(UUID id) {
        super(id);
    }

    /**
     * 새로운 CrlId 생성 (랜덤 UUID)
     *
     * @return 새로운 CrlId
     */
    public static CrlId newId() {
        return new CrlId(UUID.randomUUID());
    }

    /**
     * UUID로부터 CrlId 생성
     *
     * @param id UUID
     * @return CrlId
     * @throws IllegalArgumentException id가 null인 경우
     */
    public static CrlId of(UUID id) {
        if (id == null) {
            throw new IllegalArgumentException("id must not be null");
        }
        return new CrlId(id);
    }

    /**
     * 문자열로부터 CrlId 생성
     *
     * @param id UUID 문자열
     * @return CrlId
     * @throws IllegalArgumentException id가 null이거나 유효하지 않은 UUID 형식인 경우
     */
    public static CrlId of(String id) {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("id must not be blank");
        }
        try {
            return new CrlId(UUID.fromString(id));
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Invalid UUID format: " + id, e);
        }
    }
}
