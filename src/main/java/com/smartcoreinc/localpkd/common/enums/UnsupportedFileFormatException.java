package com.smartcoreinc.localpkd.common.enums;

public class UnsupportedFileFormatException extends RuntimeException {
    public UnsupportedFileFormatException(String message) {
        super(message);
    }
    
    public UnsupportedFileFormatException(String message, Throwable cause) {
        super(message, cause);
    }
}
