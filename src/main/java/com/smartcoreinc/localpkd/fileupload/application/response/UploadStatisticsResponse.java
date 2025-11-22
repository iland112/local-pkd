package com.smartcoreinc.localpkd.fileupload.application.response;

import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class UploadStatisticsResponse {
    long totalCount;
    long successCount;
    long failedCount;
    double successRate;
}
