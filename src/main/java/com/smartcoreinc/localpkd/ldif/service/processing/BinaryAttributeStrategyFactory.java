package com.smartcoreinc.localpkd.ldif.service.processing;

import java.util.Comparator;
import java.util.List;

import org.springframework.stereotype.Component;

@Component
public class BinaryAttributeStrategyFactory {
    private final List<BinaryAttributeProcessingStrategy> strategies;
    
    public BinaryAttributeStrategyFactory(List<BinaryAttributeProcessingStrategy> strategies) {
        this.strategies = strategies;
    }
    
    public BinaryAttributeProcessingStrategy getStrategy(String attributeName) {
        return strategies.stream()
            .filter(strategy -> strategy.supports(attributeName))
            .min(Comparator.comparingInt(BinaryAttributeProcessingStrategy::getPriority))
            .orElse(null);
    }
    
    public List<BinaryAttributeProcessingStrategy> getAllStrategies() {
        return strategies;
    }
}
