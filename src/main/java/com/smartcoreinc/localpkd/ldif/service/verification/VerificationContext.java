package com.smartcoreinc.localpkd.ldif.service.verification;

import java.util.HashMap;
import java.util.Map;

public class VerificationContext {
    private final int recordNumber;
    private final String countryCode;
    private final Map<String, Object> attributes = new HashMap<>();

    public VerificationContext(int recordNumber, String countryCode) {
        this.recordNumber = recordNumber;
        this.countryCode = countryCode;
    }

    public int getRecordNumber() { return recordNumber; }
    public String getCountryCode() { return countryCode; }
    
    public void setAttribute(String key, Object value) {
        attributes.put(key, value);
    }
    
    @SuppressWarnings("unchecked")
    public <T> T getAttribute(String key) {
        return (T) attributes.get(key);
    }
}
