package com.smartcoreinc.localpkd.fileupload.application.usecase;

import com.smartcoreinc.localpkd.fileupload.application.response.UploadStatisticsResponse;
import com.smartcoreinc.localpkd.fileupload.domain.model.UploadStatus;
import com.smartcoreinc.localpkd.fileupload.domain.repository.UploadedFileRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class GetUploadStatisticsUseCase {

    private final UploadedFileRepository uploadedFileRepository;

    public UploadStatisticsResponse execute() {
        long totalCount = uploadedFileRepository.count();
        long successCount = uploadedFileRepository.countByStatus(UploadStatus.COMPLETED);
        long failedCount = uploadedFileRepository.countByStatus(UploadStatus.FAILED);

        double successRate = totalCount > 0 ? (double) successCount / totalCount * 100 : 0.0;

        return UploadStatisticsResponse.builder()
                .totalCount(totalCount)
                .successCount(successCount)
                .failedCount(failedCount)
                .successRate(successRate)
                .build();
    }
}
