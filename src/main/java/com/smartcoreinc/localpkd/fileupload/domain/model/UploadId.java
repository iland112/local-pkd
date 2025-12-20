package com.smartcoreinc.localpkd.fileupload.domain.model;

import io.github.wimdeblauwe.jpearl.AbstractEntityId;

import java.util.UUID;

/**
 * Upload ID - Type-safe entity identifier for UploadedFile Aggregate
 *
 * <p>JPearl 기반의 타입 안전한 엔티티 식별자입니다.
 * UUID를 내부적으로 사용하며, JPA {@code @EmbeddedId}로 사용됩니다.</p>
 *
 * <h3>특징</h3>
 * <ul>
 *   <li>Type-safe: Long 타입 ID와 혼동 방지</li>
 *   <li>Immutable: 생성 후 변경 불가</li>
 *   <li>UUID 기반: 분산 환경에서 고유성 보장</li>
 *   <li>JPA 지원: @EmbeddedId로 사용 가능</li>
 * </ul>
 *
 * <h3>사용 예시 - Entity에서 사용</h3>
 * <pre>{@code
 * @Entity
 * @Table(name = "uploaded_file")
 * public class UploadedFile extends AggregateRoot<UploadId> {
 *
 *     @EmbeddedId
 *     private UploadId id;
 *
 *     // Constructor
 *     public UploadedFile(UploadId id, ...) {
 *         this.id = id;
 *     }
 *
 *     @Override
 *     public UploadId getId() {
 *         return id;
 *     }
 * }
 * }</pre>
 *
 * <h3>사용 예시 - 신규 ID 생성</h3>
 * <pre>{@code
 * // 1. 신규 UUID 기반 ID 생성
 * UploadId uploadId = UploadId.newId();
 *
 * // 2. 기존 UUID로 ID 생성 (Repository에서 조회 시)
 * UploadId uploadId = new UploadId(existingUuid);
 *
 * // 3. 문자열에서 ID 생성
 * UploadId uploadId = UploadId.of("550e8400-e29b-41d4-a716-446655440000");
 * }</pre>
 *
 * <h3>사용 예시 - Repository 메서드</h3>
 * <pre>{@code
 * public interface UploadedFileRepository extends JpaRepository<UploadedFile, UploadId> {
 *     Optional<UploadedFile> findById(UploadId id);
 *     void deleteById(UploadId id);
 * }
 * }</pre>
 *
 * <h3>장점</h3>
 * <ol>
 *   <li><b>타입 안전성</b>: 다른 엔티티 ID와 혼동 방지
 *       <pre>{@code
 *       // 컴파일 오류 방지
 *       UploadId uploadId = UploadId.newId();
 *       ParseId parseId = ParseId.newId();
 *       repository.findById(parseId);  // ❌ 컴파일 오류!
 *       }</pre>
 *   </li>
 *   <li><b>불변성</b>: 실수로 ID 변경하는 것 방지</li>
 *   <li><b>명확한 의도</b>: 코드만 봐도 어떤 엔티티의 ID인지 알 수 있음</li>
 * </ol>
 *
 * <h3>Database Schema</h3>
 * <pre>
 * CREATE TABLE uploaded_file (
 *     id UUID PRIMARY KEY,
 *     -- other columns
 * );
 * </pre>
 *
 * @author SmartCore Inc.
 * @version 1.0
 * @since 2025-10-18
 * @see io.github.wimdeblauwe.jpearl.AbstractEntityId
 * @see com.smartcoreinc.localpkd.shared.domain.AggregateRoot
 */
public class UploadId extends AbstractEntityId<UUID> {

    /**
     * JPA 전용 기본 생성자 (protected)
     *
     * <p>JPA에서 엔티티를 인스턴스화할 때만 사용됩니다.
     * 직접 호출하지 마세요.</p>
     */
    protected UploadId() {
        // JPA only
    }

    /**
     * UUID 기반 Upload ID 생성자
     *
     * @param id UUID 값
     */
    public UploadId(UUID id) {
        super(id);
    }

    /**
     * 신규 Upload ID 생성 (랜덤 UUID 기반)
     *
     * <p>새로운 업로드를 생성할 때 사용합니다.</p>
     *
     * <h4>예시</h4>
     * <pre>{@code
     * UploadId uploadId = UploadId.newId();
     * UploadedFile file = new UploadedFile(uploadId, fileName, ...);
     * }</pre>
     *
     * @return 신규 Upload ID
     */
    public static UploadId newId() {
        return new UploadId(UUID.randomUUID());
    }

    /**
     * 문자열로부터 Upload ID 생성
     *
     * <p>API 요청이나 외부 시스템에서 받은 UUID 문자열을
     * Upload ID로 변환할 때 사용합니다.</p>
     *
     * <h4>예시</h4>
     * <pre>{@code
     * String idString = "550e8400-e29b-41d4-a716-446655440000";
     * UploadId uploadId = UploadId.of(idString);
     * }</pre>
     *
     * @param id UUID 문자열
     * @return Upload ID
     * @throws IllegalArgumentException UUID 형식이 잘못된 경우
     */
    public static UploadId of(String id) {
        return new UploadId(UUID.fromString(id));
    }

    /**
     * 내부 UUID 값을 반환합니다.
     *
     * @return UUID 값
     */
    public UUID toUUID() {
        return super.getId();
    }
}