package com.smartcoreinc.localpkd.fileupload.infrastructure.repository;

import com.smartcoreinc.localpkd.fileupload.domain.model.FileFormat;
import com.smartcoreinc.localpkd.fileupload.domain.model.UploadStatus;
import com.smartcoreinc.localpkd.fileupload.domain.model.UploadedFile;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;

import java.util.ArrayList;
import java.util.List;

/**
 * JPA Specification for UploadedFile dynamic queries
 *
 * <p>JPA Specification을 사용하여 동적 쿼리를 생성합니다.
 * {@link GetUploadHistoryUseCase}에서 검색 조건에 따라 Specification을 조합하여 사용합니다.</p>
 *
 * <h3>사용 예시</h3>
 * <pre>{@code
 * Specification<UploadedFile> spec = UploadedFileSpecification.builder()
 *     .searchKeyword("ldif")
 *     .status("COMPLETED")
 *     .fileFormat("CSCA_COMPLETE_LDIF")
 *     .build();
 *
 * Page<UploadedFile> result = repository.findAll(spec, pageable);
 * }</pre>
 *
 * @author SmartCore Inc.
 * @version 1.0
 * @since 2025-10-19
 */
public class UploadedFileSpecification {

    /**
     * Specification Builder
     *
     * <p>검색 조건들을 AND 조건으로 조합합니다.</p>
     */
    public static Specification<UploadedFile> builder(
            String searchKeyword,
            String status,
            String fileFormat
    ) {
        return (root, query, criteriaBuilder) -> {
            List<Predicate> predicates = new ArrayList<>();

            // 1. Search Keyword (파일명, 버전, Collection 번호)
            if (searchKeyword != null && !searchKeyword.trim().isEmpty()) {
                String likePattern = "%" + searchKeyword.trim().toLowerCase() + "%";

                Predicate fileNamePredicate = criteriaBuilder.like(
                        criteriaBuilder.lower(root.get("fileName").get("value")),
                        likePattern
                );

                Predicate versionPredicate = criteriaBuilder.like(
                        criteriaBuilder.lower(root.get("version").get("value")),
                        likePattern
                );

                Predicate collectionPredicate = criteriaBuilder.like(
                        criteriaBuilder.lower(root.get("collectionNumber").get("value")),
                        likePattern
                );

                predicates.add(criteriaBuilder.or(fileNamePredicate, versionPredicate, collectionPredicate));
            }

            // 2. Status Filter
            if (status != null && !status.trim().isEmpty()) {
                try {
                    UploadStatus uploadStatus = UploadStatus.valueOf(status.trim());
                    predicates.add(criteriaBuilder.equal(root.get("status"), uploadStatus));
                } catch (IllegalArgumentException e) {
                    // Invalid status - ignore filter
                }
            }

            // 3. FileFormat Filter
            if (fileFormat != null && !fileFormat.trim().isEmpty()) {
                try {
                    FileFormat.Type formatType = FileFormat.Type.valueOf(fileFormat.trim());
                    predicates.add(criteriaBuilder.equal(root.get("fileFormat").get("type"), formatType));
                } catch (IllegalArgumentException e) {
                    // Invalid format - ignore filter
                }
            }

            return criteriaBuilder.and(predicates.toArray(new Predicate[0]));
        };
    }

    /**
     * Search by file name
     */
    public static Specification<UploadedFile> fileNameContains(String fileName) {
        return (root, query, criteriaBuilder) -> {
            if (fileName == null || fileName.trim().isEmpty()) {
                return criteriaBuilder.conjunction();
            }
            String likePattern = "%" + fileName.trim().toLowerCase() + "%";
            return criteriaBuilder.like(
                    criteriaBuilder.lower(root.get("fileName").get("value")),
                    likePattern
            );
        };
    }

    /**
     * Filter by status
     */
    public static Specification<UploadedFile> hasStatus(UploadStatus status) {
        return (root, query, criteriaBuilder) -> {
            if (status == null) {
                return criteriaBuilder.conjunction();
            }
            return criteriaBuilder.equal(root.get("status"), status);
        };
    }

    /**
     * Filter by file format
     */
    public static Specification<UploadedFile> hasFileFormat(FileFormat format) {
        return (root, query, criteriaBuilder) -> {
            if (format == null) {
                return criteriaBuilder.conjunction();
            }
            return criteriaBuilder.equal(root.get("fileFormat"), format);
        };
    }
}
