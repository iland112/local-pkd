package com.smartcoreinc.localpkd.sse.event;

public record UploadEvent(
    String sessionId,
    double progress,
    String fileName,
    long uloadedBytes,
    long totalBytes,
    String status,
    String message
) {

}
