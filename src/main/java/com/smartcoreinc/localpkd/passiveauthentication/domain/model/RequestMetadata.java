package com.smartcoreinc.localpkd.passiveauthentication.domain.model;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.AccessLevel;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * Request metadata for audit trail.
 *
 * <p>Captures information about who requested the verification, from where, and when.
 * Used for security auditing and compliance.
 */
@Embeddable
@Getter
@EqualsAndHashCode
@NoArgsConstructor(access = AccessLevel.PROTECTED)  // For JPA
public class RequestMetadata {

    @Column(name = "request_ip_address", length = 45)  // IPv6 support (max 45 chars)
    private String ipAddress;

    @Column(name = "request_user_agent", columnDefinition = "TEXT")
    private String userAgent;

    @Column(name = "requested_by", length = 100)
    private String requestedBy;  // User ID, API Key, or System Name

    /**
     * Create RequestMetadata.
     *
     * @param ipAddress client IP address (IPv4 or IPv6)
     * @param userAgent HTTP User-Agent header
     * @param requestedBy identifier of requester (user ID, API key, system name)
     * @return RequestMetadata instance
     */
    public static RequestMetadata of(String ipAddress, String userAgent, String requestedBy) {
        return new RequestMetadata(ipAddress, userAgent, requestedBy);
    }

    /**
     * Create RequestMetadata with IP address only.
     *
     * @param ipAddress client IP address
     * @return RequestMetadata instance
     */
    public static RequestMetadata ofIpOnly(String ipAddress) {
        return new RequestMetadata(ipAddress, null, null);
    }

    private RequestMetadata(String ipAddress, String userAgent, String requestedBy) {
        this.ipAddress = ipAddress;
        this.userAgent = userAgent;
        this.requestedBy = requestedBy;
    }
}
