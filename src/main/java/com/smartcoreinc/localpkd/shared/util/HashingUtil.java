package com.smartcoreinc.localpkd.shared.util;

import com.smartcoreinc.localpkd.shared.exception.InfrastructureException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

public final class HashingUtil {

    private HashingUtil() {
        // Private constructor to prevent instantiation
    }

    public static String calculateSha256(byte[] data) {
        if (data == null) {
            return null;
        }
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(data);
            StringBuilder hexString = new StringBuilder(2 * hash.length);
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) {
                    hexString.append('0');
                }
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (NoSuchAlgorithmException e) {
            // This should never happen for SHA-256
            throw new InfrastructureException("HASHING_ERROR", "Could not calculate SHA-256 hash", e);
        }
    }
}
