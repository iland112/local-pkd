package com.smartcoreinc.localpkd.service;

import com.smartcoreinc.localpkd.common.enums.FileFormat;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * 파일 저장 서비스
 *
 * 업로드된 파일을 로컬 파일 시스템에 저장하고 관리합니다.
 *
 * @author SmartCore Inc.
 * @version 1.0
 */
@Slf4j
@Service
public class FileStorageService {

    @Value("${app.upload.directory:./data/uploads}")
    private String uploadDirectory;

    /**
     * 파일 저장
     *
     * @param file 업로드된 파일
     * @param format 파일 포맷
     * @return 저장된 파일의 전체 경로
     * @throws IOException 파일 저장 실패 시
     */
    public String saveFile(MultipartFile file, FileFormat format) throws IOException {
        log.debug("Saving file: originalName={}, format={}", file.getOriginalFilename(), format);

        // 저장 디렉토리 생성
        Path uploadPath = createUploadDirectory(format);

        // 파일명 생성 (타임스탬프 포함하여 중복 방지)
        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
        String originalFilename = file.getOriginalFilename();
        String filename = timestamp + "_" + originalFilename;

        // 파일 저장
        Path targetPath = uploadPath.resolve(filename);
        Files.copy(file.getInputStream(), targetPath, StandardCopyOption.REPLACE_EXISTING);

        String savedPath = targetPath.toString();
        log.info("File saved successfully: path={}, size={}", savedPath, file.getSize());

        return savedPath;
    }

    /**
     * 업로드 디렉토리 생성
     *
     * @param format 파일 포맷
     * @return 생성된 디렉토리 경로
     * @throws IOException 디렉토리 생성 실패 시
     */
    private Path createUploadDirectory(FileFormat format) throws IOException {
        String subDir = format.name().toLowerCase();
        Path uploadPath = Paths.get(uploadDirectory, subDir);

        if (!Files.exists(uploadPath)) {
            Files.createDirectories(uploadPath);
            log.info("Created upload directory: {}", uploadPath);
        }

        return uploadPath;
    }

    /**
     * 파일 해시 계산 (SHA-256)
     *
     * 중복 파일 감지를 위해 SHA-256 해시를 계산합니다.
     *
     * @param file 업로드된 파일
     * @return SHA-256 해시 값 (16진수 문자열)
     * @throws IOException 파일 읽기 실패 시
     */
    public String calculateFileHash(MultipartFile file) throws IOException {
        log.debug("Calculating file hash for: {}", file.getOriginalFilename());

        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] fileBytes = file.getBytes();
            byte[] hashBytes = digest.digest(fileBytes);

            // 바이트 배열을 16진수 문자열로 변환
            StringBuilder hexString = new StringBuilder();
            for (byte b : hashBytes) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) {
                    hexString.append('0');
                }
                hexString.append(hex);
            }

            String hash = hexString.toString();
            log.debug("File hash calculated: {} (length: {})", hash, hash.length());

            return hash;

        } catch (NoSuchAlgorithmException e) {
            log.error("SHA-256 algorithm not found", e);
            throw new RuntimeException("파일 해시 계산 실패: SHA-256 알고리즘을 찾을 수 없습니다.", e);
        }
    }

    /**
     * 파일 삭제
     *
     * @param filePath 삭제할 파일 경로
     * @return 삭제 성공 여부
     */
    public boolean deleteFile(String filePath) {
        try {
            Path path = Paths.get(filePath);
            boolean deleted = Files.deleteIfExists(path);

            if (deleted) {
                log.info("File deleted successfully: {}", filePath);
            } else {
                log.warn("File does not exist: {}", filePath);
            }

            return deleted;

        } catch (IOException e) {
            log.error("Failed to delete file: {}", filePath, e);
            return false;
        }
    }

    /**
     * 파일 존재 여부 확인
     *
     * @param filePath 확인할 파일 경로
     * @return 파일 존재 여부
     */
    public boolean fileExists(String filePath) {
        return Files.exists(Paths.get(filePath));
    }

    /**
     * 디스크 공간 확인
     *
     * @return 사용 가능한 디스크 공간 (bytes)
     */
    public long getAvailableDiskSpace() {
        try {
            Path path = Paths.get(uploadDirectory);
            if (!Files.exists(path)) {
                path = Paths.get(".");
            }
            return Files.getFileStore(path).getUsableSpace();
        } catch (IOException e) {
            log.error("Failed to get available disk space", e);
            return -1;
        }
    }
}