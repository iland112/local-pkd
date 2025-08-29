package com.smartcoreinc.localpkd.ldif.strategy;

import com.unboundid.ldap.sdk.Entry;

/**
 * Factory for getting appropriate validation strategies
 */
public interface StrategyFactory {
    /**
     * Get validation strategy for the given entry
     * 
     * @param entry LDAP entry to be validated
     * @return appropriate validation strategy, or null if no strategy available
     */
    EntryValidationStrategy getStrategy(Entry entry);
    
    /**
     * Check if strategy exists for the given entry
     */
    boolean hasStrategy(Entry entry);
    
    /**
     * Get all available strategy names
     */
    java.util.Set<String> getAvailableStrategies();
}
