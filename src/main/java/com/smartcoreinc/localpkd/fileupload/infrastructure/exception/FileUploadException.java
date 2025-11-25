package com.smartcoreinc.localpkd.fileupload.infrastructure.exception;

import com.smartcoreinc.localpkd.shared.exception.BusinessException;
import lombok.Getter;

import java.util.Map;

@Getter
public class FileUploadException extends BusinessException {

    public FileUploadException(String errorCode, String message) {
        super(errorCode, message);
    }

    public static class DuplicateFileException extends FileUploadException {
        private final Map<String, Object> details;

        public DuplicateFileException(String message, Map<String, Object> details) {
            super("DUPLICATE_FILE", message);
            this.details = details;
        }

        public Map<String, Object> getDetails() {
            return details;
        }
    }
}
