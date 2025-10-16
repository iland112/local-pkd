package com.smartcoreinc.localpkd.common.util;

import lombok.extern.slf4j.Slf4j;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * 파일 체크섬 검증 유틸리티
 *
 * ICAO PKD 파일의 SHA-1 체크섬을 계산하고 검증합니다.
 * ICAO PKD 다운로드 페이지에서 제공하는 체크섬과 비교하여 파일 무결성을 확인합니다.
 *
 * @author SmartCore Inc.
 * @version 1.0
 * @see <a href="https://pkddownloadsg.icao.int/download">ICAO PKD Download</a>
 */
@Slf4j
public class ChecksumValidator {

    private static final String SHA1_ALGORITHM = "SHA-1";
    private static final String MD5_ALGORITHM = "MD5";
    private static final int BUFFER_SIZE = 8192;

    /**
     * 파일의 SHA-1 체크섬 계산
     *
     * @param filePath 파일 경로
     * @return SHA-1 체크섬 (40자 hex 문자열)
     * @throws IOException 파일 읽기 실패
     */
    public static String calculateSHA1(String filePath) throws IOException {
        return calculateChecksum(filePath, SHA1_ALGORITHM);
    }

    /**
     * 파일의 SHA-1 체크섬 계산
     *
     * @param file 파일 객체
     * @return SHA-1 체크섬 (40자 hex 문자열)
     * @throws IOException 파일 읽기 실패
     */
    public static String calculateSHA1(File file) throws IOException {
        return calculateChecksum(file, SHA1_ALGORITHM);
    }

    /**
     * InputStream의 SHA-1 체크섬 계산
     *
     * @param inputStream 입력 스트림
     * @return SHA-1 체크섬 (40자 hex 문자열)
     * @throws IOException 스트림 읽기 실패
     */
    public static String calculateSHA1(InputStream inputStream) throws IOException {
        return calculateChecksum(inputStream, SHA1_ALGORITHM);
    }

    /**
     * 파일의 MD5 체크섬 계산
     *
     * @param filePath 파일 경로
     * @return MD5 체크섬 (32자 hex 문자열)
     * @throws IOException 파일 읽기 실패
     */
    public static String calculateMD5(String filePath) throws IOException {
        return calculateChecksum(filePath, MD5_ALGORITHM);
    }

    /**
     * 파일의 체크섬 계산 (범용)
     *
     * @param filePath 파일 경로
     * @param algorithm 해시 알고리즘 (SHA-1, MD5 등)
     * @return 체크섬 hex 문자열
     * @throws IOException 파일 읽기 실패
     */
    public static String calculateChecksum(String filePath, String algorithm) throws IOException {
        return calculateChecksum(new File(filePath), algorithm);
    }

    /**
     * 파일의 체크섬 계산 (범용)
     *
     * @param file 파일 객체
     * @param algorithm 해시 알고리즘 (SHA-1, MD5 등)
     * @return 체크섬 hex 문자열
     * @throws IOException 파일 읽기 실패
     */
    public static String calculateChecksum(File file, String algorithm) throws IOException {
        if (!file.exists()) {
            throw new IOException("File not found: " + file.getAbsolutePath());
        }

        if (!file.isFile()) {
            throw new IOException("Not a file: " + file.getAbsolutePath());
        }

        try (FileInputStream fis = new FileInputStream(file)) {
            return calculateChecksum(fis, algorithm);
        }
    }

    /**
     * InputStream의 체크섬 계산 (범용)
     *
     * @param inputStream 입력 스트림
     * @param algorithm 해시 알고리즘 (SHA-1, MD5 등)
     * @return 체크섬 hex 문자열
     * @throws IOException 스트림 읽기 실패
     */
    public static String calculateChecksum(InputStream inputStream, String algorithm) throws IOException {
        try {
            MessageDigest digest = MessageDigest.getInstance(algorithm);
            byte[] buffer = new byte[BUFFER_SIZE];
            int bytesRead;

            while ((bytesRead = inputStream.read(buffer)) != -1) {
                digest.update(buffer, 0, bytesRead);
            }

            byte[] hashBytes = digest.digest();
            return bytesToHex(hashBytes);

        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("Algorithm not available: " + algorithm, e);
        }
    }

    /**
     * 체크섬 검증
     *
     * @param filePath 파일 경로
     * @param expectedChecksum 기대되는 체크섬 (ICAO 공식)
     * @return ChecksumValidationResult 검증 결과
     */
    public static ChecksumValidationResult validate(String filePath, String expectedChecksum) {
        return validate(new File(filePath), expectedChecksum);
    }

    /**
     * 체크섬 검증
     *
     * @param file 파일 객체
     * @param expectedChecksum 기대되는 체크섬 (ICAO 공식)
     * @return ChecksumValidationResult 검증 결과
     */
    public static ChecksumValidationResult validate(File file, String expectedChecksum) {
        try {
            long startTime = System.currentTimeMillis();
            String calculatedChecksum = calculateSHA1(file);
            long elapsedTime = System.currentTimeMillis() - startTime;

            boolean matches = calculatedChecksum.equalsIgnoreCase(expectedChecksum);

            log.info("Checksum validation for {}: {} (took {}ms)",
                    file.getName(),
                    matches ? "PASSED" : "FAILED",
                    elapsedTime);

            if (!matches) {
                log.warn("Checksum mismatch for {}: expected={}, actual={}",
                        file.getName(), expectedChecksum, calculatedChecksum);
            }

            return ChecksumValidationResult.builder()
                    .valid(matches)
                    .calculatedChecksum(calculatedChecksum)
                    .expectedChecksum(expectedChecksum)
                    .elapsedTimeMs(elapsedTime)
                    .build();

        } catch (IOException e) {
            log.error("Failed to calculate checksum for {}", file.getName(), e);
            return ChecksumValidationResult.error(e.getMessage());
        }
    }

    /**
     * 체크섬 검증 (입력 스트림)
     *
     * @param inputStream 입력 스트림
     * @param expectedChecksum 기대되는 체크섬
     * @return ChecksumValidationResult 검증 결과
     */
    public static ChecksumValidationResult validate(InputStream inputStream, String expectedChecksum) {
        try {
            long startTime = System.currentTimeMillis();
            String calculatedChecksum = calculateSHA1(inputStream);
            long elapsedTime = System.currentTimeMillis() - startTime;

            boolean matches = calculatedChecksum.equalsIgnoreCase(expectedChecksum);

            log.info("Checksum validation: {} (took {}ms)",
                    matches ? "PASSED" : "FAILED",
                    elapsedTime);

            return ChecksumValidationResult.builder()
                    .valid(matches)
                    .calculatedChecksum(calculatedChecksum)
                    .expectedChecksum(expectedChecksum)
                    .elapsedTimeMs(elapsedTime)
                    .build();

        } catch (IOException e) {
            log.error("Failed to calculate checksum", e);
            return ChecksumValidationResult.error(e.getMessage());
        }
    }

    /**
     * 바이트 배열을 16진수 문자열로 변환
     *
     * @param bytes 바이트 배열
     * @return 16진수 문자열 (소문자)
     */
    private static String bytesToHex(byte[] bytes) {
        StringBuilder hexString = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            String hex = Integer.toHexString(0xff & b);
            if (hex.length() == 1) {
                hexString.append('0');
            }
            hexString.append(hex);
        }
        return hexString.toString();
    }

    /**
     * 체크섬 형식 검증 (SHA-1)
     *
     * @param checksum 체크섬 문자열
     * @return 유효한 SHA-1 형식 여부
     */
    public static boolean isValidSHA1Format(String checksum) {
        if (checksum == null || checksum.isEmpty()) {
            return false;
        }
        // SHA-1: 40자 16진수
        return checksum.matches("^[a-fA-F0-9]{40}$");
    }

    /**
     * 체크섬 형식 검증 (MD5)
     *
     * @param checksum 체크섬 문자열
     * @return 유효한 MD5 형식 여부
     */
    public static boolean isValidMD5Format(String checksum) {
        if (checksum == null || checksum.isEmpty()) {
            return false;
        }
        // MD5: 32자 16진수
        return checksum.matches("^[a-fA-F0-9]{32}$");
    }

    /**
     * 체크섬 정규화 (소문자 변환, 공백 제거)
     *
     * @param checksum 체크섬 문자열
     * @return 정규화된 체크섬
     */
    public static String normalize(String checksum) {
        if (checksum == null) {
            return null;
        }
        return checksum.trim().toLowerCase();
    }
}