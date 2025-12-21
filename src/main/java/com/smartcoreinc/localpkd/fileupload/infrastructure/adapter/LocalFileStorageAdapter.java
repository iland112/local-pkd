package com.smartcoreinc.localpkd.fileupload.infrastructure.adapter;

import com.smartcoreinc.localpkd.fileupload.domain.port.FileStoragePort;
import com.smartcoreinc.localpkd.fileupload.domain.model.FileName;
import com.smartcoreinc.localpkd.fileupload.domain.model.FileFormat;
import com.smartcoreinc.localpkd.fileupload.domain.model.FilePath;
import com.smartcoreinc.localpkd.fileupload.domain.model.Checksum;
import com.smartcoreinc.localpkd.shared.exception.InfrastructureException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * 로컬 파일 시스템 저장소 어댑터
 *
 * FileStoragePort 인터페이스를 구현하여 로컬 파일 시스템에 파일을 저장하고 관리합니다.
 *
 * @author SmartCore Inc.
 * @version 2.0 (DDD Refactoring)
 */
@Slf4j
@Component
public class LocalFileStorageAdapter implements FileStoragePort {

    @Value("${app.upload.directory:./data/uploads}")
    private String uploadDirectory;

    /**
     * 파일 저장
     *
     * @param content 파일 내용 (byte array)
     * @param fileFormat 파일 포맷
     * @param fileName 파일명
     * @return 저장된 파일의 경로
     * @throws InfrastructureException 파일 저장 실패 시
     */
    @Override
    public FilePath saveFile(byte[] content, FileFormat fileFormat, FileName fileName) {
        log.debug("Saving file: name={}, format={}, size={} bytes",
                  fileName.getValue(), fileFormat.getType(), content.length);

        try {
            // 저장 디렉토리 생성
            Path uploadPath = createUploadDirectory(fileFormat);

            // 파일명 생성 (타임스탬프 포함하여 중복 방지)
            String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
            String newFileName = timestamp + "_" + fileName.getValue();

            // 파일 저장
            Path targetPath = uploadPath.resolve(newFileName);
            Files.write(targetPath, content);

            String savedPath = targetPath.toString();
            log.info("File saved successfully: path={}, size={} bytes", savedPath, content.length);

            return FilePath.of(savedPath);

        } catch (IOException e) {
            String errorMessage = String.format(
                "Failed to save file: name=%s, format=%s, size=%d bytes",
                fileName.getValue(), fileFormat.getType(), content.length
            );
            log.error(errorMessage, e);
            throw new InfrastructureException("FILE_SAVE_FAILED", errorMessage, e);
        }
    }

    /**
     * 체크섬 계산 (SHA-1)
     *
     * ICAO PKD 표준에 따라 SHA-1 체크섬을 계산합니다.
     *
     * @param filePath 파일 경로
     * @return 계산된 체크섬
     * @throws InfrastructureException 체크섬 계산 실패 시
     */
    @Override
    public Checksum calculateChecksum(FilePath filePath) {
        log.debug("Calculating SHA-1 checksum for file: {}", filePath.getValue());

        try {
            // 파일 읽기
            Path path = Paths.get(filePath.getValue());
            if (!Files.exists(path)) {
                throw new InfrastructureException(
                    "FILE_NOT_FOUND",
                    "File does not exist: " + filePath.getValue()
                );
            }

            byte[] fileBytes = Files.readAllBytes(path);

            // SHA-1 해시 계산
            MessageDigest digest = MessageDigest.getInstance("SHA-1");
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

            String checksumValue = hexString.toString();
            log.debug("SHA-1 checksum calculated: {} (length: {})", checksumValue, checksumValue.length());

            return Checksum.of(checksumValue);

        } catch (IOException e) {
            String errorMessage = "Failed to read file for checksum calculation: " + filePath.getValue();
            log.error(errorMessage, e);
            throw new InfrastructureException("FILE_READ_FAILED", errorMessage, e);

        } catch (NoSuchAlgorithmException e) {
            String errorMessage = "SHA-1 algorithm not available";
            log.error(errorMessage, e);
            throw new InfrastructureException("ALGORITHM_NOT_FOUND", errorMessage, e);
        }
    }

    /**
     * 파일 삭제
     *
     * @param filePath 삭제할 파일 경로
     * @return 삭제 성공 여부
     */
    @Override
    public boolean deleteFile(FilePath filePath) {
        try {
            Path path = Paths.get(filePath.getValue());
            boolean deleted = Files.deleteIfExists(path);

            if (deleted) {
                log.info("File deleted successfully: {}", filePath.getValue());
            } else {
                log.warn("File does not exist: {}", filePath.getValue());
            }

            return deleted;

        } catch (IOException e) {
            log.error("Failed to delete file: {}", filePath.getValue(), e);
            throw new InfrastructureException(
                "FILE_DELETE_FAILED",
                "Failed to delete file: " + filePath.getValue(),
                e
            );
        }
    }

    /**
     * 디스크 여유 공간 조회
     *
     * @return 사용 가능한 디스크 공간 (bytes)
     */
    @Override
    public long getAvailableDiskSpace() {
        try {
            Path path = Paths.get(uploadDirectory);
            if (!Files.exists(path)) {
                path = Paths.get(".");
            }
            long availableSpace = Files.getFileStore(path).getUsableSpace();
            log.debug("Available disk space: {} bytes", availableSpace);
            return availableSpace;

        } catch (IOException e) {
            log.error("Failed to get available disk space", e);
            throw new InfrastructureException(
                "DISK_SPACE_CHECK_FAILED",
                "Failed to get available disk space",
                e
            );
        }
    }

    /**
     * 파일 존재 여부 확인
     *
     * @param filePath 확인할 파일 경로
     * @return 파일 존재 여부
     */
    @Override
    public boolean exists(FilePath filePath) {
        boolean exists = Files.exists(Paths.get(filePath.getValue()));
        log.debug("File exists check: path={}, exists={}", filePath.getValue(), exists);
        return exists;
    }

    /**
     * 파일 크기 조회
     *
     * @param filePath 파일 경로
     * @return 파일 크기 (bytes)
     * @throws InfrastructureException 파일이 존재하지 않거나 읽기 실패 시
     */
    @Override
    public long getFileSize(FilePath filePath) {
        try {
            Path path = Paths.get(filePath.getValue());
            if (!Files.exists(path)) {
                throw new InfrastructureException(
                    "FILE_NOT_FOUND",
                    "File does not exist: " + filePath.getValue()
                );
            }

            long size = Files.size(path);
            log.debug("File size: path={}, size={} bytes", filePath.getValue(), size);
            return size;

        } catch (IOException e) {
            String errorMessage = "Failed to get file size: " + filePath.getValue();
            log.error(errorMessage, e);
            throw new InfrastructureException("FILE_SIZE_READ_FAILED", errorMessage, e);
        }
    }

    /**
     * 파일 읽기
     *
     * 저장된 파일의 전체 내용을 바이트 배열로 읽어 반환합니다.
     * 파일 파싱이나 재처리 시 사용합니다.
     *
     * @param filePath 읽을 파일 경로
     * @return 파일 내용 (byte array)
     * @throws InfrastructureException 파일이 존재하지 않거나 읽기 실패 시
     */
    @Override
    public byte[] readFile(FilePath filePath) {
        log.debug("Reading file: {}", filePath.getValue());

        try {
            Path path = Paths.get(filePath.getValue());
            if (!Files.exists(path)) {
                throw new InfrastructureException(
                    "FILE_NOT_FOUND",
                    "File does not exist: " + filePath.getValue()
                );
            }

            byte[] fileBytes = Files.readAllBytes(path);
            log.debug("File read successfully: path={}, size={} bytes",
                     filePath.getValue(), fileBytes.length);

            return fileBytes;

        } catch (IOException e) {
            String errorMessage = "Failed to read file: " + filePath.getValue();
            log.error(errorMessage, e);
            throw new InfrastructureException("FILE_READ_FAILED", errorMessage, e);
        }
    }

    /**
     * 업로드 디렉토리 생성
     *
     * FileFormat의 storagePath를 기반으로 디렉토리를 생성합니다.
     *
     * @param fileFormat 파일 포맷
     * @return 생성된 디렉토리 경로
     * @throws IOException 디렉토리 생성 실패 시
     */
    private Path createUploadDirectory(FileFormat fileFormat) throws IOException {
        // FileFormat의 storagePath 사용 (예: "ldif/csca-complete", "ml/signed-cms")
        String subDir = fileFormat.getStoragePath();
        Path uploadPath = Paths.get(uploadDirectory, subDir);

        if (!Files.exists(uploadPath)) {
            Files.createDirectories(uploadPath);
            log.info("Created upload directory: {}", uploadPath);
        }

        return uploadPath;
    }
}
