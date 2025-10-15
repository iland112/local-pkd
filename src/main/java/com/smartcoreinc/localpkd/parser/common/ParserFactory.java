package com.smartcoreinc.localpkd.parser.common;

import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Service;

import com.smartcoreinc.localpkd.common.enums.FileFormat;
import com.smartcoreinc.localpkd.common.enums.FileType;
import com.smartcoreinc.localpkd.common.exception.UnsupportedFileTypeException;

@Service
public class ParserFactory {
    private final List<FileParser> parsers = new ArrayList<>();

    public FileParser getParser(FileType fileType, FileFormat fileFormat) {
        return parsers.stream()
            .filter(p -> p.supports(fileType, fileFormat))
            .findFirst()
            .orElseThrow(() -> new UnsupportedFileTypeException(fileType.getDisplayName()));
    }
}
