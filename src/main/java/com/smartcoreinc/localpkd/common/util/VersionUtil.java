package com.smartcoreinc.localpkd.common.util;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class VersionUtil {

    private VersionUtil() {
        // Utility class - private constructor
    }

    /**
     * 버전 번호 비교
     * 
     * @param currentVersion 현재 버전 (예: "000325")
     * @param newVersion 새 버전 (예: "000326")
     * @return true if newVersion is newer than currentVersion
     */
    public static boolean isNewerVersion(String currentVersion, String newVersion) {
        if (currentVersion == null || newVersion == null) {
            throw new IllegalArgumentException("Version cannot be null");
        }

        try {
            int current = parseVersionNumber(currentVersion);
            int latest = parseVersionNumber(newVersion);

            return latest > current;
        } catch (NumberFormatException e) {
            log.error("Invalid version format - current: {}, new: {}", currentVersion, newVersion);
            throw new IllegalArgumentException("Invalid version format", e);
        }
    }

    /**
     * Delta 파일 적용 가능 여부 확인
     * Delta는 바로 다음 버전이어야 함
     * 
     * @param baseVersion  기본 버전 (예: "000325")
     * @param deltaVersion Delta 버전 (예: "000326")
     * @return true if delta can be applied
     */
    public static boolean canApplyDelta(String baseVersion, String deltaVersion) {
        if (baseVersion == null || deltaVersion == null) {
            throw new IllegalArgumentException("Version cannot be null");
        }

        try {
            int base = parseVersionNumber(baseVersion);
            int delta = parseVersionNumber(deltaVersion);

            // Delta는 바로 다음 버전이어야 함
            return delta == base + 1;
        } catch (Exception e) {
            log.error("Invalid version format - base: {}, delta: {}", baseVersion, deltaVersion);
            throw new IllegalArgumentException("Invalid version format", e);
        }
    }

    /**
     * 버전 번호를 정수로 파싱
     * "000325" -> 325
     * @param version 버전 문자열
     * @return 정수 버전 번호
     */
    public static int parseVersionNumber(String version) {
        if (version == null || version.isEmpty()) {
            throw new IllegalArgumentException("Version cannot be null or empty");
        }

        // 앞의 0 제거하고 정수로 변환
        return Integer.parseInt(version);
    }

    /**
     * 정수 버전 번호를 문자열로 변환
     * 325 -> "000325"
     * 
     * @param versionNumber 정수 버전 번호
     * @param length 문자열 길이 (기본 6자리)
     * @return 포맷된 버전 문자열
     */
    public static String formatVersionNumber(int versionNumber, int length) {
        if (length < 1) {
            throw new IllegalArgumentException("Length must be postive");
        }

        return String.format("%0" + length + "d", versionNumber);
    }

    /**
     * 정수 버전 번호를 문자열로 변환 (6자리 기본)
     * 
     * @param versionNumber 정수 버전 번호
     * @return 포맷된 버전 문자열
     */
    public static String formatVersionNumber(int versionNumber) {
        return formatVersionNumber(versionNumber, 6);
    }

    /**
     * 다음 버전 번호 생성
     * "000325" -> "000326"
     * 
     * @param currentVersion 현재 버전
     * @return 다음 버전 문자열
     */
    public static String getNextVersion(String currentVersion) {
        int current = parseVersionNumber(currentVersion);
        return formatVersionNumber(current + 1);
    }

    /**
     * 버전 번호 유효성 검증
     * 
     * @param version 버전 문자열
     * @return true if valid
     */
    public static boolean isValidVersion(String version) {
        if (version == null || version.isEmpty()) {
            return false;
        }

        try {
            parseVersionNumber(version);
            return true;
        } catch (NumberFormatException e) {
            return false;
        }
    }


    /**
     * 버전 차이 계산
     * 
     * @param fromVersion 시작 버전
     * @param toVersion 종료 버전
     * @return 버전 차이 (양수: toVersion이 더 큼)
     */
    public static int getVersionDiff(String fromVersion, String toVersion) {
        int from = parseVersionNumber(fromVersion);
        int to = parseVersionNumber(toVersion);
        return to - from;
    }

    /**
     * 버전 범위 생성
     * 
     * @param startVersion 시작 버전
     * @param endVersion 종료 버전
     * @return 버전 문자열 배열
     */
    public static String[] getVersionRange(String startVersion, String endVersion) {
        int start = parseVersionNumber(startVersion);
        int end = parseVersionNumber(endVersion);

        if (start > end) {
            throw new IllegalArgumentException("Start version must be <= end version");
        }

        String[] versions = new String[end - start + 1];
        for (int i = 0; i < versions.length; i++) {
            versions[i] = formatVersionNumber(start + 1);
        }

        return versions;
    }

    /**
     * 버전 비교
     * 
     * @param version1 버전 1
     * @param version2 버전 2
     * @return -1 (version1 < version2), 0 (같음), 1 (version1 > version2)
     */
    public static int compareVersions(String version1, String version2) {
        int v1 = parseVersionNumber(version1);
        int v2 = parseVersionNumber(version2);
        return Integer.compare(v1, v2);
    }
}
