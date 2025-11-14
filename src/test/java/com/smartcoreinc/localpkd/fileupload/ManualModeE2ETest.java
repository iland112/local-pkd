package com.smartcoreinc.localpkd.fileupload;

import com.smartcoreinc.localpkd.fileupload.domain.model.ProcessingMode;
import com.smartcoreinc.localpkd.fileupload.domain.model.UploadId;
import com.smartcoreinc.localpkd.fileupload.domain.model.UploadedFile;
import com.smartcoreinc.localpkd.fileupload.domain.model.FileName;
import com.smartcoreinc.localpkd.fileupload.domain.model.FileHash;
import com.smartcoreinc.localpkd.fileupload.domain.model.FileSize;
import com.smartcoreinc.localpkd.fileupload.domain.model.FileFormat;
import com.smartcoreinc.localpkd.fileupload.domain.repository.UploadedFileRepository;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * MANUAL 모드 E2E 테스트
 *
 * <p><b>테스트 시나리오</b>:</p>
 * <ol>
 *   <li>MANUAL 모드로 파일 업로드</li>
 *   <li>파싱 시작 API 호출</li>
 *   <li>검증 시작 API 호출</li>
 *   <li>LDAP 업로드 시작 API 호출</li>
 *   <li>처리 상태 조회 및 검증</li>
 * </ol>
 */
@Slf4j
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class ManualModeE2ETest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UploadedFileRepository uploadedFileRepository;

    private UUID testUploadId;
    private UploadId uploadIdVO;

    @BeforeEach
    void setUp() {
        testUploadId = UUID.randomUUID();
        uploadIdVO = new UploadId(testUploadId);

        // MANUAL 모드로 테스트 파일 생성
        UploadedFile testFile = UploadedFile.createWithMetadata(
            uploadIdVO,
            FileName.of("test-file-002-complete-000001.ldif"),
            FileHash.of("a".repeat(64)),
            FileSize.ofBytes(1000L),
            FileFormat.of(FileFormat.Type.CSCA_COMPLETE_LDIF),
            null,  // collectionNumber
            null,  // version
            null,  // filePath
            ProcessingMode.MANUAL
        );

        uploadedFileRepository.save(testFile);
        log.info("Test file created: uploadId={}, mode=MANUAL", testUploadId);
    }

    @Test
    @DisplayName("Step 1: MANUAL 모드 파일이 제대로 생성되었는지 확인")
    void testManualModeFileCreation() {
        Optional<UploadedFile> file = uploadedFileRepository.findById(uploadIdVO);
        assertThat(file).isPresent();
        assertThat(file.get().isManualMode()).isTrue();
        log.info("✅ Step 1 PASSED: MANUAL 모드 파일 생성 확인");
    }

    @Test
    @DisplayName("Step 2: 파싱 시작 API - MANUAL 모드만 허용")
    void testParsingStartAPI() throws Exception {
        mockMvc.perform(
            post("/api/processing/parse/{uploadId}", testUploadId.toString())
                .contentType(MediaType.APPLICATION_JSON)
        )
        .andDo(print())
        .andExpect(status().isAccepted());

        log.info("✅ Step 2 PASSED: 파싱 시작 API 호출 성공");
    }

    @Test
    @DisplayName("Step 3: AUTO 모드 파일로 파싱 API 호출 시 거부")
    void testParsingStartAPIShouldRejectAutoMode() throws Exception {
        // AUTO 모드 파일 생성
        UUID autoUploadId = UUID.randomUUID();
        UploadId autoUploadIdVO = new UploadId(autoUploadId);
        UploadedFile autoFile = UploadedFile.createWithMetadata(
            autoUploadIdVO,
            FileName.of("auto-file-002-complete-000001.ldif"),
            FileHash.of("b".repeat(64)),
            FileSize.ofBytes(1000L),
            FileFormat.of(FileFormat.Type.CSCA_COMPLETE_LDIF),
            null,  // collectionNumber
            null,  // version
            null,  // filePath
            ProcessingMode.AUTO
        );
        uploadedFileRepository.save(autoFile);

        // AUTO 모드에서는 파싱 API 호출 시 BAD_REQUEST
        mockMvc.perform(
            post("/api/processing/parse/{uploadId}", autoUploadId.toString())
                .contentType(MediaType.APPLICATION_JSON)
        )
        .andDo(print())
        .andExpect(status().isBadRequest());

        log.info("✅ Step 3 PASSED: AUTO 모드 파일에서 파싱 API 거부 확인");
    }

    @Test
    @DisplayName("Step 4: 검증 시작 API - MANUAL 모드만 허용")
    void testValidationStartAPI() throws Exception {
        mockMvc.perform(
            post("/api/processing/validate/{uploadId}", testUploadId.toString())
                .contentType(MediaType.APPLICATION_JSON)
        )
        .andDo(print())
        .andExpect(status().isAccepted());

        log.info("✅ Step 4 PASSED: 검증 시작 API 호출 성공");
    }

    @Test
    @DisplayName("Step 5: LDAP 업로드 시작 API - MANUAL 모드만 허용")
    void testLdapUploadStartAPI() throws Exception {
        mockMvc.perform(
            post("/api/processing/upload-to-ldap/{uploadId}", testUploadId.toString())
                .contentType(MediaType.APPLICATION_JSON)
        )
        .andDo(print())
        .andExpect(status().isAccepted());

        log.info("✅ Step 5 PASSED: LDAP 업로드 시작 API 호출 성공");
    }

    @Test
    @DisplayName("Step 6: 처리 상태 조회 API")
    void testProcessingStatusAPI() throws Exception {
        mockMvc.perform(
            get("/api/processing/status/{uploadId}", testUploadId.toString())
                .contentType(MediaType.APPLICATION_JSON)
        )
        .andDo(print())
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.uploadId").value(testUploadId.toString()))
        .andExpect(jsonPath("$.processingMode").value("MANUAL"));

        log.info("✅ Step 6 PASSED: 처리 상태 조회 API 성공");
    }

    @Test
    @DisplayName("E2E: 완전한 MANUAL 모드 워크플로우")
    void testCompleteManualModeWorkflow() throws Exception {
        log.info("🚀 E2E 테스트 시작: MANUAL 모드 완전한 워크플로우");

        // 1. 초기 상태 확인
        mockMvc.perform(
            get("/api/processing/status/{uploadId}", testUploadId.toString())
        )
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.processingMode").value("MANUAL"));
        log.info("✅ Phase 1: 초기 상태 확인");

        // 2. 파싱 시작
        mockMvc.perform(
            post("/api/processing/parse/{uploadId}", testUploadId.toString())
        )
        .andExpect(status().isAccepted());
        log.info("✅ Phase 2: 파싱 시작");

        // 3. 검증 시작
        mockMvc.perform(
            post("/api/processing/validate/{uploadId}", testUploadId.toString())
        )
        .andExpect(status().isAccepted());
        log.info("✅ Phase 3: 검증 시작");

        // 4. LDAP 업로드 시작
        mockMvc.perform(
            post("/api/processing/upload-to-ldap/{uploadId}", testUploadId.toString())
        )
        .andExpect(status().isAccepted());
        log.info("✅ Phase 4: LDAP 업로드 시작");

        // 5. 최종 상태 확인
        mockMvc.perform(
            get("/api/processing/status/{uploadId}", testUploadId.toString())
        )
        .andExpect(status().isOk());
        log.info("✅ Phase 5: 최종 상태 확인");

        log.info("✅✅✅ E2E 테스트 완료: MANUAL 모드 완전한 워크플로우");
    }
}
