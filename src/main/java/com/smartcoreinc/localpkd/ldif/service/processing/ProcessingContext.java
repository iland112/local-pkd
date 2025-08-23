package com.smartcoreinc.localpkd.ldif.service.processing;

import java.util.HashMap;
import java.util.Map;

public class ProcessingContext {
    private final int recordNumber;
    private final String countryCode;
    private final Map<String, Object> sharedData = new HashMap<>();
    
    public ProcessingContext(int recordNumber, String countryCode) {
        this.recordNumber = recordNumber;
        this.countryCode = countryCode;
    }
    
    public int getRecordNumber() { return recordNumber; }
    public String getCountryCode() { return countryCode; }
    
    public void setSharedData(String key, Object value) {
        sharedData.put(key, value);
    }
    
    @SuppressWarnings("unchecked")
    public <T> T getSharedData(String key) {
        return (T) sharedData.get(key);
    }
}
