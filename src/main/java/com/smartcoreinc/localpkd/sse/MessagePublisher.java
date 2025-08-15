package com.smartcoreinc.localpkd.sse;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.springframework.stereotype.Component;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
public class MessagePublisher {

    // 파싱 진행 상테 정보 Listener 컨테이너
    private final Set<MessageListener> messageListeners = new HashSet<>();

    public MessagePublisher() {

    }

    public Set<MessageListener> getMessageListeners() {
        return messageListeners;
    }

    public void addMessageListener(MessageListener messageListener) {
        messageListeners.add(messageListener);
    }

    public void removeMessageListener(MessageListener messageListener) {
        messageListeners.remove(messageListener);
    }

    public void notifyToListeners(Map<String, Object> messageMap) {
        if (messageListeners.isEmpty()) {
            log.warn("Currently, There is no subscripted listeners");
            return;
        }

        messageMap.forEach((key, value) -> {
            log.debug("key: {}, value: {}", key, value.toString());
        });
        
        for (MessageListener messageListener : messageListeners) {
            messageListener.onMessage(messageMap);
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
