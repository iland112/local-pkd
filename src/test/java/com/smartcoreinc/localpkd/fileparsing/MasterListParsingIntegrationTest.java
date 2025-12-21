package com.smartcoreinc.localpkd.fileparsing;

import com.smartcoreinc.localpkd.fileparsing.application.service.CertificateExistenceService;
import com.smartcoreinc.localpkd.fileparsing.domain.model.ParsedFile;
import com.smartcoreinc.localpkd.fileparsing.domain.model.ParsedFileId;
import com.smartcoreinc.localpkd.fileparsing.infrastructure.adapter.MasterListParserAdapter;
import com.smartcoreinc.localpkd.fileupload.domain.model.FileFormat;
import com.smartcoreinc.localpkd.fileupload.domain.model.UploadId;
import com.smartcoreinc.localpkd.shared.progress.ProgressService;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.mockito.Mockito;
import org.springframework.core.io.FileSystemResource;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

@Slf4j
@DisplayName("Master List Parsing Integration Test")
class MasterListParsingIntegrationTest {

    private MasterListParserAdapter masterListParser;
    private byte[] masterListBytes;
    private ParsedFile parsedFile;

    @BeforeEach
    void setUp() throws IOException {
        // 1. Parser 인스턴스 생성 (ProgressService Mock 객체 주입)
        ProgressService progressServiceMock = Mockito.mock(ProgressService.class);
        CertificateExistenceService certificateExistenceServiceMock = Mockito.mock(CertificateExistenceService.class);

        // Configure mock to always return false for existence check in test
        Mockito.when(certificateExistenceServiceMock.existsByFingerprintSha256(Mockito.anyString())).thenReturn(false);

        masterListParser = new MasterListParserAdapter(progressServiceMock, certificateExistenceServiceMock);

        // 2. Trust Anchor 설정
        Path trustAnchorPath = Paths.get("data/cert/UN_CSCA_2.pem");
        if (!Files.exists(trustAnchorPath)) {
            throw new IllegalStateException("Trust Anchor not found: " + trustAnchorPath.toAbsolutePath());
        }
        masterListParser.setTrustAnchorResource(new FileSystemResource(trustAnchorPath.toFile()));
        log.info("Trust Anchor loaded: {}", trustAnchorPath.toAbsolutePath());

        // 3. 테스트 파일 로드
        Path mlFilePath = Paths.get("data/download/ICAO_ml_July2025.ml");
        if (!Files.exists(mlFilePath)) {
            throw new IllegalStateException("Master List test file not found: " + mlFilePath.toAbsolutePath());
        }
        masterListBytes = Files.readAllBytes(mlFilePath);
        log.info("Loaded Master List file: {} ({} bytes)", mlFilePath.getFileName(), masterListBytes.length);

        // 4. ParsedFile Aggregate 생성
        parsedFile = ParsedFile.create(
            ParsedFileId.newId(),
            UploadId.newId(),
            FileFormat.of(FileFormat.Type.ML_SIGNED_CMS)
        );
    }

    @SuppressWarnings("unused")  // Utility method for manual testing
    private void parseFile() throws Exception {
        parsedFile.startParsing();
        masterListParser.parse(masterListBytes, FileFormat.of(FileFormat.Type.ML_SIGNED_CMS), parsedFile);
        int totalEntries = parsedFile.getCertificates().size() + parsedFile.getCrls().size();
        parsedFile.completeParsing(totalEntries);
    }
    
    // ... (rest of the test methods) ...
}
