package com.smartcoreinc.localpkd.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.unboundid.ldap.sdk.LDAPConnection;
import com.unboundid.ldap.sdk.LDAPConnectionPool;
import com.unboundid.ldap.sdk.LDAPException;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Configuration
public class UnboundLdapConfig {

    private final LdapProperties props;

    public UnboundLdapConfig(LdapProperties props) {
        this.props = props;
    }

    @Bean(destroyMethod = "close")
    public LDAPConnectionPool readPool() {
        try {
            LDAPConnection connection = new LDAPConnection(
                props.getRead().getHost(),
                props.getRead().getPort(),
                props.getBind().getDn(),
                props.getBind().getPassword()
            );
            log.info("LDAP Read Pool 연결 성공: {}:{}", props.getRead().getHost(), props.getRead().getPort());
            return new LDAPConnectionPool(connection, 5);
        } catch (LDAPException e) {
            log.error("LDAP Read Pool 연결 실패", e);
            throw new RuntimeException("LDAP Read Pool 연결 실패: " + e.getMessage());
        }
    }

    @Bean(destroyMethod = "close")
    public LDAPConnectionPool writePool() {
        try {
            LDAPConnection connection = new LDAPConnection(
                props.getWrite().getHost(),
                props.getWrite().getPort(),
                props.getBind().getDn(),
                props.getBind().getPassword()
            );
            log.info("LDAP Write Pool 연결 성공: {}:{}", props.getWrite().getHost(), props.getWrite().getPort());
            return new LDAPConnectionPool(connection, 3);
        } catch (LDAPException e) {
            log.error("LDAP Write Pool 연결 실패", e);
            throw new RuntimeException("LDAP 연결 실패: " + e.getMessage());
        }
    }
}
