package com.smartcoreinc.localpkd.icao;

import java.io.FileInputStream;
import java.io.InputStream;
import com.smartcoreinc.localpkd.icao.service.CscaLdapAddService;
import com.unboundid.ldap.sdk.LDAPConnection;
import com.unboundid.ldap.sdk.LDAPConnectionPool;
import com.unboundid.ldap.sdk.SearchResult;
import com.unboundid.ldap.sdk.SearchScope;

public class IcaoMasterListParsingTest {
    public static void main(String[] args) {
        try {
            InputStream is = new FileInputStream("ICAO_ml_01April2025.ml");
            LDAPConnection ldapConnection = new LDAPConnection(
                "192.168.100.10",
                389,
                "cn=admin,dc=ldap,dc=smartcoreinc,dc=com",
                "core"
            );

            LDAPConnectionPool connectionPool = new LDAPConnectionPool(ldapConnection, 5, 10);

            SearchResult result = connectionPool.search("dc=ldap, dc=smartcoreinc,dc=com", SearchScope.SUB, "(objectClass=*)");
            System.out.println("결과 개수: " + result.getEntryCount());
            
            CscaLdapAddService ldapService = new CscaLdapAddService(connectionPool);
            
            // CscaMasterListParser parser = new CscaMasterListParser(ldapService);
            // List<X509Certificate> x509Certificates = parser.parseMasterList(is.readAllBytes(), false);
            
            // for (X509Certificate x509Certificate : x509Certificates) {
            //     System.out.println(x509Certificate.getSubjectX500Principal().getName());
            //     System.out.println(x509Certificate.getSerialNumber());
            // }
            is.close();
            connectionPool.close();

        } catch (Exception e) {
            e.printStackTrace();
        }

    }

    private static String extractCountryCode(String dn) {
        for (String part : dn.split(",")) {
            part = part.trim();
            if (part.startsWith("C=")) {
                return part.substring(2).toUpperCase();
            }
        }
        return "UNKNOWN";
    }
}
