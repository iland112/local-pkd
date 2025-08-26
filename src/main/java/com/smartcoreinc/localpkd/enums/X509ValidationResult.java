package com.smartcoreinc.localpkd.enums;

public enum X509ValidationResult {
    // 성공
    SUCCESS("유효성 검증 성공"),

    // 실패
    FAILURE_EXPIRED("인증서 만료 또는 유효하지 않은 기간"),
    FAILURE_SIGNATURE("서명 검증 실패"),
    FAILURE_BASIC_CONSTRAINTS("CA 인증서가 아님"),
    FAILURE_KEY_USAGE("keyCertSign 키 사용이 활성화되지 않음"),
    FAILURE_SUBJECT_COUNTRY_CODE("Subject DN에 국가 코드(C)가 없음"),
    FAILURE_UNKNOWN("알 수 없는 오류");

    private final String description;

    X509ValidationResult(String description) {
        this.description = description;
    }

    public String getDescription() {
        return description;
    }
}
