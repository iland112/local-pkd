package com.smartcoreinc.localpkd.icao;

import com.unboundid.ldap.sdk.Entry;
import com.unboundid.ldap.sdk.LDAPConnection;
import com.unboundid.ldap.sdk.LDAPConnectionPool;
import com.unboundid.ldap.sdk.SearchResult;
import com.unboundid.ldap.sdk.SearchScope;

public class LdapPoolConnectionTest {
    public static void main(String[] args) {
        try {
            LDAPConnection ldapConnection = new LDAPConnection(
                "192.168.100.10",
                389,
                "cn=admin,dc=ldap,dc=smartcoreinc,dc=com",
                "core"
            );

            LDAPConnectionPool connectionPool = new LDAPConnectionPool(ldapConnection, 5, 10);

            SearchResult result = connectionPool.search("dc=ldap, dc=smartcoreinc,dc=com", SearchScope.SUB, "(objectClass=*)");
            System.out.println("결과 개수: " + result.getEntryCount());
            
            // 예: 테스트 항목 추가
            Entry entry = new Entry("cn=testuser,ou=People,dc=ldap,dc=smartcoreinc,dc=com");
            entry.addAttribute("objectClass", "inetOrgPerson");
            entry.addAttribute("sn", "User");
            entry.addAttribute("cn", "testuser");
            connectionPool.add(entry);

            // 풀은 종료 시 명시적으로 close
            connectionPool.close();

        } catch (Exception e) {
            e.printStackTrace();
        }

    }
}
