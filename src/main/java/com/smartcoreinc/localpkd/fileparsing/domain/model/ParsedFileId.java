package com.smartcoreinc.localpkd.fileparsing.domain.model;

import io.github.wimdeblauwe.jpearl.AbstractEntityId;

import jakarta.persistence.Embeddable;
import java.util.UUID;

/**
 * ParsedFileId - 타입 안전한 파싱 파일 엔티티 ID
 *
 * <p><b>JPearl Pattern</b>: UUID 기반 타입 안전 엔티티 ID</p>
 *
 * <p><b>사용 예시</b>:</p>
 * <pre>
 * // 신규 ID 생성
 * ParsedFileId id = ParsedFileId.newId();
 *
 * // UUID로부터 생성
 * ParsedFileId id = ParsedFileId.of(uuid);
 *
 * // 문자열로부터 생성
 * ParsedFileId id = ParsedFileId.of("550e8400-e29b-41d4-a716-446655440000");
 *
 * // 타입 안전성 보장
 * UploadId uploadId = UploadId.newId();
 * ParsedFileId parsedId = ParsedFileId.newId();
 * repository.findById(uploadId);  // ❌ 컴파일 오류!
 * repository.findById(parsedId);  // ✅ OK
 * </pre>
 *
 * @see io.github.wimdeblauwe.jpearl.AbstractEntityId
 */
@Embeddable
public class ParsedFileId extends AbstractEntityId<UUID> {

    /**
     * JPA용 기본 생성자 (protected)
     */
    protected ParsedFileId() {
    }

    /**
     * UUID로부터 ParsedFileId 생성
     *
     * @param id UUID
     */
    public ParsedFileId(UUID id) {
        super(id);
    }

    /**
     * 새로운 ParsedFileId 생성 (랜덤 UUID)
     *
     * @return 새로운 ParsedFileId
     */
    public static ParsedFileId newId() {
        return new ParsedFileId(UUID.randomUUID());
    }

    /**
     * UUID로부터 ParsedFileId 생성
     *
     * @param id UUID
     * @return ParsedFileId
     */
    public static ParsedFileId of(UUID id) {
        if (id == null) {
            throw new IllegalArgumentException("id must not be null");
        }
        return new ParsedFileId(id);
    }

    /**
     * 문자열로부터 ParsedFileId 생성
     *
     * @param id UUID 문자열
     * @return ParsedFileId
     * @throws IllegalArgumentException id가 null이거나 유효하지 않은 UUID 형식인 경우
     */
    public static ParsedFileId of(String id) {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("id must not be blank");
        }
        try {
            return new ParsedFileId(UUID.fromString(id));
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Invalid UUID format: " + id, e);
        }
    }
}
