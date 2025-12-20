package com.smartcoreinc.localpkd.ldapintegration.domain.port;

import com.smartcoreinc.localpkd.ldapintegration.domain.model.DistinguishedName;
import com.smartcoreinc.localpkd.ldapintegration.domain.model.LdapAttributes;
import com.smartcoreinc.localpkd.ldapintegration.domain.model.LdapCertificateEntry;
import com.smartcoreinc.localpkd.ldapintegration.domain.model.LdapCrlEntry;

import java.util.List;

/**
 * LdapUploadService - LDAP 서버에 인증서/CRL 데이터 업로드 Port (Hexagonal Architecture)
 *
 * <p><b>Hexagonal Architecture Port</b>:</p>
 * <ul>
 *   <li>도메인에서 인프라스트럭처로의 의존성 역전</li>
 *   <li>Spring LDAP 또는 다른 LDAP 라이브러리로 구현 가능</li>
 *   <li>테스트 시 Mock 구현으로 대체 가능</li>
 * </ul>
 *
 * <h3>책임</h3>
 * <ul>
 *   <li>LDAP 서버에 인증서 엔트리 추가/업데이트</li>
 *   <li>LDAP 서버에 CRL 엔트리 추가/업데이트</li>
 *   <li>배치 업로드 (대량 데이터 일괄 처리)</li>
 *   <li>LDAP 엔트리 삭제</li>
 *   <li>업로드 결과 추적</li>
 * </ul>
 *
 * <h3>LDAP 엔트리 생명주기</h3>
 * <pre>{@code
 * 1. Certificate/CRL Aggregate 생성 (Phase 11)
 * 2. LdapCertificateEntry/LdapCrlEntry 변환
 * 3. LDAP 속성 매핑 (LdapAttributes)
 * 4. LdapUploadService.add() 또는 update()
 * 5. LDAP 서버에 저장
 * 6. LdapCertificateEntry.markAsSynced() 호출
 * 7. 로컬 DB에 동기화 상태 업데이트
 * }</pre>
 *
 * <h3>오류 처리</h3>
 * <ul>
 *   <li>LDAP 연결 실패: LdapConnectionException</li>
 *   <li>중복된 DN: LdapEntryAlreadyExistsException</li>
 *   <li>DN 형식 오류: InvalidLdapDnException</li>
 *   <li>속성 오류: LdapAttributeException</li>
 * </ul>
 *
 * <h3>사용 예시</h3>
 * <pre>{@code
 * // 단일 인증서 업로드
 * Certificate certificate = ...;
 * LdapCertificateEntry entry = LdapCertificateEntry.createFromCertificate(certificate, CSCA);
 * LdapAttributes attrs = LdapAttributes.forCertificate(
 *     "CSCA-KOREA", "KR", "2025-10-25T10:30:00", base64cert
 * );
 *
 * UploadResult result = ldapUploadService.addCertificate(entry, attrs);
 * if (result.isSuccess()) {
 *     entry.markAsSynced();
 *     certificateRepository.save(entry);
 * }
 *
 * // 배치 업로드
 * List<LdapCertificateEntry> entries = ...;
 * BatchUploadResult batchResult = ldapUploadService.addCertificatesBatch(entries);
 * log.info("Successfully uploaded: {}", batchResult.getSuccessCount());
 * log.warn("Failed uploads: {}", batchResult.getFailedCount());
 * }</pre>
 *
 * @author SmartCore Inc.
 * @version 1.0
 * @since 2025-10-25
 */
public interface LdapUploadService {

    /**
     * LDAP 서버에 인증서 엔트리 추가
     *
     * <p>새로운 인증서를 LDAP 디렉토리에 추가합니다.</p>
     *
     * @param entry LDAP 인증서 엔트리
     * @param attributes 인증서 속성들
     * @return 업로드 결과
     * @throws LdapUploadException 업로드 실패 시
     */
    UploadResult addCertificate(LdapCertificateEntry entry, LdapAttributes attributes);

    /**
     * LDAP 서버의 인증서 엔트리 업데이트
     *
     * <p>기존 인증서를 업데이트합니다.
     * DN이 동일하면 기존 엔트리를 덮어씁니다.</p>
     *
     * @param entry LDAP 인증서 엔트리
     * @param attributes 인증서 속성들
     * @return 업로드 결과
     * @throws LdapUploadException 업로드 실패 시
     */
    UploadResult updateCertificate(LdapCertificateEntry entry, LdapAttributes attributes);

    /**
     * LDAP 서버에 인증서 엔트리 추가 또는 업데이트
     *
     * <p>DN이 존재하면 업데이트, 없으면 추가합니다.</p>
     *
     * @param entry LDAP 인증서 엔트리
     * @param attributes 인증서 속성들
     * @return 업로드 결과
     * @throws LdapUploadException 업로드 실패 시
     */
    UploadResult addOrUpdateCertificate(LdapCertificateEntry entry, LdapAttributes attributes);

    /**
     * 배치로 여러 인증서를 LDAP 서버에 추가
     *
     * <p>대량의 인증서를 효율적으로 업로드합니다.
     * 개별 실패가 다른 항목에 영향을 주지 않습니다.</p>
     *
     * @param entries LDAP 인증서 엔트리 리스트
     * @return 배치 업로드 결과
     */
    BatchUploadResult addCertificatesBatch(List<LdapCertificateEntry> entries);

    /**
     * LDAP 서버에 CRL 엔트리 추가
     *
     * <p>새로운 CRL을 LDAP 디렉토리에 추가합니다.</p>
     *
     * @param entry LDAP CRL 엔트리
     * @param attributes CRL 속성들
     * @return 업로드 결과
     * @throws LdapUploadException 업로드 실패 시
     */
    UploadResult addCrl(LdapCrlEntry entry, LdapAttributes attributes);

    /**
     * LDAP 서버의 CRL 엔트리 업데이트
     *
     * <p>기존 CRL을 업데이트합니다.</p>
     *
     * @param entry LDAP CRL 엔트리
     * @param attributes CRL 속성들
     * @return 업로드 결과
     * @throws LdapUploadException 업로드 실패 시
     */
    UploadResult updateCrl(LdapCrlEntry entry, LdapAttributes attributes);

    /**
     * 배치로 여러 CRL을 LDAP 서버에 추가
     *
     * @param entries LDAP CRL 엔트리 리스트
     * @return 배치 업로드 결과
     */
    BatchUploadResult addCrlsBatch(List<LdapCrlEntry> entries);

    /**
     * LDAP 서버에서 엔트리 삭제
     *
     * <p>주의: 삭제 후 복구 불가능합니다.</p>
     *
     * @param dn 삭제할 엔트리의 Distinguished Name
     * @return 삭제 성공 여부
     * @throws LdapUploadException 삭제 실패 시
     */
    boolean deleteEntry(DistinguishedName dn);

    /**
     * 특정 경로 아래의 모든 엔트리 삭제
     *
     * <p>위험한 작업입니다. 신중하게 사용하세요.</p>
     *
     * @param baseDn 삭제할 기본 DN
     * @return 삭제된 엔트리 개수
     * @throws LdapUploadException 삭제 실패 시
     */
    int deleteSubtree(DistinguishedName baseDn);

    /**
     * 업로드 결과 정보
     */
    interface UploadResult {
        /**
         * 업로드 성공 여부
         */
        boolean isSuccess();

        /**
         * 업로드된 DN
         */
        DistinguishedName getUploadedDn();

        /**
         * 성공 메시지
         */
        String getMessage();

        /**
         * 실패 이유 (성공 시 null)
         */
        String getErrorMessage();

        /**
         * 업로드 완료 시간 (밀리초)
         */
        long getDurationMillis();

        /**
         * 업로드된 바이트 수
         */
        long getUploadedBytes();
    }

    /**
     * 배치 업로드 결과 정보
     */
    interface BatchUploadResult {
        /**
         * 총 요청 개수
         */
        int getTotalCount();

        /**
         * 성공 개수
         */
        int getSuccessCount();

        /**
         * 실패 개수
         */
        int getFailedCount();

        /**
         * 성공률 (0-100%)
         */
        double getSuccessRate();

        /**
         * 개별 실패 항목들
         */
        List<FailedEntry> getFailedEntries();

        /**
         * 배치 처리 완료 시간 (밀리초)
         */
        long getDurationMillis();

        /**
         * 업로드된 총 바이트 수
         */
        long getTotalUploadedBytes();

        /**
         * 실패 항목 정보
         */
        interface FailedEntry {
            DistinguishedName getDn();
            String getErrorMessage();
            Exception getException();
        }
    }

    /**
     * LDAP 업로드 예외
     */
    class LdapUploadException extends RuntimeException {
        public LdapUploadException(String message) {
            super(message);
        }

        public LdapUploadException(String message, Throwable cause) {
            super(message, cause);
        }
    }

    /**
     * LDAP 엔트리 이미 존재 예외
     */
    class LdapEntryAlreadyExistsException extends LdapUploadException {
        private final DistinguishedName dn;

        public LdapEntryAlreadyExistsException(DistinguishedName dn, String message) {
            super(message);
            this.dn = dn;
        }

        public DistinguishedName getDn() {
            return dn;
        }
    }
}
