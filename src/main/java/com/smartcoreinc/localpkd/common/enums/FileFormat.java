package com.smartcoreinc.localpkd.common.enums;

import lombok.Getter;

@Getter
public enum FileFormat {
    ML_CMS("ml", "CMS/PKCS7 format (.ml file)"),
    ML_LDIF("ldif", "LDIF format for Master List"),
    DSC_CRL_LDIF("ldif", "LDIF format for DSC and CRL"),
    NON_CONFORMANT_LDIF("ldif", "LDIF format for Deviation");
    
    private final String extension;
    private final String description;
    
    FileFormat(String extension, String description) {
        this.extension = extension;
        this.description = description;
    }
    
    /**
     * 파일명으로부터 포맷 감지
     */
    public static FileFormat detectFromFilename(String filename) {
        if (filename == null || filename.isEmpty()) {
            throw new IllegalArgumentException("Filename cannot be null or empty");
        }
        
        String lower = filename.toLowerCase();
        
        // .ml 파일 (CMS 포맷)
        if (lower.endsWith(".ml")) {
            return ML_CMS;
        }
        
        // LDIF 파일들
        if (lower.endsWith(".ldif")) {
            // ML_XX.ldif 패턴 (Master List 증분)
            if (lower.matches("ml_[a-z]{2}\\.ldif")) {
                return ML_LDIF;
            }
            // CscaDS_XX.ldif 패턴 (DSC)
            if (lower.matches("cscads_[a-z]{2}\\.ldif")) {
                return DSC_CRL_LDIF;
            }
            
            // NON_CONFORMANT 패턴
            if (lower.contains("deviation")) {
                return NON_CONFORMANT_LDIF;
            }
        }
                
        throw new UnsupportedFileFormatException("Unsupported file format: " + filename);
    }
    
    /**
     * 파일 타입 추출
     */
    public FileType getFileType() {
        return switch (this) {
            case ML_CMS, ML_LDIF -> FileType.ML;
            case DSC_CRL_LDIF -> FileType.DSC_CRL;
            case NON_CONFORMANT_LDIF -> FileType.NON_CONFORMANT;
        };
    }
}
