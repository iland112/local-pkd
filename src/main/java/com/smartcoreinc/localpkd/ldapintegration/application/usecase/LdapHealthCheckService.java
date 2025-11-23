package com.smartcoreinc.localpkd.ldapintegration.application.usecase;

import com.smartcoreinc.localpkd.ldapintegration.domain.port.LdapConnectionPort;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class LdapHealthCheckService implements LdapHealthCheckUseCase {

    private final LdapConnectionPort ldapConnectionPort;

    @Override
    public Map<String, Object> execute() {
        Map<String, Object> result = new HashMap<>();
        try {
            boolean isConnected = ldapConnectionPort.testConnection();
            result.put("success", isConnected);
            if (isConnected) {
                result.put("message", "LDAP 서버에 성공적으로 연결되었습니다.");
            } else {
                result.put("message", "LDAP 서버 연결 실패: 알 수 없는 오류");
            }
        } catch (Exception e) {
            log.error("LDAP health check failed: {}", e.getMessage());
            result.put("success", false);
            result.put("message", "LDAP 서버 연결 실패: " + e.getMessage());
        }
        return result;
    }
}
