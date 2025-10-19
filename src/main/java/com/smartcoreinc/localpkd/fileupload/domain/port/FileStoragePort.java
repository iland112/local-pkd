package com.smartcoreinc.localpkd.fileupload.domain.port;

import com.smartcoreinc.localpkd.fileupload.domain.model.*;

/**
 * File Storage Port - 파일 저장소 인터페이스
 *
 * <p>Hexagonal Architecture의 Port 역할을 하는 인터페이스입니다.
 * Domain Layer에서 정의하고, Infrastructure Layer에서 구현합니다.</p>
 *
 * <h3>Port & Adapter 패턴</h3>
 * <ul>
 *   <li>Port (이 인터페이스): Domain Layer가 외부 세계와 통신하는 계약</li>
 *   <li>Adapter (구현체): Infrastructure Layer에서 실제 파일 시스템과 통신</li>
 * </ul>
 *
 * <h3>구현체 예시</h3>
 * <ul>
 *   <li>LocalFileStorageAdapter - 로컬 파일 시스템</li>
 *   <li>S3FileStorageAdapter - AWS S3</li>
 *   <li>AzureBlobStorageAdapter - Azure Blob Storage</li>
 * </ul>
 *
 * <h3>사용 예시 - Use Case에서 사용</h3>
 * <pre>{@code
 * @Service
 * @RequiredArgsConstructor
 * public class UploadFileUseCase {
 *
 *     private final FileStoragePort fileStoragePort;  // Port 주입
 *
 *     public UploadFileResponse execute(UploadFileCommand command) {
 *         // 파일 저장 (어떤 구현체인지 몰라도 됨)
 *         FilePath saved = fileStoragePort.saveFile(
 *             command.fileContent(),
 *             fileFormat,
 *             fileName
 *         );
 *
 *         // 체크섬 계산
 *         Checksum checksum = fileStoragePort.calculateChecksum(saved);
 *
 *         // ...
 *     }
 * }
 * }</pre>
 *
 * <h3>구현 예시 - Infrastructure Layer</h3>
 * <pre>{@code
 * @Component
 * @Slf4j
 * public class LocalFileStorageAdapter implements FileStoragePort {
 *
 *     @Value("${app.upload.directory:./data/uploads}")
 *     private String uploadDirectory;
 *
 *     @Override
 *     public FilePath saveFile(byte[] content, FileFormat format, FileName fileName) {
 *         // 실제 파일 시스템에 저장
 *         Path directory = createDirectory(format);
 *         Path filePath = directory.resolve(fileName.getValue());
 *         Files.write(filePath, content);
 *         return FilePath.of(filePath.toString());
 *     }
 *
 *     @Override
 *     public Checksum calculateChecksum(FilePath filePath) {
 *         // SHA-1 체크섬 계산
 *         MessageDigest digest = MessageDigest.getInstance("SHA-1");
 *         byte[] fileBytes = Files.readAllBytes(Paths.get(filePath.getValue()));
 *         byte[] hashBytes = digest.digest(fileBytes);
 *         return Checksum.of(bytesToHex(hashBytes));
 *     }
 * }
 * }</pre>
 *
 * @author SmartCore Inc.
 * @version 1.0
 * @since 2025-10-19
 */
public interface FileStoragePort {

    /**
     * 파일 저장
     *
     * <p>파일 내용을 저장소에 저장하고 저장 경로를 반환합니다.
     * 파일명은 중복 방지를 위해 타임스탬프가 추가됩니다.</p>
     *
     * <h4>저장 경로 예시</h4>
     * <ul>
     *   <li>LDIF: ./data/uploads/ldif/csca-complete/file_20251019103000.ldif</li>
     *   <li>ML: ./data/uploads/ml/signed-cms/masterlist_20251019103000.ml</li>
     * </ul>
     *
     * @param content 파일 내용 (바이트 배열)
     * @param fileFormat 파일 포맷 (저장 경로 결정에 사용)
     * @param fileName 원본 파일명
     * @return 저장된 파일 경로
     * @throws com.smartcoreinc.localpkd.shared.exception.InfrastructureException 파일 저장 실패 시
     */
    FilePath saveFile(byte[] content, FileFormat fileFormat, FileName fileName);

    /**
     * SHA-1 체크섬 계산
     *
     * <p>파일의 SHA-1 체크섬을 계산합니다.
     * ICAO PKD 표준에서는 파일 무결성 검증을 위해 SHA-1을 사용합니다.</p>
     *
     * @param filePath 체크섬을 계산할 파일 경로
     * @return SHA-1 체크섬 (40자 16진수)
     * @throws com.smartcoreinc.localpkd.shared.exception.InfrastructureException 체크섬 계산 실패 시
     */
    Checksum calculateChecksum(FilePath filePath);

    /**
     * 파일 삭제
     *
     * <p>저장된 파일을 삭제합니다.
     * 파일이 존재하지 않아도 예외를 발생시키지 않습니다.</p>
     *
     * @param filePath 삭제할 파일 경로
     * @return 삭제 성공 여부 (파일이 존재하지 않으면 false)
     */
    boolean deleteFile(FilePath filePath);

    /**
     * 디스크 여유 공간 확인
     *
     * <p>파일 저장소의 여유 공간을 바이트 단위로 반환합니다.
     * 업로드 전 용량 확인에 사용합니다.</p>
     *
     * @return 여유 공간 (bytes)
     */
    long getAvailableDiskSpace();

    /**
     * 파일 존재 여부 확인
     *
     * <p>지정된 경로에 파일이 존재하는지 확인합니다.</p>
     *
     * @param filePath 확인할 파일 경로
     * @return 파일 존재 여부
     */
    boolean exists(FilePath filePath);

    /**
     * 파일 크기 조회
     *
     * <p>저장된 파일의 크기를 바이트 단위로 반환합니다.</p>
     *
     * @param filePath 파일 경로
     * @return 파일 크기 (bytes)
     * @throws com.smartcoreinc.localpkd.shared.exception.InfrastructureException 파일이 존재하지 않거나 읽을 수 없는 경우
     */
    long getFileSize(FilePath filePath);
}
