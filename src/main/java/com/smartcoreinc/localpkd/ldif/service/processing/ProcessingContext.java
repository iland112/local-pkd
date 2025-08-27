package com.smartcoreinc.localpkd.ldif.service.processing;

import java.util.HashMap;
import java.util.Map;

import com.smartcoreinc.localpkd.enums.EntryType;

public class ProcessingContext {
    private final int recordNumber;
    private final String countryCode;
    private final EntryType entryType;
    private final Map<String, Object> sharedData = new HashMap<>();
    
    public ProcessingContext(int recordNumber, String countryCode, EntryType entryType) {
        this.recordNumber = recordNumber;
        this.countryCode = countryCode;
        this.entryType = entryType;
    }
    
    public int getRecordNumber() { return recordNumber; }
    public String getCountryCode() { return countryCode; }
    public EntryType getEntryType() { return entryType; }
    
    public void setSharedData(String key, Object value) {
        sharedData.put(key, value);
    }
    
    @SuppressWarnings("unchecked")
    public <T> T getSharedData(String key) {
        return (T) sharedData.get(key);
    }
}
