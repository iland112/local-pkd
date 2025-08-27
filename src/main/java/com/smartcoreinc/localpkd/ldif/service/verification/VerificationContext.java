package com.smartcoreinc.localpkd.ldif.service.verification;

import java.util.HashMap;
import java.util.Map;

import com.smartcoreinc.localpkd.enums.EntryType;

public class VerificationContext {
    private final int recordNumber;
    private final String countryCode;
    private final EntryType entryType;
    private final Map<String, Object> attributes = new HashMap<>();

    public VerificationContext(int recordNumber, String countryCode, EntryType entryType) {
        this.recordNumber = recordNumber;
        this.countryCode = countryCode;
        this.entryType = entryType;
    }

    public int getRecordNumber() { return recordNumber; }
    public String getCountryCode() { return countryCode; }
    public EntryType getEntryType() { return entryType; }
    
    public void setAttribute(String key, Object value) {
        attributes.put(key, value);
    }
    
    @SuppressWarnings("unchecked")
    public <T> T getAttribute(String key) {
        return (T) attributes.get(key);
    }
}
