package com.smartcoreinc.localpkd.common.enums;

import lombok.Getter;

/**
 * 파일 업로드 상태
 *
 * ICAO PKD 파일 업로드 및 처리 과정의 상태를 나타냅니다.
 *
 * @author SmartCore Inc.
 * @version 1.0
 */
@Getter
public enum UploadStatus {

    /**
     * 파일 수신 완료
     * 파일이 서버에 업로드되었으나 아직 검증하지 않음
     */
    RECEIVED(
        "파일 수신 완료",
        "업로드된 파일을 검증 대기 중입니다.",
        "info",
        false,
        false
    ),

    /**
     * 파일 검증 중
     * 파일명, 크기, 형식 등 기본 검증 수행 중
     */
    VALIDATING(
        "파일 검증 중",
        "파일 형식과 무결성을 검증하고 있습니다.",
        "info",
        false,
        false
    ),

    /**
     * 체크섬 검증 중
     * SHA-1 체크섬 계산 및 ICAO 공식 체크섬과 비교 중
     */
    CHECKSUM_VALIDATING(
        "체크섬 검증 중",
        "파일의 SHA-1 체크섬을 계산하고 검증하고 있습니다.",
        "info",
        false,
        false
    ),

    /**
     * 체크섬 불일치
     * 계산된 체크섬이 ICAO 공식 체크섬과 일치하지 않음
     */
    CHECKSUM_INVALID(
        "체크섬 불일치",
        "파일 무결성 검증에 실패했습니다. ICAO 공식 체크섬과 일치하지 않습니다.",
        "error",
        true,
        true
    ),

    /**
     * 중복 파일 감지
     * 동일한 버전의 파일이 이미 존재함
     */
    DUPLICATE_DETECTED(
        "중복 파일 감지",
        "동일한 버전의 파일이 이미 시스템에 존재합니다.",
        "warning",
        true,
        false
    ),

    /**
     * 이전 버전 감지
     * 업로드된 파일이 현재 시스템보다 오래된 버전
     */
    OLDER_VERSION(
        "이전 버전 감지",
        "업로드된 파일이 현재 시스템의 버전보다 이전 버전입니다.",
        "warning",
        true,
        false
    ),

    /**
     * 파싱 진행 중
     * LDIF 또는 ML 파일 파싱 수행 중
     */
    PARSING(
        "파싱 진행 중",
        "파일 내용을 분석하고 파싱하고 있습니다.",
        "info",
        false,
        false
    ),

    /**
     * 데이터 저장 중
     * OpenLDAP 및 PostgreSQL에 데이터 저장 중
     */
    STORING(
        "데이터 저장 중",
        "파싱된 데이터를 데이터베이스에 저장하고 있습니다.",
        "info",
        false,
        false
    ),

    /**
     * 처리 완료
     * 모든 단계가 성공적으로 완료됨
     */
    SUCCESS(
        "처리 완료",
        "파일 업로드 및 처리가 성공적으로 완료되었습니다.",
        "success",
        true,
        false
    ),

    /**
     * 처리 실패
     * 처리 과정 중 오류 발생
     */
    FAILED(
        "처리 실패",
        "파일 처리 중 오류가 발생했습니다.",
        "error",
        true,
        true
    ),

    /**
     * 롤백됨
     * 오류 발생으로 인해 변경사항이 롤백됨
     */
    ROLLBACK(
        "롤백됨",
        "오류로 인해 변경사항이 롤백되었습니다.",
        "error",
        true,
        true
    ),

    /**
     * 부분 성공
     * 일부 데이터만 성공적으로 처리됨
     */
    PARTIAL_SUCCESS(
        "부분 성공",
        "일부 데이터만 성공적으로 처리되었습니다.",
        "warning",
        true,
        false
    );

    /**
     * 상태 제목
     */
    private final String title;

    /**
     * 상태 설명
     */
    private final String description;

    /**
     * UI 표시 타입 (info, success, warning, error)
     */
    private final String displayType;

    /**
     * 최종 상태 여부 (더 이상 상태 변경 없음)
     */
    private final boolean isFinal;

    /**
     * 오류 상태 여부
     */
    private final boolean isError;

    UploadStatus(String title, String description, String displayType,
                 boolean isFinal, boolean isError) {
        this.title = title;
        this.description = description;
        this.displayType = displayType;
        this.isFinal = isFinal;
        this.isError = isError;
    }

    /**
     * 진행 중 상태 여부
     *
     * @return 진행 중 여부
     */
    public boolean isInProgress() {
        return !isFinal;
    }

    /**
     * 성공 상태 여부
     *
     * @return 성공 여부
     */
    public boolean isSuccess() {
        return this == SUCCESS || this == PARTIAL_SUCCESS;
    }

    /**
     * 경고 상태 여부
     *
     * @return 경고 여부
     */
    public boolean isWarning() {
        return "warning".equals(displayType);
    }

    /**
     * UI 아이콘 클래스 반환
     *
     * @return Font Awesome 아이콘 클래스
     */
    public String getIconClass() {
        return switch (displayType) {
            case "success" -> "fas fa-check-circle text-green-500";
            case "error" -> "fas fa-times-circle text-red-500";
            case "warning" -> "fas fa-exclamation-triangle text-yellow-500";
            default -> "fas fa-info-circle text-blue-500";
        };
    }

    /**
     * UI 배지 색상 클래스 반환
     *
     * @return Tailwind CSS 배지 클래스
     */
    public String getBadgeClass() {
        return switch (displayType) {
            case "success" -> "badge badge-success";
            case "error" -> "badge badge-error";
            case "warning" -> "badge badge-warning";
            default -> "badge badge-info";
        };
    }

    /**
     * 진행률 퍼센트 반환 (대략적)
     *
     * @return 진행률 (0-100)
     */
    public int getProgressPercentage() {
        return switch (this) {
            case RECEIVED -> 10;
            case VALIDATING -> 20;
            case CHECKSUM_VALIDATING -> 30;
            case CHECKSUM_INVALID, DUPLICATE_DETECTED, OLDER_VERSION -> 30;
            case PARSING -> 60;
            case STORING -> 80;
            case SUCCESS, PARTIAL_SUCCESS -> 100;
            case FAILED, ROLLBACK -> 0;
        };
    }

    /**
     * UI 표시용 이름 반환
     *
     * @return 표시 이름
     */
    public String getDisplayName() {
        return this.title;
    }

    /**
     * 상태 전이 가능 여부 확인
     *
     * @param nextStatus 다음 상태
     * @return 전이 가능 여부
     */
    public boolean canTransitionTo(UploadStatus nextStatus) {
        // 최종 상태에서는 전이 불가
        if (this.isFinal) {
            return false;
        }

        // 정상적인 순서대로 전이
        return switch (this) {
            case RECEIVED -> nextStatus == VALIDATING || nextStatus == FAILED;
            case VALIDATING -> nextStatus == CHECKSUM_VALIDATING ||
                               nextStatus == DUPLICATE_DETECTED ||
                               nextStatus == FAILED;
            case CHECKSUM_VALIDATING -> nextStatus == PARSING ||
                                        nextStatus == CHECKSUM_INVALID ||
                                        nextStatus == FAILED;
            case PARSING -> nextStatus == STORING ||
                            nextStatus == FAILED ||
                            nextStatus == PARTIAL_SUCCESS;
            case STORING -> nextStatus == SUCCESS ||
                            nextStatus == PARTIAL_SUCCESS ||
                            nextStatus == FAILED ||
                            nextStatus == ROLLBACK;
            default -> false;
        };
    }

    @Override
    public String toString() {
        return String.format("%s [%s] - %s", title, name(), description);
    }
}
