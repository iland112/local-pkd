package com.smartcoreinc.localpkd.ldif.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class ValidationResult {
    private final TrustChainValidationResult status;
    private final String message;
}
