package com.smartcoreinc.localpkd.parser.common.exception;

public class ParsingException extends RuntimeException {

    private String fieldId;
    private String originalFileName;

    public ParsingException(String message) {
        super(message);
    }

    public ParsingException(String fieldId, String originalFileName, String message) {
        super(message);
        this.fieldId = fieldId;
        this.originalFileName = originalFileName;
    }

    public ParsingException(String fieldId, String originalFileName, String message, Exception e) {
        super(message, e);
        this.fieldId = fieldId;
        this.originalFileName = originalFileName;
    }
}
