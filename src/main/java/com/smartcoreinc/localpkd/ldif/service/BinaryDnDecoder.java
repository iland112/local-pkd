package com.smartcoreinc.localpkd.ldif.service;

import java.io.UnsupportedEncodingException;
import java.util.Base64;

import lombok.extern.slf4j.Slf4j;

/**
 * ICAO PKD 에서 다운 받은 ldif 파일 안에는 `dn::` 로 시작하는 Base64로 인코딩된 바이너리 형태의
 * dn 값이 존재하므로 이를 평문으로 디코딩하는 작업이 필요함. 
 */
@Slf4j
public class BinaryDnDecoder {
    
    public static String decodeBase64Dn(String encodedDn) {
        try {
            // Remove the 'dn:: ' prefix
            String base64String = encodedDn.replace("dn:: ", "");
            // Decode the base64 string
            byte[] decodedBytes = Base64.getDecoder().decode(base64String);
            // Convert the decoded bytes to a String using UTF-8 encoding
            return new String(decodedBytes, "UTF-8");
        } catch (IllegalArgumentException | UnsupportedEncodingException e) {
            System.err.println("Failed to decode base64 DN: " + e.getMessage());
            return null;
        }
    }

    // public static void main(String[] args) {
    //     String base64Dn = "dn:: Y249Q1w9UEFcLE9cPVJlcMO6YmxpY2EgZGUgUGFuYW3DoVwsT1VcPUF1dG9yaWRhZCBkZSBQYXNhcG9ydGVzIGRlIFBhbmFtw6FcLENOPVBhbmFtYSBJRCBDU0NBLFNFUklBTE5VTUJFUj01K3NuPTA2MEIsbz1kc2MsYz1QQSxkYz1kYXRhLGRjPWRvd25sb2FkLGRjPXBrZCxkYz1pY2FvLGRjPWludA==";
    //     String decodedDn = decodeBase64Dn(base64Dn);
    //     System.out.println("Decoded DN: " + decodedDn);
    // }
}
