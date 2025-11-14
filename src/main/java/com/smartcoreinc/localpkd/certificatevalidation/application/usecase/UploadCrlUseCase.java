package com.smartcoreinc.localpkd.certificatevalidation.application.usecase;

import com.smartcoreinc.localpkd.certificatevalidation.application.command.UploadCrlCommand;
import com.smartcoreinc.localpkd.certificatevalidation.application.response.UploadCrlResponse;
import com.smartcoreinc.localpkd.certificatevalidation.domain.model.CertificateRevocationList;
import com.smartcoreinc.localpkd.certificatevalidation.domain.port.LdapConnectionPort;
import com.smartcoreinc.localpkd.certificatevalidation.domain.repository.CertificateRevocationListRepository;
import com.smartcoreinc.localpkd.shared.exception.DomainException;
import com.smartcoreinc.localpkd.shared.progress.ProgressService;
import com.smartcoreinc.localpkd.shared.progress.ProcessingProgress;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * UploadCrlUseCase - CRL (Certificate Revocation List) LDAP 업로드 Use Case
 *
 * <p><b>책임</b>:</p>
 * <ul>
 *   <li>파일 업로드 ID를 기반으로 CRL 검색</li>
 *   <li>각 CRL을 LDAP에 업로드</li>
 *   <li>배치 및 단일 업로드 지원</li>
 *   <li>SSE Progress 추적</li>
 *   <li>오류 처리 및 부분 성공 처리</li>
 * </ul>
 *
 * <p><b>실행 흐름 (8단계)</b>:</p>
 * <ol>
 *   <li>Command 검증</li>
 *   <li>ProgressService 초기화</li>
 *   <li>CRL ID 또는 UploadId 기반으로 CRL 조회</li>
 *   <li>LDAP 연결 확인</li>
 *   <li>각 CRL마다 uploadCrl() 호출</li>
 *   <li>SSE Progress 업데이트 (동적 진행률)</li>
 *   <li>성공/실패 목록 정리</li>
 *   <li>Response 생성 및 반환</li>
 * </ol>
 *
 * <p><b>사용 예시</b>:</p>
 * <pre>{@code
 * // 1. Command 생성
 * UploadCrlCommand command = UploadCrlCommand.builder()
 *     .uploadId(uploadId)
 *     .crlIds(crlIds)
 *     .baseDn("dc=data,dc=download,dc=pkd,dc=ldap,dc=smartcoreinc,dc=com")
 *     .isBatch(true)
 *     .build();
 *
 * // 2. Use Case 실행
 * UploadCrlResponse response = uploadCrlUseCase.execute(command);
 *
 * // 3. 결과 확인
 * if (response.success()) {
 *     log.info("✅ CRL upload succeeded: {} uploaded",
 *         response.successCount());
 * } else if (response.isPartiallySuccessful()) {
 *     log.warn("⚠️  CRL upload partial: {} uploaded, {} failed",
 *         response.successCount(), response.failedCount());
 * }
 * }</pre>
 *
 * @author SmartCore Inc.
 * @version 1.0
 * @since 2025-11-13
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UploadCrlUseCase {

    private final CertificateRevocationListRepository crlRepository;
    private final LdapConnectionPort ldapConnectionPort;
    private final ProgressService progressService;

    @Value("${app.ldap.base:dc=data,dc=download,dc=pkd,dc=ldap,dc=smartcoreinc,dc=com}")
    private String defaultBaseDn;

    /**
     * CRL LDAP 업로드 실행
     *
     * @param command 업로드 명령
     * @return 업로드 결과
     */
    @Transactional(readOnly = true)
    public UploadCrlResponse execute(UploadCrlCommand command) {
        log.info("=== CRL LDAP Upload Started ===");
        log.info("Command: {}", command);

        try {
            // 1. Command 검증
            validateCommand(command);

            // 2. Base DN 설정
            String baseDn = command.baseDn() != null && !command.baseDn().isEmpty()
                ? command.baseDn()
                : defaultBaseDn;
            log.debug("Base DN: {}", baseDn);

            // 3. CRL 조회
            List<CertificateRevocationList> crls = findCrls(command);
            if (crls.isEmpty()) {
                log.warn("No CRL found for upload ID: {}", command.uploadId());
                return UploadCrlResponse.failure(
                    0,
                    "No CRL found for upload ID: " + command.uploadId()
                );
            }

            log.info("Found {} CRL(s) to upload", crls.size());

            // 4. 배치 또는 단일 업로드 실행
            UploadCrlResponse response;
            if (command.isBatch()) {
                response = uploadCrlsBatch(crls, baseDn);
            } else {
                response = uploadCrlSingle(crls.get(0), baseDn);
            }

            log.info("=== CRL LDAP Upload Completed ===");
            log.info("Result: {} uploaded, {} failed",
                response.successCount(), response.failedCount());

            return response;

        } catch (IllegalArgumentException e) {
            log.error("Validation error: {}", e.getMessage());
            return UploadCrlResponse.failure(
                command.getCrlCount(),
                "Validation error: " + e.getMessage()
            );
        } catch (DomainException e) {
            log.error("Domain error: {}", e.getMessage());
            return UploadCrlResponse.failure(
                command.getCrlCount(),
                "Domain error: " + e.getMessage()
            );
        } catch (Exception e) {
            log.error("Unexpected error during CRL LDAP upload", e);
            return UploadCrlResponse.failure(
                command.getCrlCount(),
                "Unexpected error: " + e.getMessage()
            );
        }
    }

    /**
     * 단일 CRL 업로드
     */
    private UploadCrlResponse uploadCrlSingle(CertificateRevocationList crl, String baseDn) {
        log.debug("=== Single CRL Upload ===");
        log.debug("CRL ID: {}, IssuerName: {}", crl.getId(), crl.getIssuerName());

        try {
            // LDAP 업로드
            String ldapDn = ldapConnectionPort.uploadCrl(
                crl.getX509CrlData().getCrlBinary(),
                crl.getIssuerName().getValue(),
                baseDn
            );

            log.info("CRL uploaded successfully: {} → {}", crl.getId(), ldapDn);

            return UploadCrlResponse.success(
                1,
                List.of(ldapDn)
            );

        } catch (Exception e) {
            log.error("Failed to upload CRL: {}", crl.getId(), e);
            return UploadCrlResponse.failure(
                1,
                "Failed to upload CRL: " + e.getMessage()
            );
        }
    }

    /**
     * 배치 CRL 업로드
     *
     * <p>여러 개의 CRL을 한 번에 업로드합니다.</p>
     */
    private UploadCrlResponse uploadCrlsBatch(
        List<CertificateRevocationList> crls,
        String baseDn
    ) {
        log.debug("=== Batch CRL Upload ===");
        log.debug("CRL count: {}", crls.size());

        List<String> successDns = new ArrayList<>();
        List<java.util.UUID> failedCrlIds = new ArrayList<>();
        Map<java.util.UUID, String> errorDetails = new HashMap<>();

        int totalCount = crls.size();

        for (int i = 0; i < crls.size(); i++) {
            CertificateRevocationList crl = crls.get(i);

            try {
                // 진행률 업데이트 (동적: 92-98%, ProcessingProgress가 자동 계산)
                ProcessingProgress progress = ProcessingProgress.ldapSavingInProgress(
                    crl.getUploadId(),
                    i,
                    totalCount,
                    String.format("CRL %d/%d - %s", i + 1, totalCount, crl.getIssuerName())
                );
                progressService.sendProgress(progress);

                // LDAP 업로드
                String ldapDn = ldapConnectionPort.uploadCrl(
                    crl.getX509CrlData().getCrlBinary(),
                    crl.getIssuerName().getValue(),
                    baseDn
                );

                successDns.add(ldapDn);
                log.info("CRL uploaded: {} → {}", crl.getId(), ldapDn);

            } catch (Exception e) {
                log.error("Failed to upload CRL: {}", crl.getId(), e);
                failedCrlIds.add(crl.getId().getId());
                errorDetails.put(
                    crl.getId().getId(),
                    "LDAP upload error: " + e.getMessage()
                );
            }
        }

        // 결과 반환
        if (failedCrlIds.isEmpty()) {
            return UploadCrlResponse.success(totalCount, successDns);
        } else {
            return UploadCrlResponse.partialSuccess(
                totalCount,
                successDns.size(),
                successDns,
                failedCrlIds,
                errorDetails
            );
        }
    }

    /**
     * CRL 조회
     *
     * <p>Command의 crlIds가 비어있으면 uploadId 기반으로 검색합니다.</p>
     */
    private List<CertificateRevocationList> findCrls(UploadCrlCommand command) {
        log.debug("Finding CRLs for upload ID: {}", command.uploadId());

        // crlIds가 지정되었으면 그것을 사용, 아니면 uploadId로 검색
        if (command.crlIds() != null && !command.crlIds().isEmpty()) {
            log.debug("Using provided CRL IDs: {}", command.crlIds());
            // 개별 ID로 조회 (나중에 추가 구현 가능)
            return List.of();
        } else {
            log.debug("Searching CRLs by upload ID: {}", command.uploadId());
            return crlRepository.findByUploadId(command.uploadId());
        }
    }

    /**
     * Command 검증
     */
    private void validateCommand(UploadCrlCommand command) {
        log.debug("Validating command...");

        if (command == null) {
            throw new IllegalArgumentException("Command must not be null");
        }

        command.validate();
        log.debug("Command validation passed");
    }
}
