package com.smartcoreinc.localpkd.parser.common.exception;

public class ParsingException extends RuntimeException {

    private String fileId;
    private String originalFileName;

    public ParsingException(String message) {
        super(message);
    }

    public ParsingException(String fileId, String originalFileName, String message) {
        super(message);
        this.fileId = fileId;
        this.originalFileName = originalFileName;
    }

    public ParsingException(String fileId, String originalFileName, String message, Exception e) {
        super(message, e);
        this.fileId = fileId;
        this.originalFileName = originalFileName;
    }
}
