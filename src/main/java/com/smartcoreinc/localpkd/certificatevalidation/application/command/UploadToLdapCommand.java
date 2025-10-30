package com.smartcoreinc.localpkd.certificatevalidation.application.command;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * UploadToLdapCommand - LDAP 업로드 명령 (CQRS Write Side)
 *
 * <p><b>목적</b>: 검증된 인증서/CRL을 LDAP 디렉토리에 업로드하기 위한 명령입니다.</p>
 *
 * <p><b>Use Case</b>: UploadToLdapUseCase에서 사용됩니다.</p>
 *
 * <p><b>파라미터</b>:
 * <ul>
 *   <li>uploadId: 파일 업로드 ID (File Upload Context 참조)</li>
 *   <li>certificateIds: 업로드할 인증서 ID 목록</li>
 *   <li>baseDn: LDAP Base DN</li>
 *   <li>isBatch: 배치 처리 여부</li>
 * </ul>
 * </p>
 *
 * <p><b>사용 예시</b>:</p>
 * <pre>{@code
 * UploadToLdapCommand command = UploadToLdapCommand.builder()
 *     .uploadId(fileUploadId)
 *     .certificateId(certId1)
 *     .certificateId(certId2)
 *     .baseDn("dc=ldap,dc=smartcoreinc,dc=com")
 *     .isBatch(true)
 *     .build();
 *
 * UploadToLdapResponse response = uploadToLdapUseCase.execute(command);
 * }</pre>
 *
 * @author SmartCore Inc.
 * @version 1.0
 * @since 2025-10-30 (Phase 17 Task 1.6)
 */
public class UploadToLdapCommand {

    private final UUID uploadId;           // File Upload ID
    private final List<UUID> certificateIds;  // Certificate IDs
    private final String baseDn;           // LDAP Base DN
    private final boolean isBatch;         // Batch processing flag

    public UploadToLdapCommand(UUID uploadId, List<UUID> certificateIds, String baseDn, boolean isBatch) {
        this.uploadId = Objects.requireNonNull(uploadId, "uploadId must not be null");
        this.certificateIds = Objects.requireNonNull(certificateIds, "certificateIds must not be null");
        this.baseDn = Objects.requireNonNull(baseDn, "baseDn must not be null");
        this.isBatch = isBatch;
    }

    public UUID getUploadId() { return uploadId; }
    public List<UUID> getCertificateIds() { return new ArrayList<>(certificateIds); }
    public String getBaseDn() { return baseDn; }
    public boolean isBatch() { return isBatch; }
    public int getCertificateCount() { return certificateIds.size(); }

    /**
     * Builder 클래스
     */
    public static class Builder {
        private UUID uploadId;
        private final List<UUID> certificateIds = new ArrayList<>();
        private String baseDn;
        private boolean isBatch = false;

        public Builder uploadId(UUID uploadId) {
            this.uploadId = uploadId;
            return this;
        }

        public Builder certificateId(UUID certificateId) {
            this.certificateIds.add(certificateId);
            return this;
        }

        public Builder certificateIds(List<UUID> certificateIds) {
            this.certificateIds.addAll(certificateIds);
            return this;
        }

        public Builder baseDn(String baseDn) {
            this.baseDn = baseDn;
            return this;
        }

        public Builder isBatch(boolean isBatch) {
            this.isBatch = isBatch;
            return this;
        }

        public UploadToLdapCommand build() {
            return new UploadToLdapCommand(uploadId, certificateIds, baseDn, isBatch);
        }
    }

    public static Builder builder() {
        return new Builder();
    }

    @Override
    public String toString() {
        return "UploadToLdapCommand{" +
                "uploadId=" + uploadId +
                ", certificateIds=" + certificateIds.size() +
                ", baseDn='" + baseDn + '\'' +
                ", isBatch=" + isBatch +
                '}';
    }
}
