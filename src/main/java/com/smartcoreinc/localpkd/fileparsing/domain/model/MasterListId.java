package com.smartcoreinc.localpkd.fileparsing.domain.model;

import io.github.wimdeblauwe.jpearl.AbstractEntityId;

import java.util.UUID;

/**
 * MasterListId - Unique identifier for Master List aggregate
 *
 * <p>JPearl 기반의 타입 안전한 엔티티 식별자입니다.
 * UUID를 내부적으로 사용하며, JPA {@code @EmbeddedId}로 사용됩니다.</p>
 *
 * @author SmartCore Inc.
 * @version 1.0
 * @since 2025-11-27
 */
public class MasterListId extends AbstractEntityId<UUID> {

    /**
     * JPA 전용 기본 생성자 (protected)
     *
     * <p>JPA에서 엔티티를 인스턴스화할 때만 사용됩니다.
     * 직접 호출하지 마세요.</p>
     */
    protected MasterListId() {
        // JPA only
    }

    /**
     * UUID 기반 MasterList ID 생성자
     *
     * @param id UUID 값
     */
    public MasterListId(UUID id) {
        super(id);
    }

    /**
     * 신규 MasterList ID 생성 (랜덤 UUID 사용)
     *
     * @return 새로운 MasterListId
     */
    public static MasterListId newId() {
        return new MasterListId(UUID.randomUUID());
    }

    /**
     * 문자열에서 MasterList ID 생성
     *
     * @param id UUID 문자열 (예: "550e8400-e29b-41d4-a716-446655440000")
     * @return MasterListId
     * @throws IllegalArgumentException UUID 형식이 잘못된 경우
     */
    public static MasterListId of(String id) {
        return new MasterListId(UUID.fromString(id));
    }
}
