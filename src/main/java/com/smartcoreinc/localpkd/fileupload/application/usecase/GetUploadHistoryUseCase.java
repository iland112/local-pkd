package com.smartcoreinc.localpkd.fileupload.application.usecase;

import com.smartcoreinc.localpkd.fileupload.application.query.GetUploadHistoryQuery;
import com.smartcoreinc.localpkd.fileupload.application.response.UploadHistoryResponse;
import com.smartcoreinc.localpkd.fileupload.domain.model.UploadedFile;
import com.smartcoreinc.localpkd.fileupload.infrastructure.repository.SpringDataUploadedFileRepository;
import com.smartcoreinc.localpkd.fileupload.infrastructure.repository.UploadedFileSpecification;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 업로드 이력 조회 Use Case (CQRS Query)
 *
 * <p>업로드된 파일 이력을 조회하는 Use Case입니다.
 * JPA Specification을 사용하여 동적 검색을 지원합니다.</p>
 *
 * <h3>검색 기능</h3>
 * <ul>
 *   <li>키워드 검색: 파일명, 버전, Collection 번호</li>
 *   <li>상태 필터: UploadStatus (RECEIVED, COMPLETED, etc.)</li>
 *   <li>포맷 필터: FileFormat Type (CSCA_COMPLETE_LDIF, etc.)</li>
 *   <li>Pagination 지원</li>
 * </ul>
 *
 * @author SmartCore Inc.
 * @version 1.0
 * @since 2025-10-19
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class GetUploadHistoryUseCase {

    private final SpringDataUploadedFileRepository repository;

    @Transactional(readOnly = true)
    public Page<UploadHistoryResponse> execute(GetUploadHistoryQuery query) {
        log.debug("=== Get upload history started ===");
        log.debug("Page: {}, Size: {}", query.page(), query.size());
        log.debug("Search: {}, Status: {}, Format: {}",
                  query.searchKeyword(), query.status(), query.fileFormat());

        try {
            query.validate();

            // Pageable 생성 (uploadedAt 내림차순 정렬)
            Pageable pageable = PageRequest.of(
                query.page(),
                query.size(),
                Sort.by(Sort.Direction.DESC, "uploadedAt")
            );

            // Specification 생성 (동적 검색 조건)
            Specification<UploadedFile> spec = UploadedFileSpecification.builder(
                query.searchKeyword(),
                query.status(),
                query.fileFormat()
            );

            // Repository 검색 실행
            Page<UploadedFile> uploadedFiles = repository.findAll(spec, pageable);

            log.debug("Found {} files", uploadedFiles.getTotalElements());

            // UploadedFile을 UploadHistoryResponse로 변환
            return uploadedFiles.map(this::toResponse);

        } catch (Exception e) {
            log.error("Error during get upload history", e);
            return Page.empty();
        }
    }

    /**
     * UploadedFile을 UploadHistoryResponse로 변환
     */
    private UploadHistoryResponse toResponse(UploadedFile uploadedFile) {
        return UploadHistoryResponse.from(
            uploadedFile.getId().getId(),
            uploadedFile.getFileName().getValue(),
            uploadedFile.getFileSize().getBytes(),
            uploadedFile.getFileSizeDisplay(),
            uploadedFile.getFileHash().getValue(),
            uploadedFile.getFileFormatType(),
            uploadedFile.getCollectionNumber() != null ? uploadedFile.getCollectionNumber().getValue() : null,
            uploadedFile.getVersion() != null ? uploadedFile.getVersion().getValue() : null,
            uploadedFile.getUploadedAt(),
            uploadedFile.getStatus().name(),
            uploadedFile.isDuplicate(),
            uploadedFile.getIsNewerVersion(),
            uploadedFile.getExpectedChecksum() != null ? uploadedFile.getExpectedChecksum().getValue() : null,
            uploadedFile.getCalculatedChecksum() != null ? uploadedFile.getCalculatedChecksum().getValue() : null,
            uploadedFile.getErrorMessage()
        );
    }
}
