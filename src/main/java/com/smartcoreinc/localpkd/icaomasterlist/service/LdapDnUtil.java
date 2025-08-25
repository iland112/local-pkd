package com.smartcoreinc.localpkd.icaomasterlist.service;

import java.security.cert.X509Certificate;
import java.util.HashMap;
import java.util.Map;

import javax.naming.ldap.LdapName;
import javax.naming.ldap.Rdn;
import javax.security.auth.x500.X500Principal;

public class LdapDnUtil {
    // OID → 일반 LDAP 이름 매핑
    private static final Map<String, String> OID_MAP = new HashMap<>();
    static {
        OID_MAP.put("2.5.4.3", "CN");
        OID_MAP.put("2.5.4.6", "C");
        OID_MAP.put("2.5.4.7", "L");
        OID_MAP.put("2.5.4.8", "ST");
        OID_MAP.put("2.5.4.10", "O");
        OID_MAP.put("2.5.4.11", "OU");
        OID_MAP.put("1.2.840.113549.1.9.1", "emailAddress");
        // 필요시 다른 OID 추가
    }

    public static String getLdapCompatibleDn(X509Certificate certificate) throws Exception {
        X500Principal principal = certificate.getIssuerX500Principal(); // issuer 기준
        LdapName ldapName = new LdapName(principal.getName()); // RFC2253 문자열 파싱
        StringBuilder sb = new StringBuilder();

        // RDN 순서를 LDAP가 기대하는 순서로 뒤집어서 생성
        for (int i = ldapName.size() - 1; i >= 0; i--) {
            Rdn rdn = ldapName.getRdn(i);
            String type = rdn.getType();
            String value = rdnValueToString(rdn.getValue()); // ← 여기 수정
            if (OID_MAP.containsKey(type))
                type = OID_MAP.get(type);
            value = escapeLdapValue(value);
            sb.append(type).append("=").append(value);
            if (i > 0)
                sb.append(",");
        }

        return sb.toString();
    }

    private static String rdnValueToString(Object value) {
        if (value instanceof byte[]) {
            // byte[] → Hex 문자열
            byte[] bytes = (byte[]) value;
            StringBuilder hex = new StringBuilder();
            for (byte b : bytes) {
                hex.append(String.format("%02X", b));
            }
            return hex.toString();
        } else {
            return value.toString();
        }
    }

    private static String escapeLdapValue(String value) {
        // 쉼표, 플러스, 쌍따옴표, 역슬래시, <, >, ; 등 이스케이프
        return value.replace("\\", "\\\\")
                .replace(",", "\\,")
                .replace("+", "\\+")
                .replace("\"", "\\\"")
                .replace("<", "\\<")
                .replace(">", "\\>")
                .replace(";", "\\;");
    }
}
