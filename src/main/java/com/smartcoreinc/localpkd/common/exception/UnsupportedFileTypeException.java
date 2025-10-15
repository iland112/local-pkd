package com.smartcoreinc.localpkd.common.exception;

public class UnsupportedFileTypeException extends RuntimeException {

    private final String fileExtension;

    public UnsupportedFileTypeException(String message) {
        super(message);
        this.fileExtension = null;
    }

    public UnsupportedFileTypeException(String fileExtension, String message) {
        super(String.format("[%s] %s", fileExtension, message));
        this.fileExtension = fileExtension;
    }

    public UnsupportedFileTypeException(String message, Throwable cause) {
        super(message, cause);
        this.fileExtension = null;
    }

    public String getFileExtension() {
        return fileExtension;
    }
}
