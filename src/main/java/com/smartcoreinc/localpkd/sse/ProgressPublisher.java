package com.smartcoreinc.localpkd.sse;

import java.util.HashSet;
import java.util.Set;

import org.springframework.stereotype.Component;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
public class ProgressPublisher {

    // 파싱 진행 상테 정보 Listener 컨테이너
    private final Set<ProgressListener> progressListeners = new HashSet<>();

    public ProgressPublisher() {

    }

    public void addProgressListener(ProgressListener progressListener) {
        progressListeners.add(progressListener);
    }

    public void removeProgressListener(ProgressListener progressListener) {
        progressListeners.remove(progressListener);
    }

    public void notifyProgressListeners(ProgressEvent progressEvent) {
        for (ProgressListener progressListener : progressListeners) {
            log.debug("current progress: {}/{} ({}%)", progressEvent.processedCount(), progressEvent.totalCount(), (int) (progressEvent.progress().value() * 100));
            progressListener.onProgress(progressEvent.progress(), progressEvent.processedCount(), progressEvent.totalCount(), "Current Processing Subject: " + progressEvent.message());
        }
    }

    // private void sleepQuietly(int ms) {
    //     try {
    //         Thread.sleep(ms);
    //     } catch (InterruptedException e) {
    //         throw new RuntimeException(e);
    //     }
    // }
}
