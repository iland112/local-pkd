package com.smartcoreinc.localpkd.certificatevalidation.domain.model;

import com.smartcoreinc.localpkd.shared.domain.ValueObject;
import lombok.EqualsAndHashCode;
import lombok.Getter;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * TrustPath - 인증서 신뢰 경로 Value Object
 *
 * <p>CSCA (Root) → DSC (Intermediate) → DS (Leaf) 경로를 표현합니다.
 * 인증서 체인의 순서와 계층 구조를 유지합니다.</p>
 *
 * <h3>비즈니스 규칙</h3>
 * <ul>
 *   <li>경로는 최소 1개 이상의 인증서 포함 (CSCA)</li>
 *   <li>경로 순서: [0]=CSCA (Root), [1]=DSC, [2]=DS (Leaf)</li>
 *   <li>최대 깊이: 5 (무한 루프 방지)</li>
 *   <li>첫 번째 인증서는 Self-Signed (CSCA)</li>
 * </ul>
 *
 * <h3>사용 예시</h3>
 * <pre>{@code
 * TrustPath path = TrustPath.of(List.of(cscaId, dscId, dsId));
 * UUID rootId = path.getRoot();  // CSCA ID
 * UUID leafId = path.getLeaf();  // DS ID
 * int depth = path.getDepth();   // 3
 * }</pre>
 *
 * @author SmartCore Inc.
 * @version 1.0
 * @since 2025-10-24
 */
@Getter
@EqualsAndHashCode
public class TrustPath implements ValueObject {

    public static final int MAX_DEPTH = 5;

    private final List<UUID> certificateIds;  // Order: [0]=CSCA, [1]=DSC, [2]=DS

    /**
     * Private constructor with validation
     */
    private TrustPath(List<UUID> certificateIds) {
        validate(certificateIds);
        this.certificateIds = new ArrayList<>(certificateIds);
    }

    private void validate(List<UUID> certificateIds) {
        if (certificateIds == null || certificateIds.isEmpty()) {
            throw new IllegalArgumentException("Certificate IDs must not be empty");
        }

        if (certificateIds.size() > MAX_DEPTH) {
            throw new IllegalArgumentException(
                    String.format("Trust path depth exceeds maximum allowed (%d > %d)",
                            certificateIds.size(), MAX_DEPTH)
            );
        }

        // Check for duplicates (circular reference)
        long distinctCount = certificateIds.stream().distinct().count();
        if (distinctCount != certificateIds.size()) {
            throw new IllegalArgumentException("Trust path contains circular reference (duplicate certificate IDs)");
        }

        // Check for null IDs
        if (certificateIds.stream().anyMatch(id -> id == null)) {
            throw new IllegalArgumentException("Certificate IDs must not contain null");
        }
    }

    // ==================== Static Factory Methods ====================

    /**
     * 인증서 ID 목록으로 TrustPath 생성
     */
    public static TrustPath of(List<UUID> certificateIds) {
        return new TrustPath(certificateIds);
    }

    /**
     * 단일 인증서 (CSCA only) TrustPath 생성
     */
    public static TrustPath ofSingle(UUID cscaId) {
        if (cscaId == null) {
            throw new IllegalArgumentException("CSCA ID must not be null");
        }
        return new TrustPath(List.of(cscaId));
    }

    /**
     * 2개 인증서 (CSCA → DSC) TrustPath 생성
     */
    public static TrustPath ofTwo(UUID cscaId, UUID dscId) {
        if (cscaId == null || dscId == null) {
            throw new IllegalArgumentException("Certificate IDs must not be null");
        }
        return new TrustPath(List.of(cscaId, dscId));
    }

    /**
     * 3개 인증서 (CSCA → DSC → DS) TrustPath 생성
     */
    public static TrustPath ofThree(UUID cscaId, UUID dscId, UUID dsId) {
        if (cscaId == null || dscId == null || dsId == null) {
            throw new IllegalArgumentException("Certificate IDs must not be null");
        }
        return new TrustPath(List.of(cscaId, dscId, dsId));
    }

    // ==================== Business Methods ====================

    /**
     * Root 인증서 ID 반환 (CSCA)
     */
    public UUID getRoot() {
        return certificateIds.get(0);
    }

    /**
     * Leaf 인증서 ID 반환 (마지막 인증서)
     */
    public UUID getLeaf() {
        return certificateIds.get(certificateIds.size() - 1);
    }

    /**
     * 경로 깊이 반환 (인증서 개수)
     */
    public int getDepth() {
        return certificateIds.size();
    }

    /**
     * Immutable 인증서 ID 목록 반환
     */
    public List<UUID> getCertificateIds() {
        return Collections.unmodifiableList(certificateIds);
    }

    /**
     * 특정 인덱스의 인증서 ID 반환
     */
    public UUID getCertificateIdAt(int index) {
        if (index < 0 || index >= certificateIds.size()) {
            throw new IllegalArgumentException(
                    String.format("Index out of bounds: %d (size: %d)", index, certificateIds.size())
            );
        }
        return certificateIds.get(index);
    }

    /**
     * 특정 인증서가 경로에 포함되어 있는지 확인
     */
    public boolean contains(UUID certificateId) {
        return certificateIds.contains(certificateId);
    }

    /**
     * 경로가 단일 인증서인지 확인 (CSCA only, Self-Signed)
     */
    public boolean isSingleCertificate() {
        return certificateIds.size() == 1;
    }

    /**
     * 경로 문자열 표현 (ID 앞 8자만)
     */
    public String toShortString() {
        return certificateIds.stream()
                .map(uuid -> uuid.toString().substring(0, 8))
                .collect(Collectors.joining(" → "));
    }

    @Override
    public String toString() {
        return String.format("TrustPath[depth=%d, path=%s]",
                certificateIds.size(),
                certificateIds.stream()
                        .map(UUID::toString)
                        .collect(Collectors.joining(" → "))
        );
    }
}
