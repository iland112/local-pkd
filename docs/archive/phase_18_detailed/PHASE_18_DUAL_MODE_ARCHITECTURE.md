# Phase 18: Dual Mode Architecture 설계 (Auto vs Manual Processing)

**작성 일시**: 2025-10-30
**상태**: 상세 설계 단계

---

## 📋 Executive Summary

파일 업로드 후 데이터 처리 방식을 **두 가지 모드로 지원**합니다:

| 모드 | 특징 | 용도 |
|------|------|------|
| **Auto Mode** 🤖 | 업로드 → 자동 파싱 → 자동 검증 → 자동 LDAP | 대량 파일, 배치 처리 |
| **Manual Mode** 🎮 | 각 단계별 수동 Trigger | 개별 검증, 문제 디버깅 |

**목표**: 두 모드를 완벽하게 동시 지원하고, 필요에 따라 전환 가능

---

## 🏗️ 아키텍처 개요

### 데이터 흐름 비교

**Auto Mode**:
```
파일 업로드
    ↓ (FileUploadedEvent)
파일 파싱 (자동 시작) [SSE 진행률: 10-60%]
    ↓ (FileParsingCompletedEvent)
인증서 검증 (자동 시작) [SSE 진행률: 65-85%]
    ↓ (CertificatesValidatedEvent)
LDAP 등록 (자동 시작) [SSE 진행률: 90-100%]
    ↓
완료
```

**Manual Mode**:
```
파일 업로드
    ↓ (Status: RECEIVED)
[User clicks] "파싱 시작" Button
    ↓ (API: POST /api/processing/parse/{uploadId})
파일 파싱 [UI: 진행률 바, 결과 표시]
    ↓ (Status: PARSED)
[User clicks] "검증 시작" Button
    ↓ (API: POST /api/processing/validate/{uploadId})
인증서 검증 [UI: 검증 결과]
    ↓ (Status: VALIDATED)
[User clicks] "LDAP 등록" Button
    ↓ (API: POST /api/processing/upload-to-ldap/{uploadId})
LDAP 등록 [UI: 등록 결과]
    ↓
완료
```

---

## 🛠️ Backend 구현 설계

### 1. ProcessingMode Enum 추가

**파일**: `domain/model/ProcessingMode.java` (신규)

```java
package com.smartcoreinc.localpkd.fileupload.domain.model;

/**
 * 파일 처리 모드
 * - AUTO: 업로드 후 자동으로 모든 단계 처리 (Event-Driven)
 * - MANUAL: 각 단계를 사용자가 수동으로 Trigger
 */
public enum ProcessingMode {
    AUTO("자동 처리", "업로드 후 자동으로 파싱, 검증, LDAP 등록 처리"),
    MANUAL("수동 처리", "각 단계를 사용자가 수동으로 진행");

    private final String displayName;
    private final String description;

    ProcessingMode(String displayName, String description) {
        this.displayName = displayName;
        this.description = description;
    }

    public String getDisplayName() { return displayName; }
    public String getDescription() { return description; }
}
```

### 2. UploadedFile Entity 확장

**파일**: `domain/model/UploadedFile.java` (수정)

```java
@Entity
@Table(name = "uploaded_file")
public class UploadedFile extends AggregateRoot<UploadId> {
    // 기존 필드들...

    @Enumerated(EnumType.STRING)
    @Column(name = "processing_mode", nullable = false)
    private ProcessingMode processingMode = ProcessingMode.AUTO;  // 기본값: AUTO

    @Column(name = "manual_pause_at_step")
    private String manualPauseAtStep;  // MANUAL 모드에서 현재 정지 단계

    // Constructor
    public static UploadedFile create(
        UploadId id,
        FileName fileName,
        FileHash fileHash,
        FileSize fileSize,
        ProcessingMode processingMode) {  // NEW: 파라미터 추가

        UploadedFile file = new UploadedFile(id, fileName, fileHash, fileSize);
        file.processingMode = processingMode;
        file.registerEvent(new FileUploadedEvent(id, processingMode));
        return file;
    }

    // Getters
    public ProcessingMode getProcessingMode() { return processingMode; }
    public String getManualPauseAtStep() { return manualPauseAtStep; }

    // Setters for MANUAL mode
    public void pauseAtStep(String step) {
        this.manualPauseAtStep = step;
    }

    public void resumeFromStep(String step) {
        this.manualPauseAtStep = null;
    }
}
```

### 3. Database Migration

**파일**: `db/migration/V14__Add_Processing_Mode.sql` (신규)

```sql
ALTER TABLE uploaded_file ADD COLUMN (
    processing_mode VARCHAR(20) NOT NULL DEFAULT 'AUTO',
    manual_pause_at_step VARCHAR(50)
);

CREATE INDEX idx_uploaded_file_processing_mode ON uploaded_file(processing_mode);
```

### 4. Domain Events 확장

**FileUploadedEvent.java** (수정):
```java
public class FileUploadedEvent extends DomainEvent {
    private final UploadId uploadId;
    private final ProcessingMode processingMode;  // NEW

    public FileUploadedEvent(UploadId uploadId, ProcessingMode processingMode) {
        this.uploadId = uploadId;
        this.processingMode = processingMode;
    }

    public ProcessingMode getProcessingMode() { return processingMode; }
}
```

### 5. Event Handler 조건부 처리

**파일**: `application/event/FileUploadEventHandler.java` (수정)

```java
@Slf4j
@Component
@RequiredArgsConstructor
public class FileUploadEventHandler {

    private final ParseLdifFileUseCase parseLdifFileUseCase;
    private final ParseMasterListFileUseCase parseMasterListFileUseCase;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handleFileUploaded(FileUploadedEvent event) {
        log.info("File uploaded: uploadId={}, mode={}",
            event.getUploadId(), event.getProcessingMode());

        // AUTO 모드인 경우만 자동 파싱 시작
        if (event.getProcessingMode() == ProcessingMode.AUTO) {
            startParsing(event.getUploadId());
        } else if (event.getProcessingMode() == ProcessingMode.MANUAL) {
            // MANUAL 모드: 사용자 입력 대기
            log.info("Manual mode: waiting for user to trigger parsing");
        }
    }

    private void startParsing(UploadId uploadId) {
        // 기존 파싱 로직
        // parseLdifFileUseCase.execute() 또는 parseMasterListFileUseCase.execute()
    }
}
```

**마찬가지로 다른 EventHandler들도 수정**:
- `ParsedFileEventHandler`: MANUAL 모드면 자동 검증 스킵
- `CertificateValidationEventHandler`: MANUAL 모드면 자동 LDAP 업로드 스킵

### 6. Manual Mode 처리 API Endpoints

**파일**: `controller/ProcessingController.java` (신규)

```java
package com.smartcoreinc.localpkd.fileupload.infrastructure.web;

@Slf4j
@RestController
@RequestMapping("/api/processing")
@RequiredArgsConstructor
public class ProcessingController {

    private final ParseLdifFileUseCase parseLdifFileUseCase;
    private final ParseMasterListFileUseCase parseMasterListFileUseCase;
    private final ValidateCertificateUseCase validateCertificateUseCase;
    private final UploadToLdapUseCase uploadToLdapUseCase;
    private final ProgressService progressService;

    /**
     * 파일 파싱 수동 시작
     * - MANUAL 모드인 경우만 호출 가능
     * - SSE로 진행률 전송
     */
    @PostMapping("/parse/{uploadId}")
    public ResponseEntity<ProcessingResponse> parseFile(
            @PathVariable UUID uploadId) {

        log.info("Manual parsing started: uploadId={}", uploadId);

        try {
            UploadedFile file = uploadedFileRepository.findById(UploadId.of(uploadId))
                .orElseThrow(() -> new RuntimeException("Upload not found"));

            // MANUAL 모드 확인
            if (file.getProcessingMode() != ProcessingMode.MANUAL) {
                return ResponseEntity.badRequest().body(
                    ProcessingResponse.error("This upload is in AUTO mode"));
            }

            // 파싱 실행
            progressService.sendProgress(uploadId, ProcessingStage.PARSING_STARTED, 10, "파싱 시작...");

            if (file.getFileFormat() == FileFormat.CSCA_COMPLETE_LDIF ||
                file.getFileFormat() == FileFormat.CSCA_DELTA_LDIF ||
                file.getFileFormat() == FileFormat.EMRTD_COMPLETE_LDIF ||
                file.getFileFormat() == FileFormat.EMRTD_DELTA_LDIF) {

                ParseLdifFileCommand command = ParseLdifFileCommand.builder()
                    .uploadId(uploadId)
                    .filePath(file.getLocalFilePath())
                    .build();

                parseLdifFileUseCase.execute(command);
            } else if (file.getFileFormat() == FileFormat.ML_SIGNED_CMS) {
                // Master List parsing
            }

            progressService.sendProgress(uploadId, ProcessingStage.PARSING_COMPLETED, 60, "파싱 완료");

            return ResponseEntity.ok(ProcessingResponse.success("파싱 완료"));

        } catch (Exception e) {
            log.error("Parsing error", e);
            progressService.sendProgress(uploadId, ProcessingStage.FAILED, 0, "파싱 실패: " + e.getMessage());
            return ResponseEntity.internalServerError().body(
                ProcessingResponse.error("파싱 중 오류: " + e.getMessage()));
        }
    }

    /**
     * 인증서 검증 수동 시작
     */
    @PostMapping("/validate/{uploadId}")
    public ResponseEntity<ProcessingResponse> validateCertificates(
            @PathVariable UUID uploadId) {

        log.info("Manual validation started: uploadId={}", uploadId);

        try {
            UploadedFile file = uploadedFileRepository.findById(UploadId.of(uploadId))
                .orElseThrow(() -> new RuntimeException("Upload not found"));

            // MANUAL 모드 확인
            if (file.getProcessingMode() != ProcessingMode.MANUAL) {
                return ResponseEntity.badRequest().body(
                    ProcessingResponse.error("This upload is in AUTO mode"));
            }

            // 검증 실행
            progressService.sendProgress(uploadId, ProcessingStage.VALIDATION_STARTED, 65, "검증 시작...");

            ValidateCertificateCommand command = ValidateCertificateCommand.builder()
                .uploadId(uploadId)
                .build();

            validateCertificateUseCase.execute(command);

            progressService.sendProgress(uploadId, ProcessingStage.VALIDATION_COMPLETED, 85, "검증 완료");

            return ResponseEntity.ok(ProcessingResponse.success("검증 완료"));

        } catch (Exception e) {
            log.error("Validation error", e);
            progressService.sendProgress(uploadId, ProcessingStage.FAILED, 0, "검증 실패: " + e.getMessage());
            return ResponseEntity.internalServerError().body(
                ProcessingResponse.error("검증 중 오류: " + e.getMessage()));
        }
    }

    /**
     * LDAP 업로드 수동 시작
     */
    @PostMapping("/upload-to-ldap/{uploadId}")
    public ResponseEntity<ProcessingResponse> uploadToLdap(
            @PathVariable UUID uploadId) {

        log.info("Manual LDAP upload started: uploadId={}", uploadId);

        try {
            UploadedFile file = uploadedFileRepository.findById(UploadId.of(uploadId))
                .orElseThrow(() -> new RuntimeException("Upload not found"));

            // MANUAL 모드 확인
            if (file.getProcessingMode() != ProcessingMode.MANUAL) {
                return ResponseEntity.badRequest().body(
                    ProcessingResponse.error("This upload is in AUTO mode"));
            }

            // LDAP 업로드 실행
            progressService.sendProgress(uploadId, ProcessingStage.LDAP_SAVING_STARTED, 90, "LDAP 저장 시작...");

            UploadToLdapCommand command = UploadToLdapCommand.builder()
                .uploadId(uploadId)
                .build();

            uploadToLdapUseCase.execute(command);

            progressService.sendProgress(uploadId, ProcessingStage.LDAP_SAVING_COMPLETED, 100, "LDAP 저장 완료");

            return ResponseEntity.ok(ProcessingResponse.success("LDAP 저장 완료"));

        } catch (Exception e) {
            log.error("LDAP upload error", e);
            progressService.sendProgress(uploadId, ProcessingStage.FAILED, 0, "LDAP 저장 실패: " + e.getMessage());
            return ResponseEntity.internalServerError().body(
                ProcessingResponse.error("LDAP 저장 중 오류: " + e.getMessage()));
        }
    }

    /**
     * 현재 처리 상태 조회
     */
    @GetMapping("/status/{uploadId}")
    public ResponseEntity<ProcessingStatusResponse> getProcessingStatus(
            @PathVariable UUID uploadId) {

        UploadedFile file = uploadedFileRepository.findById(UploadId.of(uploadId))
            .orElseThrow(() -> new RuntimeException("Upload not found"));

        return ResponseEntity.ok(ProcessingStatusResponse.builder()
            .uploadId(uploadId)
            .processingMode(file.getProcessingMode())
            .currentStatus(file.getStatus())
            .manualPauseAtStep(file.getManualPauseAtStep())
            .build());
    }
}
```

### 7. Response DTO

```java
// ProcessingResponse.java
@Builder
public record ProcessingResponse(
    boolean success,
    String message
) {
    public static ProcessingResponse success(String message) {
        return ProcessingResponse.builder()
            .success(true)
            .message(message)
            .build();
    }

    public static ProcessingResponse error(String message) {
        return ProcessingResponse.builder()
            .success(false)
            .message(message)
            .build();
    }
}

// ProcessingStatusResponse.java
@Builder
public record ProcessingStatusResponse(
    UUID uploadId,
    ProcessingMode processingMode,
    UploadStatus currentStatus,
    String manualPauseAtStep
) { }
```

---

## 🎨 Frontend 구현 설계

### 1. Processing Mode 선택 UI

**파일**: `/templates/fragments/processing-mode-selector.html` (신규)

```html
<th:block th:fragment="mode-selector">
  <div class="card bg-base-100 shadow-xl mb-6"
       x-data="processingModeSelector()">

    <div class="card-body">
      <h2 class="card-title">
        <i class="fas fa-cogs"></i>
        처리 방식 선택
      </h2>

      <!-- Processing Mode Selection: Radio Buttons -->
      <div class="form-control gap-4 mt-4">

        <!-- Auto Mode -->
        <label class="label cursor-pointer border rounded-lg p-4"
               :class="processingMode === 'AUTO' ? 'bg-primary bg-opacity-10' : ''">
          <div class="flex gap-4 flex-1">
            <input type="radio"
                   name="processingMode"
                   value="AUTO"
                   @change="processingMode = 'AUTO'; updateModeInfo()"
                   class="radio"
                   :checked="processingMode === 'AUTO'" />
            <div>
              <p class="font-bold">🤖 자동 처리</p>
              <p class="text-sm opacity-70">
                파일 업로드 후 자동으로 파싱 → 검증 → LDAP 등록 진행
              </p>
              <p class="text-xs mt-2 text-success">권장: 대량 파일 처리, 배치 작업</p>
            </div>
          </div>
        </label>

        <!-- Manual Mode -->
        <label class="label cursor-pointer border rounded-lg p-4"
               :class="processingMode === 'MANUAL' ? 'bg-info bg-opacity-10' : ''">
          <div class="flex gap-4 flex-1">
            <input type="radio"
                   name="processingMode"
                   value="MANUAL"
                   @change="processingMode = 'MANUAL'; updateModeInfo()"
                   class="radio"
                   :checked="processingMode === 'MANUAL'" />
            <div>
              <p class="font-bold">🎮 수동 처리</p>
              <p class="text-sm opacity-70">
                각 단계를 사용자가 수동으로 진행 (파싱 → 검증 → LDAP 등록)
              </p>
              <p class="text-xs mt-2 text-info">권장: 개별 검증, 문제 디버깅</p>
            </div>
          </div>
        </label>
      </div>

      <!-- Mode Description -->
      <div class="alert mt-4" :class="modeAlertClass">
        <i :class="modeIcon"></i>
        <span x-text="modeDescription"></span>
      </div>

      <!-- Hidden input for form -->
      <input type="hidden" name="processingMode" :value="processingMode" />
    </div>
  </div>

  <script>
    function processingModeSelector() {
      return {
        processingMode: 'AUTO',

        get modeAlertClass() {
          return this.processingMode === 'AUTO'
            ? 'alert-success'
            : 'alert-info';
        },

        get modeIcon() {
          return this.processingMode === 'AUTO'
            ? 'fas fa-check-circle'
            : 'fas fa-info-circle';
        },

        get modeDescription() {
          return this.processingMode === 'AUTO'
            ? 'AUTO: 업로드 후 자동으로 모든 단계가 진행됩니다. SSE로 실시간 진행 상황을 표시합니다.'
            : 'MANUAL: 각 단계를 수동으로 시작합니다. 단계별로 검토하고 진행할 수 있습니다.';
        },

        updateModeInfo() {
          // 모드 정보 업데이트
        }
      };
    }
  </script>
</th:block>
```

### 2. Auto Mode UI

**파일**: `/templates/fragments/auto-mode-panel.html` (신규)

```html
<!-- Auto Mode Panel: SSE 진행률 표시 (기존과 동일) -->
<th:block th:fragment="auto-mode-panel">
  <div x-show="processingMode === 'AUTO'" class="card bg-base-100 shadow-xl">
    <div class="card-body">
      <h3 class="card-title">
        <i class="fas fa-spinner fa-spin"></i>
        파일 자동 처리 중
      </h3>

      <!-- SSE Progress Modal -->
      <div id="sseProgressContent">
        <!-- 진행률 바, 메시지, 단계 표시 -->
        <!-- (기존 progressModal과 동일한 내용) -->
      </div>
    </div>
  </div>
</th:block>
```

### 3. Manual Mode UI

**파일**: `/templates/fragments/manual-mode-panel.html` (신규)

```html
<!-- Manual Mode Panel: Step-by-Step Control -->
<th:block th:fragment="manual-mode-panel">
  <div x-show="processingMode === 'MANUAL'"
       x-data="manualModeController()"
       class="card bg-base-100 shadow-xl">

    <div class="card-body">
      <h3 class="card-title">
        <i class="fas fa-tasks"></i>
        파일 수동 처리
      </h3>

      <!-- Processing Steps -->
      <div class="steps steps-vertical">

        <!-- Step 1: Upload -->
        <div class="step step-success" :class="currentStep === 'RECEIVED' ? 'step-primary' : ''">
          <div class="step-content">
            <p class="step-title">파일 업로드</p>
            <p class="text-xs opacity-70">파일이 서버에 저장되었습니다</p>
          </div>
        </div>

        <!-- Step 2: Parsing -->
        <div class="step" :class="getStepClass('PARSED')">
          <div class="step-content">
            <p class="step-title">파일 파싱</p>
            <p class="text-xs opacity-70">인증서 및 CRL 추출</p>

            <div x-show="isStepActive('PARSED')" class="mt-3 space-y-2">
              <!-- Progress info (if processing) -->
              <progress x-show="isProcessing('PARSING')"
                        id="parsingProgress"
                        class="progress progress-primary w-full"
                        value="0" max="100"></progress>

              <!-- Result (if completed) -->
              <div x-show="steps.PARSED.completed" class="alert alert-success alert-sm">
                <i class="fas fa-check-circle"></i>
                <span x-text="steps.PARSED.message"></span>
              </div>

              <!-- Error (if failed) -->
              <div x-show="steps.PARSED.error" class="alert alert-error alert-sm">
                <i class="fas fa-exclamation-circle"></i>
                <span x-text="steps.PARSED.errorMessage"></span>
              </div>

              <!-- Action button -->
              <button type="button"
                      @click="triggerStep('PARSING')"
                      :disabled="isProcessing('PARSING') || steps.PARSED.completed"
                      class="btn btn-sm btn-primary w-full gap-2">
                <i :class="isProcessing('PARSING') ? 'fas fa-spinner fa-spin' : 'fas fa-play'"></i>
                <span x-text="isProcessing('PARSING') ? '파싱 중...' : '파싱 시작'"></span>
              </button>
            </div>
          </div>
        </div>

        <!-- Step 3: Validation -->
        <div class="step" :class="getStepClass('VALIDATED')">
          <div class="step-content">
            <p class="step-title">인증서 검증</p>
            <p class="text-xs opacity-70">Trust Chain, CRL 검증</p>

            <div x-show="isStepActive('VALIDATED')" class="mt-3 space-y-2">
              <progress x-show="isProcessing('VALIDATION')"
                        id="validationProgress"
                        class="progress progress-info w-full"
                        value="0" max="100"></progress>

              <div x-show="steps.VALIDATED.completed" class="alert alert-success alert-sm">
                <span x-text="steps.VALIDATED.message"></span>
              </div>

              <div x-show="steps.VALIDATED.error" class="alert alert-error alert-sm">
                <span x-text="steps.VALIDATED.errorMessage"></span>
              </div>

              <button type="button"
                      @click="triggerStep('VALIDATION')"
                      :disabled="!steps.PARSED.completed || isProcessing('VALIDATION') || steps.VALIDATED.completed"
                      class="btn btn-sm btn-info w-full gap-2">
                <i :class="isProcessing('VALIDATION') ? 'fas fa-spinner fa-spin' : 'fas fa-play'"></i>
                <span x-text="isProcessing('VALIDATION') ? '검증 중...' : '검증 시작'"></span>
              </button>
            </div>
          </div>
        </div>

        <!-- Step 4: LDAP Upload -->
        <div class="step" :class="getStepClass('COMPLETED')">
          <div class="step-content">
            <p class="step-title">LDAP 등록</p>
            <p class="text-xs opacity-70">OpenLDAP 서버에 저장</p>

            <div x-show="isStepActive('COMPLETED')" class="mt-3 space-y-2">
              <progress x-show="isProcessing('LDAP_UPLOAD')"
                        id="ldapProgress"
                        class="progress progress-success w-full"
                        value="0" max="100"></progress>

              <div x-show="steps.COMPLETED.completed" class="alert alert-success alert-sm">
                <span x-text="steps.COMPLETED.message"></span>
              </div>

              <div x-show="steps.COMPLETED.error" class="alert alert-error alert-sm">
                <span x-text="steps.COMPLETED.errorMessage"></span>
              </div>

              <button type="button"
                      @click="triggerStep('LDAP_UPLOAD')"
                      :disabled="!steps.VALIDATED.completed || isProcessing('LDAP_UPLOAD') || steps.COMPLETED.completed"
                      class="btn btn-sm btn-success w-full gap-2">
                <i :class="isProcessing('LDAP_UPLOAD') ? 'fas fa-spinner fa-spin' : 'fas fa-play'"></i>
                <span x-text="isProcessing('LDAP_UPLOAD') ? 'LDAP 등록 중...' : 'LDAP 등록 시작'"></span>
              </button>
            </div>
          </div>
        </div>
      </div>

      <!-- Summary -->
      <div class="mt-6 p-4 bg-base-200 rounded-lg">
        <h4 class="font-bold mb-2">처리 현황</h4>
        <div class="text-sm space-y-1">
          <div>
            <span class="font-semibold">총 진행률:</span>
            <span x-text="totalProgressPercentage + '%'"></span>
          </div>
          <div>
            <span class="font-semibold">소요 시간:</span>
            <span x-text="elapsedTime"></span>
          </div>
          <div x-show="lastError">
            <span class="font-semibold text-error">마지막 오류:</span>
            <span class="text-error" x-text="lastError"></span>
          </div>
        </div>
      </div>
    </div>
  </div>
</th:block>
```

### 4. Manual Mode Alpine.js Component

**파일**: `/static/js/shared/alpine-components.js` (추가)

```javascript
function manualModeController() {
  return {
    uploadId: null,
    processingMode: 'MANUAL',
    currentStep: 'RECEIVED',

    // 단계별 상태
    steps: {
      PARSED: {
        completed: false,
        processing: false,
        message: '',
        error: false,
        errorMessage: ''
      },
      VALIDATED: {
        completed: false,
        processing: false,
        message: '',
        error: false,
        errorMessage: ''
      },
      COMPLETED: {
        completed: false,
        processing: false,
        message: '',
        error: false,
        errorMessage: ''
      }
    },

    // 시간 추적
    startTime: null,
    elapsedTime: '0초',

    // SSE 연결
    sseSource: null,
    lastError: null,

    // 초기화
    init() {
      this.startTime = Date.now();
      this.updateElapsedTime();
      setInterval(() => this.updateElapsedTime(), 1000);
    },

    // 단계 상태 확인
    isStepActive(step) {
      return this.currentStep === step;
    },

    getStepClass(step) {
      if (this.steps[step].completed) {
        return 'step-success';
      } else if (this.steps[step].processing) {
        return 'step-warning';
      } else if (this.currentStep === step) {
        return 'step-primary';
      }
      return '';
    },

    // 진행 중 확인
    isProcessing(stepType) {
      switch (stepType) {
        case 'PARSING':
          return this.steps.PARSED.processing;
        case 'VALIDATION':
          return this.steps.VALIDATED.processing;
        case 'LDAP_UPLOAD':
          return this.steps.COMPLETED.processing;
        default:
          return false;
      }
    },

    // 단계 Trigger
    async triggerStep(stepType) {
      let endpoint, stepKey, message;

      switch (stepType) {
        case 'PARSING':
          endpoint = `/api/processing/parse/${this.uploadId}`;
          stepKey = 'PARSED';
          message = '파싱 중...';
          break;
        case 'VALIDATION':
          endpoint = `/api/processing/validate/${this.uploadId}`;
          stepKey = 'VALIDATED';
          message = '검증 중...';
          break;
        case 'LDAP_UPLOAD':
          endpoint = `/api/processing/upload-to-ldap/${this.uploadId}`;
          stepKey = 'COMPLETED';
          message = 'LDAP 등록 중...';
          break;
      }

      this.steps[stepKey].processing = true;
      this.steps[stepKey].message = message;
      this.steps[stepKey].error = false;

      try {
        // SSE 연결 시작
        this.startSSEForStep(stepType);

        // API 호출
        const response = await fetch(endpoint, {
          method: 'POST',
          headers: { 'Content-Type': 'application/json' }
        });

        if (!response.ok) {
          throw new Error(`HTTP ${response.status}`);
        }

        const result = await response.json();
        if (!result.success) {
          throw new Error(result.message);
        }

        // SSE 수신 대기
        await this.waitForSSECompletion(stepType, 5000);  // 5초 대기

        // 단계 완료 처리
        this.steps[stepKey].completed = true;
        this.steps[stepKey].message = `${message.replace('중...', '')} 완료`;
        this.updateCurrentStep(stepKey);

      } catch (error) {
        this.steps[stepKey].error = true;
        this.steps[stepKey].errorMessage = error.message;
        this.lastError = error.message;
        console.error(`${stepType} error:`, error);
      } finally {
        this.steps[stepKey].processing = false;
      }
    },

    // SSE 연결
    startSSEForStep(stepType) {
      if (!this.sseSource) {
        this.sseSource = new EventSource('/progress/stream');

        this.sseSource.addEventListener('progress', (e) => {
          const progress = JSON.parse(e.data);
          if (progress.uploadId === this.uploadId) {
            this.updateProgressUI(progress);
          }
        });

        this.sseSource.addEventListener('heartbeat', () => {
          // Heartbeat received
        });

        this.sseSource.onerror = (error) => {
          console.error('SSE error:', error);
          if (this.sseSource.readyState === EventSource.CLOSED) {
            // 재연결 시도
            setTimeout(() => this.startSSEForStep(stepType), 3000);
          }
        };
      }
    },

    // SSE 완료 대기
    waitForSSECompletion(stepType, timeout) {
      return new Promise((resolve, reject) => {
        const startTime = Date.now();
        const checkInterval = setInterval(() => {
          // 단계별 완료 상태 확인
          let stepKey;
          switch (stepType) {
            case 'PARSING':
              stepKey = 'PARSED';
              break;
            case 'VALIDATION':
              stepKey = 'VALIDATED';
              break;
            case 'LDAP_UPLOAD':
              stepKey = 'COMPLETED';
              break;
          }

          // 타임아웃 체크
          if (Date.now() - startTime > timeout) {
            clearInterval(checkInterval);
            resolve();  // 타임아웃 후에도 진행
            return;
          }

          // 완료 상태 확인 (SSE에서 업데이트됨)
          // 여기서는 SSE 업데이트를 기다림
        }, 100);

        setTimeout(() => {
          clearInterval(checkInterval);
          resolve();
        }, timeout);
      });
    },

    // 진행률 UI 업데이트
    updateProgressUI(progress) {
      let progressElementId;
      switch (progress.stage) {
        case 'PARSING_IN_PROGRESS':
          progressElementId = 'parsingProgress';
          this.steps.PARSED.message = `파싱 중... (${progress.processedCount}/${progress.totalCount})`;
          break;
        case 'VALIDATION_IN_PROGRESS':
          progressElementId = 'validationProgress';
          this.steps.VALIDATED.message = `검증 중... (${progress.processedCount}/${progress.totalCount})`;
          break;
        case 'LDAP_SAVING_IN_PROGRESS':
          progressElementId = 'ldapProgress';
          this.steps.COMPLETED.message = `LDAP 등록 중... (${progress.processedCount}/${progress.totalCount})`;
          break;
      }

      if (progressElementId) {
        const element = document.getElementById(progressElementId);
        if (element) {
          element.value = progress.percentage;
        }
      }
    },

    // 현재 단계 업데이트
    updateCurrentStep(stepKey) {
      // 다음 단계로 전환
      if (stepKey === 'PARSED') {
        this.currentStep = 'VALIDATED';
      } else if (stepKey === 'VALIDATED') {
        this.currentStep = 'COMPLETED';
      } else if (stepKey === 'COMPLETED') {
        this.currentStep = 'COMPLETED';
      }
    },

    // 총 진행률 계산
    get totalProgressPercentage() {
      let percentage = 5;  // Upload: 5%
      if (this.steps.PARSED.completed) percentage += 30;
      else if (this.steps.PARSED.processing) percentage += 15;

      if (this.steps.VALIDATED.completed) percentage += 35;
      else if (this.steps.VALIDATED.processing) percentage += 17;

      if (this.steps.COMPLETED.completed) percentage += 30;
      else if (this.steps.COMPLETED.processing) percentage += 15;

      return percentage;
    },

    // 소요 시간 업데이트
    updateElapsedTime() {
      const elapsed = Math.floor((Date.now() - this.startTime) / 1000);
      const minutes = Math.floor(elapsed / 60);
      const seconds = elapsed % 60;

      if (minutes > 0) {
        this.elapsedTime = `${minutes}분 ${seconds}초`;
      } else {
        this.elapsedTime = `${seconds}초`;
      }
    }
  };
}
```

---

## 🔄 데이터 흐름 통합

### API 엔드포인트 정리

```
파일 업로드:
  GET  /file/upload                  → 업로드 페이지 (AUTO/MANUAL 선택)
  POST /file/upload                  → 파일 업로드

Auto Mode (자동 처리):
  SSE /progress/stream               → 실시간 진행률
  Event Chain:
    FileUploadedEvent → ParseLdifFileUseCase → FileParsingCompletedEvent
                     → ValidateCertificateUseCase → CertificatesValidatedEvent
                     → UploadToLdapUseCase → UploadToLdapCompletedEvent

Manual Mode (수동 처리):
  POST /api/processing/parse/{uploadId}              → 파싱 시작
  POST /api/processing/validate/{uploadId}           → 검증 시작
  POST /api/processing/upload-to-ldap/{uploadId}     → LDAP 등록 시작
  GET  /api/processing/status/{uploadId}             → 상태 조회
  SSE  /progress/stream                              → 진행률 스트림

업로드 이력:
  GET  /upload-history               → 이력 조회
  GET  /upload-history?id={id}       → 상세 정보 (통계 포함)
```

---

## 📊 구현 파일 추가 목록

### Backend (신규 6개)

```
NEW:
├── domain/model/ProcessingMode.java                 (50 lines)
├── infrastructure/web/ProcessingController.java     (250 lines)
├── application/response/ProcessingResponse.java     (30 lines)
├── application/response/ProcessingStatusResponse.java (30 lines)
└── db/migration/V14__Add_Processing_Mode.sql       (20 lines)

MODIFIED:
├── domain/model/UploadedFile.java                  (추가: 3-5 lines)
├── domain/event/FileUploadedEvent.java             (추가: 1 field)
├── application/event/FileUploadEventHandler.java   (수정: 조건부 처리)
├── application/event/ParsedFileEventHandler.java   (수정: 조건부 처리)
├── application/event/CertificateValidationEventHandler.java (수정)
└── domain/repository/UploadedFileRepository.java   (추가: 쿼리)
```

### Frontend (신규 3개)

```
NEW:
├── templates/fragments/processing-mode-selector.html (150 lines)
├── templates/fragments/auto-mode-panel.html          (100 lines)
├── templates/fragments/manual-mode-panel.html        (200 lines)

MODIFIED:
├── templates/file/unified-upload.html              (추가: mode selector 포함)
└── static/js/shared/alpine-components.js           (확장: manualModeController)
```

---

## 🎯 구현 일정 (추가)

### Week 3: Dual Mode Architecture 추가 (2-3일)

**Day 10: Backend 구현**
- [ ] ProcessingMode enum 추가
- [ ] UploadedFile entity 확장
- [ ] V14 migration 생성
- [ ] ProcessingController 구현 (3개 엔드포인트)
- [ ] Event Handler 조건부 처리 수정

**Day 11: Frontend 구현**
- [ ] Mode Selector Fragment 작성
- [ ] Auto Mode Panel 작성
- [ ] Manual Mode Panel 작성 + Alpine.js

**Day 12: 통합 테스트**
- [ ] Auto Mode 전체 테스트
- [ ] Manual Mode 전체 테스트
- [ ] Mode 전환 테스트
- [ ] SSE 통합 테스트

---

## ✅ 최종 검증 기준

### 기능 요구사항

- ✅ **Auto Mode**:
  - 파일 업로드 후 자동 파싱 시작 ✓
  - 파싱 완료 후 자동 검증 시작 ✓
  - 검증 완료 후 자동 LDAP 등록 ✓
  - SSE로 실시간 진행률 표시 ✓

- ✅ **Manual Mode**:
  - 파일 업로드 후 대기 상태 ✓
  - 사용자가 "파싱 시작" 클릭 → 파싱 진행 ✓
  - 파싱 완료 후 대기 상태 ✓
  - 사용자가 "검증 시작" 클릭 → 검증 진행 ✓
  - 검증 완료 후 대기 상태 ✓
  - 사용자가 "LDAP 등록" 클릭 → LDAP 등록 진행 ✓

### UI/UX 요구사항

- ✅ Mode 선택이 명확함 (라디오 버튼)
- ✅ Mode별 설명 제시
- ✅ 각 단계가 시각적으로 구분됨
- ✅ 진행 상황 실시간 업데이트
- ✅ 오류 상황 명확한 표시

---

## 💡 향후 전환 전략

### Phase 1: 동시 지원 (현재 설계)
- Auto Mode와 Manual Mode 모두 완벽히 작동
- 사용자가 업로드 시 선택 가능

### Phase 2: 모니터링 및 피드백 (1-2주)
- 사용자 사용 패턴 분석
- 각 모드의 장점/단점 파악
- 성능 비교 (처리 시간, 오류율 등)

### Phase 3: 최적화 (필요시)
- 더 나은 모드 기본값으로 설정
- 덜 사용하는 모드는 선택적으로 제공
- 자동/수동 혼합 모드 검토 (예: 자동 파싱, 수동 검증)

---

**문서 버전**: 1.0
**상태**: ✅ Phase 18.2 추가 기능 설계 완료

이 설계를 통해:
- ✅ Auto Mode: 자동화된 대량 처리
- ✅ Manual Mode: 세밀한 제어 및 검증
- ✅ 향후 유연한 전환 가능

두 모드를 완벽하게 지원하며, 사용 경험을 통해 최적 방식으로 전환할 수 있습니다.
