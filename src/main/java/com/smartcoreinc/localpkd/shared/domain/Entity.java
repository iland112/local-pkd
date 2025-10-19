package com.smartcoreinc.localpkd.shared.domain;

import java.util.Objects;

/**
 * Entity 베이스 클래스
 *
 * <p>DDD의 Entity 패턴을 구현하는 베이스 클래스입니다.
 * Entity는 식별자(ID)로 동등성을 판단하는 객체입니다.</p>
 *
 * <h3>Entity vs Value Object</h3>
 * <table border="1">
 *   <tr>
 *     <th>특징</th>
 *     <th>Entity</th>
 *     <th>Value Object</th>
 *   </tr>
 *   <tr>
 *     <td>식별성</td>
 *     <td>ID로 구분</td>
 *     <td>속성으로 구분</td>
 *   </tr>
 *   <tr>
 *     <td>가변성</td>
 *     <td>변경 가능 (상태 변화)</td>
 *     <td>불변</td>
 *   </tr>
 *   <tr>
 *     <td>생명주기</td>
 *     <td>있음</td>
 *     <td>없음</td>
 *   </tr>
 * </table>
 *
 * <h3>특징</h3>
 * <ul>
 *   <li><b>식별성(Identity)</b>: ID가 같으면 같은 객체</li>
 *   <li><b>연속성(Continuity)</b>: 생명주기 동안 ID 유지</li>
 *   <li><b>변경 가능성</b>: 속성은 변경될 수 있음</li>
 * </ul>
 *
 * <h3>사용 예시</h3>
 * <pre>{@code
 * @Entity
 * @Table(name = "uploaded_files")
 * public class UploadedFile extends com.smartcoreinc.localpkd.shared.domain.Entity<UploadId> {
 *
 *     @EmbeddedId
 *     private UploadId id;
 *
 *     @Embedded
 *     private FileName fileName;
 *
 *     @Enumerated(EnumType.STRING)
 *     private UploadStatus status;
 *
 *     protected UploadedFile() {}
 *
 *     public UploadedFile(FileName fileName) {
 *         this.id = UploadId.random();
 *         this.fileName = fileName;
 *         this.status = UploadStatus.RECEIVED;
 *     }
 *
 *     // Getter
 *     @Override
 *     public UploadId getId() {
 *         return id;
 *     }
 *
 *     // Business methods
 *     public void markAsReceived() {
 *         this.status = UploadStatus.RECEIVED;
 *     }
 * }
 * }</pre>
 *
 * @param <ID> Entity의 식별자 타입 (JPearl ID 권장)
 * @author SmartCore Inc.
 * @version 1.0
 * @since 2025-10-18
 */
public abstract class Entity<ID> {

    /**
     * Entity의 고유 식별자
     *
     * @return Entity ID
     */
    public abstract ID getId();

    /**
     * 동등성 비교
     *
     * <p>ID가 같으면 같은 Entity로 간주합니다.
     * 속성 값이 다르더라도 ID가 같으면 동일한 객체입니다.</p>
     *
     * @param o 비교 대상 객체
     * @return ID가 같으면 true
     */
    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }

        Entity<?> entity = (Entity<?>) o;
        return getId() != null && getId().equals(entity.getId());
    }

    /**
     * 해시코드
     *
     * <p>ID의 해시코드를 반환합니다.</p>
     *
     * @return ID의 해시코드
     */
    @Override
    public int hashCode() {
        return Objects.hash(getId());
    }

    /**
     * 문자열 표현
     *
     * @return Entity 정보 문자열
     */
    @Override
    public String toString() {
        return getClass().getSimpleName() + "{id=" + getId() + "}";
    }
}
