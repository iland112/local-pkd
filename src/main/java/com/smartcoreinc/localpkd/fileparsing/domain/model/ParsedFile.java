package com.smartcoreinc.localpkd.fileparsing.domain.model;

import com.smartcoreinc.localpkd.fileparsing.domain.event.CertificatesExtractedEvent;
import com.smartcoreinc.localpkd.fileparsing.domain.event.FileParsingCompletedEvent;
import com.smartcoreinc.localpkd.fileparsing.domain.event.FileParsingStartedEvent;
import com.smartcoreinc.localpkd.fileparsing.domain.event.ParsingFailedEvent;
import com.smartcoreinc.localpkd.fileupload.domain.model.FileFormat;
import com.smartcoreinc.localpkd.fileupload.domain.model.UploadId;
import com.smartcoreinc.localpkd.shared.domain.AggregateRoot;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * ParsedFile - 파싱된 파일 Aggregate Root
 *
 * <p><b>DDD Aggregate Root 패턴</b>:</p>
 * <ul>
 *   <li>일관성 경계 (Consistency Boundary): 파싱 결과의 모든 데이터를 관리</li>
 *   <li>트랜잭션 경계: 파싱 작업은 항상 하나의 트랜잭션으로 처리</li>
 *   <li>Domain Events: 파싱 시작/완료/실패 시 이벤트 발행</li>
 * </ul>
 *
 * <p><b>비즈니스 규칙</b>:</p>
 * <ul>
 *   <li>파싱 상태 전이: RECEIVED → PARSING → PARSED/FAILED</li>
 *   <li>파싱 중에만 데이터 추가 가능 (addCertificate, addCrl, addError)</li>
 *   <li>파싱 완료 시 자동으로 통계 계산</li>
 *   <li>파싱 실패 시 오류 메시지 필수</li>
 * </ul>
 *
 * <p><b>사용 예시</b>:</p>
 * <pre>
 * // 1. 파싱 시작
 * ParsedFile parsedFile = ParsedFile.create(
 *     ParsedFileId.newId(),
 *     uploadId,
 *     fileFormat
 * );
 * parsedFile.startParsing();
 *
 * // 2. 데이터 추가
 * parsedFile.addCertificate(certificateData);
 * parsedFile.addCrl(crlData);
 *
 * // 3. 파싱 완료
 * parsedFile.completeParsing(duration);
 *
 * // 4. 저장 시 Domain Events 자동 발행
 * repository.save(parsedFile);
 * // → FileParsingStartedEvent, CertificatesExtractedEvent, FileParsingCompletedEvent
 * </pre>
 *
 * @see ParsedFileId
 * @see ParsingStatus
 * @see CertificateData
 * @see CrlData
 * @see ParsingStatistics
 * @see ParsingError
 */
@Entity
@Table(name = "parsed_file")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ParsedFile extends AggregateRoot<ParsedFileId> {

    /**
     * ParsedFile ID (Primary Key)
     */
    @EmbeddedId
    private ParsedFileId id;

    /**
     * 업로드된 파일 ID (외부 Bounded Context 참조)
     *
     * <p>File Upload Context의 UploadedFile과 1:1 관계</p>
     */
    @Embedded
    @AttributeOverrides({
        @AttributeOverride(name = "id", column = @Column(name = "upload_id"))
    })
    private UploadId uploadId;

    /**
     * 파일 포맷 (LDIF, Master List)
     *
     * <p>FileFormat은 Value Object이므로 @Embedded 사용</p>
     */
    @Embedded
    @AttributeOverrides({
        @AttributeOverride(name = "type", column = @Column(name = "file_format", length = 50, nullable = false))
    })
    private FileFormat fileFormat;

    /**
     * 파싱 상태 (RECEIVED → PARSING → PARSED/FAILED)
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 20, nullable = false)
    private ParsingStatus status;

    /**
     * 파싱 시작 일시
     */
    @Column(name = "parsing_started_at")
    private LocalDateTime parsingStartedAt;

    /**
     * 파싱 완료 일시
     */
    @Column(name = "parsing_completed_at")
    private LocalDateTime parsingCompletedAt;

    /**
     * 파싱 통계
     */
    @Embedded
    private ParsingStatistics statistics;

    /**
     * 추출된 인증서 목록
     *
     * <p>ElementCollection으로 Value Object를 컬렉션으로 저장</p>
     * <p>EAGER fetch: Event Handler에서 certificates를 사용하므로 항상 로드</p>
     */
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(
        name = "parsed_certificate",
        joinColumns = @JoinColumn(name = "parsed_file_id")
    )
    private List<CertificateData> certificates = new ArrayList<>();

    /**
     * 추출된 CRL 목록
     *
     * <p>EAGER fetch: Event Handler에서 CRLs를 사용하므로 항상 로드</p>
     */
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(
        name = "parsed_crl",
        joinColumns = @JoinColumn(name = "parsed_file_id")
    )
    private List<CrlData> crls = new ArrayList<>();

    /**
     * 파싱 오류 목록
     */
    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(
        name = "parsing_error",
        joinColumns = @JoinColumn(name = "parsed_file_id")
    )
    private List<ParsingError> errors = new ArrayList<>();

    // ========== Static Factory Method ==========

    /**
     * ParsedFile 생성
     *
     * <p>초기 상태는 RECEIVED입니다.</p>
     *
     * @param id ParsedFileId
     * @param uploadId UploadId (외부 참조)
     * @param fileFormat FileFormat
     * @return ParsedFile
     */
    public static ParsedFile create(
        ParsedFileId id,
        UploadId uploadId,
        FileFormat fileFormat
    ) {
        if (id == null) {
            throw new IllegalArgumentException("id must not be null");
        }
        if (uploadId == null) {
            throw new IllegalArgumentException("uploadId must not be null");
        }
        if (fileFormat == null) {
            throw new IllegalArgumentException("fileFormat must not be null");
        }

        ParsedFile parsedFile = new ParsedFile();
        parsedFile.id = id;
        parsedFile.uploadId = uploadId;
        parsedFile.fileFormat = fileFormat;
        parsedFile.status = ParsingStatus.RECEIVED;
        parsedFile.statistics = ParsingStatistics.empty();

        return parsedFile;
    }

    // ========== Business Methods ==========

    /**
     * 파싱 시작
     *
     * <p>상태를 PARSING으로 전환하고 FileParsingStartedEvent를 발행합니다.</p>
     *
     * @throws IllegalStateException 현재 상태가 RECEIVED가 아닌 경우
     */
    public void startParsing() {
        status.validateTransitionTo(ParsingStatus.PARSING);

        this.status = ParsingStatus.PARSING;
        this.parsingStartedAt = LocalDateTime.now();

        // Domain Event 발행
        addDomainEvent(new FileParsingStartedEvent(
            id.getId(),
            uploadId.getId(),
            fileFormat.toString(),
            parsingStartedAt
        ));
    }

    /**
     * 인증서 추가
     *
     * <p>파싱 중에만 추가 가능합니다.</p>
     *
     * @param certificate CertificateData
     * @throws IllegalStateException 파싱 중이 아닌 경우
     */
    public void addCertificate(CertificateData certificate) {
        if (!status.isParsing()) {
            throw new IllegalStateException("파싱 중에만 인증서를 추가할 수 있습니다. 현재 상태: " + status);
        }
        if (certificate == null) {
            throw new IllegalArgumentException("certificate must not be null");
        }

        // 중복 체크: fingerprint_sha256 기준으로 이미 존재하는 인증서는 추가하지 않음
        // Master List의 SignerInfo 인증서가 DLSet에도 포함되어 있는 경우를 방지
        String newFingerprint = certificate.getFingerprintSha256();
        boolean isDuplicate = certificates.stream()
            .anyMatch(existing -> existing.getFingerprintSha256().equals(newFingerprint));

        if (!isDuplicate) {
            certificates.add(certificate);
        }
    }

    /**
     * CRL 추가
     *
     * <p>파싱 중에만 추가 가능합니다.</p>
     *
     * @param crl CrlData
     * @throws IllegalStateException 파싱 중이 아닌 경우
     */
    public void addCrl(CrlData crl) {
        if (!status.isParsing()) {
            throw new IllegalStateException("파싱 중에만 CRL을 추가할 수 있습니다. 현재 상태: " + status);
        }
        if (crl == null) {
            throw new IllegalArgumentException("crl must not be null");
        }

        crls.add(crl);
    }

    /**
     * 파싱 오류 추가
     *
     * <p>파싱 중에만 추가 가능합니다.</p>
     *
     * @param error ParsingError
     * @throws IllegalStateException 파싱 중이 아닌 경우
     */
    public void addError(ParsingError error) {
        if (!status.isParsing()) {
            throw new IllegalStateException("파싱 중에만 오류를 추가할 수 있습니다. 현재 상태: " + status);
        }
        if (error == null) {
            throw new IllegalArgumentException("error must not be null");
        }

        errors.add(error);
    }

    /**
     * 파싱 완료
     *
     * <p>통계를 계산하고 상태를 PARSED로 전환합니다.</p>
     * <p>다음 Domain Events를 발행합니다:</p>
     * <ul>
     *   <li>CertificatesExtractedEvent (인증서/CRL이 있는 경우)</li>
     *   <li>FileParsingCompletedEvent</li>
     * </ul>
     *
     * @param totalEntries 전체 엔트리 수
     * @throws IllegalStateException 파싱 중이 아닌 경우
     */
    public void completeParsing(int totalEntries) {
        status.validateTransitionTo(ParsingStatus.PARSED);

        this.status = ParsingStatus.PARSED;
        this.parsingCompletedAt = LocalDateTime.now();

        // 통계 계산
        long durationMillis = java.time.Duration.between(parsingStartedAt, parsingCompletedAt).toMillis();
        int validCount = (int) certificates.stream().filter(CertificateData::isValid).count()
                       + (int) crls.stream().filter(CrlData::isValid).count();
        int invalidCount = (int) certificates.stream().filter(cert -> !cert.isValid()).count()
                         + (int) crls.stream().filter(crl -> !crl.isValid()).count();

        this.statistics = ParsingStatistics.of(
            totalEntries,
            certificates.size() + crls.size(),
            certificates.size(),
            crls.size(),
            validCount,
            invalidCount,
            errors.size(),
            durationMillis
        );

        // Domain Events 발행
        if (!certificates.isEmpty() || !crls.isEmpty()) {
            addDomainEvent(new CertificatesExtractedEvent(
                id.getId(),
                uploadId.getId(),
                certificates.size(),
                crls.size()
            ));
        }

        addDomainEvent(new FileParsingCompletedEvent(
            id.getId(),
            uploadId.getId(),
            certificates.size(),
            crls.size(),
            statistics.getTotalProcessed(),
            parsingCompletedAt
        ));
    }

    /**
     * 파싱 실패
     *
     * <p>상태를 FAILED로 전환하고 ParsingFailedEvent를 발행합니다.</p>
     *
     * @param errorMessage 오류 메시지
     * @throws IllegalStateException 파싱 중이 아닌 경우
     */
    public void failParsing(String errorMessage) {
        status.validateTransitionTo(ParsingStatus.FAILED);

        this.status = ParsingStatus.FAILED;
        this.parsingCompletedAt = LocalDateTime.now();

        // 실패 오류 추가
        ParsingError failureError = ParsingError.parseError(errorMessage);
        errors.add(failureError);

        // Domain Event 발행
        addDomainEvent(new ParsingFailedEvent(
            id.getId(),
            uploadId.getId(),
            errorMessage,
            LocalDateTime.now()
        ));
    }

    // ========== Getters (Unmodifiable Collections) ==========

    /**
     * 인증서 목록 조회 (읽기 전용)
     */
    public List<CertificateData> getCertificates() {
        return Collections.unmodifiableList(certificates);
    }

    /**
     * CRL 목록 조회 (읽기 전용)
     */
    public List<CrlData> getCrls() {
        return Collections.unmodifiableList(crls);
    }

    /**
     * 오류 목록 조회 (읽기 전용)
     */
    public List<ParsingError> getErrors() {
        return Collections.unmodifiableList(errors);
    }

    // ========== Helper Methods ==========

    /**
     * 파싱 성공 여부
     *
     * @return 파싱이 성공했는지 여부 (PARSED 상태이고 오류율 < 5%)
     */
    public boolean isSuccessful() {
        return status.isParsed() && statistics.isSuccessful();
    }

    /**
     * 파싱 진행 중 여부
     *
     * @return 파싱이 진행 중인지 여부
     */
    public boolean isParsing() {
        return status.isParsing();
    }

    /**
     * 파싱 완료 여부 (성공 또는 실패)
     *
     * @return 파싱이 완료되었는지 여부
     */
    public boolean isCompleted() {
        return status.isTerminal();
    }

    @Override
    public ParsedFileId getId() {
        return id;
    }

    @Override
    public String toString() {
        return String.format(
            "ParsedFile[id=%s, uploadId=%s, format=%s, status=%s, certs=%d, crls=%d, errors=%d]",
            id.getId(),
            uploadId.getId(),
            fileFormat.toString(),
            status.name(),
            certificates.size(),
            crls.size(),
            errors.size()
        );
    }
}
