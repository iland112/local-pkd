package com.smartcoreinc.localpkd.fileupload.domain.model;

/**
 * Processing Mode - 파일 처리 방식 Value Object
 *
 * <p>업로드된 파일의 처리 방식을 나타내는 도메인 객체입니다.
 * 두 가지 처리 모드를 지원합니다:</p>
 *
 * <h3>처리 모드</h3>
 * <ul>
 *   <li><strong>AUTO</strong>: 파일 업로드 후 자동으로 파싱, 검증, LDAP 등록까지 처리됩니다.</li>
 *   <li><strong>MANUAL</strong>: 각 단계(파싱, 검증, LDAP 등록)를 사용자가 수동으로 시작할 수 있습니다.</li>
 * </ul>
 *
 * <h3>AUTO 모드 (자동 처리)</h3>
 * <pre>{@code
 * // 파일 업로드 → FileUploadedEvent (AUTO 모드 포함)
 * //   ↓
 * // FileUploadEventHandler가 자동으로 파싱 시작
 * //   ↓
 * // FileParsingCompletedEvent (자동)
 * //   ↓
 * // CertificateValidationEventHandler가 자동으로 검증 시작
 * //   ↓
 * // CertificatesValidatedEvent (자동)
 * //   ↓
 * // UploadToLdapEventHandler가 자동으로 LDAP 등록 시작
 * }</pre>
 *
 * <h3>MANUAL 모드 (수동 처리)</h3>
 * <pre>{@code
 * // 파일 업로드 → FileUploadedEvent (MANUAL 모드 포함)
 * //   ↓
 * // UI에서 "파싱 시작" 버튼 클릭 (사용자 액션)
 * //   ↓
 * // POST /api/processing/parse/{uploadId}
 * //   ↓
 * // FileParsingCompletedEvent (수동 모드이므로 다음 단계 자동 시작 안 함)
 * //   ↓
 * // UI에서 "검증 시작" 버튼 클릭 (사용자 액션)
 * //   ↓
 * // POST /api/processing/validate/{uploadId}
 * //   ↓
 * // ... (LDAP 등록도 동일)
 * }</pre>
 *
 * <h3>사용 예시</h3>
 * <pre>{@code
 * // ProcessingMode 생성
 * ProcessingMode autoMode = ProcessingMode.AUTO;
 * ProcessingMode manualMode = ProcessingMode.MANUAL;
 *
 * // Mode 확인
 * if (autoMode.isAuto()) {
 *     // 자동으로 파이프라인 시작
 * } else if (autoMode.isManual()) {
 *     // 사용자 입력 대기
 * }
 *
 * // Mode 설명 조회
 * String description = autoMode.getDescription();
 * // → "업로드 후 자동으로 파싱, 검증, LDAP 등록"
 * }</pre>
 *
 * <h3>비즈니스 규칙</h3>
 * <ul>
 *   <li>모든 업로드는 정확히 하나의 ProcessingMode를 가져야 함</li>
 *   <li>AUTO 모드: 이벤트 핸들러가 자동으로 다음 단계 트리거</li>
 *   <li>MANUAL 모드: 사용자가 각 단계를 수동으로 트리거</li>
 *   <li>한번 설정되면 변경 불가</li>
 * </ul>
 *
 * @author SmartCore Inc.
 * @version 1.0
 * @since 2025-10-24
 */
public enum ProcessingMode {
    /**
     * AUTO 모드 - 자동 처리
     *
     * <p>파일 업로드 후 다음 단계가 자동으로 실행됩니다:</p>
     * <ol>
     *   <li>파일 파싱 (PARSING)</li>
     *   <li>인증서 검증 (VALIDATION)</li>
     *   <li>LDAP 서버 등록 (LDAP_SAVING)</li>
     * </ol>
     *
     * <p>프로덕션 환경에서 권장됩니다.</p>
     */
    AUTO("자동 처리", "업로드 후 자동으로 파싱, 검증, LDAP 등록"),

    /**
     * MANUAL 모드 - 수동 처리
     *
     * <p>각 단계를 사용자가 수동으로 진행합니다:</p>
     * <ol>
     *   <li>사용자가 "파싱 시작" 버튼 클릭</li>
     *   <li>파싱 완료 후 "검증 시작" 버튼 클릭</li>
     *   <li>검증 완료 후 "LDAP 등록" 버튼 클릭</li>
     * </ol>
     *
     * <p>개발/테스트 환경에서 권장되며, 중간 결과 검토가 필요한 경우 사용합니다.</p>
     */
    MANUAL("수동 처리", "각 단계를 사용자가 수동으로 진행");

    private final String displayName;
    private final String description;

    ProcessingMode(String displayName, String description) {
        this.displayName = displayName;
        this.description = description;
    }

    /**
     * 표시 이름 조회
     *
     * @return 사용자에게 표시할 모드명
     */
    public String getDisplayName() {
        return displayName;
    }

    /**
     * 상세 설명 조회
     *
     * @return 모드에 대한 상세 설명
     */
    public String getDescription() {
        return description;
    }

    /**
     * AUTO 모드 여부 확인
     *
     * @return AUTO 모드면 true
     */
    public boolean isAuto() {
        return this == AUTO;
    }

    /**
     * MANUAL 모드 여부 확인
     *
     * @return MANUAL 모드면 true
     */
    public boolean isManual() {
        return this == MANUAL;
    }
}
