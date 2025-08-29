package com.smartcoreinc.localpkd.ldif.strategy;

import com.smartcoreinc.localpkd.ldif.dto.LdifEntryDto;
import com.unboundid.ldap.sdk.Entry;

/**
 * Base interface for entry validation strategies
 */
public interface EntryValidationStrategy {
    /**
     * Check if this strategy can handle the given entry
     */
    boolean canHandle(Entry entry);
    
    /**
     * Validate entry and convert to DTO
     * 
     * @param entry LDAP entry to validate and convert
     * @return validated LdifEntryDto
     */
    LdifEntryDto validateAndConvert(Entry entry);
    
    /**
     * Get strategy name/type
     */
    String getStrategyName();
}
