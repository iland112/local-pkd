package com.smartcoreinc.localpkd.common.exception;

/**
 * 지원하지 않는
 */
public class UnsupportedFileFormatException extends RuntimeException {
    
    private final String filename;

    public UnsupportedFileFormatException(String message) {
        super(message);
        this.filename = null;
    }

    public UnsupportedFileFormatException(String filename, String message) {
        super(String.format("[%s] %s", filename, message));
        this.filename = filename;
    }

    public UnsupportedFileFormatException(String message, Throwable cause) {
        super(message, cause);
        this.filename = null;
    }

    public String getFilename() {
        return filename;
    }
}
