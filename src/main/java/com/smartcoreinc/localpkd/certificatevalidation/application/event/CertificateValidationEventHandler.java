package com.smartcoreinc.localpkd.certificatevalidation.application.event;

import com.smartcoreinc.localpkd.certificatevalidation.domain.event.CertificateRevokedEvent;
import com.smartcoreinc.localpkd.certificatevalidation.domain.event.CertificateValidatedEvent;
import com.smartcoreinc.localpkd.certificatevalidation.domain.event.CertificatesValidatedEvent;
import com.smartcoreinc.localpkd.certificatevalidation.domain.event.TrustChainVerifiedEvent;
import com.smartcoreinc.localpkd.certificatevalidation.domain.event.ValidationFailedEvent;
import com.smartcoreinc.localpkd.certificatevalidation.domain.model.Certificate;
import com.smartcoreinc.localpkd.certificatevalidation.domain.model.CertificateType;
import com.smartcoreinc.localpkd.certificatevalidation.domain.model.SubjectInfo;
import com.smartcoreinc.localpkd.certificatevalidation.domain.model.IssuerInfo;
import com.smartcoreinc.localpkd.certificatevalidation.domain.model.ValidityPeriod;
import com.smartcoreinc.localpkd.certificatevalidation.domain.model.X509Data;
import com.smartcoreinc.localpkd.certificatevalidation.infrastructure.repository.JpaCertificateRepository;
import org.bouncycastle.cert.X509CertificateHolder;
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;

import java.io.ByteArrayInputStream;
import java.security.PublicKey;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import com.smartcoreinc.localpkd.fileparsing.domain.event.CertificatesExtractedEvent;
import com.smartcoreinc.localpkd.fileparsing.domain.model.CertificateData;
import com.smartcoreinc.localpkd.fileparsing.domain.model.ParsedFile;
import com.smartcoreinc.localpkd.fileparsing.domain.model.ParsedFileId;
import com.smartcoreinc.localpkd.fileparsing.infrastructure.repository.JpaParsedFileRepository;
import com.smartcoreinc.localpkd.certificatevalidation.application.command.UploadToLdapCommand;
import com.smartcoreinc.localpkd.certificatevalidation.application.usecase.UploadToLdapUseCase;
import com.smartcoreinc.localpkd.shared.progress.ProcessingProgress;
import com.smartcoreinc.localpkd.shared.progress.ProcessingStage;
import com.smartcoreinc.localpkd.shared.progress.ProgressService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * CertificateValidationEventHandler - 인증서 검증 이벤트 핸들러
 *
 * <p><b>책임</b>: Certificate Validation Bounded Context의 Domain Events를 처리합니다.</p>
 *
 * <p><b>처리 이벤트</b>:</p>
 * <ul>
 *   <li>CertificateValidatedEvent - 인증서 검증 완료</li>
 *   <li>TrustChainVerifiedEvent - Trust Chain 검증 완료</li>
 *   <li>CertificateRevokedEvent - 인증서 폐기 감지</li>
 *   <li>ValidationFailedEvent - 인증서 검증 실패</li>
 * </ul>
 *
 * <p><b>이벤트 처리 방식</b>:</p>
 * <ul>
 *   <li><b>동기 처리</b>: 읽기 전용 작업 (로깅, 통계 계산)</li>
 *   <li><b>비동기 처리</b>: 외부 시스템 연동 (LDAP, 알림 등)</li>
 * </ul>
 *
 * <p><b>트랜잭션 관리</b>:</p>
 * <ul>
 *   <li>동기 핸들러: 발행 트랜잭션 내에서 실행</li>
 *   <li>비동기 핸들러: 발행 트랜잭션 커밋 후 별도 스레드에서 실행</li>
 * </ul>
 *
 * @see CertificateValidatedEvent
 * @see TrustChainVerifiedEvent
 * @see CertificateRevokedEvent
 * @see ValidationFailedEvent
 * @author SmartCore Inc.
 * @version 1.0
 * @since 2025-10-25
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CertificateValidationEventHandler {

    private final ProgressService progressService;
    private final UploadToLdapUseCase uploadToLdapUseCase;
    private final JpaParsedFileRepository parsedFileRepository;
    private final JpaCertificateRepository certificateRepository;

    /**
     * CertificateValidatedEvent 동기 핸들러
     *
     * <p><b>처리 내용</b>:</p>
     * <ul>
     *   <li>검증 완료 로깅</li>
     *   <li>검증 통계 업데이트</li>
     *   <li>검증 결과 보안 감시 (위험한 설정 감지)</li>
     * </ul>
     *
     * <p><b>실행 시점</b>: 트랜잭션 커밋 전 (읽기 전용)</p>
     *
     * @param event 검증 완료 이벤트
     */
    @TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT)
    public void handleCertificateValidatedSync(CertificateValidatedEvent event) {
        log.info("=== Certificate Validated Event (Sync) ===");
        log.info("Event: {}", event.eventType());
        log.info("Certificate ID: {}", event.getCertificateId().getId());
        log.info("Status: {}", event.getValidationStatus().name());
        log.info("Occurred at: {}", event.occurredOn());

        try {
            // 1. 검증 통계 업데이트 [skeleton]
            updateValidationStatistics(event);

            // 2. 보안 감시: 검증 실패 인증서 추적 [skeleton]
            monitorSecurityStatus(event);

            log.debug("Certificate validation sync handler completed successfully");
        } catch (Exception e) {
            // 비동기 작업이 아니므로 예외는 사전에 처리
            log.error("Error in certificate validation sync handler", e);
        }
    }

    /**
     * CertificateValidatedEvent 비동기 핸들러
     *
     * <p><b>처리 내용</b>:</p>
     * <ul>
     *   <li>LDAP Integration: LDAP 서버에 검증 결과 동기화</li>
     *   <li>감시 알림: 이상 감지 시 보안팀에 알림</li>
     *   <li>메트릭 수집: 성능 및 품질 지표 기록</li>
     * </ul>
     *
     * <p><b>실행 시점</b>: 트랜잭션 커밋 후 (별도 스레드, 읽기 전용)</p>
     *
     * @param event 검증 완료 이벤트
     */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Async
    public void handleCertificateValidatedAsync(CertificateValidatedEvent event) {
        log.info("=== Certificate Validated Event (Async) ===");
        log.info("Certificate ID: {}", event.getCertificateId().getId());

        try {
            // 1. LDAP 동기화 [skeleton]
            syncToLdap(event);

            // 2. 감시 알림 [skeleton]
            sendMonitoringAlert(event);

            // 3. 메트릭 수집 [skeleton]
            collectMetrics(event);

            log.debug("Certificate validation async handler completed successfully");
        } catch (Exception e) {
            // 비동기 작업이므로 예외를 로깅하고 계속 진행
            log.error("Error in certificate validation async handler (will not affect main flow)", e);
        }
    }

    /**
     * TrustChainVerifiedEvent 동기 핸들러
     *
     * <p><b>처리 내용</b>:</p>
     * <ul>
     *   <li>Trust Chain 검증 결과 로깅</li>
     *   <li>신뢰 관계 설정 기록</li>
     *   <li>체인 깊이 및 구조 분석</li>
     * </ul>
     *
     * <p><b>실행 시점</b>: 트랜잭션 커밋 전</p>
     *
     * @param event Trust Chain 검증 완료 이벤트
     */
    @TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT)
    public void handleTrustChainVerifiedSync(TrustChainVerifiedEvent event) {
        log.info("=== Trust Chain Verified Event (Sync) ===");
        log.info("End Entity: {}", event.getEndEntityCertificateId().getId());
        log.info("Trust Anchor: {}", event.getTrustAnchorCertificateId() != null ?
            event.getTrustAnchorCertificateId().getId() : "Not found");
        log.info("Chain Depth: {}", event.getChainDepth());
        log.info("Chain Valid: {}", event.isChainValid());
        log.info("Country Code: {}", event.getTrustAnchorCountryCode());

        try {
            // 1. Trust Chain 통계 업데이트 [skeleton]
            updateTrustChainStatistics(event);

            // 2. 체인 구조 분석 [skeleton]
            analyzeTrustChainStructure(event);

            log.debug("Trust Chain verification sync handler completed successfully");
        } catch (Exception e) {
            log.error("Error in trust chain verification sync handler", e);
        }
    }

    /**
     * TrustChainVerifiedEvent 비동기 핸들러
     *
     * <p><b>처리 내용</b>:</p>
     * <ul>
     *   <li>LDAP 관계 설정</li>
     *   <li>인증서 계층 구조 동기화</li>
     *   <li>신뢰 관계 외부 시스템 알림</li>
     * </ul>
     *
     * <p><b>실행 시점</b>: 트랜잭션 커밋 후</p>
     *
     * @param event Trust Chain 검증 완료 이벤트
     */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Async
    public void handleTrustChainVerifiedAsync(TrustChainVerifiedEvent event) {
        log.info("=== Trust Chain Verified Event (Async) ===");
        log.info("End Entity: {}", event.getEndEntityCertificateId().getId());

        try {
            // 1. LDAP 신뢰 관계 설정 [skeleton]
            syncTrustRelationshipToLdap(event);

            // 2. 감시 알림 [skeleton]
            sendTrustChainAlert(event);

            log.debug("Trust Chain verification async handler completed successfully");
        } catch (Exception e) {
            log.error("Error in trust chain verification async handler (will not affect main flow)", e);
        }
    }

    /**
     * CertificateRevokedEvent 동기 핸들러
     *
     * <p><b>처리 내용</b>:</p>
     * <ul>
     *   <li>폐기된 인증서 즉시 기록</li>
     *   <li>보안 경고 생성</li>
     *   <li>폐기 관련 통계 업데이트</li>
     * </ul>
     *
     * <p><b>실행 시점</b>: 트랜잭션 커밋 전</p>
     *
     * @param event 인증서 폐기 이벤트
     */
    @TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT)
    public void handleCertificateRevokedSync(CertificateRevokedEvent event) {
        log.warn("=== Certificate Revoked Event (Sync) ===");
        log.warn("Certificate ID: {}", event.getCertificateId().getId());
        log.warn("Serial Number: {}", event.getSerialNumber());
        log.warn("Revocation Reason: {} ({})", event.getRevocationReasonName(), event.getRevocationReasonCode());
        log.warn("Revoked At: {}", event.getRevokedAt());

        try {
            // 1. 폐기 통계 즉시 업데이트 [skeleton]
            updateRevocationStatistics(event);

            // 2. 보안 경고 기록 [skeleton]
            recordSecurityAlert(event);

            log.warn("Certificate revocation recorded: {}", event.getCertificateId().getId());
        } catch (Exception e) {
            log.error("Error in certificate revocation sync handler", e);
        }
    }

    /**
     * CertificateRevokedEvent 비동기 핸들러
     *
     * <p><b>처리 내용</b>:</p>
     * <ul>
     *   <li>LDAP 엔트리 비활성화</li>
     *   <li>폐기된 인증서 목록 업데이트</li>
     *   <li>보안팀 긴급 알림</li>
     *   <li>폐기된 인증서로 서명된 문서 추적</li>
     * </ul>
     *
     * <p><b>실행 시점</b>: 트랜잭션 커밋 후 (긴급)</p>
     *
     * @param event 인증서 폐기 이벤트
     */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Async
    public void handleCertificateRevokedAsync(CertificateRevokedEvent event) {
        log.warn("=== Certificate Revoked Event (Async/Emergency) ===");
        log.warn("Certificate: {}", event.getCertificateId().getId());

        try {
            // 1. LDAP 엔트리 비활성화 (긴급) [skeleton]
            disableLdapEntry(event);

            // 2. 보안팀 긴급 알림 [skeleton]
            sendEmergencySecurityAlert(event);

            // 3. 서명된 문서 추적 [skeleton]
            trackSignedDocuments(event);

            log.warn("Revoked certificate emergency handling completed: {}", event.getCertificateId().getId());
        } catch (Exception e) {
            // 긴급 작업이므로 예외를 로깅하되 계속 진행
            log.error("Error in certificate revocation async handler (CRITICAL - manual review needed)", e);
        }
    }

    /**
     * ValidationFailedEvent 동기 핸들러
     *
     * <p><b>처리 내용</b>:</p>
     * <ul>
     *   <li>검증 실패 원인 상세 로깅</li>
     *   <li>실패 통계 기록</li>
     *   <li>치명적 실패 여부 판단</li>
     * </ul>
     *
     * <p><b>실행 시점</b>: 트랜잭션 커밋 전</p>
     *
     * @param event 검증 실패 이벤트
     */
    @TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT)
    public void handleValidationFailedSync(ValidationFailedEvent event) {
        log.error("=== Validation Failed Event (Sync) ===");
        log.error("Certificate ID: {}", event.getCertificateId().getId());
        log.error("Subject DN: {}", event.getSubjectDn());
        log.error("Primary Failure: {}", event.getPrimaryFailureReason().name());
        log.error("Message: {}", event.getFailureMessage());
        log.error("Additional Errors: {}", event.getAdditionalErrorCount());

        if (event.isCriticalFailure()) {
            log.error("*** CRITICAL FAILURE - IMMEDIATE ACTION REQUIRED ***");
        }

        try {
            // 1. 실패 통계 업데이트 [skeleton]
            updateFailureStatistics(event);

            // 2. 심각도 평가 [skeleton]
            evaluateFailureSeverity(event);

            // 3. 오류 분석 기록 [skeleton]
            recordFailureAnalysis(event);

            log.error("Validation failure recorded: {}", event.getCertificateId().getId());
        } catch (Exception e) {
            log.error("Error in validation failed sync handler", e);
        }
    }

    /**
     * ValidationFailedEvent 비동기 핸들러
     *
     * <p><b>처리 내용</b>:</p>
     * <ul>
     *   <li>LDAP 엔트리 상태 업데이트 (조건부)</li>
     *   <li>지속적인 모니터링 설정</li>
     *   <li>운영팀 알림</li>
     *   <li>이상 탐지 시스템 알림</li>
     * </ul>
     *
     * <p><b>실행 시점</b>: 트랜잭션 커밋 후</p>
     *
     * @param event 검증 실패 이벤트
     */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Async
    public void handleValidationFailedAsync(ValidationFailedEvent event) {
        log.error("=== Validation Failed Event (Async) ===");
        log.error("Certificate: {}", event.getCertificateId().getId());

        try {
            // 1. 조건부 LDAP 업데이트 [skeleton]
            updateLdapStatusConditional(event);

            // 2. 모니터링 설정 [skeleton]
            setupMonitoring(event);

            // 3. 알림 발송 [skeleton]
            sendOperationalAlert(event);

            // 4. 이상 탐지 시스템 [skeleton]
            notifyAnomalyDetection(event);

            log.error("Validation failure async handling completed");
        } catch (Exception e) {
            // 비동기 작업이므로 예외를 로깅하고 계속 진행
            log.error("Error in validation failed async handler (will not affect main flow)", e);
        }
    }

    /**
     * CertificatesExtractedEvent 핸들러
     *
     * <p><b>책임</b>: 파일에서 추출된 인증서를 Certificate Aggregate Root로 변환하여 저장합니다.</p>
     *
     * <p><b>처리 내용</b>:</p>
     * <ul>
     *   <li>ParsedFile에서 CertificateData 리스트 조회</li>
     *   <li>각 CertificateData를 Certificate Aggregate Root로 변환</li>
     *   <li>Certificate를 데이터베이스에 저장</li>
     *   <li>변환 및 저장 통계 로깅</li>
     * </ul>
     *
     * <p><b>실행 시점</b>: 트랜잭션 커밋 후 (별도 트랜잭션)</p>
     *
     * @param event 인증서 추출 완료 이벤트
     */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = org.springframework.transaction.annotation.Propagation.REQUIRES_NEW)
    public void handleCertificatesExtracted(CertificatesExtractedEvent event) {
        log.info("=== [Event-Async] CertificatesExtracted (Converting to Certificate Aggregates) ===");
        log.info("ParsedFile ID: {}", event.getParsedFileId());
        log.info("Upload ID: {}", event.getUploadId());
        log.info("Certificate Count: {}", event.getCertificateCount());

        try {
            // 1. ParsedFile 조회
            ParsedFileId parsedFileId = ParsedFileId.of(event.getParsedFileId());
            Optional<ParsedFile> parsedFileOpt = parsedFileRepository.findById(parsedFileId);
            if (parsedFileOpt.isEmpty()) {
                log.warn("ParsedFile not found: {}", event.getParsedFileId());
                return;
            }

            ParsedFile parsedFile = parsedFileOpt.get();
            List<CertificateData> certificateDataList = parsedFile.getCertificates();

            log.info("ParsedFile loaded: {} (status={}, certificateCount={})",
                parsedFile.getId().getId(), parsedFile.getStatus(),
                parsedFile.getStatistics() != null ? parsedFile.getStatistics().getCertificateCount() : 0);

            if (certificateDataList == null) {
                log.warn("Certificate list is null in ParsedFile: {}", event.getParsedFileId());
                return;
            }

            if (certificateDataList.isEmpty()) {
                log.warn("No certificates found in ParsedFile: {} (list size=0)", event.getParsedFileId());
                return;
            }

            log.info("Found {} certificates to convert from ParsedFile", certificateDataList.size());

            // 2. 각 CertificateData를 Certificate Aggregate Root로 변환 및 저장
            int successCount = 0;
            int errorCount = 0;

            for (int i = 0; i < certificateDataList.size(); i++) {
                CertificateData certData = certificateDataList.get(i);

                try {
                    log.debug("Converting certificate {}/{}: subject={}, type={}",
                        i + 1, certificateDataList.size(), certData.getSubjectDN(), certData.getCertificateType());

                    // Certificate Aggregate Root 생성
                    Certificate certificate = convertToCertificate(certData, event.getUploadId());

                    log.debug("Certificate object created: id={}, type={}, subject={}",
                        certificate.getId().getId(),
                        certificate.getCertificateType(),
                        certificate.getSubjectInfo() != null ? certificate.getSubjectInfo().getDistinguishedName() : "null");

                    // 저장
                    Certificate savedCertificate = certificateRepository.save(certificate);
                    successCount++;

                    log.info("Certificate {} saved successfully: id={}, type={}, subject={}",
                        i + 1, savedCertificate.getId().getId(),
                        savedCertificate.getCertificateType(),
                        savedCertificate.getSubjectInfo() != null ? savedCertificate.getSubjectInfo().getDistinguishedName() : "null");

                } catch (Exception e) {
                    errorCount++;
                    log.error("Failed to convert or save certificate {}/{}: subject={}, error={}",
                        i + 1, certificateDataList.size(), certData.getSubjectDN(), e.getMessage());
                    log.error("Exception details:", e);
                }
            }

            log.info("Certificate conversion completed: {} succeeded, {} failed (total: {})",
                successCount, errorCount, certificateDataList.size());

            if (successCount > 0) {
                log.info("Successfully saved {} certificates for uploadId: {}",
                    successCount, event.getUploadId());
            } else {
                log.warn("No certificates were successfully saved for uploadId: {} (event count: {})",
                    event.getUploadId(), event.getCertificateCount());
            }

        } catch (Exception e) {
            log.error("Failed to handle CertificatesExtractedEvent: uploadId={}, error={}",
                event.getUploadId(), e.getMessage(), e);
        }
    }

    /**
     * CertificateData를 Certificate Aggregate Root로 변환
     *
     * @param certData CertificateData from parsing layer
     * @param uploadId Upload ID
     * @return Certificate Aggregate Root
     */
    private Certificate convertToCertificate(CertificateData certData, UUID uploadId) {
        try {
            // 1. X.509 인증서 파싱 (PublicKey와 SignatureAlgorithm 추출용)
            CertificateFactory certFactory = CertificateFactory.getInstance("X.509");
            X509Certificate x509Cert = (X509Certificate) certFactory.generateCertificate(
                new ByteArrayInputStream(certData.getCertificateBinary())
            );

            PublicKey publicKey = x509Cert.getPublicKey();
            String signatureAlgorithm = x509Cert.getSigAlgName();
            boolean isCA = x509Cert.getBasicConstraints() != -1;  // -1 means not a CA

            // 2. Value Objects 생성
            // 2.1 SubjectInfo (simplified - using only DN and countryCode)
            SubjectInfo subjectInfo = SubjectInfo.of(
                certData.getSubjectDN(),
                certData.getCountryCode(),
                null,  // organization - not parsed yet
                null,  // organizationalUnit - not parsed yet
                null   // commonName - not parsed yet
            );

            // 2.2 IssuerInfo (simplified - using only DN and isCA flag)
            IssuerInfo issuerInfo = IssuerInfo.of(
                certData.getIssuerDN(),
                certData.getCountryCode(),
                null,  // organization - not parsed yet
                null,  // organizationalUnit - not parsed yet
                null,  // commonName - not parsed yet
                isCA
            );

            // 2.3 ValidityPeriod
            ValidityPeriod validityPeriod = ValidityPeriod.of(
                certData.getNotBefore(),
                certData.getNotAfter()
            );

            // 2.4 X509Data
            X509Data x509Data = X509Data.of(
                certData.getCertificateBinary(),
                publicKey,
                certData.getSerialNumber(),
                certData.getFingerprintSha256()
            );

            // 2.5 CertificateType (convert String to enum)
            CertificateType certificateType = CertificateType.valueOf(certData.getCertificateType());

            // 3. Certificate Aggregate Root 생성
            return Certificate.create(
                uploadId,
                x509Data,
                subjectInfo,
                issuerInfo,
                validityPeriod,
                certificateType,
                signatureAlgorithm
            );

        } catch (Exception e) {
            log.error("Failed to convert CertificateData to Certificate: subject={}, error={}",
                certData.getSubjectDN(), e.getMessage(), e);
            throw new RuntimeException("Certificate conversion failed", e);
        }
    }

    /**
     * CertificatesValidatedEvent 동기 핸들러 (Phase 16: 배치 검증 완료)
     *
     * <p><b>처리 내용</b>:</p>
     * <ul>
     *   <li>배치 검증 완료 로깅</li>
     *   <li>검증 통계 업데이트 (성공률, 실패율)</li>
     *   <li>SSE 진행 상황 업데이트</li>
     * </ul>
     *
     * <p><b>실행 시점</b>: 트랜잭션 커밋 전 (읽기 전용)</p>
     *
     * @param event 배치 검증 완료 이벤트
     */
    @EventListener
    public void handleCertificatesValidated(CertificatesValidatedEvent event) {
        log.info("=== [Event-Sync] CertificatesValidated (Batch Validation Completed) ===");
        log.info("Upload ID: {}", event.getUploadId());
        log.info("Validation Summary:");
        log.info("  - Valid Certificates: {}", event.getValidCertificateCount());
        log.info("  - Invalid Certificates: {}", event.getInvalidCertificateCount());
        log.info("  - Valid CRLs: {}", event.getValidCrlCount());
        log.info("  - Invalid CRLs: {}", event.getInvalidCrlCount());
        log.info("  - Total Validated: {}", event.getTotalValidated());
        log.info("  - Success Rate: {}%", event.getSuccessRate());
    }

    /**
     * CertificatesValidatedEvent 비동기 핸들러 (Phase 14: 배치 검증 완료 후 LDAP 업로드 트리거)
     *
     * <p><b>처리 내용</b>:</p>
     * <ul>
     *   <li>검증된 인증서 조회: CertificateRepository에서 uploadId로 검증된 인증서 조회</li>
     *   <li>LDAP 업로드: 검증된 인증서들을 실제 LDAP 서버에 업로드</li>
     *   <li>배치 처리: 대량의 인증서/CRL을 효율적으로 업로드</li>
     *   <li>진행 상황 추적: SSE를 통해 실시간 업로드 진행률 전송</li>
     * </ul>
     *
     * <p><b>실행 시점</b>: 트랜잭션 커밋 후 (별도 스레드)</p>
     *
     * <p><b>Event Chain</b>:</p>
     * <pre>
     * FileParsingCompletedEvent
     *   → LdifParsingEventHandler
     *     → ValidateCertificatesUseCase
     *       → CertificatesValidatedEvent
     *         → handleCertificatesValidatedAndTriggerLdapUpload()  [THIS METHOD]
     *           → [Query CertificateRepository for validated certificates]
     *           → UploadToLdapUseCase (Phase 17 - Production)
     *             → LdapUploadCompletedEvent
     *               → LdapUploadEventHandler
     * </pre>
     *
     * @param event 배치 검증 완료 이벤트
     */
    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handleCertificatesValidatedAndTriggerLdapUpload(CertificatesValidatedEvent event) {
        log.info("=== [Event-Async] CertificatesValidated (Triggering LDAP upload) ===");
        log.info("Upload ID: {}", event.getUploadId());
        log.info("Valid certificates: {}, Valid CRLs: {}",
            event.getValidCertificateCount(), event.getValidCrlCount());

        try {
            // SSE Progress: LDAP_SAVING_STARTED (90%)
            progressService.sendProgress(
                ProcessingProgress.builder()
                    .uploadId(event.getUploadId())
                    .stage(ProcessingStage.LDAP_SAVING_STARTED)
                    .percentage(90)
                    .message("LDAP 서버 업로드 준비 중...")
                    .build()
            );

            // 1. CertificateRepository에서 uploadId로 검증된 인증서 조회
            // 참고: CertificatesValidatedEvent는 카운트만 포함하고 있으므로,
            // 실제 인증서 ID는 CertificateRepository에서 조회해야 함
            List<Certificate> validatedCertificates = certificateRepository.findByUploadId(event.getUploadId());

            if (validatedCertificates.isEmpty()) {
                log.warn("No validated certificates found for uploadId: {}", event.getUploadId());
                progressService.sendProgress(
                    ProcessingProgress.builder()
                        .uploadId(event.getUploadId())
                        .stage(ProcessingStage.LDAP_SAVING_COMPLETED)
                        .percentage(100)
                        .message("검증된 인증서가 없습니다")
                        .build()
                );
                return;
            }

            // 2. 검증된 인증서 ID 추출
            List<UUID> certificateIds = new java.util.ArrayList<>();
            for (Certificate cert : validatedCertificates) {
                certificateIds.add(cert.getId().getId());
            }

            log.info("Found {} validated certificates for LDAP upload", certificateIds.size());

            // 3. LDAP Base DN 설정 (Spring LDAP 설정에서 가져옴)
            String baseDn = "dc=ldap,dc=smartcoreinc,dc=com";  // Spring LDAP base from config
            boolean isBatch = certificateIds.size() > 1;

            // 4. Phase 17 UploadToLdapCommand 생성 (실제 인증서 ID 사용)
            UploadToLdapCommand command = UploadToLdapCommand.builder()
                .uploadId(event.getUploadId())
                .certificateIds(certificateIds)
                .baseDn(baseDn)
                .isBatch(isBatch)
                .build();

            log.info("Executing Phase 17 LDAP upload: uploadId={}, certificateCount={}, isBatch={}",
                event.getUploadId(), certificateIds.size(), isBatch);

            // 5. LDAP 업로드 실행 (UploadToLdapUseCase - Phase 17 Production)
            var response = uploadToLdapUseCase.execute(command);

            if (response.isSuccess()) {
                log.info("LDAP upload completed successfully: uploadId={}, uploaded={}, failed={}",
                    event.getUploadId(), response.getSuccessCount(), response.getFailureCount());

                // SSE Progress: LDAP_SAVING_COMPLETED (100%)
                progressService.sendProgress(
                    ProcessingProgress.builder()
                        .uploadId(event.getUploadId())
                        .stage(ProcessingStage.LDAP_SAVING_COMPLETED)
                        .percentage(100)
                        .message(String.format("LDAP 저장 완료: %d 인증서 업로드됨", response.getSuccessCount()))
                        .processedCount(response.getSuccessCount())
                        .totalCount(certificateIds.size())
                        .build()
                );
            } else {
                log.error("LDAP upload failed: uploadId={}, error={}",
                    event.getUploadId(), response.getErrorMessage());

                // SSE Progress: FAILED
                progressService.sendProgress(
                    ProcessingProgress.failed(
                        event.getUploadId(),
                        ProcessingStage.FAILED,
                        "LDAP 업로드 실패: " + response.getErrorMessage()
                    )
                );
            }

        } catch (Exception e) {
            log.error("Exception during LDAP upload trigger for uploadId: {}",
                event.getUploadId(), e);

            // SSE Progress: FAILED
            progressService.sendProgress(
                ProcessingProgress.failed(
                    event.getUploadId(),
                    ProcessingStage.FAILED,
                    "LDAP 업로드 트리거 실패: " + e.getMessage()
                )
            );
        }
    }

    // ========== Skeleton Methods ==========

    private void updateValidationStatistics(CertificateValidatedEvent event) {
        // TODO: Phase 11 Sprint 5에서 통계 서비스 구현 예정
        log.debug("[SKELETON] Updating validation statistics for: {}", event.getCertificateId().getId());
    }

    private void monitorSecurityStatus(CertificateValidatedEvent event) {
        // TODO: Phase 11 Sprint 5에서 보안 감시 로직 구현 예정
        log.debug("[SKELETON] Monitoring security status");
    }

    private void syncToLdap(CertificateValidatedEvent event) {
        // TODO: LDAP Integration Context와 연동 예정
        log.debug("[SKELETON] Syncing to LDAP: {}", event.getCertificateId().getId());
    }

    private void sendMonitoringAlert(CertificateValidatedEvent event) {
        // TODO: 알림 서비스 구현 예정
        log.debug("[SKELETON] Sending monitoring alert");
    }

    private void collectMetrics(CertificateValidatedEvent event) {
        // TODO: 메트릭 수집 로직 구현 예정
        log.debug("[SKELETON] Collecting metrics");
    }

    private void updateTrustChainStatistics(TrustChainVerifiedEvent event) {
        // TODO: Trust Chain 통계 구현 예정
        log.debug("[SKELETON] Updating trust chain statistics");
    }

    private void analyzeTrustChainStructure(TrustChainVerifiedEvent event) {
        // TODO: 체인 구조 분석 로직 구현 예정
        log.debug("[SKELETON] Analyzing trust chain structure");
    }

    private void syncTrustRelationshipToLdap(TrustChainVerifiedEvent event) {
        // TODO: LDAP 신뢰 관계 설정 구현 예정
        log.debug("[SKELETON] Syncing trust relationship to LDAP");
    }

    private void sendTrustChainAlert(TrustChainVerifiedEvent event) {
        // TODO: Trust Chain 관련 알림 구현 예정
        log.debug("[SKELETON] Sending trust chain alert");
    }

    private void updateRevocationStatistics(CertificateRevokedEvent event) {
        // TODO: 폐기 통계 구현 예정
        log.debug("[SKELETON] Updating revocation statistics");
    }

    private void recordSecurityAlert(CertificateRevokedEvent event) {
        // TODO: 보안 경고 기록 로직 구현 예정
        log.debug("[SKELETON] Recording security alert");
    }

    private void disableLdapEntry(CertificateRevokedEvent event) {
        // TODO: LDAP 엔트리 비활성화 구현 예정
        log.warn("[SKELETON] Disabling LDAP entry for revoked certificate");
    }

    private void sendEmergencySecurityAlert(CertificateRevokedEvent event) {
        // TODO: 긴급 보안 알림 구현 예정
        log.warn("[SKELETON] Sending emergency security alert");
    }

    private void trackSignedDocuments(CertificateRevokedEvent event) {
        // TODO: 서명된 문서 추적 로직 구현 예정
        log.debug("[SKELETON] Tracking signed documents");
    }

    private void updateFailureStatistics(ValidationFailedEvent event) {
        // TODO: 실패 통계 구현 예정
        log.debug("[SKELETON] Updating failure statistics");
    }

    private void evaluateFailureSeverity(ValidationFailedEvent event) {
        // TODO: 심각도 평가 로직 구현 예정
        log.debug("[SKELETON] Evaluating failure severity");
    }

    private void recordFailureAnalysis(ValidationFailedEvent event) {
        // TODO: 오류 분석 기록 로직 구현 예정
        log.debug("[SKELETON] Recording failure analysis");
    }

    private void updateLdapStatusConditional(ValidationFailedEvent event) {
        // TODO: 조건부 LDAP 업데이트 구현 예정
        log.debug("[SKELETON] Updating LDAP status conditionally");
    }

    private void setupMonitoring(ValidationFailedEvent event) {
        // TODO: 모니터링 설정 로직 구현 예정
        log.debug("[SKELETON] Setting up monitoring");
    }

    private void sendOperationalAlert(ValidationFailedEvent event) {
        // TODO: 운영 알림 구현 예정
        log.debug("[SKELETON] Sending operational alert");
    }

    private void notifyAnomalyDetection(ValidationFailedEvent event) {
        // TODO: 이상 탐지 시스템 연동 구현 예정
        log.debug("[SKELETON] Notifying anomaly detection system");
    }
}
